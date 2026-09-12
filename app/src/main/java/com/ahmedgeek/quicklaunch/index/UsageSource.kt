package com.ahmedgeek.quicklaunch.index

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import kotlin.math.min
import kotlin.math.pow

/**
 * Device-wide app usage from UsageStatsManager, reduced to one 0..1 score per package.
 * Needs the "Usage access" special permission; without it [compute] returns an empty map.
 *
 * Score: per-day foreground minutes (capped so a video app cannot dominate), weighted by how recent
 * the day is (half-life 4 days), plus a small bonus for having been used in the last 24 h.
 */
object UsageSource {
    private const val TAG = "QL"
    private const val DAYS = 14
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val HALF_LIFE_DAYS = 4.0
    private const val CAP_MINUTES = 90f

    fun isGranted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Background thread. Returns package -> score in 0..1 (1 = most used), empty if not permitted. */
    fun compute(context: Context): Map<String, Float> {
        if (!isGranted(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val begin = now - DAYS * DAY_MS
        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now)
        } catch (e: RuntimeException) {
            Log.w(TAG, "usage stats query failed", e)
            return emptyMap()
        } ?: return emptyMap()

        val raw = HashMap<String, Float>(128)
        val lastUsed = HashMap<String, Long>(128)
        for (s in stats) {
            val minutes = s.totalTimeInForeground / 60_000f
            if (minutes <= 0f) continue
            val ageDays = ((now - s.lastTimeStamp).coerceAtLeast(0L) / DAY_MS.toDouble())
            val weight = 2.0.pow(-ageDays / HALF_LIFE_DAYS).toFloat()
            raw.merge(s.packageName, min(minutes, CAP_MINUTES) * weight, Float::plus)
            lastUsed.merge(s.packageName, s.lastTimeUsed, ::maxOf)
        }
        if (raw.isEmpty()) return emptyMap()
        for ((pkg, last) in lastUsed) {
            if (now - last < DAY_MS) raw.merge(pkg, 10f, Float::plus)
        }
        val max = raw.values.maxOrNull() ?: return emptyMap()
        if (max <= 0f) return emptyMap()
        val out = HashMap<String, Float>(raw.size)
        for ((pkg, v) in raw) out[pkg] = (v / max).coerceIn(0f, 1f)
        return out
    }
}
