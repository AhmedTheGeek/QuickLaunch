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

/**
 * The user's pinned apps, in the order they were pinned. Pinned apps lead the empty-query list in
 * this fixed order so the first few rows become muscle memory; frecency still fills the rest.
 *
 * Mirrors [FrecencyStore]: the order lives on each [AppEntry.pinOrder] so ranking does no lookups,
 * toggles happen on main and persist on Bg.bg, and the file is a tiny list of entry keys.
 */
class PinStore(file: File) {
    private val atomicFile = AtomicFile(file)

    /** Pinned keys in display order. Replaced wholesale on every change; never mutated in place. */
    @Volatile
    private var keys: List<String> = emptyList()

    /** Background: read the file. Safe to call once before any attach(). */
    fun load() {
        if (!atomicFile.baseFile.exists()) return
        try {
            DataInputStream(BufferedInputStream(atomicFile.openRead())).use { input ->
                if (input.readInt() != MAGIC) return
                val count = input.readInt()
                if (count < 0 || count > MAX_PINS) return
                val list = ArrayList<String>(count)
                repeat(count) { list.add(input.readUTF()) }
                keys = list
            }
        } catch (e: IOException) {
            Log.w(TAG, "pins unreadable, starting fresh", e)
            keys = emptyList()
        } catch (e: RuntimeException) {
            Log.w(TAG, "pins corrupt, starting fresh", e)
            keys = emptyList()
        }
    }

    /** Write each entry's position in the pinned order (or -1) so ranking reads a plain field. */
    fun attach(entries: List<AppEntry>) {
        val order = keys
        if (order.isEmpty()) {
            for (e in entries) e.pinOrder = -1
            return
        }
        val index = HashMap<String, Int>(order.size * 2)
        for (i in order.indices) index[order[i]] = i
        for (e in entries) e.pinOrder = index[e.key] ?: -1
    }

    /**
     * Main: pin an unpinned entry at the end of the order, or unpin a pinned one. Re-attaches [entries]
     * so their positions shift immediately. Returns true when the entry is now pinned.
     */
    @Synchronized
    fun toggle(entry: AppEntry, entries: List<AppEntry>): Boolean {
        val next = ArrayList(keys)
        val pinned = if (next.remove(entry.key)) {
            false
        } else {
            if (next.size >= MAX_PINS) return false
            next.add(entry.key)
            true
        }
        keys = next
        attach(entries)
        Bg.bg.post { persist() }
        return pinned
    }

    /** Background: forget pins for apps that no longer exist. */
    @Synchronized
    fun prune(current: List<AppEntry>) {
        val order = keys
        if (order.isEmpty()) return
        val keep = HashSet<String>(current.size * 2)
        for (e in current) keep.add(e.key)
        val next = order.filter { keep.contains(it) }
        if (next.size == order.size) return
        keys = next
        attach(current)
        persist()
    }

    private fun persist() {
        val snapshot = keys
        val stream = atomicFile.startWrite()
        try {
            val out = DataOutputStream(BufferedOutputStream(stream))
            out.writeInt(MAGIC)
            out.writeInt(snapshot.size)
            for (key in snapshot) out.writeUTF(key)
            out.flush()
            atomicFile.finishWrite(stream)
        } catch (e: IOException) {
            Log.w(TAG, "pins write failed", e)
            atomicFile.failWrite(stream)
        }
    }

    companion object {
        private const val TAG = "QL"
        private const val MAGIC = 0x514C5031 // "QLP1"
        /** More pins than rows on screen defeats the purpose; also bounds the file. */
        const val MAX_PINS = 32
    }
}
