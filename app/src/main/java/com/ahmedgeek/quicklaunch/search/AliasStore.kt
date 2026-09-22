package com.ahmedgeek.quicklaunch.search

import android.content.SharedPreferences
import com.ahmedgeek.quicklaunch.settings.Prefs

/**
 * The user's own keywords for apps: "sp" always puts Spotify first. Only an exact match on the
 * normalized query counts, so typing past the alias falls back to normal ranking.
 * Stored in prefs as alias-tab-entry key lines; parsed once and again only after a change.
 */
class AliasStore(private val prefs: SharedPreferences) : SharedPreferences.OnSharedPreferenceChangeListener {

    @Volatile
    private var cache: Map<String, String>? = null


    init {
        // Registered as `this`, which the app keeps alive: SharedPreferences only holds listeners weakly.
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == null || key == Prefs.ALIASES) cache = null
    }

    /** Main: the entry key that [query] (already normalized) is an alias for, or null. */
    fun target(query: String): String? {
        if (query.isEmpty()) return null
        val m = cache ?: parse(prefs.getString(Prefs.ALIASES, null)).also { cache = it }
        return m[query.trimEnd()]
    }

    /** Alias to entry key, in the order they were added. */
    fun all(): Map<String, String> = parse(prefs.getString(Prefs.ALIASES, null))

    /** Returns false when the alias is empty once normalized. Reassigns an alias that already exists. */
    fun set(alias: String, entryKey: String): Boolean {
        val a = TextNormalizer.normalize(alias)
        if (a.isEmpty()) return false
        val m = LinkedHashMap(all())
        m[a] = entryKey
        save(m)
        return true
    }

    fun remove(alias: String) {
        val m = LinkedHashMap(all())
        if (m.remove(alias) != null) save(m)
    }

    private fun save(m: Map<String, String>) {
        prefs.edit().putString(Prefs.ALIASES, serialize(m)).apply()
    }

    companion object {
        fun serialize(m: Map<String, String>): String = m.entries.joinToString("\n") { "${it.key}\t${it.value}" }

        fun parse(s: String?): Map<String, String> {
            val m = LinkedHashMap<String, String>()
            if (s.isNullOrEmpty()) return m
            for (line in s.split('\n')) {
                val tab = line.indexOf('\t')
                if (tab <= 0 || tab == line.length - 1) continue
                m[line.substring(0, tab)] = line.substring(tab + 1)
            }
            return m
        }
    }
}
