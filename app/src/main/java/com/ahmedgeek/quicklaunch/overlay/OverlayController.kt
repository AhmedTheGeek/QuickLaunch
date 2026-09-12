package com.ahmedgeek.quicklaunch.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ContextThemeWrapper
import android.view.WindowManager
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.ui.KeyboardUtil
import com.ahmedgeek.quicklaunch.ui.LauncherPanel
import java.util.function.Consumer

/**
 * Instant mode. Hosts [LauncherPanel] in a TYPE_APPLICATION_OVERLAY window instead of an activity.
 *
 * Why: an activity that opens in its own task always gets the system's ~330 ms translucent fade,
 * which the app cannot override. A plain window uses its own animation style, so it appears on the
 * very next frame. Requires the "Display over other apps" permission.
 */
class OverlayController(private val app: QuickLaunchApp) {

    private val wm = app.getSystemService(WindowManager::class.java)
    private var root: OverlayRootView? = null
    private var panel: LauncherPanel? = null
    private var blurListener: Consumer<Boolean>? = null

    val isShowing: Boolean get() = root != null

    /** The system sends this on Home, Recents and similar; an overlay window gets no focus loss for those. */
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) hide()
        }
    }
    private var receiverRegistered = false

    fun canShow(): Boolean = Settings.canDrawOverlays(app)

    /**
     * Main thread. Shows the overlay, or resets it to a fresh search if it is already up.
     * [onOutcome] is called once with true when the window is on screen and focused, or false if the
     * foreground app hides non-system overlays (Settings, secure screens): the overlay is gone by then
     * and the caller should fall back to the activity host.
     */
    fun show(onOutcome: (Boolean) -> Unit) {
        val existing = root
        if (existing != null) {
            if (existing.alpha == 0f) setPassThrough(false) // recover from an interrupted drag
            panel?.onShown()
            onOutcome(true)
            return
        }
        val themed: Context = ContextThemeWrapper(app, R.style.Theme_QuickLaunch)
        val newRoot = OverlayRootView(themed)
        LayoutInflater.from(themed).inflate(R.layout.activity_launch, newRoot, true)

        val host = object : LauncherPanel.Host {
            override fun dismiss() = hide()
            override fun onDragStarted() = setPassThrough(true)
            override fun onDragCancelled() = setPassThrough(false)
        }
        val newPanel = LauncherPanel(app, newRoot, host)
        newRoot.onKey = { newPanel.handleKey(it) }
        newRoot.onFocusLost = { if (newRoot.alpha > 0f) hide() }

        val lp = buildLayoutParams()
        val debug = Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)
        if (debug) {
            Log.d(QuickLaunchApp.TAG, "overlay addView")
            newRoot.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    newRoot.viewTreeObserver.removeOnPreDrawListener(this)
                    Log.d(QuickLaunchApp.TAG, "overlay first draw")
                    return true
                }
            })
        }
        try {
            wm.addView(newRoot, lp)
            if (debug) Log.d(QuickLaunchApp.TAG, "overlay addView done")
        } catch (e: RuntimeException) {
            Log.w(QuickLaunchApp.TAG, "overlay addView failed", e)
            return
        }
        root = newRoot
        panel = newPanel
        // Some apps (Settings, secure screens) set HIDE_NON_SYSTEM_OVERLAY_WINDOWS: our window then exists but is
        // never shown or focused. Focus is the only client-visible signal, and WM defers focus changes until the
        // pending transition ends, so the timeout has to be generous and counted from our first frame.
        var decided = false
        newRoot.onFocusGained = {
            if (!decided) {
                decided = true
                onOutcome(true)
            }
        }
        newRoot.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                newRoot.viewTreeObserver.removeOnPreDrawListener(this)
                newRoot.postDelayed({
                    if (!decided && root === newRoot) {
                        decided = true
                        Log.i(QuickLaunchApp.TAG, "overlay never focused: hidden by the foreground app, falling back")
                        hide()
                        onOutcome(false)
                    }
                }, FOCUS_TIMEOUT_MS)
                return true
            }
        })
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                app, closeReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
        newPanel.onShown()
        if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) {
            newRoot.post {
                Log.d(QuickLaunchApp.TAG, "overlay first frame ${android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()} ms after process start")
            }
        }

        // Request the keyboard once our first frame is committed, skipping its slide-in animation.
        newRoot.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                newRoot.viewTreeObserver.removeOnPreDrawListener(this)
                newRoot.post {
                    if (root === newRoot && !KeyboardUtil.hasHardwareKeyboard(app.resources.configuration)) {
                        KeyboardUtil.showImeInstantly(newPanel.input)
                    }
                }
                return true
            }
        })
    }

    fun hide() {
        val r = root ?: return
        root = null
        panel?.onHidden()
        panel = null
        if (receiverRegistered) {
            receiverRegistered = false
            try { app.unregisterReceiver(closeReceiver) } catch (_: IllegalArgumentException) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurListener?.let { wm.removeCrossWindowBlurEnabledListener(it) }
            blurListener = null
        }
        try {
            wm.removeViewImmediate(r)
        } catch (e: RuntimeException) {
            Log.w(QuickLaunchApp.TAG, "overlay removeView failed", e)
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        lp.title = "QuickLaunch"
        lp.gravity = Gravity.TOP or Gravity.START
        // Not STATE_VISIBLE: on Android 15+ the IME joins the pending window transition, and WM then
        // holds our first frame until the keyboard has drawn (150-500 ms). Request it after our frame instead.
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        lp.windowAnimations = R.style.NoWindowAnim
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lp.fitInsetsTypes = 0
        }
        var blur = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = app.resources.getDimensionPixelSize(R.dimen.blur_radius)
            blur = wm.isCrossWindowBlurEnabled
            val listener = Consumer<Boolean> { enabled -> applyDim(enabled) }
            blurListener = listener
            wm.addCrossWindowBlurEnabledListener(listener)
        }
        lp.dimAmount = dimFor(blur)
        return lp
    }

    /** During a drag the window must stay alive (it owns the drag) but let the drop reach windows beneath. */
    private fun setPassThrough(enabled: Boolean) {
        val r = root ?: return
        val lp = r.layoutParams as? WindowManager.LayoutParams ?: return
        if (enabled) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            r.alpha = 0f
        } else {
            lp.flags = lp.flags and (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).inv()
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            r.alpha = 1f
        }
        wm.updateViewLayout(r, lp)
        if (!enabled) panel?.input?.requestFocus()
    }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 900L
    }

    private fun dimFor(blurEnabled: Boolean) = if (blurEnabled) 0.15f else 0.45f

    private fun applyDim(blurEnabled: Boolean) {
        val r = root ?: return
        val lp = r.layoutParams as? WindowManager.LayoutParams ?: return
        lp.dimAmount = dimFor(blurEnabled)
        wm.updateViewLayout(r, lp)
    }
}
