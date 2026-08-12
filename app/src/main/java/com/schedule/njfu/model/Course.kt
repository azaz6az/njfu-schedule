package com.schedule.njfu.model

import kotlinx.serialization.Serializable

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
