package com.schedule.njfu.importer

import com.schedule.njfu.importer.njfu.decodeScheduleHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class NjfuAdapterTest {

    @Test
    fun `decodes utf8 html without declared charset`() {
        val html = "<table><td>星期一</td></table>"
        assertEquals(html, decodeScheduleHtml(html.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `decodes gbk html by meta charset declaration`() {
        // 正方系统页面常见 GBK 编码：按 GBK 编码字节，声明 charset=gbk
        val body = "<html><head><meta charset=\"gbk\"></head><body><table><td>星期一</td></table></body></html>"
        val gbkBytes = body.toByteArray(Charset.forName("GBK"))
        val decoded = decodeScheduleHtml(gbkBytes)
        assertTrue("GBK 页面应解码出中文（星期）", decoded.contains("星期"))
        // 若误按 UTF-8 解码，GBK 双字节会产生乱码，星期二字不会出现
    }

    @Test
    fun `decodes gb2312 html without quotes in meta`() {
        val body = "<meta charset=gb2312><td>高等数学</td>"
        val gbBytes = body.toByteArray(Charset.forName("GB2312"))
        assertTrue(decodeScheduleHtml(gbBytes).contains("高等数学"))
    }
}
