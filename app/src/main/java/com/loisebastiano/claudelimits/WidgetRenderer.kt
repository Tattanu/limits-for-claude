package com.loisebastiano.claudelimits

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import kotlin.math.sqrt

object WidgetRenderer {
    /** Keeps each widget bitmap well inside the launcher's memory budget. */
    private const val MAX_PIXELS = 1_400_000f

    fun updateAll(c: Context, loading: Boolean = false) {
        val m = AppWidgetManager.getInstance(c)
        m.getAppWidgetIds(ComponentName(c, UsageWidgetProvider::class.java)).forEach { update(c, m, it, loading) }
    }

    fun update(c: Context, m: AppWidgetManager, id: Int, loading: Boolean = false) {
        val o = m.getAppWidgetOptions(id)
        // Portrait size: min width x max height (as documented for AppWidgetManager options).
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 320
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 150
        m.updateAppWidget(id, build(c, wDp, hDp, loading))
    }

    fun build(c: Context, wDp: Int, hDp: Int, loading: Boolean): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_usage)
        rv.setImageViewBitmap(R.id.canvas, bitmap(c, wDp.toFloat(), hDp.toFloat(), loading))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = if (Prefs.mode(c) == null) LoginActivity::class.java else MainActivity::class.java
        rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0,
            Intent(c, open).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags))
        rv.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, 1,
            Intent(c, UsageWidgetProvider::class.java).setAction(UsageWidgetProvider.ACTION_REFRESH), flags))
        return rv
    }

    /** Also used by the live preview in the app. */
    fun bitmap(c: Context, wDp: Float, hDp: Float, loading: Boolean, look: Look = Look.from(c)): Bitmap {
        var scale = c.resources.displayMetrics.density
        val px = wDp * hDp * scale * scale
        if (px > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / px)
        val mode = Prefs.mode(c)
        val snap = Prefs.snapshot(c)
        val error = Prefs.error(c)
        val title = "Claude" + (snap?.plan?.let { " $it" } ?: "")
        val stamp = when {
            loading -> "↻ …"
            snap != null -> (if (error != null) "⚠ " else "") + "↻ " + Items.clock(c, snap.fetchedAt)
            else -> "↻"
        }
        val message = when {
            mode == null -> c.getString(R.string.w_login)
            snap == null -> if (loading || error == null) c.getString(R.string.w_loading) else "⚠ $error"
            else -> null
        }
        val items = snap?.let { Items.visible(c, it) } ?: emptyList()
        val lookScaled = Look(look.style, look.traffic, look.accent, look.bg, look.opacity, look.text,
            look.showHeader, look.showReset, look.radius * scale / c.resources.displayMetrics.density)
        return WidgetDraw(c, lookScaled, wDp * scale, hDp * scale, scale).render(title, stamp, items, message)
    }
}
