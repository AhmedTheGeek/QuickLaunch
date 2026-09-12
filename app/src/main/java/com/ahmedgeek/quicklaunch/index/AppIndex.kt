package com.ahmedgeek.quicklaunch.index

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.SystemClock
import android.os.Trace
import android.os.UserHandle
import android.os.UserManager
import android.util.AtomicFile
import android.util.Log
import com.ahmedgeek.quicklaunch.Bg
import com.ahmedgeek.quicklaunch.search.FrecencyStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * In-memory list of launchable apps backed by a binary cache.
 *
 * Cold start renders from the cache immediately; a background revalidation against LauncherApps
 * follows and swaps the snapshot only if something changed. All mutation happens on Bg.bg.
 */
class AppIndex(context: Context) {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val ownPackage = appContext.packageName

    private val indexFile = AtomicFile(File(appContext.filesDir, "index.bin"))
    val frecency = FrecencyStore(File(appContext.filesDir, "frecency.bin"))

    @Volatile
    var snapshot: List<AppEntry> = emptyList()
        private set

    private val loaded = CountDownLatch(1)

    @Volatile
    private var lastRevalidateMs = 0L

    @Volatile
    private var revalidating = false

    /** Called on the main thread whenever the snapshot changes. */
    @Volatile
    var listener: (() -> Unit)? = null

    private val userHandles = HashMap<Long, UserHandle?>()
    private val myUser: UserHandle = Process.myUserHandle()
    private val mySerial: Long = userManager.getSerialNumberForUser(myUser)

    // ---- Loading -----------------------------------------------------------------------------

    /** Background: read caches and publish the first snapshot. */
    fun loadCache() {
        Trace.beginSection("ql.loadCache")
        try {
            frecency.load()
            val cached = IndexStore.read(indexFile)
            if (cached != null) {
                frecency.attach(cached)
                snapshot = cached
            }
        } finally {
            loaded.countDown()
            Trace.endSection()
        }
    }

    /** Main: returns the snapshot, waiting briefly if the cache read has not finished yet. */
    fun awaitSnapshot(): List<AppEntry> {
        if (loaded.count > 0) {
            try {
                loaded.await(80, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
            }
        }
        return snapshot
    }

    // ---- Revalidation ------------------------------------------------------------------------

    /**
     * Re-enumerate launcher activities and swap the snapshot if anything changed.
     * Cheap to call: no-ops when a fresh revalidation happened in the last 5 minutes unless forced.
     */
    fun revalidate(force: Boolean) {
        if (!Bg.isBg()) {
            Bg.bg.post { revalidate(force) }
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && lastRevalidateMs != 0L && now - lastRevalidateMs < STALE_MS) return
        if (revalidating) return
        revalidating = true
        try {
            Trace.beginSection("ql.revalidate")
            val fresh = enumerate()
            applyUsage(fresh)
            lastRevalidateMs = SystemClock.elapsedRealtime()
            if (!sameAs(snapshot, fresh) || !sameUsage(snapshot, fresh)) {
                frecency.attach(fresh)
                frecency.prune(fresh)
                snapshot = fresh
                IndexStore.write(indexFile, fresh)
                notifyChanged()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "revalidate failed", e)
        } finally {
            Trace.endSection()
            revalidating = false
        }
    }

    private fun enumerate(): List<AppEntry> {
        val out = ArrayList<AppEntry>(256)
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val isWork = user != myUser
            val paused = isWork && userManager.isQuietModeEnabled(user)
            synchronized(userHandles) { userHandles[serial] = user }
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                Log.w(TAG, "cannot list activities for $user", e)
                continue
            }
            for (info in activities) {
                val component = info.componentName
                if (component.packageName == ownPackage) continue
                val label = info.label?.toString()?.trim().orEmpty()
                if (label.isEmpty()) continue
                out.add(AppEntry(serial, component, label, isWork, paused))
            }
        }
        out.sortWith(compareBy({ it.normLabel }, { it.isWork }))
        return out
    }

    /** Device-wide usage, one score per package shared by all of its launcher activities. */
    private fun applyUsage(entries: List<AppEntry>) {
        val usage = UsageSource.compute(appContext)
        if (usage.isEmpty()) return
        for (e in entries) e.usage = usage[e.component.packageName] ?: 0f
    }

    private fun sameUsage(a: List<AppEntry>, b: List<AppEntry>): Boolean {
        val byKey = HashMap<String, Float>(a.size * 2)
        for (e in a) byKey[e.key] = e.usage
        for (e in b) {
            val old = byKey[e.key] ?: return false
            if (kotlin.math.abs(old - e.usage) > 0.02f) return false
        }
        return true
    }

    fun usagePermitted(): Boolean = UsageSource.isGranted(appContext)

    private fun sameAs(a: List<AppEntry>, b: List<AppEntry>): Boolean {
        if (a.size != b.size) return false
        val sig = HashSet<String>(a.size * 2)
        for (e in a) sig.add(e.signature())
        for (e in b) if (!sig.contains(e.signature())) return false
        return true
    }

    private fun notifyChanged() {
        Bg.main.post { listener?.invoke() }
    }

    // ---- Mutations from the UI ---------------------------------------------------------------

    /** Main: drop an entry that failed to launch, then persist and re-verify in the background. */
    fun removeEntry(entry: AppEntry) {
        val current = snapshot
        if (!current.contains(entry)) return
        val next = ArrayList<AppEntry>(current.size - 1)
        for (e in current) if (e !== entry) next.add(e)
        snapshot = next
        listener?.invoke()
        Bg.bg.post {
            IndexStore.write(indexFile, next)
            revalidate(force = true)
        }
    }

    fun recordLaunch(entry: AppEntry) = frecency.recordLaunch(entry, System.currentTimeMillis())

    // ---- Users -------------------------------------------------------------------------------

    fun userHandle(serial: Long): UserHandle? {
        if (serial == mySerial) return myUser
        synchronized(userHandles) {
            if (userHandles.containsKey(serial)) return userHandles[serial]
        }
        val handle = userManager.getUserForSerialNumber(serial)
        synchronized(userHandles) { userHandles[serial] = handle }
        return handle
    }

    fun findActivity(entry: AppEntry): android.content.pm.LauncherActivityInfo? {
        val user = userHandle(entry.userSerial) ?: return null
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).setComponent(entry.component)
        return try {
            launcherApps.resolveActivity(intent, user)
        } catch (e: RuntimeException) {
            null
        }
    }

    companion object {
        private const val TAG = "QL"
        private const val STALE_MS = 5 * 60 * 1000L
    }
}
