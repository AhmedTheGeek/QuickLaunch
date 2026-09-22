package com.ahmedgeek.quicklaunch.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry
import com.ahmedgeek.quicklaunch.suggest.Suggestion

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
    /** This row's own copy of the placeholder, as the glyph's tile; the shared one must never be tinted. */
    private val tile: Drawable = placeholder.constantState?.newDrawable(view.resources)?.mutate() ?: placeholder

    var entry: AppEntry? = null
        private set

    /** Set when this row shows a suggestion rather than an app. */
    var suggestion: Suggestion? = null
        private set

    fun bind(e: AppEntry, bitmap: Bitmap?) {
        entry = e
        suggestion = null
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
     * The pin button lives on the selected app row only. It is never hidden via visibility, so the
     * layout is stable; alpha and clickability flip together, and the drawable swap is draw-only.
     */
    private fun applyPin() {
        val e = entry
        val show = e != null && selected
        pin.alpha = if (show) 1f else 0f
        pin.isClickable = show // idle rows: taps fall through to the row and launch it
        if (e == null) return
        val pinned = e.pinOrder >= 0
        pin.setImageDrawable(if (pinned) pinFilled else pinOutline)
        pin.contentDescription = view.resources.getText(if (pinned) R.string.unpin else R.string.pin)
    }

    fun setIcon(bitmap: Bitmap?) {
        val s = suggestion
        when {
            bitmap != null -> {
                setGlyphMode(false)
                icon.setImageBitmap(bitmap)
            }
            s != null -> {
                // Image first: the tint lands on whatever drawable is showing, never on the shared placeholder.
                icon.setImageResource(s.glyph)
                setGlyphMode(true)
            }
            else -> {
                setGlyphMode(false)
                icon.setImageDrawable(placeholder)
            }
        }
        icon.alpha = if (entry?.paused == true) 0.45f else 1f
    }

    /**
     * Suggestion row: the handler app's icon when known (the default browser for a link, usually),
     * otherwise its tinted glyph in a placeholder tile.
     */
    fun bindSuggestion(s: Suggestion, bitmap: Bitmap?) {
        entry = null
        suggestion = s
        view.tag = s.key
        setIcon(bitmap)
        label.text = s.title
        label.alpha = 1f
        if (s.badge != null) {
            badge.text = s.badge
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
        applyPin()
        view.visibility = View.VISIBLE
    }

    /** App icons are full-bleed bitmaps; glyphs sit tinted inside the placeholder tile like the setup rows. */
    private fun setGlyphMode(glyph: Boolean) {
        if (glyph) {
            icon.background = tile
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
        suggestion = null
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
}
