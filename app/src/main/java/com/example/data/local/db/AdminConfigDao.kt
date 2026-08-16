package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AdminConfigDao {
    @Query("SELECT * FROM admin_configs WHERE configKey = :key LIMIT 1")
    suspend fun getConfig(key: String): AdminConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: AdminConfigEntity)

    @Query("DELETE FROM admin_configs WHERE configKey = :key")
    suspend fun deleteConfig(key: String)
}
