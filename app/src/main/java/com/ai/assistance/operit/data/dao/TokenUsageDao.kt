package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.assistance.operit.data.model.TokenStatsModelEntity
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity

data class TokenUsageModelAggregateRow(
    val provider: String,
    val model: String,
    val configId: String,
    val requests: Long,
    val uncachedInputTokens: Long,
    val uncachedInputKnown: Long,
    val cachedInputTokens: Long,
    val cachedInputKnown: Long,
    val cacheWriteTokens: Long,
    val cacheWriteKnown: Long,
    val totalInputTokens: Long,
    val totalInputKnown: Long,
    val outputTokens: Long,
    val outputKnown: Long,
) {
    val providerModel: String
        get() = "$provider:$model"
}

data class TokenUsageActivityDayRow(
    val localDate: String,
    val tokens: Long,
)

@Dao
abstract class TokenUsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRecord(record: TokenUsageRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRecords(records: List<TokenUsageRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertStatsModel(model: TokenStatsModelEntity)

    @Query(
        """
        SELECT * FROM token_stats_models
        WHERE configId = :configId AND provider = :provider AND model = :model
        """
    )
    abstract suspend fun getStatsModel(
        configId: String,
        provider: String,
        model: String,
    ): TokenStatsModelEntity?

    @Query("SELECT * FROM token_stats_models ORDER BY provider, model, configId")
    abstract suspend fun getAllStatsModels(): List<TokenStatsModelEntity>

    @Query(
        """
        UPDATE token_stats_models
        SET billingMode = NULL,
            currency = NULL,
            inputPricePerMillion = NULL,
            cachedInputPricePerMillion = NULL,
            cacheWritePricePerMillion = NULL,
            outputPricePerMillion = NULL,
            pricePerRequest = NULL
        WHERE configId = :configId AND provider = :provider AND model = :model
        """
    )
    abstract suspend fun clearPricing(configId: String, provider: String, model: String): Int

    @Query(
        """
        DELETE FROM token_stats_models
        WHERE billingMode IS NULL
            AND currency IS NULL
            AND inputPricePerMillion IS NULL
            AND cachedInputPricePerMillion IS NULL
            AND cacheWritePricePerMillion IS NULL
            AND outputPricePerMillion IS NULL
            AND pricePerRequest IS NULL
        """
    )
    abstract suspend fun deleteEmptyStatsModels(): Int

    @Query(
        """
        SELECT
            provider AS provider,
            model AS model,
            configId AS configId,
            COALESCE(SUM(requestCount), 0) AS requests,
            COALESCE(SUM(uncachedInputTokens), 0) AS uncachedInputTokens,
            COALESCE(SUM(CASE WHEN uncachedInputTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS uncachedInputKnown,
            COALESCE(SUM(cachedInputTokens), 0) AS cachedInputTokens,
            COALESCE(SUM(CASE WHEN cachedInputTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS cachedInputKnown,
            COALESCE(SUM(cacheWriteTokens), 0) AS cacheWriteTokens,
            COALESCE(SUM(CASE WHEN cacheWriteTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS cacheWriteKnown,
            COALESCE(SUM(totalInputTokens), 0) AS totalInputTokens,
            COALESCE(SUM(CASE WHEN totalInputTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS totalInputKnown,
            COALESCE(SUM(outputTokens), 0) AS outputTokens,
            COALESCE(SUM(CASE WHEN outputTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS outputKnown
        FROM token_usage_records
        WHERE (:allModels OR (provider || ':' || model) IN (:providerModels))
        GROUP BY provider, model, configId
        ORDER BY provider, model, configId
        """
    )
    abstract suspend fun aggregateModelsForLifetime(
        providerModels: List<String>,
        allModels: Boolean,
    ): List<TokenUsageModelAggregateRow>

    @Query(
        """
        SELECT MIN(occurredAtMs)
        FROM token_usage_records
        WHERE occurredAtMs IS NOT NULL
            AND (:allModels OR (provider || ':' || model) IN (:providerModels))
        """
    )
    abstract suspend fun getEarliestOccurredAtMs(
        providerModels: List<String>,
        allModels: Boolean,
    ): Long?

    @Query(
        """
        SELECT
            provider AS provider,
            model AS model,
            configId AS configId,
            COALESCE(SUM(requestCount), 0) AS requests,
            COALESCE(SUM(uncachedInputTokens), 0) AS uncachedInputTokens,
            COALESCE(SUM(CASE WHEN uncachedInputTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS uncachedInputKnown,
            COALESCE(SUM(cachedInputTokens), 0) AS cachedInputTokens,
            COALESCE(SUM(CASE WHEN cachedInputTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS cachedInputKnown,
            COALESCE(SUM(cacheWriteTokens), 0) AS cacheWriteTokens,
            COALESCE(SUM(CASE WHEN cacheWriteTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS cacheWriteKnown,
            COALESCE(SUM(totalInputTokens), 0) AS totalInputTokens,
            COALESCE(SUM(CASE WHEN totalInputTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS totalInputKnown,
            COALESCE(SUM(outputTokens), 0) AS outputTokens,
            COALESCE(SUM(CASE WHEN outputTokens IS NOT NULL THEN requestCount ELSE 0 END), 0) AS outputKnown
        FROM token_usage_records
        WHERE occurredAtMs >= :startMs AND occurredAtMs < :endMs
            AND (:allModels OR (provider || ':' || model) IN (:providerModels))
        GROUP BY provider, model, configId
        ORDER BY provider, model, configId
        """
    )
    abstract suspend fun aggregateModelsInRange(
        startMs: Long,
        endMs: Long,
        providerModels: List<String>,
        allModels: Boolean,
    ): List<TokenUsageModelAggregateRow>

    @Query(
        """
        SELECT
            strftime('%Y-%m-%d', occurredAtMs / 1000, 'unixepoch', 'localtime') AS localDate,
            COALESCE(SUM(
                COALESCE(
                    totalInputTokens,
                    CASE
                        WHEN uncachedInputTokens IS NOT NULL
                            AND cachedInputTokens IS NOT NULL
                            AND cacheWriteTokens IS NOT NULL
                        THEN uncachedInputTokens + cachedInputTokens + cacheWriteTokens
                    END,
                    0
                ) + COALESCE(outputTokens, 0)
            ), 0) AS tokens
        FROM token_usage_records
        WHERE occurredAtMs >= :startMs AND occurredAtMs < :endMs
            AND (:allModels OR (provider || ':' || model) IN (:providerModels))
        GROUP BY localDate, configId, provider, model
        ORDER BY localDate, provider, model, configId
        """
    )
    abstract suspend fun getActivityDaysInRange(
        startMs: Long,
        endMs: Long,
        providerModels: List<String>,
        allModels: Boolean,
    ): List<TokenUsageActivityDayRow>
}
