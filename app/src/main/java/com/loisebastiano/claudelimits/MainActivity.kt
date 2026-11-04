package com.loisebastiano.claudelimits

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = MainScope()
    private lateinit var status: TextView
    private lateinit var limitsBox: LinearLayout
    private lateinit var refresh: Button
    private lateinit var logout: Button
    private val intervals = intArrayOf(15, 30, 60)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        fun text(s: CharSequence, size: Float = 15f) = TextView(this).apply {
            text = s; textSize = size; setPadding(0, dp(6), 0, dp(6))
        }.also { col.addView(it) }
        fun button(res: Int, onClick: () -> Unit) = Button(this).apply {
            setText(res); setOnClickListener { onClick() }
        }.also { col.addView(it) }

        text(getString(R.string.app_name), 24f).setTypeface(null, android.graphics.Typeface.BOLD)
        status = text("")
        limitsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(limitsBox)

        button(R.string.btn_login) { startActivityForResult(Intent(this, LoginActivity::class.java), 1) }
        text(getString(R.string.m_google_hint), 13f)
        refresh = button(R.string.btn_refresh) { doRefresh() }

        text(getString(R.string.m_interval), 15f)
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        intervals.forEach { m ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = getString(R.string.m_minutes, m); tag = m
                isChecked = Prefs.interval(this@MainActivity) == m
            })
        }
        group.setOnCheckedChangeListener { g, id ->
            val m = g.findViewById<View>(id).tag as Int
            Prefs.setInterval(this, m)
            if (Prefs.mode(this) != null) UsageWorker.schedule(this, replace = true)
        }
        col.addView(group)

        logout = button(R.string.btn_logout) { doLogout() }
        text(getString(R.string.m_widget_hint), 13f)
        text(getString(R.string.m_disclaimer), 12f).alpha = 0.7f

        val root = ScrollView(this).apply { addView(col) }
        root.padForSystemBars()
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) onLoggedIn()
    }

    private fun onLoggedIn() {
        UsageWorker.schedule(this, replace = true)
        doRefresh()
    }

    private fun doRefresh() {
        if (Prefs.mode(this) == null) return
        refresh.isEnabled = false
        status.text = getString(R.string.w_loading)
        WidgetRenderer.updateAll(this, loading = true)
        scope.launch {
            UsageRepository.refresh(this@MainActivity)
            refresh.isEnabled = true
            render()
        }
    }

    private fun doLogout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        UsageWorker.cancel(this)
        Prefs.logout(this)
        WidgetRenderer.updateAll(this)
        render()
    }

    private fun render() {
        val mode = Prefs.mode(this)
        val snap = Prefs.snapshot(this)
        val err = Prefs.error(this)
        val lines = mutableListOf<String>()
        lines += when (mode) {
            Prefs.MODE_WEB -> getString(R.string.m_mode_web)
            else -> getString(R.string.m_not_logged)
        }
        snap?.plan?.let { lines += getString(R.string.m_plan, it) }
        snap?.let { lines += getString(R.string.m_last_update, WidgetRenderer.time(this, it.fetchedAt)) }
        err?.let { lines += getString(R.string.m_error, it) }
        status.text = lines.joinToString("\n")
        logout.visibility = if (mode != null) View.VISIBLE else View.GONE
        refresh.visibility = if (mode != null) View.VISIBLE else View.GONE

        limitsBox.removeAllViews()
        val size = dp(44)
        val track = getColor(R.color.ring_track)
        snap?.limits?.forEach { l ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(ImageView(this).apply {
                setImageBitmap(WidgetRenderer.ring(l.remainingPercent, WidgetRenderer.color(l.remainingPercent), track, size))
            }, LinearLayout.LayoutParams(size, size))
            val txt = getString(R.string.m_limit_line, WidgetRenderer.label(this, l.kind), l.remainingPercent) +
                (l.resetsAt?.let { "\n" + getString(R.string.m_resets, WidgetRenderer.time(this, it)) } ?: "")
            row.addView(TextView(this).apply { text = txt; textSize = 15f; setPadding(dp(12), 0, 0, 0) })
            limitsBox.addView(row)
        }
    }
}
