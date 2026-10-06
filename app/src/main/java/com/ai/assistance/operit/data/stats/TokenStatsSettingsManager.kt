package com.ai.assistance.operit.data.stats

import android.content.Context
import com.ai.assistance.operit.data.collects.DefaultModelPricingCollect
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.model.TokenStatsModelEntity
import com.ai.assistance.operit.data.model.normalizeProviderTypeId

enum class TokenStatsPriceScope { PROVIDER_MODEL, CONFIG }

data class TokenStatsPriceDraft(
    val scope: TokenStatsPriceScope,
    val provider: String,
    val model: String,
    val configId: String? = null,
    val billingMode: BillingMode,
    val currency: PricingCurrency,
    val inputPricePerMillion: Double? = null,
    val cachedInputPricePerMillion: Double? = null,
    val cacheWritePricePerMillion: Double? = null,
    val outputPricePerMillion: Double? = null,
    val pricePerRequest: Double? = null,
    val peakPricingEnabled: Boolean = false,
    val weekendOffPeakPricingEnabled: Boolean = true,
    val holidayOffPeakPricingEnabled: Boolean = true,
    val peakSchedule: List<TokenPeakTimeRange> = DEFAULT_TOKEN_PEAK_TIME_RANGES,
    val peakInputMultiplier: Double = 1.0,
    val peakCachedInputMultiplier: Double = 1.0,
    val peakCacheWriteMultiplier: Double = 1.0,
    val peakOutputMultiplier: Double = 1.0,
    val longContextPricingEnabled: Boolean = false,
    val longContextThreshold: Long? = null,
    val longContextInputMultiplier: Double = 1.0,
    val longContextCachedInputMultiplier: Double = 1.0,
    val longContextCacheWriteMultiplier: Double = 1.0,
    val longContextOutputMultiplier: Double = 1.0,
)

data class TokenStatsPriceSetting(
    val scope: TokenStatsPriceScope,
    val providerModel: String,
    val provider: String,
    val model: String,
    val configId: String?,
    val billingMode: BillingMode,
    val currency: PricingCurrency,
    val inputPricePerMillion: Double?,
    val cachedInputPricePerMillion: Double?,
    val cacheWritePricePerMillion: Double?,
    val outputPricePerMillion: Double?,
    val pricePerRequest: Double?,
    val peakPricingEnabled: Boolean?,
    val weekendOffPeakPricingEnabled: Boolean?,
    val holidayOffPeakPricingEnabled: Boolean?,
    val peakSchedule: List<TokenPeakTimeRange>?,
    val peakInputMultiplier: Double?,
    val peakCachedInputMultiplier: Double?,
    val peakCacheWriteMultiplier: Double?,
    val peakOutputMultiplier: Double?,
    val longContextPricingEnabled: Boolean?,
    val longContextThreshold: Long?,
    val longContextInputMultiplier: Double?,
    val longContextCachedInputMultiplier: Double?,
    val longContextCacheWriteMultiplier: Double?,
    val longContextOutputMultiplier: Double?,
)

class TokenStatsSettingsManager(context: Context) {
    private val appContext = context.applicationContext
    private val repository = TokenUsageRepository.getInstance(appContext)

    fun validatePriceValue(name: String, value: Double?): Double? {
        if (value == null) return null
        require(value.isFinite() && value >= 0.0) {
            "$name must be non-negative and finite, got $value"
        }
        return value
    }

    private fun validateMultiplier(name: String, value: Double): Double {
        require(value.isFinite() && value >= 0.0) {
            "$name must be non-negative and finite, got $value"
        }
        return value
    }

    private fun validateThreshold(enabled: Boolean, value: Long?): Long? {
        if (value == null) {
            require(!enabled) { "long context threshold is required when pricing is enabled" }
            return null
        }
        require(value > 0L) { "long context threshold must be positive" }
        return value
    }

    private fun encodePeakSchedule(
        enabled: Boolean,
        schedule: List<TokenPeakTimeRange>,
    ): String {
        require(!enabled || schedule.isNotEmpty()) {
            "peak schedule is required when pricing is enabled"
        }
        return TokenPricingRules.encodePeakTimeRanges(schedule)
    }

    suspend fun savePrice(draft: TokenStatsPriceDraft) {
        val provider = normalizeProviderTypeId(draft.provider)
        val model = draft.model.trim()
        val configId = draft.configId?.trim().orEmpty()
        require(provider.isNotEmpty()) { "provider must not be blank" }
        require(model.isNotEmpty()) { "model must not be blank" }
        require(draft.scope != TokenStatsPriceScope.CONFIG || configId.isNotEmpty()) {
            "configId must not be blank for config pricing"
        }
        val storageConfigId =
            if (draft.scope == TokenStatsPriceScope.PROVIDER_MODEL) "" else configId
        repository.withDao { dao ->
            val current =
                dao.getStatsModel(storageConfigId, provider, model)
                    ?: TokenStatsModelEntity(storageConfigId, provider, model)
            dao.upsertStatsModel(
                current.copy(
                    billingMode = draft.billingMode.name,
                    currency = draft.currency.name,
                    inputPricePerMillion =
                        if (draft.billingMode == BillingMode.TOKEN) {
                            validatePriceValue("inputPrice", draft.inputPricePerMillion)
                        } else {
                            null
                        },
                    cachedInputPricePerMillion =
                        if (draft.billingMode == BillingMode.TOKEN) {
                            validatePriceValue("cachedInputPrice", draft.cachedInputPricePerMillion)
                        } else {
                            null
                        },
                    cacheWritePricePerMillion =
                        if (draft.billingMode == BillingMode.TOKEN) {
                            validatePriceValue("cacheWritePrice", draft.cacheWritePricePerMillion)
                        } else {
                            null
                        },
                    outputPricePerMillion =
                        if (draft.billingMode == BillingMode.TOKEN) {
                            validatePriceValue("outputPrice", draft.outputPricePerMillion)
                        } else {
                            null
                        },
                    pricePerRequest =
                        if (draft.billingMode == BillingMode.COUNT) {
                            validatePriceValue("pricePerRequest", draft.pricePerRequest)
                        } else {
                            null
                        },
                    peakPricingEnabled = draft.peakPricingEnabled,
                    weekendOffPeakPricingEnabled = draft.weekendOffPeakPricingEnabled,
                    holidayOffPeakPricingEnabled = draft.holidayOffPeakPricingEnabled,
                    peakScheduleJson = encodePeakSchedule(
                        draft.peakPricingEnabled,
                        draft.peakSchedule,
                    ),
                    peakInputMultiplier = validateMultiplier(
                        "peakInputMultiplier",
                        draft.peakInputMultiplier,
                    ),
                    peakCachedInputMultiplier = validateMultiplier(
                        "peakCachedInputMultiplier",
                        draft.peakCachedInputMultiplier,
                    ),
                    peakCacheWriteMultiplier = validateMultiplier(
                        "peakCacheWriteMultiplier",
                        draft.peakCacheWriteMultiplier,
                    ),
                    peakOutputMultiplier = validateMultiplier(
                        "peakOutputMultiplier",
                        draft.peakOutputMultiplier,
                    ),
                    longContextPricingEnabled = draft.longContextPricingEnabled,
                    longContextThreshold = validateThreshold(
                        draft.longContextPricingEnabled,
                        draft.longContextThreshold,
                    ),
                    longContextInputMultiplier = validateMultiplier(
                        "longContextInputMultiplier",
                        draft.longContextInputMultiplier,
                    ),
                    longContextCachedInputMultiplier = validateMultiplier(
                        "longContextCachedInputMultiplier",
                        draft.longContextCachedInputMultiplier,
                    ),
                    longContextCacheWriteMultiplier = validateMultiplier(
                        "longContextCacheWriteMultiplier",
                        draft.longContextCacheWriteMultiplier,
                    ),
                    longContextOutputMultiplier = validateMultiplier(
                        "longContextOutputMultiplier",
                        draft.longContextOutputMultiplier,
                    ),
                )
            )
        }
    }

    suspend fun allPriceSettings(): List<TokenStatsPriceSetting> {
        return repository.withDao { dao ->
            dao.getAllStatsModels()
                .filter(TokenStatsModelEntity::hasPriceSetting)
                .map(TokenStatsModelEntity::toPriceSetting)
                .sortedWith(
                    compareBy(
                        { it.providerModel.lowercase() },
                        { it.scope.ordinal },
                        { it.configId.orEmpty().lowercase() },
                    )
                )
        }
    }

    suspend fun restoreBuiltInPrice(providerModel: String) {
        val (provider, model) = splitProviderModel(providerModel)
        repository.withDao { dao ->
            dao.clearPricing("", provider, model)
            dao.deleteEmptyStatsModels()
        }
    }

    suspend fun resetConfigPrice(providerModel: String, configId: String) {
        require(configId.isNotBlank()) { "configId must not be blank" }
        val (provider, model) = splitProviderModel(providerModel)
        repository.withDao { dao ->
            dao.clearPricing(configId, provider, model)
            dao.deleteEmptyStatsModels()
        }
    }

    private fun splitProviderModel(providerModel: String): Pair<String, String> {
        val separator = providerModel.indexOf(':')
        require(separator > 0 && separator < providerModel.lastIndex) {
            "provider:model is required"
        }
        return normalizeProviderTypeId(providerModel.substring(0, separator)) to
            providerModel.substring(separator + 1)
    }
}

internal fun tokenStatsPriceScopeForConfigId(configId: String): TokenStatsPriceScope =
    if (configId.isBlank()) TokenStatsPriceScope.PROVIDER_MODEL else TokenStatsPriceScope.CONFIG

internal fun TokenStatsModelEntity.hasPriceSetting(): Boolean =
    billingMode != null ||
        currency != null ||
        inputPricePerMillion != null ||
        cachedInputPricePerMillion != null ||
        cacheWritePricePerMillion != null ||
        outputPricePerMillion != null ||
        pricePerRequest != null ||
        peakPricingEnabled != null ||
        weekendOffPeakPricingEnabled != null ||
        holidayOffPeakPricingEnabled != null ||
        peakScheduleJson != null ||
        peakInputMultiplier != null ||
        peakCachedInputMultiplier != null ||
        peakCacheWriteMultiplier != null ||
        peakOutputMultiplier != null ||
        longContextPricingEnabled != null ||
        longContextThreshold != null ||
        longContextInputMultiplier != null ||
        longContextCachedInputMultiplier != null ||
        longContextCacheWriteMultiplier != null ||
        longContextOutputMultiplier != null

internal fun TokenStatsModelEntity.toModelPriceSettings(): ModelPriceSettings =
    ModelPriceSettings(
        billingMode = billingMode?.let { BillingMode.valueOf(it) },
        currency = currency?.let { PricingCurrency.valueOf(it) },
        inputPricePerMillion = inputPricePerMillion,
        cachedInputPricePerMillion = cachedInputPricePerMillion,
        cacheWritePricePerMillion = cacheWritePricePerMillion,
        outputPricePerMillion = outputPricePerMillion,
        pricePerRequest = pricePerRequest,
        peakPricingEnabled = peakPricingEnabled,
        weekendOffPeakPricingEnabled = weekendOffPeakPricingEnabled,
        holidayOffPeakPricingEnabled = holidayOffPeakPricingEnabled,
        peakSchedule = peakScheduleJson?.let(TokenPricingRules::decodePeakTimeRanges),
        peakInputMultiplier = peakInputMultiplier,
        peakCachedInputMultiplier = peakCachedInputMultiplier,
        peakCacheWriteMultiplier = peakCacheWriteMultiplier,
        peakOutputMultiplier = peakOutputMultiplier,
        longContextPricingEnabled = longContextPricingEnabled,
        longContextThreshold = longContextThreshold,
        longContextInputMultiplier = longContextInputMultiplier,
        longContextCachedInputMultiplier = longContextCachedInputMultiplier,
        longContextCacheWriteMultiplier = longContextCacheWriteMultiplier,
        longContextOutputMultiplier = longContextOutputMultiplier,
    )

private fun TokenStatsModelEntity.toPriceSetting(): TokenStatsPriceSetting {
    val providerModel = "$provider:$model"
    val defaults = DefaultModelPricingCollect.getDefaultPricing(providerModel)
    return TokenStatsPriceSetting(
        scope = if (configId.isEmpty()) TokenStatsPriceScope.PROVIDER_MODEL else TokenStatsPriceScope.CONFIG,
        providerModel = providerModel,
        provider = provider,
        model = model,
        configId = configId.ifEmpty { null },
        billingMode = billingMode?.let { BillingMode.valueOf(it) } ?: defaults.billingMode,
        currency = currency?.let { PricingCurrency.valueOf(it) } ?: defaults.currency,
        inputPricePerMillion = inputPricePerMillion,
        cachedInputPricePerMillion = cachedInputPricePerMillion,
        cacheWritePricePerMillion = cacheWritePricePerMillion,
        outputPricePerMillion = outputPricePerMillion,
        pricePerRequest = pricePerRequest,
        peakPricingEnabled = peakPricingEnabled,
        weekendOffPeakPricingEnabled = weekendOffPeakPricingEnabled,
        holidayOffPeakPricingEnabled = holidayOffPeakPricingEnabled,
        peakSchedule = peakScheduleJson?.let(TokenPricingRules::decodePeakTimeRanges),
        peakInputMultiplier = peakInputMultiplier,
        peakCachedInputMultiplier = peakCachedInputMultiplier,
        peakCacheWriteMultiplier = peakCacheWriteMultiplier,
        peakOutputMultiplier = peakOutputMultiplier,
        longContextPricingEnabled = longContextPricingEnabled,
        longContextThreshold = longContextThreshold,
        longContextInputMultiplier = longContextInputMultiplier,
        longContextCachedInputMultiplier = longContextCachedInputMultiplier,
        longContextCacheWriteMultiplier = longContextCacheWriteMultiplier,
        longContextOutputMultiplier = longContextOutputMultiplier,
    )
}
