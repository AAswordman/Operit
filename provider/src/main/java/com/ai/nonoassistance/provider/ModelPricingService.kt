package com.ai.nonoassistance.provider

/**
 * ModelPricingService — fetches and caches model pricing data.
 *
 * Pricing is fetched from remote JSON (via [PricingFetcher]), cached locally,
 * with fallback to stale cache if remote fetch fails.
 *
 * Usage:
 * ```kotlin
 * val pricingService = ModelPricingService()
 * val pricing = pricingService.getPricing("deepseek", "deepseek-chat")
 * // pricing?.inputPerMillionTokens → 0.14
 * ```
 */
class ModelPricingService(
    private val fetcher: PricingFetcher = PricingFetcher()
) {

    /**
     * Get pricing for a model from a specific provider.
     * Returns cached/fetched pricing or null if unavailable.
     */
    suspend fun getPricing(providerId: String, modelId: String): ModelPricing? {
        return fetcher.getPricing(providerId, modelId)
    }

    /**
     * Get all available model pricing for a given provider.
     */
    suspend fun getProviderPricing(providerId: String): Map<String, ModelPricing> {
        val config = fetcher.fetchPricing()
        val providerEntry = config.providers[providerId] ?: return emptyMap()

        return providerEntry.models.mapValues { (_, entry) ->
            ModelPricing(
                inputPerMillionTokens = entry.inputPerMillionTokens,
                outputPerMillionTokens = entry.outputPerMillionTokens,
                currency = entry.currency,
                lastUpdated = config.lastUpdated
            )
        }
    }

    /**
     * Calculate estimated cost for a request based on pricing.
     * Returns cost in the pricing currency (usually USD).
     */
    fun estimateCost(
        pricing: ModelPricing,
        inputTokens: Long,
        outputTokens: Long
    ): Double {
        val inputCost = (inputTokens.toDouble() / 1_000_000.0) * pricing.inputPerMillionTokens
        val outputCost = (outputTokens.toDouble() / 1_000_000.0) * pricing.outputPerMillionTokens
        return inputCost + outputCost
    }

    /** Force clear pricing cache */
    fun clearCache() {
        fetcher.clearCache()
    }
}
