package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency

data class TokenStatsQueryParams(
    val targetCurrency: PricingCurrency = PricingCurrency.CNY,
    val manualRate: Double = TokenCostCurrency.DEFAULT_USD_TO_CNY_RATE,
    val providerModels: Set<String>? = null,
)

data class TokenStatsCostSummary(
    val currency: PricingCurrency,
    val knownAmount: Double,
    val unknownContributionCount: Long,
    val totalContributionCount: Long,
    val rateUsed: Double,
    val originalCurrencyAmounts: Map<PricingCurrency, Double>,
)

data class TokenStatsTokenAggregate(
    val knownSum: Long,
    val knownEventCount: Long,
    val unknownEventCount: Long,
    val totalEventCount: Long,
) {
    val isFullyKnown: Boolean get() = unknownEventCount == 0L
}

data class TokenStatsTotals(
    val requests: Long,
    val uncachedInput: TokenStatsTokenAggregate,
    val cachedInput: TokenStatsTokenAggregate,
    val totalInput: TokenStatsTokenAggregate,
    val output: TokenStatsTokenAggregate,
    val totalTokens: TokenStatsTokenAggregate,
    val cost: TokenStatsCostSummary,
)

data class TokenStatsLifetimeOverview(
    val totals: TokenStatsTotals,
    val displayModels: List<TokenStatsDisplayModelBreakdown>,
)

data class TokenStatsTrendBucket(
    val bucketStartMs: Long,
    val bucketEndMs: Long,
    val totals: TokenStatsTotals,
    val byModel: Map<String, TokenStatsModelBucket>,
)

data class TokenStatsModelBucket(
    val requests: Long,
    val uncachedInput: Long,
    val cachedInput: Long,
    val output: Long,
    val totalTokens: Long,
    val totalTokensUnknownEventCount: Long,
    val unknownTokenEventCount: Long,
    val cost: TokenStatsCostSummary,
)

data class TokenStatsIdentityBreakdown(
    val configId: String,
    val provider: String,
    val model: String,
    val totals: TokenStatsTotals,
)

data class TokenStatsDisplayModelBreakdown(
    val displayModelId: String,
    val displayName: String,
    val normalizedModel: String,
    val totals: TokenStatsTotals,
    val identities: List<TokenStatsIdentityBreakdown>,
    val providerModels: List<String>,
)

/** 年度报告的 token 汇总：全年合计、按模型、按月和按本地日期。 */
data class TokenStatsAnnualSummary(
    val totals: TokenStatsTotals,
    val displayModels: List<TokenStatsDisplayModelBreakdown>,
    val monthTotals: List<Long>,
    val dayTotals: Map<java.time.LocalDate, Long>,
)

data class TokenStatsRangeData(
    val range: TokenStatsTimeRange,
    val granularity: TokenStatsGranularity,
    val eventCount: Long,
    val summary: TokenStatsTotals,
    val buckets: List<TokenStatsTrendBucket>,
    val displayModels: List<TokenStatsDisplayModelBreakdown>,
)

internal val TokenStatsTotals.cacheRate: Double?
    get() {
        if (
            !cachedInput.isFullyKnown ||
                !totalInput.isFullyKnown ||
                totalInput.knownSum <= 0L ||
                cachedInput.knownSum !in 0L..totalInput.knownSum
        ) {
            return null
        }
        return cachedInput.knownSum.toDouble() / totalInput.knownSum.toDouble()
    }
