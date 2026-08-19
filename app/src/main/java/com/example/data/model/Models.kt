package com.example.data.model

import java.util.UUID

data class User(
    val userId: String = "usr_${UUID.randomUUID().toString().take(12)}",
    val email: String,
    val passwordHash: String,
    val name: String,
    val role: String = if (email.trim().lowercase() == "chitronbhattacharjee@gmail.com") "SUPER_ADMIN" else "USER",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Conversation(
    val conversationId: String = "conv_${UUID.randomUUID().toString().take(12)}",
    val userId: String,
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

data class Message(
    val messageId: String = "msg_${UUID.randomUUID().toString().take(12)}",
    val conversationId: String,
    val userId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0
)

data class UserMemory(
    val memoryId: String = "mem_${UUID.randomUUID().toString().take(12)}",
    val userId: String,
    val fact: String,
    val category: String = "general", // "preference", "personal", "project", "habit", "general"
    val sourceMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class UserPreferences(
    val userId: String,
    val theme: String = "light", // "light", "dark", "system"
    val defaultModel: String = "openrouter/free",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val customSystemPrompt: String = "",
    val autoMemoryEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

data class Session(
    val sessionId: String = "sess_${UUID.randomUUID().toString().take(12)}",
    val userId: String,
    val token: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 days
)
