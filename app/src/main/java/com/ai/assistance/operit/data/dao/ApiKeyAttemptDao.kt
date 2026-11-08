package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.assistance.operit.data.model.ApiKeyAttemptRecordEntity

@Dao
interface ApiKeyAttemptDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: ApiKeyAttemptRecordEntity): Long

    @Query(
        """
        SELECT * FROM api_key_attempt_records
        WHERE configId = :configId AND occurredAtMs >= :sinceMs
        ORDER BY occurredAtMs DESC
        LIMIT :limit
        """
    )
    suspend fun recentForConfig(
        configId: String,
        sinceMs: Long,
        limit: Int,
    ): List<ApiKeyAttemptRecordEntity>
}