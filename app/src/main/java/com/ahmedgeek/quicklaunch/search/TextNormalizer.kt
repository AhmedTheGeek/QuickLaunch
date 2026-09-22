package com.ahmedgeek.quicklaunch.search

import java.text.Normalizer

/** Pure string helpers. No regex on the hot path. */
object TextNormalizer {

    /**
     * Lowercase, strip diacritics, collapse every run of non letter/digit characters into one space, trim.
     * "Café  Müller!" -> "cafe muller"
     */
    fun normalize(s: String): String {
        if (s.isEmpty()) return s
        val decomposed = if (needsDecomposition(s)) Normalizer.normalize(s, Normalizer.Form.NFD) else s
        val sb = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (ch in decomposed) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            if (Character.isLetterOrDigit(ch)) {
                if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                pendingSpace = false
                sb.append(Character.toLowerCase(ch))
            } else {
                pendingSpace = true
            }
        }
        return sb.toString()
    }

    /**
     * [normalize] for what the user typed, keeping one trailing space: "My " means the word is
     * complete, which [Ranker] treats differently from "My".
     */
    fun normalizeQuery(raw: String): String {
        val q = normalize(raw)
        return if (q.isNotEmpty() && raw.last().isWhitespace()) "$q " else q
    }

    private fun needsDecomposition(s: String): Boolean {
        for (ch in s) if (ch.code > 0x7F) return true
        return false
    }

    /**
     * Words of a label after camelCase splitting and normalization.
     * "YouTube Music" -> [you, tube, music]; "WhatsApp" -> [whats, app]; "HTMLViewer" -> [html, viewer]
     */
    fun splitWords(label: String): Array<String> {
        val spaced = StringBuilder(label.length + 8)
        val n = label.length
        for (i in 0 until n) {
            val c = label[i]
            if (i > 0 && Character.isUpperCase(c)) {
                val prev = label[i - 1]
                val nextIsLower = i + 1 < n && Character.isLowerCase(label[i + 1])
                if (Character.isLowerCase(prev) || Character.isDigit(prev) || (Character.isUpperCase(prev) && nextIsLower)) {
                    spaced.append(' ')
                }
            }
            spaced.append(c)
        }
        val norm = normalize(spaced.toString())
        if (norm.isEmpty()) return EMPTY
        return norm.split(' ').filter { it.isNotEmpty() }.toTypedArray()
    }

    fun initials(words: Array<String>): String {
        if (words.isEmpty()) return ""
        val sb = StringBuilder(words.size)
        for (w in words) sb.append(w[0])
        return sb.toString()
    }

    private val EMPTY = emptyArray<String>()
}
