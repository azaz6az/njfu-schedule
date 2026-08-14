package com.schedule.njfu.importer

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.dhatim.fastexcel.reader.ReadableWorkbook
import java.io.InputStream

object ExcelImporter {

    /** 列顺序固定：课程名,教师,地点,星期,开始节,结束节,周次 */
    fun parse(input: InputStream): List<Course> {
        val courses = mutableListOf<Course>()
        ReadableWorkbook(input).use { wb ->
            val sheet = wb.firstSheet
            sheet.openStream().use { rows ->
                var first = true
                for (row in rows) {
                    if (first) { first = false; continue } // 跳表头
                    val cell = { i: Int -> row.getCell(i)?.text ?: "" }
                    val name = cell(0).trim()
                    if (name.isEmpty()) continue
                    val startPeriod = cell(4).trim().toIntOrNull() ?: 1
                    courses += Course(
                        name = name,
                        teacher = cell(1).trim(),
                        location = cell(2).trim(),
                        dayOfWeek = cell(3).trim().toIntOrNull() ?: continue,
                        startPeriod = startPeriod,
                        endPeriod = cell(5).trim().toIntOrNull() ?: startPeriod,
                        weeks = parseWeeks(cell(6).trim()),
                        color = 0,
                    )
                }
            }
        }
        return courses
    }

    /** 支持 "1-16"、"1,3,5"、"1-16(单)"、"2-16(双)"、"单周"、"双周" 等（统一解析器） */
    fun parseWeeks(text: String): Int = WeekUtils.parseWeeksText(text)
}
