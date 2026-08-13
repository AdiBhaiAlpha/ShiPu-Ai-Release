package com.example

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
    fun testUserDocumentSerialization() {
        val user = User(
            userId = "usr_123456789",
            email = "test@example.com",
            passwordHash = "hashedpass",
            name = "Test User"
        )

        val doc = user.toDocument()
        assertEquals("usr_123456789", doc.getString("userId"))
        assertEquals("test@example.com", doc.getString("email"))
        assertEquals("Test User", doc.getString("name"))

        val restored = User.fromDocument(doc)
        assertEquals(user.userId, restored.userId)
        assertEquals(user.email, restored.email)
        assertEquals(user.name, restored.name)
    }

    @Test
    fun testConversationDocumentSerialization() {
        val conv = Conversation(
            conversationId = "conv_abc123",
            userId = "usr_123456789",
            title = "My Project Discussion",
            isPinned = true
        )

        val doc = conv.toDocument()
        assertEquals("conv_abc123", doc.getString("conversationId"))
        assertEquals("usr_123456789", doc.getString("userId"))
        assertEquals("My Project Discussion", doc.getString("title"))
        assertEquals(true, doc.getBoolean("isPinned"))

        val restored = Conversation.fromDocument(doc)
        assertEquals(conv.conversationId, restored.conversationId)
        assertEquals(conv.title, restored.title)
        assertEquals(conv.isPinned, restored.isPinned)
    }

    @Test
    fun testMessageDocumentSerialization() {
        val msg = Message(
            messageId = "msg_001",
            conversationId = "conv_abc123",
            userId = "usr_123456789",
            role = "user",
            content = "Hello ShiPu AI!"
        )

        val doc = msg.toDocument()
        assertEquals("msg_001", doc.getString("messageId"))
        assertEquals("user", doc.getString("role"))
        assertEquals("Hello ShiPu AI!", doc.getString("content"))

        val restored = Message.fromDocument(doc)
        assertEquals(msg.messageId, restored.messageId)
        assertEquals(msg.role, restored.role)
        assertEquals(msg.content, restored.content)
    }

    @Test
    fun testUserMemorySerialization() {
        val mem = UserMemory(
            memoryId = "mem_001",
            userId = "usr_123456789",
            fact = "User works as an Android Developer",
            category = "personal"
        )

        val doc = mem.toDocument()
        assertEquals("mem_001", doc.getString("memoryId"))
        assertEquals("User works as an Android Developer", doc.getString("fact"))
        assertEquals("personal", doc.getString("category"))

        val restored = UserMemory.fromDocument(doc)
        assertEquals(mem.memoryId, restored.memoryId)
        assertEquals(mem.fact, restored.fact)
        assertEquals(mem.category, restored.category)
    }

    @Test
    fun testUserPreferencesSerialization() {
        val prefs = UserPreferences(
            userId = "usr_123456789",
            theme = "dark",
            defaultModel = "openrouter/free",
            customSystemPrompt = "Custom AI Persona"
        )

        val doc = prefs.toDocument()
        assertEquals("usr_123456789", doc.getString("userId"))
        assertEquals("dark", doc.getString("theme"))
        assertEquals("openrouter/free", doc.getString("defaultModel"))
        assertEquals("Custom AI Persona", doc.getString("customSystemPrompt"))

        val restored = UserPreferences.fromDocument(doc)
        assertEquals(prefs.userId, restored.userId)
        assertEquals(prefs.theme, restored.theme)
        assertEquals(prefs.customSystemPrompt, restored.customSystemPrompt)
    }
}
