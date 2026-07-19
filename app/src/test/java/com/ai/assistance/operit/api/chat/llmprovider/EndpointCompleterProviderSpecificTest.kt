package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointCompleterProviderSpecificTest {

    @Test fun openAiResponsesGeneric_usesResponsesCompletion() {
        assertEquals(
            "https://api.example.com/v1/responses",
            EndpointCompleter.completeEndpoint("https://api.example.com", ApiProviderType.OPENAI_RESPONSES_GENERIC)
        )
    }

    @Test fun anthropicGeneric_usesMessagesCompletion() {
        assertEquals(
            "https://proxy.example.com/v1/messages",
            EndpointCompleter.completeEndpoint("https://proxy.example.com", ApiProviderType.ANTHROPIC_GENERIC)
        )
    }

    @Test fun atlasCloud_usesOpenAiCompatibleChatCompletion() {
        assertEquals(
            "https://api.atlascloud.ai/v1/chat/completions",
            EndpointCompleter.completeEndpoint("https://api.atlascloud.ai/v1", ApiProviderType.ATLASCLOUD)
        )
    }

    @Test fun googleProvider_withHashReturnsRawUrlWithoutHash() {
        assertEquals(
            "https://example.com",
            EndpointCompleter.completeEndpoint("https://example.com#", ApiProviderType.GOOGLE)
        )
    }

    @Test fun mnnProvider_withWhitespaceAndHashReturnsTrimmedRawUrl() {
        assertEquals(
            "http://127.0.0.1:8080",
            EndpointCompleter.completeEndpoint("  http://127.0.0.1:8080#  ", ApiProviderType.MNN)
        )
    }
}
