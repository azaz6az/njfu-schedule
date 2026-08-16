package com.schedule.njfu.importer.njfu

import com.schedule.njfu.importer.HttpSession
import com.schedule.njfu.importer.RsaEncryptor
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder

data class LoginPage(
    val lt: String, val execution: String, val salt: String,
    val action: String, val needsSlider: Boolean,
)

/** 教务系统要求验证码：携带登录页上下文，UI 展示验证码后调 [CasLoginClient.loginWithCaptcha] */
class CaptchaRequiredException(val page: LoginPage) : IllegalStateException("需要验证码")

object CasLoginClient {

    const val LOGIN_URL = "https://uia.njfu.edu.cn/authserver/login"
    const val SERVICE_URL = "http://jwxt.njfu.edu.cn/sso.jsp"

    /** 浏览器 UA：降低教务系统 WAF 对非浏览器客户端的拦截概率。
     *  注意：不能伪装移动端 UA——服务端对移动端返回精简版登录页（salt 仅在 JS 变量中），
     *  需用桌面 Chrome UA 获取含 HTML input 的完整登录页。 */
    const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /** 纯函数：解析登录页 HTML 表单字段（可单测） */
    fun parseLoginPage(html: String): LoginPage {
        fun field(id: String): String =
            // 1) HTML input：id/name 在前，value 在后
            Regex("(?:id|name)=\"$id\"[^>]*value=\"([^\"]*)\"")
                .find(html)?.groupValues?.get(1)
                // 2) HTML input：value 在前，id/name 在后
                ?: Regex("value=\"([^\"]*)\"[^>]*(?:id|name)=\"$id\"")
                    .find(html)?.groupValues?.get(1)
                // 3) 移动精简版页面：JS 变量赋值形式（如 pwdDefaultEncryptSalt = "xxx"）
                ?: Regex("$id\\s*=\\s*\"([^\"]*)\"")
                    .find(html)?.groupValues?.get(1)
                ?: ""
        val action = Regex("<form[^>]*action=\"([^\"]*)\"").find(html)?.groupValues?.get(1) ?: ""
        return LoginPage(
            lt = field("lt"), execution = field("execution"),
            salt = field("pwdDefaultEncryptSalt"),
            action = action, needsSlider = field("isSliderCaptcha").isNotEmpty(),
        )
    }

    fun login(
        baseUrl: String = LOGIN_URL,
        username: String,
        password: String,
    ): Result<Unit> = runCatching {
        val client = HttpSession.noRedirectClient
        val page = fetchLoginPage(client, baseUrl) ?: return@runCatching
        // 验证码判定：needCaptcha.html 位于 /authserver/ 下（baseUrl 的上级路径）
        val captchaUrl = "${originOf(baseUrl)}/authserver/needCaptcha.html" +
            "?username=${URLEncoder.encode(username, "UTF-8")}&pwdEncrypt2=${URLEncoder.encode(page.salt, "UTF-8")}"
        val needCaptcha = client.newCall(Request.Builder()
            .url(captchaUrl)
            .header("User-Agent", BROWSER_UA)
            .build())
            .execute().use { it.body?.string().orEmpty().trim() == "true" }
        if (needCaptcha) {
            throw CaptchaRequiredException(page)   // UI 层捕获后展示验证码输入
        }
        submitLogin(client, baseUrl, username, password, page, captcha = null)
    }

    /** 带验证码提交登录（验证码输入完成后调用） */
    fun loginWithCaptcha(
        baseUrl: String = LOGIN_URL,
        username: String,
        password: String,
        captcha: String,
        page: LoginPage,
    ): Result<Unit> = runCatching {
        submitLogin(HttpSession.noRedirectClient, baseUrl, username, password, page, captcha)
    }

    /** 获取验证码图片（JPEG 字节），与登录共用同一会话 Cookie */
    fun fetchCaptcha(baseUrl: String = LOGIN_URL): Result<ByteArray> = runCatching {
        HttpSession.client.newCall(Request.Builder()
            .url("${originOf(baseUrl)}/authserver/captcha.html")
            .header("User-Agent", BROWSER_UA)
            .build())
            .execute().use { it.body?.bytes() ?: throw IllegalStateException("验证码接口未返回数据") }
    }

    /**
     * 请求登录页。返回 null 表示 CookieJar 中已有未过期的 TGC（上次登录成功），
     * CAS 直接 302 到 service（带 ticket），已跟随 service 链建立会话，无需再走表单。
     */
    private fun fetchLoginPage(client: OkHttpClient, baseUrl: String): LoginPage? {
        val loginPageUrl = "$baseUrl?service=${URLEncoder.encode(SERVICE_URL, "UTF-8")}"
        val resp = client.newCall(Request.Builder()
            .url(loginPageUrl)
            .header("User-Agent", BROWSER_UA)
            .build()).execute()
        val code = resp.code
        if (code in 300..399) {
            val location = resp.header("Location") ?: ""
            resp.close()
            if (location.contains("ticket=")) {
                followServiceRedirect(client, resolveUrl(location, baseUrl))
                return null
            }
            throw IllegalStateException("登录页请求异常重定向（HTTP $code → $location）")
        }
        val pageHtml = resp.body?.string().orEmpty()
        val page = parseLoginPage(pageHtml)
        if (page.lt.isBlank()) {
            // 带页面诊断信息，便于定位是 302/验证页/改版
            val head = pageHtml.take(200).replace('\n', ' ')
            throw IllegalStateException(
                "登录页缺少 lt 票据（HTTP $code，页面 ${pageHtml.length} 字符，头部：$head）"
            )
        }
        return page
    }

    private fun submitLogin(
        client: OkHttpClient,
        baseUrl: String,
        username: String,
        password: String,
        page: LoginPage,
        captcha: String?,
    ) {
        val encrypted = RsaEncryptor.encryptPassword(password, page.salt)
        val builder = FormBody.Builder()
            .add("username", username)
            .add("password", encrypted)
            .add("lt", page.lt)
            .add("dllt", "userNamePasswordLogin")
            .add("execution", page.execution)
            .add("_eventId", "submit")
            .add("rmShown", "1")
        if (!captcha.isNullOrBlank()) builder.add("captchaResponse", captcha)
        val response = client.newCall(Request.Builder()
            .url(resolveAction(page.action, baseUrl))
            .header("User-Agent", BROWSER_UA)
            .post(builder.build()).build()).execute()
        handleLoginResponse(client, response, baseUrl)
    }

    private fun handleLoginResponse(client: OkHttpClient, response: Response, baseUrl: String) {
        if (response.code == 302) {
            // CAS 成功重定向的 Location 必带 ticket=；否则视为异常重定向，不能当作登录成功
            val location = response.header("Location") ?: ""
            if (location.contains("ticket=")) {
                // 跟随 service 重定向（sso.jsp → jsxsd 框架页），校验 ticket 并收下会话 Cookie
                followServiceRedirect(client, resolveUrl(location, baseUrl))
            } else {
                throw IllegalStateException("登录失败：重定向异常")
            }
        } else {
            val body = response.body?.string().orEmpty()
            when {
                // 精确特征匹配，避免把任意含"用户名"的响应误报为密码错误
                body.contains("您提供的用户名或者密码有误") ||
                    body.contains("用户名或密码错误") ||
                    body.contains("密码错误") ||
                    Regex("id=\"msg\"").containsMatchIn(body) &&
                        body.contains("用户名") ->
                    throw IllegalArgumentException("用户名或密码错误")
                body.contains("验证码") && (Regex("id=\"msg\"").containsMatchIn(body) || body.contains("验证码错误")) ->
                    throw IllegalStateException("验证码错误或已过期，请重新输入")
                body.contains("认证服务不可用") ->
                    throw IllegalStateException("教务系统认证服务暂时不可用，请稍后再试")
                else -> {
                    val head = body.take(200).replace('\n', ' ')
                    throw IllegalStateException("登录失败，请检查网络后重试（HTTP ${response.code}：$head）")
                }
            }
        }
    }

    /** 跟随 service 重定向链（最多 5 跳），完成 ticket 校验并建立目标系统会话 */
    private fun followServiceRedirect(client: OkHttpClient, startUrl: String) {
        var current = startUrl
        repeat(5) {
            val r = client.newCall(Request.Builder()
                .url(current)
                .header("User-Agent", BROWSER_UA)
                .build()).execute()
            val code = r.code
            val location = r.header("Location")
            // 非重定向落地时读取 body，判断是否被踢回登录页（会话未建立）
            val bodyHead = if (code !in 300..399) {
                runCatching { r.body?.string()?.take(400) }.getOrNull().orEmpty()
            } else ""
            r.close()
            if (code !in 300..399 || location.isNullOrBlank()) {
                if (bodyHead.contains("authserver/login") || bodyHead.contains("window.location")) {
                    throw IllegalStateException(
                        "会话建立失败：ticket 校验被重定向回登录页（HTTP $code）"
                    )
                }
                return
            }
            current = resolveUrl(location, current)
        }
    }

    /** 提取 baseUrl 的协议+主机（如 https://uia.njfu.edu.cn），用于拼接绝对路径 */
    private fun originOf(baseUrl: String): String =
        Regex("^https?://[^/]+").find(baseUrl)?.value ?: baseUrl

    private fun resolveAction(action: String, baseUrl: String): String = resolveUrl(action, baseUrl)

    private fun resolveUrl(path: String, baseUrl: String): String =
        if (path.startsWith("http")) path
        else if (path.startsWith("/")) originOf(baseUrl) + path
        else baseUrl.removeSuffix("/") + "/" + path
}
