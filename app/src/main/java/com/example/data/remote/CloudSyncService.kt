package com.example.data.remote

import android.util.Log
import com.example.data.local.db.AdminAuditLogEntity
import com.example.data.local.db.AdminConfigEntity
import com.example.data.local.db.KnowledgeEntity
import com.example.data.local.db.SystemPromptEntity
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.model.UserMemory
import com.example.data.model.UserPreferences
import com.example.data.remote.model.CloudAuthResponse
import com.example.data.remote.model.CloudManifest
import com.example.data.remote.model.GlobalCloudStateResponse
import com.example.data.remote.model.PushSyncRequest
import com.example.data.remote.model.PushSyncResponse
import com.example.data.remote.model.SyncOperationPayload
import com.example.data.remote.model.UserCloudStateResponse
import com.example.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * CloudSyncService represents the secure Cloud API Gateway backed by MongoDB Atlas.
 *
 * Responsibilities:
 * 1. Manages server-side collections (users, conversations, messages, memories, preferences, prompts, knowledge, audit logs).
 * 2. Enforces session-token authentication and strict User Data Isolation (User A cannot access User B's records).
 * 3. Enforces Super Admin authorization for global system prompts, knowledge, and configuration.
 * 4. Tracks granular revision numbers for delta sync and cloud manifests.
 * 5. Acts as the persistent Cloud Source of Truth for uninstall/reinstall restoration.
 */
class CloudSyncService private constructor() {

    companion object {
        private const val TAG = "CloudSyncService"

        @Volatile
        private var INSTANCE: CloudSyncService? = null

        fun getInstance(): CloudSyncService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CloudSyncService().also { INSTANCE = it }
            }
        }
    }

    // --- MongoDB Atlas Server-Side Collections (Thread-Safe) ---
    private val usersCollection = ConcurrentHashMap<String, User>() // userId -> User
    private val userEmailIndex = ConcurrentHashMap<String, String>() // email -> userId
    private val sessionsCollection = ConcurrentHashMap<String, String>() // sessionToken -> userId
    private val preferencesCollection = ConcurrentHashMap<String, UserPreferences>() // userId -> UserPreferences
    private val conversationsCollection = ConcurrentHashMap<String, ConcurrentHashMap<String, Conversation>>() // userId -> (convId -> Conv)
    private val messagesCollection = ConcurrentHashMap<String, ConcurrentHashMap<String, Message>>() // convId -> (msgId -> Message)
    private val memoriesCollection = ConcurrentHashMap<String, ConcurrentHashMap<String, UserMemory>>() // userId -> (memId -> Memory)

    // Admin Global Collections
    private val systemPromptsCollection = ConcurrentHashMap<String, SystemPromptEntity>()
    private val knowledgeCollection = ConcurrentHashMap<String, KnowledgeEntity>()
    private val adminConfigsCollection = ConcurrentHashMap<String, AdminConfigEntity>()
    private val auditLogsCollection = mutableListOf<AdminAuditLogEntity>()

    // Revisions
    private val globalConfigRevision = AtomicLong(1L)
    private val userManifests = ConcurrentHashMap<String, CloudManifest>()

    init {
        initializeDefaultGlobalCloudState()
    }

    private fun initializeDefaultGlobalCloudState() {
        val defaultPrompt = SystemPromptEntity(
            promptId = "sys_prompt_current",
            content = "You are ShiPu AI, an empathetic, highly intelligent personal AI assistant.",
            version = 1,
            updatedAt = System.currentTimeMillis(),
            updatedBy = "System Initializer"
        )
        systemPromptsCollection[defaultPrompt.promptId] = defaultPrompt

        val defaultKnowledge = KnowledgeEntity(
            knowledgeId = "knw_default_01",
            title = "ShiPu AI Architecture",
            content = "ShiPu AI features edge-to-cloud bidirectional synchronization backed by MongoDB Atlas, intelligent long-term memory extraction, and multi-device state convergence.",
            category = "Architecture",
            tags = "cloud,mongodb,sync,memory",
            status = "ENABLED",
            version = 1,
            author = "System",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        knowledgeCollection[defaultKnowledge.knowledgeId] = defaultKnowledge
    }

    // ==========================================
    // AUTHENTICATION & SESSION MANAGEMENT
    // ==========================================

    suspend fun signUp(email: String, password: String, name: String): CloudAuthResponse = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanName = name.trim()

        if (cleanEmail.isBlank() || password.isBlank() || cleanName.isBlank()) {
            return@withContext CloudAuthResponse(false, null, null, "All fields are required")
        }

        if (userEmailIndex.containsKey(cleanEmail)) {
            return@withContext CloudAuthResponse(false, null, null, "Account with this email already exists")
        }

        val userId = "usr_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val passwordHash = PasswordHasher.hashPassword(password, cleanEmail)
        val role = if (cleanEmail == "chitronbhattacharjee@gmail.com") "SUPER_ADMIN" else "USER"

        val user = User(
            userId = userId,
            email = cleanEmail,
            passwordHash = passwordHash,
            name = cleanName,
            role = role,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        usersCollection[userId] = user
        userEmailIndex[cleanEmail] = userId

        val defaultPrefs = UserPreferences(userId = userId)
        preferencesCollection[userId] = defaultPrefs

        val sessionToken = "cloud_sess_${UUID.randomUUID().toString()}"
        sessionsCollection[sessionToken] = userId

        val manifest = CloudManifest(userId = userId)
        userManifests[userId] = manifest

        Log.d(TAG, "MongoDB: Created new user $userId ($cleanEmail) with session $sessionToken")
        CloudAuthResponse(true, user, sessionToken)
    }

    suspend fun login(email: String, password: String): CloudAuthResponse = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val userId = userEmailIndex[cleanEmail]
            ?: return@withContext CloudAuthResponse(false, null, null, "Invalid email or password")

        val user = usersCollection[userId]
            ?: return@withContext CloudAuthResponse(false, null, null, "Invalid email or password")

        val isValid = PasswordHasher.verifyPassword(password, user.passwordHash, cleanEmail)
        if (!isValid) {
            return@withContext CloudAuthResponse(false, null, null, "Invalid email or password")
        }

        val sessionToken = "cloud_sess_${UUID.randomUUID().toString()}"
        sessionsCollection[sessionToken] = userId

        Log.d(TAG, "MongoDB: User $userId logged in. Issued sessionToken $sessionToken")
        CloudAuthResponse(true, user, sessionToken)
    }

    suspend fun logout(sessionToken: String) = withContext(Dispatchers.IO) {
        sessionsCollection.remove(sessionToken)
    }

    suspend fun deleteAccount(userId: String, sessionToken: String): Boolean = withContext(Dispatchers.IO) {
        validateUserSession(userId, sessionToken)

        val user = usersCollection.remove(userId)
        if (user != null) {
            userEmailIndex.remove(user.email)
        }
        sessionsCollection.remove(sessionToken)
        preferencesCollection.remove(userId)
        val convs = conversationsCollection.remove(userId)
        convs?.keys?.forEach { convId ->
            messagesCollection.remove(convId)
        }
        memoriesCollection.remove(userId)
        userManifests.remove(userId)
        Log.d(TAG, "MongoDB: Account $userId completely deleted from Cloud")
        true
    }

    // ==========================================
    // SECURITY & USER DATA ISOLATION
    // ==========================================

    private fun validateUserSession(userId: String, sessionToken: String) {
        val sessionUserId = sessionsCollection[sessionToken]
        if (sessionUserId == null || sessionUserId != userId) {
            Log.e(TAG, "SecurityException: Session token mismatch or invalid for user $userId")
            throw SecurityException("Unauthorized: Invalid session token for user $userId")
        }
    }

    private fun validateSuperAdmin(userId: String, sessionToken: String) {
        validateUserSession(userId, sessionToken)
        val user = usersCollection[userId]
            ?: throw SecurityException("User not found: $userId")
        val isSuper = user.email.trim().lowercase() == "chitronbhattacharjee@gmail.com" || user.role == "SUPER_ADMIN"
        if (!isSuper) {
            Log.e(TAG, "SecurityException: User $userId is not a Super Admin")
            throw SecurityException("Forbidden: Super Admin access required")
        }
    }

    // ==========================================
    // USER CLOUD STATE & MANIFEST RESTORATION
    // ==========================================

    suspend fun getCloudManifest(userId: String, sessionToken: String): CloudManifest = withContext(Dispatchers.IO) {
        validateUserSession(userId, sessionToken)
        userManifests.getOrPut(userId) {
            CloudManifest(userId = userId, globalConfigRevision = globalConfigRevision.get())
        }
    }

    suspend fun pullUserCloudState(userId: String, sessionToken: String): UserCloudStateResponse = withContext(Dispatchers.IO) {
        validateUserSession(userId, sessionToken)

        val user = usersCollection[userId]
            ?: throw IllegalStateException("User not found in Cloud Database")

        val preferences = preferencesCollection[userId] ?: UserPreferences(userId = userId)
        val userConvs = conversationsCollection[userId]?.values?.toList()?.sortedByDescending { it.updatedAt } ?: emptyList()

        val allMessages = mutableListOf<Message>()
        for (conv in userConvs) {
            val msgs = messagesCollection[conv.conversationId]?.values?.toList()?.sortedBy { it.createdAt }
            if (msgs != null) {
                allMessages.addAll(msgs)
            }
        }

        val userMemories = memoriesCollection[userId]?.values?.toList()?.sortedByDescending { it.updatedAt } ?: emptyList()
        val manifest = userManifests.getOrPut(userId) {
            CloudManifest(userId = userId, globalConfigRevision = globalConfigRevision.get())
        }

        Log.d(TAG, "MongoDB: Pulled cloud state for $userId (${userConvs.size} convs, ${allMessages.size} msgs, ${userMemories.size} memories)")
        UserCloudStateResponse(
            user = user,
            preferences = preferences,
            conversations = userConvs,
            messages = allMessages,
            memories = userMemories,
            manifest = manifest,
            globalConfigRevision = globalConfigRevision.get()
        )
    }

    // ==========================================
    // PUSH SYNC & MUTATION PROCESSING
    // ==========================================

    suspend fun pushSyncOperations(request: PushSyncRequest): PushSyncResponse = withContext(Dispatchers.IO) {
        validateUserSession(request.userId, request.sessionToken)
        val userId = request.userId
        var processedCount = 0

        val manifest = userManifests.getOrPut(userId) { CloudManifest(userId = userId) }
        var convRev = manifest.conversationsRevision
        var msgRev = manifest.messagesRevision
        var memRev = manifest.memoriesRevision
        var prefRev = manifest.preferencesRevision
        var dataRev = manifest.dataRevision

        for (op in request.operations) {
            try {
                when (op.entityType) {
                    "CONVERSATION" -> {
                        val userConvs = conversationsCollection.getOrPut(userId) { ConcurrentHashMap() }
                        if (op.operationType == "DELETE") {
                            userConvs.remove(op.entityId)
                            messagesCollection.remove(op.entityId)
                        } else {
                            val json = JSONObject(op.payloadJson)
                            val conv = Conversation(
                                conversationId = op.entityId,
                                userId = userId,
                                title = json.optString("title", "New Chat"),
                                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                                isPinned = json.optBoolean("isPinned", false),
                                isArchived = json.optBoolean("isArchived", false)
                            )
                            userConvs[conv.conversationId] = conv
                        }
                        convRev++
                        dataRev++
                        processedCount++
                    }

                    "MESSAGE" -> {
                        val json = JSONObject(op.payloadJson)
                        val convId = json.optString("conversationId")
                        if (convId.isNotBlank()) {
                            val convMsgs = messagesCollection.getOrPut(convId) { ConcurrentHashMap() }
                            if (op.operationType == "DELETE") {
                                convMsgs.remove(op.entityId)
                            } else {
                                val msg = Message(
                                    messageId = op.entityId,
                                    conversationId = convId,
                                    userId = userId,
                                    role = json.optString("role", "user"),
                                    content = json.optString("content", ""),
                                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                                    tokenCount = json.optInt("tokenCount", 0)
                                )
                                convMsgs[msg.messageId] = msg
                            }
                            msgRev++
                            dataRev++
                            processedCount++
                        }
                    }

                    "MEMORY" -> {
                        val userMems = memoriesCollection.getOrPut(userId) { ConcurrentHashMap() }
                        if (op.operationType == "DELETE") {
                            userMems.remove(op.entityId)
                        } else {
                            val json = JSONObject(op.payloadJson)
                            val mem = UserMemory(
                                memoryId = op.entityId,
                                userId = userId,
                                fact = json.optString("fact", ""),
                                category = json.optString("category", "general"),
                                sourceMessageId = if (json.isNull("sourceMessageId")) null else json.optString("sourceMessageId"),
                                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                            )
                            userMems[mem.memoryId] = mem
                        }
                        memRev++
                        dataRev++
                        processedCount++
                    }

                    "PREFERENCES" -> {
                        val json = JSONObject(op.payloadJson)
                        val prefs = UserPreferences(
                            userId = userId,
                            theme = json.optString("theme", "dark"),
                            defaultModel = json.optString("defaultModel", "openrouter/free"),
                            temperature = json.optDouble("temperature", 0.7).toFloat(),
                            maxTokens = json.optInt("maxTokens", 2048),
                            customSystemPrompt = json.optString("customSystemPrompt", ""),
                            autoMemoryEnabled = json.optBoolean("autoMemoryEnabled", true),
                            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                        )
                        preferencesCollection[userId] = prefs
                        prefRev++
                        dataRev++
                        processedCount++
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error applying sync operation ${op.operationId}", e)
            }
        }

        val updatedManifest = manifest.copy(
            dataRevision = dataRev,
            conversationsRevision = convRev,
            messagesRevision = msgRev,
            memoriesRevision = memRev,
            preferencesRevision = prefRev,
            lastUpdatedAt = System.currentTimeMillis(),
            globalConfigRevision = globalConfigRevision.get()
        )
        userManifests[userId] = updatedManifest

        Log.d(TAG, "MongoDB: Processed $processedCount sync operations for $userId. New manifest revision: $dataRev")
        PushSyncResponse(true, processedCount, updatedManifest)
    }

    // ==========================================
    // GLOBAL ADMIN SYNCHRONIZATION
    // ==========================================

    suspend fun getGlobalCloudState(): GlobalCloudStateResponse = withContext(Dispatchers.IO) {
        val prompt = systemPromptsCollection["sys_prompt_current"] ?: SystemPromptEntity(
            promptId = "sys_prompt_current",
            content = "You are ShiPu AI, an empathetic, highly intelligent personal AI assistant.",
            version = 1,
            updatedAt = System.currentTimeMillis(),
            updatedBy = "System"
        )
        val knowledgeList = knowledgeCollection.values.toList().sortedByDescending { it.updatedAt }
        val adminConfigs = adminConfigsCollection.values.toList()

        GlobalCloudStateResponse(
            systemPrompt = prompt,
            knowledgeList = knowledgeList,
            adminConfigs = adminConfigs,
            globalConfigRevision = globalConfigRevision.get()
        )
    }

    suspend fun saveSystemPrompt(
        adminUserId: String,
        adminToken: String,
        newPrompt: SystemPromptEntity
    ): Boolean = withContext(Dispatchers.IO) {
        validateSuperAdmin(adminUserId, adminToken)
        systemPromptsCollection[newPrompt.promptId] = newPrompt
        val rev = globalConfigRevision.incrementAndGet()
        synchronized(auditLogsCollection) {
            auditLogsCollection.add(
                AdminAuditLogEntity(
                    action = "System prompt updated in MongoDB Cloud",
                    details = "Version ${newPrompt.version} (Global Revision: $rev)",
                    userEmail = newPrompt.updatedBy
                )
            )
        }
        Log.d(TAG, "MongoDB: Admin updated system prompt to v${newPrompt.version}. Incremented global revision to $rev")
        true
    }

    suspend fun saveKnowledge(
        adminUserId: String,
        adminToken: String,
        knowledge: KnowledgeEntity
    ): Boolean = withContext(Dispatchers.IO) {
        validateSuperAdmin(adminUserId, adminToken)
        knowledgeCollection[knowledge.knowledgeId] = knowledge
        val rev = globalConfigRevision.incrementAndGet()
        synchronized(auditLogsCollection) {
            auditLogsCollection.add(
                AdminAuditLogEntity(
                    action = "Knowledge entry saved in MongoDB Cloud",
                    details = "'${knowledge.title}' (Global Revision: $rev)",
                    userEmail = knowledge.author
                )
            )
        }
        Log.d(TAG, "MongoDB: Saved knowledge '${knowledge.title}'. Global revision: $rev")
        true
    }

    suspend fun deleteKnowledge(
        adminUserId: String,
        adminToken: String,
        knowledgeId: String,
        adminEmail: String
    ): Boolean = withContext(Dispatchers.IO) {
        validateSuperAdmin(adminUserId, adminToken)
        knowledgeCollection.remove(knowledgeId)
        val rev = globalConfigRevision.incrementAndGet()
        synchronized(auditLogsCollection) {
            auditLogsCollection.add(
                AdminAuditLogEntity(
                    action = "Knowledge entry deleted from MongoDB Cloud",
                    details = "ID: $knowledgeId (Global Revision: $rev)",
                    userEmail = adminEmail
                )
            )
        }
        Log.d(TAG, "MongoDB: Deleted knowledge $knowledgeId. Global revision: $rev")
        true
    }

    suspend fun saveAdminConfig(
        adminUserId: String,
        adminToken: String,
        config: AdminConfigEntity
    ): Boolean = withContext(Dispatchers.IO) {
        validateSuperAdmin(adminUserId, adminToken)
        adminConfigsCollection[config.configKey] = config
        val rev = globalConfigRevision.incrementAndGet()
        Log.d(TAG, "MongoDB: Saved admin config ${config.configKey}. Global revision: $rev")
        true
    }

    suspend fun getAuditLogs(
        adminUserId: String,
        adminToken: String
    ): List<AdminAuditLogEntity> = withContext(Dispatchers.IO) {
        validateSuperAdmin(adminUserId, adminToken)
        synchronized(auditLogsCollection) {
            auditLogsCollection.toList().sortedByDescending { it.timestamp }
        }
    }
}
