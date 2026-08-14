package com.schedule.njfu.importer

import com.schedule.njfu.importer.gxu.GxuParser
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GxuParserTest {

    // ---- 课表解析 ----

    @Test
    fun `parses schedule json with zero padded periods and week suffix`() {
        val json = """
            {"kbList":[
              {
                "kcmc": "高等数学A",
                "xm": "张三",
                "xqj": "1",
                "jcs": "01-02",
                "zcd": "1-16周",
                "jxdd": "综合教学楼-101",
                "xqmc": "东校区"
              }
            ]}
        """.trimIndent()
        val courses = GxuParser.parseScheduleJson(json)
        assertEquals(1, courses.size)
        val c = courses.first()
        assertEquals("高等数学A", c.name)
        assertEquals("张三", c.teacher)
        assertEquals(1, c.dayOfWeek)
        assertEquals(1, c.startPeriod)
        assertEquals(2, c.endPeriod)
        assertTrue(WeekUtils.contains(c.weeks, 1))
        assertTrue(WeekUtils.contains(c.weeks, 16))
        assertEquals("东校区 综合教学楼-101", c.location)
        assertEquals(0, c.color)
    }

    @Test
    fun `parses period variants`() {
        fun parse(jcs: String): Pair<Int, Int> {
            val json = """{"kbList":[{"kcmc":"课","xqj":"3","jcs":"$jcs"}]}"""
            val c = GxuParser.parseScheduleJson(json).first()
            return c.startPeriod to c.endPeriod
        }
        assertEquals(1 to 2, parse("1-2"))
        assertEquals(3 to 4, parse("3-4节"))
        assertEquals(1 to 3, parse("01-03"))
        assertEquals(5 to 5, parse("5"))
    }

    @Test
    fun `parses location combination from xqmc jxdd jsmc`() {
        // 只有 jxdd（无校区）
        val onlyRoom = GxuParser.parseScheduleJson(
            """{"kbList":[{"kcmc":"课","xqj":"2","jcs":"1-2","jxdd":"工程楼302"}]}"""
        ).first()
        assertEquals("工程楼302", onlyRoom.location)

        // 只有 jsmc（教室名，退回）
        val onlyClassroom = GxuParser.parseScheduleJson(
            """{"kbList":[{"kcmc":"课","xqj":"2","jcs":"1-2","jsmc":"教室A-201","xqmc":"西校区"}]}"""
        ).first()
        assertEquals("西校区 教室A-201", onlyClassroom.location)

        // 全部为空 → 空地点
        val empty = GxuParser.parseScheduleJson(
            """{"kbList":[{"kcmc":"课","xqj":"2","jcs":"1-2"}]}"""
        ).first()
        assertEquals("", empty.location)
    }

    @Test
    fun `multiple courses in same slot are returned as separate courses`() {
        val json = """
            {"kbList":[
              {"kcmc":"操作系统","xqj":"4","jcs":"3-4","zcd":"1-8周","jxdd":"A101"},
              {"kcmc":"网络编程","xqj":"4","jcs":"3-4","zcd":"9-16周","jxdd":"A202","xm":"李四"}
            ]}
        """.trimIndent()
        val courses = GxuParser.parseScheduleJson(json)
        assertEquals(2, courses.size)
        // 同格两门课
        assertEquals("操作系统", courses[0].name)
        assertEquals("网络编程", courses[1].name)
        assertEquals(4, courses[0].dayOfWeek)
        assertEquals(4, courses[1].dayOfWeek)
        assertEquals(3, courses[0].startPeriod)
        assertEquals(3, courses[1].startPeriod)
        assertEquals(4, courses[0].endPeriod)
        assertEquals(4, courses[1].endPeriod)
    }

    @Test
    fun `empty kbList returns no courses`() {
        assertTrue(GxuParser.parseScheduleJson("""{"kbList":[]}""").isEmpty())
        assertTrue(GxuParser.parseScheduleJson("""{"kbList":null}""").isEmpty())
        assertTrue(GxuParser.parseScheduleJson("{}").isEmpty())
    }

    @Test
    fun `bare kbList array is accepted`() {
        val courses = GxuParser.parseScheduleJson(
            """[{"kcmc":"线性代数","xqj":"5","jcs":"5-6","zcd":"1-16"}]"""
        )
        assertEquals(1, courses.size)
        assertEquals("线性代数", courses.first().name)
    }

    @Test
    fun `missing optional fields are tolerated and invalid entries dropped`() {
        val json = """
            {"kbList":[
              {"kcmc":"体育","xqj":"4","jcs":"2-2"},
              {"xm":"无名","xqj":"5","jcs":"1-2"},
              {"kcmc":"坏星期","xqj":"9","jcs":"1-2"},
              {"kcmc":"坏节次","xqj":"1","jcs":""}
            ]}
        """.trimIndent()
        val courses = GxuParser.parseScheduleJson(json)
        assertEquals(1, courses.size)
        assertEquals("体育", courses.first().name)
        assertEquals("", courses.first().teacher)
        assertEquals("", courses.first().location)
        // 缺周次 → weeks=0（导入兜底按全学期）
        assertEquals(0, courses.first().weeks)
    }

    @Test(expected = IllegalStateException::class)
    fun `malformed schedule json throws chinese reason`() {
        GxuParser.parseScheduleJson("not json at all")
    }

    // ---- 考试解析 ----

    @Test
    fun `parses exams with full and short time formats`() {
        val json = """
            {"items":[
              {"kcmc":"高等数学A","kssj":"2025-07-02 09:00:00","cdmc":"考场-东101"},
              {"kcmc":"英语","kssj":"2025-07-05 14:30","cdmc":"考场-西202"}
            ]}
        """.trimIndent()
        val exams = GxuParser.parseExamsJson(json)
        assertEquals(2, exams.size)
        assertEquals("高等数学A", exams[0].name)
        assertEquals("2025-07-02", exams[0].date)
        assertEquals("09:00", exams[0].note)
        assertEquals("考场-东101", exams[0].location)
        assertEquals("英语", exams[1].name)
        assertEquals("2025-07-05", exams[1].date)
        assertEquals("14:30", exams[1].note)
    }

    @Test
    fun `empty items returns empty exams`() {
        assertTrue(GxuParser.parseExamsJson("""{"items":[]}""").isEmpty())
        assertTrue(GxuParser.parseExamsJson("{}").isEmpty())
        // 非 JSON 或结构异常不崩溃，返回空
        assertTrue(GxuParser.parseExamsJson("bad").isEmpty())
    }

    @Test
    fun `bare items array accepted`() {
        val exams = GxuParser.parseExamsJson(
            """[{"kcmc":"物理","kssj":"2025-07-08 10:00:00"}]"""
        )
        assertEquals(1, exams.size)
        assertEquals("10:00", exams[0].note)
    }

    // ---- 学期推导 ----

    @Test
    fun `deriveSemester boundaries`() {
        val sep = GxuParser.deriveSemester(LocalDate.of(2025, 9, 1))
        assertNotNull(sep)
        assertEquals("2025" to "3", sep)

        val mar = GxuParser.deriveSemester(LocalDate.of(2025, 3, 1))
        assertNotNull(mar)
        assertEquals("2024" to "12", mar)

        val jan = GxuParser.deriveSemester(LocalDate.of(2025, 1, 6))
        assertNotNull(jan)
        assertEquals("2024" to "12", jan)
    }

    @Test
    fun `deriveSemester month 8 is autumn and 7 is spring`() {
        assertEquals(GxuParser.deriveSemester(LocalDate.of(2026, 8, 3)), "2026" to "3")
        assertEquals(GxuParser.deriveSemester(LocalDate.of(2026, 7, 1)), "2025" to "12")
    }

    @Test
    fun `deriveSemester null input yields null`() {
        assertNull(GxuParser.deriveSemester(null))
    }

    @Test
    fun `semesterLabel formats autumn spring and summer`() {
        assertEquals("2025-2026 学年第 1 学期", GxuParser.semesterLabel("2025", "3"))
        assertEquals("2025-2026 学年第 2 学期", GxuParser.semesterLabel("2025", "12"))
        assertEquals("2025-2026 学年第 3 学期", GxuParser.semesterLabel("2025", "16"))
    }
}
