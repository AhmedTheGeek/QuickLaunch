package com.ahmedgeek.quicklaunch.suggest

import java.net.URLEncoder

/** A search engine behind a keyword: `yt funny cat` opens YouTube results for "funny cat". */
class WebSearch(
    @JvmField val keyword: String,
    @JvmField val name: String,
    /** `%s` is replaced by the URL-encoded search terms. */
    @JvmField val url: String,
) {
    fun urlFor(terms: String): String = url.replace("%s", URLEncoder.encode(terms, "UTF-8"))

    /** What the search box asked for: the engine and the terms after the keyword. */
    class Match(@JvmField val engine: WebSearch, @JvmField val terms: String)

    companion object {
        val DEFAULTS = listOf(
            WebSearch("g", "Google", "https://www.google.com/search?q=%s"),
            WebSearch("yt", "YouTube", "https://www.youtube.com/results?search_query=%s"),
            WebSearch("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
            WebSearch("wiki", "Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search=%s"),
            WebSearch("maps", "Google Maps", "https://www.google.com/maps/search/?api=1&query=%s"),
            WebSearch("play", "Play Store", "https://play.google.com/store/search?q=%s&c=apps"),
            WebSearch("gh", "GitHub", "https://github.com/search?q=%s"),
        )

        /** "<keyword> <terms>", keyword case-insensitive. Null until there is something to search for. */
        fun match(raw: String, engines: List<WebSearch>): Match? {
            val space = raw.indexOf(' ')
            if (space <= 0) return null
            val terms = raw.substring(space + 1).trim()
            if (terms.isEmpty()) return null
            for (e in engines) {
                if (e.keyword.length == space && raw.regionMatches(0, e.keyword, 0, space, ignoreCase = true)) {
                    return Match(e, terms)
                }
            }
            return null
        }
    }
}
