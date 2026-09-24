package com.ahmedgeek.quicklaunch.suggest

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R

/**
 * A row that is not an app: the clipboard link today, later a calculator answer or a web search.
 * Suggestions sit above the app results and are built per query, so they stay small and cheap.
 */
class Suggestion(
    /** Row tag and icon cache key. App keys start with a user serial, so these start with a letter. */
    @JvmField val key: String,
    @JvmField val title: CharSequence,
    @JvmField val badge: CharSequence?,
    /** Tinted glyph shown when there is no handler icon. */
    @JvmField val glyph: Int,
    /** When set, the row shows the icon of the app that opens this URL instead of [glyph]. */
    @JvmField val handlerUrl: String?,
    /** Main thread. Returns true when the panel should close. */
    @JvmField val run: (Context) -> Boolean,
) {
    companion object {
        /** Opens [url] in whatever handles it. Toasts and returns false when nothing can. */
        fun openUrl(context: Context, url: String): Boolean {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
                false
            } catch (e: RuntimeException) {
                Log.w(QuickLaunchApp.TAG, "open url failed", e)
                Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
}
