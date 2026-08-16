package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "admin_knowledge")
data class KnowledgeEntity(
    @PrimaryKey val knowledgeId: String = "knw_${UUID.randomUUID().toString().take(12)}",
    val title: String,
    val content: String,
    val category: String,
    val tags: String, // comma separated
    val status: String = "ENABLED", // ENABLED / DISABLED
    val version: Int = 1,
    val author: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
