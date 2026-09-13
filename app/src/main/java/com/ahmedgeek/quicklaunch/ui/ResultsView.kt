package com.ahmedgeek.quicklaunch.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry
import com.ahmedgeek.quicklaunch.search.Ranker

/**
 * Fixed pool of pre-inflated rows. No adapter, no recycling, no animations: results are capped at
 * [Ranker.MAX_RESULTS] and always fit on screen, so toggling visibility is the cheapest possible list.
 *
 * Row 0 can hold a clipboard link instead of an app; app results then start at row 1. Selection
 * indices are over the combined list, so the host never has to know which rows are which.
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
    /** Rows currently visible: the link row (if any) plus bound app results. */
    private var boundCount = 0

    var iconLoader: IconLoader? = null
    var onRowClick: ((AppEntry) -> Unit)? = null
    var onLinkClick: ((String) -> Unit)? = null
    /** Long-press: return true if a drag was started for this entry. */
    var onRowLongPress: ((AppEntry, android.view.View) -> Boolean)? = null
    /** Tap on a row's pin button; the view is passed for haptic feedback. */
    var onPinClick: ((AppEntry, android.view.View) -> Unit)? = null

    /** Rows that fit on screen; recomputed from window insets. */
    var maxVisible: Int = Ranker.MAX_RESULTS

    init {
        orientation = VERTICAL
        val inflater = LayoutInflater.from(context)
        val placeholder = ContextCompat.getDrawable(context, R.drawable.bg_icon_placeholder)!!
        // One spare row so a clipboard link never displaces the full set of app results.
        rows = Array(Ranker.MAX_RESULTS + 1) { i ->
            val v = inflater.inflate(R.layout.row_result, this, false)
            addView(v)
            val row = ResultRow(v, placeholder)
            v.setOnClickListener {
                row.entry?.let { e -> onRowClick?.invoke(e) }
                row.link?.let { url -> onLinkClick?.invoke(url) }
            }
            v.setOnLongClickListener { row.entry?.let { e -> onRowLongPress?.invoke(e, v) } ?: false }
            // While clickable, the pin owns its touches, so a long press on it never starts a drag.
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
     * @param link clipboard URL shown as the first row, or null
     * @param pinned how many leading [results] form the pinned section; 0 hides the section
     * @param selected index over the combined list (link row first when present)
     */
    fun bind(link: String?, results: List<AppEntry>, pinned: Int, selected: Int, onIcon: (String, Bitmap) -> Unit) {
        val loader = iconLoader
        val offset = if (link != null) 1 else 0
        // Rows are reused by position: clear the old highlight before it lands on a different app.
        if (selectedIndex in rows.indices) rows[selectedIndex].setSelected(false)
        if (link != null) {
            val key = ResultRow.linkKey(link)
            val cached = loader?.peek(key)
            rows[0].bindLink(link, cached)
            if (cached == null) loader?.requestLinkIcon(link, key, onIcon)
        }
        val count = minOf(results.size, maxVisible - offset)
        for (i in 0 until Ranker.MAX_RESULTS) {
            val row = rows[i + offset]
            if (i < count) {
                val e = results[i]
                val cached = loader?.peek(e.key)
                row.bind(e, cached)
                if (cached == null) loader?.request(e, onIcon)
            } else {
                row.hide()
            }
        }
        if (link == null) rows[Ranker.MAX_RESULTS].hide()
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
