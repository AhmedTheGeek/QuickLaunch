package com.ahmedgeek.quicklaunch.launch

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.DisplayMetrics
import android.util.Log
import com.ahmedgeek.quicklaunch.Bg
import com.ahmedgeek.quicklaunch.index.AppEntry
import com.ahmedgeek.quicklaunch.index.AppIndex

/**
 * "Add to Home screen": asks the home app to pin a shortcut that opens [AppEntry] directly.
 *
 * The shortcut is published by Quick Launch (only the default home app may pin another app's own
 * shortcuts), but its intent targets the app's launcher activity, so tapping it never passes
 * through us. The home app shows its own confirmation. Personal profile only: a pinned shortcut runs
 * in our user, so a work-profile intent would not resolve.
 */
class HomeShortcuts(context: Context, private val index: AppIndex) {
    private val appContext = context.applicationContext
    private val shortcuts: ShortcutManager? = appContext.getSystemService(ShortcutManager::class.java)

    fun canAdd(entry: AppEntry): Boolean =
        !entry.isWork && shortcuts?.isRequestPinShortcutSupported == true

    /**
     * Renders the icon off the main thread (a binder call plus a rasterization), then requests the
     * pin. [onResult] is called on main with false when the app is gone or the request was refused.
     */
    fun add(entry: AppEntry, onResult: (Boolean) -> Unit) {
        val manager = shortcuts
        if (manager == null || !canAdd(entry)) {
            onResult(false)
            return
        }
        Bg.icons.execute {
            val ok = try {
                val icon = loadIcon(entry)
                if (icon == null) {
                    false
                } else {
                    val launch = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(entry.component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    val info = ShortcutInfo.Builder(appContext, "app|${entry.component.flattenToShortString()}")
                        .setShortLabel(entry.label)
                        .setLongLabel(entry.label)
                        .setIcon(icon)
                        .setIntent(launch)
                        .build()
                    manager.requestPinShortcut(info, null)
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "pin shortcut failed for ${entry.key}", e)
                false
            }
            Bg.main.post { onResult(ok) }
        }
    }

    /**
     * Adaptive icons are passed as the full 108dp layer so the home app applies its own mask, exactly
     * like the app's real icon. Legacy icons go as a plain bitmap and get the home app's legacy treatment.
     */
    private fun loadIcon(entry: AppEntry): Icon? {
        val info = index.findActivity(entry) ?: return null
        val drawable = info.getIcon(DisplayMetrics.DENSITY_XXHIGH) ?: return null
        val size = (appContext.resources.displayMetrics.density * LAYER_DP).toInt().coerceIn(MIN_PX, MAX_PX)
        return if (drawable is AdaptiveIconDrawable) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.background?.let { draw(it, canvas, size) }
            drawable.foreground?.let { draw(it, canvas, size) }
            Icon.createWithAdaptiveBitmap(bitmap)
        } else {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            draw(drawable, Canvas(bitmap), size)
            Icon.createWithBitmap(bitmap)
        }
    }

    private fun draw(d: Drawable, canvas: Canvas, size: Int) {
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
    }

    private companion object {
        const val TAG = "QL"
        /** Full adaptive layer size; the visible masked area is the central 72dp of it. */
        const val LAYER_DP = 108
        const val MIN_PX = 216
        const val MAX_PX = 432
    }
}
