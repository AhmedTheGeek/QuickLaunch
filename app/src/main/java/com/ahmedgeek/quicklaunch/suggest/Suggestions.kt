package com.ahmedgeek.quicklaunch.suggest

import android.content.Context

/** The built-in sources, in display order. App-scoped: the overlay rebuilds its panel on every show. */
class Suggestions(context: Context) {
    private val sources: List<SuggestionSource> = listOf(
        CalculatorSource(context),
        UnitSource(context),
        WebSearchSource(context),
    )

    fun collect(raw: String, query: String, out: MutableList<Suggestion>) {
        for (s in sources) s.suggest(raw, query, out)
    }
}
