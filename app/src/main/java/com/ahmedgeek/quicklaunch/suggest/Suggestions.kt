package com.ahmedgeek.quicklaunch.suggest

import android.content.Context
import android.content.SharedPreferences
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.search.AliasStore
import com.ahmedgeek.quicklaunch.settings.Prefs

/**
 * The built-in sources, in display order, filtered by the user's settings. App-scoped: the overlay
 * rebuilds its panel on every show. Settings are re-read lazily after they change, never on the show path.
 */
class Suggestions(private val context: Context, private val aliases: AliasStore) :
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = Prefs.get(context)
    private val calculator = CalculatorSource(context)
    private val units = UnitSource(context)
    private val web = WebSearchSource(context)
    private val system = SystemSettingsSource(context, aliases)
    private val url = TypedUrlSource(context)

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
        if (raw.startsWith(HELP)) {
            help(raw.substring(1).trim().lowercase(), out)
            return
        }
        for (s in sources) s.suggest(raw, query, out)
    }

    /**
     * `?` lists what the search box understands: your aliases, the search keywords, then one example per
     * feature that is on. `?y` filters. Choosing a row types it in, the way Flow Launcher's plugin
     * indicator does, so the list doubles as autocomplete.
     */
    private fun help(filter: String, out: MutableList<Suggestion>) {
        fun add(fill: String, title: CharSequence, badge: CharSequence, glyph: Int, key: String = "help|$fill", handlerUrl: String? = null) {
            if (filter.isNotEmpty() && !fill.startsWith(filter) && !badge.toString().lowercase().contains(filter)) return
            out.add(Suggestion(key, title, badge, glyph, handlerUrl, fill = fill) { false })
        }
        val aliasMap = aliases.all()
        if (aliasMap.isNotEmpty()) {
            val labels = HashMap<String, String>()
            for (e in (context.applicationContext as QuickLaunchApp).index.snapshot) labels[e.key] = e.label
            for (s in SystemShortcuts.ALL) labels[s.key] = s.title
            for ((alias, key) in aliasMap) add(alias, alias, labels[key] ?: key.substringAfter('|'), R.drawable.ic_star)
        }
        if (web in sources) {
            // Same key as the search row, so the engine's app icon is already cached.
            for (e in web.engines) if (e.enabled) add("${e.keyword} ", "${e.keyword} \u2026", e.name, R.drawable.ic_search, "web|${e.keyword}", e.urlFor("x"))
        }
        if (calculator in sources) add("12*4", "12*4", context.getText(R.string.settings_calculator), R.drawable.ic_calculator)
        if (units in sources) add("10cm in inch", "10cm in inch", context.getText(R.string.settings_units), R.drawable.ic_convert)
        if (system in sources) add("wifi", "wifi, bluetooth, torch", context.getText(R.string.settings_system), R.drawable.ic_settings)
        if (url in sources) add("example.org", "example.org", context.getText(R.string.settings_typed_url), R.drawable.ic_link)
        add("settings", "settings", context.getText(R.string.settings_title), R.drawable.ic_settings)
    }

    private fun reload() {
        stale = false
        web.engines = WebSearch.parse(prefs.getString(Prefs.WEB_ENGINES, null))
        val list = ArrayList<SuggestionSource>(5)
        if (prefs.getBoolean(Prefs.CALCULATOR, true)) list.add(calculator)
        if (prefs.getBoolean(Prefs.UNITS, true)) list.add(units)
        if (prefs.getBoolean(Prefs.WEB_SEARCH, true)) list.add(web)
        if (prefs.getBoolean(Prefs.SYSTEM_SETTINGS, true)) list.add(system)
        if (prefs.getBoolean(Prefs.TYPED_URL, true)) list.add(url)
        sources = list
    }

    companion object {
        /** Typed first, opens the list of keywords and examples. */
        const val HELP = "?"
    }
}
