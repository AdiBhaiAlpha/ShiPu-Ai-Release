package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.UserPreferences

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val userId: String,
    val defaultModel: String,
    val temperature: Float,
    val maxTokens: Int,
    val autoMemoryEnabled: Boolean,
    val customSystemPrompt: String,
    val theme: String,
    val updatedAt: Long
) {
    fun toUserPreferences(): UserPreferences = UserPreferences(
        userId = userId,
        defaultModel = defaultModel,
        temperature = temperature,
        maxTokens = maxTokens,
        autoMemoryEnabled = autoMemoryEnabled,
        customSystemPrompt = customSystemPrompt,
        theme = theme,
        updatedAt = updatedAt
    )

    companion object {
        fun fromUserPreferences(prefs: UserPreferences): UserPreferencesEntity = UserPreferencesEntity(
            userId = prefs.userId,
            defaultModel = prefs.defaultModel,
            temperature = prefs.temperature,
            maxTokens = prefs.maxTokens,
            autoMemoryEnabled = prefs.autoMemoryEnabled,
            customSystemPrompt = prefs.customSystemPrompt,
            theme = prefs.theme,
            updatedAt = prefs.updatedAt
        )
    }
}
