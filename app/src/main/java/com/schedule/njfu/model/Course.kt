package com.schedule.njfu.model

import kotlinx.serialization.Serializable

/** 一门课程。id 为 0 表示尚未持久化的新课程（入库后由主键回填）。
 *  dayOfWeek 1=周一..7=周日；startPeriod/endPeriod 为起止节次；
 *  weeks 为位掩码（见 [com.schedule.njfu.model.WeekUtils]，位 n 表示第 n 周有课）；
 *  color 为 ARGB 展示色（见 [com.schedule.njfu.data.CourseMapper]）；
 *  source 标识数据来源：auto=自动导入，manual=手动录入。 */
@Serializable
data class Course(
    val id: Long = 0,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int,          // 1=周一 .. 7=周日
    val startPeriod: Int,        // 第几节开始
    val endPeriod: Int,          // 第几节结束
    val weeks: Int,              // 位掩码
    val color: Int,              // ARGB
    val source: String = "auto", // "auto" | "manual"
    val note: String = "",
)
