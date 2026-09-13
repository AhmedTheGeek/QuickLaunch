package com.ahmedgeek.quicklaunch.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.ahmedgeek.quicklaunch.QuickLaunchApp

/**
 * Reads the primary clip and answers "is there a link on the clipboard?".
 *
 * Android 10+ only hands the clip to the focused window, so callers must ask after window focus.
 * Android 12+ shows a system "pasted from your clipboard" toast on every content read; the
 * description (mime type, timestamp, classification) is free. So: cheap checks first, then read the
 * content at most once per clip and remember the answer by clip timestamp.
 */
class ClipboardLinkSource(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(ClipboardManager::class.java)

    private var cachedTimestamp = Long.MIN_VALUE
    private var cachedLink: String? = null

    /** Main thread. Null when the clipboard is empty, unreadable, or holds no link. */
    fun currentLink(): String? {
        val desc: ClipDescription = try {
            manager?.primaryClipDescription
        } catch (e: RuntimeException) {
            Log.w(QuickLaunchApp.TAG, "clipboard description failed", e)
            null
        } ?: return null

        val timestamp = desc.timestamp
        if (timestamp == cachedTimestamp) return cachedLink

        val link = if (looksLikeText(desc)) readLink() else null
        cachedTimestamp = timestamp
        cachedLink = link
        return link
    }

    /** Everything decidable from the description alone, before the read that triggers the toast. */
    private fun looksLikeText(desc: ClipDescription): Boolean {
        if (!desc.hasMimeType("text/*")) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            desc.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
        ) {
            return false // passwords and the like: never surface them
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            desc.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE
        ) {
            // The system already ran TextClassifier on this clip; trust a confident "no URL here".
            val score = try {
                desc.getConfidenceScore(android.view.textclassifier.TextClassifier.TYPE_URL)
            } catch (e: IllegalStateException) {
                1f
            }
            if (score < 0.05f) return false
        }
        return true
    }

    private fun readLink(): String? {
        val clip = try {
            manager?.primaryClip
        } catch (e: RuntimeException) {
            Log.w(QuickLaunchApp.TAG, "clipboard read failed", e)
            null
        } ?: return null
        if (clip.itemCount == 0) return null
        val text = try {
            clip.getItemAt(0).coerceToText(app)
        } catch (e: RuntimeException) {
            return null
        }
        return LinkDetector.detect(text)
    }
}
