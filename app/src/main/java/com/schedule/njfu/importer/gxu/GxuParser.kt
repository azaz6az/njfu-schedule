package com.schedule.njfu.importer.gxu

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import com.schedule.njfu.model.WeekUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.LocalDate

/**
 * 广西大学（正方 jwglxt 新版教务系统）JSON 解析器（纯函数，可单测）。
 *
 * 接口返回 JSON：课表为 `kbList[]`，考试为 `items[]`。字段名与旧版 jsxsd 略有差异，
 * 且部分字段缺失/为空时需容错，故用 [Json] + 可空默认值解析。
 */
object GxuParser {

    /** 容错：忽略未声明的字段，缺失字段用默认值 */
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class KbItem(
        val kcmc: String = "",
        val xm: String = "",
        val xqj: String = "",
        val jcs: String = "",
        val zcd: String = "",
        val jxdd: String = "",
        val jsmc: String = "",
        val xqmc: String = "",
    )

    @Serializable
    private data class KsItem(
        val kcmc: String = "",
        val kssj: String = "",
        val cdmc: String = "",
    )

    /** 课程表 JSON（`kbList[]` 数组，或包裹整个响应的对象）→ [Course] 列表 */
    fun parseScheduleJson(jsonText: String): List<Course> {
        val items: List<KbItem> = when (val el = runCatching { json.parseToJsonElement(jsonText) }
            .getOrElse { throw IllegalStateException("课表 JSON 结构无法解析，教务接口可能已改版") }
        ) {
            is JsonArray -> runCatching { json.decodeFromJsonElement<List<KbItem>>(el) }
                .getOrElse { throw IllegalStateException("课表 JSON 结构无法解析，教务接口可能已改版") }
            is JsonObject -> when (val arr = el["kbList"]) {
                null -> emptyList()
                is JsonArray -> runCatching { json.decodeFromJsonElement<List<KbItem>>(arr) }
                    .getOrElse { throw IllegalStateException("课表 JSON 结构无法解析，教务接口可能已改版") }
                else -> emptyList()
            }
            else -> emptyList()
        }
        return items.mapNotNull(::toCourse)
    }

    /** 考试 JSON（`items[]` 数组或包裹整个响应的对象）→ [Exam] 列表；无考试返回空列表 */
    fun parseExamsJson(jsonText: String): List<Exam> {
        val list: List<KsItem> = when (val el = runCatching { json.parseToJsonElement(jsonText) }
            .getOrElse { return emptyList() }
        ) {
            is JsonArray -> runCatching { json.decodeFromJsonElement<List<KsItem>>(el) }
                .getOrElse { return emptyList() }
            is JsonObject -> when (val arr = el["items"]) {
                null -> emptyList()
                is JsonArray -> runCatching { json.decodeFromJsonElement<List<KsItem>>(arr) }
                    .getOrElse { return emptyList() }
                else -> emptyList()
            }
            else -> emptyList()
        }
        return list.mapNotNull(::toExam)
    }

    private fun toCourse(it: KbItem): Course? {
        val name = it.kcmc.trim()
        if (name.isEmpty()) return null
        val day = it.xqj.trim().toIntOrNull() ?: return null
        if (day !in 1..7) return null
        val periods = parsePeriods(it.jcs) ?: 0 to 0
        if (periods.first == 0) return null
        return Course(
            name = name,
            teacher = it.xm.trim(),
            location = combineLocation(it),
            dayOfWeek = day,
            startPeriod = periods.first,
            endPeriod = periods.second,
            // 周次：省去"周"字表达后交通用解析器，解析失败 weeks=0（由导入兜底按全学期显示）
            weeks = WeekUtils.parseWeeksText(it.zcd),
            color = 0,
        )
    }

    private fun toExam(it: KsItem): Exam? {
        val name = it.kcmc.trim()
        val kssj = it.kssj.trim()
        if (name.isEmpty() || kssj.isEmpty()) return null
        // 时间格式："yyyy-MM-dd HH:mm:ss" 或 "yyyy-MM-dd HH:mm"
        val m = Regex("^(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})").find(kssj) ?: return null
        return Exam(
            name = name,
            date = m.groupValues[1],
            note = m.groupValues[2],
            location = it.cdmc.trim(),
        )
    }

    /**
     * 节次归一化：`1-2`、`01-02`、`1-2节`、`1-2-3` 等 → (startPeriod, endPeriod)。
     * 识别开头连续数字作为起止节，无法识别返回 null。
     */
    private fun parsePeriods(raw: String): Pair<Int, Int>? {
        var t = raw.trim().removeSuffix("节").trim()
        if (t.isEmpty()) return null
        val parts = t.split('-', '–', '—', '~', '～')
        val nums = parts.mapNotNull { p ->
            val digits = Regex("^\\d+").find(p.trim())?.value ?: return@mapNotNull null
            digits.toInt()
        }
        if (nums.isEmpty()) return null
        val start = nums.first()
        val end = if (nums.size >= 2) nums.last() else start
        if (start <= 0 || end < start) return null
        return start to end
    }

    /** 校区 + 地点拼接：xqmc 校区、jxdd 地点（退回 jsmc 教室名），去空段、空格分隔 */
    private fun combineLocation(it: KbItem): String {
        val primary = it.jxdd.trim().ifEmpty { it.jsmc.trim() }
        val segments = buildList {
            if (it.xqmc.trim().isNotEmpty()) add(it.xqmc.trim())
            if (primary.isNotEmpty()) add(primary)
        }
        return segments.joinToString(" ")
    }

    /**
     * 由学期起始日推导正方教务系统的学年度(xnm)/学季代码(xqm)。
     * null → null（无法推导，需用户手动选择）。
     * 规则：月份 >= 8 → 本学年度秋季第 1 学期；月份 <= 7 → 上一学年度春季第 2 学期。
     */
    fun deriveSemester(semesterStart: LocalDate?): Pair<String, String>? {
        if (semesterStart == null) return null
        val m = semesterStart.monthValue
        return if (m >= 8) semesterStart.year.toString() to "3"
        else (semesterStart.year - 1).toString() to "12"
    }

    /** 把正方 xnm/xqm 转成可读学期标签，如 "2025-2026 学年第 1 学期"（16 → 第 3 学期） */
    fun semesterLabel(xnm: String, xqm: String): String {
        val year = xnm
        val next = runCatching { year.toInt() + 1 }.getOrElse { "" }
        val term = when (xqm) {
            "3" -> "1"
            "12" -> "2"
            "16" -> "3"
            else -> xqm
        }
        return "$year-$next 学年第 $term 学期"
    }
}
