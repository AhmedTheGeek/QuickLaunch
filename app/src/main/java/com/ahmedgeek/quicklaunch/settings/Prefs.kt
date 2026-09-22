package com.ahmedgeek.quicklaunch.settings

import android.content.Context
import android.content.SharedPreferences

/** User settings, in the same "ql" file as the setup-hint flags. Every feature defaults to on. */
object Prefs {
    const val CALCULATOR = "calculator"
    const val UNITS = "units"
    const val WEB_SEARCH = "web_search"
    const val SYSTEM_SETTINGS = "system_settings"
    const val TYPED_URL = "typed_url"
    /** Off by default, unlike the rest: it needs All files access. Only exists in builds with file search. */
    const val FILE_SEARCH = "file_search"
    const val CLIPBOARD_LINK = "clipboard_link"
    /** Serialized engine list, see [com.ahmedgeek.quicklaunch.suggest.WebSearch.serialize]. */
    const val WEB_ENGINES = "web_engines"
    /** Serialized app aliases, see [com.ahmedgeek.quicklaunch.search.AliasStore]. */
    const val ALIASES = "aliases"

    fun get(context: Context): SharedPreferences = context.getSharedPreferences("ql", Context.MODE_PRIVATE)
}
