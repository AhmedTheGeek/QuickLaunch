package com.ahmedgeek.quicklaunch.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.clipboard.LinkDetector
import com.ahmedgeek.quicklaunch.index.AppEntry

/** View holder over row_result.xml. Binding does only setText/setImageBitmap; never decodes or draws. */
class ResultRow(val view: View, private val placeholder: Drawable) {
    private val icon: ImageView = view.findViewById(R.id.icon)
    private val label: TextView = view.findViewById(R.id.label)
    private val badge: TextView = view.findViewById(R.id.badge)
    private val enter: View = view.findViewById(R.id.enter)
    val pin: ImageView = view.findViewById(R.id.pin)
    private val pinFilled: Drawable = ContextCompat.getDrawable(view.context, R.drawable.ic_pin)!!
    private val pinOutline: Drawable = ContextCompat.getDrawable(view.context, R.drawable.ic_pin_outline)!!
    private var selected = false

    private val glyphPadding = (view.resources.displayMetrics.density * 9f + 0.5f).toInt()
    private val glyphTint = ColorStateList.valueOf(ContextCompat.getColor(view.context, R.color.text_secondary))

    var entry: AppEntry? = null
        private set

    /** The clipboard URL this row opens, when it is the link row rather than an app. */
    var link: String? = null
        private set

    fun bind(e: AppEntry, bitmap: Bitmap?) {
        entry = e
        link = null
        view.tag = e.key
        setGlyphMode(false)
        label.text = e.label
        label.alpha = if (e.paused) 0.45f else 1f
        if (e.isWork) {
            badge.text = if (e.paused) "work · paused" else "work"
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
        setIcon(bitmap)
        applyPin()
        view.visibility = View.VISIBLE
    }

    /**
     * The pin button is never hidden while an app is bound, so the touch target is stable; its look
     * tells the state. Alpha and drawable swaps are draw-only changes, no layout.
     */
    private fun applyPin() {
        val e = entry
        if (e == null) {
            pin.alpha = 0f
            pin.isClickable = false // taps fall through to the row (or nothing, for the link row)
            return
        }
        pin.isClickable = true
        val pinned = e.pinOrder >= 0
        pin.setImageDrawable(if (pinned) pinFilled else pinOutline)
        pin.alpha = if (pinned || selected) 1f else PIN_IDLE_ALPHA
        pin.contentDescription = view.resources.getText(if (pinned) R.string.unpin else R.string.pin)
    }

    fun setIcon(bitmap: Bitmap?) {
        when {
            bitmap != null -> {
                setGlyphMode(false)
                icon.setImageBitmap(bitmap)
            }
            link != null -> {
                setGlyphMode(true)
                icon.setImageResource(R.drawable.ic_link)
            }
            else -> icon.setImageDrawable(placeholder)
        }
        icon.alpha = if (entry?.paused == true) 0.45f else 1f
    }

    /**
     * Clipboard link row: the handler app's icon when known (the default browser, usually), otherwise
     * a tinted link glyph in a placeholder tile; the URL as label, "Open link" as badge.
     */
    fun bindLink(url: String, bitmap: Bitmap?) {
        entry = null
        link = url
        view.tag = linkKey(url)
        setIcon(bitmap)
        label.text = LinkDetector.display(url)
        label.alpha = 1f
        badge.setText(R.string.link_open)
        badge.visibility = View.VISIBLE
        applyPin()
        view.visibility = View.VISIBLE
    }

    /** App icons are full-bleed bitmaps; glyphs sit tinted inside the placeholder tile like the setup rows. */
    private fun setGlyphMode(glyph: Boolean) {
        if (glyph) {
            icon.background = placeholder
            icon.imageTintList = glyphTint
            icon.setPadding(glyphPadding, glyphPadding, glyphPadding, glyphPadding)
        } else if (icon.background != null) {
            icon.background = null
            icon.imageTintList = null
            icon.setPadding(0, 0, 0, 0)
        }
    }

    fun hide() {
        entry = null
        link = null
        view.tag = null
        view.visibility = View.GONE
        view.isSelected = false
        selected = false
        enter.visibility = View.INVISIBLE
        applyPin()
    }

    fun setSelected(selected: Boolean) {
        this.selected = selected
        view.isSelected = selected
        enter.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        applyPin()
    }

    companion object {
        /** Idle rows keep a faint pin so touch users can see the target exists without it shouting. */
        private const val PIN_IDLE_ALPHA = 0.35f

        /** Row tag and icon-cache key for a link. App keys start with a user serial, so no collision. */
        fun linkKey(url: String): String = "link|$url"
    }
}
