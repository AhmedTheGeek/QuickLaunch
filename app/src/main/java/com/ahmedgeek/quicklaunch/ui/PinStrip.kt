package com.ahmedgeek.quicklaunch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry

/**
 * Pinned apps as one row of icons (the "Compact pinned apps" setting), so any number of pins costs a
 * single row of height. More pins than fit scroll sideways, with faded edges hinting at the rest.
 *
 * Touch mirrors the result rows: tap launches, long press opens the row menu, and sliding on from the
 * long press starts the split-screen drag. Cells are reused by position, like [ResultsView]'s rows.
 */
class PinStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    var iconLoader: IconLoader? = null
    var onClick: ((AppEntry) -> Unit)? = null
    var onLongPress: ((AppEntry, View) -> Unit)? = null
    var onDrag: ((AppEntry, View) -> Boolean)? = null

    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val cells = ArrayList<ImageView>()
    private val entries = ArrayList<AppEntry>()
    private val placeholder = ContextCompat.getDrawable(context, R.drawable.bg_icon_placeholder)!!
    private val cellSize = resources.getDimensionPixelSize(R.dimen.row_height)
    private val iconPad = (cellSize - resources.getDimensionPixelSize(R.dimen.icon_size)) / 2
    private var selectedIndex = -1

    val count: Int get() = entries.size

    init {
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(cellSize / 2)
        overScrollMode = OVER_SCROLL_NEVER
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, cellSize))
    }

    fun entryAt(i: Int): AppEntry? = entries.getOrNull(i)

    fun bind(pins: List<AppEntry>, selected: Int, onIcon: (String, Bitmap) -> Unit) {
        entries.clear()
        entries.addAll(pins)
        while (cells.size < pins.size) cells.add(newCell())
        for (i in cells.indices) {
            val cell = cells[i]
            if (i < pins.size) {
                val e = pins[i]
                cell.tag = e.key
                cell.contentDescription = e.label
                val cached = iconLoader?.peek(e.key)
                if (cached != null) cell.setImageBitmap(cached) else {
                    cell.setImageDrawable(placeholder)
                    iconLoader?.request(e, onIcon)
                }
                cell.alpha = if (e.paused) 0.45f else 1f
                cell.visibility = VISIBLE
            } else {
                cell.tag = null
                cell.visibility = GONE
            }
        }
        selectedIndex = -1
        setSelected(selected)
    }

    /** -1 clears the highlight. Keeps the selected icon scrolled into view. */
    fun setSelected(index: Int) {
        if (selectedIndex in cells.indices) cells[selectedIndex].isSelected = false
        selectedIndex = if (index in entries.indices) index else -1
        if (selectedIndex < 0) return
        val cell = cells[selectedIndex]
        cell.isSelected = true
        post {
            val left = cell.left - cellSize / 2
            val right = cell.right + cellSize / 2 - width
            when {
                left < scrollX -> smoothScrollTo(maxOf(0, left), 0)
                right > scrollX -> smoothScrollTo(right, 0)
            }
        }
    }

    fun onIconLoaded(key: String, bitmap: Bitmap) {
        for (c in cells) if (c.tag == key) c.setImageBitmap(bitmap)
    }

    private fun newCell(): ImageView {
        val cell = ImageView(context)
        // R.id.icon so the drag shadow finds the icon the same way it does on a result row.
        cell.id = R.id.icon
        cell.background = ContextCompat.getDrawable(context, R.drawable.bg_row)
        cell.setPadding(iconPad, iconPad, iconPad, iconPad)
        cell.scaleType = ImageView.ScaleType.FIT_CENTER
        cell.isClickable = true
        cell.setOnClickListener { entries.getOrNull(cells.indexOf(cell))?.let { onClick?.invoke(it) } }
        installLongPress(cell)
        row.addView(cell, LinearLayout.LayoutParams(cellSize, cellSize))
        return cell
    }

    /** Same gesture as [ResultsView]: the long press opens the menu, sliding on from it starts a drag. */
    @SuppressLint("ClickableViewAccessibility")
    private fun installLongPress(cell: View) {
        var armed = false
        var downX = 0f
        var downY = 0f
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        cell.setOnLongClickListener {
            val e = entries.getOrNull(cells.indexOf(cell)) ?: return@setOnLongClickListener false
            armed = true
            parent?.requestDisallowInterceptTouchEvent(true)
            requestDisallowInterceptTouchEvent(true)
            onLongPress?.invoke(e, cell)
            true
        }
        cell.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    armed = false
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_MOVE -> if (armed && (Math.abs(ev.x - downX) > slop || Math.abs(ev.y - downY) > slop)) {
                    armed = false
                    entries.getOrNull(cells.indexOf(cell))?.let { e -> onDrag?.invoke(e, cell) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> armed = false
            }
            false
        }
    }
}
