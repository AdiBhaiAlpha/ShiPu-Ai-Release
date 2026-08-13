package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE token = :token")
    suspend fun deleteSession(token: String)

    @Query("DELETE FROM sessions WHERE userId = :userId")
    suspend fun deleteUserSessions(userId: String)
}
