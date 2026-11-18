package com.ai.assistance.operit.data.stats

import android.content.Context
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.ChatMessageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChatCostEstimate(val cny: Double, val incomplete: Boolean)

/** A current-price estimate from saved reply usage, not a historical invoice. */
internal suspend fun estimateChatCost(context: Context, chatId: String): ChatCostEstimate =
    withContext(Dispatchers.IO) {
        val messages = com.ai.assistance.operit.data.db.AppDatabase.getDatabase(context)
            .messageDao().getAssistantUsage(chatId)
        val prices = TokenUsageRepository.getInstance(context).withDao { it.getAllStatsModels() }
            .filter { it.configId.isEmpty() }.associate { "${it.provider}:${it.model}" to it.toModelPriceSettings() }
        val (rate, estimatedRate) = TokenStatsPreferences(context).loadRateWithEstimate()
        calculateChatCost(messages, prices, rate).let {
            if (estimatedRate) it.copy(incomplete = true) else it
        }
    }

internal fun calculateChatCost(
    messages: List<ChatMessageUsage>,
    prices: Map<String, ModelPriceSettings>,
    rate: Double,
): ChatCostEstimate {
        var total = 0.0
        var incomplete = false
        for (message in messages) {
            if (message.inputTokens <= 0 && message.outputTokens <= 0) {
                incomplete = true
                continue
            }
            val providerModel = "${message.provider}:${message.modelName}"
            val pricing = TokenPriceResolver.resolve(providerModel, prices[providerModel])
            // Saved messages aggregate a whole turn, so request boundaries and cache writes
            // cannot be reconstructed. Use base token rates and disclose incomplete estimates.
            if (pricing.billingMode == com.ai.assistance.operit.data.model.BillingMode.COUNT) {
                incomplete = true
                continue
            }
            if (message.modelName.contains("claude", ignoreCase = true) ||
                message.provider.contains("claude", ignoreCase = true) ||
                message.provider.contains("anthropic", ignoreCase = true)
            ) incomplete = true
            val cost = TokenCostCalculator.currentCost(
                com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow(
                    provider = message.provider, model = message.modelName, configId = "", requests = 1L,
                    uncachedInputTokens = (message.inputTokens - message.cachedInputTokens).coerceAtLeast(0).toLong(),
                    uncachedInputKnown = 1L,
                    cachedInputTokens = message.cachedInputTokens.coerceAtLeast(0).toLong(),
                    cachedInputKnown = 1L,
                    cacheWriteTokens = 0, cacheWriteKnown = 0,
                    totalInputTokens = message.inputTokens.coerceAtLeast(0), totalInputKnown = 1L,
                    outputTokens = message.outputTokens.coerceAtLeast(0).toLong(),
                    outputKnown = 1L,
                ),
                pricing,
                PricingCurrency.CNY,
                rate,
            )
            val converted = cost.knownAmount
            if (cost.unknownContributionCount > 0) incomplete = true
            if (!(total + converted).isFinite()) incomplete = true
            else total += converted
        }
        return ChatCostEstimate(total, incomplete)
}
