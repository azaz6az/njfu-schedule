package com.schedule.njfu.importer.gxu

import com.schedule.njfu.importer.Credentials
import com.schedule.njfu.importer.HttpSession
import com.schedule.njfu.importer.SchoolAdapter
import com.schedule.njfu.importer.ZfJwglxtConfig
import com.schedule.njfu.importer.ZfMethod
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response

/**
 * 正方 jwglxt 新版教务系统（教学管理信息服务平台）通用导入适配器。
 *
 * 广西大学、广东海洋大学等大量高校使用同一套正方系统，登录流程与数据接口结构一致，
 * 仅部署域名、路径前缀与接口名不同（见 [ZfJwglxtConfig]）。登录在 WebView 内完成
 * （见 [com.schedule.njfu.ui.import.CasLoginActivity]），OkHttp 只负责携带 WebView 登录
 * 得到的会话 Cookie 抓取 JSON 接口，故 [login] 直接成功、不在此处做表单登录。
 *
 * @param config 正方教务连接配置（baseUrl + contextPath + 接口路径/方法）
 */
class GxuAdapter(
    private val config: ZfJwglxtConfig,
) : SchoolAdapter {

    constructor(baseUrl: String, contextPath: String = "/jwglxt") :
        this(ZfJwglxtConfig(baseUrl, contextPath))

    // 与登录共用同一会话（CookieJar），保证 jwglxt 会话 Cookie 生效
    private val http = HttpSession.client

    override suspend fun login(credentials: Credentials): Result<Unit> =
        // 登录在 WebView 内完成，OkHttp 仅抓数据，故此处直接成功
        Result.success(Unit)

    override suspend fun fetchSchedule(): Result<List<Course>> =
        withContext(Dispatchers.IO) {
            runCatching { throw IllegalStateException("正方教务课表需先通过应用内置浏览器登录后再导入") }
        }

    override suspend fun fetchExams(): Result<List<Exam>> =
        withContext(Dispatchers.IO) {
            runCatching { throw IllegalStateException("正方教务考试需先通过应用内置浏览器登录后再导入") }
        }

    /**
     * 携带 WebView 登录得到的会话 Cookie 抓取课表 JSON。
     * @param cookieHeader WebView 回传的 Cookie 字符串（登录成功后才能拿到会话，见 CasLoginActivity）
     */
    suspend fun fetchScheduleWithCookies(cookieHeader: String?, xnm: String, xqm: String): Result<List<Course>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = if (config.scheduleMethod == ZfMethod.GET) {
                    getJson(config.scheduleUrl(xnm, xqm), cookieHeader, config.scheduleReferer)
                } else {
                    postForm(config.scheduleUrl(xnm, xqm), cookieHeader, xnm, xqm, config.scheduleReferer)
                }
                GxuParser.parseScheduleJson(body)
            }
        }

    /**
     * 携带会话 Cookie 抓取考试 JSON。
     */
    suspend fun fetchExamsWithCookies(cookieHeader: String?, xnm: String, xqm: String): Result<List<Exam>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = if (config.examMethod == ZfMethod.GET) {
                    getJson(config.examUrl(xnm, xqm), cookieHeader, config.examReferer)
                } else {
                    postForm(config.examUrl(xnm, xqm), cookieHeader, xnm, xqm, config.examReferer)
                }
                GxuParser.parseExamsJson(body)
            }
        }

    /** GET JSON：携带浏览器化请求头 + 会话 Cookie */
    private fun getJson(url: String, cookieHeader: String?, referer: String): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_UA)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Referer", referer)
            .get()
        if (!cookieHeader.isNullOrBlank()) builder.header("Cookie", cookieHeader)
        val response = http.newCall(builder.build()).execute()
        return response.use { readJsonBody(it) }
    }

    /** POST xnm/xqm 表单，携带移动 UA/浏览器化请求头/Referer/会话 Cookie；响应非 JSON 或含登录跳转视为会话失效 */
    private fun postForm(
        url: String,
        cookieHeader: String?,
        xnm: String,
        xqm: String,
        referer: String,
    ): String {
        val form = FormBody.Builder().add("xnm", xnm).add("xqm", xqm).build()
        val builder = Request.Builder()
            .url(url)
            // 与 WebView 登录页一致的移动 UA：正方 WAF 对桌面 UA 的脚本化请求会直接踢回登录页
            .header("User-Agent", MOBILE_UA)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            // 浏览器同源 AJAX 的 Sec-Fetch 头族：补齐后 WAF 不再把请求识别为脚本
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            // 正方 jwglxt 的 AJAX 接口校验 Referer，缺失会返回登录页/空数据
            .header("Referer", referer)
            .post(form)
        if (!cookieHeader.isNullOrBlank()) builder.header("Cookie", cookieHeader)
        val response = http.newCall(builder.build()).execute()
        return response.use { readJsonBody(it) }
    }

    /**
     * 读取响应体。若 content-type 不是 JSON，或 body 含 "login_slogin"（未登录被踢回登录页），
     * 说明会话已失效或接口路径不对，抛中文异常由 ViewModel 展示。
     */
    private fun readJsonBody(response: Response): String {
        val contentType = response.header("Content-Type").orEmpty().lowercase()
        val body = response.body?.string().orEmpty()
        if (!contentType.contains("json") || body.contains("login_slogin")) {
            throw IllegalStateException("登录会话已失效，请重新登录后再导入")
        }
        return body
    }

    companion object {
        /**
         * 与 WebView 登录一致的移动 UA。
         * 注意：广西大学等正方系统前置 WAF（安恒盾阵，响应含 ADCCookie）会对非浏览器特征的
         * 请求返回 302 登录页——桌面 UA + 脚本头组合是最常见的被拒样本。
         */
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/119.0.0.0 Mobile Safari/537.36"
    }
}