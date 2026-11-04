package com.loisebastiano.claudelimits

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

/** Shows the real claude.ai login page; done as soon as the session cookie appears. */
class LoginActivity : Activity() {
    private lateinit var web: WebView
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = WebFetcher.cleanUserAgent(web.settings.userAgentString)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) { check(url) }
        }
        web.loadUrl("https://claude.ai/login")
    }

    private fun check(url: String) {
        if (done) return
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return
        if ("sessionKey=" in cookies && url.startsWith("https://claude.ai") && "/login" !in url) {
            done = true
            CookieManager.getInstance().flush()
            Prefs.setWeb(this)
            setResult(RESULT_OK)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
