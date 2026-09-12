package com.ahmedgeek.quicklaunch.shortcut

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.ahmedgeek.quicklaunch.EntryActivity
import com.ahmedgeek.quicklaunch.QuickLaunchApp

/**
 * Global Ctrl+Space. An accessibility service with key filtering is the only place an app can see a
 * key before the system does; the system otherwise consumes Ctrl+Space to switch keyboard language.
 * The service subscribes to no accessibility events and reads nothing from the screen.
 */
class KeyboardShortcutService : AccessibilityService() {

    private var swallowSpaceUp = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = 0
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 0
        }
        if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) Log.d(QuickLaunchApp.TAG, "shortcut service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) {
            Log.d(QuickLaunchApp.TAG, "key action=${event.action} code=${event.keyCode} meta=0x${Integer.toHexString(event.metaState)}")
        }
        if (event.keyCode != KeyEvent.KEYCODE_SPACE) return false
        val ctrlOnly = event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!ctrlOnly) return false
                swallowSpaceUp = true
                if (event.repeatCount == 0) trigger()
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (!swallowSpaceUp) return false
                swallowSpaceUp = false
                return true
            }
        }
        return false
    }

    private fun trigger() {
        val app = QuickLaunchApp.get(this)
        val overlay = app.overlay
        when {
            overlay.isShowing -> overlay.hide()          // Ctrl+Space again closes it
            overlay.canShow() -> overlay.show { shown -> if (!shown) EntryActivity.openActivityHost(app) }
            else -> EntryActivity.openActivityHost(app)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, KeyboardShortcutService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
