package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointCompleterDeepSeekTest {

    @Test
    fun releasedChatEndpoint_resolvesToChatCompletions() {
        assertEquals(
            DeepSeekApiProtocol.CHAT_COMPLETIONS,
            EndpointCompleter.resolveDeepSeekProtocol(
                "https://api.deepseek.com/v1/chat/completions"
            )
        )
    }

    @Test
    fun releasedResponsesEndpoint_resolvesToResponses() {
        assertEquals(
            DeepSeekApiProtocol.RESPONSES,
            EndpointCompleter.resolveDeepSeekProtocol("https://api.deepseek.com/v1/responses")
        )
    }

    @Test
    fun rootAndV1Endpoints_remainChatCompletions() {
        assertEquals(
            DeepSeekApiProtocol.CHAT_COMPLETIONS,
            EndpointCompleter.resolveDeepSeekProtocol("https://api.deepseek.com")
        )
        assertEquals(
            DeepSeekApiProtocol.CHAT_COMPLETIONS,
            EndpointCompleter.resolveDeepSeekProtocol("https://proxy.example.com/custom/v1")
        )
    }

    @Test
    fun customResponsesEndpoint_resolvesToResponsesWithoutPersistingChanges() {
        val endpoint = "  https://proxy.example.com/custom/responses/?region=cn#  "

        assertEquals(
            DeepSeekApiProtocol.RESPONSES,
            EndpointCompleter.resolveDeepSeekProtocol(endpoint)
        )
    }

    @Test
    fun nonResponsesEndpointForms_resolveToChatCompletions() {
        assertEquals(
            DeepSeekApiProtocol.CHAT_COMPLETIONS,
            EndpointCompleter.resolveDeepSeekProtocol("https://proxy.example.com/custom/search")
        )
        assertEquals(
            DeepSeekApiProtocol.CHAT_COMPLETIONS,
            EndpointCompleter.resolveDeepSeekProtocol("https://proxy.example.com/custom/chat")
        )
    }
}
