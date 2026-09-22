package com.ahmedgeek.quicklaunch.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView

/**
 * Scrolls the result rows when they don't all fit above the keyboard, with a fading bottom edge as the
 * hint that there is more. As tall as its content up to [maxHeight].
 *
 * Touches: a quick vertical swipe scrolls; a long press on a row asks its parents not to intercept, so
 * sliding on from it still becomes the row's drag (split screen, or a file into another app).
 */
class ResultsScroll @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {

    var maxHeight: Int = Int.MAX_VALUE
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private var touching = false

    init {
        isVerticalFadingEdgeEnabled = true
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val size = MeasureSpec.getSize(heightMeasureSpec)
        val limit = if (mode == MeasureSpec.UNSPECIFIED) maxHeight else minOf(size, maxHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> touching = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * The scroll bar only shows once the list is actually scrolled: Android also flashes it when the view
     * appears or its content changes, which here is every keystroke.
     */
    override fun awakenScrollBars(startDelay: Int, invalidate: Boolean): Boolean =
        if (touching || scrollY != 0) super.awakenScrollBars(startDelay, invalidate) else false

    /** Scroll just enough to show [row]; the first row goes all the way up so a section header shows too. */
    fun reveal(row: View, first: Boolean) {
        post {
            if (first) {
                smoothScrollTo(0, 0)
                return@post
            }
            val top = row.top + (row.parent as View).top
            val bottom = top + row.height
            when {
                top < scrollY -> smoothScrollTo(0, top)
                bottom > scrollY + height -> smoothScrollTo(0, bottom - height)
            }
        }
    }
}
