package com.schedule.njfu.importer

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.InputStream

/**
 * 解析南京林业大学教务系统「学生个人课表」导出的老式 .xls（BIFF）文件。
 *
 * 该文件并非扁平的课程列表，而是「星期 × 大节」的二维网格：
 *  - 第 0-1 行：标题 / 学年学期元信息
 *  - 第 2 行：表头（列 1..7 对应星期一..星期日，第 0 列为节次标签）
 *  - 第 3 行起：第一大节、第二大节…（每「大节」对应 2 节课）
 *  - 每个单元格内是多行文本，一块课程为 4 行：
 *       课程名
 *       教师
 *       周次([周])[XX-YY节]
 *       地点
 *    多个课程块用空行分隔；同一门课因周次/地点不同会拆成多块。
 *
 * 周次写法支持：1-12、1-6,8-10,12-13、1,3-12、单段如 13。
 */
object NjfuXlsImporter {

    /** 大节 → (开始节, 结束节) 映射。第一大节=01-02，依此类推；最多支持 12 节。 */
    private val PERIOD_BLOCKS = listOf(
        1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10, 11 to 12,
    )

    fun parse(input: InputStream): List<Course> {
        val courses = mutableListOf<Course>()

        HSSFWorkbook(input).use { wb ->
            val sheet = wb.getSheetAt(0) ?: return emptyList()
            val lastRow = sheet.lastRowNum
            val lastCell = sheet.getRow(0)?.lastCellNum ?: 0

            // 从第 3 行开始是「第一大节」所在行；但稳妥起见扫描所有行，找节次标签行
            for (rowIdx in 0..lastRow) {
                val row = sheet.getRow(rowIdx) ?: continue
                // 第 0 列若是「第N大节」，则本行的列 1..7 是课程
                val label = cellText(row, 0)
                val block = blockIndexOf(label) ?: continue
                val (startPeriod, endPeriod) = PERIOD_BLOCKS[block]
                // 列 1..7 对应星期一到日（上限取 8 或实际列数）
                val maxCol = minOf(8, lastCell)
                for (col in 1 until maxCol) {
                    val text = cellText(row, col)
                    if (text.isBlank()) continue
                    val dayOfWeek = col // 列1=星期一 … 列7=星期日
                    courses += parseCellCourses(text, dayOfWeek, startPeriod, endPeriod)
                }
            }
        }
        return courses
    }

    /** "第一大节" → 0, "第二大节" → 1, ... 非节次标签返回 null */
    private fun blockIndexOf(label: String): Int? {
        val m = Regex("^第([一二三四五六七八九]+)大节").find(label.trim()) ?: return null
        val cn = m.groupValues[1]
        return chineseToInt(cn)?.let { it - 1 }
    }

    /** 中文数字 → Int（支持一~十二） */
    internal fun chineseToInt(s: String): Int? = WeekUtils.chineseToInt(s)

    /** 解析一个单元格内的多块课程文本 */
    private fun parseCellCourses(
        text: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
    ): List<Course> {
        val result = mutableListOf<Course>()
        // 按空行切分成多个「课程块」
        val blocks = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        for (block in blocks) {
            val lines = block.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (lines.size < 3) continue
            val name = lines[0]
            val teacher = lines.getOrNull(1).orEmpty()
            // 找到含「节」的行作为周次行，其后是地点
            val periodIdx = lines.indexOfFirst { it.contains("节") && it.contains("周") }
            val weeksText = if (periodIdx >= 0) lines[periodIdx] else ""
            val location = lines.getOrNull(periodIdx + 1).orEmpty()
            if (name.isEmpty()) continue
            result += Course(
                name = name,
                teacher = teacher,
                location = location,
                dayOfWeek = dayOfWeek,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                weeks = parseWeeks(weeksText),
                color = 0,
                source = "auto",
            )
        }
        return result
    }

    /** 解析周次文本，如 "1-12([周])[01-02节]" 或 "1-6,8-10,12-13([周])[03-04节]" */
    internal fun parseWeeks(text: String): Int = WeekUtils.parseWeeksText(text)

    private fun cellText(row: org.apache.poi.ss.usermodel.Row, idx: Int): String {
        val cell = row.getCell(idx) ?: return ""
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                val d = cell.numericCellValue
                if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
            }
            else -> cell.toString()
        }
    }
}
