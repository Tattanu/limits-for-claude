package com.loisebastiano.claudelimits

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import java.util.Date
import java.util.Locale

object WidgetRenderer {
    private val cells = arrayOf(
        intArrayOf(R.id.cell0, R.id.ring0, R.id.pct0, R.id.label0, R.id.reset0),
        intArrayOf(R.id.cell1, R.id.ring1, R.id.pct1, R.id.label1, R.id.reset1),
        intArrayOf(R.id.cell2, R.id.ring2, R.id.pct2, R.id.label2, R.id.reset2),
        intArrayOf(R.id.cell3, R.id.ring3, R.id.pct3, R.id.label3, R.id.reset3),
    )

    fun updateAll(c: Context, loading: Boolean = false) {
        val m = AppWidgetManager.getInstance(c)
        val ids = m.getAppWidgetIds(ComponentName(c, UsageWidgetProvider::class.java))
        if (ids.isNotEmpty()) m.updateAppWidget(ids, build(c, loading))
    }

    fun build(c: Context, loading: Boolean): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_usage)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags))
        rv.setOnClickPendingIntent(R.id.header, PendingIntent.getBroadcast(c, 1,
            Intent(c, UsageWidgetProvider::class.java).setAction(UsageWidgetProvider.ACTION_REFRESH), flags))

        val mode = Prefs.mode(c)
        val snap = Prefs.snapshot(c)
        val error = Prefs.error(c)
        rv.setTextViewText(R.id.title, "Claude" + (snap?.plan?.let { " $it" } ?: ""))

        fun message(text: String) {
            rv.setViewVisibility(R.id.rings, View.GONE)
            rv.setViewVisibility(R.id.message, View.VISIBLE)
            rv.setTextViewText(R.id.message, text)
        }

        val stamp = when {
            loading -> "↻ …"
            snap != null -> "↻ " + time(c, snap.fetchedAt) + (if (error != null) " ⚠" else "")
            else -> "↻"
        }
        rv.setTextViewText(R.id.updated, stamp)

        when {
            mode == null -> message(c.getString(R.string.w_login))
            snap == null -> message(if (loading || error == null) c.getString(R.string.w_loading) else "⚠ $error")
            snap.limits.isEmpty() -> message(c.getString(R.string.w_no_data))
            else -> {
                rv.setViewVisibility(R.id.message, View.GONE)
                rv.setViewVisibility(R.id.rings, View.VISIBLE)
                val size = (56 * c.resources.displayMetrics.density).toInt()
                val track = c.getColor(R.color.ring_track)
                cells.forEachIndexed { i, ids ->
                    val l = snap.limits.getOrNull(i)
                    if (l == null) { rv.setViewVisibility(ids[0], View.GONE); return@forEachIndexed }
                    rv.setViewVisibility(ids[0], View.VISIBLE)
                    rv.setImageViewBitmap(ids[1], ring(l.remainingPercent, color(l.remainingPercent), track, size))
                    rv.setTextViewText(ids[2], "${l.remainingPercent}%")
                    rv.setTextViewText(ids[3], label(c, l.kind))
                    rv.setTextViewText(ids[4], l.resetsAt?.let { "↺ " + time(c, it) } ?: "")
                }
            }
        }
        return rv
    }

    fun color(remaining: Int): Int = when {
        remaining > 50 -> 0xFF34C759.toInt()
        remaining > 20 -> 0xFFFF9F0A.toInt()
        else -> 0xFFFF453A.toInt()
    }

    fun ring(remaining: Int, color: Int, track: Int, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val stroke = size * 0.11f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        val r = RectF(stroke / 2 + 1, stroke / 2 + 1, size - stroke / 2 - 1, size - stroke / 2 - 1)
        p.color = track
        cv.drawArc(r, 0f, 360f, false, p)
        if (remaining > 0) {
            p.color = color
            cv.drawArc(r, -90f, 360f * remaining / 100f, false, p)
        }
        return b
    }

    fun label(c: Context, kind: String): String {
        val base = kind.substringBefore(':')
        val scope = kind.substringAfter(':', "")
        return when (base) {
            "session" -> c.getString(R.string.l_session)
            "weekly_all" -> c.getString(R.string.l_week)
            "weekly_opus" -> c.getString(R.string.l_week_opus)
            "weekly_sonnet" -> c.getString(R.string.l_week_sonnet)
            "weekly_cowork" -> c.getString(R.string.l_cowork)
            "weekly_oauth_apps" -> c.getString(R.string.l_apps)
            "extra_usage" -> c.getString(R.string.l_extra)
            else -> pretty(scope.ifBlank { base })
        }
    }

    private fun pretty(s: String) = s.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /** "21:49" if within 24 h, otherwise "Thu 04:59" in the user's locale. */
    fun time(c: Context, ms: Long): String {
        val skeleton = if (Math.abs(ms - System.currentTimeMillis()) < 24 * 3600_000L) "Hm" else "EEEHm"
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
            if (DateFormat.is24HourFormat(c)) skeleton else skeleton.replace("H", "h") + "a")
        return DateFormat.format(pattern, Date(ms)).toString()
    }
}
