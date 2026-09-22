package com.ahmedgeek.quicklaunch.suggest

import android.provider.Settings
import com.ahmedgeek.quicklaunch.search.TextNormalizer

/**
 * System settings pages and quick actions the search box can open: `wifi`, `bluetooth`, `battery`,
 * `flashlight`. Only public Settings actions, so every page exists on stock Android; OEMs that drop
 * one fail the launch, which the source reports as "not available".
 */
object SystemShortcuts {

    class Shortcut(
        /** Alias target and row key: "setting|<id>". */
        @JvmField val id: String,
        @JvmField val title: String,
        /** Settings action, or null for [FLASHLIGHT], which is handled in place. */
        @JvmField val action: String?,
        /** Normalized words that find it besides the title. */
        @JvmField val keywords: Array<String>,
    ) {
        val key: String get() = KEY_PREFIX + id
    }

    const val KEY_PREFIX = "setting|"
    const val FLASHLIGHT = "flashlight"

    val ALL: List<Shortcut> = listOf(
        Shortcut(FLASHLIGHT, "Flashlight", null, arrayOf("torch", "flash", "lamp")),
        Shortcut("wifi", "Wi-Fi", Settings.ACTION_WIFI_SETTINGS, arrayOf("wifi", "wlan", "internet")),
        Shortcut("bluetooth", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, arrayOf("bt")),
        Shortcut("airplane", "Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, arrayOf("flight", "plane")),
        Shortcut("connections", "Connections", Settings.ACTION_WIRELESS_SETTINGS, arrayOf("network", "mobile data", "hotspot")),
        Shortcut("data_usage", "Data usage", Settings.ACTION_DATA_USAGE_SETTINGS, arrayOf("mobile data")),
        Shortcut("nfc", "NFC", Settings.ACTION_NFC_SETTINGS, arrayOf("contactless", "tap to pay")),
        Shortcut("vpn", "VPN", Settings.ACTION_VPN_SETTINGS, emptyArray()),
        Shortcut("cast", "Cast", Settings.ACTION_CAST_SETTINGS, arrayOf("screen mirroring", "smart view")),
        Shortcut("display", "Display", Settings.ACTION_DISPLAY_SETTINGS, arrayOf("brightness", "screen", "dark mode")),
        Shortcut("night_light", "Night light", Settings.ACTION_NIGHT_DISPLAY_SETTINGS, arrayOf("eye comfort", "blue light")),
        Shortcut("sound", "Sound", Settings.ACTION_SOUND_SETTINGS, arrayOf("volume", "ringtone", "vibration")),
        Shortcut("battery", "Battery", android.content.Intent.ACTION_POWER_USAGE_SUMMARY, arrayOf("power")),
        Shortcut("storage", "Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, arrayOf("space")),
        Shortcut("location", "Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, arrayOf("gps")),
        Shortcut("apps", "Apps", Settings.ACTION_APPLICATION_SETTINGS, arrayOf("applications")),
        Shortcut("date", "Date & time", Settings.ACTION_DATE_SETTINGS, arrayOf("time", "clock", "timezone")),
        Shortcut("language", "Language", Settings.ACTION_LOCALE_SETTINGS, arrayOf("locale")),
        Shortcut("keyboard", "Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, arrayOf("input")),
        Shortcut("accessibility", "Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, emptyArray()),
        Shortcut("security", "Security", Settings.ACTION_SECURITY_SETTINGS, arrayOf("lock screen", "fingerprint")),
        Shortcut("privacy", "Privacy", Settings.ACTION_PRIVACY_SETTINGS, arrayOf("permissions")),
        Shortcut("developer", "Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, arrayOf("adb", "usb debugging")),
        Shortcut("about", "About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, arrayOf("device info")),
    )

    private val BY_KEY: Map<String, Shortcut> = ALL.associateBy { it.key }

    fun byKey(key: String): Shortcut? = BY_KEY[key]

    class Match(@JvmField val shortcut: Shortcut, @JvmField val exact: Boolean)

    /** Normalized names per shortcut, title first, computed once. */
    private val NAMES: List<Array<String>> = ALL.map { s ->
        arrayOf(TextNormalizer.normalize(s.title)) + s.keywords
    }

    /**
     * Shortcuts whose title or a keyword starts with [query] (normalized, no trailing space), best first.
     * One or two letters only match a whole name, so "d" or "ba" never fill the list.
     */
    fun match(query: String, max: Int = 2): List<Match> {
        if (query.isEmpty()) return emptyList()
        val exact = ArrayList<Match>(2)
        val prefix = ArrayList<Match>(2)
        for (i in ALL.indices) {
            var hit = 0
            for (name in NAMES[i]) {
                if (name == query) {
                    hit = 2
                    break
                }
                if (query.length >= 3 && startsAtWord(name, query)) hit = 1
            }
            when (hit) {
                2 -> exact.add(Match(ALL[i], true))
                1 -> prefix.add(Match(ALL[i], false))
            }
        }
        exact.addAll(prefix)
        return if (exact.size > max) exact.subList(0, max) else exact
    }

    private fun startsAtWord(name: String, q: String): Boolean {
        if (name.startsWith(q)) return true
        var i = name.indexOf(' ')
        while (i >= 0) {
            if (name.startsWith(q, i + 1)) return true
            i = name.indexOf(' ', i + 1)
        }
        return false
    }
}
