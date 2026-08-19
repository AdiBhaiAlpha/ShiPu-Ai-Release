package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SessionManager
import com.example.data.local.db.AppDatabase
import com.example.data.model.UserPreferences
import com.example.data.remote.CloudSyncService
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthResult
import com.example.data.repository.ChatRepository
import com.example.data.repository.MemoryRepository
import com.example.data.sync.CloudSyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudSyncEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var sessionManager: SessionManager
    private lateinit var cloudService: CloudSyncService
    private lateinit var syncEngine: CloudSyncEngine
    private lateinit var authRepository: AuthRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var adminRepository: AdminRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionManager = SessionManager(context)
        cloudService = CloudSyncService.getInstance()
        syncEngine = CloudSyncEngine(context, cloudService)
        authRepository = AuthRepository(sessionManager, context)
        chatRepository = ChatRepository(context = context)
        memoryRepository = MemoryRepository(context = context)
        adminRepository = AdminRepository(context = context)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testUserSignUpAndCloudInitialization() = runBlocking {
        val email = "cloud_user_${System.currentTimeMillis()}@example.com"
        val password = "SecurePassword123!"
        val name = "Cloud Tester"

        val authResult = authRepository.signUp(email, password, name)
        assertTrue("Sign up should succeed", authResult is AuthResult.Success)

        val user = (authResult as AuthResult.Success).data
        assertNotNull(user.userId)
        assertEquals(email, user.email)

        // Verify session saved
        val token = sessionManager.getActiveToken()
        assertNotNull(token)

        // Verify cloud manifest exists
        val manifest = cloudService.getCloudManifest(user.userId, token!!)
        assertNotNull(manifest)
        assertEquals(user.userId, manifest.userId)
    }

    @Test
    fun testCompleteUninstallAndReinstallRestoration() = runBlocking {
        // Step 1: Create user on Device 1
        val email = "restore_test_${System.currentTimeMillis()}@example.com"
        val password = "RestorePass123!"
        val name = "Restore User"

        val signUpResult = authRepository.signUp(email, password, name)
        assertTrue(signUpResult is AuthResult.Success)
        val user = (signUpResult as AuthResult.Success).data
        val token = sessionManager.getActiveToken()!!

        // Step 2: Create conversation and messages
        val conv = chatRepository.createConversation(user.userId, "Project Roadmap")
        val msg1 = com.example.data.model.Message(
            messageId = "msg_test_001",
            conversationId = conv.conversationId,
            userId = user.userId,
            role = "user",
            content = "How should we structure MongoDB cloud sync?",
            createdAt = System.currentTimeMillis()
        )
        chatRepository.saveMessage(msg1)

        val msg2 = com.example.data.model.Message(
            messageId = "msg_test_002",
            conversationId = conv.conversationId,
            userId = user.userId,
            role = "assistant",
            content = "We use an outbox pattern with bidirectional delta sync.",
            createdAt = System.currentTimeMillis() + 100
        )
        chatRepository.saveMessage(msg2)

        // Step 3: Add user memories and custom preferences
        memoryRepository.addMemory(user.userId, "User is lead architect of ShiPu AI", "personal")
        chatRepository.updateUserPreferences(
            UserPreferences(
                userId = user.userId,
                theme = "dark",
                defaultModel = "openrouter/auto",
                customSystemPrompt = "Always provide precise code examples."
            )
        )

        // Step 4: Synchronize to MongoDB Cloud
        val syncResult = syncEngine.syncUserData(user.userId, token)
        assertTrue("Sync should succeed", syncResult)

        // Step 5: SIMULATE COMPLETE APP UNINSTALL & REINSTALL
        // Wipe local database completely and clear local session
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val freshDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionManager.clearSession()

        // Verify fresh DB has zero data
        assertEquals(0, freshDb.conversationDao().getConversationsForUser(user.userId).size)
        assertEquals(0, freshDb.userMemoryDao().getMemoriesForUser(user.userId).size)

        // Step 6: Log in with same email and password on fresh reinstall
        val loginResult = authRepository.login(email, password)
        assertTrue("Login on fresh reinstall should succeed", loginResult is AuthResult.Success)

        // Step 7: Verify complete user cloud state is restored from MongoDB
        val cloudState = cloudService.pullUserCloudState(user.userId, token)
        assertEquals(1, cloudState.conversations.size)
        assertEquals("Project Roadmap", cloudState.conversations.first().title)
        assertEquals(2, cloudState.messages.size)
        assertEquals(1, cloudState.memories.size)
        assertEquals("User is lead architect of ShiPu AI", cloudState.memories.first().fact)
        assertEquals("Always provide precise code examples.", cloudState.preferences.customSystemPrompt)

        freshDb.close()
    }

    @Test
    fun testMultiDeviceCloudSync() = runBlocking {
        // Device A signs up
        val email = "multidevice_${System.currentTimeMillis()}@example.com"
        val password = "MultiPassword123!"
        val name = "Multi Device User"

        val signUpResult = authRepository.signUp(email, password, name)
        val user = (signUpResult as AuthResult.Success).data
        val token = sessionManager.getActiveToken()!!

        // Device A creates a conversation
        val conv = chatRepository.createConversation(user.userId, "Device A Conversation")
        syncEngine.syncUserData(user.userId, token)

        // Device B logs in
        val deviceBLogin = authRepository.login(email, password)
        assertTrue(deviceBLogin is AuthResult.Success)

        val deviceBState = cloudService.pullUserCloudState(user.userId, token)
        assertEquals(1, deviceBState.conversations.size)
        assertEquals("Device A Conversation", deviceBState.conversations.first().title)
    }

    @Test
    fun testUserDataIsolationSecurity() = runBlocking {
        // Create User 1
        val user1Result = authRepository.signUp("user1_${System.currentTimeMillis()}@example.com", "Pass123456!", "User One")
        val user1 = (user1Result as AuthResult.Success).data
        val token1 = sessionManager.getActiveToken()!!

        // Create User 2
        val user2Result = authRepository.signUp("user2_${System.currentTimeMillis()}@example.com", "Pass123456!", "User Two")
        val user2 = (user2Result as AuthResult.Success).data
        val token2 = sessionManager.getActiveToken()!!

        // User 1 creates confidential conversation
        chatRepository.createConversation(user1.userId, "User 1 Secret Notes")
        syncEngine.syncUserData(user1.userId, token1)

        // Verify User 2 attempting to pull User 1's cloud state with User 2's token throws SecurityException
        var securityExceptionThrown = false
        try {
            cloudService.pullUserCloudState(user1.userId, token2)
        } catch (e: SecurityException) {
            securityExceptionThrown = true
        }
        assertTrue("User 2 must NOT be able to access User 1 data", securityExceptionThrown)
    }

    @Test
    fun testAdminGlobalPromptAndKnowledgePropagation() = runBlocking {
        // Super admin signs up
        val adminEmail = "chitronbhattacharjee@gmail.com"
        val adminPass = "AdminPass123!"
        val adminName = "Super Admin"

        val adminResult = authRepository.signUp(adminEmail, adminPass, adminName)
        val adminUser = (adminResult as AuthResult.Success).data
        val adminToken = sessionManager.getActiveToken()!!

        // Regular user signs up
        val userResult = authRepository.signUp("regular_user_${System.currentTimeMillis()}@example.com", "UserPass123!", "Regular User")
        val regularUser = (userResult as AuthResult.Success).data
        val userToken = sessionManager.getActiveToken()!!

        // Admin updates global system prompt and knowledge
        val newPromptContent = "Updated Global AI Persona with strict ethics and enhanced reasoning."
        val promptSave = adminRepository.saveSystemPrompt(adminUser.userId, adminEmail, newPromptContent)
        assertTrue("Admin prompt update should succeed", promptSave)

        val knowledgeSave = adminRepository.saveKnowledge(
            userId = adminUser.userId,
            userEmail = adminEmail,
            knowledgeId = "knw_global_test",
            title = "Global Policies",
            content = "All requests are processed securely.",
            category = "Security",
            tags = "security,policies",
            status = "ENABLED"
        )
        assertTrue("Admin knowledge save should succeed", knowledgeSave)

        // Regular user synchronizes and receives global admin updates
        val userSyncResult = syncEngine.syncUserData(regularUser.userId, userToken)
        assertTrue("User sync should succeed", userSyncResult)

        val globalCloud = cloudService.getGlobalCloudState()
        assertEquals(newPromptContent, globalCloud.systemPrompt.content)
        assertTrue(globalCloud.knowledgeList.any { it.title == "Global Policies" })
    }
}
