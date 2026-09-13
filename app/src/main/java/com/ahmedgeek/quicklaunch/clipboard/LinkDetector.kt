package com.ahmedgeek.quicklaunch.clipboard

/**
 * Decides whether a piece of clipboard text is a web link worth offering to open. Pure Kotlin so
 * it is unit-testable; deliberately stricter than android.util.Patterns so that filenames
 * ("notes.md", "index.html") and version numbers never masquerade as links.
 */
object LinkDetector {
    private const val MAX_LENGTH = 2048

    /** http(s)://host[:port][/path]. Hosts may be IDN, IPv4 or bracketed IPv6. */
    private val WITH_SCHEME = Regex(
        """^https?://(?:[\p{L}\p{N}_-]+(?:\.[\p{L}\p{N}_-]+)*\.?|\[[0-9A-Fa-f:.]+\])(?::\d{1,5})?(?:[/?#]\S*)?$""",
        RegexOption.IGNORE_CASE,
    )

    /** Scheme-less: labels, a letters-only TLD, optional port, optional path. Group 1 = TLD, group 2 = path. */
    private val BARE = Regex(
        """^(?:[\p{L}\p{N}-]+\.)+([\p{L}]{2,63})(?::\d{1,5})?([/?#]\S*)?$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A bare domain with no path is only a link when its TLD is one people actually paste
     * ("example.com"), so "readme.txt" or "settings.gradle" stay plain text. With a path or a
     * "www." prefix any TLD is accepted.
     */
    private val COMMON_TLDS = hashSetOf(
        "com", "org", "net", "io", "dev", "app", "co", "me", "ai", "edu", "gov", "info", "biz",
        "uk", "de", "fr", "es", "it", "nl", "eu", "ca", "au", "us", "in", "jp", "br", "ru", "ch",
        "se", "no", "dk", "fi", "pl", "at", "be", "ie", "nz", "tv", "ly", "gg", "sh", "so", "to",
        "xyz", "tech", "cloud", "page", "site", "blog", "shop", "store", "news", "link", "eg", "sa", "ae",
    )

    /** Returns a launchable URL (scheme included) or null when the text is not a link. */
    fun detect(text: CharSequence?): String? {
        if (text == null) return null
        val s = text.trim().toString()
        if (s.isEmpty() || s.length > MAX_LENGTH) return null
        for (c in s) if (c.isWhitespace()) return null

        if (WITH_SCHEME.matches(s)) return s

        val m = BARE.matchEntire(s) ?: return null
        val tld = m.groupValues[1].lowercase()
        val hasPath = m.groupValues[2].isNotEmpty()
        val www = s.length > 4 && s.regionMatches(0, "www.", 0, 4, ignoreCase = true)
        if (!hasPath && !www && tld !in COMMON_TLDS) return null
        return "https://$s"
    }

    /** What the row shows: the URL without its scheme and without a bare trailing slash. */
    fun display(url: String): String {
        var s = url
        val scheme = s.indexOf("://")
        if (scheme in 1..8) s = s.substring(scheme + 3)
        if (s.endsWith("/") && s.indexOf('/') == s.length - 1) s = s.dropLast(1)
        return s
    }
}
