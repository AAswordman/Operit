package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelListFetcherAtlasCloudTest {

    @Test fun atlasCloud_usesOpenAiCompatibleModelsEndpoint() {
        assertEquals(
            "https://api.atlascloud.ai/v1/models",
            ModelListFetcher.getModelsListUrl(
                "https://api.atlascloud.ai/v1/chat/completions",
                ApiProviderType.ATLASCLOUD
            )
        )
    }
}
