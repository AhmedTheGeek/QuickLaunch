package com.ahmedgeek.quicklaunch.suggest

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.OperationCanceledException
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.ahmedgeek.quicklaunch.Bg
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R

/**
 * `f invoice`: files by name, through MediaStore, which indexes every file on shared storage once the
 * app has All files access. Only behind the keyword, never on plain queries.
 *
 * The query runs on its own thread, 150 ms after typing stops, and a newer query cancels the running
 * one. [suggest] stays synchronous: it returns the last results for these terms (or narrows the
 * previous ones while the new query runs) and [onUpdate] asks the panel to re-read once rows arrive.
 */
class FileSearchSource(private val context: Context) : SuggestionSource {

    /** Main thread. Set by the panel while it is showing. */
    var onUpdate: (() -> Unit)? = null

    private val thread by lazy { HandlerThread("ql-files", Process.THREAD_PRIORITY_BACKGROUND).apply { start() } }
    private val worker by lazy { Handler(thread.looper) }
    private var pending: Runnable? = null
    private var running: CancellationSignal? = null

    private var cachedTerms: String? = null
    private var cached: List<Hit> = emptyList()

    class Hit(@JvmField val id: Long, @JvmField val name: String, @JvmField val folder: String, @JvmField val mime: String?)

    override fun suggest(raw: String, query: String, out: MutableList<Suggestion>) {
        val terms = terms(raw) ?: return
        if (!granted()) {
            out.add(Suggestion("files|grant", context.getText(R.string.files_grant), null, R.drawable.ic_file, null) { c ->
                openGrantScreen(c)
                true
            })
            return
        }
        if (terms.length < MIN_TERMS) return
        val last = cachedTerms
        if (terms == last) {
            for (h in cached) out.add(row(h))
            return
        }
        // Still typing: keep showing what matches from the previous results so the list doesn't blink.
        if (last != null && terms.startsWith(last)) {
            for (h in cached) if (matches(h.name, terms)) out.add(row(h))
        }
        schedule(terms)
    }

    private fun row(h: Hit): Suggestion {
        val uri = ContentUris.withAppendedId(FILES, h.id)
        val visual = h.mime != null && (h.mime.startsWith("image/") || h.mime.startsWith("video/"))
        return Suggestion(
            "files|${h.id}", h.name, h.folder, R.drawable.ic_file, null,
            content = uri, contentMime = h.mime, thumbnail = if (visual) uri else null,
        ) { c -> open(c, h) }
    }

    private fun schedule(terms: String) {
        pending?.let { worker.removeCallbacks(it) }
        running?.cancel()
        val task = Runnable { run(terms) }
        pending = task
        worker.postDelayed(task, DEBOUNCE_MS)
    }

    /** Worker thread. */
    private fun run(terms: String) {
        val signal = CancellationSignal()
        running = signal
        val t0 = SystemClock.elapsedRealtime()
        val hits = try {
            query(terms, signal)
        } catch (e: OperationCanceledException) {
            return
        } catch (e: RuntimeException) {
            Log.w(QuickLaunchApp.TAG, "file search failed", e)
            emptyList()
        }
        if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) {
            Log.d(QuickLaunchApp.TAG, "files '$terms': ${hits.size} in ${SystemClock.elapsedRealtime() - t0} ms")
        }
        Bg.main.post {
            cachedTerms = terms
            cached = hits
            onUpdate?.invoke()
        }
    }

    private fun query(terms: String, signal: CancellationSignal): List<Hit> {
        val words = words(terms)
        val args = Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection(words.size))
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, words.map { likeArg(it) }.toTypedArray())
            putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED))
            putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, MAX_HITS)
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val out = ArrayList<Hit>(MAX_HITS)
        context.contentResolver.query(FILES, projection, args, signal)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: ""
                if (name.isNotEmpty()) out.add(Hit(c.getLong(0), name, folder(c.getString(2)), c.getString(3)))
            }
        }
        return out
    }

    private fun open(c: Context, h: Hit): Boolean {
        val uri = ContentUris.withAppendedId(FILES, h.id)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, h.mime ?: "*/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            c.startActivity(intent)
            true
        } catch (e: RuntimeException) {
            Toast.makeText(c, R.string.files_no_app, Toast.LENGTH_SHORT).show()
            false
        }
    }

    companion object {
        const val KEYWORD = "f"
        private const val MIN_TERMS = 2
        private const val MAX_HITS = 8
        private const val DEBOUNCE_MS = 150L
        private val FILES = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

        /** Whether this build includes file search at all (Gradle property quicklaunch.fileSearch). */
        fun built(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context.resources.getBoolean(R.bool.file_search_build)

        fun granted(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

        /** The system's "All files access" switch for this app. */
        fun openGrantScreen(c: Context) {
            try {
                c.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${c.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: RuntimeException) {
                Log.w(QuickLaunchApp.TAG, "all files access screen missing", e)
            }
        }

        /** "f invoice march" -> "invoice march"; null when the input isn't a file search. */
        fun terms(raw: String): String? {
            if (raw.length < 2 || !raw.regionMatches(0, "$KEYWORD ", 0, 2, ignoreCase = true)) return null
            return raw.substring(2).trim().lowercase()
        }

        fun words(terms: String): List<String> = terms.split(' ').filter { it.isNotEmpty() }

        /** Every word must appear in the name, in any order. Directories have no MIME type, so they're skipped. */
        fun selection(words: Int): String {
            val sb = StringBuilder("${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL")
            repeat(words) { sb.append(" AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'") }
            return sb.toString()
        }

        /** LIKE argument matching [word] anywhere, with % and _ taken literally. */
        fun likeArg(word: String): String {
            val sb = StringBuilder(word.length + 4).append('%')
            for (ch in word) {
                if (ch == '%' || ch == '_' || ch == '\\') sb.append('\\')
                sb.append(ch)
            }
            return sb.append('%').toString()
        }

        fun matches(name: String, terms: String): Boolean {
            val lower = name.lowercase()
            return words(terms).all { lower.contains(it) }
        }

        /** "Documents/Invoices/" -> "Invoices"; the badge only has room for the last folder. */
        fun folder(relativePath: String?): String {
            if (relativePath.isNullOrEmpty()) return ""
            val trimmed = relativePath.trimEnd('/')
            return trimmed.substring(trimmed.lastIndexOf('/') + 1)
        }
    }
}
