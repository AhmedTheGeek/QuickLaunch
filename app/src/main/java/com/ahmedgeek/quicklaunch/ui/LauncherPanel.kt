package com.ahmedgeek.quicklaunch.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Trace
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry
import com.ahmedgeek.quicklaunch.launch.LaunchResult
import com.ahmedgeek.quicklaunch.search.Ranker
import com.ahmedgeek.quicklaunch.search.TextNormalizer

/**
 * The search UI, independent of how it is hosted. [LaunchActivity] hosts it in an activity window
 * (fallback); [com.ahmedgeek.quicklaunch.overlay.OverlayController] hosts it in a system overlay window.
 * Everything here runs on the main thread and is sub-millisecond except the first layout.
 */
class LauncherPanel(
    private val app: QuickLaunchApp,
    /** The view that receives window insets and whose padding tracks the IME. */
    private val windowRoot: View,
    private val host: Host,
) {
    interface Host {
        /** Close the panel immediately, no animation. */
        fun dismiss()

        /** A system drag started from a row: make the window pass-through so drop zones underneath get the drop. */
        fun onDragStarted()

        /** The drag ended without a drop: restore the window. */
        fun onDragCancelled()
    }

    private var imeWasVisible = false

    private val index = app.index
    private val icons = app.icons
    private val launcher = app.launcher
    private val res = windowRoot.resources

    private val scrim: View = windowRoot.findViewById(R.id.root)
    private val card: View = windowRoot.findViewById(R.id.card)
    val input: EditText = windowRoot.findViewById(R.id.input)
    private val resultsView: ResultsView = windowRoot.findViewById(R.id.results)
    private val emptyView: TextView = windowRoot.findViewById(R.id.empty)
    val hintBar: TextView = windowRoot.findViewById(R.id.hint_bar)
    private val usageHint: TextView = windowRoot.findViewById(R.id.usage_hint)
    private val shortcutHint: TextView = windowRoot.findViewById(R.id.shortcut_hint)
    private val prefs = app.getSharedPreferences("ql", android.content.Context.MODE_PRIVATE)

    private val results = ArrayList<AppEntry>(Ranker.MAX_RESULTS)
    private var query = ""
    private var selected = 0
    private var launched = false
    private var active = false
    private var dragging = false

    private val iconCallback: (String, Bitmap) -> Unit = { key, bitmap -> resultsView.onIconLoaded(key, bitmap) }

    init {
        resultsView.iconLoader = icons
        resultsView.onRowClick = { entry -> launch(entry) }
        resultsView.onRowLongPress = { entry, row -> startDrag(entry, row) }
        windowRoot.setOnDragListener { _, event ->
            if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) {
                Log.d(QuickLaunchApp.TAG, "drag event action=${event.action} result=${event.result} dragging=$dragging")
            }
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> event.localState is AppEntry
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    endDrag(dropped = event.result)
                    true
                }
                else -> false
            }
        }
        scrim.setOnClickListener { host.dismiss() }
        card.setOnClickListener { /* consume: taps on the card never dismiss */ }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = onQueryChanged(s?.toString() ?: "")
        })
        input.setOnEditorActionListener { _, actionId, event ->
            val isEnterKey = actionId == EditorInfo.IME_NULL && event != null && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || isEnterKey) {
                launchSelected()
            }
            true
        }
        usageHint.setOnClickListener {
            prefs.edit().putBoolean(PREF_USAGE_HINT_TAPPED, true).apply()
            try {
                app.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: RuntimeException) {
                Log.w(QuickLaunchApp.TAG, "usage access settings unavailable", e)
            }
            host.dismiss()
        }
        shortcutHint.setOnClickListener {
            prefs.edit().putBoolean(PREF_SHORTCUT_HINT_TAPPED, true).apply()
            try {
                app.startActivity(com.ahmedgeek.quicklaunch.shortcut.KeyboardShortcutService.settingsIntent())
            } catch (e: RuntimeException) {
                Log.w(QuickLaunchApp.TAG, "accessibility settings unavailable", e)
            }
            host.dismiss()
        }
        installInsetsHandling()
    }

    private fun updateUsageHint() {
        val show = query.isEmpty() && !prefs.getBoolean(PREF_USAGE_HINT_TAPPED, false) && !index.usagePermitted()
        val visibility = if (show) View.VISIBLE else View.GONE
        // Only one hint row at a time; the shortcut hint appears once a physical keyboard is attached.
        val showShortcut = !show && query.isEmpty() &&
            !prefs.getBoolean(PREF_SHORTCUT_HINT_TAPPED, false) &&
            KeyboardUtil.hasHardwareKeyboard(res.configuration) &&
            !com.ahmedgeek.quicklaunch.shortcut.KeyboardShortcutService.isEnabled(app)
        val shortcutVisibility = if (showShortcut) View.VISIBLE else View.GONE
        if (usageHint.visibility != visibility || shortcutHint.visibility != shortcutVisibility) {
            usageHint.visibility = visibility
            shortcutHint.visibility = shortcutVisibility
            ViewCompat.requestApplyInsets(windowRoot)
        }
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    /** Called every time the panel becomes visible. Renders cached results synchronously. */
    fun onShown() {
        active = true
        launched = false
        imeWasVisible = false
        index.listener = { onIndexChanged() }
        if (input.text.isNotEmpty()) input.setText("") else rerank()
        input.requestFocus()
        // After the first frame is committed: revalidate the index and warm the icon cache.
        windowRoot.post {
            if (!active) return@post
            index.revalidate(force = false)
            icons.prewarm(index.snapshot, iconCallback)
        }
    }

    fun onHidden() {
        active = false
        dragging = false
        windowRoot.removeCallbacks(dragWatchdog)
        index.listener = null
    }

    // ---- Insets --------------------------------------------------------------------------------

    private fun installInsetsHandling() {
        val topOffsetMin = res.getDimensionPixelSize(R.dimen.card_top_offset)
        val sideMargin = res.getDimensionPixelSize(R.dimen.card_margin_h)
        val maxWidth = res.getDimensionPixelSize(R.dimen.card_max_width)
        val rowHeight = res.getDimensionPixelSize(R.dimen.row_height)
        val fixedChrome = res.getDimensionPixelSize(R.dimen.input_height) +
            (rowHeight * 0.85f).toInt() + res.getDimensionPixelSize(R.dimen.card_padding) * 2

        ViewCompat.setOnApplyWindowInsetsListener(windowRoot) { v, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime: Insets = insets.getInsets(WindowInsetsCompat.Type.ime())
            // Back with the keyboard up hides the keyboard first; treat that as "cancel" like Esc.
            val imeVisible = ime.bottom > 0
            if (active && !dragging && imeWasVisible && !imeVisible) {
                imeWasVisible = false
                host.dismiss()
                return@setOnApplyWindowInsetsListener insets
            }
            imeWasVisible = imeVisible

            val screenWidth = if (v.width > 0) v.width else res.displayMetrics.widthPixels
            val screenHeight = if (v.height > 0) v.height else res.displayMetrics.heightPixels
            val bottom = maxOf(bars.bottom, ime.bottom) + sideMargin
            v.setPadding(bars.left, 0, bars.right, bottom)

            // Phones: full width minus margins. Tablets, foldables, DeX, landscape: a centered card
            // capped at card_max_width, sitting a bit lower so it reads as a floating palette.
            val availableWidth = screenWidth - bars.left - bars.right - sideMargin * 2
            val cardWidth = minOf(availableWidth, maxWidth)
            val topMargin = bars.top + maxOf(topOffsetMin, (screenHeight * 0.08f).toInt())

            val lp = card.layoutParams as ViewGroup.MarginLayoutParams
            if (lp.width != cardWidth || lp.topMargin != topMargin) {
                lp.width = cardWidth
                lp.topMargin = topMargin
                card.layoutParams = lp
            }

            val hintRow = if (usageHint.visibility == View.VISIBLE || shortcutHint.visibility == View.VISIBLE) rowHeight else 0
            val available = screenHeight - topMargin - bottom - fixedChrome - hintRow
            val maxRows = (available / rowHeight).coerceIn(3, Ranker.MAX_RESULTS)
            if (resultsView.maxVisible != maxRows) {
                resultsView.maxVisible = maxRows
                resultsView.bind(results, selected, iconCallback)
            }
            insets
        }
        // Fold/unfold, rotation and DeX resize change the root size without new insets: recompute then.
        windowRoot.addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) ViewCompat.requestApplyInsets(v)
        }
    }

    // ---- Search --------------------------------------------------------------------------------

    private fun onQueryChanged(raw: String) {
        query = TextNormalizer.normalize(raw)
        selected = 0
        rerank()
    }

    private fun onIndexChanged() {
        rerank(keepSelection = true)
    }

    private fun rerank(keepSelection: Boolean = false) {
        Trace.beginSection("ql.rank")
        val entries = index.awaitSnapshot()
        Ranker.rank(entries, query, System.currentTimeMillis(), results)
        Trace.endSection()

        selected = if (!keepSelection || results.isEmpty()) 0 else selected.coerceIn(0, results.size - 1)

        Trace.beginSection("ql.bind")
        resultsView.bind(results, selected, iconCallback)
        emptyView.visibility = if (results.isEmpty() && query.isNotEmpty()) View.VISIBLE else View.GONE
        updateUsageHint()
        Trace.endSection()
    }

    private fun moveSelection(delta: Int) {
        if (results.isEmpty()) return
        val next = (selected + delta).coerceIn(0, minOf(results.size, resultsView.maxVisible) - 1)
        if (next == selected) return
        selected = next
        resultsView.setSelected(selected)
    }

    // ---- Keys ----------------------------------------------------------------------------------

    /** Returns true when the key was consumed. Call from the host's dispatchKeyEvent before super. */
    fun handleKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (down && event.repeatCount == 0) launchSelected()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (down) moveSelection(+1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (down) moveSelection(-1)
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                if (down) moveSelection(if (event.isShiftPressed) -1 else +1)
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                if (down) host.dismiss()
                return true
            }
            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_J -> if (event.isCtrlPressed) {
                if (down) moveSelection(+1)
                return true
            }
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_K -> if (event.isCtrlPressed) {
                if (down) moveSelection(-1)
                return true
            }
        }
        return false
    }

    // ---- Drag to split screen ------------------------------------------------------------------

    private companion object {
        const val PREF_USAGE_HINT_TAPPED = "usage_hint_tapped"
        const val PREF_SHORTCUT_HINT_TAPPED = "shortcut_hint_tapped"
    }

    private val dragWatchdog = Runnable { endDrag(dropped = false) }

    private fun startDrag(entry: AppEntry, row: View): Boolean {
        if (dragging || !AppDrag.canDrag(entry)) return false
        // Hiding the keyboard here must not be read as "user pressed Back" by the insets listener.
        imeWasVisible = false
        KeyboardUtil.hideIme(input)
        if (!AppDrag.start(row, entry)) {
            Log.w(QuickLaunchApp.TAG, "startDragAndDrop refused for ${entry.key}")
            return false
        }
        if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) Log.d(QuickLaunchApp.TAG, "drag started for ${entry.key}")
        dragging = true
        host.onDragStarted()
        // Safety net: if the system never reports the end of the drag, never leave an invisible window behind.
        windowRoot.postDelayed(dragWatchdog, 15_000L)
        return true
    }

    private fun endDrag(dropped: Boolean) {
        if (!dragging) return
        dragging = false
        windowRoot.removeCallbacks(dragWatchdog)
        if (dropped) host.dismiss() else host.onDragCancelled()
    }

    // ---- Launch --------------------------------------------------------------------------------

    private fun launchSelected() {
        val entry = results.getOrNull(selected) ?: return
        launch(entry)
    }

    private fun launch(entry: AppEntry) {
        if (launched) return
        launched = true
        val t0 = SystemClock.elapsedRealtimeNanos()
        when (launcher.launch(entry, null)) {
            LaunchResult.OK -> {
                index.recordLaunch(entry)
                if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) {
                    Log.d(QuickLaunchApp.TAG, "launch ${entry.component.flattenToShortString()} in ${(SystemClock.elapsedRealtimeNanos() - t0) / 1000} us")
                }
                host.dismiss()
            }
            LaunchResult.PAUSED -> {
                launched = false
                Toast.makeText(app, R.string.error_work_paused, Toast.LENGTH_SHORT).show()
            }
            LaunchResult.NOT_AVAILABLE -> {
                launched = false
                Toast.makeText(app, R.string.error_not_available, Toast.LENGTH_SHORT).show()
                index.removeEntry(entry)
            }
        }
    }
}
