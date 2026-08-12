package com.schedule.njfu.importer.njfu

import com.schedule.njfu.importer.RsaEncryptor
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

data class LoginPage(
    val lt: String, val execution: String, val salt: String,
    val action: String, val needsSlider: Boolean,
)

object CasLoginClient {

    const val LOGIN_URL = "https://uia.njfu.edu.cn/authserver/login"
    const val SERVICE_URL = "http://jwxt.njfu.edu.cn/sso.jsp"

    /** 纯函数：解析登录页 HTML 表单字段（可单测） */
    fun parseLoginPage(html: String): LoginPage {
        fun field(id: String): String =
            Regex("(?:id|name)=\"$id\"[^>]*value=\"([^\"]*)\"")
                .find(html)?.groupValues?.get(1)
                ?: Regex("value=\"([^\"]*)\"[^>]*(?:id|name)=\"$id\"")
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
        // 关闭自动重定向：302 是 CAS 登录成功的标志，需由本方法显式判定；
        // 302 响应中的 Set-Cookie 会话 Cookie 仍会被 OkHttp CookieJar 保存
        val client = OkHttpClient.Builder().followRedirects(false).build()
        val loginPageUrl = "$baseUrl?service=${URLEncoder.encode(SERVICE_URL, "UTF-8")}"
        val pageHtml = client.newCall(Request.Builder().url(loginPageUrl).build())
            .execute().use { it.body!!.string() }
        val page = parseLoginPage(pageHtml)
        require(page.lt.isNotBlank()) { "登录页缺少 lt 票据" }
        // 验证码判定：needCaptcha.html 位于 /authserver/ 下（baseUrl 的上级路径）
        val captchaUrl = "${originOf(baseUrl)}/authserver/needCaptcha.html?username=${URLEncoder.encode(username, "UTF-8")}&pwdEncrypt2=${URLEncoder.encode(page.salt, "UTF-8")}"
        val needCaptcha = client.newCall(Request.Builder().url(captchaUrl).build())
            .execute().use { it.body!!.string().trim() == "true" }
        if (needCaptcha) {
            throw IllegalStateException("需要验证码")   // UI 层捕获后展示验证码输入
        }
        val encrypted = RsaEncryptor.encryptPassword(password, page.salt)
        val form = FormBody.Builder()
            .add("username", username)
            .add("password", encrypted)
            .add("lt", page.lt)
            .add("dllt", "userNamePasswordLogin")
            .add("execution", page.execution)
            .add("_eventId", "submit")
            .add("rmShown", "1")
            .build()
        val response = client.newCall(Request.Builder()
            .url(resolveAction(page.action, baseUrl))
            .post(form).build()).execute()
        if (response.code == 302) {
            // CAS 成功重定向的 Location 必带 ticket=；否则视为异常重定向，不能当作登录成功
            val location = response.header("Location") ?: ""
            if (location.contains("ticket=")) {
                Unit   // 重定向到 service → 登录成功，会话 Cookie 已保存
            } else {
                throw IllegalStateException("登录失败：重定向异常")
            }
        } else {
            val body = response.body?.string().orEmpty()
            when {
                body.contains("您提供的用户名或者密码有误") || body.contains("用户名或密码错误") ->
                    throw IllegalArgumentException("用户名或密码错误")
                body.contains("认证服务不可用") ->
                    throw IllegalStateException("教务系统认证服务暂时不可用，请稍后再试")
                body.contains("用户名") ->
                    throw IllegalArgumentException("用户名或密码错误")
                else ->
                    throw IllegalStateException("登录失败：HTTP ${response.code}")
            }
        }
    }

    /** 提取 baseUrl 的协议+主机（如 https://uia.njfu.edu.cn），用于拼接绝对路径 */
    private fun originOf(baseUrl: String): String =
        Regex("^https?://[^/]+").find(baseUrl)?.value ?: baseUrl

    private fun resolveAction(action: String, baseUrl: String): String =
        if (action.startsWith("http")) action
        else if (action.startsWith("/")) originOf(baseUrl) + action
        else baseUrl.removeSuffix("/") + "/" + action
}
