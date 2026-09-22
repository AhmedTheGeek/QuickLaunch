package com.ahmedgeek.quicklaunch.diag

import android.content.Context

/**
 * Usage counters and timings for the Diagnostics screen. Main thread only, so no locking.
 *
 * Recording is a field increment or an array store; nothing here touches disk on the show path.
 * Totals are added to a small prefs file in [flush], which the panel calls after it has closed.
 */
object Stats {
    private const val FILE = "ql_stats"
    private const val OPENS = "opens"
    private const val LAUNCHES = "launches"
    private const val ACTIONS = "actions"
    private const val SINCE = "since"
    private const val COLD_WINDOW_MS = 1000L

    // Not yet written to disk.
    private var pendingOpens = 0
    private var pendingLaunches = 0
    private var pendingActions = 0

    /** This process only. */
    var sessionOpens = 0
        private set
    var lastOpenMs = -1L
        private set
    /** Process start to the first frame of the first open, when this process was started to show it. */
    var coldOpenMs = -1L
        private set

    private var openStart = 0L
    private val keystrokes = LongArray(64)
    private var keystrokeCount = 0

    /** When the card was asked to show: the overlay starts building its window. */
    fun openStarted(now: Long) {
        openStart = now
    }

    /** First frame of the card is drawn. [sinceProcessStart] is only used for the first open. */
    fun openDrawn(now: Long, sinceProcessStart: Long) {
        if (openStart == 0L) return
        lastOpenMs = now - openStart
        // Only a cold start if the open began right after the process did; otherwise the process was
        // already up (started by the system, a broadcast, an install) and the gap is just idle time.
        val processStart = now - sinceProcessStart
        if (sessionOpens == 0 && openStart - processStart < COLD_WINDOW_MS) coldOpenMs = sinceProcessStart
        sessionOpens++
        pendingOpens++
        openStart = 0L
    }

    fun launched() {
        pendingLaunches++
    }

    /** A suggestion did its thing: a copy, a web search, a setting, a file. */
    fun actionRun() {
        pendingActions++
    }

    /** Rank and bind time for one keystroke. */
    fun keystroke(nanos: Long) {
        keystrokes[keystrokeCount % keystrokes.size] = nanos
        keystrokeCount++
    }

    class Keys(@JvmField val count: Int, @JvmField val avgMicros: Long, @JvmField val maxMicros: Long)

    /** Over the last 64 keystrokes of this process. */
    fun keys(): Keys {
        val n = minOf(keystrokeCount, keystrokes.size)
        if (n == 0) return Keys(0, 0, 0)
        var sum = 0L
        var max = 0L
        for (i in 0 until n) {
            sum += keystrokes[i]
            if (keystrokes[i] > max) max = keystrokes[i]
        }
        return Keys(keystrokeCount, sum / n / 1000, max / 1000)
    }

    class Totals(@JvmField val opens: Int, @JvmField val launches: Int, @JvmField val actions: Int, @JvmField val since: Long)

    fun totals(context: Context): Totals {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Totals(
            p.getInt(OPENS, 0) + pendingOpens,
            p.getInt(LAUNCHES, 0) + pendingLaunches,
            p.getInt(ACTIONS, 0) + pendingActions,
            p.getLong(SINCE, 0L),
        )
    }

    /** After the card closed: add what's pending to the totals. apply() writes off the main thread. */
    fun flush(context: Context) {
        if (pendingOpens == 0 && pendingLaunches == 0 && pendingActions == 0) return
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val e = p.edit()
            .putInt(OPENS, p.getInt(OPENS, 0) + pendingOpens)
            .putInt(LAUNCHES, p.getInt(LAUNCHES, 0) + pendingLaunches)
            .putInt(ACTIONS, p.getInt(ACTIONS, 0) + pendingActions)
        if (!p.contains(SINCE)) e.putLong(SINCE, System.currentTimeMillis())
        e.apply()
        pendingOpens = 0
        pendingLaunches = 0
        pendingActions = 0
    }

    fun reset(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
        pendingOpens = 0
        pendingLaunches = 0
        pendingActions = 0
    }
}
