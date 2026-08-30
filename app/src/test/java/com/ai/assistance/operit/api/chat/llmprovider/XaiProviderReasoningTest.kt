package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class XaiProviderReasoningTest {
    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    @Test
    fun defaultConfigUsesTheOfficialXaiEndpointAndModel() {
        assertEquals(
            "grok-4.6",
            ApiProviderConfigs.getDefaultModelName(ApiProviderType.XAI)
        )
        assertEquals(
            "https://api.x.ai/v1/chat/completions",
            ApiProviderConfigs.getDefaultApiEndpoint(ApiProviderType.XAI)
        )
        assertEquals(
            "https://api.x.ai/v1/models",
            ModelListFetcher.getModelsListUrl(
                "https://api.x.ai/v1/chat/completions",
                ApiProviderType.XAI
            )
        )
    }

    @Test
    fun configuredGrokOptionsMapToReasoningEfforts() {
        val mapping = xaiMapping("grok-4.6")

        assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
        assertEquals("reasoning_effort", mapping.parameterLabel)
        assertEquals(
            listOf("high", "low", "medium", "xhigh"),
            mapping.options.map { it.id }
        )
    }

    @Test
    fun selectedGrokEffortIsWrittenToTheRequest() {
        val request = JSONObject()

        ThinkingConfigurationApplier.apply(
            requestJson = request,
            providerTypeId = ApiProviderType.XAI.name,
            modelName = "grok-4.6",
            apiEndpoint = "https://api.x.ai/v1/chat/completions",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.XAI.name),
            enableThinking = true,
            optionId = "high",
        )

        assertEquals("high", request.getString("reasoning_effort"))
    }

    @Test
    fun supportedGrokGenerationsExposeTheirDocumentedEfforts() {
        val grok46 = xaiMapping("grok-4.6")
        val grok45 = xaiMapping("grok-4.5-latest")
        val legacyGrok = xaiMapping("grok-3-mini")

        assertEquals(listOf("high", "low", "medium", "xhigh"), grok46.options.map { it.id })
        assertEquals(listOf("high", "low", "medium"), grok45.options.map { it.id })
        assertEquals(ThinkingQualityControl.UNSUPPORTED, legacyGrok.control)
    }

    @Test
    fun restoredCurrentGrokPresetPrecedesTheLegacyRule() {
        val legacyConfigurations =
            """[{"id":"xai-grok-reasoning-effort","control":"levels","parameterLabel":"reasoning_effort"}]"""

        val merged = ModelThinkingConfigDefaults.mergeSelectedPresetsForProvider(
            providerTypeId = ApiProviderType.XAI.name,
            currentConfigurations = legacyConfigurations,
            selectedPresetIds = setOf("xai-grok-46-reasoning-effort"),
        )
        val rules = JSONArray(merged)

        assertEquals("xai-grok-46-reasoning-effort", rules.getJSONObject(0).getString("id"))
        assertEquals("xai-grok-reasoning-effort", rules.getJSONObject(1).getString("id"))
    }

    private fun xaiMapping(modelName: String): ThinkingQualityMapping {
        return ThinkingQualityMappingRegistry.resolve(
            providerTypeId = ApiProviderType.XAI.name,
            modelName = modelName,
            apiEndpoint = "https://api.x.ai/v1/chat/completions",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.XAI.name),
        )
    }
}
