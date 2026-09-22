package com.ahmedgeek.quicklaunch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry
import com.ahmedgeek.quicklaunch.search.Ranker
import com.ahmedgeek.quicklaunch.suggest.Suggestion

/**
 * Fixed pool of pre-inflated rows. No adapter, no recycling, no animations: results are capped at
 * [Ranker.MAX_RESULTS] and always fit on screen, so toggling visibility is the cheapest possible list.
 *
 * Leading rows can hold suggestions (the clipboard link, ...) instead of apps; app results follow
 * them. Selection indices are over the combined list, so the host never has to know which rows are which.
 *
 * On the empty query, pinned apps form their own section: a "Pinned" header above them and a
 * hairline below, both plain child views that are re-inserted at the right child index only when
 * the boundary moves (pins change rarely; typing hides the section entirely).
 */
class ResultsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val rows: Array<ResultRow>
    private val pinnedHeader: TextView
    private val separator: View
    private var selectedIndex = -1
    /** Rows currently visible: suggestions plus bound app results. */
    private var boundCount = 0

    var iconLoader: IconLoader? = null
    var onRowClick: ((AppEntry) -> Unit)? = null
    var onSuggestionClick: ((Suggestion) -> Unit)? = null
    /** Long press fired, finger still down: show the row's context menu. */
    var onRowLongPress: ((AppEntry, View) -> Unit)? = null
    /** Moved after the long press while still held: return true if a drag was started for this entry. */
    var onRowDrag: ((AppEntry, View) -> Boolean)? = null
    /** Tap on a row's pin button; the view is passed for haptic feedback. */
    var onPinClick: ((AppEntry, android.view.View) -> Unit)? = null

    /** Rows that fit on screen; recomputed from window insets. */
    var maxVisible: Int = Ranker.MAX_RESULTS

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private fun moved(ev: MotionEvent, downX: Float, downY: Float): Boolean =
        Math.abs(ev.x - downX) > touchSlop || Math.abs(ev.y - downY) > touchSlop

    init {
        orientation = VERTICAL
        val inflater = LayoutInflater.from(context)
        val placeholder = ContextCompat.getDrawable(context, R.drawable.bg_icon_placeholder)!!
        // Suggestions and apps share these rows; together they never exceed maxVisible.
        rows = Array(Ranker.MAX_RESULTS) { i ->
            val v = inflater.inflate(R.layout.row_result, this, false)
            addView(v)
            val row = ResultRow(v, placeholder)
            v.setOnClickListener {
                row.entry?.let { e -> onRowClick?.invoke(e) }
                row.suggestion?.let { s -> onSuggestionClick?.invoke(s) }
            }
            installLongPress(v, row)
            // While clickable, the pin owns its touches, so a long press on it never arms the row.
            // It must not be long-clickable on its own: that would make the hidden pin eat taps too.
            row.pin.setOnClickListener { row.entry?.let { e -> onPinClick?.invoke(e, row.pin) } }
            row.hide()
            row
        }
        pinnedHeader = inflater.inflate(R.layout.section_header, this, false) as TextView
        pinnedHeader.setText(R.string.pinned_header)
        pinnedHeader.visibility = GONE
        addView(pinnedHeader, 0)
        separator = inflater.inflate(R.layout.section_separator, this, false)
        separator.visibility = GONE
        addView(separator, 1)
    }

    /**
     * The long press opens the context menu at once via [onRowLongPress], the way a home screen icon
     * does, and keeps the row armed while the finger is still down. Move past the touch slop from there
     * and the row starts a system drag via [onRowDrag] (the host closes the menu first); lift and the
     * menu simply stays open.
     *
     * The touch listener only watches for movement after the long press and always returns false, so
     * the view's own click, long-click and accessibility handling run untouched.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun installLongPress(v: View, row: ResultRow) {
        var armed = false
        var downX = 0f
        var downY = 0f
        v.setOnLongClickListener {
            val e = row.entry ?: return@setOnLongClickListener false
            armed = true
            v.parent?.requestDisallowInterceptTouchEvent(true)
            onRowLongPress?.invoke(e, v)
            true // the view gives the long-press haptic itself when the listener consumes it
        }
        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    armed = false
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_MOVE -> if (armed && moved(ev, downX, downY)) {
                    armed = false
                    row.entry?.let { e -> onRowDrag?.invoke(e, v) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> armed = false
            }
            false
        }
    }

    /**
     * @param suggestions rows shown before the apps
     * @param pinned how many leading [results] form the pinned section; 0 hides the section
     * @param selected index over the combined list (suggestions first)
     */
    fun bind(suggestions: List<Suggestion>, results: List<AppEntry>, pinned: Int, selected: Int, onIcon: (String, Bitmap) -> Unit) {
        val loader = iconLoader
        val offset = minOf(suggestions.size, maxVisible)
        // Rows are reused by position: clear the old highlight before it lands on a different app.
        if (selectedIndex in rows.indices) rows[selectedIndex].setSelected(false)
        val count = minOf(results.size, maxVisible - offset)
        for (i in rows.indices) {
            val row = rows[i]
            if (i < offset) {
                val s = suggestions[i]
                val cached = loader?.peek(s.key)
                row.bindSuggestion(s, cached)
                if (cached == null && s.handlerUrl != null) loader?.requestLinkIcon(s.handlerUrl, s.key, onIcon)
            } else if (i - offset < count) {
                val e = results[i - offset]
                val cached = loader?.peek(e.key)
                row.bind(e, cached)
                if (cached == null) loader?.request(e, onIcon)
            } else {
                row.hide()
            }
        }
        boundCount = offset + count
        placeSection(offset, minOf(pinned, count), count)
        selectedIndex = -1
        setSelected(selected)
    }

    /** Header before the first pinned row, hairline before the first suggestion after them. */
    private fun placeSection(offset: Int, pinned: Int, count: Int) {
        if (pinned == 0) {
            pinnedHeader.visibility = GONE
            separator.visibility = GONE
            return
        }
        placeBefore(pinnedHeader, rows[offset].view)
        pinnedHeader.visibility = VISIBLE
        if (pinned < count) {
            placeBefore(separator, rows[offset + pinned].view)
            separator.visibility = VISIBLE
        } else {
            separator.visibility = GONE
        }
    }

    /** Move [view] directly before [anchor] in the child order, only when it is not already there. */
    private fun placeBefore(view: View, anchor: View) {
        val anchorIndex = indexOfChild(anchor)
        if (indexOfChild(view) == anchorIndex - 1) return
        removeView(view)
        addView(view, indexOfChild(anchor))
    }

    fun setSelected(index: Int) {
        if (index == selectedIndex) return
        if (selectedIndex in rows.indices) rows[selectedIndex].setSelected(false)
        selectedIndex = index
        if (index in 0 until boundCount) rows[index].setSelected(true)
    }

    /** Called on main when an icon finishes loading; applies only if that row still shows the same app. */
    fun onIconLoaded(key: String, bitmap: Bitmap) {
        for (row in rows) {
            if (row.view.tag == key) {
                row.setIcon(bitmap)
                return
            }
        }
    }
}
