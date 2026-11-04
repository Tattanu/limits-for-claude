package com.loisebastiano.claudelimits

import android.content.Context

object Prefs {
    const val MODE_WEB = "web"

    private fun p(c: Context) = c.getSharedPreferences("limits", Context.MODE_PRIVATE)

    /** Only claude.ai sign-in is supported (token mode removed: Anthropic terms forbid subscription OAuth tokens in third-party apps). */
    fun mode(c: Context): String? = p(c).getString("mode", null)?.takeIf { it == MODE_WEB }
    fun org(c: Context): String? = p(c).getString("org", null)
    fun error(c: Context): String? = p(c).getString("error", null)
    fun snapshot(c: Context): Snapshot? = Snapshot.fromJson(p(c).getString("snapshot", null))

    fun setWeb(c: Context) = p(c).edit().putString("mode", MODE_WEB).remove("org").remove("snapshot").remove("error").apply()
    fun setOrg(c: Context, o: String) = p(c).edit().putString("org", o).apply()
    fun setError(c: Context, e: String?) = p(c).edit().putString("error", e).apply()
    /** Last refresh attempt: time, source and outcome. */
    fun attempt(c: Context): Triple<Long, String, Boolean>? =
        p(c).getLong("attemptAt", 0L).takeIf { it > 0 }?.let {
            Triple(it, p(c).getString("attemptSource", "") ?: "", p(c).getBoolean("attemptOk", false))
        }
    fun setAttempt(c: Context, source: String, ok: Boolean) = p(c).edit().putLong("attemptAt", System.currentTimeMillis())
        .putString("attemptSource", source).putBoolean("attemptOk", ok).apply()
    fun setSnapshot(c: Context, s: Snapshot) = p(c).edit().putString("snapshot", s.toJson()).remove("error").apply()

    fun logout(c: Context) {
        val keep = p(c).all.filterKeys { it.startsWith("s_") }
        val e = p(c).edit().clear()
        keep.forEach { (k, v) ->
            when (v) {
                is Int -> e.putInt(k, v); is Boolean -> e.putBoolean(k, v); is String -> e.putString(k, v)
                is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
            }
        }
        e.apply()
    }

    // ---- Appearance and behaviour (keys start with "s_" so they survive sign-out) ----
    fun style(c: Context) = p(c).getString("s_style", Style.RING.name)!!.let { runCatching { Style.valueOf(it) }.getOrDefault(Style.RING) }
    fun setStyle(c: Context, s: Style) = p(c).edit().putString("s_style", s.name).apply()
    fun traffic(c: Context) = p(c).getBoolean("s_traffic", true)
    fun setTraffic(c: Context, v: Boolean) = p(c).edit().putBoolean("s_traffic", v).apply()
    fun accent(c: Context) = p(c).getInt("s_accent", Palette.ACCENTS[0])
    fun setAccent(c: Context, v: Int) = p(c).edit().putInt("s_accent", v).apply()
    /** 0 = follow system light/dark, otherwise an opaque color. */
    fun background(c: Context) = p(c).getInt("s_bg", 0)
    fun setBackground(c: Context, v: Int) = p(c).edit().putInt("s_bg", v).apply()
    /** 0..100 */
    fun opacity(c: Context) = p(c).getInt("s_opacity", 92)
    fun setOpacity(c: Context, v: Int) = p(c).edit().putInt("s_opacity", v).apply()
    /** 0 = automatic, otherwise a color. */
    fun textColor(c: Context) = p(c).getInt("s_text", 0)
    fun setTextColor(c: Context, v: Int) = p(c).edit().putInt("s_text", v).apply()
    fun showHeader(c: Context) = p(c).getBoolean("s_header", true)
    fun setShowHeader(c: Context, v: Boolean) = p(c).edit().putBoolean("s_header", v).apply()
    fun showReset(c: Context) = p(c).getBoolean("s_reset", true)
    fun setShowReset(c: Context, v: Boolean) = p(c).edit().putBoolean("s_reset", v).apply()
    /** true: product counters as share of the weekly limit; false: share of what you used this week. */
    fun productsOfLimit(c: Context) = p(c).getBoolean("s_prod_limit", true)
    fun setProductsOfLimit(c: Context, v: Boolean) = p(c).edit().putBoolean("s_prod_limit", v).apply()
    fun hidden(c: Context): Set<String> = p(c).getStringSet("s_hidden", emptySet())!!.toSet()
    fun setHidden(c: Context, key: String, hide: Boolean) {
        val s = hidden(c).toMutableSet(); if (hide) s += key else s -= key
        p(c).edit().putStringSet("s_hidden", s).apply()
    }
    fun interval(c: Context) = p(c).getInt("s_interval", 5)
    fun setInterval(c: Context, m: Int) = p(c).edit().putInt("s_interval", m).apply()
}

enum class Style { RING, BATTERY, VBAR, HBAR }

object Palette {
    val ACCENTS = intArrayOf(
        0xFFD97757.toInt(), 0xFF0A84FF.toInt(), 0xFF34C759.toInt(), 0xFFAF52DE.toInt(),
        0xFFFF9F0A.toInt(), 0xFFFF375F.toInt(), 0xFF64D2FF.toInt(), 0xFF8E8E93.toInt(),
    )
    /** First entry 0 = automatic (system light/dark). */
    val BACKGROUNDS = intArrayOf(
        0, 0xFF000000.toInt(), 0xFF1F1F22.toInt(), 0xFF1C2541.toInt(), 0xFF2D2A26.toInt(),
        0xFFFFFFFF.toInt(), 0xFFF3EEE6.toInt(), 0xFFE8F0FE.toInt(),
    )
    /** First entry 0 = automatic (contrast with the background). */
    val TEXTS = intArrayOf(0, 0xFFFFFFFF.toInt(), 0xFF1C1B1F.toInt())

    const val GREEN = 0xFF34C759.toInt()
    const val ORANGE = 0xFFFF9F0A.toInt()
    const val RED = 0xFFFF453A.toInt()
}
