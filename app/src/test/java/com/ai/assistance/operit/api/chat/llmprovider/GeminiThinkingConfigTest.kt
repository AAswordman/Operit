package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiThinkingConfigTest {
    @Test
    fun `maps each global thinking quality to a Gemini level`() {
        val expectedLevels = listOf("MINIMAL", "LOW", "MEDIUM", "HIGH", "HIGH")

        expectedLevels.forEachIndexed { index, expectedLevel ->
            val config = GeminiProvider.createThinkingConfigFromGlobalQuality(index + 1)

            assertEquals(expectedLevel, config.getString("thinkingLevel"))
        }
    }

    @Test
    fun `requests thought summaries for every enabled global thinking level`() {
        val config = GeminiProvider.createThinkingConfigFromGlobalQuality(3)

        assertTrue(config.getBoolean("includeThoughts"))
    }
}
