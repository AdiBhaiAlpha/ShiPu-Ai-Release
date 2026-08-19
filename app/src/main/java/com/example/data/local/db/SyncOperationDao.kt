package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncOperationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: SyncOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperations(operations: List<SyncOperationEntity>)

    @Query("SELECT * FROM sync_operations WHERE userId = :userId AND status != 'COMPLETED' ORDER BY createdAt ASC")
    suspend fun getPendingOperations(userId: String): List<SyncOperationEntity>

    @Query("SELECT COUNT(*) FROM sync_operations WHERE userId = :userId AND status != 'COMPLETED'")
    suspend fun getPendingCount(userId: String): Int

    @Query("UPDATE sync_operations SET status = :status, retryCount = :retryCount WHERE operationId = :operationId")
    suspend fun updateOperationStatus(operationId: String, status: String, retryCount: Int)

    @Query("DELETE FROM sync_operations WHERE operationId = :operationId")
    suspend fun deleteOperation(operationId: String)

    @Query("DELETE FROM sync_operations WHERE operationId IN (:operationIds)")
    suspend fun deleteOperations(operationIds: List<String>)

    @Query("DELETE FROM sync_operations WHERE userId = :userId")
    suspend fun clearUserOperations(userId: String)
}
