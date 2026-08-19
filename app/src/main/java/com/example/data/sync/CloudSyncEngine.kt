package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.SessionManager
import com.example.data.local.db.*
import com.example.data.remote.CloudSyncService
import com.example.data.remote.model.PushSyncRequest
import com.example.data.remote.model.SyncOperationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Restoring(val progressMessage: String) : SyncStatus()
    data class Synced(val timestamp: Long = System.currentTimeMillis()) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

/**
 * CloudSyncEngine coordinates real-time and background bidirectional synchronization
 * between the local Room database cache and MongoDB Atlas cloud storage.
 *
 * It provides:
 * 1. Complete Cloud Restoration on App Reinstall / Fresh Login.
 * 2. Durable Outbox (sync_operations table) for complete offline reliability.
 * 3. Bidirectional push/pull delta sync with revision tracking and conflict resolution.
 * 4. Super Admin Global Configuration & Knowledge propagation to all client apps.
 */
class CloudSyncEngine(
    context: Context? = null,
    private val cloudService: CloudSyncService = CloudSyncService.getInstance()
) {
    private val db: AppDatabase by lazy { AppDatabase.getInstance(context) }
    private val sessionManager: SessionManager? = context?.let { SessionManager(it) }
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    companion object {
        private const val TAG = "CloudSyncEngine"

        @Volatile
        private var INSTANCE: CloudSyncEngine? = null

        fun getInstance(context: Context? = null): CloudSyncEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CloudSyncEngine(context).also { INSTANCE = it }
            }
        }
    }

    // ==========================================
    // 1. UNINSTALL / REINSTALL RESTORATION FLOW
    // ==========================================

    /**
     * Completely restores the user's persistent cloud state from MongoDB into the local Room database.
     * Called automatically after successful login on any device.
     */
    suspend fun restoreUserData(userId: String, sessionToken: String): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                Log.d(TAG, "Starting complete cloud state restoration for user: $userId")
                _syncStatus.value = SyncStatus.Restoring("Restoring your data from Cloud...")

                // Fetch full user cloud state from MongoDB Atlas
                val cloudState = cloudService.pullUserCloudState(userId, sessionToken)

                // 1. Reconstruct User Profile
                db.userDao().insertUser(UserEntity.fromUser(cloudState.user))

                // 2. Reconstruct User Preferences & Personalized Instructions
                db.userPreferencesDao().insertOrUpdatePreferences(
                    UserPreferencesEntity.fromUserPreferences(cloudState.preferences)
                )

                // 3. Reconstruct Conversations
                for (conv in cloudState.conversations) {
                    db.conversationDao().insertConversation(ConversationEntity.fromConversation(conv))
                }

                // 4. Reconstruct Messages
                for (msg in cloudState.messages) {
                    db.messageDao().insertMessage(MessageEntity.fromMessage(msg))
                }

                // 5. Reconstruct Long-term Memories
                for (mem in cloudState.memories) {
                    db.userMemoryDao().insertMemory(UserMemoryEntity.fromUserMemory(mem))
                }

                // 6. Pull Admin Global State (System Prompts & Knowledge)
                val globalState = cloudService.getGlobalCloudState()
                db.systemPromptDao().savePrompt(globalState.systemPrompt)
                for (knowledge in globalState.knowledgeList) {
                    db.knowledgeDao().insertOrUpdate(knowledge)
                }

                // 7. Update Local Sync Metadata
                val metadata = SyncMetadataEntity(
                    userId = userId,
                    lastSyncedAt = System.currentTimeMillis(),
                    dataRevision = cloudState.manifest.dataRevision,
                    conversationsRevision = cloudState.manifest.conversationsRevision,
                    messagesRevision = cloudState.manifest.messagesRevision,
                    memoriesRevision = cloudState.manifest.memoriesRevision,
                    preferencesRevision = cloudState.manifest.preferencesRevision,
                    globalConfigRevision = globalState.globalConfigRevision,
                    isInitialRestoreCompleted = true
                )
                db.syncMetadataDao().insertOrUpdate(metadata)

                Log.d(TAG, "Successfully restored complete cloud state for $userId: " +
                        "${cloudState.conversations.size} conversations, ${cloudState.messages.size} messages, " +
                        "${cloudState.memories.size} memories")
                _syncStatus.value = SyncStatus.Synced()
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Error restoring cloud state for user $userId", e)
                _syncStatus.value = SyncStatus.Error("Failed to restore cloud state: ${e.localizedMessage}")
                false
            }
        }
    }

    // ==========================================
    // 2. OUTBOX ENQUEUE & OFFLINE QUEUEING
    // ==========================================

    /**
     * Enqueues a persistent change into the local durable outbox table (sync_operations)
     * and triggers an asynchronous cloud sync.
     */
    suspend fun enqueueOperation(
        userId: String,
        entityType: String,
        entityId: String,
        operationType: String,
        payloadJson: String
    ) = withContext(Dispatchers.IO) {
        try {
            val op = SyncOperationEntity(
                userId = userId,
                entityType = entityType,
                entityId = entityId,
                operationType = operationType,
                payloadJson = payloadJson,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            db.syncOperationDao().insertOperation(op)
            Log.d(TAG, "Enqueued sync operation: $entityType $operationType for $entityId")

            // Trigger non-blocking cloud sync in background
            triggerBackgroundSync(userId)
        } catch (e: Throwable) {
            Log.e(TAG, "Error enqueuing sync operation", e)
        }
    }

    fun triggerBackgroundSync(userId: String) {
        val token = sessionManager?.getActiveToken() ?: return
        syncScope.launch {
            syncUserData(userId, token)
        }
    }

    // ==========================================
    // 3. AUTOMATIC BIDIRECTIONAL SYNC (PUSH & PULL)
    // ==========================================

    /**
     * Performs a full bidirectional synchronization:
     * 1. Pushes pending local outbox operations to MongoDB.
     * 2. Pulls new cloud changes if cloud revision is newer.
     * 3. Checks and propagates Super Admin global prompt and knowledge updates.
     */
    suspend fun syncUserData(userId: String, sessionToken: String): Boolean = withContext(Dispatchers.IO) {
        if (syncMutex.isLocked) {
            Log.d(TAG, "Sync already in progress, skipping duplicate trigger")
            return@withContext true
        }

        syncMutex.withLock {
            try {
                _syncStatus.value = SyncStatus.Syncing
                Log.d(TAG, "Starting bidirectional sync for $userId")

                // Step A: Push local outbox operations to Cloud
                val pendingOps = db.syncOperationDao().getPendingOperations(userId)
                if (pendingOps.isNotEmpty()) {
                    Log.d(TAG, "Pushing ${pendingOps.size} pending operations to MongoDB Cloud")
                    val payloads = pendingOps.map {
                        SyncOperationPayload(
                            operationId = it.operationId,
                            userId = it.userId,
                            entityType = it.entityType,
                            entityId = it.entityId,
                            operationType = it.operationType,
                            payloadJson = it.payloadJson,
                            createdAt = it.createdAt,
                            revision = it.revision
                        )
                    }

                    val pushResponse = cloudService.pushSyncOperations(
                        PushSyncRequest(
                            userId = userId,
                            sessionToken = sessionToken,
                            operations = payloads
                        )
                    )

                    if (pushResponse.success) {
                        val completedIds = pendingOps.map { it.operationId }
                        db.syncOperationDao().deleteOperations(completedIds)
                        Log.d(TAG, "Successfully pushed outbox and cleared ${completedIds.size} operations")
                    }
                }

                // Step B: Pull Cloud Manifest & Delta Changes
                val cloudManifest = cloudService.getCloudManifest(userId, sessionToken)
                val localMetadata = db.syncMetadataDao().getMetadata(userId)

                if (localMetadata == null || !localMetadata.isInitialRestoreCompleted) {
                    // Rebuild entire local DB from cloud
                    val cloudState = cloudService.pullUserCloudState(userId, sessionToken)
                    for (conv in cloudState.conversations) {
                        db.conversationDao().insertConversation(ConversationEntity.fromConversation(conv))
                    }
                    for (msg in cloudState.messages) {
                        db.messageDao().insertMessage(MessageEntity.fromMessage(msg))
                    }
                    for (mem in cloudState.memories) {
                        db.userMemoryDao().insertMemory(UserMemoryEntity.fromUserMemory(mem))
                    }
                    db.userPreferencesDao().insertOrUpdatePreferences(
                        UserPreferencesEntity.fromUserPreferences(cloudState.preferences)
                    )
                } else if (cloudManifest.dataRevision > localMetadata.dataRevision) {
                    Log.d(TAG, "Cloud data revision (${cloudManifest.dataRevision}) > Local (${localMetadata.dataRevision}), pulling deltas")
                    val cloudState = cloudService.pullUserCloudState(userId, sessionToken)

                    // Merge conversations
                    for (conv in cloudState.conversations) {
                        db.conversationDao().insertConversation(ConversationEntity.fromConversation(conv))
                    }
                    // Merge messages (append-only)
                    for (msg in cloudState.messages) {
                        db.messageDao().insertMessage(MessageEntity.fromMessage(msg))
                    }
                    // Merge memories
                    for (mem in cloudState.memories) {
                        db.userMemoryDao().insertMemory(UserMemoryEntity.fromUserMemory(mem))
                    }
                    // Merge preferences
                    db.userPreferencesDao().insertOrUpdatePreferences(
                        UserPreferencesEntity.fromUserPreferences(cloudState.preferences)
                    )
                }

                // Step C: Check Global Admin Configuration Revision (System Prompt & Knowledge)
                val globalState = cloudService.getGlobalCloudState()
                val currentLocalGlobalRev = localMetadata?.globalConfigRevision ?: 0L
                if (globalState.globalConfigRevision > currentLocalGlobalRev) {
                    Log.d(TAG, "Newer Global Admin Configuration detected (${globalState.globalConfigRevision} > $currentLocalGlobalRev). Updating local cache.")
                    db.systemPromptDao().savePrompt(globalState.systemPrompt)
                    for (knowledge in globalState.knowledgeList) {
                        db.knowledgeDao().insertOrUpdate(knowledge)
                    }
                }

                // Update local metadata
                val updatedMetadata = SyncMetadataEntity(
                    userId = userId,
                    lastSyncedAt = System.currentTimeMillis(),
                    dataRevision = cloudManifest.dataRevision,
                    conversationsRevision = cloudManifest.conversationsRevision,
                    messagesRevision = cloudManifest.messagesRevision,
                    memoriesRevision = cloudManifest.memoriesRevision,
                    preferencesRevision = cloudManifest.preferencesRevision,
                    globalConfigRevision = globalState.globalConfigRevision,
                    isInitialRestoreCompleted = true
                )
                db.syncMetadataDao().insertOrUpdate(updatedMetadata)

                _syncStatus.value = SyncStatus.Synced()
                Log.d(TAG, "Bidirectional sync completed successfully for $userId")
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Sync failed for user $userId", e)
                _syncStatus.value = SyncStatus.Error("Sync error: ${e.localizedMessage}")
                false
            }
        }
    }
}
