package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCodeThinkingConfigurationTest {
    private fun mapping(modelName: String): ThinkingQualityMapping =
        ThinkingQualityMappingRegistry.resolve(
            providerTypeId = ApiProviderType.OPENCODE.name,
            modelName = modelName,
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.OPENCODE.name)
        )

    @Test
    fun opencodeZhipuProviderSegmentUsesGlmEffortConfig() {
        val mapping = mapping("zhipu/glm-5.3")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("reasoning_effort", mapping.parameterLabel)
        assertEquals(listOf("low", "high", "max"), mapping.options.map { it.id })
    }

    @Test
    fun opencodeZaiOrgProviderSegmentUsesGlmEffortConfig() {
        val mapping = mapping("zai-org/glm-4.6")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals(listOf("low", "high", "max"), mapping.options.map { it.id })
    }

    @Test
    fun opencodeGoogleProviderSegmentUsesGeminiThinkingLevelConfig() {
        val mapping = mapping("google/gemini-2.5-pro")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("thinkingLevel", mapping.parameterLabel)
        assertEquals(listOf("LOW", "MEDIUM", "HIGH"), mapping.options.map { it.id })
    }

    @Test
    fun opencodeUnknownProviderKeepsGenericChatEffortConfig() {
        val mapping = mapping("custom-provider/custom-thinking-model")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("reasoning_effort", mapping.parameterLabel)
        assertEquals(listOf("low", "medium", "high"), mapping.options.map { it.id })
    }

    @Test
    fun customConfigurationControlsOpenCodeOptionCount() {
        val json =
            """
            [
              {
                "id": "opencode-custom",
                "providers": ["OPENCODE"],
                "control": "levels",
                "parameterLabel": "reasoning_effort",
                "options": [
                  {"id": "short", "label": "short", "path": "reasoning_effort", "value": "low"},
                  {"id": "long", "label": "long", "path": "reasoning_effort", "value": "high"}
                ]
              }
            ]
            """.trimIndent()

        val mapping =
            ThinkingQualityMappingRegistry.resolve(
                providerTypeId = ApiProviderType.OPENCODE.name,
                modelName = "anything",
                thinkingConfigurations = json
            )

        assertEquals(listOf("short", "long"), mapping.options.map { it.id })
    }
}
