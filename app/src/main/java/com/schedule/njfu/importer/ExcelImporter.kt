package com.schedule.njfu.importer

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.dhatim.fastexcel.reader.ReadableWorkbook
import java.io.InputStream

object ExcelImporter {

    /** 中文星期（星期一~星期日、周一~周日）与阿拉伯数字 → 1..7；无法识别返回 null */
    private fun parseDay(s: String): Int? {
        val t = s.trim()
        if (t.isEmpty()) return null
        t.toIntOrNull()?.takeIf { it in 1..7 }?.let { return it }
        val zh = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7,
            "七" to 7,
        )
        zh[t.removePrefix("星期")]?.let { return it }
        zh[t.removePrefix("周")]?.let { return it }
        return null
    }

    /** 轻量行级调试日志（importer 无 Context，Android Log 不依赖 Context 且单元测试中为 no-op） */
    private fun debugLog(rowNo: Int, msg: String) {
        android.util.Log.d("ExcelImporter", "第 $rowNo 行：$msg")
    }

    /** 列顺序固定：课程名,教师,地点,星期,开始节,结束节,周次 */
    fun parse(input: InputStream): List<Course> {
        val courses = mutableListOf<Course>()
        ReadableWorkbook(input).use { wb ->
            val sheet = wb.firstSheet
            sheet.openStream().use { rows ->
                var first = true
                var rowNo = 0
                for (row in rows) {
                    rowNo++
                    if (first) { first = false; continue } // 跳表头
                    // 列可能超出已填充宽度（如无周次列的窄表）：getCell(i) 对越界列抛
                    // IndexOutOfBoundsException，统一安全取值，缺失列按空串处理
                    val cell = { i: Int -> runCatching { row.getCell(i)?.text }.getOrNull() ?: "" }
                    val name = cell(0).trim()
                    if (name.isEmpty()) continue
                    val day = parseDay(cell(3))
                    if (day == null) { debugLog(rowNo, "非法星期「${cell(3)}」，已跳过"); continue }
                    val startPeriod = cell(4).trim().toIntOrNull()
                    if (startPeriod == null || startPeriod <= 0) {
                        debugLog(rowNo, "非法开始节「${cell(4)}」，已跳过"); continue
                    }
                    courses += Course(
                        name = name,
                        teacher = cell(1).trim(),
                        location = cell(2).trim(),
                        dayOfWeek = day,
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
