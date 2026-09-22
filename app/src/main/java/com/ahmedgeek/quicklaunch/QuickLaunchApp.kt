package com.ahmedgeek.quicklaunch

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
import com.ahmedgeek.quicklaunch.clipboard.ClipboardLinkSource
import com.ahmedgeek.quicklaunch.index.AppIndex
import com.ahmedgeek.quicklaunch.launch.AppLauncher
import com.ahmedgeek.quicklaunch.overlay.OverlayController
import com.ahmedgeek.quicklaunch.suggest.Suggestions
import com.ahmedgeek.quicklaunch.ui.IconLoader

class QuickLaunchApp : Application() {

    lateinit var index: AppIndex
        private set
    lateinit var icons: IconLoader
        private set
    lateinit var launcher: AppLauncher
    lateinit var homeShortcuts: com.ahmedgeek.quicklaunch.launch.HomeShortcuts
        private set
    lateinit var overlay: OverlayController
        private set
    /** App-scoped so its per-clip cache outlives the panel, which the overlay rebuilds on every show. */
    lateinit var clipboardLinks: ClipboardLinkSource
        private set
    lateinit var suggestions: Suggestions
        private set

    override fun onCreate() {
        val t0 = SystemClock.elapsedRealtimeNanos()
        super.onCreate()
        index = AppIndex(this)
        icons = IconLoader(this, index)
        launcher = AppLauncher(this, index)
        homeShortcuts = com.ahmedgeek.quicklaunch.launch.HomeShortcuts(this, index)
        overlay = OverlayController(this)
        clipboardLinks = ClipboardLinkSource(this)
        suggestions = Suggestions(this)

        // Cache load runs concurrently with LaunchActivity.onCreate; the activity waits a few ms at most.
        Bg.bg.post { index.loadCache() }

        getSystemService(LauncherApps::class.java).registerCallback(packageCallback, Bg.bg)

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "app.onCreate took ${(SystemClock.elapsedRealtimeNanos() - t0) / 1000} us")
        }
    }

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach(::changed)
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach(::changed)

        private fun changed(packageName: String) {
            icons.invalidatePackage(packageName)
            index.revalidate(force = true)
        }
    }

    companion object {
        const val TAG = "QL"
        fun get(context: Context): QuickLaunchApp = context.applicationContext as QuickLaunchApp
    }
}
