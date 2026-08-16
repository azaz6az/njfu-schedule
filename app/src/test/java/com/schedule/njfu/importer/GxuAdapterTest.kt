package com.schedule.njfu.importer

import com.schedule.njfu.importer.gxu.GxuAdapter
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GxuAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchSchedule posts xnm xqm with cookie header and parses json`() = runBlocking {
        val json = """{"kbList":[{"kcmc":"高数","xm":"张三","xqj":"1","jcs":"1-2","zcd":"1-16周","jxdd":"A101"}]}"""
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json),
        )
        val base = server.url("/").toString().trimEnd('/')
        val adapter = GxuAdapter(base)
        val result = adapter.fetchScheduleWithCookies("JSESSIONID=abc123", xnm = "2025", xqm = "3")

        val request = server.takeRequest()
        // POST 到课表接口路径
        assertTrue(request.path!!.startsWith("/jwglxt/kbcx/xskbcx_cxXsKb"))
        assertEquals("POST", request.method)
        // form 参数
        val form = request.body.readUtf8()
        assertTrue(form.contains("xnm=2025"))
        assertTrue(form.contains("xqm=3"))
        // Cookie 头
        assertEquals("JSESSIONID=abc123", request.getHeader("Cookie"))
        // Referer 头（正方 jwglxt 校验 AJAX Referer）
        assertTrue(
            "应携带课表页 Referer",
            request.getHeader("Referer")?.contains("xskbcx_cxXskbcxIndex.html") == true,
        )

        val courses = result.getOrThrow()
        assertEquals(1, courses.size)
        assertEquals("高数", courses.first().name)
        assertEquals(1, courses.first().dayOfWeek)
    }

    @Test
    fun `fetchExams parses items json`() = runBlocking {
        val json = """{"items":[{"kcmc":"英语","kssj":"2025-07-05 14:30:00","cdmc":"西202"}]}"""
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json),
        )
        val base = server.url("/").toString().trimEnd('/')
        val adapter = GxuAdapter(base)
        val result = adapter.fetchExamsWithCookies("JSESSIONID=abc123", xnm = "2025", xqm = "3")

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/jwglxt/kscx_cxXsksxxDg.html"))

        val exams = result.getOrThrow()
        assertEquals(1, exams.size)
        assertEquals("英语", exams.first().name)
        assertEquals("2025-07-05", exams.first().date)
    }

    @Test
    fun `login redirect html response reports session expired`() = runBlocking {
        // 未登录被踢回登录页：content-type 不是 JSON，且 body 含 login_slogin
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html><script>window.location.href='/jwglxt/xtgl/login_slogin.html'</script></html>"),
        )
        val base = server.url("/").toString().trimEnd('/')
        val adapter = GxuAdapter(base)
        val result = adapter.fetchScheduleWithCookies("JSESSIONID=expired", xnm = "2025", xqm = "3")

        server.takeRequest()
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue("会话失效提示应含登录字句：$message", message.contains("登录会话已失效"))
    }
}
