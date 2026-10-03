package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow
import com.ai.assistance.operit.data.model.BillingMode

object TokenCostCalculator {
    fun saturatedAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    fun zeroCost(
        targetCurrency: PricingCurrency,
        usdToCnyRate: Double,
        contributionCount: Long,
    ): TokenStatsCostSummary =
        TokenStatsCostSummary(
            currency = targetCurrency,
            knownAmount = 0.0,
            unknownContributionCount = 0L,
            totalContributionCount = contributionCount,
            rateUsed = usdToCnyRate,
            originalCurrencyAmounts = emptyMap(),
        )

    fun currentCost(
        row: TokenUsageModelAggregateRow,
        pricing: ResolvedTokenPricing,
        targetCurrency: PricingCurrency,
        usdToCnyRate: Double,
    ): TokenStatsCostSummary {
        val nativeAmount: Double
        val unknown: Long
        if (pricing.billingMode == BillingMode.COUNT) {
            nativeAmount = pricing.pricePerRequest * row.requests
            unknown =
                if (pricing.pricePerRequest > 0.0) {
                    0L
                } else {
                    row.requests
                }
        } else {
            var amount = 0.0
            var unknownRequests = 0L
            fun add(tokens: Long, known: Long, price: Double) {
                if (price > 0.0) {
                    amount += tokens.toDouble() * price / 1_000_000.0
                    unknownRequests = maxOf(unknownRequests, row.requests - known)
                }
            }
            if (
                pricing.inputPricePerMillion == pricing.cachedInputPricePerMillion &&
                pricing.inputPricePerMillion == pricing.cacheWritePricePerMillion
            ) {
                add(row.totalInputTokens, row.totalInputKnown, pricing.inputPricePerMillion)
            } else {
                add(row.uncachedInputTokens, row.uncachedInputKnown, pricing.inputPricePerMillion)
                add(row.cachedInputTokens, row.cachedInputKnown, pricing.cachedInputPricePerMillion)
                add(row.cacheWriteTokens, row.cacheWriteKnown, pricing.cacheWritePricePerMillion)
            }
            add(row.outputTokens, row.outputKnown, pricing.outputPricePerMillion)
            nativeAmount = amount
            unknown =
                if (
                    pricing.inputPricePerMillion <= 0.0 &&
                    pricing.cachedInputPricePerMillion <= 0.0 &&
                    pricing.cacheWritePricePerMillion <= 0.0 &&
                    pricing.outputPricePerMillion <= 0.0
                ) {
                    row.requests
                } else {
                    unknownRequests
                }
        }
        val converted = TokenCostCurrency.convertTo(
            nativeAmount,
            pricing.currency,
            targetCurrency,
            usdToCnyRate,
        )
        return TokenStatsCostSummary(
            currency = targetCurrency,
            knownAmount = converted,
            unknownContributionCount = unknown,
            totalContributionCount = row.requests,
            rateUsed = usdToCnyRate,
            originalCurrencyAmounts =
                if (nativeAmount > 0.0) mapOf(pricing.currency to nativeAmount) else emptyMap(),
        )
    }
}

object TokenCostCurrency {
    const val DEFAULT_USD_TO_CNY_RATE = 7.0

    fun convertTo(
        amount: Double,
        source: PricingCurrency,
        target: PricingCurrency,
        usdToCnyRate: Double,
    ): Double = when {
        source == target -> amount
        source == PricingCurrency.USD -> amount * usdToCnyRate
        else -> amount / usdToCnyRate
    }
}
