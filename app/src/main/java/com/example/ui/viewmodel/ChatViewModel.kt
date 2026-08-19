package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatSearchResult
import com.example.data.model.Conversation
import com.example.data.model.GenerationErrorType
import com.example.data.model.GenerationState
import com.example.data.model.GenerationStatus
import com.example.data.model.Message
import com.example.data.model.StreamEvent
import com.example.data.model.UserMemory
import com.example.data.model.UserPreferences
import com.example.data.repository.ChatRepository
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val streamingChunk: String = "",
    val generationState: GenerationState = GenerationState(),
    val isStreaming: Boolean = false,
    val isLoadingConversations: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ChatSearchResult> = emptyList(),
    val highlightedMessageId: String? = null,
    val searchHighlightKeyword: String? = null,
    val userMemories: List<UserMemory> = emptyList(),
    val userPreferences: UserPreferences? = null,
    val isDarkTheme: Boolean = true,
    val errorMessage: String? = null,
    val lastFailedPrompt: String? = null,
    val lastFailedUserMsgId: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val chatRepository by lazy { ChatRepository(context = application) }
    private val memoryRepository by lazy { MemoryRepository(context = application) }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null
    private var currentUserId: String? = null

    fun initUser(userId: String) {
        if (currentUserId == userId) return
        currentUserId = userId
        _uiState.update {
            ChatUiState(
                isDarkTheme = true
            )
        }
        loadUserData(userId)
    }

    fun loadUserData(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingConversations = true) }
            val convs = chatRepository.getConversations(userId)
            val prefs = chatRepository.getUserPreferences(userId)
            val memories = memoryRepository.getUserMemories(userId)

            _uiState.update {
                it.copy(
                    conversations = convs,
                    userPreferences = prefs,
                    userMemories = memories,
                    isDarkTheme = prefs.theme != "light",
                    isLoadingConversations = false
                )
            }

            // Automatically select first conversation if available
            if (convs.isNotEmpty() && _uiState.value.activeConversationId == null) {
                selectConversation(convs.first().conversationId)
            }

            // Trigger non-blocking cloud sync with MongoDB Atlas
            com.example.data.sync.CloudSyncEngine.getInstance().triggerBackgroundSync(userId)
        }
    }

    fun selectConversation(conversationId: String) {
        val userId = currentUserId ?: return
        if (_uiState.value.isStreaming) {
            stopGeneration()
        }

        _uiState.update {
            it.copy(
                activeConversationId = conversationId,
                isLoadingMessages = true,
                streamingChunk = "",
                isStreaming = false,
                generationState = GenerationState(status = GenerationStatus.IDLE),
                errorMessage = null,
                lastFailedPrompt = null,
                lastFailedUserMsgId = null
            )
        }

        viewModelScope.launch {
            val msgs = chatRepository.getMessages(conversationId, userId)
            _uiState.update {
                it.copy(
                    messages = msgs,
                    isLoadingMessages = false
                )
            }
        }
    }

    fun createNewChat() {
        val userId = currentUserId ?: return
        if (_uiState.value.isStreaming) {
            stopGeneration()
        }

        viewModelScope.launch {
            val newConv = chatRepository.createConversation(userId)
            val updatedConvs = chatRepository.getConversations(userId)
            _uiState.update {
                it.copy(
                    conversations = updatedConvs,
                    activeConversationId = newConv.conversationId,
                    messages = emptyList(),
                    streamingChunk = "",
                    isStreaming = false,
                    generationState = GenerationState(status = GenerationStatus.IDLE),
                    errorMessage = null,
                    lastFailedPrompt = null,
                    lastFailedUserMsgId = null
                )
            }
        }
    }

    fun sendMessage(text: String) {
        val userId = currentUserId ?: return
        val cleanText = text.trim()
        if (cleanText.isEmpty() || _uiState.value.isStreaming) return

        viewModelScope.launch {
            var convId = _uiState.value.activeConversationId
            if (convId == null) {
                val newConv = chatRepository.createConversation(userId, cleanText.take(25))
                convId = newConv.conversationId
                _uiState.update { it.copy(activeConversationId = convId) }
            }

            val currentMsgs = _uiState.value.messages.toList()
            val userMsgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val tempUserMsg = Message(
                messageId = userMsgId,
                conversationId = convId,
                userId = userId,
                role = "user",
                content = cleanText,
                createdAt = System.currentTimeMillis()
            )

            val generationId = "gen_${UUID.randomUUID().toString().replace("-", "").take(12)}"

            _uiState.update {
                it.copy(
                    messages = currentMsgs + tempUserMsg,
                    isStreaming = true,
                    streamingChunk = "",
                    generationState = GenerationState(
                        generationId = generationId,
                        status = GenerationStatus.CONNECTING,
                        conversationId = convId
                    ),
                    errorMessage = null,
                    lastFailedPrompt = null,
                    lastFailedUserMsgId = null
                )
            }

            startStreamingPipeline(
                conversationId = convId,
                userId = userId,
                userMessageText = cleanText,
                userMsgId = userMsgId,
                existingMessages = currentMsgs,
                generationId = generationId
            )
        }
    }

    private fun startStreamingPipeline(
        conversationId: String,
        userId: String,
        userMessageText: String,
        userMsgId: String,
        existingMessages: List<Message>,
        generationId: String
    ) {
        streamingJob?.cancel()
        val fullAssistantResponse = StringBuilder()

        streamingJob = viewModelScope.launch {
            chatRepository.streamAssistantResponseEvents(
                conversationId = conversationId,
                userId = userId,
                userMessageText = userMessageText,
                existingMessages = existingMessages,
                existingUserMsgId = userMsgId,
                generationId = generationId
            ).catch { e ->
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        generationState = GenerationState(
                            generationId = generationId,
                            status = GenerationStatus.NETWORK_ERROR,
                            conversationId = conversationId,
                            errorMessage = "Unable to connect to AI server. Please check your network.",
                            errorType = GenerationErrorType.NETWORK_UNAVAILABLE,
                            isRetryable = true
                        ),
                        lastFailedPrompt = userMessageText,
                        lastFailedUserMsgId = userMsgId
                    )
                }
            }.collect { event ->
                when (event) {
                    is StreamEvent.Connecting -> {
                        _uiState.update {
                            it.copy(
                                isStreaming = true,
                                generationState = it.generationState.copy(status = GenerationStatus.CONNECTING)
                            )
                        }
                    }
                    is StreamEvent.FirstTokenReceived -> {
                        _uiState.update {
                            it.copy(
                                isStreaming = true,
                                generationState = it.generationState.copy(status = GenerationStatus.STREAMING)
                            )
                        }
                    }
                    is StreamEvent.ChunkReceived -> {
                        fullAssistantResponse.append(event.chunk)
                        _uiState.update {
                            it.copy(
                                isStreaming = true,
                                streamingChunk = fullAssistantResponse.toString(),
                                generationState = it.generationState.copy(
                                    status = GenerationStatus.STREAMING,
                                    partialText = fullAssistantResponse.toString()
                                )
                            )
                        }
                    }
                    is StreamEvent.StreamCompleted -> {
                        // Reload fresh messages from DB
                        val freshMsgs = chatRepository.getMessages(conversationId, userId)
                        val freshConvs = chatRepository.getConversations(userId)
                        val freshMemories = memoryRepository.getUserMemories(userId)

                        _uiState.update {
                            it.copy(
                                messages = freshMsgs,
                                conversations = freshConvs,
                                userMemories = freshMemories,
                                streamingChunk = "",
                                isStreaming = false,
                                generationState = GenerationState(
                                    generationId = generationId,
                                    status = GenerationStatus.COMPLETED,
                                    conversationId = conversationId
                                ),
                                lastFailedPrompt = null,
                                lastFailedUserMsgId = null
                            )
                        }
                    }
                    is StreamEvent.StreamFailed -> {
                        val status = when (event.errorType) {
                            GenerationErrorType.NETWORK_UNAVAILABLE,
                            GenerationErrorType.CONNECTION_INTERRUPTED -> GenerationStatus.NETWORK_ERROR
                            GenerationErrorType.TIMEOUT -> GenerationStatus.TIMEOUT
                            GenerationErrorType.AUTHENTICATION_FAILED,
                            GenerationErrorType.RATE_LIMITED,
                            GenerationErrorType.CLIENT_ERROR,
                            GenerationErrorType.SERVER_ERROR -> GenerationStatus.API_ERROR
                            GenerationErrorType.PARSING_ERROR -> GenerationStatus.PARSING_ERROR
                            GenerationErrorType.UNKNOWN -> GenerationStatus.UNKNOWN_ERROR
                        }

                        // If some text was streamed, persist it as a partial response so user doesn't lose it
                        if (fullAssistantResponse.isNotBlank()) {
                            val partialMsg = Message(
                                messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                                conversationId = conversationId,
                                userId = userId,
                                role = "assistant",
                                content = fullAssistantResponse.toString(),
                                createdAt = System.currentTimeMillis()
                            )
                            chatRepository.saveMessage(partialMsg)
                        }

                        val freshMsgs = chatRepository.getMessages(conversationId, userId)

                        _uiState.update {
                            it.copy(
                                messages = freshMsgs,
                                streamingChunk = "",
                                isStreaming = false,
                                generationState = GenerationState(
                                    generationId = generationId,
                                    status = status,
                                    conversationId = conversationId,
                                    errorMessage = event.errorMessage,
                                    errorType = event.errorType,
                                    isRetryable = event.isRetryable
                                ),
                                errorMessage = event.errorMessage,
                                lastFailedPrompt = userMessageText,
                                lastFailedUserMsgId = userMsgId
                            )
                        }
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        streamingJob?.cancel()
        streamingJob = null

        val userId = currentUserId ?: return
        val convId = _uiState.value.activeConversationId ?: return
        val accumulatedChunk = _uiState.value.streamingChunk.trim()

        viewModelScope.launch {
            if (accumulatedChunk.isNotBlank()) {
                // Save the generated partial text cleanly WITHOUT appending "[Stopped]" or fake text tags
                val partialMsg = Message(
                    messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                    conversationId = convId,
                    userId = userId,
                    role = "assistant",
                    content = accumulatedChunk,
                    createdAt = System.currentTimeMillis()
                )
                chatRepository.saveMessage(partialMsg)
            }

            val freshMsgs = chatRepository.getMessages(convId, userId)
            _uiState.update {
                it.copy(
                    messages = freshMsgs,
                    streamingChunk = "",
                    isStreaming = false,
                    generationState = GenerationState(
                        status = GenerationStatus.USER_CANCELLED,
                        conversationId = convId
                    )
                )
            }
        }
    }

    fun retryLastFailed() {
        val prompt = _uiState.value.lastFailedPrompt ?: return
        val failedUserMsgId = _uiState.value.lastFailedUserMsgId
        val userId = currentUserId ?: return
        val convId = _uiState.value.activeConversationId ?: return

        if (_uiState.value.isStreaming) return

        viewModelScope.launch {
            val currentMsgs = _uiState.value.messages.filter { it.messageId != failedUserMsgId }
            val existingUserMsg = _uiState.value.messages.firstOrNull { it.messageId == failedUserMsgId }
            val userMsg = existingUserMsg ?: Message(
                messageId = failedUserMsgId ?: "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                conversationId = convId,
                userId = userId,
                role = "user",
                content = prompt,
                createdAt = System.currentTimeMillis()
            )

            val generationId = "gen_${UUID.randomUUID().toString().replace("-", "").take(12)}"

            _uiState.update {
                it.copy(
                    messages = currentMsgs + userMsg,
                    isStreaming = true,
                    streamingChunk = "",
                    generationState = GenerationState(
                        generationId = generationId,
                        status = GenerationStatus.CONNECTING,
                        conversationId = convId
                    ),
                    errorMessage = null
                )
            }

            startStreamingPipeline(
                conversationId = convId,
                userId = userId,
                userMessageText = prompt,
                userMsgId = userMsg.messageId,
                existingMessages = currentMsgs,
                generationId = generationId
            )
        }
    }

    fun regenerateLastResponse() {
        val userId = currentUserId ?: return
        val convId = _uiState.value.activeConversationId ?: return
        val msgs = _uiState.value.messages

        if (msgs.isEmpty() || _uiState.value.isStreaming) return

        val lastUserMsg = msgs.lastOrNull { it.role == "user" } ?: return

        // Filter out the last assistant response if it exists
        val lastMsg = msgs.lastOrNull()
        val trimmedMsgs = if (lastMsg?.role == "assistant") {
            msgs.dropLast(1)
        } else {
            msgs
        }

        val generationId = "gen_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        _uiState.update {
            it.copy(
                messages = trimmedMsgs,
                isStreaming = true,
                streamingChunk = "",
                generationState = GenerationState(
                    generationId = generationId,
                    status = GenerationStatus.CONNECTING,
                    conversationId = convId
                ),
                errorMessage = null
            )
        }

        startStreamingPipeline(
            conversationId = convId,
            userId = userId,
            userMessageText = lastUserMsg.content,
            userMsgId = lastUserMsg.messageId,
            existingMessages = trimmedMsgs.filter { it.messageId != lastUserMsg.messageId },
            generationId = generationId
        )
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            chatRepository.renameConversation(conversationId, userId, newTitle)
            val updatedConvs = chatRepository.getConversations(userId)
            _uiState.update { it.copy(conversations = updatedConvs) }
        }
    }

    fun togglePinConversation(conversationId: String, currentPinned: Boolean) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            chatRepository.togglePinConversation(conversationId, userId, currentPinned)
            val updatedConvs = chatRepository.getConversations(userId)
            _uiState.update { it.copy(conversations = updatedConvs) }
        }
    }

    fun deleteConversation(conversationId: String) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId, userId)
            val updatedConvs = chatRepository.getConversations(userId)
            val newActiveId = if (_uiState.value.activeConversationId == conversationId) {
                updatedConvs.firstOrNull()?.conversationId
            } else {
                _uiState.value.activeConversationId
            }

            _uiState.update {
                it.copy(
                    conversations = updatedConvs,
                    activeConversationId = newActiveId
                )
            }

            if (newActiveId != null) {
                selectConversation(newActiveId)
            } else {
                _uiState.update { it.copy(messages = emptyList()) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val userId = currentUserId ?: return
        viewModelScope.launch {
            if (query.isBlank()) {
                val allConvs = chatRepository.getConversations(userId)
                _uiState.update { it.copy(conversations = allConvs, searchResults = emptyList()) }
            } else {
                val results = chatRepository.searchConversationalContent(userId, query)
                val convResults = chatRepository.searchConversations(userId, query)
                _uiState.update { it.copy(conversations = convResults, searchResults = results) }
            }
        }
    }

    fun selectSearchResult(result: ChatSearchResult) {
        val query = _uiState.value.searchQuery
        if (result.conversationId.isNotBlank()) {
            selectConversation(result.conversationId)
            _uiState.update {
                it.copy(
                    highlightedMessageId = result.matchedMessageId,
                    searchHighlightKeyword = query.ifBlank { null }
                )
            }
        }
    }

    fun clearHighlight() {
        _uiState.update {
            it.copy(
                highlightedMessageId = null,
                searchHighlightKeyword = null
            )
        }
    }

    fun addMemory(fact: String, category: String) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            memoryRepository.addMemory(userId, fact, category)
            val updatedMemories = memoryRepository.getUserMemories(userId)
            _uiState.update { it.copy(userMemories = updatedMemories) }
        }
    }

    fun updateMemory(memoryId: String, fact: String, category: String) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            memoryRepository.updateMemory(memoryId, userId, fact, category)
            val updatedMemories = memoryRepository.getUserMemories(userId)
            _uiState.update { it.copy(userMemories = updatedMemories) }
        }
    }

    fun deleteMemory(memoryId: String) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            memoryRepository.deleteMemory(memoryId, userId)
            val updatedMemories = memoryRepository.getUserMemories(userId)
            _uiState.update { it.copy(userMemories = updatedMemories) }
        }
    }

    fun clearAllMemories() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            memoryRepository.clearAllMemories(userId)
            _uiState.update { it.copy(userMemories = emptyList()) }
        }
    }

    fun saveUserPreferences(prefs: UserPreferences) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            val cleanPrefs = prefs.copy(userId = userId)
            chatRepository.updateUserPreferences(cleanPrefs)
            _uiState.update {
                it.copy(
                    userPreferences = cleanPrefs,
                    isDarkTheme = cleanPrefs.theme != "light"
                )
            }
        }
    }

    fun toggleTheme() {
        val current = _uiState.value.isDarkTheme
        val newMode = if (current) "light" else "dark"
        val existingPrefs = _uiState.value.userPreferences ?: UserPreferences(userId = currentUserId ?: "")
        val updatedPrefs = existingPrefs.copy(theme = newMode)
        saveUserPreferences(updatedPrefs)
    }

    fun exportUserData(): String {
        val userId = currentUserId ?: "unknown"
        val convs = _uiState.value.conversations
        val memories = _uiState.value.userMemories
        val prefs = _uiState.value.userPreferences

        val builder = StringBuilder()
        builder.append("{\n")
        builder.append("  \"userId\": \"$userId\",\n")
        builder.append("  \"exportDate\": \"${System.currentTimeMillis()}\",\n")
        builder.append("  \"conversationsCount\": ${convs.size},\n")
        builder.append("  \"memoriesCount\": ${memories.size},\n")
        builder.append("  \"customInstructions\": \"${prefs?.customSystemPrompt?.replace("\"", "\\\"") ?: ""}\"\n")
        builder.append("}")
        return builder.toString()
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                lastFailedPrompt = null,
                lastFailedUserMsgId = null
            )
        }
    }
}
