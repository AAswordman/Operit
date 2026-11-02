package com.ai.assistance.operit.ui.features.management.settings.model

import com.ai.assistance.operit.api.chat.keypool.ApiKeyInfo
import com.ai.assistance.operit.data.config.model.ModelConfigData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigPreviewTest {
    @Test
    fun authenticationAndHeaderValuesAreMaskedWithoutChangingConfiguration() {
        val config = ModelConfigData(
            id = "preview",
            name = "预览",
            apiKey = "single-private-value",
            apiKeyPool = listOf(ApiKeyInfo(id = "pool", key = "pool-private-value")),
            customHeaders = """{"Authorization":"header-private-value","X-Mode":"mode-private-value"}""",
        )
        val preview = buildModelConfigPreview(config)
        for (value in listOf(config.apiKey, config.apiKeyPool.single().key, "header-private-value", "mode-private-value")) {
            assertFalse(preview.contains(value))
        }
        assertEquals("single-private-value", config.apiKey)
        assertEquals("pool-private-value", config.apiKeyPool.single().key)
        assertTrue(Json.parseToJsonElement(preview).jsonObject.getValue("headers").jsonObject.containsKey("Authorization"))
    }

    @Test
    fun previewUsesCurrentCapabilitiesThinkingAndQueue() {
        val config = ModelConfigData(
            id = "preview",
            name = "预览",
            enableStreamOutput = false,
            enableToolCall = true,
            temperatureEnabled = false,
            maxTokensEnabled = true,
            maxTokens = 4096,
            requestLimitPerMinute = 20,
            maxConcurrentRequests = 2,
            thinkingOptionId = "high",
            thinkingConfigurations = """[{"id":"thinking"}]""",
        )
        val preview = Json.parseToJsonElement(buildModelConfigPreview(config)).jsonObject
        val capabilities = preview.getValue("capabilities").jsonObject
        assertEquals(5, capabilities.size)
        assertFalse(capabilities.getValue("stream").jsonPrimitive.boolean)
        assertTrue(capabilities.getValue("toolCall").jsonPrimitive.boolean)
        assertFalse(preview.getValue("parameters").jsonObject.containsKey("temperature"))
        assertEquals(4096, preview.getValue("parameters").jsonObject.getValue("max_tokens").jsonPrimitive.int)
        assertEquals(2, preview.getValue("requestQueue").jsonObject.getValue("maxConcurrentRequests").jsonPrimitive.int)
        assertEquals("high", preview.getValue("thinkingOptionId").jsonPrimitive.content)
    }
}