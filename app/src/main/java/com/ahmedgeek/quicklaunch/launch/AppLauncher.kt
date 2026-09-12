package com.ahmedgeek.quicklaunch.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.util.Log
import com.ahmedgeek.quicklaunch.index.AppEntry
import com.ahmedgeek.quicklaunch.index.AppIndex

enum class LaunchResult { OK, PAUSED, NOT_AVAILABLE }

/** Starts an app through LauncherApps so work-profile targets work without extra permissions. */
class AppLauncher(context: Context, private val index: AppIndex) {
    private val launcherApps = context.applicationContext.getSystemService(LauncherApps::class.java)

    fun launch(entry: AppEntry, sourceBounds: Rect?): LaunchResult {
        val user = index.userHandle(entry.userSerial) ?: return LaunchResult.NOT_AVAILABLE
        return try {
            launcherApps.startMainActivity(entry.component, user, sourceBounds, null)
            LaunchResult.OK
        } catch (e: SecurityException) {
            Log.w(TAG, "launch refused for ${entry.key}", e)
            if (entry.paused) LaunchResult.PAUSED else LaunchResult.NOT_AVAILABLE
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "activity gone for ${entry.key}", e)
            LaunchResult.NOT_AVAILABLE
        } catch (e: RuntimeException) {
            Log.w(TAG, "launch failed for ${entry.key}", e)
            LaunchResult.NOT_AVAILABLE
        }
    }

    private companion object {
        const val TAG = "QL"
    }
}
