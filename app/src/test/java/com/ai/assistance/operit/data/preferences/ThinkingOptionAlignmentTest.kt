package com.ai.assistance.operit.data.preferences

import com.ai.assistance.operit.api.chat.llmprovider.ThinkingConfigurationApplier
import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * A stored thinking option must be listed by the levels rule it is sent with. Requests that do not
 * pass through the chat input, such as the config page's connection test, otherwise fail with
 * "option is not supported" before reaching the provider.
 */
class ThinkingOptionAlignmentTest {
    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    @Before
    fun disableAndroidLogging() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    private val minimaxEndpoint = "https://api.minimaxi.com/v1/chat/completions"
    private val openAiEndpoint = "https://api.openai.com/v1/chat/completions"

    // The rule the config page's "add thinking configuration" dialog saves with its default levels
    // when the model filter is left empty and "always think" is turned on.
    private val userLevelsRules =
        """
        [
          {
            "id": "user-reasoning-effort",
            "control": "levels",
            "parameterLabel": "reasoning_effort",
            "required": true,
            "options": [
              {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
              {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
              {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
            ]
          }
        ]
        """.trimIndent()

    private val userToggleRules =
        """
        [
          {
            "id": "user-thinking-toggle",
            "control": "toggle_only",
            "parameterLabel": "thinking.type",
            "enable": [{"path": "thinking.type", "value": "enabled"}],
            "disable": [{"path": "thinking.type", "value": "disabled"}]
          }
        ]
        """.trimIndent()

    private fun alignForSavedRules(storedOptionId: String, rules: String): String =
        ModelConfigManager.alignedThinkingOptionId(
            configId = "minimax",
            providerTypeId = ApiProviderType.MINIMAX.name,
            modelName = "MiniMax-M3.1-Flash",
            apiEndpoint = minimaxEndpoint,
            thinkingConfigurations = rules,
            storedOptionId = storedOptionId,
        )

    /** The connection test sends with thinking off; a required rule applies its option anyway. */
    private fun connectionTestRequest(rules: String, optionId: String): JSONObject {
        val requestJson = JSONObject()
        ThinkingConfigurationApplier.apply(
            requestJson = requestJson,
            providerTypeId = ApiProviderType.MINIMAX.name,
            modelName = "MiniMax-M3.1-Flash",
            apiEndpoint = minimaxEndpoint,
            thinkingConfigurations = rules,
            enableThinking = false,
            optionId = optionId,
        )
        return requestJson
    }

    private fun openAiConfig(modelName: String, thinkingOptionId: String): ModelConfigData =
        ModelConfigData(
            id = "openai",
            name = "OpenAI",
            apiEndpoint = openAiEndpoint,
            modelName = modelName,
            apiProviderType = ApiProviderType.OPENAI,
            apiProviderTypeId = ApiProviderType.OPENAI.name,
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.OPENAI.name),
            thinkingOptionId = thinkingOptionId,
        )

    private fun nextOptionWithinProvider(current: ModelConfigData, modelName: String): String {
        val nextRules =
            ModelConfigManager.nextThinkingRulesForProvider(current, current.apiProviderTypeId, current.apiEndpoint)
        return ModelConfigManager.nextThinkingOptionIdForProvider(
            current,
            current.apiProviderTypeId,
            modelName,
            current.apiEndpoint,
            nextRules,
        )
    }

    @Test
    fun requestValidationStillRejectsAnOptionTheRuleDoesNotList() {
        // Request-time validation is unchanged; this is the failure reported for MiniMax.
        val error = assertThrows(IllegalArgumentException::class.java) {
            connectionTestRequest(userLevelsRules, optionId = "")
        }
        assertEquals("MINIMAX option is not supported: ", error.message)
    }

    @Test
    fun savingLevelsRuleStoresItsFirstOptionWhenTheStoredOptionIsNotListed() {
        val aligned = alignForSavedRules(storedOptionId = "", rules = userLevelsRules)

        assertEquals("low", aligned)
        assertEquals("low", connectionTestRequest(userLevelsRules, aligned).getString("reasoning_effort"))
    }

    @Test
    fun savingRulesKeepsAStoredOptionThatIsStillListed() {
        assertEquals("high", alignForSavedRules(storedOptionId = "high", rules = userLevelsRules))
    }

    @Test
    fun savingRulesWithoutLevelsKeepsTheStoredOption() {
        assertEquals("high", alignForSavedRules(storedOptionId = "high", rules = userToggleRules))
        assertEquals("high", alignForSavedRules(storedOptionId = "high", rules = "[]"))
        assertEquals("", alignForSavedRules(storedOptionId = "", rules = "[]"))
    }

    @Test
    fun sameProviderModelChangeAlignsWithTheNewlyMatchedLevelsRule() {
        // gpt-4o matches no levels rule, so a fresh OpenAI config stores no option.
        val current = openAiConfig(modelName = "gpt-4o", thinkingOptionId = "")

        assertEquals("low", nextOptionWithinProvider(current, modelName = "o3"))
    }

    @Test
    fun sameProviderModelChangeKeepsAnOptionTheMatchedRuleStillLists() {
        val current = openAiConfig(modelName = "o3", thinkingOptionId = "high")

        assertEquals("high", nextOptionWithinProvider(current, modelName = "o4-mini"))
    }

    @Test
    fun sameProviderModelChangeOntoAModelWithoutLevelsKeepsTheStoredOption() {
        val current = openAiConfig(modelName = "o3", thinkingOptionId = "high")

        assertEquals("high", nextOptionWithinProvider(current, modelName = "gpt-4o"))
    }

    @Test
    fun providerChangeStillSeedsTheNewProvidersFirstOption() {
        val current =
            ModelConfigData(
                id = "generic",
                name = "Generic",
                apiEndpoint = minimaxEndpoint,
                modelName = "gpt-4o",
                apiProviderType = ApiProviderType.OPENAI_GENERIC,
                apiProviderTypeId = ApiProviderType.OPENAI_GENERIC.name,
                thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.OPENAI_GENERIC.name),
                thinkingOptionId = "",
            )
        val nextRules =
            ModelConfigManager.nextThinkingRulesForProvider(current, ApiProviderType.MINIMAX.name, minimaxEndpoint)

        assertEquals(ModelThinkingConfigDefaults.forProvider(ApiProviderType.MINIMAX.name), nextRules)
        assertEquals(
            "low",
            ModelConfigManager.nextThinkingOptionIdForProvider(
                current,
                ApiProviderType.MINIMAX.name,
                "MiniMax-M3.1-Flash",
                minimaxEndpoint,
                nextRules,
            )
        )
    }
}
