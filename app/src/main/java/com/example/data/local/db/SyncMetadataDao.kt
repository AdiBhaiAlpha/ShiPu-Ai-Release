package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE userId = :userId LIMIT 1")
    suspend fun getMetadata(userId: String): SyncMetadataEntity?

    @Query("UPDATE sync_metadata SET isInitialRestoreCompleted = :completed WHERE userId = :userId")
    suspend fun setInitialRestoreCompleted(userId: String, completed: Boolean)

    @Query("DELETE FROM sync_metadata WHERE userId = :userId")
    suspend fun clearMetadata(userId: String)
}
