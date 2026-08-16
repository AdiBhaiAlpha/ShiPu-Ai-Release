package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.ShiPuAiApplication

@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        UserMemoryEntity::class,
        UserPreferencesEntity::class,
        SessionEntity::class,
        SystemPromptEntity::class,
        KnowledgeEntity::class,
        AdminConfigEntity::class,
        AdminAuditLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun sessionDao(): SessionDao
    abstract fun systemPromptDao(): SystemPromptDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun adminConfigDao(): AdminConfigDao
    abstract fun adminAuditLogDao(): AdminAuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context? = null): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val ctx = context?.applicationContext
                    ?: (if (ShiPuAiApplication.isInitialized) ShiPuAiApplication.instance.applicationContext else null)
                    ?: throw IllegalStateException("AppDatabase initialized before Application Context was available")
                val instance = Room.databaseBuilder(
                    ctx,
                    AppDatabase::class.java,
                    "shipu_ai_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
