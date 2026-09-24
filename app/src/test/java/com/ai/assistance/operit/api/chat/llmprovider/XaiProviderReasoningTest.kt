package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
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
}
