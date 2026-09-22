package com.ahmedgeek.quicklaunch.suggest

import android.content.Context
import com.ahmedgeek.quicklaunch.R

/** `g restaurants near me`, `yt funny cat`. Opens in the app that handles the site, or the browser. */
class WebSearchSource(private val context: Context) : SuggestionSource {
    var engines: List<WebSearch> = WebSearch.DEFAULTS

    override fun suggest(raw: String, query: String, out: MutableList<Suggestion>) {
        val m = WebSearch.match(raw, engines) ?: return
        val url = m.engine.urlFor(m.terms)
        out.add(
            Suggestion(
                // Per engine, not per URL: the handler app is the same whatever the terms.
                key = "web|${m.engine.keyword}",
                title = m.terms,
                badge = context.getString(R.string.web_search_badge, m.engine.name),
                glyph = R.drawable.ic_search,
                handlerUrl = url,
            ) { c -> Suggestion.openUrl(c, url) },
        )
    }
}
