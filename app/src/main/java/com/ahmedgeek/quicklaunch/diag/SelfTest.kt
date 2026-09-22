package com.ahmedgeek.quicklaunch.diag

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.index.AppEntry
import com.ahmedgeek.quicklaunch.index.UsageSource
import com.ahmedgeek.quicklaunch.search.Ranker
import com.ahmedgeek.quicklaunch.shortcut.KeyboardShortcutService
import com.ahmedgeek.quicklaunch.suggest.Calculator
import com.ahmedgeek.quicklaunch.suggest.SystemShortcuts
import com.ahmedgeek.quicklaunch.suggest.UnitConverter

/**
 * Checks run on the user's own phone when they tap "Run checks", never otherwise. Background thread.
 * Results hold counts and timings only, no app names, so the report can be pasted into an issue.
 */
class SelfTest(context: Context) {
    private val app = QuickLaunchApp.get(context)

    enum class Status { OK, WARN, FAIL, INFO }

    class Check(@JvmField val name: String, @JvmField val status: Status, @JvmField val detail: String)

    fun run(): List<Check> {
        val out = ArrayList<Check>()
        fun add(name: String, status: Status, detail: String) = out.add(Check(name, status, detail))
        fun guard(name: String, block: () -> Unit) = try {
            block()
        } catch (e: Exception) {
            add(name, Status.FAIL, e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        }

        guard("Instant mode") {
            if (Settings.canDrawOverlays(app)) add("Instant mode", Status.OK, "Display over other apps is allowed")
            else add("Instant mode", Status.WARN, "Not allowed: the card opens as a normal screen, slower and with an animation")
        }

        val snapshot = app.index.awaitSnapshot()
        guard("App index") {
            val la = app.getSystemService(LauncherApps::class.java)
            val t0 = SystemClock.elapsedRealtime()
            var live = 0
            for (user in la.profiles) live += la.getActivityList(null, user).size
            val ms = SystemClock.elapsedRealtime() - t0
            // The live list includes Quick Launch's own entry point, which the index leaves out.
            val diff = live - 1 - snapshot.size
            val detail = "${snapshot.size} apps indexed, $live launcher entries now, listed in $ms ms"
            add("App index", if (kotlin.math.abs(diff) <= 1) Status.OK else Status.WARN, if (diff == 0) detail else "$detail (re-index to refresh)")
        }

        guard("Ranking speed") {
            val queries = ArrayList<String>()
            for (c in 'a'..'z') queries.add(c.toString())
            for (c in 'a'..'z') queries.add("${c}e")
            queries.addAll(listOf("mes", "yt", "set", "goo ma", "whats app", "sptfy", "cam", "my "))
            val results = ArrayList<AppEntry>(Ranker.MAX_RESULTS)
            val now = System.currentTimeMillis()
            repeat(3) { for (q in queries) Ranker.rank(snapshot, q, now, results) } // warm up the JIT
            var total = 0L
            var max = 0L
            for (q in queries) {
                val t = SystemClock.elapsedRealtimeNanos()
                Ranker.rank(snapshot, q, now, results)
                val d = SystemClock.elapsedRealtimeNanos() - t
                total += d
                if (d > max) max = d
            }
            val avg = total / queries.size / 1000
            add("Ranking speed", if (avg < 1000) Status.OK else Status.WARN, "avg $avg µs, worst ${max / 1000} µs over ${queries.size} queries, ${snapshot.size} apps")
        }

        guard("Icons") {
            val sample = snapshot.take(12)
            val t0 = SystemClock.elapsedRealtime()
            var failed = 0
            for (e in sample) if (app.index.findActivity(e)?.getIcon(0) == null) failed++
            val ms = SystemClock.elapsedRealtime() - t0
            val avg = if (sample.isEmpty()) 0 else ms / sample.size
            add("Icons", if (failed == 0) Status.OK else Status.WARN, "$avg ms per icon (${sample.size} loaded, $failed failed)")
        }

        guard("Calculator") {
            val ok = Calculator.evaluate("(12+4)/2") == 8.0 && UnitConverter.convert("10cm in inch")?.unit == "in"
            add("Calculator", if (ok) Status.OK else Status.FAIL, if (ok) "math and unit conversion answer" else "wrong answer")
        }

        guard("Settings pages") {
            val pm = app.packageManager
            val missing = SystemShortcuts.ALL.filter { s ->
                s.action != null && pm.resolveActivity(Intent(s.action), PackageManager.MATCH_DEFAULT_ONLY) == null
            }.map { it.id }
            val total = SystemShortcuts.ALL.count { it.action != null }
            if (missing.isEmpty()) add("Settings pages", Status.OK, "all $total open on this phone")
            else add("Settings pages", Status.WARN, "${total - missing.size}/$total open, missing: ${missing.joinToString()}")
        }

        guard("Browser") {
            val r = app.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")), 0)
            add("Browser", if (r != null) Status.OK else Status.WARN, if (r != null) "links and web searches can open" else "nothing opens https links")
        }

        guard("Storage") {
            val probe = java.io.File(app.filesDir, "selftest.tmp")
            probe.writeText("ok")
            val ok = probe.readText() == "ok" && probe.delete()
            val kb = (app.filesDir.listFiles()?.sumOf { it.length() } ?: 0L) / 1024
            add("Storage", if (ok) Status.OK else Status.FAIL, "app files ${kb} KB, writable: $ok")
        }

        guard("Profiles") {
            val um = app.getSystemService(UserManager::class.java)
            val profiles = app.getSystemService(LauncherApps::class.java).profiles
            val paused = profiles.count { um.isQuietModeEnabled(it) }
            val work = snapshot.count { it.isWork }
            add("Profiles", Status.INFO, "${profiles.size} (work apps: $work, paused profiles: $paused)")
        }

        guard("File search") { fileSearch()?.let { out.add(it) } }

        guard("Optional access") {
            val usage = if (UsageSource.isGranted(app)) "on" else "off"
            val keys = if (KeyboardShortcutService.isEnabled(app)) "on" else "off"
            add("Optional access", Status.INFO, "most used apps $usage, Ctrl+Space $keys")
        }
        return out
    }

    /**
     * Only in builds that include file search (they declare All files access). Decoupled from the feature
     * on purpose: it just reads the same setting and runs the same kind of MediaStore query.
     */
    private fun fileSearch(): Check? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val declared = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) == true
        if (!declared) return null
        val on = app.getSharedPreferences("ql", Context.MODE_PRIVATE).getBoolean("file_search", false)
        if (!Environment.isExternalStorageManager()) {
            return Check("File search", if (on) Status.WARN else Status.INFO,
                if (on) "turned on but All files access isn't allowed" else "off, All files access not allowed")
        }
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val resolver = app.contentResolver
        var t0 = SystemClock.elapsedRealtime()
        val total = resolver.query(files, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL", null, null)
            ?.use { it.count } ?: 0
        val countMs = SystemClock.elapsedRealtime() - t0
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%e%"))
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, 8)
        }
        t0 = SystemClock.elapsedRealtime()
        resolver.query(files, arrayOf(MediaStore.MediaColumns._ID), args, null)?.use { it.count }
        val searchMs = SystemClock.elapsedRealtime() - t0
        val image = resolver.query(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), arrayOf(MediaStore.MediaColumns._ID),
            Bundle().apply { putInt(ContentResolver.QUERY_ARG_LIMIT, 1) }, null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
        val thumb = if (image == null) "no images" else {
            t0 = SystemClock.elapsedRealtime()
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), image)
            val ok = try { resolver.loadThumbnail(uri, android.util.Size(96, 96), null); true } catch (e: Exception) { false }
            if (ok) "thumbnail ${SystemClock.elapsedRealtime() - t0} ms" else "thumbnail failed"
        }
        val status = if (!on) Status.INFO else if (searchMs < 150) Status.OK else Status.WARN
        return Check("File search", status, "${if (on) "on" else "off"}, $total files indexed (counted in $countMs ms), a search takes $searchMs ms, $thumb")
    }

    companion object {
        fun device(): String = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
}
