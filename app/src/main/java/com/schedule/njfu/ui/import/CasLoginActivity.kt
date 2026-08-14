package com.schedule.njfu.ui.import

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.schedule.njfu.importer.School
import com.schedule.njfu.importer.njfu.CasLoginClient

/**
 * 教务系统登录页（WebView）。
 *
 * 背景：jwxt 反向代理会拒绝非浏览器客户端的 ticket 落地请求（OkHttp/Python/curl 均被 404），
 * 但 WebView 走系统 Chrome 内核可正常登录。登录完成后读取会话 Cookie 回传，
 * 由 [ImportViewModel.autoImportWithCookies] / [ImportViewModel.gxuImportWithCookies] 用 OkHttp 抓取数据。
 *
 * 支持多学校：通过 intent extra 指定登录页 URL、成功判定前缀与会话 Cookie 标记；
 * 未传 extra 时默认南林行为，与历史版本完全一致。
 */
class CasLoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_COOKIES = "extra_cookies"
        const val EXTRA_START_URL = "extra_start_url"
        const val EXTRA_SUCCESS_HOST_PREFIXES = "extra_success_host_prefixes"
        const val EXTRA_SUCCESS_COOKIE_MARKER = "extra_success_cookie_marker"
    }

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "教务系统登录"

        val startUrl = intent.getStringExtra(EXTRA_START_URL) ?: School.NJFU.loginUrl
        val hostPrefixes = intent.getStringArrayListExtra(EXTRA_SUCCESS_HOST_PREFIXES)
            ?.takeIf { it.isNotEmpty() } ?: School.NJFU.successHostPrefixes
        val cookieMarker = intent.getStringExtra(EXTRA_SUCCESS_COOKIE_MARKER)
            ?.takeIf { it.isNotEmpty() } ?: School.NJFU.successCookieMarker

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
                // 成功判定：URL 落在目标站点前缀下 && Cookie 含该站点登录成功的会话标记
                val onTarget = hostPrefixes.any { url.startsWith(it) }
                if (!onTarget) return
                val cookies = CookieManager.getInstance().getCookie(url) ?: return
                if (!cookies.contains(cookieMarker)) return
                done = true
                setResult(RESULT_OK, Intent().putExtra(EXTRA_COOKIES, cookies))
                finish()
            }
        }
        setContentView(webView)
        webView.loadUrl(startUrl)
    }
}
