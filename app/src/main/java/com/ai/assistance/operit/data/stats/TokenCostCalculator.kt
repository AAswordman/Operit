package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity

object TokenCostCalculator {
    fun saturatedAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

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
        return summary(
            nativeAmount = nativeAmount,
            unknown = unknown,
            total = row.requests,
            pricing = pricing,
            targetCurrency = targetCurrency,
            usdToCnyRate = usdToCnyRate,
        )
    }

    /** 按请求应用长上下文和峰谷规则，避免先聚合后丢失请求时间。 */
    fun currentCost(
        records: List<TokenUsageRecordEntity>,
        pricing: ResolvedTokenPricing,
        targetCurrency: PricingCurrency,
        usdToCnyRate: Double,
    ): TokenStatsCostSummary {
        var nativeAmount = 0.0
        var unknown = 0L
        var total = 0L
        records.forEach { rawRecord ->
            val record = normalizeLegacyCacheWriteUsage(rawRecord)
            val requestCount = record.requestCount.coerceAtLeast(0L)
            total = saturatedAdd(total, requestCount)
            if (pricing.billingMode == BillingMode.COUNT) {
                if (pricing.pricePerRequest > 0.0) {
                    nativeAmount += pricing.pricePerRequest * requestCount
                } else {
                    unknown = saturatedAdd(unknown, requestCount)
                }
                return@forEach
            }

            val multipliers = multipliersFor(record, pricing)
            val inputPrice = pricing.inputPricePerMillion * multipliers.input
            val cachedInputPrice = pricing.cachedInputPricePerMillion * multipliers.cachedInput
            val cacheWritePrice = pricing.cacheWritePricePerMillion * multipliers.cacheWrite
            val outputPrice = pricing.outputPricePerMillion * multipliers.output
            val hasPricedComponent =
                pricing.inputPricePerMillion > 0.0 ||
                    pricing.cachedInputPricePerMillion > 0.0 ||
                    pricing.cacheWritePricePerMillion > 0.0 ||
                    pricing.outputPricePerMillion > 0.0
            if (!hasPricedComponent) {
                unknown = saturatedAdd(unknown, requestCount)
                return@forEach
            }

            var recordUnknown = false
            val inputPricesEqual =
                inputPrice == cachedInputPrice && inputPrice == cacheWritePrice
            if (inputPricesEqual) {
                val totalInput = totalInputTokens(record)
                if (inputPrice > 0.0 && totalInput == null) {
                    recordUnknown = true
                } else if (totalInput != null) {
                    nativeAmount += totalInput.toDouble() * inputPrice / 1_000_000.0
                }
            } else {
                nativeAmount += addKnownComponent(
                    record.uncachedInputTokens,
                    inputPrice,
                    requestCount,
                ) { recordUnknown = true }
                nativeAmount += addKnownComponent(
                    record.cachedInputTokens,
                    cachedInputPrice,
                    requestCount,
                ) { recordUnknown = true }
                nativeAmount += addKnownComponent(
                    record.cacheWriteTokens,
                    cacheWritePrice,
                    requestCount,
                ) { recordUnknown = true }
            }
            nativeAmount += addKnownComponent(
                record.outputTokens,
                outputPrice,
                requestCount,
            ) { recordUnknown = true }
            if (recordUnknown) unknown = saturatedAdd(unknown, requestCount)
        }
        return summary(
            nativeAmount = nativeAmount,
            unknown = unknown,
            total = total,
            pricing = pricing,
            targetCurrency = targetCurrency,
            usdToCnyRate = usdToCnyRate,
        )
    }

    private fun addKnownComponent(
        tokens: Long?,
        price: Double,
        requestCount: Long,
        onUnknown: () -> Unit,
    ): Double {
        if (price <= 0.0) return 0.0
        val knownTokens = tokens?.takeIf { it >= 0L }
        if (knownTokens == null) {
            if (requestCount > 0L) onUnknown()
            return 0.0
        }
        return knownTokens.toDouble() * price / 1_000_000.0
    }

    private fun totalInputTokens(record: TokenUsageRecordEntity): Long? =
        record.totalInputTokens?.takeIf { it >= 0L }
            ?: listOf(
                record.uncachedInputTokens,
                record.cachedInputTokens,
                record.cacheWriteTokens,
            ).takeIf { values -> values.all { it != null && it >= 0L } }
                ?.fold(0L) { total, value -> saturatedAdd(total, value ?: 0L) }

    // 长上下文阈值按未缓存输入、缓存输入和输出 Token 的总和判断。
    private fun longContextTokens(record: TokenUsageRecordEntity): Long? {
        val uncachedInput = record.uncachedInputTokens?.takeIf { it >= 0L } ?: return null
        val cachedInput = record.cachedInputTokens?.takeIf { it >= 0L } ?: return null
        val output = record.outputTokens?.takeIf { it >= 0L } ?: return null
        return saturatedAdd(saturatedAdd(uncachedInput, cachedInput), output)
    }

    private data class PriceMultipliers(
        val input: Double,
        val cachedInput: Double,
        val cacheWrite: Double,
        val output: Double,
    )

    private fun multipliersFor(
        record: TokenUsageRecordEntity,
        pricing: ResolvedTokenPricing,
    ): PriceMultipliers {
        var input = 1.0
        var cachedInput = 1.0
        var cacheWrite = 1.0
        var output = 1.0
        val contextTokens = longContextTokens(record)
        val longContextThreshold = pricing.longContextThreshold
        val requestLevelEligible = record.occurredAtMs != null && record.requestCount == 1L
        if (
            requestLevelEligible &&
            pricing.longContextPricingEnabled &&
            longContextThreshold != null &&
            contextTokens != null &&
            contextTokens >= longContextThreshold
        ) {
            input *= pricing.longContextInputMultiplier
            cachedInput *= pricing.longContextCachedInputMultiplier
            cacheWrite *= pricing.longContextCacheWriteMultiplier
            output *= pricing.longContextOutputMultiplier
        }
        if (
            requestLevelEligible &&
            pricing.peakPricingEnabled &&
            TokenPricingRules.isPeakTime(
                occurredAtMs = record.occurredAtMs,
                periods = pricing.peakSchedule,
                weekendOffPeakPricingEnabled = pricing.weekendOffPeakPricingEnabled,
                holidayOffPeakPricingEnabled = pricing.holidayOffPeakPricingEnabled,
            )
        ) {
            input *= pricing.peakInputMultiplier
            cachedInput *= pricing.peakCachedInputMultiplier
            cacheWrite *= pricing.peakCacheWriteMultiplier
            output *= pricing.peakOutputMultiplier
        }
        return PriceMultipliers(input, cachedInput, cacheWrite, output)
    }

    private fun summary(
        nativeAmount: Double,
        unknown: Long,
        total: Long,
        pricing: ResolvedTokenPricing,
        targetCurrency: PricingCurrency,
        usdToCnyRate: Double,
    ): TokenStatsCostSummary {
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
            totalContributionCount = total,
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
