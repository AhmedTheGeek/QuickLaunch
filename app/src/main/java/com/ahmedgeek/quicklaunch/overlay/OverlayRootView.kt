package com.ahmedgeek.quicklaunch.overlay

import android.content.Context
import android.view.KeyEvent
import android.widget.FrameLayout

/** Root of the overlay window: routes keys to the panel and reports focus loss so the overlay can dismiss. */
class OverlayRootView(context: Context) : FrameLayout(context) {
    var onKey: ((KeyEvent) -> Boolean)? = null
    var onFocusLost: (() -> Unit)? = null
    var onFocusGained: (() -> Unit)? = null
    private var hadFocus = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (onKey?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    val hasHadFocus: Boolean get() = hadFocus

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (android.util.Log.isLoggable("QL", android.util.Log.DEBUG)) android.util.Log.d("QL", "overlay window focus=$hasWindowFocus")
        if (hasWindowFocus) {
            hadFocus = true
            onFocusGained?.invoke()
        } else if (hadFocus) {
            // Home pressed, notification shade, screen off, another window on top: never linger.
            onFocusLost?.invoke()
        }
    }
}
