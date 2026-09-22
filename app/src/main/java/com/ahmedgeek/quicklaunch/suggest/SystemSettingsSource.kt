package com.ahmedgeek.quicklaunch.suggest

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.search.AliasStore

/**
 * `wifi`, `bluetooth`, `battery`, `flashlight`. An exact name or the user's alias ("fl") puts the row
 * above the apps; a partial name ("disp") puts it below, so "dis" still opens Discord first.
 */
class SystemSettingsSource(private val context: Context, private val aliases: AliasStore) : SuggestionSource {
    private val torch = Torch(context)

    override fun suggest(raw: String, query: String, out: MutableList<Suggestion>) {
        val q = query.trimEnd()
        val aliased = aliases.target(q)?.let { SystemShortcuts.byKey(it) }
        if (aliased != null) {
            row(aliased, belowApps = false)?.let { out.add(it) }
            return
        }
        for (m in SystemShortcuts.match(q)) row(m.shortcut, belowApps = !m.exact)?.let { out.add(it) }
    }

    private fun row(s: SystemShortcuts.Shortcut, belowApps: Boolean): Suggestion? {
        val action = s.action
        if (action == null) {
            if (!torch.available()) return null
            val badge = context.getText(if (torch.on) R.string.torch_turn_off else R.string.torch_turn_on)
            return Suggestion(s.key, s.title, badge, R.drawable.ic_flashlight, null, belowApps) { torch.toggle() }
        }
        return Suggestion(s.key, s.title, context.getText(R.string.system_setting), R.drawable.ic_settings, null, belowApps) { c ->
            open(c, action)
        }
    }

    private fun open(c: Context, action: String): Boolean = try {
        c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) {
        Log.w(QuickLaunchApp.TAG, "settings page missing: $action", e)
        Toast.makeText(c, R.string.error_setting_missing, Toast.LENGTH_SHORT).show()
        false
    }
}
