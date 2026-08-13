package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Message

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val userId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val tokenCount: Int = 0
) {
    fun toMessage(): Message = Message(
        messageId = messageId,
        conversationId = conversationId,
        userId = userId,
        role = role,
        content = content,
        createdAt = createdAt,
        tokenCount = tokenCount
    )

    companion object {
        fun fromMessage(msg: Message): MessageEntity = MessageEntity(
            messageId = msg.messageId,
            conversationId = msg.conversationId,
            userId = msg.userId,
            role = msg.role,
            content = msg.content,
            createdAt = msg.createdAt,
            tokenCount = msg.tokenCount
        )
    }
}
