package com.example

import com.example.data.model.ChatSearchResult
import com.example.data.model.GenerationErrorType
import com.example.data.model.GenerationState
import com.example.data.model.GenerationStatus
import com.example.data.model.Message
import com.example.data.model.SearchMatchType
import com.example.data.model.StreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingReliabilityTest {

    @Test
    fun testGenerationStateTransitions() {
        val stateIdle = GenerationState(status = GenerationStatus.IDLE)
        assertFalse(stateIdle.status == GenerationStatus.STREAMING)

        val stateStreaming = stateIdle.copy(
            generationId = "gen_123",
            status = GenerationStatus.STREAMING,
            accumulatedText = "আমি ShiPu AI"
        )
        assertEquals("আমি ShiPu AI", stateStreaming.accumulatedText)
        assertEquals(GenerationStatus.STREAMING, stateStreaming.status)

        val stateCompleted = stateStreaming.copy(status = GenerationStatus.COMPLETED)
        assertEquals(GenerationStatus.COMPLETED, stateCompleted.status)
    }

    @Test
    fun testErrorClassification() {
        val networkError = StreamEvent.Error(
            errorType = GenerationErrorType.NETWORK_ERROR,
            message = "Connection reset by peer",
            isRetryable = true
        )
        assertTrue(networkError.isRetryable)
        assertEquals(GenerationErrorType.NETWORK_ERROR, networkError.errorType)

        val authError = StreamEvent.Error(
            errorType = GenerationErrorType.AUTHENTICATION_ERROR,
            message = "Invalid API key",
            isRetryable = false
        )
        assertFalse(authError.isRetryable)
    }

    @Test
    fun testChatSearchResultModel() {
        val result = ChatSearchResult(
            conversationId = "conv_1",
            conversationTitle = "Kotlin Architecture",
            matchedMessageId = "msg_42",
            matchedRole = "assistant",
            snippetText = "...best practices for Kotlin StateFlow in Jetpack Compose...",
            matchType = SearchMatchType.MESSAGE_ASSISTANT,
            timestamp = 1000L
        )

        assertEquals("conv_1", result.conversationId)
        assertEquals("Kotlin Architecture", result.conversationTitle)
        assertEquals(SearchMatchType.MESSAGE_ASSISTANT, result.matchType)
        assertTrue(result.snippetText.contains("StateFlow"))
    }
}
