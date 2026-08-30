package com.ai.nonoassistance.provider

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PricingConfigTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `PricingRemoteConfig deserializes from JSON`() {
        val jsonStr = """
        {
            "lastUpdated": "2026-08-29T00:00:00Z",
            "providers": {
                "deepseek": {
                    "models": {
                        "deepseek-chat": {
                            "inputPerMillionTokens": 0.14,
                            "outputPerMillionTokens": 0.28,
                            "currency": "USD"
                        }
                    }
                }
            }
        }
        """.trimIndent()

        val config = json.decodeFromString<PricingRemoteConfig>(jsonStr)

        assertEquals("2026-08-29T00:00:00Z", config.lastUpdated)
        assertEquals(1, config.providers.size)

        val deepseek = config.providers["deepseek"]
        assertNotNull(deepseek)
        assertEquals(1, deepseek?.models?.size)

        val chatModel = deepseek?.models?.get("deepseek-chat")
        assertNotNull(chatModel)
        assertEquals(0.14, chatModel?.inputPerMillionTokens ?: 0.0, 0.001)
        assertEquals(0.28, chatModel?.outputPerMillionTokens ?: 0.0, 0.001)
        assertEquals("USD", chatModel?.currency)
    }

    @Test
    fun `PricingRemoteConfig handles empty JSON gracefully`() {
        val jsonStr = "{}"
        val config = json.decodeFromString<PricingRemoteConfig>(jsonStr)

        assertEquals("", config.lastUpdated)
        assertTrue(config.providers.isEmpty())
    }

    @Test
    fun `PricingRemoteConfig handles unknown fields gracefully`() {
        val jsonStr = """
        {
            "lastUpdated": "2026-01-01T00:00:00Z",
            "unknownField": "should be ignored",
            "providers": {}
        }
        """.trimIndent()

        val config = json.decodeFromString<PricingRemoteConfig>(jsonStr)
        assertEquals("2026-01-01T00:00:00Z", config.lastUpdated)
    }
}
