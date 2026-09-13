package com.ahmedgeek.quicklaunch.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry

/** View holder over row_result.xml. Binding does only setText/setImageBitmap; never decodes or draws. */
class ResultRow(val view: View, private val placeholder: Drawable) {
    private val icon: ImageView = view.findViewById(R.id.icon)
    private val label: TextView = view.findViewById(R.id.label)
    private val badge: TextView = view.findViewById(R.id.badge)
    private val enter: View = view.findViewById(R.id.enter)

    var entry: AppEntry? = null
        private set

    fun bind(e: AppEntry, bitmap: Bitmap?) {
        entry = e
        view.tag = e.key
        label.text = e.label
        label.alpha = if (e.paused) 0.45f else 1f
        if (e.isWork) {
            badge.text = if (e.paused) "work · paused" else "work"
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
        setIcon(bitmap)
        view.visibility = View.VISIBLE
    }

    fun setIcon(bitmap: Bitmap?) {
        if (bitmap != null) icon.setImageBitmap(bitmap) else icon.setImageDrawable(placeholder)
        icon.alpha = if (entry?.paused == true) 0.45f else 1f
    }

    fun hide() {
        entry = null
        view.tag = null
        view.visibility = View.GONE
        view.isSelected = false
        enter.visibility = View.INVISIBLE
    }

    fun setSelected(selected: Boolean) {
        view.isSelected = selected
        enter.visibility = if (selected) View.VISIBLE else View.INVISIBLE
    }
}
