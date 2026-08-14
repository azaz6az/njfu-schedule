package com.schedule.njfu.importer.gxu

import com.schedule.njfu.importer.Credentials
import com.schedule.njfu.importer.HttpSession
import com.schedule.njfu.importer.SchoolAdapter
import com.schedule.njfu.importer.njfu.CasLoginClient
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response

/**
 * 广西大学（正方 jwglxt 新版教务系统）导入适配器。
 *
 * 登录在 WebView 内完成（见 [com.schedule.njfu.ui.import.CasLoginActivity]），OkHttp 只负责
 * 携带 WebView 登录得到的会话 Cookie 抓取 JSON 接口，故 [login] 直接成功、不在此处做表单登录。
 *
 * @param baseUrl 教务系统根地址（默认正式站点；测试时注入 MockWebServer 地址）
 */
class GxuAdapter(
    private val baseUrl: String = "https://jwxt2018.gxu.edu.cn",
) : SchoolAdapter {

    // 与登录共用同一会话（CookieJar），保证 jwglxt 会话 Cookie 生效
    private val http = HttpSession.client

    override suspend fun login(credentials: Credentials): Result<Unit> =
        // 登录在 WebView 内完成，OkHttp 仅抓数据，故此处直接成功
        Result.success(Unit)

    override suspend fun fetchSchedule(): Result<List<Course>> =
        withContext(Dispatchers.IO) {
            runCatching { throw IllegalStateException("广西大学课表需先通过应用内置浏览器登录后再导入") }
        }

    override suspend fun fetchExams(): Result<List<Exam>> =
        withContext(Dispatchers.IO) {
            runCatching { throw IllegalStateException("广西大学考试需先通过应用内置浏览器登录后再导入") }
        }

    /**
     * 携带 WebView 登录得到的会话 Cookie 抓取课表 JSON。
     * @param cookieHeader WebView 回传的 Cookie 字符串（登录成功后才能拿到会话，见 CasLoginActivity）
     */
    suspend fun fetchScheduleWithCookies(cookieHeader: String?, xnm: String, xqm: String): Result<List<Course>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = postForm(
                    url = "$baseUrl/jwglxt/kbcx/xskbcx_cxXsKb?gnmkdm=N2151",
                    cookieHeader = cookieHeader,
                    xnm = xnm,
                    xqm = xqm,
                )
                GxuParser.parseScheduleJson(body)
            }
        }

    /**
     * 携带会话 Cookie 抓取考试 JSON。
     */
    suspend fun fetchExamsWithCookies(cookieHeader: String?, xnm: String, xqm: String): Result<List<Exam>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = postForm(
                    url = "$baseUrl/jwglxt/kscx_cxXsksxxDg.html?gnmkdm=N358105",
                    cookieHeader = cookieHeader,
                    xnm = xnm,
                    xqm = xqm,
                )
                GxuParser.parseExamsJson(body)
            }
        }

    /** POST xnm/xqm 表单，携带浏览器 UA 与会话 Cookie；响应非 JSON 或含登录跳转视为会话失效 */
    private fun postForm(url: String, cookieHeader: String?, xnm: String, xqm: String): String {
        val form = FormBody.Builder().add("xnm", xnm).add("xqm", xqm).build()
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", CasLoginClient.BROWSER_UA)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .post(form)
        if (!cookieHeader.isNullOrBlank()) builder.header("Cookie", cookieHeader)
        val response = http.newCall(builder.build()).execute()
        return response.use { readJsonBody(it) }
    }

    /**
     * 读取响应体。若 content-type 不是 JSON，或 body 含 "login_slogin"（未登录被踢回登录页），
     * 说明会话已失效，抛中文异常由 ViewModel 展示。
     */
    private fun readJsonBody(response: Response): String {
        val contentType = response.header("Content-Type").orEmpty().lowercase()
        val body = response.body?.string().orEmpty()
        if (!contentType.contains("json") || body.contains("login_slogin")) {
            throw IllegalStateException("登录会话已失效，请重新登录后再导入")
        }
        return body
    }
}
