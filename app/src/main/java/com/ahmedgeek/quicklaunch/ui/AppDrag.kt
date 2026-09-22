package com.ahmedgeek.quicklaunch.ui

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.view.View
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.AppEntry

/**
 * Starts the same system drag a launcher uses when you drag an app icon to the screen edge:
 * WM Shell recognises the activity mime type and shows split-screen drop zones.
 * Personal profile only; a PendingIntent for another user needs a system permission.
 */
object AppDrag {
    // ClipDescription.MIMETYPE_APPLICATION_ACTIVITY and its extras are not in the public SDK, only their values are.
    private const val MIME_ACTIVITY = "application/vnd.android.activity"
    private const val EXTRA_PENDING_INTENT = "android.intent.extra.PENDING_INTENT"

    fun canDrag(entry: AppEntry): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !entry.isWork

    fun start(anchor: View, entry: AppEntry): Boolean {
        if (!canDrag(entry)) return false
        val context = anchor.context
        val launch = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val pending = PendingIntent.getActivity(
            context, entry.key.hashCode(), launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val payload = Intent().apply {
            putExtra(EXTRA_PENDING_INTENT, pending)
            putExtra(Intent.EXTRA_USER, Process.myUserHandle())
        }
        val clip = ClipData(ClipDescription(entry.label, arrayOf(MIME_ACTIVITY)), ClipData.Item(payload))
        val shadow = IconShadow(anchor, context)
        return anchor.startDragAndDrop(clip, shadow, entry, View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_OPAQUE)
    }

    /**
     * Drags a file (a content URI) into whatever app is underneath, the same way a file manager does.
     * DRAG_FLAG_GLOBAL_URI_READ hands the drop target read access to that one URI only.
     */
    fun startContent(anchor: View, uri: android.net.Uri, mime: String?, label: CharSequence, localState: Any): Boolean {
        val clip = ClipData(ClipDescription(label, arrayOf(mime ?: "application/octet-stream")), ClipData.Item(uri))
        val flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ or View.DRAG_FLAG_OPAQUE
        return anchor.startDragAndDrop(clip, IconShadow(anchor, anchor.context), localState, flags)
    }

    /** Drag shadow: the row's icon at 1.5x, so it reads as "the app" rather than a list row. */
    private class IconShadow(row: View, context: Context) : View.DragShadowBuilder(row) {
        private val icon: Drawable? = row.findViewById<android.widget.ImageView>(R.id.icon)?.drawable
        private val size = (context.resources.getDimensionPixelSize(R.dimen.icon_size) * 1.5f).toInt()

        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(size, size)
            outShadowTouchPoint.set(size / 2, size / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            val d = icon ?: return
            d.setBounds(0, 0, size, size)
            d.draw(canvas)
        }
    }
}
