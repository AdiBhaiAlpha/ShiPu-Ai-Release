package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val token: String,
    val userId: String,
    val createdAt: Long = System.currentTimeMillis()
)
