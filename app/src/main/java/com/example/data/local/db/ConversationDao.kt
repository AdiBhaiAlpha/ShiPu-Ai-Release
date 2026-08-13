package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conv: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE userId = :userId AND isArchived = 0 ORDER BY updatedAt DESC")
    suspend fun getConversationsForUser(userId: String): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE conversationId = :conversationId AND userId = :userId LIMIT 1")
    suspend fun getConversationById(conversationId: String, userId: String): ConversationEntity?

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun updateTitle(conversationId: String, userId: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun togglePin(conversationId: String, userId: String, isPinned: Boolean)

    @Query("DELETE FROM conversations WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun deleteConversation(conversationId: String, userId: String)

    @Query("DELETE FROM conversations WHERE userId = :userId")
    suspend fun deleteUserConversations(userId: String)
}
