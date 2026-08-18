package com.schedule.njfu.ui.import

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.schedule.njfu.R
import com.schedule.njfu.importer.School
import com.schedule.njfu.importer.njfu.CasLoginClient

/**
 * 教务系统登录页（WebView）。
 *
 * 背景：jwxt 反向代理会拒绝非浏览器客户端的 ticket 落地请求（OkHttp/Python/curl 均被 404），
 * 但 WebView 走系统 Chrome 内核可正常登录。登录完成后读取会话 Cookie 回传，
 * 由 [ImportViewModel.zfImportWithCookies] / [ImportViewModel.autoImportWithCookies] 用 OkHttp 抓取数据。
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
        const val EXTRA_SUCCESS_URL_BLACKLIST = "extra_success_url_blacklist"
        const val EXTRA_USER_AGENT = "extra_user_agent"
        /** 登录加载超时（毫秒）：超过仍未成功判定则自动结束并提示 */
        const val LOGIN_TIMEOUT_MS = 120_000L
    }

    private var done = false
    private var webView: WebView? = null
    private var timeoutGuard: TimeoutGuard? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 明文地址转 https 兜底只执行一次，防止加载失败后无限重试 */
    private var httpsRetryDone = false

    /** 登录超时守卫：启动加载后 120 秒未成功则自动结束并提示，防止永久挂起 */
    private inner class TimeoutGuard : Runnable {
        override fun run() {
            if (!done) {
                done = true
                Toast.makeText(
                    this@CasLoginActivity,
                    getString(R.string.cas_login_timeout),
                    Toast.LENGTH_SHORT,
                ).show()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onDestroy() {
        // WebView 泄漏防护：停止加载、从窗口树移除并销毁；带判空与重复销毁保护
        timeoutGuard?.let { mainHandler.removeCallbacks(it); timeoutGuard = null }
        webView?.let { v ->
            webView = null
            v.stopLoading()
            (v.parent as? ViewGroup)?.removeView(v)
            v.removeAllViews()
            v.destroy()
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.cas_login_title)

        val startUrl = intent.getStringExtra(EXTRA_START_URL) ?: School.NJFU.loginUrl
        val hostPrefixes = intent.getStringArrayListExtra(EXTRA_SUCCESS_HOST_PREFIXES)
            ?.takeIf { it.isNotEmpty() } ?: School.NJFU.successHostPrefixes
        val cookieMarker = intent.getStringExtra(EXTRA_SUCCESS_COOKIE_MARKER)
            ?.takeIf { it.isNotEmpty() } ?: School.NJFU.successCookieMarker
        val urlBlacklist = intent.getStringArrayListExtra(EXTRA_SUCCESS_URL_BLACKLIST)
            ?.takeIf { it.isNotEmpty() } ?: School.NJFU.successUrlBlacklist
        // UA 规则：未传 extra → 桌面 UA（历史默认，南林 CAS 必需）；
        // 传空串 → 系统默认移动 UA（正方 jwglxt 手机版自适应页）；
        // 传非空 → 按学校指定。
        val userAgentExtra = intent.getStringExtra(EXTRA_USER_AGENT)

        val webView = WebView(this)
        this.webView = webView
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // 安全收紧：禁止文件/内容访问，禁止 JS 自动打开窗口（保留 JS 与缩放，教务页需要）
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            @Suppress("DEPRECATION")
            setAllowFileAccess(false)
            @Suppress("DEPRECATION")
            setAllowContentAccess(false)
            when {
                userAgentExtra == null -> userAgentString = CasLoginClient.BROWSER_UA
                userAgentExtra.isNotEmpty() -> userAgentString = userAgentExtra
                else -> Unit // 空串：保持系统默认（移动 UA）
            }
            // 触屏可用性：允许双指缩放、按屏宽排版，手机页/桌面页都能操作
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (done || url == null) return
                // 成功判定：URL 落在目标站点前缀下 && Cookie 含该站点登录成功的会话标记
                val onTarget = hostPrefixes.any { url.startsWith(it) }
                if (!onTarget) return
                // 黑名单片段（如正方登录页 login_slogin）：即使 Cookie 已下发会话，
                // 只要还停在登录页上就不算登录成功，避免「秒退」
                if (urlBlacklist.any { url.contains(it) }) return
                val cookies = CookieManager.getInstance().getCookie(url) ?: return
                if (!cookies.contains(cookieMarker)) return
                // 登录成功：取消超时守卫，取消定时器并回传 Cookie
                timeoutGuard?.let { mainHandler.removeCallbacks(it) }
                timeoutGuard = null
                done = true
                setResult(RESULT_OK, Intent().putExtra(EXTRA_COOKIES, cookies))
                finish()
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?,
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (done) return
                // 明文流量被拦（net::ERR_CLEARTEXT_NOT_PERMITTED）兜底：
                // 个别设备/ROM 的网络安全配置对 http 放行失效，把地址改写为 https 重载一次。
                // 广西大学等正方系统 http 请求最终也会被服务器重定向到 https，效果一致。
                if (description?.contains("CLEARTEXT", ignoreCase = true) == true &&
                    !httpsRetryDone && failingUrl?.startsWith("http://") == true
                ) {
                    httpsRetryDone = true
                    view?.loadUrl(failingUrl.replaceFirst("http://", "https://"))
                    return
                }
                Toast.makeText(
                    this@CasLoginActivity,
                    getString(R.string.cas_login_load_failed, errorCode, description ?: "null"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        setContentView(webView)
        webView.loadUrl(startUrl)
        val timeout = TimeoutGuard()
        timeoutGuard = timeout
        mainHandler.postDelayed(timeout, LOGIN_TIMEOUT_MS)
    }
}
