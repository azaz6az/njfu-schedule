package com.schedule.njfu.ui.import

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.schedule.njfu.importer.njfu.CasLoginClient
import java.net.URLEncoder

/**
 * 教务系统登录页（WebView）。
 *
 * 背景：jwxt 反向代理会拒绝非浏览器客户端的 ticket 落地请求（OkHttp/Python/curl 均被 404），
 * 但 WebView 走系统 Chrome 内核可正常登录。登录完成后读取会话 Cookie 回传，
 * 由 [ImportViewModel.autoImportWithCookies] 用 OkHttp 抓取课表页。
 */
class CasLoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_COOKIES = "extra_cookies"
        val START_URL = "https://uia.njfu.edu.cn/authserver/login?service=" +
            URLEncoder.encode("http://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do", "UTF-8")
    }

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "教务系统登录"
        val webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // 桌面 UA：移动 UA 会拿到精简版登录页（表单字段缺失）
            userAgentString = CasLoginClient.BROWSER_UA
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (done || url == null) return
                val onJwxt = url.startsWith("https://jwxt.njfu.edu.cn") ||
                    url.startsWith("http://jwxt.njfu.edu.cn")
                if (!onJwxt) return
                // bzb_jsxsd 是 jwxt 登录成功后才下发的会话 Cookie
                val cookies = CookieManager.getInstance().getCookie(url) ?: return
                if (!cookies.contains("bzb_jsxsd")) return
                done = true
                setResult(RESULT_OK, Intent().putExtra(EXTRA_COOKIES, cookies))
                finish()
            }
        }
        setContentView(webView)
        webView.loadUrl(START_URL)
    }
}
