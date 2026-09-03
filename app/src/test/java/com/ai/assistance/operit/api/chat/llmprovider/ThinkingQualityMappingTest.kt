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

    private fun presetOptions(providerTypeId: String, presetId: String): List<String> {
        val rules = JSONArray(ModelThinkingConfigDefaults.forProvider(providerTypeId))
        val options = (0 until rules.length())
            .mapNotNull { index -> rules.optJSONObject(index) }
            .first { rule -> rule.optString("id") == presetId }
            .getJSONArray("options")
        return (0 until options.length()).map { index -> options.getJSONObject(index).getString("id") }
    }


    @Test
    fun xaiMapsSupportedModelsToReasoningEfforts() {
        val mapping = mapping(ApiProviderType.XAI, "grok-4.6")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("reasoning_effort", mapping.parameterLabel)
        assertEquals(listOf("low", "medium", "high", "xhigh"), mapping.options.map { it.displayLabel })

        assertEquals("high", mapping.textValueFor("high"))
    }

    @Test
    fun xaiLeavesUndocumentedLegacyGrokModelsUnconfigured() {
        val mapping = mapping(ApiProviderType.XAI, "grok-3-mini")

        assertEquals(ThinkingQualityControl.UNSUPPORTED, mapping.control)
        assertTrue(mapping.options.isEmpty())
    }

    @Test
    fun openAiUsesCurrentReasoningEffortValues() {
        val mapping = mapping(ApiProviderType.OPENAI, "gpt-5.6-luna")

        assertEquals(
            listOf("minimal", "low", "medium", "high", "xhigh", "max"),
            mapping.options.map { it.displayLabel }
        )
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
    fun deepSeekResponsesWritesEverySelectedReasoningEffort() {
        listOf("low", "high", "max").forEach { optionId ->
            val request = JSONObject()
            ThinkingConfigurationApplier.apply(
                requestJson = request,
                providerTypeId = ApiProviderType.DEEPSEEK.name,
                modelName = "deepseek-reasoner",
                apiEndpoint = "https://api.deepseek.com/v1/responses",
                thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.DEEPSEEK.name),
                enableThinking = true,
                optionId = optionId,
            )

            assertEquals(
                optionId,
                request.getJSONObject("reasoning").getString("effort"),
            )
        }
    }

    @Test
    fun selectedDeepSeekResponsesEffortReplacesStaleRequestValues() {
        val request = JSONObject()
            .put("thinking", JSONObject().put("type", "enabled"))
            .put("reasoning_effort", "high")
            .put("reasoning", JSONObject().put("effort", "high"))

        ThinkingConfigurationApplier.apply(
            requestJson = request,
            providerTypeId = ApiProviderType.DEEPSEEK.name,
            modelName = "deepseek-reasoner",
            apiEndpoint = "https://api.deepseek.com/v1/responses",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.DEEPSEEK.name),
            enableThinking = true,
            optionId = "low",
        )

        assertEquals("low", request.getJSONObject("reasoning").getString("effort"))
    }

    @Test
    fun invalidSelectedEffortFallsBackToLowestEnabledOption() {
        val request = JSONObject()
        ThinkingConfigurationApplier.apply(
            requestJson = request,
            providerTypeId = ApiProviderType.DEEPSEEK.name,
            modelName = "deepseek-reasoner",
            apiEndpoint = "https://api.deepseek.com/v1/responses",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.DEEPSEEK.name),
            enableThinking = true,
            optionId = "legacy-option",
        )

        assertEquals("low", request.getJSONObject("reasoning").getString("effort"))
    }

    @Test
    fun builtInLevelPresetsUseAscendingOptionOrder() {
        assertEquals(
            listOf("low", "medium", "high", "xhigh"),
            presetOptions(ApiProviderType.XAI.name, "xai-grok-46-reasoning-effort"),
        )
        assertEquals(
            listOf("low", "high", "max"),
            presetOptions(ApiProviderType.MOONSHOT.name, "moonshot-kimi-k3-reasoning-effort"),
        )
        assertEquals(
            listOf("low", "high", "max"),
            presetOptions(ApiProviderType.ZHIPU.name, "zhipu-glm-53-required-effort"),
        )
        assertEquals(
            listOf("off", "low", "high", "max"),
            presetOptions(ApiProviderType.DEEPSEEK.name, "deepseek-responses-reasoning-effort"),
        )
        assertEquals(
            listOf("1024", "4096", "8192", "16384"),
            presetOptions(ApiProviderType.OTHER.name, "other-claude-extended-thinking"),
        )
        assertEquals(
            listOf("MINIMAL", "LOW", "MEDIUM", "HIGH"),
            presetOptions(ApiProviderType.OTHER.name, "other-gemini-3-thinking-level"),
        )
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

    @Test
    fun currentModelGenerationsOverrideLegacyThinkingRules() {
        val gpt5 = mapping(ApiProviderType.OPENAI, "gpt-5.6-luna")
        val gemini2 = mapping(ApiProviderType.GOOGLE, "gemini-2.0-flash")
        val claude45 = mapping(ApiProviderType.ANTHROPIC, "claude-sonnet-4-5-20250929")
        val claude46 = mapping(ApiProviderType.ANTHROPIC, "claude-sonnet-4-6")
        val kimiK3 = mapping(ApiProviderType.MOONSHOT, "kimi-k3")
        val kimiK27 = mapping(ApiProviderType.MOONSHOT, "kimi-k2.7-code")
        val kimiK26 = mapping(ApiProviderType.MOONSHOT, "kimi-k2.6")

        assertEquals(listOf("minimal", "low", "medium", "high", "xhigh", "max"), gpt5.options.map { it.id })
        assertEquals(ThinkingQualityControl.UNSUPPORTED, gemini2.control)
        assertEquals("thinking.budget_tokens", claude45.parameterLabel)
        assertEquals("output_config.effort", claude46.parameterLabel)
        assertEquals("reasoning_effort", kimiK3.parameterLabel)
        assertEquals(ThinkingQualityControl.UNSUPPORTED, kimiK27.control)
        assertEquals(ThinkingQualityControl.TOGGLE_ONLY, kimiK26.control)
    }

    @Test
    fun documentedNativeProviderPresetsWriteTheirOfficialFields() {
        val qwen = mapping(ApiProviderType.ALIYUN, "qwen3.6-27b")
        val mistral = mapping(ApiProviderType.MISTRAL, "mistral-medium-3-5")
        val minimax = mapping(ApiProviderType.MINIMAX, "MiniMax-M3")
        val antLing = mapping(ApiProviderType.ALIPAY_BAILING, "Ring-2.6-1T")
        val infiniQwen = mapping(ApiProviderType.INFINIAI, "qwen3.6-27b")
        val request = JSONObject()

        ThinkingConfigurationApplier.apply(
            requestJson = request,
            providerTypeId = ApiProviderType.ALIYUN.name,
            modelName = "qwen3.6-27b",
            apiEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.ALIYUN.name),
            enableThinking = true,
            optionId = "",
        )

        assertEquals(ThinkingQualityControl.TOGGLE_ONLY, qwen.control)
        assertEquals(listOf("off", "high"), mistral.options.map { it.id })
        assertEquals("thinking.type", minimax.parameterLabel)
        assertEquals(listOf("high", "xhigh"), antLing.options.map { it.id })
        assertEquals("enable_thinking", infiniQwen.parameterLabel)
        assertTrue(request.getBoolean("enable_thinking"))
    }

    @Test
    fun xunfeiSparkXUsesTheDocumentedThinkingToggle() {
        val sparkX = mapping(ApiProviderType.XUNFEI, "spark-x")
        val legacySpark = mapping(ApiProviderType.XUNFEI, "spark3.5")
        val request = JSONObject()

        ThinkingConfigurationApplier.apply(
            requestJson = request,
            providerTypeId = ApiProviderType.XUNFEI.name,
            modelName = "spark-x",
            apiEndpoint = "https://spark-api-open.xf-yun.com/x2/chat/completions",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.XUNFEI.name),
            enableThinking = true,
            optionId = "",
        )

        assertEquals(ThinkingQualityControl.TOGGLE_ONLY, sparkX.control)
        assertEquals("thinking.type", sparkX.parameterLabel)
        assertEquals(ThinkingQualityControl.UNSUPPORTED, legacySpark.control)
        assertEquals("enabled", request.getJSONObject("thinking").getString("type"))
    }
}
