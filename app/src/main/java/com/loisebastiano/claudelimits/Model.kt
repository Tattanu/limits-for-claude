package com.loisebastiano.claudelimits

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/** One usage limit. [usedPercent] is 0..100 as reported by the server. */
data class Limit(val kind: String, val usedPercent: Double, val resetsAt: Long?) {
    val remainingPercent: Int get() = (100.0 - usedPercent).coerceIn(0.0, 100.0).toInt()
}

data class Snapshot(val limits: List<Limit>, val fetchedAt: Long, val plan: String?) {
    fun toJson(): String {
        val arr = JSONArray()
        limits.forEach { l ->
            arr.put(JSONObject().put("kind", l.kind).put("used", l.usedPercent)
                .put("resets", l.resetsAt ?: JSONObject.NULL))
        }
        return JSONObject().put("limits", arr).put("fetchedAt", fetchedAt)
            .put("plan", plan ?: JSONObject.NULL).toString()
    }

    companion object {
        fun fromJson(s: String?): Snapshot? = try {
            if (s == null) null else {
                val o = JSONObject(s)
                val arr = o.getJSONArray("limits")
                val list = (0 until arr.length()).map { i ->
                    val l = arr.getJSONObject(i)
                    Limit(l.getString("kind"), l.getDouble("used"),
                        if (l.isNull("resets")) null else l.getLong("resets"))
                }
                Snapshot(list, o.getLong("fetchedAt"), if (o.isNull("plan")) null else o.getString("plan"))
            }
        } catch (e: Exception) { null }
    }
}

data class FetchResult(val usage: JSONObject, val plan: String?)

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

    fun parse(o: JSONObject): List<Limit> {
        val out = mutableListOf<Limit>()
        val arr = o.optJSONArray("limits")
        if (arr != null && arr.length() > 0) {
            // Newer generic format: one entry per limit that applies to this plan.
            for (i in 0 until arr.length()) {
                val l = arr.optJSONObject(i) ?: continue
                if (!l.has("percent") || l.isNull("percent")) continue
                val kind = l.optString("kind", "limit")
                val scope = if (l.isNull("scope")) null else l.optString("scope").takeIf { it.isNotBlank() }
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
            else -> null
        }
    }
}
