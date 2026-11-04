package com.loisebastiano.claudelimits

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UsageWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.loisebastiano.claudelimits.REFRESH"
        const val ACTION_TICK = "com.loisebastiano.claudelimits.TICK"
    }

    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> if (Prefs.mode(c) != null) {
                WidgetRenderer.updateAll(c, loading = true)
                refreshInReceiver(c, UsageRepository.Source.WIDGET)
            }
            ACTION_TICK -> if (Prefs.mode(c) != null) {
                Scheduler.scheduleTick(c)
                refreshInReceiver(c, UsageRepository.Source.TIMER)
            }
            else -> super.onReceive(c, intent)
        }
    }

    /**
     * Runs the refresh right here instead of handing it to WorkManager, which may postpone
     * one-time work (battery optimizations) — the tap would then appear to do nothing.
     * goAsync() keeps the receiver alive; a fetch takes a few seconds, well under the limit.
     */
    private fun refreshInReceiver(c: Context, source: UsageRepository.Source) {
        val pending = goAsync()
        val app = c.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { UsageRepository.refresh(app, source) } finally { pending.finish() }
        }
    }

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetRenderer.update(c, m, it) }
        if (Prefs.mode(c) != null) {
            Scheduler.start(c, replace = false)
            val snap = Prefs.snapshot(c)
            if (snap == null || System.currentTimeMillis() - snap.fetchedAt > Prefs.interval(c) * 60_000L) UsageWorker.refreshNow(c)
        }
    }

    /** Called when the user resizes the widget: redraw at the new size. */
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetRenderer.update(c, m, id)
    }

    override fun onDisabled(c: Context) {
        Scheduler.stop(c)
    }
}
