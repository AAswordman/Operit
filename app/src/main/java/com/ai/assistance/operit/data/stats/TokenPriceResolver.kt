package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.DefaultModelPricingCollect
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.BillingMode

data class ModelPriceSettings(
    val billingMode: BillingMode? = null,
    val currency: PricingCurrency? = null,
    val inputPricePerMillion: Double? = null,
    val cachedInputPricePerMillion: Double? = null,
    val cacheWritePricePerMillion: Double? = null,
    val outputPricePerMillion: Double? = null,
    val pricePerRequest: Double? = null,
    val peakPricingEnabled: Boolean? = null,
    val weekendOffPeakPricingEnabled: Boolean? = null,
    val holidayOffPeakPricingEnabled: Boolean? = null,
    val peakSchedule: List<TokenPeakTimeRange>? = null,
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
) {
    fun hasAnyUserSetting(): Boolean =
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
            peakSchedule != null ||
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
}

data class TokenPriceSettingsSnapshot(
    val providerModels: Map<String, ModelPriceSettings?>,
    val configs: Map<String, ModelPriceSettings>,
) {
    fun settingFor(providerModel: String, configId: String?): ModelPriceSettings? {
        val model = providerModels[providerModel]
        val config = configId?.let { configs[tokenPriceConfigKey(providerModel, it)] }
        if (config == null) return model
        return ModelPriceSettings(
            billingMode = config.billingMode ?: model?.billingMode,
            currency = config.currency ?: model?.currency,
            inputPricePerMillion = config.inputPricePerMillion ?: model?.inputPricePerMillion,
            cachedInputPricePerMillion =
                config.cachedInputPricePerMillion ?: model?.cachedInputPricePerMillion,
            cacheWritePricePerMillion =
                config.cacheWritePricePerMillion ?: model?.cacheWritePricePerMillion,
            outputPricePerMillion = config.outputPricePerMillion ?: model?.outputPricePerMillion,
            pricePerRequest = config.pricePerRequest ?: model?.pricePerRequest,
            peakPricingEnabled = config.peakPricingEnabled ?: model?.peakPricingEnabled,
            weekendOffPeakPricingEnabled =
                config.weekendOffPeakPricingEnabled ?: model?.weekendOffPeakPricingEnabled,
            holidayOffPeakPricingEnabled =
                config.holidayOffPeakPricingEnabled ?: model?.holidayOffPeakPricingEnabled,
            peakSchedule = config.peakSchedule ?: model?.peakSchedule,
            peakInputMultiplier = config.peakInputMultiplier ?: model?.peakInputMultiplier,
            peakCachedInputMultiplier =
                config.peakCachedInputMultiplier ?: model?.peakCachedInputMultiplier,
            peakCacheWriteMultiplier =
                config.peakCacheWriteMultiplier ?: model?.peakCacheWriteMultiplier,
            peakOutputMultiplier = config.peakOutputMultiplier ?: model?.peakOutputMultiplier,
            longContextPricingEnabled =
                config.longContextPricingEnabled ?: model?.longContextPricingEnabled,
            longContextThreshold = config.longContextThreshold ?: model?.longContextThreshold,
            longContextInputMultiplier =
                config.longContextInputMultiplier ?: model?.longContextInputMultiplier,
            longContextCachedInputMultiplier =
                config.longContextCachedInputMultiplier ?: model?.longContextCachedInputMultiplier,
            longContextCacheWriteMultiplier =
                config.longContextCacheWriteMultiplier ?: model?.longContextCacheWriteMultiplier,
            longContextOutputMultiplier =
                config.longContextOutputMultiplier ?: model?.longContextOutputMultiplier,
        )
    }
}

internal fun tokenPriceConfigKey(providerModel: String, configId: String): String =
    "$providerModel\u001f$configId"

data class ResolvedTokenPricing(
    val billingMode: BillingMode,
    val currency: PricingCurrency,
    val inputPricePerMillion: Double,
    val cachedInputPricePerMillion: Double,
    val cacheWritePricePerMillion: Double,
    val outputPricePerMillion: Double,
    val pricePerRequest: Double,
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
    val source: PricingSource,
)

/** 先解析基础价格，随后由 TokenCostCalculator 应用上下文和时间规则。 */
object TokenPriceResolver {
    fun resolve(
        providerModel: String,
        user: ModelPriceSettings?,
    ): ResolvedTokenPricing {
        val defaults = DefaultModelPricingCollect.getDefaultPricing(providerModel)
        return ResolvedTokenPricing(
            billingMode = user?.billingMode ?: defaults.billingMode,
            currency = user?.currency ?: defaults.currency,
            inputPricePerMillion = user?.inputPricePerMillion ?: defaults.inputPricePerMillion,
            cachedInputPricePerMillion =
                user?.cachedInputPricePerMillion
                    ?: user?.inputPricePerMillion
                    ?: defaults.cachedInputPricePerMillion,
            cacheWritePricePerMillion =
                user?.cacheWritePricePerMillion
                    ?: user?.inputPricePerMillion
                    ?: defaults.inputPricePerMillion,
            outputPricePerMillion = user?.outputPricePerMillion ?: defaults.outputPricePerMillion,
            pricePerRequest = user?.pricePerRequest ?: defaults.pricePerRequest,
            peakPricingEnabled = user?.peakPricingEnabled ?: false,
            weekendOffPeakPricingEnabled = user?.weekendOffPeakPricingEnabled ?: true,
            holidayOffPeakPricingEnabled = user?.holidayOffPeakPricingEnabled ?: true,
            peakSchedule = user?.peakSchedule ?: DEFAULT_TOKEN_PEAK_TIME_RANGES,
            peakInputMultiplier = user?.peakInputMultiplier ?: 1.0,
            peakCachedInputMultiplier = user?.peakCachedInputMultiplier ?: 1.0,
            peakCacheWriteMultiplier = user?.peakCacheWriteMultiplier ?: 1.0,
            peakOutputMultiplier = user?.peakOutputMultiplier ?: 1.0,
            longContextPricingEnabled = user?.longContextPricingEnabled ?: false,
            longContextThreshold = user?.longContextThreshold,
            longContextInputMultiplier = user?.longContextInputMultiplier ?: 1.0,
            longContextCachedInputMultiplier = user?.longContextCachedInputMultiplier ?: 1.0,
            longContextCacheWriteMultiplier = user?.longContextCacheWriteMultiplier ?: 1.0,
            longContextOutputMultiplier = user?.longContextOutputMultiplier ?: 1.0,
            source = if (user?.hasAnyUserSetting() == true) PricingSource.USER else PricingSource.BUILT_IN,
        )
    }
}
