package com.ahmedgeek.quicklaunch.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry
import com.ahmedgeek.quicklaunch.search.Ranker

/**
 * Fixed pool of pre-inflated rows. No adapter, no recycling, no animations: results are capped at
 * [Ranker.MAX_RESULTS] and always fit on screen, so toggling visibility is the cheapest possible list.
 */
class ResultsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val rows: Array<ResultRow>
    private var selectedIndex = -1
    private var bound: List<AppEntry> = emptyList()

    var iconLoader: IconLoader? = null
    var onRowClick: ((AppEntry) -> Unit)? = null
    /** Long-press: return true if a drag was started for this entry. */
    var onRowLongPress: ((AppEntry, android.view.View) -> Boolean)? = null

    /** Rows that fit on screen; recomputed from window insets. */
    var maxVisible: Int = Ranker.MAX_RESULTS

    init {
        orientation = VERTICAL
        val inflater = LayoutInflater.from(context)
        val placeholder = ContextCompat.getDrawable(context, R.drawable.bg_icon_placeholder)!!
        rows = Array(Ranker.MAX_RESULTS) { i ->
            val v = inflater.inflate(R.layout.row_result, this, false)
            addView(v)
            val row = ResultRow(v, placeholder)
            v.setOnClickListener { row.entry?.let { e -> onRowClick?.invoke(e) } }
            v.setOnLongClickListener { row.entry?.let { e -> onRowLongPress?.invoke(e, v) } ?: false }
            row.hide()
            row
        }
    }

    fun bind(results: List<AppEntry>, selected: Int, onIcon: (String, Bitmap) -> Unit) {
        bound = results
        val loader = iconLoader
        val count = minOf(results.size, maxVisible)
        for (i in rows.indices) {
            val row = rows[i]
            if (i < count) {
                val e = results[i]
                val cached = loader?.peek(e.key)
                row.bind(e, cached)
                if (cached == null) loader?.request(e, onIcon)
            } else {
                row.hide()
            }
        }
        selectedIndex = -1
        setSelected(selected)
    }

    fun setSelected(index: Int) {
        if (index == selectedIndex) return
        if (selectedIndex in rows.indices) rows[selectedIndex].setSelected(false)
        selectedIndex = index
        if (index in 0 until minOf(bound.size, maxVisible)) rows[index].setSelected(true)
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
