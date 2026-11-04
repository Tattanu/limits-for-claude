package com.loisebastiano.claudelimits

import android.content.Context

object UsageRepository {
    /** Downloads fresh data, stores it and redraws the widgets. Returns an error message or null. */
    suspend fun refresh(c: Context): String? {
        val mode = Prefs.mode(c) ?: return c.getString(R.string.m_not_logged)
        val err = try {
            val r = if (mode == Prefs.MODE_TOKEN) TokenApi.fetch(c) else WebFetcher.fetch(c)
            Prefs.setSnapshot(c, Snapshot(UsageParser.parse(r.usage), System.currentTimeMillis(), r.plan))
            null
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            Prefs.setError(c, msg)
            msg
        }
        WidgetRenderer.updateAll(c)
        return err
    }
}
