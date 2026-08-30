package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingQualityMappingTest {
    private fun mapping(
        providerType: ApiProviderType,
        modelName: String,
        apiEndpoint: String = ""
    ): ThinkingQualityMapping =
        ThinkingQualityMappingRegistry.resolve(
            providerTypeId = providerType.name,
            modelName = modelName,
            apiEndpoint = apiEndpoint,
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(providerType.name)
        )

    @Test
    fun xaiMapsSupportedModelsToReasoningEfforts() {
        val mapping = mapping(ApiProviderType.XAI, "grok-4.6")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("reasoning_effort", mapping.parameterLabel)
        assertEquals(listOf("low", "medium", "high", "xhigh"), mapping.options.map { it.displayLabel })
        assertEquals("high", mapping.textValueFor("high"))
    }

    @Test
    fun xaiKeepsGenericControlsForNewGrokModels() {
        val mapping = mapping(ApiProviderType.XAI, "grok-3-mini")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertTrue(mapping.options.isNotEmpty())
    }

    @Test
    fun openAiUsesFiveNamedEffortValues() {
        val mapping = mapping(ApiProviderType.OPENAI, "gpt-5.6-luna")

        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), mapping.options.map { it.displayLabel })
        assertEquals("high", mapping.textValueFor("high"))
    }

    @Test
    fun providersUseModelSpecificWireValues() {
        val gemini = mapping(ApiProviderType.GOOGLE, "gemini-3-flash")
        val deepseek = mapping(ApiProviderType.DEEPSEEK, "deepseek-reasoner")

        assertEquals(listOf("MINIMAL", "LOW", "MEDIUM", "HIGH"), gemini.options.map { it.displayLabel })
        assertEquals(listOf("low", "high", "max"), deepseek.options.map { it.displayLabel })
        assertEquals(listOf("low", "high", "max"), deepseek.options.map { deepseek.textValueFor(it.id) })
    }
    @Test
    fun deepSeekChatRemainsInTheDeepSeekFamilyMapping() {
        val mapping = mapping(
            ApiProviderType.DEEPSEEK,
            "deepseek-chat",
            "https://api.deepseek.com/v1/chat/completions"
        )

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals(3, mapping.options.size)
        assertEquals(listOf("low", "high", "max"), mapping.options.map { it.id })
    }

    @Test
    fun deepSeekResponsesExposeFourDistinctReasoningLevels() {
        val mapping = mapping(
            ApiProviderType.DEEPSEEK,
            "deepseek-reasoner",
            "https://api.deepseek.com/v1/responses/"
        )

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("reasoning.effort", mapping.parameterLabel)
        assertTrue(mapping.reasoningRequired)
        assertEquals(listOf("off", "low", "high", "max"), mapping.options.map { it.id })
        assertEquals(listOf("none", "low", "high", "max"), mapping.options.map { mapping.textValueFor(it.id) })
    }

    @Test
    fun deepSeekResponsesOffWritesNoneWithoutChatThinkingFlag() {
        val request = JSONObject()
        ThinkingConfigurationApplier.apply(
            requestJson = request,
            providerTypeId = ApiProviderType.DEEPSEEK.name,
            modelName = "deepseek-reasoner",
            apiEndpoint = "https://api.deepseek.com/v1/responses?stream=true",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.DEEPSEEK.name),
            enableThinking = false,
            optionId = "off",
        )

        assertEquals("none", request.getJSONObject("reasoning").getString("effort"))
        assertFalse(request.has("thinking"))
        assertFalse(request.has("reasoning_effort"))
    }

    @Test
    fun deepSeekResponsesOffOverridesStaleSelectedLevel() {
        val request = JSONObject()
        ThinkingConfigurationApplier.apply(
            requestJson = request,
            providerTypeId = ApiProviderType.DEEPSEEK.name,
            modelName = "deepseek-reasoner",
            apiEndpoint = "https://api.deepseek.com/v1/responses?stream=true",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.DEEPSEEK.name),
            enableThinking = false,
            optionId = "high",
        )

        assertEquals("none", request.getJSONObject("reasoning").getString("effort"))
    }

    @Test
    fun restoringDeepSeekResponsesPresetKeepsBuiltInRulePrecedence() {
        val providerTypeId = ApiProviderType.DEEPSEEK.name
        val responsesPresetId = "deepseek-responses-reasoning-effort"
        val chatPresetId = "deepseek-reasoning-effort"
        val defaults = JSONArray(ModelThinkingConfigDefaults.forProvider(providerTypeId))
        val chatRule = (0 until defaults.length())
            .mapNotNull { index -> defaults.optJSONObject(index) }
            .first { rule -> rule.optString("id") == chatPresetId }
        val chatOnlyConfigurations = JSONArray()
            .put(JSONObject(chatRule.toString()))
            .toString()

        assertEquals(
            listOf(responsesPresetId),
            ModelThinkingConfigDefaults.missingPresetIdsForProvider(
                providerTypeId,
                chatOnlyConfigurations,
            )
        )

        val restoredConfigurations = ModelThinkingConfigDefaults.mergeSelectedPresetsForProvider(
            providerTypeId = providerTypeId,
            currentConfigurations = chatOnlyConfigurations,
            selectedPresetIds = setOf(responsesPresetId),
        )
        val restoredRules = JSONArray(restoredConfigurations)
        assertEquals(responsesPresetId, restoredRules.getJSONObject(0).getString("id"))
        assertEquals(chatPresetId, restoredRules.getJSONObject(1).getString("id"))

        val restoredAgain = ModelThinkingConfigDefaults.mergeSelectedPresetsForProvider(
            providerTypeId = providerTypeId,
            currentConfigurations = restoredConfigurations,
            selectedPresetIds = setOf(responsesPresetId),
        )
        assertEquals(2, JSONArray(restoredAgain).length())
    }

    @Test
    fun providerSpecificModelMatchingControlsLevelSupport() {
        val gptOss = mapping(ApiProviderType.NVIDIA, "gpt-oss-120b")
        val otherNvidiaModel = mapping(ApiProviderType.NVIDIA, "nemotron-future")

        assertTrue(gptOss.options.isNotEmpty())
        assertTrue(otherNvidiaModel.options.isNotEmpty())
    }

    @Test
    fun numericProviderValuesRemainTyped() {
        val mapping = mapping(ApiProviderType.SILICONFLOW, "Qwen3")

        assertEquals("128", mapping.options.first().displayLabel)
        assertEquals(8_192, mapping.numberValueFor("8192"))
    }

    @Test
    fun zhipuGlm53UsesRequiredReasoningEffortLevels() {
        val mapping = mapping(ApiProviderType.ZHIPU, "glm-5.3")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("reasoning_effort", mapping.parameterLabel)
        assertTrue(mapping.reasoningRequired)
        assertEquals(listOf("low", "high", "max"), mapping.options.map { it.id })
    }

    @Test
    fun zhipuOlderThinkingModelsUseToggleOnly() {
        val mapping = mapping(ApiProviderType.ZHIPU, "glm-4.7-thinking")

        assertEquals(ThinkingQualityControl.TOGGLE_ONLY, mapping.control)
        assertEquals("thinking.type", mapping.parameterLabel)
        assertFalse(mapping.reasoningRequired)
    }

    @Test
    fun zhipuLegacyModelsDoNotShowThinkingControls() {
        val mapping = mapping(ApiProviderType.ZHIPU, "glm-3-turbo")

        assertEquals(ThinkingQualityControl.UNSUPPORTED, mapping.control)
    }
}
