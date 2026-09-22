package com.ahmedgeek.quicklaunch.suggest

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R

/**
 * A row that is not an app: the clipboard link, a calculator answer, a web search, a system setting.
 * Suggestions sit above the app results, or below them with [belowApps], and are built per query.
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
    /** Shown after the app results instead of before them: a weaker match than the apps. */
    @JvmField val belowApps: Boolean = false,
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

        fun copy(context: Context, text: String): Boolean {
            val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
            cm.setPrimaryClip(ClipData.newPlainText(text, text))
            // Android 13+ confirms clipboard writes itself.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            return true
        }
    }
}
