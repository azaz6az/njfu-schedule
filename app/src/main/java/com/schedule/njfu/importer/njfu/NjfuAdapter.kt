package com.schedule.njfu.importer.njfu

import com.schedule.njfu.importer.Credentials
import com.schedule.njfu.importer.SchoolAdapter
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class NjfuAdapter : SchoolAdapter {

    private val http = OkHttpClient()

    override suspend fun login(credentials: Credentials): Result<Unit> =
        withContext(Dispatchers.IO) {
            CasLoginClient.login(
                username = credentials.username,
                password = credentials.password,
            )
        }

    override suspend fun fetchSchedule(): Result<List<Course>> =
        withContext(Dispatchers.IO) {
            runCatching { JwxtParser.parseSchedule(fetchScheduleHtml()) }
        }

    override suspend fun fetchExams(): Result<List<Exam>> =
        withContext(Dispatchers.IO) { runCatching { emptyList() } } // 考试页抓取后续任务

    private fun fetchScheduleHtml(): String {
        // 会话 Cookie 由 http 实例自动保持；登录后课表页 URL 需真机验证，
        // 已知前缀 https://jwxt.njfu.edu.cn/jsxsd/（任务 9 侦察确认）
        val url = "https://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do"
        return http.newCall(Request.Builder().url(url).build()).execute().use { it.body!!.string() }
    }
}
