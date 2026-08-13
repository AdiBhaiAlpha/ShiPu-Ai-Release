package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Conversation

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val conversationId: String,
    val userId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
) {
    fun toConversation(): Conversation = Conversation(
        conversationId = conversationId,
        userId = userId,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
        isArchived = isArchived
    )

    companion object {
        fun fromConversation(conv: Conversation): ConversationEntity = ConversationEntity(
            conversationId = conv.conversationId,
            userId = conv.userId,
            title = conv.title,
            createdAt = conv.createdAt,
            updatedAt = conv.updatedAt,
            isPinned = conv.isPinned,
            isArchived = conv.isArchived
        )
    }
}
