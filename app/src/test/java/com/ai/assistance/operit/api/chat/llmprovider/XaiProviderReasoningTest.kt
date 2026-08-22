package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.util.AppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun enabledQualityLevelsMapToXaiEfforts() {
        assertEquals(
            listOf("low", "medium", "medium", "high", "xhigh"),
            (1..5).map { XaiReasoningMapper.effortForQuality(enableThinking = true, qualityLevel = it) }
        )
    }

    @Test
    fun disabledThinkingUsesTheLowestSupportedEffort() {
        assertEquals(
            List(5) { "low" },
            (1..5).map { XaiReasoningMapper.effortForQuality(enableThinking = false, qualityLevel = it) }
        )
    }

    @Test
    fun reasoningEffortIsLimitedToDocumentedGrokModels() {
        assertTrue(xaiModelSupportsReasoningEffort("grok-4.6"))
        assertTrue(xaiModelSupportsReasoningEffort("grok-4.5-latest"))
        assertFalse(xaiModelSupportsReasoningEffort("grok-3-mini"))
    }
}
