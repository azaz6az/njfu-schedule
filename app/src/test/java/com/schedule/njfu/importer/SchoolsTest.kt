package com.schedule.njfu.importer

import com.schedule.njfu.importer.njfu.CasLoginClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * School 枚举登录配置测试：
 * 锁定各学校的 UA 策略与登录成功判定规则，防止回归。
 */
class SchoolsTest {

    @Test
    fun `njfu uses desktop ua for CAS login page`() {
        assertEquals("南林 CAS 必须桌面 UA（移动 UA 缺表单字段）", CasLoginClient.BROWSER_UA, School.NJFU.userAgent)
    }

    @Test
    fun `njfu has no url blacklist`() {
        assertTrue("南林按 CAS 回跳判定，无需黑名单", School.NJFU.successUrlBlacklist.isEmpty())
    }

    @Test
    fun `gxu uses default mobile ua for adaptive login page`() {
        assertNull("广西大学 jwglxt 登录页应使用系统默认移动 UA", School.GXU.userAgent)
    }

    @Test
    fun `gxu login success excludes the login page itself`() {
        // 正方登录页自身即下发 JSESSIONID，必须排除，否则会「秒退」
        assertTrue(School.GXU.successUrlBlacklist.contains("login_slogin"))
    }

    @Test
    fun `gxu points to jwglxt context path`() {
        val config = requireNotNull(School.GXU.zfJwglxt) { "广西大学应是正方 jwglxt 学校" }
        assertEquals("https://jwxt2018.gxu.edu.cn", config.baseUrl)
        assertEquals("/jwglxt", config.contextPath)
    }

    @Test
    fun `gdou is a zhengfang school deployed at root context path`() {
        // 广东海洋大学：正方系统部署在根路径（无 /jwglxt 前缀），接口与登录页都在根路径
        val config = requireNotNull(School.GDOU.zfJwglxt) { "广东海洋大学应是正方 jwglxt 学校" }
        assertEquals("https://jw.gdou.edu.cn", config.baseUrl)
        assertEquals("", config.contextPath)
    }

    @Test
    fun `gdou uses default mobile ua and login page blacklist`() {
        assertNull("广东海洋大学正方登录页应使用系统默认移动 UA", School.GDOU.userAgent)
        assertTrue(School.GDOU.successUrlBlacklist.contains("login_slogin"))
    }

    @Test
    fun `zhengfang config derives api urls from context path`() {
        val gxu = School.GXU.zfJwglxt!!
        assertEquals(
            "https://jwxt2018.gxu.edu.cn/jwglxt/xtgl/login_slogin.html",
            gxu.loginUrl,
        )
        // V9 实测课表接口：GET xskbcx_cxXsgrkb.html（必须带 .html 后缀，否则 404），xnm/xqm 拼入查询串
        assertEquals(
            "https://jwxt2018.gxu.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151&xnm=2025&xqm=3",
            gxu.scheduleUrl("2025", "3"),
        )
        assertEquals(
            "https://jwxt2018.gxu.edu.cn/jwglxt/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105",
            gxu.examUrl("2025", "3"),
        )

        val gdou = School.GDOU.zfJwglxt!!
        assertEquals("https://jw.gdou.edu.cn/xtgl/login_slogin.html", gdou.loginUrl)
        assertEquals(
            "https://jw.gdou.edu.cn/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151&xnm=2025&xqm=12",
            gdou.scheduleUrl("2025", "12"),
        )
    }

    @Test
    fun `parse custom zhengfang login url with jwglxt context path`() {
        val config = ZfJwglxtConfig.fromLoginUrl("https://jw.school.edu.cn/jwglxt/xtgl/login_slogin.html")
        assertTrue(config != null)
        assertEquals("https://jw.school.edu.cn", config!!.baseUrl)
        assertEquals("/jwglxt", config.contextPath)
    }

    @Test
    fun `parse custom zhengfang login url at root context path`() {
        val config = ZfJwglxtConfig.fromLoginUrl("https://jw.school.edu.cn/xtgl/login_slogin.html")
        assertTrue(config != null)
        assertEquals("https://jw.school.edu.cn", config!!.baseUrl)
        assertEquals("", config.contextPath)
    }

    @Test
    fun `parse custom url tolerates trailing slash and whitespace`() {
        val config = ZfJwglxtConfig.fromLoginUrl("  https://jw.school.edu.cn/xtgl/login_slogin.html/  ")
        assertTrue(config != null)
        assertEquals("https://jw.school.edu.cn", config!!.baseUrl)
        assertEquals("", config.contextPath)
    }

    @Test
    fun `parse rejects non url input`() {
        assertNull(ZfJwglxtConfig.fromLoginUrl("not a url"))
        assertNull(ZfJwglxtConfig.fromLoginUrl(""))
        assertNull(ZfJwglxtConfig.fromLoginUrl("ftp://jw.school.edu.cn"))
    }
}