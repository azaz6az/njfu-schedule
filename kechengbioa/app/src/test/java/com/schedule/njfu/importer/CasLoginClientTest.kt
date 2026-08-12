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
            .addHeader("Location", "http://jwxt.njfu.edu.cn/sso.jsp?ticket=ST-1"))
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
}
