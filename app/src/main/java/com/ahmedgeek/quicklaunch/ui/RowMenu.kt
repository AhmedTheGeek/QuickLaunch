package com.ahmedgeek.quicklaunch.ui

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry

/**
 * Long-press menu for a result row, drawn inside the panel's own window.
 *
 * Not a PopupWindow: a focusable popup takes window focus from the overlay, and the overlay treats
 * focus loss as "something else is on top, go away". Instead a full-size transparent catcher is added
 * over the whole root and the menu on top of it, so the first tap anywhere else only closes the menu,
 * never launches a row or dismisses the panel. Both views are created once and re-attached per show.
 */
class RowMenu(private val root: FrameLayout) {
    var onAppInfo: ((AppEntry) -> Unit)? = null
    var onAddToHome: ((AppEntry) -> Unit)? = null
    var onTogglePin: ((AppEntry) -> Unit)? = null
    /** Whether "Add to Home screen" applies to this entry; the item hides otherwise. */
    var canAddToHome: ((AppEntry) -> Boolean)? = null

    private val catcher = View(root.context).apply {
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        isClickable = true
        // Just under the menu but above the card: touch order follows elevation, and this has no outline, so no shadow.
        elevation = root.resources.getDimension(R.dimen.menu_elevation) - 1f
        setOnClickListener { hide() }
    }
    // Absolute gravity on purpose: the menu is placed by window coordinates, which are already resolved for RTL.
    @SuppressLint("RtlHardcoded")
    private val menu: View = LayoutInflater.from(root.context).inflate(R.layout.menu_row, root, false).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT,
        )
    }
    private val title: TextView = menu.findViewById(R.id.menu_title)
    private val appInfo: TextView = menu.findViewById(R.id.menu_app_info)
    private val addHome: TextView = menu.findViewById(R.id.menu_add_home)
    private val pin: TextView = menu.findViewById(R.id.menu_pin)
    private val gap = root.resources.getDimensionPixelSize(R.dimen.menu_gap)

    private var entry: AppEntry? = null

    val isShowing: Boolean get() = entry != null

    init {
        appInfo.setOnClickListener { entry?.let { e -> hide(); onAppInfo?.invoke(e) } }
        addHome.setOnClickListener { entry?.let { e -> hide(); onAddToHome?.invoke(e) } }
        pin.setOnClickListener { entry?.let { e -> hide(); onTogglePin?.invoke(e) } }
    }

    /** Show the menu for [e], hanging off [anchor] (the row): below it when it fits, above it otherwise. */
    fun show(e: AppEntry, anchor: View) {
        if (entry != null) hide()
        entry = e
        title.text = e.label
        addHome.visibility = if (canAddToHome?.invoke(e) == true) View.VISIBLE else View.GONE
        val pinned = e.pinOrder >= 0
        pin.setText(if (pinned) R.string.unpin else R.string.pin)
        pin.setCompoundDrawablesRelativeWithIntrinsicBounds(if (pinned) R.drawable.ic_pin else R.drawable.ic_pin_outline, 0, 0, 0)
        root.addView(catcher)
        root.addView(menu)

        // Position relative to the root's content box, where FrameLayout lays out a top|left child.
        val rootPos = IntArray(2).also { root.getLocationInWindow(it) }
        val anchorPos = IntArray(2).also { anchor.getLocationInWindow(it) }
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        menu.measure(spec, spec)
        val w = menu.measuredWidth
        val h = menu.measuredHeight
        val contentLeft = root.paddingLeft
        val contentRight = root.width - root.paddingRight
        val contentBottom = root.height - root.paddingBottom

        val anchorX = anchorPos[0] - rootPos[0]
        val anchorY = anchorPos[1] - rootPos[1]
        // Tucked under the icon end of the row, kept inside the content box.
        val x = (anchorX + anchor.paddingLeft).coerceIn(contentLeft, maxOf(contentLeft, contentRight - w))
        val below = anchorY + anchor.height + gap
        val y = if (below + h <= contentBottom) below else maxOf(root.paddingTop, anchorY - gap - h)
        menu.translationX = (x - contentLeft).toFloat()
        menu.translationY = (y - root.paddingTop).toFloat()
    }

    fun hide() {
        if (entry == null) return
        entry = null
        root.removeView(menu)
        root.removeView(catcher)
    }
}
