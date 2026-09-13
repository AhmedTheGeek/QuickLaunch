package com.ahmedgeek.quicklaunch.index

import android.content.ComponentName
import com.ahmedgeek.quicklaunch.search.FrecencyEntry
import com.ahmedgeek.quicklaunch.search.TextNormalizer

/** One launchable activity. Immutable except for the frecency reference, which is attached after load. */
class AppEntry(
    @JvmField val userSerial: Long,
    @JvmField val component: ComponentName,
    @JvmField val label: String,
    @JvmField val isWork: Boolean,
    @JvmField val paused: Boolean,
) {
    @JvmField val key: String = "$userSerial|${component.flattenToShortString()}"
    @JvmField val normLabel: String = TextNormalizer.normalize(label)
    @JvmField val words: Array<String> = TextNormalizer.splitWords(label)
    @JvmField val initials: String = TextNormalizer.initials(words)

    /** Direct reference so ranking never does a map lookup. Mutated only via FrecencyStore. */
    @JvmField var frecency: FrecencyEntry? = null

    /** Device-wide usage score 0..1 from [UsageSource], 0 when unknown or not permitted. */
    @Volatile @JvmField var usage: Float = 0f

    /** Position in the user's pinned order, or -1 when not pinned. Written only by PinStore.attach. */
    @Volatile @JvmField var pinOrder: Int = -1

    /** Identity used to detect index changes: same component, same label, same paused state. */
    fun signature(): String = "$key|$label|$paused"

    override fun toString(): String = "AppEntry($label, $key)"
}
