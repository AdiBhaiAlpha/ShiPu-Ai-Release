package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemoryEntity)

    @Query("SELECT * FROM user_memories WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getMemoriesForUser(userId: String): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories WHERE userId = :userId AND fact = :fact LIMIT 1")
    suspend fun getMemoryByFact(userId: String, fact: String): UserMemoryEntity?

    @Query("UPDATE user_memories SET fact = :fact, category = :category, updatedAt = :updatedAt WHERE memoryId = :memoryId AND userId = :userId")
    suspend fun updateMemory(memoryId: String, userId: String, fact: String, category: String, updatedAt: Long)

    @Query("DELETE FROM user_memories WHERE memoryId = :memoryId AND userId = :userId")
    suspend fun deleteMemory(memoryId: String, userId: String)

    @Query("DELETE FROM user_memories WHERE userId = :userId")
    suspend fun clearUserMemories(userId: String)
}
