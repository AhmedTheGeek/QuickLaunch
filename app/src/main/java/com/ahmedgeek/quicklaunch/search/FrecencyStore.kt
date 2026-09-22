package com.ahmedgeek.quicklaunch.search

import android.util.AtomicFile
import android.util.Log
import com.ahmedgeek.quicklaunch.Bg
import com.ahmedgeek.quicklaunch.index.AppEntry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/** Launch history for one app: a decayed count plus the timestamp it was last updated at. */
class FrecencyEntry(@JvmField @Volatile var score: Float, @JvmField @Volatile var lastTs: Long) {
    fun decayed(now: Long): Float = Frecency.decay(score, lastTs, now)
}

object Frecency {
    const val HALF_LIFE_MS: Double = 7.0 * 24 * 60 * 60 * 1000
    const val MAX_BOOST = 300
    const val PRUNE_BELOW = 0.05f

    fun decay(score: Float, lastTs: Long, now: Long): Float {
        val age = (now - lastTs).coerceAtLeast(0L)
        if (age == 0L) return score
        return (score * 2.0.pow(-age / HALF_LIFE_MS)).toFloat()
    }

    /** 0..300; one launch ~42, five ~108, twenty ~183. */
    fun boost(decayed: Float): Int {
        if (decayed <= 0f) return 0
        return min(MAX_BOOST, (60.0 * ln(1.0 + decayed)).toInt())
    }
}

/**
 * Per-app launch frecency persisted in a small binary file.
 * File I/O only on Bg.bg; in-memory map is concurrent so main can record a launch without waiting.
 */
class FrecencyStore(file: File) {
    private val atomicFile = AtomicFile(file)
    private val map = ConcurrentHashMap<String, FrecencyEntry>(64)

    /** Apps with launch history. */
    val size: Int get() = map.size

    /** Background: read the file. Safe to call once before any attach(). */
    fun load() {
        if (!atomicFile.baseFile.exists()) return
        try {
            DataInputStream(BufferedInputStream(atomicFile.openRead())).use { input ->
                if (input.readInt() != MAGIC) return
                val count = input.readInt()
                repeat(count) {
                    val key = input.readUTF()
                    val score = input.readFloat()
                    val ts = input.readLong()
                    map[key] = FrecencyEntry(score, ts)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "frecency unreadable, starting fresh", e)
            map.clear()
        } catch (e: RuntimeException) {
            Log.w(TAG, "frecency corrupt, starting fresh", e)
            map.clear()
        }
    }

    /** Point each entry at its history so ranking does no lookups. */
    fun attach(entries: List<AppEntry>) {
        for (e in entries) e.frecency = map[e.key]
    }

    /** Main: bump the entry and schedule a write. */
    fun recordLaunch(entry: AppEntry, now: Long) {
        val existing = entry.frecency ?: map[entry.key]
        if (existing == null) {
            val fresh = FrecencyEntry(1f, now)
            map[entry.key] = fresh
            entry.frecency = fresh
        } else {
            existing.score = existing.decayed(now) + 1f
            existing.lastTs = now
            entry.frecency = existing
        }
        Bg.bg.post { persist() }
    }

    /** Background: drop history for apps that no longer exist or have decayed to nothing. */
    fun prune(current: List<AppEntry>) {
        val keep = HashSet<String>(current.size * 2)
        for (e in current) keep.add(e.key)
        val now = System.currentTimeMillis()
        val it = map.entries.iterator()
        var removed = false
        while (it.hasNext()) {
            val (key, entry) = it.next()
            if (!keep.contains(key) || entry.decayed(now) < Frecency.PRUNE_BELOW) {
                it.remove()
                removed = true
            }
        }
        if (removed) persist()
    }

    private fun persist() {
        val stream = atomicFile.startWrite()
        try {
            val out = DataOutputStream(BufferedOutputStream(stream))
            out.writeInt(MAGIC)
            val snapshot = map.entries.toList()
            out.writeInt(snapshot.size)
            for ((key, entry) in snapshot) {
                out.writeUTF(key)
                out.writeFloat(entry.score)
                out.writeLong(entry.lastTs)
            }
            out.flush()
            atomicFile.finishWrite(stream)
        } catch (e: IOException) {
            Log.w(TAG, "frecency write failed", e)
            atomicFile.failWrite(stream)
        }
    }

    companion object {
        private const val TAG = "QL"
        private const val MAGIC = 0x514C4631 // "QLF1"
    }
}
