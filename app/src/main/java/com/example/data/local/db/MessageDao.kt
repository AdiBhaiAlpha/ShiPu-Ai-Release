package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND userId = :userId ORDER BY createdAt ASC")
    suspend fun getMessagesForConversation(conversationId: String, userId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun deleteMessagesForConversation(conversationId: String, userId: String)

    @Query("DELETE FROM messages WHERE userId = :userId")
    suspend fun deleteUserMessages(userId: String)
}
