package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sync_operations")
data class SyncOperationEntity(
    @PrimaryKey val operationId: String = "sync_op_${UUID.randomUUID().toString().take(12)}",
    val userId: String,
    val entityType: String, // "CONVERSATION", "MESSAGE", "MEMORY", "PREFERENCES", "KNOWLEDGE", "SYSTEM_PROMPT", "ADMIN_CONFIG"
    val entityId: String,
    val operationType: String, // "UPSERT", "DELETE"
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: String = "PENDING", // "PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"
    val revision: Long = 1L
)
