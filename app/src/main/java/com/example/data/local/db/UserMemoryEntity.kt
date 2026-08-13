package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.UserMemory

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey val memoryId: String,
    val userId: String,
    val fact: String,
    val category: String,
    val sourceMessageId: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toUserMemory(): UserMemory = UserMemory(
        memoryId = memoryId,
        userId = userId,
        fact = fact,
        category = category,
        sourceMessageId = sourceMessageId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromUserMemory(mem: UserMemory): UserMemoryEntity = UserMemoryEntity(
            memoryId = mem.memoryId,
            userId = mem.userId,
            fact = mem.fact,
            category = mem.category,
            sourceMessageId = mem.sourceMessageId,
            createdAt = mem.createdAt,
            updatedAt = mem.updatedAt
        )
    }
}
