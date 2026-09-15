package com.ahmedgeek.quicklaunch.shortcut

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R

/**
 * Prominent disclosure for the AccessibilityService API (Play User Data policy). Shown in the normal
 * flow, before the user is sent to Settings: it states what the service is used for, what it can and
 * cannot see, and requires an affirmative tap to continue. Hosted in its own activity so the panel can
 * open it from both the overlay window and the fallback activity.
 */
class ShortcutDisclosureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.45f }
        setContentView(R.layout.activity_shortcut_disclosure)
        findViewById<View>(R.id.root).setOnClickListener { finish() }
        findViewById<View>(R.id.card).setOnClickListener { /* consume */ }
        findViewById<View>(R.id.disclosure_decline).setOnClickListener { finish() }
        findViewById<View>(R.id.disclosure_accept).setOnClickListener { accept() }
    }

    /** The user agreed: open the system Accessibility settings where the service is enabled. */
    private fun accept() {
        if (sideloaded()) {
            // Sideloaded apps hit Android's "Restricted setting" block on the accessibility toggle.
            Toast.makeText(this, R.string.shortcut_restricted_tip, Toast.LENGTH_LONG).show()
        }
        try {
            startActivity(KeyboardShortcutService.settingsIntent())
        } catch (e: RuntimeException) {
            Log.w(QuickLaunchApp.TAG, "accessibility settings unavailable", e)
        }
        finish()
    }

    /**
     * Android 13+ applies "Restricted setting" only to session installs from non-store apps (a browser, a
     * file manager). Play and adb installs are exempt, so the tip is noise for them.
     */
    private fun sideloaded(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val installer = try {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } catch (e: Exception) {
            null
        }
        return installer != null && installer != PLAY_STORE
    }

    private companion object {
        const val PLAY_STORE = "com.android.vending"
    }
}
