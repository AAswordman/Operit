package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            listOf("low", "medium", "high", "xhigh"),
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
    fun grokFamilyUsesTheConfiguredReasoningRule() {
        listOf("grok-4.6", "grok-4.5-latest", "grok-3-mini").forEach { modelName ->
            val mapping = xaiMapping(modelName)
            assertEquals(ThinkingQualityControl.LEVELS, mapping.control)
            assertTrue(mapping.options.isNotEmpty())
        }
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
