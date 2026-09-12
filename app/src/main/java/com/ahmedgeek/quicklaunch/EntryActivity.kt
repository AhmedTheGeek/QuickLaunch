package com.ahmedgeek.quicklaunch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * The launcher-visible entry point (what One Hand Operation+ or a shortcut starts). Theme.NoDisplay: it
 * never draws and finishes inside onCreate, so WM has nothing to animate or wait for.
 *
 * With the overlay permission it shows the instant overlay window; otherwise, or if the foreground app
 * hides non-system overlays (Settings, secure screens), the activity host is opened instead.
 */
class EntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) Log.d(QuickLaunchApp.TAG, "entry.onCreate")
        val app = QuickLaunchApp.get(this)
        if (app.overlay.canShow()) {
            app.overlay.show { shown -> if (!shown) openActivityHost(app) }
        } else {
            openActivityHost(this)
        }
        finish()
    }

    override fun onDestroy() {
        if (Log.isLoggable(QuickLaunchApp.TAG, Log.DEBUG)) Log.d(QuickLaunchApp.TAG, "entry.onDestroy")
        super.onDestroy()
    }

    companion object {
        /** Works from the app context too: our overlay surface still counts as a visible window for the launch check. */
        fun openActivityHost(context: Context) {
            try {
                context.startActivity(
                    Intent(context, LaunchActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            } catch (e: RuntimeException) {
                Log.w(QuickLaunchApp.TAG, "activity host start failed", e)
            }
        }
    }
}
