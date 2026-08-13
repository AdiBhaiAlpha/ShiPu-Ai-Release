package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.model.Message
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

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val streamingChunk: String = "",
    val isStreaming: Boolean = false,
    val isLoadingConversations: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val searchQuery: String = "",
    val userMemories: List<UserMemory> = emptyList(),
    val userPreferences: UserPreferences? = null,
    val isDarkTheme: Boolean = true,
    val errorMessage: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val chatRepository = ChatRepository()
    private val memoryRepository = MemoryRepository()

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
                errorMessage = null
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
                    isStreaming = false
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

            // Immediate optimistic user message update
            val tempUserMsg = Message(
                messageId = "temp_user_${System.currentTimeMillis()}",
                conversationId = convId,
                userId = userId,
                role = "user",
                content = cleanText,
                createdAt = System.currentTimeMillis()
            )

            _uiState.update {
                it.copy(
                    messages = currentMsgs + tempUserMsg,
                    isStreaming = true,
                    streamingChunk = "",
                    errorMessage = null
                )
            }

            // Start streaming from OpenRouter
            val fullAssistantResponse = StringBuilder()

            streamingJob = launch {
                chatRepository.streamAssistantResponse(
                    conversationId = convId,
                    userId = userId,
                    userMessageText = cleanText,
                    existingMessages = currentMsgs
                ).catch { e ->
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            errorMessage = "Unable to reach ShiPu AI servers. Please check your network connection."
                        )
                    }
                }.collect { chunk ->
                    fullAssistantResponse.append(chunk)
                    _uiState.update {
                        it.copy(streamingChunk = fullAssistantResponse.toString())
                    }
                }

                // Streaming finished: Reload complete persisted messages and conversations
                val freshMsgs = chatRepository.getMessages(convId, userId)
                val freshConvs = chatRepository.getConversations(userId)
                val freshMemories = memoryRepository.getUserMemories(userId)

                _uiState.update {
                    it.copy(
                        messages = freshMsgs,
                        conversations = freshConvs,
                        userMemories = freshMemories,
                        streamingChunk = "",
                        isStreaming = false
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        streamingJob?.cancel()
        streamingJob = null

        val userId = currentUserId ?: return
        val convId = _uiState.value.activeConversationId ?: return
        val accumulatedChunk = _uiState.value.streamingChunk

        viewModelScope.launch {
            if (accumulatedChunk.isNotBlank()) {
                val partialMsg = Message(
                    messageId = "msg_${System.currentTimeMillis()}",
                    conversationId = convId,
                    userId = userId,
                    role = "assistant",
                    content = accumulatedChunk + " [Stopped]",
                    createdAt = System.currentTimeMillis()
                )
                chatRepository.saveMessage(partialMsg)
            }

            val freshMsgs = chatRepository.getMessages(convId, userId)
            _uiState.update {
                it.copy(
                    messages = freshMsgs,
                    streamingChunk = "",
                    isStreaming = false
                )
            }
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

        _uiState.update {
            it.copy(
                messages = trimmedMsgs,
                isStreaming = true,
                streamingChunk = "",
                errorMessage = null
            )
        }

        val fullAssistantResponse = StringBuilder()
        streamingJob = viewModelScope.launch {
            chatRepository.streamAssistantResponse(
                conversationId = convId,
                userId = userId,
                userMessageText = lastUserMsg.content,
                existingMessages = trimmedMsgs.filter { it.messageId != lastUserMsg.messageId }
            ).catch { e ->
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        errorMessage = "Failed to regenerate response. Please try again."
                    )
                }
            }.collect { chunk ->
                fullAssistantResponse.append(chunk)
                _uiState.update {
                    it.copy(streamingChunk = fullAssistantResponse.toString())
                }
            }

            val freshMsgs = chatRepository.getMessages(convId, userId)
            _uiState.update {
                it.copy(
                    messages = freshMsgs,
                    streamingChunk = "",
                    isStreaming = false
                )
            }
        }
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
        _uiState.update { it.copy(errorMessage = null) }
    }
}
