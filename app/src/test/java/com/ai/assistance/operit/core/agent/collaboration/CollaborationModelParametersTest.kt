package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import org.junit.Assert.*
import org.junit.Test

class CollaborationModelParametersTest {
    private fun config(provider: ApiProviderType, model: String) = ModelConfigData(
        id = "config", name = "model", apiProviderType = provider, modelName = model,
        thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(provider.name),
    )

    @Test fun mapsProviderCasingAndClampsHigherNamedEffort() {
        assertEquals("HIGH", CollaborationModelParameters.overrideConfig(
            config(ApiProviderType.GOOGLE, "gemini-3-flash"), 0, "high").thinkingOptionId)
        assertEquals("max", CollaborationModelParameters.overrideConfig(
            config(ApiProviderType.OPENAI, "gpt-5.6-luna"), 0, "ultra").thinkingOptionId)
    }

    @Test fun budgetRequestsSelectOnlyConfiguredBudgetOptions() {
        val original = config(ApiProviderType.SILICONFLOW, "Qwen3")
        val mapping = com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityMappingRegistry.resolve(
            original.apiProviderTypeId, original.modelName, original.apiEndpoint, original.thinkingConfigurations)
        val selected = CollaborationModelParameters.overrideConfig(original, 0, "high")
        assertTrue(mapping.options.any { it.id == selected.thinkingOptionId })
        assertEquals("", original.thinkingOptionId)
    }

    @Test fun disableAndToggleRespectModelCapability() {
        val toggle = config(ApiProviderType.ZHIPU, "glm-4.7-thinking")
        assertEquals(toggle.thinkingOptionId, CollaborationModelParameters.overrideConfig(toggle, 0, "high").thinkingOptionId)
        assertFalse(CollaborationModelParameters.thinkingEnabled(true, "none"))
        assertTrue(CollaborationModelParameters.thinkingEnabled(false, "high"))
        assertTrue(CollaborationModelParameters.thinkingEnabled(true, null))
        assertThrows(IllegalArgumentException::class.java) {
            CollaborationModelParameters.overrideConfig(config(ApiProviderType.ZHIPU, "glm-5.3"), 0, "none")
        }
    }

    @Test fun explicitEffortOverridesCustomRequestParametersWithoutEditingSavedRules() {
        val original = config(ApiProviderType.OPENAI, "gpt-5.6-luna")
        val selected = CollaborationModelParameters.overrideConfig(original, 0, "high")
        val request = org.json.JSONObject().put("reasoning_effort", "low").put("temperature", 0.4)
        com.ai.assistance.operit.api.chat.llmprovider.ThinkingConfigurationApplier.apply(
            request, selected.apiProviderTypeId, selected.modelName, selected.apiEndpoint,
            selected.thinkingConfigurations, true, selected.thinkingOptionId,
        )
        assertEquals("high", request.getString("reasoning_effort"))
        assertEquals(0.4, request.getDouble("temperature"), 0.0)
        assertEquals(ModelThinkingConfigDefaults.forProvider(ApiProviderType.OPENAI.name), original.thinkingConfigurations)
    }

    @Test fun usesTheConfiguredProviderMappingWithoutChangingTheSavedConfig() {
        val config = ModelConfigData(
            id = "config", name = "configured model",
            apiProviderType = ApiProviderType.OPENAI,
            modelName = "gpt-5.6-luna",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.OPENAI.name),
            thinkingOptionId = "low",
        )
        val result = CollaborationModelParameters.overrideConfig(config, 0, "high")
        assertEquals("high", result.thinkingOptionId)
        assertEquals("low", config.thinkingOptionId)
        assertSame(config, CollaborationModelParameters.overrideConfig(config, 0, null))
    }

    @Test fun unsupportedEffortIsRejectedBeforeBuildingAProviderRequest() {
        val config = ModelConfigData(id = "config", name = "unconfigured", modelName = "model")
        val failure = runCatching { CollaborationModelParameters.overrideConfig(config, 0, "high") }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertTrue(failure.exceptionOrNull()!!.message!!.contains("not configured"))
    }
}
