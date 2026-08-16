package com.schedule.njfu.model

import kotlinx.serialization.Serializable

/** 一场考试。id 为 0 表示尚未持久化的新考试。
 *  date 为 ISO yyyy-MM-dd 字符串；location/note 为地点与备注。 */
@Serializable
data class Exam(
    val id: Long = 0,
    val name: String,
    val date: String,            // ISO yyyy-MM-dd
    val location: String = "",
    val note: String = "",
)
