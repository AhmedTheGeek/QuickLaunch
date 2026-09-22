package com.ahmedgeek.quicklaunch.suggest

import android.content.Context
import android.content.SharedPreferences
import com.ahmedgeek.quicklaunch.settings.Prefs

/**
 * The built-in sources, in display order, filtered by the user's settings. App-scoped: the overlay
 * rebuilds its panel on every show. Settings are re-read lazily after they change, never on the show path.
 */
class Suggestions(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = Prefs.get(context)
    private val calculator = CalculatorSource(context)
    private val units = UnitSource(context)
    private val web = WebSearchSource(context)

    private var sources: List<SuggestionSource> = emptyList()
    @Volatile private var stale = true

    init {
        // SharedPreferences holds listeners weakly; this object lives as long as the app. A lambda in a
        // private field doesn't work in release builds: R8 inlines the field and the lambda gets collected.
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        stale = true
    }

    fun collect(raw: String, query: String, out: MutableList<Suggestion>) {
        if (stale) reload()
        for (s in sources) s.suggest(raw, query, out)
    }

    private fun reload() {
        stale = false
        web.engines = WebSearch.parse(prefs.getString(Prefs.WEB_ENGINES, null))
        val list = ArrayList<SuggestionSource>(3)
        if (prefs.getBoolean(Prefs.CALCULATOR, true)) list.add(calculator)
        if (prefs.getBoolean(Prefs.UNITS, true)) list.add(units)
        if (prefs.getBoolean(Prefs.WEB_SEARCH, true)) list.add(web)
        sources = list
    }
}
