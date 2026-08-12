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

    /** 支持 "1-16"、"1,3,5"、"单周"、"双周" */
    fun parseWeeks(text: String): Int {
        val t = text.trim()
        if (t == "单周") return WeekUtils.oddWeeks(1, WeekUtils.MAX_WEEKS)
        if (t == "双周") return WeekUtils.evenWeeks(1, WeekUtils.MAX_WEEKS)
        var mask = 0
        for (part in t.split(',', '、', '，')) {
            val p = part.trim()
            val range = Regex("^(\\d+)\\s*[-–~至]\\s*(\\d+)$").find(p)
            if (range != null) {
                mask = mask or WeekUtils.maskFor(range.groupValues[1].toInt(), range.groupValues[2].toInt())
            } else {
                p.toIntOrNull()?.let { mask = mask or WeekUtils.maskFor(it) }
            }
        }
        return mask
    }
}
