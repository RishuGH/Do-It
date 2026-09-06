package com.rishugh.doit.data

import androidx.annotation.Keep

@Keep
data class Task(
    val id: Int = 0,
    val title: String = "",
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0
)

