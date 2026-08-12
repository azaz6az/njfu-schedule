package com.schedule.njfu.model

import kotlinx.serialization.Serializable

@Serializable
data class Exam(
    val id: Long = 0,
    val name: String,
    val date: String,            // ISO yyyy-MM-dd
    val location: String = "",
    val note: String = "",
)
