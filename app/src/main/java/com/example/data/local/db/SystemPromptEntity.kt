package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_prompts")
data class SystemPromptEntity(
    @PrimaryKey val promptId: String = "sys_prompt_current",
    val content: String,
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String
)
