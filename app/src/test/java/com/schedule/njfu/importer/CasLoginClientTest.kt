package com.schedule.njfu.importer

import com.schedule.njfu.importer.njfu.CasLoginClient
import com.schedule.njfu.importer.njfu.LoginPage
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CasLoginClientTest {

    private val loginHtml = """
        <html><body>
        <form id="casLoginForm" action="/authserver/login?service=http%3A%2F%2Fjwxt.njfu.edu.cn%2Fsso.jsp" method="post">
        <input id="username" name="username" type="text"/>
        <input id="passwordEncrypt" name="password" type="hidden"/>
        <input type="hidden" name="lt" value="LT-123-test"/>
        <input type="hidden" name="execution" value="e1s1"/>
        <input type="hidden" name="_eventId" value="submit"/>
        <input type="hidden" id="pwdDefaultEncryptSalt" value="testSalt12345678"/>
        </form>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses login page form fields`() {
        val page = CasLoginClient.parseLoginPage(loginHtml)
        assertEquals("LT-123-test", page.lt)
        assertEquals("e1s1", page.execution)
        assertEquals("testSalt12345678", page.salt)
    }

    @Test
    fun `posts encrypted credentials and follows redirect`() {
        val server = MockWebServer()
        server.start()
        // 1) 登录页 2) needCaptcha=false 3) 表单提交 302 → sso.jsp 4) sso.jsp 200
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
        server.enqueue(MockResponse().setResponseCode(200).setBody("false"))
        server.enqueue(MockResponse().setResponseCode(302)
            .addHeader("Location", server.url("/sso.jsp?ticket=ST-1").toString()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>已登录</html>"))
        val result = CasLoginClient.login(
            baseUrl = server.url("/authserver/login").toString().removeSuffix("/"),
            username = "2023001", password = "secret123")
        assertTrue(result.isSuccess)
        // takeRequest() 按 FIFO 返回，先消费登录页 GET 与 needCaptcha GET，再取表单提交
        server.takeRequest()   // 1) 登录页 GET
        server.takeRequest()   // 2) needCaptcha GET
        val posted = server.takeRequest()!!   // 3) 表单提交
        val body = posted.body.readUtf8()
        assertTrue(body.contains("username=2023001"))
        assertTrue(body.contains("lt=LT-123-test"))
        assertTrue(body.contains("execution=e1s1"))
        assertTrue(body.contains("_eventId=submit"))
        assertTrue(body.contains("dllt=userNamePasswordLogin"))
        assertTrue(body.contains("rmShown=1"))
        assertTrue(body.contains("password="))   // 加密密文
        assertTrue(!body.contains("secret123"))  // 明文不能出现
        server.shutdown()
    }

    @Test
    fun `follows sso redirect to establish jwxt session cookie`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
        server.enqueue(MockResponse().setResponseCode(200).setBody("false"))
        server.enqueue(MockResponse().setResponseCode(302)
            .addHeader("Location", server.url("/sso.jsp?ticket=ST-1").toString()))
        server.enqueue(MockResponse().setResponseCode(302)
            .addHeader("Set-Cookie", "JSESSIONID=abc123; Path=/")
            .addHeader("Location", server.url("/jsxsd/framework/xsMainV.htmlx").toString()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>主框架</html>"))
        val result = CasLoginClient.login(
            baseUrl = server.url("/authserver/login").toString().removeSuffix("/"),
            username = "2023001", password = "secret123")
        assertTrue(result.isSuccess)
        server.takeRequest()   // 1) 登录页 GET
        server.takeRequest()   // 2) needCaptcha GET
        server.takeRequest()   // 3) 表单提交 302
        val ssoRequest = server.takeRequest()!!
        assertEquals("/sso.jsp?ticket=ST-1", ssoRequest.path)
        val frameRequest = server.takeRequest()!!
        assertEquals("/jsxsd/framework/xsMainV.htmlx", frameRequest.path)
        // sso.jsp 设置的会话 Cookie 必须带到后续 jsxsd 请求
        assertTrue("框架页请求应携带 sso.jsp 设置的 JSESSIONID",
            frameRequest.getHeader("Cookie")?.contains("JSESSIONID=abc123") == true)
        server.shutdown()
    }

    @Test
    fun `fails when server says wrong password`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
        server.enqueue(MockResponse().setResponseCode(200).setBody("false"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>您提供的用户名或者密码有误</html>"))
        val result = CasLoginClient.login(
            baseUrl = server.url("/authserver/login").toString().removeSuffix("/"),
            username = "2023001", password = "bad")
        assertTrue(result.isFailure)
        server.shutdown()
    }

    @Test
    fun `redirect without ticket is not a success`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
        server.enqueue(MockResponse().setResponseCode(200).setBody("false"))
        // 302 但 Location 无 ticket（如重定向回错误页）
        server.enqueue(MockResponse().setResponseCode(302)
            .addHeader("Location", "https://uia.njfu.edu.cn/authserver/login?service=x"))
        val result = CasLoginClient.login(
            baseUrl = server.url("/authserver/login").toString().removeSuffix("/"),
            username = "2023001", password = "bad")
        assertTrue(result.isFailure)
        server.shutdown()
    }

    @Test
    fun `reports service unavailable message`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
        server.enqueue(MockResponse().setResponseCode(200).setBody("false"))
        server.enqueue(MockResponse().setResponseCode(500)
            .setBody("<html>哎呦，出错啦 认证服务不可用</html>"))
        val result = CasLoginClient.login(
            baseUrl = server.url("/authserver/login").toString().removeSuffix("/"),
            username = "2023001", password = "bad")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("认证服务") == true)
        server.shutdown()
    }
}
