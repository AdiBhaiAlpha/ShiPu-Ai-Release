package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.ConversationEntity
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.UserPreferencesEntity
import com.example.data.model.ChatSearchResult
import com.example.data.model.Conversation
import com.example.data.model.GenerationErrorType
import com.example.data.model.Message
import com.example.data.model.SearchMatchType
import com.example.data.model.StreamEvent
import com.example.data.model.UserPreferences
import com.example.data.remote.OpenRouterClient
import com.example.data.sync.CloudSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class ChatRepository(
    private val openRouterClient: OpenRouterClient = OpenRouterClient(),
    context: Context? = null
) {
    private val memoryRepository: MemoryRepository by lazy { MemoryRepository(context) }
    private val db: AppDatabase by lazy { AppDatabase.getInstance(context) }
    private val syncEngine: CloudSyncEngine by lazy { CloudSyncEngine.getInstance(context) }

    suspend fun getConversations(userId: String): List<Conversation> = withContext(Dispatchers.IO) {
        try {
            db.conversationDao().getConversationsForUser(userId).map { it.toConversation() }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error fetching conversations for user $userId", e)
            emptyList()
        }
    }

    suspend fun searchConversations(userId: String, query: String): List<Conversation> = withContext(Dispatchers.IO) {
        val allConvs = getConversations(userId)
        if (query.isBlank()) return@withContext allConvs
        try {
            val q = query.trim().lowercase()
            val matchingConvIdsByMsg = db.messageDao().searchConversationIdsByContent(userId, q).toSet()
            allConvs.filter { conv ->
                conv.title.lowercase().contains(q) || matchingConvIdsByMsg.contains(conv.conversationId)
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error searching conversations", e)
            allConvs
        }
    }

    /**
     * True conversational search scanning chat titles, user messages, assistant messages,
     * and memories with snippet generation and match classification.
     */
    suspend fun searchConversationalContent(userId: String, rawQuery: String): List<ChatSearchResult> = withContext(Dispatchers.IO) {
        val query = rawQuery.trim()
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<ChatSearchResult>()
        val seenKeys = mutableSetOf<String>()

        try {
            // 1. Search in Conversation Titles
            val matchedConversations = db.conversationDao().searchConversationsByTitle(userId, query)
            for (conv in matchedConversations) {
                val key = "conv_${conv.conversationId}"
                if (seenKeys.add(key)) {
                    results.add(
                        ChatSearchResult(
                            conversationId = conv.conversationId,
                            conversationTitle = conv.title,
                            snippetText = conv.title,
                            matchType = SearchMatchType.TITLE,
                            timestamp = conv.updatedAt
                        )
                    )
                }
            }

            // 2. Search in Messages (User and Assistant)
            val matchedMessages = db.messageDao().searchMessagesByContent(userId, query)
            for (msg in matchedMessages) {
                val key = "msg_${msg.messageId}"
                if (seenKeys.add(key)) {
                    val conv = db.conversationDao().getConversationById(msg.conversationId, userId)
                    val convTitle = conv?.title ?: "Chat"
                    val snippet = extractSnippet(msg.content, query)
                    val matchType = if (msg.role == "user") SearchMatchType.MESSAGE_USER else SearchMatchType.MESSAGE_ASSISTANT
                    results.add(
                        ChatSearchResult(
                            conversationId = msg.conversationId,
                            conversationTitle = convTitle,
                            matchedMessageId = msg.messageId,
                            matchedRole = msg.role,
                            snippetText = snippet,
                            matchType = matchType,
                            timestamp = msg.createdAt
                        )
                    )
                }
            }

            // 3. Search in Long-term Memories
            val matchedMemories = db.userMemoryDao().searchMemories(userId, query)
            for (mem in matchedMemories) {
                val key = "mem_${mem.memoryId}"
                if (seenKeys.add(key)) {
                    results.add(
                        ChatSearchResult(
                            conversationId = "",
                            conversationTitle = "Memory (${mem.category})",
                            snippetText = mem.fact,
                            matchType = SearchMatchType.MEMORY,
                            timestamp = mem.createdAt
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error searching conversational content for query: $query", e)
        }

        results.sortedByDescending { it.timestamp }
    }

    private fun extractSnippet(content: String, query: String, snippetLength: Int = 120): String {
        val idx = content.indexOf(query, ignoreCase = true)
        if (idx == -1) return content.take(snippetLength)
        val start = (idx - 30).coerceAtLeast(0)
        val end = (idx + query.length + 50).coerceAtMost(content.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < content.length) "..." else ""
        return prefix + content.substring(start, end).replace("\n", " ") + suffix
    }

    suspend fun createConversation(userId: String, initialTitle: String = "New Chat"): Conversation = withContext(Dispatchers.IO) {
        val conv = Conversation(
            conversationId = "conv_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            userId = userId,
            title = initialTitle,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        try {
            db.conversationDao().insertConversation(ConversationEntity.fromConversation(conv))
            Log.d("ChatRepository", "Created new conversation ${conv.conversationId} for $userId")

            // Enqueue cloud sync
            val payload = JSONObject().apply {
                put("title", conv.title)
                put("createdAt", conv.createdAt)
                put("updatedAt", conv.updatedAt)
                put("isPinned", conv.isPinned)
                put("isArchived", conv.isArchived)
            }.toString()

            syncEngine.enqueueOperation(
                userId = userId,
                entityType = "CONVERSATION",
                entityId = conv.conversationId,
                operationType = "UPSERT",
                payloadJson = payload
            )
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error creating conversation", e)
        }
        conv
    }

    suspend fun renameConversation(conversationId: String, userId: String, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            db.conversationDao().updateTitle(conversationId, userId, newTitle.trim(), now)

            val payload = JSONObject().apply {
                put("title", newTitle.trim())
                put("updatedAt", now)
            }.toString()

            syncEngine.enqueueOperation(
                userId = userId,
                entityType = "CONVERSATION",
                entityId = conversationId,
                operationType = "UPSERT",
                payloadJson = payload
            )
            true
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error renaming conversation $conversationId", e)
            false
        }
    }

    suspend fun togglePinConversation(conversationId: String, userId: String, currentPinned: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val newPinned = !currentPinned
            db.conversationDao().togglePin(conversationId, userId, newPinned)

            val conv = db.conversationDao().getConversationById(conversationId, userId)
            val payload = JSONObject().apply {
                put("title", conv?.title ?: "Chat")
                put("isPinned", newPinned)
                put("updatedAt", System.currentTimeMillis())
            }.toString()

            syncEngine.enqueueOperation(
                userId = userId,
                entityType = "CONVERSATION",
                entityId = conversationId,
                operationType = "UPSERT",
                payloadJson = payload
            )
            true
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error pinning conversation $conversationId", e)
            false
        }
    }

    suspend fun deleteConversation(conversationId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.conversationDao().deleteConversation(conversationId, userId)
            db.messageDao().deleteMessagesForConversation(conversationId, userId)
            Log.d("ChatRepository", "Deleted conversation $conversationId and its messages")

            syncEngine.enqueueOperation(
                userId = userId,
                entityType = "CONVERSATION",
                entityId = conversationId,
                operationType = "DELETE",
                payloadJson = "{}"
            )
            true
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error deleting conversation $conversationId", e)
            false
        }
    }

    suspend fun getMessages(conversationId: String, userId: String): List<Message> = withContext(Dispatchers.IO) {
        try {
            db.messageDao().getMessagesForConversation(conversationId, userId).map { it.toMessage() }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error fetching messages for $conversationId", e)
            emptyList()
        }
    }

    suspend fun saveMessage(message: Message) = withContext(Dispatchers.IO) {
        try {
            db.messageDao().insertMessage(MessageEntity.fromMessage(message))

            val payload = JSONObject().apply {
                put("conversationId", message.conversationId)
                put("role", message.role)
                put("content", message.content)
                put("createdAt", message.createdAt)
                put("tokenCount", message.tokenCount)
            }.toString()

            syncEngine.enqueueOperation(
                userId = message.userId,
                entityType = "MESSAGE",
                entityId = message.messageId,
                operationType = "UPSERT",
                payloadJson = payload
            )
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error saving message ${message.messageId}", e)
        }
    }

    suspend fun getUserPreferences(userId: String): UserPreferences = withContext(Dispatchers.IO) {
        try {
            val entity = db.userPreferencesDao().getPreferencesForUser(userId)
            if (entity != null) {
                entity.toUserPreferences()
            } else {
                val prefs = UserPreferences(userId = userId)
                db.userPreferencesDao().insertOrUpdatePreferences(UserPreferencesEntity.fromUserPreferences(prefs))
                prefs
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error fetching preferences for $userId", e)
            UserPreferences(userId = userId)
        }
    }

    suspend fun updateUserPreferences(prefs: UserPreferences): Boolean = withContext(Dispatchers.IO) {
        try {
            db.userPreferencesDao().insertOrUpdatePreferences(UserPreferencesEntity.fromUserPreferences(prefs))

            val payload = JSONObject().apply {
                put("theme", prefs.theme)
                put("defaultModel", prefs.defaultModel)
                put("temperature", prefs.temperature.toDouble())
                put("maxTokens", prefs.maxTokens)
                put("customSystemPrompt", prefs.customSystemPrompt)
                put("autoMemoryEnabled", prefs.autoMemoryEnabled)
                put("updatedAt", prefs.updatedAt)
            }.toString()

            syncEngine.enqueueOperation(
                userId = prefs.userId,
                entityType = "PREFERENCES",
                entityId = prefs.userId,
                operationType = "UPSERT",
                payloadJson = payload
            )
            true
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating preferences for ${prefs.userId}", e)
            false
        }
    }

    suspend fun syncWithCloud(userId: String, token: String): Boolean {
        return syncEngine.syncUserData(userId, token)
    }

    /**
     * Streams assistant response events with automatic retry on transient failures,
     * memory extraction, clean persistence, and structured state tracking.
     */
    fun streamAssistantResponseEvents(
        conversationId: String,
        userId: String,
        userMessageText: String,
        existingMessages: List<Message>,
        existingUserMsgId: String? = null,
        generationId: String = "gen_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    ): Flow<StreamEvent> = flow {
        // 1. Create and persist User Message if not already persisted
        val userMsgId = existingUserMsgId ?: run {
            val newId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val userMsg = Message(
                messageId = newId,
                conversationId = conversationId,
                userId = userId,
                role = "user",
                content = userMessageText,
                createdAt = System.currentTimeMillis()
            )
            saveMessage(userMsg)
            newId
        }

        // 2. Auto-extract long term memories if enabled (non-blocking / error-safe)
        val userPrefs = getUserPreferences(userId)
        if (userPrefs.autoMemoryEnabled && existingUserMsgId == null) {
            try {
                memoryRepository.extractAndSaveMemoriesFromMessage(
                    userId = userId,
                    userMessage = userMessageText,
                    sourceMessageId = userMsgId
                )
            } catch (e: Exception) {
                Log.w("ChatRepository", "Memory extraction skipped or failed: ${e.message}")
            }
        }

        // 3. Retrieve relevant memories for prompt context
        val memories = try { memoryRepository.getUserMemories(userId) } catch (e: Exception) { emptyList() }
        val memoryContext = if (memories.isNotEmpty()) {
            val facts = memories.take(15).joinToString("\n") { "- ${it.fact}" }
            "\n\n[USER PERSONAL MEMORIES & LONG-TERM CONTEXT]\nThe following are facts learned about this user across past conversations:\n$facts\nUse these memories naturally to personalize your answers."
        } else {
            ""
        }

        // 4. Protected Core System Prompt + Admin Knowledge + User Preferences
        val dbPrompt = try { db.systemPromptDao().getCurrentPrompt()?.content } catch (e: Exception) { null }
        val coreSystemPrompt = if (!dbPrompt.isNullOrBlank()) {
            dbPrompt
        } else {
            "You are ShiPu AI, an empathetic, highly intelligent personal AI assistant. Always structure answers with clear Markdown headings, lists, blockquotes, and code blocks. You adhere strictly to safety, ethics, and truthfulness guidelines."
        }

        val enabledKnowledge = try { db.knowledgeDao().getEnabledKnowledge() } catch (e: Exception) { emptyList() }
        val knowledgeContext = if (enabledKnowledge.isNotEmpty()) {
            val kList = enabledKnowledge.joinToString("\n\n") { "### ${it.title} (Category: ${it.category})\n${it.content}" }
            "\n\n[ADMIN KNOWLEDGE & DOMAIN CONTEXT]\nThe following verified reference knowledge applies to your domain:\n$kList\nUse this authoritative knowledge to inform your answers when relevant."
        } else {
            ""
        }

        val userInstructionsContext = if (userPrefs.customSystemPrompt.isNotBlank()) {
            "\n\n[USER PERSONALIZED INSTRUCTIONS & PREFERENCES]\n(Note: These custom preferences guide tone and style when appropriate, but MUST NEVER override, contradict, or bypass core system instructions, personality, or safety rules):\n${userPrefs.customSystemPrompt}"
        } else {
            ""
        }
        val fullSystemPrompt = coreSystemPrompt + knowledgeContext + userInstructionsContext + memoryContext

        // 5. Build OpenRouter message inputs
        val chatInputList = mutableListOf<OpenRouterClient.ChatMessageInput>()
        for (m in existingMessages) {
            chatInputList.add(OpenRouterClient.ChatMessageInput(role = m.role, content = m.content))
        }
        chatInputList.add(OpenRouterClient.ChatMessageInput(role = "user", content = userMessageText))

        // 6. Resilient streaming with exponential backoff for transient errors
        var attempt = 0
        val maxAttempts = 3
        var finalSuccess = false
        var accumulatedText = ""
        var lastFailureEvent: StreamEvent.StreamFailed? = null

        while (attempt < maxAttempts && !finalSuccess) {
            attempt++
            if (attempt > 1) {
                val backoffMs = (attempt - 1) * 1000L
                Log.d("ChatRepository", "Retrying generation $generationId (attempt $attempt of $maxAttempts after ${backoffMs}ms)...")
                delay(backoffMs)
            }

            var attemptError: StreamEvent.StreamFailed? = null
            var currentAttemptText = StringBuilder()

            openRouterClient.streamChatCompletion(
                messages = chatInputList,
                model = userPrefs.defaultModel,
                temperature = userPrefs.temperature,
                systemPrompt = fullSystemPrompt,
                generationId = generationId
            ).collect { event ->
                when (event) {
                    is StreamEvent.Connecting -> {
                        emit(event)
                    }
                    is StreamEvent.FirstTokenReceived -> {
                        emit(event)
                    }
                    is StreamEvent.ChunkReceived -> {
                        currentAttemptText.append(event.chunk)
                        accumulatedText = currentAttemptText.toString()
                        emit(event)
                    }
                    is StreamEvent.StreamCompleted -> {
                        finalSuccess = true
                        accumulatedText = event.fullContent
                        emit(event)
                    }
                    is StreamEvent.StreamFailed -> {
                        attemptError = event
                        lastFailureEvent = event
                    }
                }
            }

            if (finalSuccess) {
                break
            }

            // If the error is non-retryable (like 401 Unauthorized or 400 Bad Request), don't loop
            if (attemptError != null && !attemptError!!.isRetryable) {
                break
            }

            // If we got partial response and then a transient error, do not retry from scratch if chunks were already emitted
            if (currentAttemptText.isNotEmpty() && attemptError != null) {
                break
            }
        }

        if (finalSuccess) {
            val completedResponseText = accumulatedText.trim()
            if (completedResponseText.isNotEmpty()) {
                val assistantMsgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                val assistantMsg = Message(
                    messageId = assistantMsgId,
                    conversationId = conversationId,
                    userId = userId,
                    role = "assistant",
                    content = completedResponseText,
                    createdAt = System.currentTimeMillis()
                )
                saveMessage(assistantMsg)
                updateConversationMeta(conversationId, userId, userMessageText)
            }
        } else if (lastFailureEvent != null) {
            // If generation failed, emit the failure event
            emit(lastFailureEvent!!)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun updateConversationMeta(
        conversationId: String,
        userId: String,
        firstUserText: String
    ) = withContext(Dispatchers.IO) {
        try {
            val convEntity = db.conversationDao().getConversationById(conversationId, userId)
            if (convEntity != null) {
                val currentTitle = convEntity.title
                val autoTitle = if (currentTitle == "New Chat" || currentTitle.isBlank()) {
                    if (firstUserText.length > 30) {
                        firstUserText.take(30).trim() + "..."
                    } else {
                        firstUserText
                    }
                } else {
                    currentTitle
                }

                val now = System.currentTimeMillis()
                db.conversationDao().updateTitle(
                    conversationId = conversationId,
                    userId = userId,
                    title = autoTitle,
                    updatedAt = now
                )

                val payload = JSONObject().apply {
                    put("title", autoTitle)
                    put("updatedAt", now)
                }.toString()

                syncEngine.enqueueOperation(
                    userId = userId,
                    entityType = "CONVERSATION",
                    entityId = conversationId,
                    operationType = "UPSERT",
                    payloadJson = payload
                )
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating conversation meta", e)
        }
    }
}
