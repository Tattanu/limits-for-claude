package com.loisebastiano.claudelimits

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class UsageWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.loisebastiano.claudelimits.REFRESH"
        private const val STALE_MS = 10 * 60_000L
    }

    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            if (Prefs.mode(c) != null) {
                WidgetRenderer.updateAll(c, loading = true)
                UsageWorker.refreshNow(c)
            }
            return
        }
        super.onReceive(c, intent)
    }

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        m.updateAppWidget(ids, WidgetRenderer.build(c, false))
        if (Prefs.mode(c) != null) {
            UsageWorker.schedule(c, replace = false)
            val snap = Prefs.snapshot(c)
            if (snap == null || System.currentTimeMillis() - snap.fetchedAt > STALE_MS) UsageWorker.refreshNow(c)
        }
    }
}
