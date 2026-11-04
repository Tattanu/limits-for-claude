package com.loisebastiano.claudelimits

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = MainScope()
    private lateinit var column: LinearLayout
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var itemsBox: LinearLayout
    private lateinit var loginBtn: Button
    private lateinit var refreshBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var trafficSwitch: Switch

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Int) = dp(v.toFloat())
    private val night get() = Items.isNight(this)
    private val cardColor get() = if (night) 0xFF26262A.toInt() else 0xFFF3F1F6.toInt()
    private val pageColor get() = if (night) 0xFF121214.toInt() else 0xFFFFFFFF.toInt()
    private val textColor get() = if (night) 0xFFF2F0F4.toInt() else 0xFF1C1B1F.toInt()
    private val subColor get() = if (night) 0xFFA8A4AD.toInt() else 0xFF6B6870.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }

        column.addView(TextView(this).apply {
            setText(R.string.app_name); textSize = 26f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor)
            setPadding(dp(4), dp(8), 0, dp(12))
        })

        // Live preview on a "wallpaper" so transparency is visible
        val frame = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF2B3A67.toInt(), 0xFF7A4069.toInt(), 0xFFE07A5F.toInt())).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        preview = ImageView(this).apply { adjustViewBounds = true }
        frame.addView(preview)
        column.addView(frame)

        // Account
        val acc = section(R.string.sec_account)
        status = label(acc, "", 14f, subColor)
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        loginBtn = button(btns, R.string.btn_login) { startActivityForResult(Intent(this, LoginActivity::class.java), 1) }
        refreshBtn = button(btns, R.string.btn_refresh) { doRefresh() }
        logoutBtn = button(btns, R.string.btn_logout) { doLogout() }
        acc.addView(btns)

        // Items
        val items = section(R.string.sec_items)
        label(items, getString(R.string.items_hint), 13f, subColor)
        itemsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.addView(itemsBox)
        switchRow(items, R.string.opt_products_of_limit, Prefs.productsOfLimit(this)) { Prefs.setProductsOfLimit(this, it); changed() }

        // Style
        val st = section(R.string.sec_style)
        choice(st, listOf(R.string.style_ring, R.string.style_battery, R.string.style_vbar, R.string.style_hbar),
            Style.values().indexOf(Prefs.style(this))) { Prefs.setStyle(this, Style.values()[it]); changed() }
        switchRow(st, R.string.opt_header, Prefs.showHeader(this)) { Prefs.setShowHeader(this, it); changed() }
        switchRow(st, R.string.opt_reset, Prefs.showReset(this)) { Prefs.setShowReset(this, it); changed() }

        // Colors
        val col = section(R.string.sec_colors)
        trafficSwitch = switchRow(col, R.string.opt_traffic, Prefs.traffic(this)) { Prefs.setTraffic(this, it); changed() }
        label(col, getString(R.string.c_accent), 14f, textColor)
        swatches(col, Palette.ACCENTS, Prefs.accent(this)) { Prefs.setAccent(this, it); Prefs.setTraffic(this, false); trafficSwitch.isChecked = false; changed() }
        label(col, getString(R.string.c_background), 14f, textColor)
        swatches(col, Palette.BACKGROUNDS, Prefs.background(this)) { Prefs.setBackground(this, it); changed() }
        val op = label(col, getString(R.string.c_opacity, Prefs.opacity(this)), 14f, textColor)
        col.addView(SeekBar(this).apply {
            max = 100; progress = Prefs.opacity(this@MainActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    Prefs.setOpacity(this@MainActivity, v); op.text = getString(R.string.c_opacity, v); renderPreview()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) { changed() }
            })
        })
        label(col, getString(R.string.c_text), 14f, textColor)
        swatches(col, Palette.TEXTS, Prefs.textColor(this)) { Prefs.setTextColor(this, it); changed() }

        // Refresh
        val rf = section(R.string.sec_refresh)
        val mins = listOf(1, 2, 5, 10, 15, 30, 60)
        choiceText(rf, mins.map { getString(R.string.m_minutes, it) }, mins.indexOf(Prefs.interval(this))) {
            Prefs.setInterval(this, mins[it]); if (Prefs.mode(this) != null) Scheduler.start(this, replace = true)
        }
        label(rf, getString(R.string.refresh_hint), 12f, subColor)

        label(column, getString(R.string.m_widget_hint), 13f, subColor)
        label(column, getString(R.string.m_disclaimer), 12f, subColor).alpha = 0.8f

        val root = ScrollView(this).apply { addView(column); setBackgroundColor(pageColor) }
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
        if (requestCode == 1 && resultCode == RESULT_OK) doRefresh()
    }

    // ---------- building blocks ----------
    private fun section(title: Int): LinearLayout {
        column.addView(TextView(this).apply {
            setText(title); textSize = 13f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xFFD97757.toInt())
            isAllCaps = true; letterSpacing = 0.06f; setPadding(dp(6), dp(22), 0, dp(8))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(cardColor) }
        }
        column.addView(card)
        return card
    }

    private fun label(parent: ViewGroup, s: String, size: Float, color: Int) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); setPadding(0, dp(6), 0, dp(6))
    }.also { parent.addView(it) }

    private fun button(parent: ViewGroup, res: Int, onClick: () -> Unit) = Button(this).apply {
        setText(res); isAllCaps = false; setOnClickListener { onClick() }
    }.also { parent.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }

    private fun switchRow(parent: ViewGroup, res: Int, checked: Boolean, onChange: (Boolean) -> Unit) =
        switchRow(parent, getString(res), checked, onChange)

    private fun switchRow(parent: ViewGroup, s: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = s; textSize = 15f; setTextColor(textColor); isChecked = checked; setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }.also { parent.addView(it) }

    private fun choice(parent: ViewGroup, labels: List<Int>, selected: Int, onPick: (Int) -> Unit) =
        choiceText(parent, labels.map { getString(it) }, selected, onPick)

    private fun choiceText(parent: ViewGroup, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        labels.forEachIndexed { i, s ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = s; tag = i; setTextColor(textColor); isChecked = i == selected
                setPadding(dp(2), 0, dp(12), 0)
            })
        }
        group.setOnCheckedChangeListener { g, id -> onPick(g.findViewById<View>(id).tag as Int) }
        parent.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(group) })
    }

    /** Row of color circles; value 0 means "automatic". */
    private fun swatches(parent: ViewGroup, palette: IntArray, selected: Int, onPick: (Int) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, dp(8)) }
        val size = dp(34)
        fun paint(v: TextView, color: Int, on: Boolean) {
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            if (color == 0) {
                d.orientation = GradientDrawable.Orientation.TL_BR
                d.colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFF1F1F22.toInt())
            } else d.setColor(color)
            d.setStroke(if (on) dp(3) else dp(1), if (on) 0xFFD97757.toInt() else 0x55888888)
            v.background = d
        }
        palette.forEach { color ->
            val v = TextView(this)
            v.gravity = Gravity.CENTER; v.textSize = 12f; v.setTypeface(v.typeface, Typeface.BOLD)
            v.text = if (color == 0) "A" else ""
            v.setTextColor(Color.GRAY)
            v.tag = color
            paint(v, color, color == selected)
            v.setOnClickListener {
                for (i in 0 until row.childCount) { val o = row.getChildAt(i) as TextView; paint(o, o.tag as Int, o === v) }
                onPick(color)
            }
            row.addView(v, LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(10) })
        }
        parent.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }

    // ---------- behaviour ----------
    private fun changed() {
        renderPreview()
        WidgetRenderer.updateAll(this)
    }

    private fun renderPreview() {
        val wDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density) - 32 - 28
        preview.setImageBitmap(WidgetRenderer.bitmap(this, wDp, wDp * 0.46f, false))
    }

    private fun doRefresh() {
        if (Prefs.mode(this) == null) return
        refreshBtn.isEnabled = false
        status.text = getString(R.string.w_loading)
        WidgetRenderer.updateAll(this, loading = true)
        scope.launch {
            UsageRepository.refresh(this@MainActivity)
            refreshBtn.isEnabled = true
            render()
        }
    }

    private fun doLogout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        Scheduler.stop(this)
        Prefs.logout(this)
        WidgetRenderer.updateAll(this)
        render()
    }

    private fun render() {
        val mode = Prefs.mode(this)
        val snap = Prefs.snapshot(this)
        val err = Prefs.error(this)
        val lines = mutableListOf<String>()
        lines += if (mode != null) getString(R.string.m_mode_web) else getString(R.string.m_not_logged)
        snap?.plan?.let { lines += getString(R.string.m_plan, it) }
        snap?.let { lines += getString(R.string.m_last_update, Items.clock(this, it.fetchedAt)) }
        err?.let { lines += getString(R.string.m_error, it) }
        status.text = lines.joinToString("\n")
        loginBtn.visibility = if (mode == null) View.VISIBLE else View.GONE
        refreshBtn.visibility = if (mode != null) View.VISIBLE else View.GONE
        logoutBtn.visibility = if (mode != null) View.VISIBLE else View.GONE

        itemsBox.removeAllViews()
        val hidden = Prefs.hidden(this)
        val all = snap?.let { Items.all(this, it) } ?: emptyList()
        if (all.isEmpty()) label(itemsBox, getString(R.string.items_empty), 13f, subColor)
        all.forEach { item ->
            val pct = Math.round(item.used.coerceIn(0.0, 100.0))
            val name = (if (item.isProduct) "• " else "") + item.label + "  —  $pct%"
            switchRow(itemsBox, name, item.key !in hidden) { show -> Prefs.setHidden(this, item.key, !show); changed() }
        }
        renderPreview()
    }
}
