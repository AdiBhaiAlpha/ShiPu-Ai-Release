package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "admin_audit_logs")
data class AdminAuditLogEntity(
    @PrimaryKey val logId: String = "log_${UUID.randomUUID().toString().take(12)}",
    val action: String,
    val details: String,
    val userEmail: String,
    val timestamp: Long = System.currentTimeMillis()
)
