package com.loisebastiano.claudelimits

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.format.DateFormat
import java.util.Date
import java.util.Locale

/** One thing to draw: a limit or a product counter. [used] is 0..100. */
data class Item(val key: String, val label: String, val used: Double, val resetsAt: Long?, val isProduct: Boolean)

object Items {
    fun all(c: Context, s: Snapshot): List<Item> {
        val out = s.limits.map { Item(it.kind, limitLabel(c, it.kind), it.usedPercent, it.resetsAt, false) }.toMutableList()
        val week = s.limits.firstOrNull { it.kind == "weekly_all" }
        val ofLimit = Prefs.productsOfLimit(c) && week != null
        s.products.forEach { p ->
            val used = if (ofLimit) p.share * week.usedPercent / 100.0 else p.share
            out += Item("product:" + p.key, productLabel(c, p), used, week?.resetsAt, true)
        }
        return out
    }

    fun visible(c: Context, s: Snapshot): List<Item> {
        val hidden = Prefs.hidden(c)
        return all(c, s).filter { it.key !in hidden }
    }

    fun limitLabel(c: Context, kind: String): String {
        val base = kind.substringBefore(':')
        val scope = kind.substringAfter(':', "")
        return when (if (scope.isNotBlank()) "" else base) {
            "session" -> c.getString(R.string.l_session)
            "weekly_all" -> c.getString(R.string.l_week)
            "weekly_opus" -> c.getString(R.string.l_week_opus)
            "weekly_sonnet" -> c.getString(R.string.l_week_sonnet)
            "weekly_cowork" -> c.getString(R.string.l_cowork)
            "weekly_oauth_apps" -> c.getString(R.string.l_apps)
            "extra_usage" -> c.getString(R.string.l_extra)
            else -> when {
                scope.isBlank() -> pretty(base)
                "week" in base || "seven_day" in base -> c.getString(R.string.l_week_model, scope)
                "session" in base || "five_hour" in base -> c.getString(R.string.l_session_model, scope)
                else -> pretty(scope)
            }
        }
    }

    private fun productLabel(c: Context, p: Product) = when (p.key) {
        "claude_code" -> "Code"
        "chat" -> "Chat"
        "cowork" -> "Cowork"
        "other" -> c.getString(R.string.l_other)
        else -> p.name
    }

    private fun pretty(s: String) = s.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /** "21:49" today, otherwise "Thu 04:59", in the user's locale. */
    fun clock(c: Context, ms: Long): String {
        val skeleton = if (Math.abs(ms - System.currentTimeMillis()) < 20 * 3600_000L) "Hm" else "EEEHm"
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
            if (DateFormat.is24HourFormat(c)) skeleton else skeleton.replace("H", "h") + "a")
        return DateFormat.format(pattern, Date(ms)).toString()
    }

    /** Time until reset: "4h 27m", "27m", or the day and time when farther than 24 h. */
    fun untilReset(c: Context, ms: Long): String {
        val d = ms - System.currentTimeMillis()
        if (d <= 0) return c.getString(R.string.w_now)
        if (d >= 24 * 3600_000L) return clock(c, ms)
        val h = d / 3600_000L
        val m = (d % 3600_000L) / 60_000L
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun isNight(c: Context) =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun cornerRadiusPx(c: Context): Float =
        if (Build.VERSION.SDK_INT >= 31) c.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
        else 20 * c.resources.displayMetrics.density
}
