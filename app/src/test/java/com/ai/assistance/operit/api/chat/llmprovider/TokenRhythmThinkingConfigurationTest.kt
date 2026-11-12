package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TokenRhythm routes many model families through one Chat Completions endpoint, and each
 * family accepts different reasoning controls. Several always-on models return HTTP 400
 * ("当前模型必须开启深度思考") when a disable field is sent, so those must never receive one.
 */
class TokenRhythmThinkingConfigurationTest {
    private val provider = ApiProviderType.TOKENRHYTHM.name

    private fun mapping(modelName: String): ThinkingQualityMapping =
        ThinkingQualityMappingRegistry.resolve(
            providerTypeId = provider,
            modelName = modelName,
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(provider)
        )

    /** Mirrors what OpenAIProvider.createRequestBody sends for a config seeded with [optionId]. */
    private fun requestBody(modelName: String, enableThinking: Boolean, optionId: String = ""): JSONObject {
        val requestJson = JSONObject().put("model", modelName).put("stream", true)
        ThinkingConfigurationApplier.apply(
            requestJson = requestJson,
            providerTypeId = provider,
            modelName = modelName,
            apiEndpoint = "https://tokenrhythm.studio/v1/chat/completions",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(provider),
            enableThinking = enableThinking,
            optionId = optionId,
        )
        return requestJson
    }

    @Test
    fun deepSeekModelsOfferToggleAndEffortLevels() {
        for (model in listOf("deepseek-v4-pro-0813", "deepseek-v4-flash-0731", "deepseek-flash")) {
            val mapping = mapping(model)
            assertEquals(model, ThinkingQualityControl.LEVELS, mapping.control)
            assertEquals(model, "reasoning_effort", mapping.parameterLabel)
            assertEquals(model, listOf("low", "high", "max"), mapping.options.map { it.id })
            assertFalse(model, mapping.reasoningRequired)
        }
    }

    @Test
    fun deepSeekEnabledSendsThinkingTypeAndEffort() {
        // The dated V4 models do not think unless thinking.type is enabled explicitly.
        val body = requestBody("deepseek-v4-pro-0813", enableThinking = true, optionId = "max")

        assertEquals("enabled", body.getJSONObject("thinking").getString("type"))
        assertEquals("max", body.getString("reasoning_effort"))
    }

    @Test
    fun deepSeekDisabledSendsOnlyThinkingTypeDisabled() {
        val body = requestBody("deepseek-v4-pro-0813", enableThinking = false, optionId = "max")

        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertFalse(body.has("reasoning_effort"))
    }

    @Test
    fun qwenAndSeedModelsOfferToggleOnly() {
        for (model in listOf(
            "qwen3.7-flash", "qwen3.7-max", "qwen3.8-27b", "qwen3.8-flash", "qwen3.8-max",
            "seed-2.1-pro", "seed-2.1-turbo"
        )) {
            val mapping = mapping(model)
            assertEquals(model, ThinkingQualityControl.TOGGLE_ONLY, mapping.control)
            assertFalse(model, mapping.reasoningRequired)

            assertEquals(model, "enabled", requestBody(model, enableThinking = true).getJSONObject("thinking").getString("type"))
            val disabled = requestBody(model, enableThinking = false)
            assertEquals(model, "disabled", disabled.getJSONObject("thinking").getString("type"))
            // reasoning_effort is accepted but ignored by these models on TokenRhythm.
            assertFalse(model, disabled.has("reasoning_effort"))
        }
    }

    @Test
    fun alwaysOnModelsAreRequiredAndNeverReceiveReasoningFields() {
        for (model in listOf(
            "glm-5.1", "glm-5.2", "glm-5.3", "glm-5.3-flash", "kimi-k2.6", "kimi-k2.7-code",
            "longcat-2.0", "minimax-m2.7", "mimo-v2.5-pro"
        )) {
            val mapping = mapping(model)
            assertEquals(model, ThinkingQualityControl.TOGGLE_ONLY, mapping.control)
            assertTrue(model, mapping.reasoningRequired)

            for (enableThinking in listOf(true, false)) {
                val body = requestBody(model, enableThinking)
                assertFalse("$model thinking=$enableThinking", body.has("thinking"))
                assertFalse("$model thinking=$enableThinking", body.has("reasoning_effort"))
                assertFalse("$model thinking=$enableThinking", body.has("enable_thinking"))
                assertFalse("$model thinking=$enableThinking", body.has("reasoning"))
            }
        }
    }

    @Test
    fun unlistedModelsGetNoReasoningControl() {
        val mapping = mapping("some-future-model")

        assertEquals(ThinkingQualityControl.UNSUPPORTED, mapping.control)
        assertFalse(requestBody("some-future-model", enableThinking = false).has("thinking"))
    }
}
