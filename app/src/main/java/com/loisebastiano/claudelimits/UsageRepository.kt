package com.loisebastiano.claudelimits

import android.content.Context

object UsageRepository {
    /** Downloads fresh data, stores it and redraws the widgets. Returns an error message or null. */
    suspend fun refresh(c: Context): String? {
        Prefs.mode(c) ?: return c.getString(R.string.m_not_logged)
        val err = try {
            val r = WebFetcher.fetch(c)
            Prefs.setSnapshot(c, UsageParser.parse(r.usage, r.plan))
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
