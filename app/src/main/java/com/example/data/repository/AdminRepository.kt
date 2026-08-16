package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminRepository(context: Context? = null) {
    private val db = AppDatabase.getInstance(context)

    suspend fun verifySuperAdmin(userId: String): Boolean = withContext(Dispatchers.IO) {
        val user = db.userDao().getUserById(userId) ?: return@withContext false
        user.email.trim().lowercase() == "chitronbhattacharjee@gmail.com" || user.role == "SUPER_ADMIN"
    }

    suspend fun getSystemPrompt(userId: String): SystemPromptEntity? = withContext(Dispatchers.IO) {
        if (!verifySuperAdmin(userId)) return@withContext null
        db.systemPromptDao().getCurrentPrompt() ?: SystemPromptEntity(
            content = "You are ShiPu AI, an empathetic, highly intelligent personal AI assistant.",
            version = 1,
            updatedBy = "System Default"
        )
    }

    suspend fun saveSystemPrompt(userId: String, userEmail: String, newContent: String): Boolean = withContext(Dispatchers.IO) {
        if (!verifySuperAdmin(userId)) return@withContext false
        val current = db.systemPromptDao().getCurrentPrompt()
        val nextVersion = (current?.version ?: 0) + 1
        val entity = SystemPromptEntity(
            promptId = "sys_prompt_current",
            content = newContent,
            version = nextVersion,
            updatedAt = System.currentTimeMillis(),
            updatedBy = userEmail
        )
        db.systemPromptDao().savePrompt(entity)
        db.adminAuditLogDao().insertLog(
            AdminAuditLogEntity(
                action = "System prompt updated",
                details = "Updated system prompt to version $nextVersion",
                userEmail = userEmail
            )
        )
        true
    }

    suspend fun getAllKnowledge(userId: String): List<KnowledgeEntity> = withContext(Dispatchers.IO) {
        if (!verifySuperAdmin(userId)) return@withContext emptyList()
        db.knowledgeDao().getAllKnowledge()
    }

    suspend fun saveKnowledge(
        userId: String,
        userEmail: String,
        knowledgeId: String?,
        title: String,
        content: String,
        category: String,
        tags: String,
        status: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!verifySuperAdmin(userId)) return@withContext false
        val id = knowledgeId ?: "knw_${java.util.UUID.randomUUID().toString().take(12)}"
        val existing = db.knowledgeDao().getAllKnowledge().find { it.knowledgeId == id }
        val nextVersion = (existing?.version ?: 0) + 1
        val entity = KnowledgeEntity(
            knowledgeId = id,
            title = title,
            content = content,
            category = category,
            tags = tags,
            status = status,
            version = nextVersion,
            author = userEmail,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        db.knowledgeDao().insertOrUpdate(entity)
        db.adminAuditLogDao().insertLog(
            AdminAuditLogEntity(
                action = if (existing == null) "Knowledge entry created" else "Knowledge entry updated",
                details = "Knowledge: '$title' (Category: $category)",
                userEmail = userEmail
            )
        )
        true
    }

    suspend fun deleteKnowledge(userId: String, userEmail: String, knowledgeId: String): Boolean = withContext(Dispatchers.IO) {
        if (!verifySuperAdmin(userId)) return@withContext false
        db.knowledgeDao().deleteKnowledge(knowledgeId)
        db.adminAuditLogDao().insertLog(
            AdminAuditLogEntity(
                action = "Knowledge entry deleted",
                details = "Deleted knowledge ID: $knowledgeId",
                userEmail = userEmail
            )
        )
        true
    }

    suspend fun getApiKeyStatus(userId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!verifySuperAdmin(userId)) return@withContext Pair(false, "Unauthorized")
        val config = db.adminConfigDao().getConfig("openrouter_api_key")
        if (config == null || config.configValue.isBlank()) {
            Pair(false, "Not Configured")
        } else {
            Pair(true, config.configValue)
        }
    }

    suspend fun saveApiKey(userId: String, userEmail: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (!verifySuperAdmin(userId)) return@withContext false
        val cleanKey = apiKey.trim()
        val masked = if (cleanKey.length > 8) {
            "••••••••${cleanKey.takeLast(4)}"
        } else {
            "••••••••"
        }
        val entity = AdminConfigEntity(
            configKey = "openrouter_api_key",
            configValue = masked,
            updatedAt = System.currentTimeMillis(),
            updatedBy = userEmail
        )
        db.adminConfigDao().saveConfig(entity)
        db.adminAuditLogDao().insertLog(
            AdminAuditLogEntity(
                action = "OpenRouter key rotated",
                details = "Rotated OpenRouter API key ($masked)",
                userEmail = userEmail
            )
        )
        true
    }

    suspend fun revokeApiKey(userId: String, userEmail: String): Boolean = withContext(Dispatchers.IO) {
        if (!verifySuperAdmin(userId)) return@withContext false
        db.adminConfigDao().deleteConfig("openrouter_api_key")
        db.adminAuditLogDao().insertLog(
            AdminAuditLogEntity(
                action = "OpenRouter key revoked",
                details = "Revoked OpenRouter API key",
                userEmail = userEmail
            )
        )
        true
    }

    suspend fun getAuditLogs(userId: String): List<AdminAuditLogEntity> = withContext(Dispatchers.IO) {
        if (!verifySuperAdmin(userId)) return@withContext emptyList()
        db.adminAuditLogDao().getAllLogs()
    }
}
