package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val userId: String,
    val lastSyncedAt: Long = 0L,
    val dataRevision: Long = 0L,
    val conversationsRevision: Long = 0L,
    val messagesRevision: Long = 0L,
    val memoriesRevision: Long = 0L,
    val preferencesRevision: Long = 0L,
    val globalConfigRevision: Long = 0L,
    val isInitialRestoreCompleted: Boolean = false
)
