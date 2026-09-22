package com.ahmedgeek.quicklaunch.suggest

/** Adds rows above the app results. Called on main for every keystroke, so reject fast. */
fun interface SuggestionSource {
    /**
     * @param raw the input as typed, trimmed
     * @param query the same input normalized for matching ([com.ahmedgeek.quicklaunch.search.TextNormalizer])
     */
    fun suggest(raw: String, query: String, out: MutableList<Suggestion>)
}
