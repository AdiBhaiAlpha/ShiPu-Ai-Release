package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.UserMemoryEntity
import com.example.data.model.UserMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class MemoryRepository(
    context: Context? = null
) {
    private val db: AppDatabase by lazy { AppDatabase.getInstance(context) }

    suspend fun getUserMemories(userId: String): List<UserMemory> = withContext(Dispatchers.IO) {
        try {
            db.userMemoryDao().getMemoriesForUser(userId).map { it.toUserMemory() }
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Error fetching memories for user $userId", e)
            emptyList()
        }
    }

    suspend fun addMemory(
        userId: String,
        fact: String,
        category: String = "general",
        sourceMessageId: String? = null
    ): UserMemory? = withContext(Dispatchers.IO) {
        try {
            val cleanFact = fact.trim()
            if (cleanFact.isBlank()) return@withContext null

            // Avoid duplicate memory facts for same user
            val existing = db.userMemoryDao().getMemoryByFact(userId, cleanFact)
            if (existing != null) {
                return@withContext existing.toUserMemory()
            }

            val memory = UserMemory(
                memoryId = "mem_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                userId = userId,
                fact = cleanFact,
                category = category,
                sourceMessageId = sourceMessageId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            db.userMemoryDao().insertMemory(UserMemoryEntity.fromUserMemory(memory))
            Log.d("MemoryRepository", "Saved memory for $userId: $cleanFact")
            memory
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Error adding memory for $userId", e)
            null
        }
    }

    suspend fun updateMemory(
        memoryId: String,
        userId: String,
        newFact: String,
        newCategory: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            db.userMemoryDao().updateMemory(
                memoryId = memoryId,
                userId = userId,
                fact = newFact.trim(),
                category = newCategory,
                updatedAt = System.currentTimeMillis()
            )
            true
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Error updating memory $memoryId", e)
            false
        }
    }

    suspend fun deleteMemory(memoryId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.userMemoryDao().deleteMemory(memoryId, userId)
            true
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Error deleting memory $memoryId", e)
            false
        }
    }

    suspend fun clearAllMemories(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.userMemoryDao().clearUserMemories(userId)
            Log.d("MemoryRepository", "Cleared memories for user $userId")
            true
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Error clearing memories for $userId", e)
            false
        }
    }

    suspend fun extractAndSaveMemoriesFromMessage(
        userId: String,
        userMessage: String,
        sourceMessageId: String? = null
    ): List<UserMemory> = withContext(Dispatchers.IO) {
        val extracted = mutableListOf<UserMemory>()
        val text = userMessage.trim()

        val patterns = listOf(
            Regex("(?i)(?:my name is|i am|call me)\\s+([A-Z][a-zA-Z\\s]{1,20})", RegexOption.IGNORE_CASE) to "personal",
            Regex("(?i)(?:i live in|i am from|i reside in)\\s+([a-zA-Z\\s,]{2,30})", RegexOption.IGNORE_CASE) to "personal",
            Regex("(?i)(?:i work as|i am a|my job is|my profession is)\\s+([a-zA-Z\\s]{2,30})", RegexOption.IGNORE_CASE) to "personal",
            Regex("(?i)(?:i love|i prefer|my favorite|i really like)\\s+([a-zA-Z0-9\\s]{2,40})", RegexOption.IGNORE_CASE) to "preference",
            Regex("(?i)(?:i am learning|i am studying|i am building|i am working on)\\s+([a-zA-Z0-9\\s]{2,40})", RegexOption.IGNORE_CASE) to "project",
            Regex("(?i)(?:remember that|note that|keep in mind that)\\s+(.{5,80})", RegexOption.IGNORE_CASE) to "general"
        )

        for ((regex, cat) in patterns) {
            val match = regex.find(text)
            if (match != null) {
                val captured = match.groupValues.lastOrNull()?.trim() ?: ""
                if (captured.isNotBlank() && captured.length > 2) {
                    val fullFact = when (cat) {
                        "personal" -> if (text.lowercase().contains("name")) "User's name is $captured" else if (text.lowercase().contains("live") || text.lowercase().contains("from")) "User is located in $captured" else "User works as $captured"
                        "preference" -> "User prefers/likes: $captured"
                        "project" -> "User is currently working on/learning: $captured"
                        else -> captured
                    }

                    val saved = addMemory(userId, fullFact, cat, sourceMessageId)
                    saved?.let { extracted.add(it) }
                }
            }
        }
        extracted
    }
}
