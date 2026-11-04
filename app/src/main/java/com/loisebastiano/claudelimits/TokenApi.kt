package com.loisebastiano.claudelimits

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Token mode: the OAuth endpoint used by Claude Code's /usage (no Cloudflare in front). */
object TokenApi {
    private const val BASE = "https://api.anthropic.com/api/oauth"

    suspend fun fetch(c: Context): FetchResult = withContext(Dispatchers.IO) {
        val token = Prefs.token(c) ?: throw ApiException(c.getString(R.string.e_no_token))
        val usage = JSONObject(get(c, "$BASE/usage", token))
        val plan = Prefs.snapshot(c)?.plan ?: try {
            Plans.label(JSONObject(get(c, "$BASE/profile", token)).optJSONObject("organization")
                ?.optString("organization_type"))
        } catch (e: Exception) { null }
        FetchResult(usage, plan)
    }

    private fun get(c: Context, url: String, token: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("anthropic-beta", "oauth-2025-04-20")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "LimitsForClaude/${BuildConfigCompat.version(c)}")
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 401 || code == 403) throw ApiException(c.getString(R.string.e_auth, code))
            if (code != 200) throw ApiException("HTTP $code")
            return body
        } finally { conn.disconnect() }
    }

    /** Accepts a bare token or the JSON of ~/.claude/.credentials.json. */
    fun extractToken(text: String): String? {
        val t = text.trim()
        if (t.startsWith("{")) {
            return try {
                val o = JSONObject(t)
                (o.optJSONObject("claudeAiOauth") ?: o).optString("accessToken").takeIf { it.isNotBlank() }
            } catch (e: Exception) { null }
        }
        return Regex("sk-ant-[A-Za-z0-9_\\-]+").find(t)?.value ?: t.takeIf { it.length > 20 && ' ' !in it }
    }
}

object BuildConfigCompat {
    fun version(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }
}
