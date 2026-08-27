package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiThinkingConfigTest {
    private val thinkingConfigurations =
        ModelThinkingConfigDefaults.forProvider(ApiProviderType.GOOGLE.name)

    @Test
    fun mapsModelOptionToGeminiLevel() {
        val config = GeminiThinkingConfig.fromOption("gemini-3-pro", "HIGH", thinkingConfigurations)
        assertEquals("HIGH", config.thinkingLevel)
    }

    @Test
    fun requestsThoughtSummariesForAnEnabledOption() {
        val config = GeminiThinkingConfig.fromOption("gemini-3-pro", "HIGH", thinkingConfigurations)
        assertTrue(config.includeThoughts)
    }
}
