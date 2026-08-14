package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow
import com.ai.assistance.operit.data.model.BillingMode
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
        provider = "OPENAI",
        model = "gpt-test",
        configId = "test-config",
        requests = requests,
        requestCountKnown = requests,
        usageRows = requests,
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
        reasoningTokens = 0L,
        reasoningKnown = requests,
        ttftTotalMs = 0L,
        ttftSamples = 0L,
        durationTotalMs = 0L,
        durationSamples = 0L,
    )
}
