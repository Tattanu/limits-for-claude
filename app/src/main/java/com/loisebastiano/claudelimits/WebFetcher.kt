package com.loisebastiano.claudelimits

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * claude.ai sits behind Cloudflare, which rejects plain HTTP clients.
 * So we run the request inside an invisible WebView that shares the cookies of
 * the login screen: it behaves exactly like the browser that shows claude.ai/settings/usage.
 */
object WebFetcher {
    private const val START = "https://claude.ai/api/organizations"

    fun cleanUserAgent(ua: String): String = ua.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")

    suspend fun fetch(c: Context): FetchResult {
        val raw = try {
            withTimeout(45_000) { withContext(Dispatchers.Main) { run(c.applicationContext, Prefs.org(c)) } }
        } catch (e: TimeoutCancellationException) {
            throw ApiException(c.getString(R.string.e_timeout))
        }
        val o = JSONObject(raw)
        if (o.has("error")) throw ApiException(o.getString("error"))
        val status = o.optInt("status")
        val plan = Plans.label(o.optString("plan"))
        Prefs.setDiagnostic(c, JSONObject().put("status", status).put("capabilities", o.optString("plan"))
            .put("body", o.optString("body").take(4000)).toString(2))
        // 401 anywhere, or 403 on the organization list, means the session is no longer valid.
        if (status == 401 || (status == 403 && o.optString("stage") == "orgs")) throw ApiException(c.getString(R.string.e_session))
        o.optString("org").takeIf { it.isNotBlank() }?.let { Prefs.setOrg(c, it) }
        // Signed in, but the usage endpoint is refused or missing: report it as "not available", not as an error.
        if (status == 403 || status == 404) return FetchResult(null, plan)
        if (status != 200) throw ApiException("HTTP $status")
        return FetchResult(JSONObject(o.getString("body")), plan)
    }

    private class Bridge(val onDone: (String) -> Unit) {
        @JavascriptInterface
        fun done(s: String) { Handler(Looper.getMainLooper()).post { onDone(s) } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun run(c: Context, orgHint: String?): String = suspendCancellableCoroutine { cont ->
        val wv = WebView(c)
        var finished = false
        fun close() { if (!finished) { finished = true; wv.stopLoading(); wv.destroy() } }
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = cleanUserAgent(wv.settings.userAgentString)
        wv.addJavascriptInterface(Bridge { s -> if (!finished) { close(); if (cont.isActive) cont.resume(s) } }, "LFC")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Skip Chromium's error page: the script would only fail with "Failed to fetch".
                if (!finished && url.startsWith("https://claude.ai/")) view.evaluateJavascript(script(orgHint), null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame || finished) return
                val msg = JSONObject().put("error", "network: ${error.description} (${error.errorCode})").toString()
                close()
                if (cont.isActive) cont.resume(msg)
            }
        }
        cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { close() } }
        wv.loadUrl(START)
    }

    private fun script(orgHint: String?): String {
        val hint = if (orgHint == null) "null" else JSONObject.quote(orgHint)
        return """
(function(){
  if (window.__lfc) return;
  if ((document.title || '').indexOf('Just a moment') >= 0) return; // Cloudflare check still running
  window.__lfc = 1;
  (async function(){
    try {
      var org = $hint, plan = '';
      var h = {credentials: 'include', headers: {'Accept': 'application/json'}};
      var r = await fetch('/api/organizations', h);
      if (r.status === 401 || r.status === 403) { LFC.done(JSON.stringify({status: r.status, stage: 'orgs'})); return; }
      if (r.ok) {
        var a = await r.json(), sel = null, i;
        var m = document.cookie.match(/(?:^|; )lastActiveOrg=([^;]+)/);
        var pref = org || (m ? decodeURIComponent(m[1]) : null);
        for (i = 0; i < a.length; i++) if (a[i].uuid === pref) sel = a[i];
        if (!sel) for (i = 0; i < a.length; i++) if ((a[i].capabilities || []).indexOf('chat') >= 0) { sel = a[i]; break; }
        if (!sel && a.length) sel = a[0];
        if (sel) { org = sel.uuid; plan = (sel.capabilities || []).join(',') + ',' + (sel.rate_limit_tier || ''); }
      }
      if (!org) { LFC.done(JSON.stringify({error: 'no organization'})); return; }
      var u = await fetch('/api/organizations/' + org + '/usage', h);
      var t = await u.text();
      LFC.done(JSON.stringify({status: u.status, stage: 'usage', org: org, plan: plan, body: t}));
    } catch (e) { LFC.done(JSON.stringify({error: String(e)})); }
  })();
})();
""".trimIndent()
    }
}
