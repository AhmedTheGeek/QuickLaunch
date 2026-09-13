package com.ahmedgeek.quicklaunch

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.ahmedgeek.quicklaunch.ui.KeyboardUtil
import com.ahmedgeek.quicklaunch.ui.LauncherPanel
import java.util.function.Consumer

/**
 * Fallback host for [LauncherPanel] when the overlay permission is not granted.
 * The system applies its own ~330 ms fade to this window; the overlay path avoids that.
 */
class LaunchActivity : Activity(), LauncherPanel.Host {

    private lateinit var panel: LauncherPanel
    private var imeShownOnce = false
    private var blurListener: Consumer<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        setContentView(R.layout.activity_launch)
        panel = LauncherPanel(QuickLaunchApp.get(this), findViewById(R.id.root), this)
        offerInstantMode()
        panel.onShown()
    }

    private fun setupWindow() {
        val w = window
        WindowCompat.setDecorFitsSystemWindows(w, false)
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyDim(windowManager.isCrossWindowBlurEnabled)
            val listener = Consumer<Boolean> { enabled -> applyDim(enabled) }
            blurListener = listener
            windowManager.addCrossWindowBlurEnabledListener(listener)
        } else {
            applyDim(blurEnabled = false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun applyDim(blurEnabled: Boolean) {
        val lp = window.attributes
        lp.dimAmount = if (blurEnabled) 0.15f else 0.45f
        window.attributes = lp
    }

    /** Running as an activity means the permission is missing: make the footer the way to grant it. */
    private fun offerInstantMode() {
        panel.showFooterAction(
            getString(R.string.instant_mode_message), getString(R.string.instant_mode_action), R.drawable.ic_bolt,
        ) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            dismiss()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        imeShownOnce = false
        panel.onShown()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) panel.onWindowFocusGained()
        if (hasFocus && !imeShownOnce) {
            imeShownOnce = true
            if (!KeyboardUtil.hasHardwareKeyboard(resources.configuration)) {
                KeyboardUtil.showImeInstantly(panel.input)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && findViewById<android.view.View>(R.id.root).alpha > 0f) dismiss()
    }

    override fun onDestroy() {
        panel.onHidden()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurListener?.let { windowManager.removeCrossWindowBlurEnabledListener(it) }
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (panel.handleKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onDragStarted() {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        findViewById<android.view.View>(R.id.root).alpha = 0f
    }

    override fun onDragCancelled() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        findViewById<android.view.View>(R.id.root).alpha = 1f
    }

    override fun dismiss() {
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
