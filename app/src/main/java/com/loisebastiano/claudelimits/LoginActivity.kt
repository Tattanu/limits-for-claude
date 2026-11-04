package com.loisebastiano.claudelimits

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Real claude.ai sign-in page; it closes by itself as soon as the session cookie appears.
 * Email sign-in links reach it in two automatic ways:
 *  1. opened directly from the mail app (the user allows "claude.ai" links once in Android settings);
 *  2. copied to the clipboard: detected as soon as the user comes back here.
 */
class LoginActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var linkBtn: Button
    private var done = false
    private var lastClip: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            setText(R.string.login_hint); textSize = 13f; setPadding(pad, pad / 2, pad, pad / 2)
        })
        linkBtn = Button(this).apply {
            setText(R.string.btn_open_links); setOnClickListener { openLinkSettings() }
        }
        root.addView(linkBtn)
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
        web.loadUrl(linkFrom(intent) ?: "https://claude.ai/login")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { web.loadUrl(it) }
    }

    override fun onResume() {
        super.onResume()
        linkBtn.visibility = if (needsLinkSetting()) View.VISIBLE else View.GONE
    }

    /** Clipboard can be read only while the window has focus. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !done) checkClipboard()
    }

    private fun linkFrom(i: Intent?): String? =
        i?.data?.toString()?.takeIf { it.startsWith("https://claude.ai/") }

    private fun checkClipboard() {
        val clip = try { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip } catch (e: Exception) { null }
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        val url = Regex("https://claude\\.ai/\\S+").find(text)?.value ?: return
        if (url == lastClip) return
        lastClip = url
        web.loadUrl(url)
    }

    private fun needsLinkSetting(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val st = getSystemService(DomainVerificationManager::class.java)?.getDomainVerificationUserState(packageName) ?: return false
        return st.hostToStateMap["claude.ai"] == DomainVerificationUserState.DOMAIN_STATE_NONE
    }

    private fun openLinkSettings() {
        if (Build.VERSION.SDK_INT < 31) return
        startActivity(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun check(url: String) {
        if (done) return
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return
        if ("sessionKey=" in cookies && url.startsWith("https://claude.ai") && "/login" !in url && "/magic-link" !in url) {
            done = true
            CookieManager.getInstance().flush()
            Prefs.setWeb(this)
            Scheduler.start(this, replace = true)
            UsageWorker.refreshNow(this)
            setResult(RESULT_OK)
            if (isTaskRoot) startActivity(Intent(this, MainActivity::class.java))
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
