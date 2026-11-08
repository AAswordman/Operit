package com.ai.assistance.operit.data.model

import androidx.room.Entity
/** User-owned price settings for one provider/model identity. */
@Entity(
    tableName = "token_stats_models",
    primaryKeys = ["configId", "provider", "model"],
)
data class TokenStatsModelEntity(
    /** Empty means provider/model-wide pricing and the configuration-unscoped identity. */
    val configId: String,
    val provider: String,
    val model: String,
    val billingMode: String? = null,
    val currency: String? = null,
    val inputPricePerMillion: Double? = null,
    val cachedInputPricePerMillion: Double? = null,
    val cacheWritePricePerMillion: Double? = null,
    val outputPricePerMillion: Double? = null,
    val pricePerRequest: Double? = null,
    val peakPricingEnabled: Boolean? = null,
    val weekendOffPeakPricingEnabled: Boolean? = null,
    val holidayOffPeakPricingEnabled: Boolean? = null,
    val peakScheduleJson: String? = null,
    val peakInputMultiplier: Double? = null,
    val peakCachedInputMultiplier: Double? = null,
    val peakCacheWriteMultiplier: Double? = null,
    val peakOutputMultiplier: Double? = null,
    val longContextPricingEnabled: Boolean? = null,
    val longContextThreshold: Long? = null,
    val longContextInputMultiplier: Double? = null,
    val longContextCachedInputMultiplier: Double? = null,
    val longContextCacheWriteMultiplier: Double? = null,
    val longContextOutputMultiplier: Double? = null,
)
