package com.loisebastiano.claudelimits

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Shows the real claude.ai login page; done as soon as the session cookie appears.
 * Sign-in is needed only once: the session is kept by the app afterwards.
 * "Paste link" covers the email sign-in, whose link would otherwise open in Chrome, not here.
 */
class LoginActivity : Activity() {
    private lateinit var web: WebView
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            setText(R.string.login_hint); textSize = 13f; setPadding(pad, pad / 2, pad, 0)
        })
        root.addView(Button(this).apply {
            setText(R.string.btn_paste_link); setOnClickListener { pasteLink() }
        })
        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.padForSystemBars()
        setContentView(root)

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

    private fun pasteLink() {
        val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        val url = text?.let { Regex("https://(?:[a-z0-9-]+\\.)*claude\\.ai/\\S+").find(it)?.value }
        if (url == null) {
            Toast.makeText(this, R.string.e_no_link, Toast.LENGTH_LONG).show()
        } else {
            web.loadUrl(url)
        }
    }

    private fun check(url: String) {
        if (done) return
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return
        if ("sessionKey=" in cookies && url.startsWith("https://claude.ai") && "/login" !in url && "/magic-link" !in url) {
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
