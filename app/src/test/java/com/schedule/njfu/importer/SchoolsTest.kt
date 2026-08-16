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
    fun `gxu uses default mobile ua for adaptive login page`() {
        assertNull("广西大学 jwglxt 登录页应使用系统默认移动 UA", School.GXU.userAgent)
    }

    @Test
    fun `gxu login success excludes the login page itself`() {
        // 正方登录页自身即下发 JSESSIONID，必须排除，否则会「秒退」
        assertTrue(School.GXU.successUrlBlacklist.contains("login_slogin"))
    }

    @Test
    fun `njfu has no url blacklist`() {
        assertTrue("南林按 CAS 回跳判定，无需黑名单", School.NJFU.successUrlBlacklist.isEmpty())
    }
}
