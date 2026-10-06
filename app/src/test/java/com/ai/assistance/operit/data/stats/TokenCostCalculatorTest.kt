package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenCostCalculatorTest {
    @Test
    fun `token cost uses current split prices`() {
        val result =
            TokenCostCalculator.currentCost(
                row = aggregateRow(
                    uncachedInputTokens = 800L,
                    cachedInputTokens = 200L,
                    totalInputTokens = 1_000L,
                    outputTokens = 500L,
                ),
                pricing = tokenPricing(),
                targetCurrency = PricingCurrency.USD,
                usdToCnyRate = 7.0,
            )

        assertEquals(0.0019, result.knownAmount, 1e-12)
        assertEquals(0L, result.unknownContributionCount)
        assertEquals(
            0.0019,
            result.originalCurrencyAmounts.getValue(PricingCurrency.USD),
            1e-12,
        )
    }

    @Test
    fun `equal input prices use total input when split is unknown`() {
        val result =
            TokenCostCalculator.currentCost(
                row = aggregateRow(
                    uncachedInputKnown = 0L,
                    cachedInputKnown = 0L,
                    totalInputTokens = 1_000L,
                    outputTokens = 500L,
                ),
                pricing = tokenPricing(cachedInputPricePerMillion = 1.0),
                targetCurrency = PricingCurrency.USD,
                usdToCnyRate = 7.0,
            )

        assertEquals(0.002, result.knownAmount, 1e-12)
        assertEquals(0L, result.unknownContributionCount)
    }

    @Test
    fun `missing priced token field counts request as unknown`() {
        val result =
            TokenCostCalculator.currentCost(
                row = aggregateRow(outputKnown = 0L),
                pricing = tokenPricing(),
                targetCurrency = PricingCurrency.USD,
                usdToCnyRate = 7.0,
            )

        assertEquals(1L, result.unknownContributionCount)
        assertEquals(1L, result.totalContributionCount)
    }

    @Test
    fun `legacy non independent cache write is known zero`() {
        val row = normalizeLegacyCacheWriteUsage(
            aggregateRow(
                requests = 165L,
                provider = "DEEPSEEK/legacy configuration",
                configId = "",
                cacheWriteKnown = 0L,
            )
        )
        val result = TokenCostCalculator.currentCost(
            row = row,
            pricing = tokenPricing(),
            targetCurrency = PricingCurrency.USD,
            usdToCnyRate = 7.0,
        )

        assertEquals(165L, row.cacheWriteKnown)
        assertEquals(0L, result.unknownContributionCount)
    }

    @Test
    fun `legacy independently billed cache write remains unknown`() {
        val row = normalizeLegacyCacheWriteUsage(
            aggregateRow(
                provider = "ANTHROPIC/legacy configuration",
                configId = "",
                cacheWriteKnown = 0L,
            )
        )

        assertEquals(0L, row.cacheWriteKnown)
    }

    @Test
    fun `configured non independent provider rows are not rewritten`() {
        val row = normalizeLegacyCacheWriteUsage(
            aggregateRow(
                provider = "DEEPSEEK",
                configId = "current-config",
                cacheWriteKnown = 0L,
            )
        )

        assertEquals(0L, row.cacheWriteKnown)
    }

    @Test
    fun `unknown zero pricing counts every request as unknown`() {
        val result =
            TokenCostCalculator.currentCost(
                row = aggregateRow(requests = 3L),
                pricing =
                    tokenPricing(
                        inputPricePerMillion = 0.0,
                        cachedInputPricePerMillion = 0.0,
                        outputPricePerMillion = 0.0,
                        source = PricingSource.UNKNOWN,
                    ),
                targetCurrency = PricingCurrency.CNY,
                usdToCnyRate = 7.0,
            )

        assertEquals(0.0, result.knownAmount, 0.0)
        assertEquals(3L, result.unknownContributionCount)
    }

    @Test
    fun `count billing uses current per request price`() {
        val result =
            TokenCostCalculator.currentCost(
                row = aggregateRow(requests = 4L),
                pricing =
                    ResolvedTokenPricing(
                        billingMode = BillingMode.COUNT,
                        currency = PricingCurrency.CNY,
                        inputPricePerMillion = 0.0,
                        cachedInputPricePerMillion = 0.0,
                        cacheWritePricePerMillion = 0.0,
                        outputPricePerMillion = 0.0,
                        pricePerRequest = 0.02,
                        source = PricingSource.USER,
                    ),
                targetCurrency = PricingCurrency.CNY,
                usdToCnyRate = 7.0,
            )

        assertEquals(0.08, result.knownAmount, 1e-12)
        assertEquals(0L, result.unknownContributionCount)
    }

    @Test
    fun `request pricing applies long context before peak component multipliers`() {
        val result = TokenCostCalculator.currentCost(
            records = listOf(
                TokenUsageRecordEntity(
                    occurredAtMs = at("2026-08-03T10:00"),
                    configId = "config",
                    provider = "OPENAI",
                    model = "gpt-test",
                    requestCount = 1L,
                    uncachedInputTokens = 1_000_000L,
                    cachedInputTokens = 1_000_000L,
                    cacheWriteTokens = 1_000_000L,
                    totalInputTokens = 3_000_000L,
                    outputTokens = 1_000_000L,
                ),
            ),
            pricing = ResolvedTokenPricing(
                billingMode = BillingMode.TOKEN,
                currency = PricingCurrency.CNY,
                inputPricePerMillion = 1.0,
                cachedInputPricePerMillion = 1.0,
                cacheWritePricePerMillion = 1.0,
                outputPricePerMillion = 1.0,
                pricePerRequest = 0.0,
                peakPricingEnabled = true,
                peakInputMultiplier = 3.0,
                peakCachedInputMultiplier = 3.0,
                peakCacheWriteMultiplier = 3.0,
                peakOutputMultiplier = 3.0,
                longContextPricingEnabled = true,
                longContextThreshold = 2_000_000L,
                longContextInputMultiplier = 2.0,
                longContextCachedInputMultiplier = 1.5,
                longContextCacheWriteMultiplier = 1.5,
                longContextOutputMultiplier = 1.5,
                source = PricingSource.USER,
            ),
            targetCurrency = PricingCurrency.CNY,
            usdToCnyRate = 7.0,
        )

        assertEquals(19.5, result.knownAmount, 1e-12)
        assertEquals(0L, result.unknownContributionCount)
    }

    @Test
    fun `long context threshold includes output tokens`() {
        val result = TokenCostCalculator.currentCost(
            records = listOf(
                TokenUsageRecordEntity(
                    occurredAtMs = at("2026-08-03T13:00"),
                    configId = "config",
                    provider = "OPENAI",
                    model = "gpt-test",
                    requestCount = 1L,
                    uncachedInputTokens = 1_000_000L,
                    cachedInputTokens = 1_000_000L,
                    cacheWriteTokens = 10_000_000L,
                    totalInputTokens = 2_000_000L,
                    outputTokens = 1_000_000L,
                ),
            ),
            pricing = tokenPricing().copy(
                longContextPricingEnabled = true,
                longContextThreshold = 2_500_000L,
                longContextInputMultiplier = 2.0,
                longContextCachedInputMultiplier = 1.0,
                longContextCacheWriteMultiplier = 1.0,
                longContextOutputMultiplier = 1.0,
            ),
            targetCurrency = PricingCurrency.USD,
            usdToCnyRate = 7.0,
        )

        assertEquals(4.5, result.knownAmount, 1e-12)
    }

    @Test
    fun `count pricing ignores token-only advanced rules`() {
        val result = TokenCostCalculator.currentCost(
            records = listOf(
                TokenUsageRecordEntity(
                    occurredAtMs = at("2026-08-03T10:00"),
                    configId = "config",
                    provider = "OPENAI",
                    model = "gpt-test",
                    requestCount = 2L,
                ),
            ),
            pricing = ResolvedTokenPricing(
                billingMode = BillingMode.COUNT,
                currency = PricingCurrency.CNY,
                inputPricePerMillion = 0.0,
                cachedInputPricePerMillion = 0.0,
                cacheWritePricePerMillion = 0.0,
                outputPricePerMillion = 0.0,
                pricePerRequest = 0.25,
                peakPricingEnabled = true,
                peakInputMultiplier = 3.0,
                longContextPricingEnabled = true,
                longContextThreshold = 1L,
                longContextInputMultiplier = 2.0,
                source = PricingSource.USER,
            ),
            targetCurrency = PricingCurrency.CNY,
            usdToCnyRate = 7.0,
        )

        assertEquals(0.5, result.knownAmount, 1e-12)
        assertEquals(0L, result.unknownContributionCount)
    }

    @Test
    fun `request aggregates do not receive request-level multipliers`() {
        val result = TokenCostCalculator.currentCost(
            records = listOf(
                TokenUsageRecordEntity(
                    occurredAtMs = at("2026-08-03T10:00"),
                    configId = "config",
                    provider = "OPENAI",
                    model = "gpt-test",
                    requestCount = 2L,
                    uncachedInputTokens = 1_000_000L,
                    cachedInputTokens = 0L,
                    cacheWriteTokens = 0L,
                    totalInputTokens = 1_000_000L,
                    outputTokens = 1_000_000L,
                ),
            ),
            pricing = tokenPricing().copy(
                peakPricingEnabled = true,
                peakInputMultiplier = 3.0,
                peakOutputMultiplier = 3.0,
            ),
            targetCurrency = PricingCurrency.USD,
            usdToCnyRate = 7.0,
        )

        assertEquals(3.0, result.knownAmount, 1e-12)
        assertEquals(0L, result.unknownContributionCount)
        assertEquals(2L, result.totalContributionCount)
    }

    @Test
    fun `zero multiplier makes a priced component free`() {
        val result = TokenCostCalculator.currentCost(
            records = listOf(
                TokenUsageRecordEntity(
                    occurredAtMs = at("2026-08-03T10:00"),
                    configId = "config",
                    provider = "OPENAI",
                    model = "gpt-test",
                    requestCount = 1L,
                    uncachedInputTokens = 1_000_000L,
                    cachedInputTokens = 0L,
                    cacheWriteTokens = 0L,
                    totalInputTokens = 1_000_000L,
                    outputTokens = 1_000_000L,
                ),
            ),
            pricing = tokenPricing().copy(
                peakPricingEnabled = true,
                peakInputMultiplier = 0.0,
                peakCachedInputMultiplier = 0.0,
                peakCacheWriteMultiplier = 0.0,
                peakOutputMultiplier = 0.0,
            ),
            targetCurrency = PricingCurrency.USD,
            usdToCnyRate = 7.0,
        )

        assertEquals(0.0, result.knownAmount, 0.0)
        assertEquals(0L, result.unknownContributionCount)
        assertEquals(1L, result.totalContributionCount)
    }

    @Test
    fun `currency conversion uses configured rate`() {
        assertEquals(
            70.0,
            TokenCostCurrency.convertTo(
                amount = 10.0,
                source = PricingCurrency.USD,
                target = PricingCurrency.CNY,
                usdToCnyRate = 7.0,
            ),
            1e-12,
        )
        assertEquals(
            10.0,
            TokenCostCurrency.convertTo(
                amount = 70.0,
                source = PricingCurrency.CNY,
                target = PricingCurrency.USD,
                usdToCnyRate = 7.0,
            ),
            1e-12,
        )
    }

    @Test
    fun `saturated add clamps overflow`() {
        assertEquals(Long.MAX_VALUE, TokenCostCalculator.saturatedAdd(Long.MAX_VALUE, 1L))
        assertEquals(7L, TokenCostCalculator.saturatedAdd(3L, 4L))
    }

    private fun at(value: String): Long =
        LocalDateTime.parse(value)
            .atZone(TokenPricingRules.PRICING_ZONE)
            .toInstant()
            .toEpochMilli()

    private fun tokenPricing(
        inputPricePerMillion: Double = 1.0,
        cachedInputPricePerMillion: Double = 0.5,
        outputPricePerMillion: Double = 2.0,
        source: PricingSource = PricingSource.BUILT_IN,
    ) = ResolvedTokenPricing(
        billingMode = BillingMode.TOKEN,
        currency = PricingCurrency.USD,
        inputPricePerMillion = inputPricePerMillion,
        cachedInputPricePerMillion = cachedInputPricePerMillion,
        cacheWritePricePerMillion = inputPricePerMillion,
        outputPricePerMillion = outputPricePerMillion,
        pricePerRequest = 0.0,
        source = source,
    )

    private fun aggregateRow(
        requests: Long = 1L,
        provider: String = "OPENAI",
        configId: String = "test-config",
        uncachedInputTokens: Long = 1_000L,
        uncachedInputKnown: Long = requests,
        cachedInputTokens: Long = 0L,
        cachedInputKnown: Long = requests,
        cacheWriteTokens: Long = 0L,
        cacheWriteKnown: Long = requests,
        totalInputTokens: Long = uncachedInputTokens + cachedInputTokens,
        totalInputKnown: Long = requests,
        outputTokens: Long = 500L,
        outputKnown: Long = requests,
    ) = TokenUsageModelAggregateRow(
        provider = provider,
        model = "gpt-test",
        configId = configId,
        requests = requests,
        uncachedInputTokens = uncachedInputTokens,
        uncachedInputKnown = uncachedInputKnown,
        cachedInputTokens = cachedInputTokens,
        cachedInputKnown = cachedInputKnown,
        cacheWriteTokens = cacheWriteTokens,
        cacheWriteKnown = cacheWriteKnown,
        totalInputTokens = totalInputTokens,
        totalInputKnown = totalInputKnown,
        outputTokens = outputTokens,
        outputKnown = outputKnown,
    )
}
