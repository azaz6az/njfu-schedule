package com.schedule.njfu.model

data class Exam(
    val id: Long = 0,
    val name: String,
    val date: String,            // ISO yyyy-MM-dd
    val location: String = "",
    val note: String = "",
)
