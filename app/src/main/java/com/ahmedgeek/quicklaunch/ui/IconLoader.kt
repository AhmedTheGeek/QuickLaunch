package com.ahmedgeek.quicklaunch.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.ahmedgeek.quicklaunch.Bg
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry
import com.ahmedgeek.quicklaunch.index.AppIndex
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Icons are rasterized once to a fixed-size bitmap off the main thread and cached in memory and on disk.
 * The main thread only ever calls [peek] (LruCache hit) or [request] (enqueue); it never decodes.
 */
class IconLoader(context: Context, private val index: AppIndex) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager
    private val iconPx = appContext.resources.getDimensionPixelSize(R.dimen.icon_size)
    private val dir = File(appContext.cacheDir, "icons")

    private val memory = object : LruCache<String, Bitmap>(64 * iconPx * iconPx * 4) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Keys currently being loaded. Main thread only. */
    private val inFlight = HashSet<String>()

    fun peek(key: String): Bitmap? = memory.get(key)

    /** Main thread, for diagnostics. */
    fun memoryCount(): Int = memory.snapshot().size

    /** Main thread: drop every cached icon; they re-render on next use. */
    fun clear() {
        memory.evictAll()
        Bg.icons.execute { dir.listFiles()?.forEach { it.delete() } }
    }

    fun request(entry: AppEntry, callback: (String, Bitmap) -> Unit) {
        val key = entry.key
        if (memory.get(key) != null || !inFlight.add(key)) return
        Bg.icons.execute {
            val bitmap = loadFromDisk(entry) ?: render(entry)
            Bg.main.post {
                inFlight.remove(key)
                if (bitmap != null) {
                    memory.put(key, bitmap)
                    callback(key, bitmap)
                }
            }
        }
    }

    /**
     * Icon of the app that will open [url] (the default browser for most links), under [key], which
     * is also the link row's tag. Resolution is a binder call, so it runs off the main thread too.
     * No callback when there is no single default handler; the row keeps its link glyph then.
     */
    fun requestLinkIcon(url: String, key: String, callback: (String, Bitmap) -> Unit) {
        if (memory.get(key) != null || !inFlight.add(key)) return
        Bg.icons.execute {
            val bitmap = renderLinkHandler(url)
            Bg.main.post {
                inFlight.remove(key)
                if (bitmap != null) {
                    memory.put(key, bitmap)
                    callback(key, bitmap)
                }
            }
        }
    }

    /** Warm the memory cache for the apps a short query is most likely to surface. */
    fun prewarm(entries: List<AppEntry>, callback: (String, Bitmap) -> Unit) {
        val now = System.currentTimeMillis()
        entries.asSequence()
            .filter { it.frecency != null }
            .sortedByDescending { it.frecency!!.decayed(now) }
            .take(PREWARM_COUNT)
            .forEach { request(it, callback) }
    }

    /** Called on any thread when a package changed: drop its icons everywhere. */
    fun invalidatePackage(packageName: String) {
        Bg.main.post {
            val snapshot = memory.snapshot()
            for (k in snapshot.keys) if (k.contains("|$packageName/")) memory.remove(k)
        }
        Bg.icons.execute {
            dir.listFiles()?.forEach { f -> if (f.name.contains("_${sanitize(packageName)}_")) f.delete() }
        }
    }

    // ---- Background --------------------------------------------------------------------------

    private fun file(entry: AppEntry): File =
        File(dir, "${entry.userSerial}_${sanitize(entry.component.packageName)}_${sanitize(entry.component.className)}.png")

    private fun loadFromDisk(entry: AppEntry): Bitmap? {
        val f = file(entry)
        if (!f.isFile) return null
        // Re-render stale icons occasionally; app updates while our process was dead are otherwise invisible.
        if (System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) return null
        return try {
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun render(entry: AppEntry): Bitmap? {
        val info = index.findActivity(entry) ?: return null
        val drawable: Drawable = try {
            val raw = info.getIcon(0)
            if (entry.isWork) pm.getUserBadgedIcon(raw, info.user) else raw
        } catch (e: RuntimeException) {
            Log.w(TAG, "icon load failed for ${entry.key}", e)
            return null
        }
        val bitmap = rasterize(drawable)
        writeToDisk(entry, bitmap)
        return bitmap
    }

    private fun renderLinkHandler(url: String): Bitmap? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
            val pkg = resolved.activityInfo?.packageName ?: return null
            // "android" is the system chooser: several browsers, none chosen as default.
            if (pkg == "android") return null
            rasterize(pm.getApplicationIcon(pkg))
        } catch (e: RuntimeException) {
            Log.w(TAG, "link handler icon failed", e)
            null
        }
    }

    private fun rasterize(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, iconPx, iconPx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun writeToDisk(entry: AppEntry, bitmap: Bitmap) {
        try {
            if (!dir.isDirectory && !dir.mkdirs()) return
            val target = file(entry)
            val tmp = File(dir, target.name + ".tmp")
            FileOutputStream(tmp).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (!tmp.renameTo(target)) tmp.delete()
        } catch (e: IOException) {
            Log.w(TAG, "icon cache write failed", e)
        }
    }

    private fun sanitize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c.isLetterOrDigit() || c == '.' || c == '-') c else '_')
        return sb.toString()
    }

    private companion object {
        const val TAG = "QL"
        const val PREWARM_COUNT = 24
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
