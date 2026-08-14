package com.schedule.njfu.importer

import com.schedule.njfu.importer.njfu.CasLoginClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.net.URLEncoder

/**
 * 真实教务系统冒烟测试（需外网访问 uia.njfu.edu.cn）。
 *
 * 手动运行：
 *   gradlew :app:testDebugUnitTest --tests "*Smoke*"
 *
 * 默认 @Ignore 跳过，避免常规构建/CI 连外网。
 * 覆盖：登录页解析 → needCaptcha → 验证码图片获取与解码 → 登录表单提交链路。
 */
@Ignore("需外网访问教务系统，手动运行")
class CasLoginSmokeTest {

    /** 1. 登录页可访问且含 lt/salt；验证码接口返回可解码的 JPEG */
    @Test
    fun `smoke - login page and captcha endpoint`() {
        val client = HttpSession.client
        val loginUrl = "${CasLoginClient.LOGIN_URL}?service=" +
            URLEncoder.encode(CasLoginClient.SERVICE_URL, "UTF-8")
        val pageHtml = client.newCall(Request.Builder()
            .url(loginUrl)
            .header("User-Agent", CasLoginClient.BROWSER_UA)
            .build()).execute().use { it.body!!.string() }
        assertTrue("登录页应包含 lt 表单", pageHtml.contains("name=\"lt\""))
        val page = CasLoginClient.parseLoginPage(pageHtml)
        assertTrue("lt 不应为空", page.lt.isNotBlank())
        assertTrue("salt 不应为空", page.salt.isNotBlank())

        // 验证码图片：应为 JPEG（魔数 + JFIF 标记），与 App 端 BitmapFactory 解码兼容
        val bytes = CasLoginClient.fetchCaptcha().getOrThrow()
        assertTrue("验证码字节应足够大", bytes.size > 100)
        assertEquals("JPEG 魔数 FF", 0xFF, bytes[0].toInt() and 0xFF)
        assertEquals("JPEG 魔数 D8", 0xD8, bytes[1].toInt() and 0xFF)
        assertEquals("JPEG 魔数 FF(2)", 0xFF, bytes[2].toInt() and 0xFF)
        // 含 JFIF/EXIF 标记（\u00FF\u00E0 或 \u00FF\u00E1），确认是完整 JPEG 结构
        val hasMarker = (0 until minOf(bytes.size - 4, 64)).any { i ->
            bytes[i].toInt() and 0xFF == 0xFF &&
                (bytes[i + 1].toInt() and 0xFF == 0xE0 || bytes[i + 1].toInt() and 0xFF == 0xE1)
        }
        assertTrue("JPEG 应含 JFIF/EXIF 段标记", hasMarker)
    }

    /** 2. 假账号登录：流程应走通到"用户名或密码错误"（或触发验证码机制） */
    @Test
    fun `smoke - login flow with fake credentials`() {
        val result = CasLoginClient.login(username = "0000000000", password = "smoke-test")
        assertTrue("登录应失败（假账号）", result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "流程应走到密码校验或验证码（实际: $msg）",
            msg.contains("用户名或密码") || msg.contains("验证码") || msg.contains("需要"),
        )
    }
}
