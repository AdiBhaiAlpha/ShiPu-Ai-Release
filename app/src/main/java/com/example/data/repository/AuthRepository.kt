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

            val existing = db.userDao().getUserByEmail(cleanEmail)
            if (existing != null) {
                return@withContext AuthResult.Error("Account with this email already exists")
            }

            // Generate unique userId and hash password
            val userId = "usr_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val passwordHash = PasswordHasher.hashPassword(password, cleanEmail)

            val newUser = User(
                userId = userId,
                email = cleanEmail,
                passwordHash = passwordHash,
                name = cleanName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Insert into Room DB
            db.userDao().insertUser(UserEntity.fromUser(newUser))

            // Create default preferences
            val defaultPrefs = UserPreferences(userId = userId)
            db.userPreferencesDao().insertOrUpdatePreferences(UserPreferencesEntity.fromUserPreferences(defaultPrefs))

            // Create session
            val sessionToken = UUID.randomUUID().toString()
            db.sessionDao().insertSession(SessionEntity(token = sessionToken, userId = userId))

            // Save session locally in SessionManager
            sessionManager.saveSession(userId, sessionToken, cleanName, cleanEmail)

            Log.d("AuthRepository", "Signed up new user with userId: $userId")
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

            val userEntity = db.userDao().getUserByEmail(cleanEmail)
            if (userEntity == null) {
                return@withContext AuthResult.Error("Invalid email or password")
            }

            val user = userEntity.toUser()
            val isValid = PasswordHasher.verifyPassword(password, user.passwordHash, cleanEmail)

            if (!isValid) {
                return@withContext AuthResult.Error("Invalid email or password")
            }

            // Create session
            val sessionToken = UUID.randomUUID().toString()
            db.sessionDao().insertSession(SessionEntity(token = sessionToken, userId = user.userId))

            // Save session locally
            sessionManager.saveSession(user.userId, sessionToken, user.name, user.email)

            Log.d("AuthRepository", "Logged in user: ${user.userId}")
            AuthResult.Success(user)
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Error during login", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to log in")
        }
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getActiveToken()
            if (token != null) {
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
            db.userDao().deleteUser(userId)
            db.conversationDao().deleteUserConversations(userId)
            db.messageDao().deleteUserMessages(userId)
            db.userMemoryDao().clearUserMemories(userId)
            db.userPreferencesDao().deleteUserPreferences(userId)
            db.sessionDao().deleteUserSessions(userId)

            sessionManager.clearSession()
            Log.d("AuthRepository", "Deleted account and all associated data for $userId")
            AuthResult.Success(true)
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Error deleting account $userId", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to delete account")
        }
    }
}
