package com.ahmedgeek.quicklaunch.search

import com.ahmedgeek.quicklaunch.index.AppEntry

/**
 * Synchronous tiered matcher. One pass over all entries, keeping the top N with insertion into a
 * fixed array. ~250 entries rank in well under a millisecond, so it runs on the main thread per keystroke.
 */
object Ranker {
    const val MAX_RESULTS = 8

    /** The query is the user's alias for this app. */
    private const val TIER_ALIAS = 7000
    private const val TIER_EXACT = 6000
    private const val TIER_PREFIX = 5000
    private const val TIER_WORD_PREFIX = 4000
    private const val TIER_INITIALS = 3000
    private const val TIER_SUBSTRING = 2000
    private const val TIER_FUZZY = 1000

    /**
     * Empty query: pinned apps lead in their pinned order, far above any popularity score.
     * Typed query: a pin is worth this much within a tier, less than a well-used app's frecency boost
     * ([Frecency.MAX_BOOST]) and never enough to cross into the next tier.
     */
    private const val PINNED_EMPTY_BASE = 1 shl 30
    const val PIN_BOOST = 120

    /**
     * @param query already normalized via [TextNormalizer.normalize]
     * @param out cleared and filled with at most [MAX_RESULTS] entries, best first
     * @param aliasKey key of the entry the query is an alias for, ranked above everything else
     */
    fun rank(entries: List<AppEntry>, query: String, now: Long, out: MutableList<AppEntry>, aliasKey: String? = null) {
        out.clear()
        val topScore = IntArray(MAX_RESULTS)
        val top = arrayOfNulls<AppEntry>(MAX_RESULTS)
        var size = 0

        val empty = query.isEmpty()
        for (i in entries.indices) {
            val e = entries[i]
            val score = when {
                empty -> emptyScore(e, now)
                aliasKey != null && e.key == aliasKey -> TIER_ALIAS
                else -> score(e, query, now)
            }
            if (score == NO_MATCH) continue

            // Insert into the sorted top array (descending), breaking ties deterministically.
            var pos = size
            while (pos > 0 && better(score, e, topScore[pos - 1], top[pos - 1]!!)) pos--
            if (pos >= MAX_RESULTS) continue
            val last = if (size < MAX_RESULTS) size else MAX_RESULTS - 1
            for (j in last downTo pos + 1) {
                topScore[j] = topScore[j - 1]
                top[j] = top[j - 1]
            }
            topScore[pos] = score
            top[pos] = e
            if (size < MAX_RESULTS) size++
        }
        for (i in 0 until size) out.add(top[i]!!)
    }

    /** True when (score, e) should rank above (otherScore, other). */
    private fun better(score: Int, e: AppEntry, otherScore: Int, other: AppEntry): Boolean {
        if (score != otherScore) return score > otherScore
        if (e.isWork != other.isWork) return !e.isWork
        return e.normLabel < other.normLabel
    }

    /** One launch through Quick Launch counts like this much device-wide usage. Device usage tops out at 1.0. */
    private const val USAGE_WEIGHT = 2.5f

    /**
     * Blend of our own decayed launch count and device-wide usage. A never-launched app that you
     * use constantly ranks like ~2.5 recent Quick Launch launches; the tool's own history wins beyond that.
     */
    private fun popularity(e: AppEntry, now: Long): Float {
        val own = e.frecency?.decayed(now) ?: 0f
        return own + USAGE_WEIGHT * e.usage
    }

    private fun emptyScore(e: AppEntry, now: Long): Int {
        if (e.pinOrder >= 0) return PINNED_EMPTY_BASE - e.pinOrder
        // Scale so used apps sort first; alphabetical among the never-used.
        return (popularity(e, now) * 1000f).toInt()
    }

    /**
     * Returns [NO_MATCH] or a score where the tier dominates and within-tier terms never exceed 999
     * ([Frecency.MAX_BOOST] + [PIN_BOOST] at most, before penalties).
     */
    private fun score(e: AppEntry, q: String, now: Long): Int {
        val label = e.normLabel
        val boost = Frecency.boost(popularity(e, now)) + (if (e.pinOrder >= 0) PIN_BOOST else 0)
        val lengthPenalty = label.length.coerceAtMost(200)

        if (label == q) return TIER_EXACT + boost - lengthPenalty
        if (label.startsWith(q)) return TIER_PREFIX + boost - lengthPenalty

        val wordIndex = wordPrefixIndex(e.words, q)
        if (wordIndex >= 0) return TIER_WORD_PREFIX + boost - wordIndex * 4 - lengthPenalty

        if (q.length >= 2 && e.initials.startsWith(q)) return TIER_INITIALS + boost - lengthPenalty

        val sub = label.indexOf(q)
        if (sub >= 0) return TIER_SUBSTRING + boost - (sub * 4).coerceAtMost(400) - lengthPenalty

        if (q.length >= 2) {
            val fuzzy = subsequence(label, q)
            if (fuzzy != NO_MATCH) {
                val first = fuzzy ushr 16
                val gaps = fuzzy and 0xFFFF
                return TIER_FUZZY + boost - (first * 4).coerceAtMost(300) - (gaps * 3).coerceAtMost(300) - lengthPenalty
            }
        }
        return NO_MATCH
    }

    /**
     * Index of the first word that starts with q, or for a multi-token query the index of the word
     * matching the first token when every token prefixes a distinct later word in order. -1 if none.
     */
    private fun wordPrefixIndex(words: Array<String>, q: String): Int {
        val space = q.indexOf(' ')
        if (space < 0) {
            for (i in words.indices) if (words[i].startsWith(q)) return i
            return -1
        }
        // Multi-token: "meta bus" must match words in order.
        var tokenStart = 0
        var wordPos = 0
        var firstMatch = -1
        while (tokenStart <= q.length) {
            var tokenEnd = q.indexOf(' ', tokenStart)
            if (tokenEnd < 0) tokenEnd = q.length
            if (tokenEnd > tokenStart) {
                var matched = -1
                for (i in wordPos until words.size) {
                    if (words[i].regionMatches(0, q, tokenStart, tokenEnd - tokenStart)) {
                        matched = i
                        break
                    }
                }
                if (matched < 0) return -1
                if (firstMatch < 0) firstMatch = matched
                wordPos = matched + 1
            }
            tokenStart = tokenEnd + 1
        }
        return firstMatch
    }

    /** Greedy subsequence match. Returns (firstIndex shl 16) or gaps, or NO_MATCH. */
    private fun subsequence(label: String, q: String): Int {
        var li = 0
        var first = -1
        var gaps = 0
        var lastMatch = -1
        for (qc in q) {
            var found = -1
            while (li < label.length) {
                if (label[li] == qc) {
                    found = li
                    li++
                    break
                }
                li++
            }
            if (found < 0) return NO_MATCH
            if (first < 0) first = found else gaps += found - lastMatch - 1
            lastMatch = found
        }
        return (first.coerceAtMost(0x7FFF) shl 16) or gaps.coerceAtMost(0xFFFF)
    }

    const val NO_MATCH = Int.MIN_VALUE
}
