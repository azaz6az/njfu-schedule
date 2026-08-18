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

/**
 * GxuAdapter（通用正方 jwglxt）请求形态测试。
 * V9 新版实测（广西大学，2026-08）：课表接口 GET kbcx/xskbcx_cxXsgrkb，考试接口 POST kwgl/kscx_cxXsksxxIndex.html。
 */
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
    fun `fetchSchedule GETs schedule api with params and cookie`() = runBlocking {
        val json = """{"kbList":[{"kcmc":"高数","xm":"张三","xqj":"1","jcs":"1-2","zcd":"1-16周","cdmc":"A101"}]}"""
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json),
        )
        val base = server.url("/").toString().trimEnd('/')
        val adapter = GxuAdapter(ZfJwglxtConfig(base, "/jwglxt"))
        val result = adapter.fetchScheduleWithCookies("JSESSIONID=abc123", xnm = "2025", xqm = "3")

        val request = server.takeRequest()
        // V9 新版课表接口：GET + 路径 + xnm/xqm 查询参数
        assertEquals("GET", request.method)
        assertTrue(
            "应请求 xskbcx_cxXsgrkb：${request.path}",
            request.path!!.startsWith("/jwglxt/kbcx/xskbcx_cxXsgrkb"),
        )
        assertTrue(request.path!!.contains("xnm=2025"))
        assertTrue(request.path!!.contains("xqm=3"))
        assertEquals("JSESSIONID=abc123", request.getHeader("Cookie"))
        // Referer（正方 jwglxt 校验 AJAX Referer）
        assertTrue(
            "应携带课表页 Referer",
            request.getHeader("Referer")?.contains("xskbcx_cxXskbcxIndex.html") == true,
        )
        // 请求头里的浏览器化特征（WAF 识别）
        assertEquals("empty", request.getHeader("Sec-Fetch-Dest"))
        assertTrue(request.getHeader("User-Agent")!!.contains("Android"))

        val courses = result.getOrThrow()
        assertEquals(1, courses.size)
        assertEquals("高数", courses.first().name)
        assertEquals("A101", courses.first().location)
    }

    @Test
    fun `fetchExams POSTs exam api with form body`() = runBlocking {
        val json = """{"items":[{"kcmc":"英语","kssj":"2026-07-15(15:00-17:00)","cdmc":"西202"}]}"""
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json),
        )
        val base = server.url("/").toString().trimEnd('/')
        val adapter = GxuAdapter(ZfJwglxtConfig(base, "/jwglxt"))
        val result = adapter.fetchExamsWithCookies("JSESSIONID=abc123", xnm = "2025", xqm = "12")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(
            "应请求 kwgl/kscx_cxXsksxxIndex.html：${request.path}",
            request.path!!.startsWith("/jwglxt/kwgl/kscx_cxXsksxxIndex.html"),
        )
        val form = request.body.readUtf8()
        assertTrue(form.contains("xnm=2025"))
        assertTrue(form.contains("xqm=12"))

        val exams = result.getOrThrow()
        assertEquals(1, exams.size)
        assertEquals("英语", exams.first().name)
        assertEquals("2026-07-15", exams.first().date)
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

    @Test
    fun `supports root context path deployment like gdou`() = runBlocking {
        // 广东海洋大学等学校正方系统部署在根路径（contextPath = ""），
        // 接口 URL 不应带 /jwglxt 前缀
        val json = """{"kbList":[{"kcmc":"大学英语","xm":"李四","xqj":"2","jcs":"3-4","zcd":"1-16周","cdmc":"B202"}]}"""
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json),
        )
        val base = server.url("/").toString().trimEnd('/')
        val adapter = GxuAdapter(base, contextPath = "")
        val result = adapter.fetchScheduleWithCookies("JSESSIONID=xyz", xnm = "2025", xqm = "3")

        val request = server.takeRequest()
        assertTrue(
            "根路径部署应请求 /kbcx/... 而非 /jwglxt/kbcx/...：${request.path}",
            request.path!!.startsWith("/kbcx/xskbcx_cxXsgrkb"),
        )
        assertTrue(request.path!!.contains("xnm=2025"))

        val courses = result.getOrThrow()
        assertEquals(1, courses.size)
        assertEquals("大学英语", courses.first().name)
    }

    @Test
    fun `legacy post-style schedule api is supported via config`() = runBlocking {
        // 旧版正方（jsxsd 风格）课表接口为 POST 表单，可通过配置指定
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"kbList":[]}"""),
        )
        val base = server.url("/").toString().trimEnd('/')
        val config = ZfJwglxtConfig(
            baseUrl = base,
            contextPath = "/jwglxt",
            schedulePath = "kbcx/xskbcx_cxXsKb",
            scheduleMethod = ZfMethod.POST,
        )
        val adapter = GxuAdapter(config)
        val result = adapter.fetchScheduleWithCookies("JSESSIONID=old", xnm = "2025", xqm = "3")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.startsWith("/jwglxt/kbcx/xskbcx_cxXsKb"))
        val form = request.body.readUtf8()
        assertTrue(form.contains("xnm=2025"))

        assertTrue(result.getOrThrow().isEmpty())
    }
}