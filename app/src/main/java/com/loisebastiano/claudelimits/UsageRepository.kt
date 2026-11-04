package com.loisebastiano.claudelimits

import android.content.Context
import kotlinx.coroutines.sync.Mutex

object UsageRepository {
    /** Where a refresh was started from; shown in the app to make problems easy to diagnose. */
    enum class Source { APP, WIDGET, TIMER, BACKGROUND }

    private val running = Mutex()

    /**
     * Downloads fresh data, stores it and redraws the widgets. Returns an error message or null.
     * Concurrent calls are skipped: the one already running will redraw everything.
     */
    suspend fun refresh(c: Context, source: Source): String? {
        Prefs.mode(c) ?: return c.getString(R.string.m_not_logged)
        if (!running.tryLock()) return null
        try {
            val err = try {
                val r = WebFetcher.fetch(c)
                Prefs.setSnapshot(c, UsageParser.parse(r.usage, r.plan))
                null
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                Prefs.setError(c, msg)
                msg
            }
            Prefs.setAttempt(c, source.name, err == null)
            WidgetRenderer.updateAll(c)
            return err
        } finally {
            running.unlock()
        }
    }
}
