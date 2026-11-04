package com.loisebastiano.claudelimits

import android.content.Context

object Prefs {
    const val MODE_WEB = "web"

    private fun p(c: Context) = c.getSharedPreferences("limits", Context.MODE_PRIVATE)

    /** Only claude.ai sign-in is supported (token mode removed: Anthropic terms forbid subscription OAuth tokens in third-party apps). */
    fun mode(c: Context): String? = p(c).getString("mode", null)?.takeIf { it == MODE_WEB }
    fun org(c: Context): String? = p(c).getString("org", null)
    fun error(c: Context): String? = p(c).getString("error", null)
    fun interval(c: Context): Int = p(c).getInt("interval", 30)
    fun snapshot(c: Context): Snapshot? = Snapshot.fromJson(p(c).getString("snapshot", null))

    fun setWeb(c: Context) = p(c).edit().putString("mode", MODE_WEB).remove("token").remove("org")
        .remove("snapshot").remove("error").apply()

    fun setOrg(c: Context, o: String) = p(c).edit().putString("org", o).apply()
    fun setError(c: Context, e: String?) = p(c).edit().putString("error", e).apply()
    fun setInterval(c: Context, m: Int) = p(c).edit().putInt("interval", m).apply()
    fun setSnapshot(c: Context, s: Snapshot) = p(c).edit().putString("snapshot", s.toJson()).remove("error").apply()

    fun logout(c: Context) {
        val keep = interval(c)
        p(c).edit().clear().putInt("interval", keep).apply()
    }
}
