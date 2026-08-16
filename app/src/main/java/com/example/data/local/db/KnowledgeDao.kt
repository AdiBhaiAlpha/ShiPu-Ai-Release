package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM admin_knowledge ORDER BY updatedAt DESC")
    suspend fun getAllKnowledge(): List<KnowledgeEntity>

    @Query("SELECT * FROM admin_knowledge WHERE status = 'ENABLED'")
    suspend fun getEnabledKnowledge(): List<KnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(knowledge: KnowledgeEntity)

    @Query("DELETE FROM admin_knowledge WHERE knowledgeId = :knowledgeId")
    suspend fun deleteKnowledge(knowledgeId: String)
}
