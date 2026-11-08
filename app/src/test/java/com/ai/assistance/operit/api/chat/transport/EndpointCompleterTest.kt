package com.ai.assistance.operit.api.chat.transport

import com.ai.assistance.operit.data.config.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointCompleterTest {

    @Test fun rootUrl_appendsChatCompletions() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            EndpointCompleter.completeEndpoint("https://api.example.com")
        )
    }

    @Test fun rootUrlWithTrailingSlash_appendsChatCompletions() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            EndpointCompleter.completeEndpoint("https://api.example.com/")
        )
    }

    @Test fun v1Path_appendsChatCompletionsSuffix() {
        assertEquals(
            "https://api.example.com/custom/v1/chat/completions",
            EndpointCompleter.completeEndpoint("https://api.example.com/custom/v1")
        )
    }

    @Test fun v1PathWithTrailingSlash_appendsChatCompletionsSuffix() {
        assertEquals(
            "https://api.example.com/custom/v1/chat/completions",
            EndpointCompleter.completeEndpoint("https://api.example.com/custom/v1/")
        )
    }

    @Test fun customNonV1Path_isUnchanged() {
        val endpoint = "https://api.example.com/custom/chat"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint))
    }

    @Test fun invalidUrl_isUnchanged() {
        val endpoint = "not a url"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint))
    }

    @Test fun trailingHash_disablesCompletion() {
        assertEquals("https://api.example.com", EndpointCompleter.completeEndpoint("https://api.example.com#"))
    }

    @Test fun openAiResponsesRoot_appendsResponsesPath() {
        assertEquals(
            "https://api.example.com/v1/responses",
            EndpointCompleter.completeEndpoint("https://api.example.com", ApiProviderType.OPENAI_RESPONSES)
        )
    }

    @Test fun openAiResponsesV1Path_appendsResponsesSuffix() {
        assertEquals(
            "https://api.example.com/proxy/v1/responses",
            EndpointCompleter.completeEndpoint("https://api.example.com/proxy/v1", ApiProviderType.OPENAI_RESPONSES)
        )
    }

    @Test fun openAiResponsesCustomPath_isUnchanged() {
        val endpoint = "https://api.example.com/custom/responses"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint, ApiProviderType.OPENAI_RESPONSES))
    }

    @Test fun openAiResponsesTrailingHash_disablesCompletion() {
        assertEquals(
            "https://api.example.com",
            EndpointCompleter.completeEndpoint("https://api.example.com#", ApiProviderType.OPENAI_RESPONSES)
        )
    }

    @Test fun anthropicRoot_appendsMessagesPath() {
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            EndpointCompleter.completeEndpoint("https://api.anthropic.com", ApiProviderType.ANTHROPIC)
        )
    }

    @Test fun anthropicBasePath_appendsMessagesPath() {
        assertEquals(
            "https://proxy.example.com/anthropic/v1/messages",
            EndpointCompleter.completeEndpoint("https://proxy.example.com/anthropic", ApiProviderType.ANTHROPIC)
        )
    }

    @Test fun anthropicV1Path_appendsMessagesSuffix() {
        assertEquals(
            "https://proxy.example.com/custom/v1/messages",
            EndpointCompleter.completeEndpoint("https://proxy.example.com/custom/v1", ApiProviderType.ANTHROPIC)
        )
    }

    @Test fun anthropicCustomPath_isUnchanged() {
        val endpoint = "https://proxy.example.com/custom/messages"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint, ApiProviderType.ANTHROPIC))
    }

    @Test fun anthropicInvalidUrl_isUnchanged() {
        val endpoint = "anthropic local"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint, ApiProviderType.ANTHROPIC))
    }

    @Test fun googleProvider_leavesEndpointUntouched() {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint, ApiProviderType.GOOGLE))
    }

    @Test fun geminiGenericProvider_leavesEndpointUntouched() {
        val endpoint = "https://proxy.example.com/google"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint, ApiProviderType.GEMINI_GENERIC))
    }

    @Test fun mnnProvider_leavesEndpointUntouched() {
        val endpoint = "http://127.0.0.1:8080"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint, ApiProviderType.MNN))
    }

    @Test fun openAiGeneric_usesDefaultCompletionLogic() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            EndpointCompleter.completeEndpoint("https://api.example.com", ApiProviderType.OPENAI_GENERIC)
        )
    }

    @Test fun otherProvider_usesDefaultCompletionLogic() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            EndpointCompleter.completeEndpoint("https://api.example.com", ApiProviderType.OTHER)
        )
    }

    @Test fun whitespaceAroundUrl_preservesTrimmedCompletion() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            EndpointCompleter.completeEndpoint("  https://api.example.com  ")
        )
    }

    @Test fun openAiResponsesWhitespaceAroundUrl_preservesTrimmedCompletion() {
        assertEquals(
            "https://api.example.com/v1/responses",
            EndpointCompleter.completeEndpoint("  https://api.example.com  ", ApiProviderType.OPENAI_RESPONSES)
        )
    }

    @Test fun anthropicTrailingHash_disablesCompletion() {
        assertEquals(
            "https://api.anthropic.com",
            EndpointCompleter.completeEndpoint("https://api.anthropic.com#", ApiProviderType.ANTHROPIC)
        )
    }

    @Test fun queryOnlyRootUrl_stillCompletesFromEmptyPath() {
        assertEquals(
            "https://api.example.com?v=1/v1/chat/completions",
            EndpointCompleter.completeEndpoint("https://api.example.com?v=1")
        )
    }
}
