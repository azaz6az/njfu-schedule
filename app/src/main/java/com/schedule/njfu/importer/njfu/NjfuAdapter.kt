package com.schedule.njfu.importer.njfu

import com.schedule.njfu.importer.Credentials
import com.schedule.njfu.importer.HttpSession
import com.schedule.njfu.importer.SchoolAdapter
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.nio.charset.Charset

class NjfuAdapter : SchoolAdapter {

    // 与登录共用同一会话（CookieJar），保证 jwxt 会话 Cookie 生效
    private val http = HttpSession.client

    override suspend fun login(credentials: Credentials): Result<Unit> =
        withContext(Dispatchers.IO) {
            CasLoginClient.login(
                username = credentials.username,
                password = credentials.password,
            )
        }

    override suspend fun fetchSchedule(): Result<List<Course>> = fetchScheduleWithCookies(null)

    /**
     * 携带 WebView 登录得到的会话 Cookie 抓取课表页。
     * 教务系统反向代理拒绝非浏览器客户端的 ticket 落地，故登录在 WebView 内完成（见 CasLoginActivity），
     * 这里只需带会话 Cookie 做普通页面 GET。
     */
    suspend fun fetchScheduleWithCookies(cookieHeader: String?): Result<List<Course>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val html = fetchScheduleHtml(cookieHeader)
                val courses = JwxtParser.parseSchedule(html)
                if (courses.isEmpty() && !JwxtParser.looksLikeSchedulePage(html)) {
                    throw IllegalStateException("课表页面结构无法解析，接口可能已改版")
                }
                courses
            }
        }

    override suspend fun fetchExams(): Result<List<Exam>> =
        withContext(Dispatchers.IO) { runCatching { throw IllegalStateException("考试页抓取尚未实现") } }

    private fun fetchScheduleHtml(cookieHeader: String?): String {
        // 课表接口：正方教务系统 jsxsd 标准列表接口（登录后框架页为 xsMainV.htmlx）
        val url = "https://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do"
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", CasLoginClient.BROWSER_UA)
        if (!cookieHeader.isNullOrBlank()) builder.header("Cookie", cookieHeader)
        val response = http.newCall(builder.build()).execute()
        val contentType = response.header("Content-Type")
        val bytes = response.use { it.body!!.bytes() }
        val html = decodeScheduleHtml(bytes, contentType)
        if (JwxtParser.isLoginRedirect(html)) {
            throw IllegalStateException("登录会话已失效，请重新登录后再导入")
        }
        return html
    }
}

/**
 * 按响应头/页面声明解码 HTML。正方系统页面常为 GBK 且可能不声明 charset，
 * OkHttp 默认按 UTF-8 解码会乱码：优先响应头 charset，其次探测 meta charset，最后退回 UTF-8。
 */
internal fun decodeScheduleHtml(bytes: ByteArray, contentType: String? = null): String {
    val headerCharset = contentType?.let {
        Regex("charset=([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)
    }
    if (headerCharset != null) {
        return runCatching { String(bytes, Charset.forName(headerCharset)) }
            .getOrElse { String(bytes, Charsets.UTF_8) }
    }
    val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
    val declared = Regex("charset\\s*=\\s*[\"']?(gbk|gb2312|gb18030)", RegexOption.IGNORE_CASE)
    return if (declared.containsMatchIn(head)) String(bytes, Charset.forName("GBK"))
    else String(bytes, Charsets.UTF_8)
}
