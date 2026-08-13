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
import kotlin.math.min

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

    override suspend fun fetchSchedule(): Result<List<Course>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val html = fetchScheduleHtml()
                val courses = JwxtParser.parseSchedule(html)
                if (courses.isEmpty() && !JwxtParser.looksLikeSchedulePage(html)) {
                    throw IllegalStateException("课表页面结构无法解析，接口可能已改版")
                }
                courses
            }
        }

    override suspend fun fetchExams(): Result<List<Exam>> =
        withContext(Dispatchers.IO) { runCatching { emptyList() } } // 考试页抓取后续任务

    private fun fetchScheduleHtml(): String {
        // 课表接口：正方教务系统 jsxsd 标准列表接口（登录后框架页为 xsMainV.htmlx）
        val url = "https://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do"
        val bytes = http.newCall(Request.Builder().url(url).build()).execute().use { it.body!!.bytes() }
        val html = decodeScheduleHtml(bytes)
        if (JwxtParser.isLoginRedirect(html)) {
            throw IllegalStateException("登录会话已失效，请重新登录后再导入")
        }
        return html
    }
}

/**
 * 按页面声明的编码解码 HTML。正方系统页面常为 GBK 且可能不声明 charset，
 * OkHttp 默认按 UTF-8 解码会乱码，这里先按 ISO-8859-1 无损读字节再探测 meta charset。
 */
internal fun decodeScheduleHtml(bytes: ByteArray): String {
    val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
    val declared = Regex("charset\\s*=\\s*[\"']?(gbk|gb2312|gb18030)", RegexOption.IGNORE_CASE)
    return if (declared.containsMatchIn(head)) String(bytes, Charset.forName("GBK"))
    else String(bytes, Charsets.UTF_8)
}
