package com.ahmedgeek.quicklaunch.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.index.UsageSource
import com.ahmedgeek.quicklaunch.shortcut.KeyboardShortcutService
import com.ahmedgeek.quicklaunch.shortcut.ShortcutDisclosureActivity
import com.ahmedgeek.quicklaunch.suggest.SystemShortcuts
import com.ahmedgeek.quicklaunch.suggest.WebSearch

/**
 * Settings, opened like any app: it is a launcher activity, so the card finds it by typing "settings"
 * or "qls". Plain views built from two row layouts; the list is small enough to rebuild on every change.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var list: LinearLayout
    private lateinit var inflater: LayoutInflater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs.get(this)
        list = findViewById(R.id.list)
        inflater = LayoutInflater.from(this)
        // Edge to edge on API 35+ and no action bar: the list scrolls between the system bars.
        val scroll = findViewById<View>(R.id.scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    /** Permissions can change while we are away in system settings. */
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()

        header(R.string.settings_search)
        toggle(R.string.settings_compact_pins, R.string.settings_compact_pins_summary, Prefs.COMPACT_PINS, default = false)
        toggle(R.string.settings_calculator, R.string.settings_calculator_summary, Prefs.CALCULATOR)
        toggle(R.string.settings_units, R.string.settings_units_summary, Prefs.UNITS)
        toggle(R.string.settings_clipboard, R.string.settings_clipboard_summary, Prefs.CLIPBOARD_LINK)
        toggle(R.string.settings_typed_url, R.string.settings_typed_url_summary, Prefs.TYPED_URL)
        toggle(R.string.settings_web, R.string.settings_web_summary, Prefs.WEB_SEARCH)
        toggle(R.string.settings_system, R.string.settings_system_summary, Prefs.SYSTEM_SETTINGS)

        if (prefs.getBoolean(Prefs.WEB_SEARCH, true)) {
            header(R.string.settings_engines)
            val engines = engines()
            for (i in engines.indices) {
                val e = engines[i]
                row(e.name, getString(R.string.settings_engine_summary, e.keyword), checked = e.enabled, onToggle = { on ->
                    val fresh = engines().toMutableList()
                    val f = fresh[i]
                    fresh[i] = WebSearch(f.keyword, f.name, f.url, on)
                    saveEngines(fresh)
                }) { editEngine(i) }
            }
            row(getString(R.string.settings_engine_add)) { editEngine(-1) }
            row(getString(R.string.settings_engine_reset)) {
                prefs.edit().remove(Prefs.WEB_ENGINES).apply()
                render()
            }
        }

        header(R.string.settings_aliases)
        val labels = HashMap<String, String>()
        for (e in QuickLaunchApp.get(this).index.awaitSnapshot()) labels[e.key] = e.label
        for (s in SystemShortcuts.ALL) labels[s.key] = s.title
        for ((alias, key) in QuickLaunchApp.get(this).aliases.all()) {
            row(alias, labels[key] ?: key.substringAfter('|')) { removeAlias(alias) }
        }
        row(getString(R.string.settings_alias_add), getString(R.string.settings_alias_add_summary)) { pickApp() }

        header(R.string.settings_permissions)
        status(R.string.settings_overlay, Settings.canDrawOverlays(this)) {
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        status(R.string.settings_usage, UsageSource.isGranted(this)) {
            open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        status(R.string.settings_shortcut, KeyboardShortcutService.isEnabled(this)) {
            open(Intent(this, ShortcutDisclosureActivity::class.java))
        }

        header(R.string.settings_about)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        row(getString(R.string.settings_version, version ?: "?"))
        row(getString(R.string.settings_author), getString(R.string.settings_source)) {
            open(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
        }
    }

    private companion object {
        const val SOURCE_URL = "https://github.com/AhmedTheGeek/QuickLaunch"
    }

    // ---- Rows ----------------------------------------------------------------------------------

    private fun header(title: Int) {
        val v = inflater.inflate(R.layout.settings_header, list, false) as TextView
        v.setText(title)
        list.addView(v)
    }

    /**
     * @param checked shows a switch in that state; null for a plain row
     * @param onToggle when set, the switch is its own tap target and [onClick] handles the rest of the row
     */
    private fun row(
        title: CharSequence,
        summary: CharSequence? = null,
        checked: Boolean? = null,
        onToggle: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null,
    ) {
        val v = inflater.inflate(R.layout.settings_row, list, false)
        v.findViewById<TextView>(R.id.title).text = title
        if (summary != null) {
            val s = v.findViewById<TextView>(R.id.summary)
            s.text = summary
            s.visibility = View.VISIBLE
        }
        if (checked != null) {
            val sw = v.findViewById<Switch>(R.id.toggle)
            sw.isChecked = checked
            sw.visibility = View.VISIBLE
            if (onToggle != null) {
                sw.setOnCheckedChangeListener { _, on -> onToggle(on) }
            } else {
                sw.isClickable = false
            }
        }
        if (onClick != null) v.setOnClickListener { onClick() } else v.background = null
        list.addView(v)
    }

    private fun toggle(title: Int, summary: Int, key: String, default: Boolean = true) {
        val on = prefs.getBoolean(key, default)
        row(getText(title), getText(summary), checked = on) {
            prefs.edit().putBoolean(key, !on).apply()
            render()
        }
    }

    private fun status(title: Int, granted: Boolean, onClick: () -> Unit) {
        row(getText(title), getText(if (granted) R.string.settings_on else R.string.settings_off), onClick = onClick)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            Log.w(QuickLaunchApp.TAG, "settings target unavailable", e)
        }
    }

    // ---- Aliases -------------------------------------------------------------------------------

    /** Settings pages and the flashlight first, then every app. */
    private fun pickApp() {
        val apps = QuickLaunchApp.get(this).index.awaitSnapshot()
        val system = SystemShortcuts.ALL
        val names = Array<CharSequence>(system.size + apps.size) { i ->
            if (i < system.size) return@Array getString(R.string.settings_alias_system, system[i].title)
            val e = apps[i - system.size]
            if (e.isWork) getString(R.string.settings_alias_work, e.label) else e.label
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_alias_pick)
            .setItems(names) { _, i ->
                if (i < system.size) askAlias(system[i].title, system[i].key) else askAlias(apps[i - system.size].label, apps[i - system.size].key)
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun askAlias(title: String, key: String) {
        val pad = (resources.displayMetrics.density * 20).toInt()
        val input = EditText(this).apply {
            setHint(R.string.settings_alias_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
        }
        val frame = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(frame)
            .setPositiveButton(R.string.settings_save, null)
            .setNegativeButton(R.string.settings_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (QuickLaunchApp.get(this).aliases.set(input.text.toString(), key)) {
                    dialog.dismiss()
                    render()
                } else {
                    input.error = getString(R.string.settings_alias_bad)
                }
            }
        }
        dialog.show()
    }

    private fun removeAlias(alias: String) {
        AlertDialog.Builder(this)
            .setTitle(alias)
            .setPositiveButton(R.string.settings_delete) { _, _ ->
                QuickLaunchApp.get(this).aliases.remove(alias)
                render()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    // ---- Search engines ------------------------------------------------------------------------

    private fun engines(): List<WebSearch> = WebSearch.parse(prefs.getString(Prefs.WEB_ENGINES, null))

    private fun saveEngines(engines: List<WebSearch>) {
        prefs.edit().putString(Prefs.WEB_ENGINES, WebSearch.serialize(engines)).apply()
    }

    /** Add ([index] -1) or edit an engine. The dialog stays open until the fields are valid. */
    private fun editEngine(index: Int) {
        val engines = engines().toMutableList()
        val current = engines.getOrNull(index)
        val pad = (resources.displayMetrics.density * 20).toInt()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        fun field(hint: Int, value: String?, type: Int): EditText = EditText(this).apply {
            setHint(hint)
            setText(value)
            inputType = type
            isSingleLine = true
            form.addView(this)
        }
        val name = field(R.string.settings_engine_name, current?.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val keyword = field(R.string.settings_engine_keyword, current?.keyword, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val url = field(R.string.settings_engine_url, current?.url, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)

        val builder = AlertDialog.Builder(this)
            .setTitle(current?.name ?: getString(R.string.settings_engine_add))
            .setView(form)
            .setPositiveButton(R.string.settings_save, null)
            .setNegativeButton(R.string.settings_cancel, null)
        if (current != null) {
            builder.setNeutralButton(R.string.settings_delete) { _, _ ->
                engines.removeAt(index)
                saveEngines(engines)
                render()
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val k = keyword.text.toString().trim().lowercase()
                val u = url.text.toString().trim()
                val n = name.text.toString().trim().replace('\t', ' ').ifEmpty { k }
                val taken = engines.withIndex().any { (i, e) -> i != index && e.keyword == k }
                when {
                    !WebSearch.isValidKeyword(k) || taken -> keyword.error = getString(R.string.settings_engine_bad_keyword)
                    !u.startsWith("http://") && !u.startsWith("https://") || !u.contains("%s") || u.any { it.isWhitespace() } ->
                        url.error = getString(R.string.settings_engine_bad_url)
                    else -> {
                        val e = WebSearch(k, n, u, current?.enabled ?: true)
                        if (current != null) engines[index] = e else engines.add(e)
                        saveEngines(engines)
                        dialog.dismiss()
                        render()
                    }
                }
            }
        }
        dialog.show()
    }
}
