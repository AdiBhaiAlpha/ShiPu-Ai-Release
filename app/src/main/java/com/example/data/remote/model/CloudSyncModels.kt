package com.example.data.remote.model

import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.model.UserMemory
import com.example.data.model.UserPreferences
import com.example.data.local.db.KnowledgeEntity
import com.example.data.local.db.SystemPromptEntity
import com.example.data.local.db.AdminConfigEntity
import com.example.data.local.db.AdminAuditLogEntity

/**
 * Cloud synchronization data transfer objects and manifest for ShiPu AI MongoDB cloud architecture.
 */
data class CloudManifest(
    val userId: String,
    val schemaVersion: Int = 1,
    val dataRevision: Long = 1L,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val conversationsRevision: Long = 1L,
    val messagesRevision: Long = 1L,
    val memoriesRevision: Long = 1L,
    val preferencesRevision: Long = 1L,
    val globalConfigRevision: Long = 1L
)

data class SyncOperationPayload(
    val operationId: String,
    val userId: String,
    val entityType: String, // "CONVERSATION", "MESSAGE", "MEMORY", "PREFERENCES", "KNOWLEDGE", "SYSTEM_PROMPT", "ADMIN_CONFIG"
    val entityId: String,
    val operationType: String, // "UPSERT", "DELETE"
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val revision: Long = 1L
)

data class PushSyncRequest(
    val userId: String,
    val sessionToken: String,
    val operations: List<SyncOperationPayload>
)

data class PushSyncResponse(
    val success: Boolean,
    val syncedCount: Int,
    val newManifest: CloudManifest,
    val message: String? = null
)

data class UserCloudStateResponse(
    val user: User,
    val preferences: UserPreferences,
    val conversations: List<Conversation>,
    val messages: List<Message>,
    val memories: List<UserMemory>,
    val manifest: CloudManifest,
    val globalConfigRevision: Long
)

data class GlobalCloudStateResponse(
    val systemPrompt: SystemPromptEntity,
    val knowledgeList: List<KnowledgeEntity>,
    val adminConfigs: List<AdminConfigEntity>,
    val globalConfigRevision: Long
)

data class CloudAuthResponse(
    val success: Boolean,
    val user: User?,
    val sessionToken: String?,
    val message: String? = null
)
