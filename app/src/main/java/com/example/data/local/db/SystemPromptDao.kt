package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SystemPromptDao {
    @Query("SELECT * FROM system_prompts WHERE promptId = 'sys_prompt_current' LIMIT 1")
    suspend fun getCurrentPrompt(): SystemPromptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePrompt(prompt: SystemPromptEntity)
}
