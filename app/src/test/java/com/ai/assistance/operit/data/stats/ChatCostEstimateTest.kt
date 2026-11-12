package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.model.ChatMessageUsage
import org.junit.Assert.*
import org.junit.Test

class ChatCostEstimateTest {
    @Test fun usesCurrentConfiguredTokenPricesAndConvertsCurrency() {
        val message = ChatMessageUsage(provider = "OPENAI",
            modelName = "test", inputTokens = 1_000_000L, outputTokens = 500_000L, cachedInputTokens = 0L)
        val prices = mapOf("OPENAI:test" to ModelPriceSettings(
            billingMode = BillingMode.TOKEN, currency = PricingCurrency.USD,
            inputPricePerMillion = 2.0, cachedInputPricePerMillion = 2.0,
            cacheWritePricePerMillion = 2.0, outputPricePerMillion = 4.0))
        val cost = calculateChatCost(listOf(message), prices, 7.0)
        assertEquals(28.0, cost.cny, 0.00001)
        assertFalse(cost.incomplete)
    }

    @Test fun missingUsageAndRequestBillingAreDisclosedRatherThanInvented() {
        val message = ChatMessageUsage(provider = "OPENAI", modelName = "test",
            inputTokens = 0L, outputTokens = 0L, cachedInputTokens = 0L)
        assertTrue(calculateChatCost(listOf(message), emptyMap(), 7.0).incomplete)
        val byRequest = mapOf("OPENAI:test" to ModelPriceSettings(billingMode = BillingMode.COUNT, pricePerRequest = 1.0))
        val cost = calculateChatCost(listOf(message.copy(inputTokens = 10L)), byRequest, 7.0)
        assertEquals(0.0, cost.cny, 0.0)
        assertTrue(cost.incomplete)
    }
}
