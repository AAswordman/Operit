package com.ai.nonoassistance.provider

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ModelPricingServiceTest {

    private lateinit var service: ModelPricingService

    @Before
    fun setup() {
        service = ModelPricingService()
    }

    @Test
    fun `estimateCost calculates correct cost for given tokens`() {
        val pricing = ModelPricing(
            inputPerMillionTokens = 2.50,
            outputPerMillionTokens = 10.00,
            currency = "USD"
        )

        val cost = service.estimateCost(pricing, inputTokens = 1_000_000, outputTokens = 500_000)
        // Input: 1M * $2.50/M = $2.50
        // Output: 500K * $10.00/M = $5.00
        // Total: $7.50
        assertEquals(7.50, cost, 0.01)
    }

    @Test
    fun `estimateCost returns zero for zero tokens`() {
        val pricing = ModelPricing(
            inputPerMillionTokens = 2.50,
            outputPerMillionTokens = 10.00
        )

        val cost = service.estimateCost(pricing, inputTokens = 0, outputTokens = 0)
        assertEquals(0.0, cost, 0.001)
    }

    @Test
    fun `estimateCost handles small token counts`() {
        val pricing = ModelPricing(
            inputPerMillionTokens = 0.14,
            outputPerMillionTokens = 0.28
        )

        // 1000 input tokens + 500 output tokens
        val cost = service.estimateCost(pricing, inputTokens = 1000, outputTokens = 500)
        // Input: 1000/1M * $0.14 = $0.00014
        // Output: 500/1M * $0.28 = $0.00014
        // Total: $0.00028
        assertEquals(0.00028, cost, 0.00001)
    }

    @Test
    fun `estimateCost with cheap provider pricing`() {
        val pricing = ModelPricing(
            inputPerMillionTokens = 0.14,
            outputPerMillionTokens = 0.28,
            currency = "USD"
        )

        // Typical conversation: 4K input, 1K output
        val cost = service.estimateCost(pricing, inputTokens = 4000, outputTokens = 1000)
        // Input: 4000/1M * $0.14 = $0.00056
        // Output: 1000/1M * $0.28 = $0.00028
        // Total: $0.00084
        assertEquals(0.00084, cost, 0.00001)
    }
}
