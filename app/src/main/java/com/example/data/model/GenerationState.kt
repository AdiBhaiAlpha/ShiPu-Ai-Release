package com.example.data.model

/**
 * Explicit generation and streaming states for production AI chat.
 */
enum class GenerationStatus {
    IDLE,
    QUEUED,
    CONNECTING,
    STREAMING,
    COMPLETED,
    USER_CANCELLED,
    NETWORK_ERROR,
    API_ERROR,
    PARSING_ERROR,
    TIMEOUT,
    UNKNOWN_ERROR
}

sealed class StreamEvent {
    data class Connecting(val generationId: String) : StreamEvent()
    data class FirstTokenReceived(val generationId: String, val timestamp: Long = System.currentTimeMillis()) : StreamEvent()
    data class ChunkReceived(val generationId: String, val chunk: String, val totalLength: Int) : StreamEvent()
    data class StreamCompleted(val generationId: String, val fullContent: String, val tokenCount: Int) : StreamEvent()
    data class StreamFailed(
        val generationId: String,
        val partialContent: String,
        val errorType: GenerationErrorType,
        val errorMessage: String,
        val isRetryable: Boolean,
        val statusCode: Int = 0
    ) : StreamEvent()
}

enum class GenerationErrorType {
    NETWORK_UNAVAILABLE,
    CONNECTION_INTERRUPTED,
    TIMEOUT,
    RATE_LIMITED,
    AUTHENTICATION_FAILED,
    SERVER_ERROR,
    PARSING_ERROR,
    CLIENT_ERROR,
    UNKNOWN
}

data class GenerationState(
    val generationId: String = "",
    val status: GenerationStatus = GenerationStatus.IDLE,
    val conversationId: String? = null,
    val partialText: String = "",
    val errorMessage: String? = null,
    val errorType: GenerationErrorType? = null,
    val isRetryable: Boolean = false,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 2
)
