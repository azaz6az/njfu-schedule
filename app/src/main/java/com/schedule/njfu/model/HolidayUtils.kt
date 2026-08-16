package com.schedule.njfu.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 调休处理：把「某一天按星期几显示」的映射应用到课表与提醒。
 *
 * 背景：国内节假日调休后常出现「周六上周一的课」等情况，各校安排不一，
 * 故不内置节假日表，由用户在设置中按本校实际安排添加映射。
 *
 * 映射语义（值 0-7）：
 *  - 1-7：该日期【顶替】星期 X 的上课日——本周内星期 X 的课程只显示在这一天，
 *    原星期 X 的自然列不再显示课程（如 10-11 设为 1：周六补周一的课，周一当天放假）。
 *  - 0：该日期【放假】，不显示任何课程。
 *
 * 举例（2025 国庆调休，补周一的课）：{"2025-10-11":1}。
 */
object HolidayUtils {

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析设置中保存的映射 JSON：{"2025-10-11":1,"2025-10-08":0}；非法项忽略 */
    fun parseShifts(raw: String?): Map<LocalDate, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, Int>>(raw)
        }.getOrDefault(emptyMap()).mapNotNull { (key, value) ->
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
            if (value in 0..7) date to value else null
        }.toMap()
    }

    /**
     * 序列化为 JSON，供设置保存（按日期排序，输出稳定）；格式 {"2025-10-11":1,"2025-10-08":0}。
     * 复用与 [parseShifts] 相同的 kotlinx.serialization json 实例，保证编解码对称；
     * 键仍是 LocalDate.toString() 的 ISO 日期字符串（"2025-10-11"）。
     */
    fun serializeShifts(shifts: Map<LocalDate, Int>): String =
        json.encodeToString(shifts.toSortedMap().mapKeys { (date, _) -> date.toString() })

    /** 某日期实际应显示的星期（1=周一..7=周日）；0 = 放假无课；无映射返回自然星期 */
    fun shiftedDayOfWeek(date: LocalDate, shifts: Map<LocalDate, Int>): Int =
        shifts[date] ?: date.dayOfWeek.value

    /** 判断某日期的自然星期是否因调休被映射（供 UI 提示） */
    fun isShifted(date: LocalDate, shifts: Map<LocalDate, Int>): Boolean =
        shifts.containsKey(date)
}
