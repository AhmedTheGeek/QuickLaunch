package com.ahmedgeek.quicklaunch.index

import android.content.ComponentName
import android.util.AtomicFile
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Compact binary cache of the app index. Parses in about a millisecond; no JSON, no SQLite.
 *
 * Layout: magic "QLX1", u16 version, u16 count, then per entry:
 *   i64 userSerial, UTF package, UTF class, UTF label, u8 flags (bit0 work, bit1 paused), f32 usage (v2)
 */
object IndexStore {
    private const val TAG = "QL"
    private const val MAGIC = 0x514C5831 // "QLX1"
    private const val VERSION = 2

    fun read(file: AtomicFile): List<AppEntry>? {
        if (!file.baseFile.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(file.openRead(), 16 * 1024)).use { input ->
                if (input.readInt() != MAGIC) return null
                val version = input.readUnsignedShort()
                if (version < 1 || version > VERSION) return null
                val count = input.readUnsignedShort()
                val out = ArrayList<AppEntry>(count)
                repeat(count) {
                    val serial = input.readLong()
                    val pkg = input.readUTF()
                    val cls = input.readUTF()
                    val label = input.readUTF()
                    val flags = input.readUnsignedByte()
                    val usage = if (version >= 2) input.readFloat() else 0f
                    out.add(
                        AppEntry(
                            userSerial = serial,
                            component = ComponentName(pkg, cls),
                            label = label,
                            isWork = flags and 0x1 != 0,
                            paused = flags and 0x2 != 0,
                        ).also { it.usage = usage }
                    )
                }
                out
            }
        } catch (e: IOException) {
            Log.w(TAG, "index cache unreadable, ignoring", e)
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "index cache corrupt, ignoring", e)
            null
        }
    }

    fun write(file: AtomicFile, entries: List<AppEntry>) {
        val stream = file.startWrite()
        try {
            val out = DataOutputStream(BufferedOutputStream(stream, 16 * 1024))
            out.writeInt(MAGIC)
            out.writeShort(VERSION)
            out.writeShort(entries.size.coerceAtMost(0xFFFF))
            for (e in entries.take(0xFFFF)) {
                out.writeLong(e.userSerial)
                out.writeUTF(e.component.packageName)
                out.writeUTF(e.component.className)
                out.writeUTF(e.label)
                out.writeByte((if (e.isWork) 1 else 0) or (if (e.paused) 2 else 0))
                out.writeFloat(e.usage)
            }
            out.flush()
            file.finishWrite(stream)
        } catch (e: IOException) {
            Log.w(TAG, "index cache write failed", e)
            file.failWrite(stream)
        }
    }
}
