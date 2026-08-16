package com.example

import com.example.data.local.db.ConversationEntity
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.UserEntity
import com.example.data.local.db.UserMemoryEntity
import com.example.data.local.db.UserPreferencesEntity
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.model.UserMemory
import com.example.data.model.UserPreferences
import com.example.util.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiPuAiUnitTest {

    @Test
    fun testPasswordHasherVerification() {
        val rawPassword = "securePassword123!"
        val email = "testuser@example.com"
        val hash = PasswordHasher.hashPassword(rawPassword, email)

        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())

        val isValid = PasswordHasher.verifyPassword(rawPassword, hash, email)
        assertTrue(isValid)
    }

    @Test
    fun testUserEntitySerialization() {
        val user = User(
            userId = "usr_123456789",
            email = "test@example.com",
            passwordHash = "hashedpass",
            name = "Test User"
        )

        val entity = UserEntity.fromUser(user)
        assertEquals("usr_123456789", entity.userId)
        assertEquals("test@example.com", entity.email)
        assertEquals("Test User", entity.name)

        val restored = entity.toUser()
        assertEquals(user.userId, restored.userId)
        assertEquals(user.email, restored.email)
        assertEquals(user.name, restored.name)
    }

    @Test
    fun testConversationEntitySerialization() {
        val conv = Conversation(
            conversationId = "conv_abc123",
            userId = "usr_123456789",
            title = "My Project Discussion",
            isPinned = true
        )

        val entity = ConversationEntity.fromConversation(conv)
        assertEquals("conv_abc123", entity.conversationId)
        assertEquals("usr_123456789", entity.userId)
        assertEquals("My Project Discussion", entity.title)
        assertEquals(true, entity.isPinned)

        val restored = entity.toConversation()
        assertEquals(conv.conversationId, restored.conversationId)
        assertEquals(conv.title, restored.title)
        assertEquals(conv.isPinned, restored.isPinned)
    }

    @Test
    fun testMessageEntitySerialization() {
        val msg = Message(
            messageId = "msg_001",
            conversationId = "conv_abc123",
            userId = "usr_123456789",
            role = "user",
            content = "Hello ShiPu AI!"
        )

        val entity = MessageEntity.fromMessage(msg)
        assertEquals("msg_001", entity.messageId)
        assertEquals("user", entity.role)
        assertEquals("Hello ShiPu AI!", entity.content)

        val restored = entity.toMessage()
        assertEquals(msg.messageId, restored.messageId)
        assertEquals(msg.role, restored.role)
        assertEquals(msg.content, restored.content)
    }

    @Test
    fun testUserMemoryEntitySerialization() {
        val mem = UserMemory(
            memoryId = "mem_001",
            userId = "usr_123456789",
            fact = "User works as an Android Developer",
            category = "personal"
        )

        val entity = UserMemoryEntity.fromUserMemory(mem)
        assertEquals("mem_001", entity.memoryId)
        assertEquals("User works as an Android Developer", entity.fact)
        assertEquals("personal", entity.category)

        val restored = entity.toUserMemory()
        assertEquals(mem.memoryId, restored.memoryId)
        assertEquals(mem.fact, restored.fact)
        assertEquals(mem.category, restored.category)
    }

    @Test
    fun testUserPreferencesEntitySerialization() {
        val prefs = UserPreferences(
            userId = "usr_123456789",
            theme = "dark",
            defaultModel = "openrouter/free",
            customSystemPrompt = "Custom AI Persona"
        )

        val entity = UserPreferencesEntity.fromUserPreferences(prefs)
        assertEquals("usr_123456789", entity.userId)
        assertEquals("dark", entity.theme)
        assertEquals("openrouter/free", entity.defaultModel)
        assertEquals("Custom AI Persona", entity.customSystemPrompt)

        val restored = entity.toUserPreferences()
        assertEquals(prefs.userId, restored.userId)
        assertEquals(prefs.theme, restored.theme)
        assertEquals(prefs.customSystemPrompt, restored.customSystemPrompt)
    }
}

