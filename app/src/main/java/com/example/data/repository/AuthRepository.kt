package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.SessionManager
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.SessionEntity
import com.example.data.local.db.UserEntity
import com.example.data.local.db.UserPreferencesEntity
import com.example.data.model.User
import com.example.data.model.UserPreferences
import com.example.data.remote.CloudSyncService
import com.example.data.sync.CloudSyncEngine
import com.example.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

class AuthRepository(
    private val sessionManager: SessionManager,
    context: Context? = null
) {
    private val db: AppDatabase by lazy { AppDatabase.getInstance(context) }
    private val cloudService: CloudSyncService = CloudSyncService.getInstance()
    private val syncEngine: CloudSyncEngine by lazy { CloudSyncEngine.getInstance(context) }

    suspend fun signUp(email: String, password: String, name: String): AuthResult<User> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            val cleanName = name.trim()

            if (cleanEmail.isBlank() || password.isBlank() || cleanName.isBlank()) {
                return@withContext AuthResult.Error("All fields are required")
            }

            if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                return@withContext AuthResult.Error("Please enter a valid email address")
            }

            if (password.length < 6) {
                return@withContext AuthResult.Error("Password must be at least 6 characters")
            }

            // 1. Sign up on MongoDB Cloud Backend
            val cloudResp = cloudService.signUp(cleanEmail, password, cleanName)
            if (!cloudResp.success || cloudResp.user == null || cloudResp.sessionToken == null) {
                return@withContext AuthResult.Error(cloudResp.message ?: "Account with this email already exists")
            }

            val newUser = cloudResp.user
            val sessionToken = cloudResp.sessionToken

            // 2. Insert into local Room DB cache
            db.userDao().insertUser(UserEntity.fromUser(newUser))

            // 3. Create default preferences locally
            val defaultPrefs = UserPreferences(userId = newUser.userId)
            db.userPreferencesDao().insertOrUpdatePreferences(UserPreferencesEntity.fromUserPreferences(defaultPrefs))

            // 4. Create session
            db.sessionDao().insertSession(SessionEntity(token = sessionToken, userId = newUser.userId))

            // 5. Save session locally in SessionManager
            sessionManager.saveSession(newUser.userId, sessionToken, cleanName, cleanEmail)

            // 6. Perform initial cloud restore / metadata setup
            syncEngine.restoreUserData(newUser.userId, sessionToken)

            Log.d("AuthRepository", "Signed up new user with userId: ${newUser.userId} and cloud-synced")
            AuthResult.Success(newUser)
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Error during sign up", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to create account")
        }
    }

    suspend fun login(email: String, password: String): AuthResult<User> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank() || password.isBlank()) {
                return@withContext AuthResult.Error("Email and password are required")
            }

            // 1. First authenticate with MongoDB Cloud Backend (Source of Truth)
            val cloudResp = cloudService.login(cleanEmail, password)
            if (cloudResp.success && cloudResp.user != null && cloudResp.sessionToken != null) {
                val cloudUser = cloudResp.user
                val sessionToken = cloudResp.sessionToken

                // Save session in SessionManager
                sessionManager.saveSession(cloudUser.userId, sessionToken, cloudUser.name, cloudUser.email)
                db.sessionDao().insertSession(SessionEntity(token = sessionToken, userId = cloudUser.userId))

                // Restore complete user cloud state into local Room DB (conversations, messages, memories, preferences)
                syncEngine.restoreUserData(cloudUser.userId, sessionToken)

                Log.d("AuthRepository", "Logged in user via MongoDB Cloud: ${cloudUser.userId}")
                return@withContext AuthResult.Success(cloudUser)
            }

            // Fallback: Check local Room DB cache if offline
            val userEntity = db.userDao().getUserByEmail(cleanEmail)
            if (userEntity != null) {
                val user = userEntity.toUser()
                val isValid = PasswordHasher.verifyPassword(password, user.passwordHash, cleanEmail)
                if (isValid) {
                    val sessionToken = UUID.randomUUID().toString()
                    db.sessionDao().insertSession(SessionEntity(token = sessionToken, userId = user.userId))
                    sessionManager.saveSession(user.userId, sessionToken, user.name, user.email)
                    Log.d("AuthRepository", "Logged in user via local cache: ${user.userId}")
                    return@withContext AuthResult.Success(user)
                }
            }

            AuthResult.Error(cloudResp.message ?: "Invalid email or password")
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Error during login", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to log in")
        }
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getActiveToken()
            if (token != null) {
                cloudService.logout(token)
                db.sessionDao().deleteSession(token)
            }
            sessionManager.clearSession()
            true
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Error during logout", e)
            sessionManager.clearSession()
            true
        }
    }

    suspend fun getCurrentUser(userId: String): User? = withContext(Dispatchers.IO) {
        try {
            val userEntity = db.userDao().getUserById(userId)
            userEntity?.toUser()
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Error getting user $userId", e)
            null
        }
    }

    suspend fun deleteAccount(userId: String): AuthResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getActiveToken()
            if (token != null) {
                try {
                    cloudService.deleteAccount(userId, token)
                } catch (e: Throwable) {
                    Log.w("AuthRepository", "Cloud delete warning: ${e.message}")
                }
            }

            db.userDao().deleteUser(userId)
            db.conversationDao().deleteUserConversations(userId)
            db.messageDao().deleteUserMessages(userId)
            db.userMemoryDao().clearUserMemories(userId)
            db.userPreferencesDao().deleteUserPreferences(userId)
            db.sessionDao().deleteUserSessions(userId)
            db.syncOperationDao().clearUserOperations(userId)
            db.syncMetadataDao().clearMetadata(userId)

            sessionManager.clearSession()
            Log.d("AuthRepository", "Deleted account and all associated data for $userId")
            AuthResult.Success(true)
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Error deleting account $userId", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to delete account")
        }
    }
}
