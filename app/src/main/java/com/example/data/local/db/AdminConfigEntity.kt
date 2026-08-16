package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_configs")
data class AdminConfigEntity(
    @PrimaryKey val configKey: String,
    val configValue: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String
)
