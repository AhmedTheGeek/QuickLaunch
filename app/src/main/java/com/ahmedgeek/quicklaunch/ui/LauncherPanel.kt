package com.ahmedgeek.quicklaunch.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.os.Trace
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.clipboard.ClipboardLinkSource
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
    private val clipboard = app.clipboardLinks
    private val res = windowRoot.resources

    private val scrim: View = windowRoot.findViewById(R.id.root)
    private val card: View = windowRoot.findViewById(R.id.card)
    val input: EditText = windowRoot.findViewById(R.id.input)
    private val resultsView: ResultsView = windowRoot.findViewById(R.id.results)
    private val emptyView: TextView = windowRoot.findViewById(R.id.empty)
    private val footer: View = windowRoot.findViewById(R.id.footer)
    private val footerMessage: TextView = windowRoot.findViewById(R.id.hint_bar)
    private val footerAction: TextView = windowRoot.findViewById(R.id.footer_action)
    private val footerKeys: View = windowRoot.findViewById(R.id.footer_keys)
    private var footerMessageSet = false
    private val setupHeader: View = windowRoot.findViewById(R.id.setup_header)
    private val usageHint = SetupRow(
        windowRoot.findViewById(R.id.usage_hint),
        R.drawable.ic_star, R.string.setup_usage_title, R.string.setup_usage_subtitle,
    )
    private val shortcutHint = SetupRow(
        windowRoot.findViewById(R.id.shortcut_hint),
        R.drawable.ic_keyboard, R.string.setup_shortcut_title, R.string.setup_shortcut_subtitle,
    )
    private val prefs = app.getSharedPreferences("ql", android.content.Context.MODE_PRIVATE)

    private val results = ArrayList<AppEntry>(Ranker.MAX_RESULTS)
    /** URL on the clipboard when the panel opened, offered as the first row while the query is empty. */
    private var link: String? = null
    private var linkChecked = false
    private var query = ""
    /** Index over the combined list: the link row (when shown) is 0, app results follow. */
    private var selected = 0
    private var launched = false
    private var active = false
    private var dragging = false

    private val iconCallback: (String, Bitmap) -> Unit = { key, bitmap -> resultsView.onIconLoaded(key, bitmap) }

    init {
        resultsView.iconLoader = icons
        resultsView.onRowClick = { entry -> launch(entry) }
        resultsView.onLinkClick = { url -> openLink(url) }
        resultsView.onRowLongPress = { entry, row -> startDrag(entry, row) }
        resultsView.onPinClick = { entry, button -> togglePin(entry, button) }
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
        // Tapping a setup row opens the setting; the row stays until the feature is on or the user dismisses it.
        usageHint.onDismiss = {
            prefs.edit().putBoolean(PREF_USAGE_HINT_DISMISSED, true).apply()
            updateUsageHint()
        }
        usageHint.onClick = {
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
        shortcutHint.onDismiss = {
            prefs.edit().putBoolean(PREF_SHORTCUT_HINT_DISMISSED, true).apply()
            updateUsageHint()
        }
        shortcutHint.onClick = {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Sideloaded apps hit Android's "Restricted setting" block on the accessibility toggle.
                Toast.makeText(app, R.string.shortcut_restricted_tip, Toast.LENGTH_LONG).show()
            }
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
        val show = query.isEmpty() && !prefs.getBoolean(PREF_USAGE_HINT_DISMISSED, false) && !index.usagePermitted()
        val visibility = if (show) View.VISIBLE else View.GONE
        // Keyboard-first app: offer the Ctrl+Space setup until it is enabled or dismissed, keyboard attached or not.
        val showShortcut = query.isEmpty() &&
            !prefs.getBoolean(PREF_SHORTCUT_HINT_DISMISSED, false) &&
            !com.ahmedgeek.quicklaunch.shortcut.KeyboardShortcutService.isEnabled(app)
        val shortcutVisibility = if (showShortcut) View.VISIBLE else View.GONE
        val headerVisibility = if (show || showShortcut) View.VISIBLE else View.GONE
        if (usageHint.view.visibility != visibility || shortcutHint.view.visibility != shortcutVisibility) {
            usageHint.view.visibility = visibility
            shortcutHint.view.visibility = shortcutVisibility
            setupHeader.visibility = headerVisibility
            ViewCompat.requestApplyInsets(windowRoot)
        }
    }

    /** Footer keycap hints are only useful with a physical keyboard; the footer hides when it has nothing to say. */
    private fun updateFooter() {
        val keys = !footerMessageSet && KeyboardUtil.hasHardwareKeyboard(res.configuration)
        footerKeys.visibility = if (keys) View.VISIBLE else View.GONE
        footer.visibility = if (keys || footerMessageSet) View.VISIBLE else View.GONE
    }

    /** Replace the key hints with a message and an action chip (the fallback activity offers instant mode here). */
    fun showFooterAction(message: CharSequence, action: CharSequence, iconRes: Int, onClick: () -> Unit) {
        footerMessageSet = true
        footerMessage.text = message
        footerMessage.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
        footerMessage.compoundDrawableTintList = footerMessage.textColors
        footerMessage.visibility = View.VISIBLE
        footerAction.text = action
        footerAction.visibility = View.VISIBLE
        footer.setOnClickListener { onClick() }
        footerAction.setOnClickListener { onClick() }
        updateFooter()
    }

    /** View holder over row_setup.xml. */
    private class SetupRow(val view: View, iconRes: Int, titleRes: Int, subtitleRes: Int) {
        var onClick: (() -> Unit)? = null
        var onDismiss: (() -> Unit)? = null

        init {
            view.findViewById<ImageView>(R.id.setup_icon).setImageResource(iconRes)
            view.findViewById<TextView>(R.id.setup_title).setText(titleRes)
            view.findViewById<TextView>(R.id.setup_subtitle).setText(subtitleRes)
            view.setOnClickListener { onClick?.invoke() }
            view.findViewById<View>(R.id.setup_dismiss).setOnClickListener { onDismiss?.invoke() }
        }
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    /** Called every time the panel becomes visible. Renders cached results synchronously. */
    fun onShown() {
        active = true
        launched = false
        imeWasVisible = false
        link = null
        linkChecked = false
        index.listener = { onIndexChanged() }
        updateFooter()
        if (input.text.isNotEmpty()) input.setText("") else rerank()
        // Succeeds only if the window already has focus (a re-show); otherwise onWindowFocusGained retries.
        refreshLink()
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

    /** Android 10+ releases the clipboard only to the focused window; hosts call this when focus arrives. */
    fun onWindowFocusGained() {
        if (!active || linkChecked) return
        refreshLink()
    }

    // ---- Clipboard link -----------------------------------------------------------------------

    /** Reads the clipboard once per show. A dropped drag or a refocus never re-reads. */
    private fun refreshLink() {
        val found = clipboard.currentLink()
        if (found == null && !windowRoot.hasWindowFocus()) return // not yet allowed to read; try again on focus
        linkChecked = true
        if (found == link) return
        link = found
        if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) Log.d(QuickLaunchApp.TAG, "clipboard link=${found != null}")
        selected = 0
        resultsView.bind(visibleLink(), results, selected, iconCallback)
    }

    /** The link row only competes with the empty-query list; typing means the user wants an app. */
    private fun visibleLink(): String? = if (query.isEmpty()) link else null

    /** Rows the user can select: the link row plus app results. */
    private fun rowCount(): Int = (if (visibleLink() != null) 1 else 0) + results.size

    private fun openLink(url: String) {
        if (launched) return
        launched = true
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(intent)
            host.dismiss()
        } catch (e: ActivityNotFoundException) {
            launched = false
            Toast.makeText(app, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
        } catch (e: RuntimeException) {
            launched = false
            Log.w(QuickLaunchApp.TAG, "open link failed", e)
            Toast.makeText(app, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Insets --------------------------------------------------------------------------------

    private fun installInsetsHandling() {
        val topOffsetMin = res.getDimensionPixelSize(R.dimen.card_top_offset)
        val sideMargin = res.getDimensionPixelSize(R.dimen.card_margin_h)
        val maxWidth = res.getDimensionPixelSize(R.dimen.card_max_width)
        val rowHeight = res.getDimensionPixelSize(R.dimen.row_height)
        val headerHeight = res.getDimensionPixelSize(R.dimen.section_header_height)
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

            val hintRows = (if (usageHint.view.visibility == View.VISIBLE) 1 else 0) + (if (shortcutHint.view.visibility == View.VISIBLE) 1 else 0)
            val hintRow = hintRows * rowHeight + (if (hintRows > 0) headerHeight else 0)
            val available = screenHeight - topMargin - bottom - fixedChrome - hintRow
            val maxRows = (available / rowHeight).coerceIn(3, Ranker.MAX_RESULTS)
            if (resultsView.maxVisible != maxRows) {
                resultsView.maxVisible = maxRows
                resultsView.bind(visibleLink(), results, selected, iconCallback)
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

    /**
     * @param keepSelection keep the selected index where it is (clamped)
     * @param follow keep the selection on this entry wherever it lands, or row 0 if it fell off the list
     */
    private fun rerank(keepSelection: Boolean = false, follow: AppEntry? = null) {
        Trace.beginSection("ql.rank")
        val entries = index.awaitSnapshot()
        Ranker.rank(entries, query, System.currentTimeMillis(), results)
        Trace.endSection()

        val rows = rowCount()
        val followed = if (follow != null) results.indexOf(follow) else -1
        selected = when {
            rows == 0 -> 0
            followed >= 0 -> (followed + (if (visibleLink() != null) 1 else 0)).coerceAtMost(resultsView.maxVisible - 1)
            keepSelection -> selected.coerceIn(0, rows - 1)
            else -> 0
        }

        Trace.beginSection("ql.bind")
        resultsView.bind(visibleLink(), results, selected, iconCallback)
        emptyView.visibility = if (results.isEmpty() && query.isNotEmpty()) View.VISIBLE else View.GONE
        updateUsageHint()
        Trace.endSection()
    }

    private fun moveSelection(delta: Int) {
        val rows = rowCount()
        if (rows == 0) return
        val next = (selected + delta).coerceIn(0, minOf(rows, resultsView.maxVisible) - 1)
        if (next == selected) return
        selected = next
        resultsView.setSelected(selected)
    }

    // ---- Pins ----------------------------------------------------------------------------------

    /** The app entry at a combined-list row index, or null for the link row / out of range. */
    private fun entryAt(row: Int): AppEntry? {
        val offset = if (visibleLink() != null) 1 else 0
        return results.getOrNull(row - offset)
    }

    /**
     * Pin or unpin and re-rank so the row moves where it now belongs, keeping the selection on it.
     * [feedback] is the tapped button for touch; null from the keyboard.
     */
    private fun togglePin(entry: AppEntry, feedback: View?) {
        if (launched) return
        val pinned = index.togglePin(entry)
        feedback?.performHapticFeedback(
            if (pinned) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK,
        )
        if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) Log.d(QuickLaunchApp.TAG, "pin ${entry.key} -> $pinned")
        rerank(follow = entry)
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
            KeyEvent.KEYCODE_D -> if (event.isCtrlPressed) {
                if (down && event.repeatCount == 0) entryAt(selected)?.let { togglePin(it, null) }
                return true
            }
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> if (event.isCtrlPressed) {
                // Direct launch of row N. Pins keep the top rows stable, so this is a one-chord launch.
                if (down && event.repeatCount == 0) launchRow(event.keyCode - KeyEvent.KEYCODE_1)
                return true
            }
        }
        return false
    }

    // ---- Drag to split screen ------------------------------------------------------------------

    private companion object {
        // Stored under the old "tapped" names so users who acted on the previous hints are not asked again.
        const val PREF_USAGE_HINT_DISMISSED = "usage_hint_tapped"
        const val PREF_SHORTCUT_HINT_DISMISSED = "shortcut_hint_tapped"
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

    private fun launchSelected() = launchRow(selected)

    /** Launch whatever sits at a combined-list row index: the link row or an app. Ignores rows not on screen. */
    private fun launchRow(row: Int) {
        if (row < 0 || row >= minOf(rowCount(), resultsView.maxVisible)) return
        val url = visibleLink()
        if (url != null && row == 0) {
            openLink(url)
            return
        }
        launch(entryAt(row) ?: return)
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
