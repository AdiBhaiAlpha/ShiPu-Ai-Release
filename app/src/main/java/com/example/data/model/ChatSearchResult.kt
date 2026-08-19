package com.example.data.model

/**
 * Result model for true conversational search across titles, user/assistant messages, and memories.
 */
data class ChatSearchResult(
    val conversationId: String,
    val conversationTitle: String,
    val matchedMessageId: String? = null,
    val matchedRole: String? = null,
    val snippetText: String,
    val matchType: SearchMatchType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SearchMatchType {
    TITLE,
    MESSAGE_USER,
    MESSAGE_ASSISTANT,
    MEMORY
}
