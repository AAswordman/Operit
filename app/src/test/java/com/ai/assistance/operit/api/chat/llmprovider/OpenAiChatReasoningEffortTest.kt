package com.ai.assistance.operit.api.chat.llmprovider

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenAI's Chat Completions endpoint rejects reasoning_effort with a 400 on models that
 * do not reason, so the parameter must only reach the wire for models that accept it.
 */
class OpenAiChatReasoningEffortTest {
    private val legacyOpenAiChatReasoningEffortRule =
        """
        {
          "id": "openai-chat-reasoning-effort",
          "control": "levels",
          "parameterLabel": "reasoning_effort",
          "options": [
            {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
            {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
            {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
            {"id": "xhigh", "label": "xhigh", "path": "reasoning_effort", "value": "xhigh"},
            {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
          ]
        }
        """.trimIndent()

    private val legacyOpenAiChatReasoningEffortRules =
        "[$legacyOpenAiChatReasoningEffortRule]"

    private fun mapping(provider: ApiProviderType, modelName: String): ThinkingQualityMapping =
        ThinkingQualityMappingRegistry.resolve(
            providerTypeId = provider.name,
            modelName = modelName,
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(provider.name)
        )

    /** Mirrors what OpenAIProvider.createRequestBody sends for a freshly seeded config. */
    private fun requestBody(
        provider: ApiProviderType,
        modelName: String,
        enableThinking: Boolean
    ): JSONObject {
        val thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(provider.name)
        val requestJson = JSONObject().put("model", modelName).put("stream", true)
        ThinkingConfigurationApplier.apply(
            requestJson = requestJson,
            providerTypeId = provider.name,
            modelName = modelName,
            apiEndpoint = "https://api.openai.com/v1/chat/completions",
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            // ModelConfigManager seeds a config with the first option its mapping offers.
            optionId = mapping(provider, modelName).options.firstOrNull()?.id.orEmpty(),
        )
        return requestJson
    }

    private fun migrateConfig(config: ModelConfigData): ModelConfigData {
        val configKey = stringPreferencesKey("config_${config.id}")
        val preferences = mutablePreferencesOf(
            ModelConfigManager.CONFIG_LIST_KEY to ModelConfigManager.json.encodeToString(listOf(config.id)),
            configKey to ModelConfigManager.json.encodeToString(config),
        )

        ModelConfigManager.migratePreferencesFromVersionTwo(preferences)

        return ModelConfigManager.json.decodeFromString(preferences[configKey]!!)
    }

    private fun requestBodyFor(config: ModelConfigData, enableThinking: Boolean): JSONObject {
        val requestJson = JSONObject().put("model", config.modelName).put("stream", true)
        ThinkingConfigurationApplier.apply(
            requestJson = requestJson,
            providerTypeId = config.apiProviderTypeId,
            modelName = config.modelName,
            apiEndpoint = "https://api.openai.com/v1/chat/completions",
            thinkingConfigurations = config.thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = config.thinkingOptionId,
        )
        return requestJson
    }

    @Test
    fun openAiChatModelsNeverReceiveReasoningEffort() {
        for (model in listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-3.5-turbo", "chatgpt-4o-latest")) {
            assertEquals(model, ThinkingQualityControl.UNSUPPORTED, mapping(ApiProviderType.OPENAI, model).control)
            assertFalse(model, requestBody(ApiProviderType.OPENAI, model, enableThinking = true).has("reasoning_effort"))
            assertFalse(model, requestBody(ApiProviderType.OPENAI, model, enableThinking = false).has("reasoning_effort"))
        }
    }

    @Test
    fun openAiReasoningModelsKeepReasoningEffortWhenThinkingIsOn() {
        for (model in listOf("o3", "o4-mini", "gpt-5-mini", "gpt-5.6-luna", "gpt-oss-120b", "codex-mini-latest")) {
            assertEquals(model, ThinkingQualityControl.LEVELS, mapping(ApiProviderType.OPENAI, model).control)
            assertEquals(model, "minimal", requestBody(ApiProviderType.OPENAI, model, enableThinking = true).getString("reasoning_effort"))
        }
    }

    @Test
    fun openAiReasoningModelsOmitReasoningEffortWhenThinkingIsOff() {
        assertFalse(requestBody(ApiProviderType.OPENAI, "o3", enableThinking = false).has("reasoning_effort"))
    }

    @Test
    fun compatibleEndpointsDropReasoningEffortForOpenAiChatModels() {
        assertEquals(ThinkingQualityControl.UNSUPPORTED, mapping(ApiProviderType.OPENAI_GENERIC, "gpt-4o").control)
        assertFalse(requestBody(ApiProviderType.OPENAI_GENERIC, "gpt-4o", enableThinking = true).has("reasoning_effort"))
    }

    @Test
    fun compatibleEndpointsKeepReasoningEffortForThirdPartyModels() {
        for (model in listOf("deepseek-reasoner", "glm-4.6", "qwen3-235b-a22b")) {
            assertEquals(model, ThinkingQualityControl.LEVELS, mapping(ApiProviderType.OPENAI_GENERIC, model).control)
            assertEquals(model, "low", requestBody(ApiProviderType.OPENAI_GENERIC, model, enableThinking = true).getString("reasoning_effort"))
        }
    }

    @Test
    fun migrationUpdatesExistingOpenAiChatConfigs() {
        val migrated = migrateConfig(
            ModelConfigData(
                id = "openai-chat",
                name = "OpenAI Chat",
                modelName = "gpt-4o",
                apiProviderType = ApiProviderType.OPENAI,
                apiProviderTypeId = ApiProviderType.OPENAI.name,
                thinkingConfigurations = legacyOpenAiChatReasoningEffortRules,
                thinkingOptionId = "low",
            )
        )

        val mapping = ThinkingQualityMappingRegistry.resolve(
            providerTypeId = migrated.apiProviderTypeId,
            modelName = migrated.modelName,
            thinkingConfigurations = migrated.thinkingConfigurations,
        )
        assertEquals(ThinkingQualityControl.UNSUPPORTED, mapping.control)
        assertEquals("", migrated.thinkingOptionId)
        assertFalse(requestBodyFor(migrated, enableThinking = true).has("reasoning_effort"))
    }

    @Test
    fun migrationKeepsReasoningEffortForExistingOpenAiReasoningModels() {
        val migrated = migrateConfig(
            ModelConfigData(
                id = "openai-reasoning",
                name = "OpenAI Reasoning",
                modelName = "o3",
                apiProviderType = ApiProviderType.OPENAI,
                apiProviderTypeId = ApiProviderType.OPENAI.name,
                thinkingConfigurations = legacyOpenAiChatReasoningEffortRules,
                thinkingOptionId = "low",
            )
        )

        assertEquals(
            ThinkingQualityControl.LEVELS,
            ThinkingQualityMappingRegistry.resolve(
                providerTypeId = migrated.apiProviderTypeId,
                modelName = migrated.modelName,
                thinkingConfigurations = migrated.thinkingConfigurations,
            ).control
        )
        assertEquals("low", migrated.thinkingOptionId)
        assertEquals("low", requestBodyFor(migrated, enableThinking = true).getString("reasoning_effort"))
    }

    @Test
    fun migrationUpdatesExistingCompatibleOpenAiChatConfigs() {
        val migrated = migrateConfig(
            ModelConfigData(
                id = "compatible-chat",
                name = "Compatible Chat",
                modelName = "gpt-4o",
                apiProviderType = ApiProviderType.OPENAI_GENERIC,
                apiProviderTypeId = ApiProviderType.OPENAI_GENERIC.name,
                thinkingConfigurations = legacyOpenAiChatReasoningEffortRules,
                thinkingOptionId = "low",
            )
        )
        val migratedRules = JSONArray(migrated.thinkingConfigurations)

        assertEquals("openai-gpt5-chat-reasoning-effort", migratedRules.getJSONObject(0).getString("id"))
        assertTrue(migratedRules.getJSONObject(0).has("match"))
        assertEquals("openai-chat-non-reasoning-models", migratedRules.getJSONObject(1).getString("id"))
        assertEquals("openai-compatible-chat-reasoning-effort", migratedRules.getJSONObject(2).getString("id"))
        assertEquals(
            ThinkingQualityControl.UNSUPPORTED,
            ThinkingQualityMappingRegistry.resolve(
                providerTypeId = migrated.apiProviderTypeId,
                modelName = migrated.modelName,
                thinkingConfigurations = migrated.thinkingConfigurations,
            ).control
        )
        assertFalse(requestBodyFor(migrated, enableThinking = true).has("reasoning_effort"))
    }

    @Test
    fun migrationKeepsCustomThinkingRules() {
        val customRule =
            """
            {
              "id": "custom-thinking-rule",
              "match": {"modelContains": ["custom"]},
              "control": "toggle_only",
              "parameterLabel": "custom_thinking",
              "enable": [{"path": "custom_thinking", "value": true}],
              "disable": [{"path": "custom_thinking", "value": false}]
            }
            """.trimIndent()
        val migrated = migrateConfig(
            ModelConfigData(
                id = "compatible-custom",
                name = "Compatible Custom",
                modelName = "custom-model",
                apiProviderType = ApiProviderType.OPENAI_GENERIC,
                apiProviderTypeId = ApiProviderType.OPENAI_GENERIC.name,
                thinkingConfigurations = "[$legacyOpenAiChatReasoningEffortRule,$customRule]",
                thinkingOptionId = "low",
            )
        )
        val migratedRules = JSONArray(migrated.thinkingConfigurations)
        val migratedRuleIds = (0 until migratedRules.length()).map {
            migratedRules.getJSONObject(it).getString("id")
        }

        assertTrue(migratedRuleIds.contains("custom-thinking-rule"))
    }
}
