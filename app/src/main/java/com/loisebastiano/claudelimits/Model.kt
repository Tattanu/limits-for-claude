package com.loisebastiano.claudelimits

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/** One usage limit. [usedPercent] is 0..100 as reported by the server. */
data class Limit(val kind: String, val usedPercent: Double, val resetsAt: Long?)

/** Share of the weekly usage by product (Claude Code, chat, Cowork…), 0..100 of the weekly total. */
data class Product(val key: String, val name: String, val share: Double)

data class Snapshot(
    val limits: List<Limit>,
    val products: List<Product>,
    val fetchedAt: Long,
    val plan: String?,
    /** false when claude.ai answered but does not expose usage data for this account (seen as 403/404). */
    val usageAvailable: Boolean = true,
) {
    fun toJson(): String {
        val la = JSONArray()
        limits.forEach { l ->
            la.put(JSONObject().put("kind", l.kind).put("used", l.usedPercent).put("resets", l.resetsAt ?: JSONObject.NULL))
        }
        val pa = JSONArray()
        products.forEach { p -> pa.put(JSONObject().put("key", p.key).put("name", p.name).put("share", p.share)) }
        return JSONObject().put("limits", la).put("products", pa).put("fetchedAt", fetchedAt)
            .put("plan", plan ?: JSONObject.NULL).put("available", usageAvailable).toString()
    }

    companion object {
        fun fromJson(s: String?): Snapshot? = try {
            if (s == null) null else {
                val o = JSONObject(s)
                val la = o.getJSONArray("limits")
                val limits = (0 until la.length()).map { i ->
                    val l = la.getJSONObject(i)
                    Limit(l.getString("kind"), l.getDouble("used"), if (l.isNull("resets")) null else l.getLong("resets"))
                }
                val pa = o.optJSONArray("products") ?: JSONArray()
                val products = (0 until pa.length()).map { i ->
                    val p = pa.getJSONObject(i)
                    Product(p.getString("key"), p.getString("name"), p.getDouble("share"))
                }
                Snapshot(limits, products, o.getLong("fetchedAt"), if (o.isNull("plan")) null else o.getString("plan"),
                    o.optBoolean("available", true))
            }
        } catch (e: Exception) { null }
    }
}

/** [usage] is null when the usage endpoint is not available for this account. */
data class FetchResult(val usage: JSONObject?, val plan: String?)

class ApiException(msg: String) : Exception(msg)

object UsageParser {
    private val legacy = listOf(
        "five_hour" to "session",
        "seven_day" to "weekly_all",
        "seven_day_opus" to "weekly_opus",
        "seven_day_sonnet" to "weekly_sonnet",
        "seven_day_cowork" to "weekly_cowork",
        "seven_day_oauth_apps" to "weekly_oauth_apps",
    )

    fun parse(o: JSONObject?, plan: String?): Snapshot =
        if (o == null) Snapshot(emptyList(), emptyList(), System.currentTimeMillis(), plan, usageAvailable = false)
        else Snapshot(limits(o), products(o), System.currentTimeMillis(), plan)

    fun limits(o: JSONObject): List<Limit> {
        val out = mutableListOf<Limit>()
        val arr = o.optJSONArray("limits")
        if (arr != null && arr.length() > 0) {
            // Newer generic format: one entry per limit that applies to this plan.
            for (i in 0 until arr.length()) {
                val l = arr.optJSONObject(i) ?: continue
                if (!l.has("percent") || l.isNull("percent")) continue
                val kind = l.optString("kind", "limit")
                val scope = scopeName(l)
                out += Limit(if (scope != null) "$kind:$scope" else kind, l.optDouble("percent"), time(l, "resets_at"))
            }
        } else {
            // Older format: fixed keys, null when the limit does not apply.
            for ((key, kind) in legacy) {
                val l = o.optJSONObject(key) ?: continue
                if (!l.has("utilization") || l.isNull("utilization")) continue
                out += Limit(kind, l.optDouble("utilization"), time(l, "resets_at"))
            }
        }
        val ex = o.optJSONObject("extra_usage")
        if (ex != null && ex.optBoolean("is_enabled") && !ex.isNull("utilization")) {
            out += Limit("extra_usage", ex.optDouble("utilization"), null)
        }
        return out
    }

    /**
     * The scope of a limit as a short readable name. It used to be a plain string ("opus");
     * since November 2026 it can be an object like {"model":{"id":null,"display_name":"Fable"},"surface":null}.
     */
    fun scopeName(l: JSONObject): String? {
        if (!l.has("scope") || l.isNull("scope")) return null
        val raw = l.opt("scope")
        if (raw is JSONObject) {
            val parts = mutableListOf<String>()
            val m = raw.opt("model")
            when (m) {
                is JSONObject -> text(m, "display_name") ?: text(m, "name") ?: text(m, "id")
                is String -> m.takeIf { it.isNotBlank() }
                else -> null
            }?.let { parts += it }
            val sf = raw.opt("surface")
            when (sf) {
                is JSONObject -> text(sf, "display_name") ?: text(sf, "name") ?: text(sf, "id")
                is String -> sf.takeIf { it.isNotBlank() }
                else -> null
            }?.let { parts += it }
            return parts.joinToString(" ").takeIf { it.isNotBlank() }
        }
        val s = raw?.toString()?.trim().orEmpty()
        // Never show raw JSON as a label.
        return s.takeIf { it.isNotBlank() && !s.startsWith("{") && !s.startsWith("[") }
    }

    private fun text(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) null else o.optString(key).trim().takeIf { it.isNotBlank() }

    fun products(o: JSONObject): List<Product> {
        val rows = o.optJSONObject("seven_day_breakdown")?.optJSONArray("rows") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val r = rows.optJSONObject(i) ?: return@mapNotNull null
            if (!r.has("percent") || r.isNull("percent")) return@mapNotNull null
            val key = r.optString("key").ifBlank { "p$i" }
            Product(key, r.optString("display_name").ifBlank { key }, r.optDouble("percent"))
        }
    }

    private fun time(o: JSONObject, key: String): Long? {
        if (!o.has(key) || o.isNull(key)) return null
        return try { OffsetDateTime.parse(o.getString(key)).toInstant().toEpochMilli() } catch (e: Exception) { null }
    }
}

object Plans {
    fun label(raw: String?): String? {
        val s = raw?.lowercase() ?: return null
        return when {
            "enterprise" in s -> "Enterprise"
            "raven" in s || "team" in s -> "Team"
            "max" in s -> "Max"
            "pro" in s -> "Pro"
            "free" in s -> "Free"
            // A claude.ai organization with chat but no paid capability is a Free account.
            s.split(',').map { it.trim() }.contains("chat") -> "Free"
            else -> null
        }
    }
}
