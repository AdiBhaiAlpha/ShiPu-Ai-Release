package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserPreferencesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreferences(prefs: UserPreferencesEntity)

    @Query("SELECT * FROM user_preferences WHERE userId = :userId LIMIT 1")
    suspend fun getPreferencesForUser(userId: String): UserPreferencesEntity?

    @Query("DELETE FROM user_preferences WHERE userId = :userId")
    suspend fun deleteUserPreferences(userId: String)
}
