package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XaiProviderReasoningTest {
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
    fun enabledOptionsMapToXaiEfforts() {
        assertEquals(
            listOf("low", "medium", "high", "xhigh"),
            listOf("low", "medium", "high", "xhigh").map {
                XaiReasoningMapper.effortForOption(optionId = it)
            }
        )
    }

    @Test
    fun mapperPreservesTheSelectedEffort() {
        assertEquals(
            "high",
            XaiReasoningMapper.effortForOption(optionId = "high")
        )
    }

    @Test
    fun reasoningEffortUsesTheGrokFamilyRule() {
        assertTrue(xaiModelSupportsReasoningEffort("grok-4.6"))
        assertTrue(xaiModelSupportsReasoningEffort("grok-4.5-latest"))
        assertTrue(xaiModelSupportsReasoningEffort("grok-3-mini"))
    }
}
