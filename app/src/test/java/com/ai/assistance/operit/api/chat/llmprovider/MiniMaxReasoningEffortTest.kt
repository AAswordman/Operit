package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MiniMax-M3.1-Flash always thinks, accepts reasoning_effort low, medium, high, xhigh, or max, and
 * answers reasoning_effort "none" with HTTP 400. Other MiniMax models ignore the field.
 */
class MiniMaxReasoningEffortTest {
    private val endpoint = "https://api.minimaxi.com/v1/chat/completions"
    private val thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.MINIMAX.name)
    private val levels = listOf("low", "medium", "high", "xhigh", "max")

    private fun mapping(modelName: String): ThinkingQualityMapping =
        ThinkingQualityMappingRegistry.resolve(
            providerTypeId = ApiProviderType.MINIMAX.name,
            modelName = modelName,
            apiEndpoint = endpoint,
            thinkingConfigurations = thinkingConfigurations,
        )

    private fun requestBody(modelName: String, enableThinking: Boolean, optionId: String): JSONObject {
        val requestJson = JSONObject().put("model", modelName).put("stream", true)
        ThinkingConfigurationApplier.apply(
            requestJson = requestJson,
            providerTypeId = ApiProviderType.MINIMAX.name,
            modelName = modelName,
            apiEndpoint = endpoint,
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = optionId,
        )
        return requestJson
    }

    @Test
    fun m31FlashOffersTheFiveDocumentedLevelsWithThinkingAlwaysOn() {
        val mapping = mapping("MiniMax-M3.1-Flash")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertTrue(mapping.reasoningRequired)
        assertEquals(levels, mapping.options.map { it.id })
    }

    @Test
    fun m31FlashSendsTheSelectedLevelAndNeverNone() {
        for (level in levels) {
            for (enableThinking in listOf(true, false)) {
                val body = requestBody("MiniMax-M3.1-Flash", enableThinking, level)
                assertEquals("$level thinking=$enableThinking", level, body.getString("reasoning_effort"))
            }
        }
    }

    @Test
    fun otherMiniMaxModelsDoNotReceiveReasoningEffort() {
        assertEquals(ThinkingQualityControl.UNSUPPORTED, mapping("MiniMax-M3").control)
        assertFalse(requestBody("MiniMax-M3", enableThinking = true, optionId = "").has("reasoning_effort"))
    }

    @Test
    fun switchingToMiniMaxSeedsTheDefaultModelWithItsFirstLevel() {
        val defaultModel = ApiProviderConfigs.getDefaultModelName(ApiProviderType.MINIMAX)

        assertEquals("MiniMax-M3.1-Flash", defaultModel)
        assertEquals(
            "low",
            ModelConfigManager.firstThinkingOptionIdForModel(ApiProviderType.MINIMAX.name, defaultModel, endpoint)
        )
    }
}
