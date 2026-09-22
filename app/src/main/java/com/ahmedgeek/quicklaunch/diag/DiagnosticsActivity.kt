package com.ahmedgeek.quicklaunch.diag

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.profileinstaller.ProfileVerifier
import com.ahmedgeek.quicklaunch.Bg
import com.ahmedgeek.quicklaunch.QuickLaunchApp
import com.ahmedgeek.quicklaunch.R
import java.text.DateFormat
import java.util.Date

/**
 * Hidden screen (tap the version in settings 7 times): usage counts, how fast this phone is at the
 * things Quick Launch does, and on demand a set of checks. Everything can be copied or shared as
 * plain text for a bug report. Nothing here runs unless this screen is open.
 */
class DiagnosticsActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var inflater: LayoutInflater
    private val app get() = QuickLaunchApp.get(this)

    /** Plain-text version of what's on screen, for copy and share. */
    private val report = StringBuilder()
    private var checks: List<SelfTest.Check>? = null
    private var running = false
    private var profile = "…"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<TextView>(R.id.settings_title)?.setText(R.string.diag_title)
        list = findViewById(R.id.list)
        inflater = LayoutInflater.from(this)
        val scroll = findViewById<View>(R.id.scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        Bg.icons.execute {
            val status = try {
                val s = ProfileVerifier.getCompilationStatusAsync().get()
                when {
                    s.isCompiledWithProfile -> getString(R.string.diag_profile_compiled)
                    s.hasProfileEnqueuedForCompilation() -> getString(R.string.diag_profile_pending)
                    else -> getString(R.string.diag_profile_none, s.profileInstallResultCode)
                }
            } catch (e: Exception) {
                getString(R.string.diag_profile_unknown)
            }
            Bg.main.post {
                profile = status
                if (!isFinishing) render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        report.setLength(0)
        report.append("Quick Launch diagnostics\n")

        section(R.string.diag_device)
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        item(getString(R.string.diag_app), "$version")
        item(getString(R.string.diag_phone), SelfTest.device())
        item(getString(R.string.diag_profile), profile)

        section(R.string.diag_usage)
        val t = Stats.totals(this)
        val since = if (t.since == 0L) getString(R.string.diag_today) else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.since))
        item(getString(R.string.diag_opens), "${t.opens}", getString(R.string.diag_since, since))
        item(getString(R.string.diag_launches), "${t.launches}")
        item(getString(R.string.diag_actions), "${t.actions}", getString(R.string.diag_actions_summary))

        section(R.string.diag_session)
        item(getString(R.string.diag_last_open), ms(Stats.lastOpenMs))
        item(getString(R.string.diag_cold_open), ms(Stats.coldOpenMs), getString(R.string.diag_cold_open_summary))
        val k = Stats.keys()
        item(getString(R.string.diag_keystroke), if (k.count == 0) "–" else "avg ${k.avgMicros} µs, worst ${k.maxMicros} µs", getString(R.string.diag_keystroke_summary, k.count))

        section(R.string.diag_index)
        val snap = app.index.snapshot
        item(getString(R.string.diag_apps), "${snap.size}", getString(R.string.diag_apps_summary, snap.count { it.isWork }))
        val ago = app.index.lastRevalidateAgoMs()
        item(getString(R.string.diag_refreshed), if (ago < 0) "–" else DateUtils.getRelativeTimeSpanString(System.currentTimeMillis() - ago).toString())
        item(getString(R.string.diag_history), "${app.index.frecency.size} apps", getString(R.string.diag_history_summary, app.index.pins.count, app.aliases.all().size))
        item(getString(R.string.diag_icons), "${app.icons.memoryCount()} in memory")

        section(R.string.diag_checks)
        val c = checks
        when {
            running -> item(getString(R.string.diag_running), "")
            c == null -> action(R.string.diag_run, R.string.diag_run_summary) { runChecks() }
            else -> {
                for (check in c) item("${mark(check.status)}  ${check.name}", check.detail)
                action(R.string.diag_run_again, 0) { runChecks() }
            }
        }

        section(R.string.diag_share)
        action(R.string.diag_copy, R.string.diag_copy_summary) { copy() }
        action(R.string.diag_share_action, 0) { share() }
        action(R.string.diag_reindex, 0) {
            app.index.revalidate(force = true)
            Toast.makeText(this, R.string.diag_reindex_done, Toast.LENGTH_SHORT).show()
            list.postDelayed({ render() }, 1500)
        }
        action(R.string.diag_clear_icons, R.string.diag_clear_icons_summary) {
            app.icons.clear()
            render()
        }
        action(R.string.diag_reset, 0) {
            Stats.reset(this)
            render()
        }
    }

    private fun runChecks() {
        if (running) return
        running = true
        render()
        Thread({
            val result = SelfTest(this).run()
            Bg.main.post {
                running = false
                checks = result
                if (!isFinishing) render()
            }
        }, "ql-selftest").start()
    }

    private fun mark(s: SelfTest.Status) = when (s) {
        SelfTest.Status.OK -> "✓"
        SelfTest.Status.WARN -> "!"
        SelfTest.Status.FAIL -> "✗"
        SelfTest.Status.INFO -> "·"
    }

    private fun ms(v: Long) = if (v < 0) "–" else "$v ms"

    private fun section(title: Int) {
        val v = inflater.inflate(R.layout.settings_header, list, false) as TextView
        v.setText(title)
        list.addView(v)
        report.append("\n").append(getString(title)).append("\n")
    }

    private fun item(title: String, value: String, summary: String? = null) {
        val v = inflater.inflate(R.layout.settings_row, list, false)
        v.findViewById<TextView>(R.id.title).text = if (value.isEmpty()) title else "$title: $value"
        if (summary != null) {
            v.findViewById<TextView>(R.id.summary).apply {
                text = summary
                visibility = View.VISIBLE
            }
        }
        v.background = null
        list.addView(v)
        report.append("  ").append(title)
        if (value.isNotEmpty()) report.append(": ").append(value)
        report.append("\n")
    }

    private fun action(title: Int, summary: Int, onClick: () -> Unit) {
        val v = inflater.inflate(R.layout.settings_row, list, false)
        v.findViewById<TextView>(R.id.title).setText(title)
        if (summary != 0) {
            v.findViewById<TextView>(R.id.summary).apply {
                setText(summary)
                visibility = View.VISIBLE
            }
        }
        v.setOnClickListener { onClick() }
        list.addView(v)
    }

    private fun copy() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Quick Launch diagnostics", report.toString()))
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun share() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report.toString())
        startActivity(Intent.createChooser(send, getString(R.string.diag_share_action)))
    }
}
