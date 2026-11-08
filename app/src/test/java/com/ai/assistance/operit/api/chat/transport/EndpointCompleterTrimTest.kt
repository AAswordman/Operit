package com.ai.assistance.operit.api.chat.transport

import com.ai.assistance.operit.data.config.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointCompleterTrimTest {

    @Test fun defaultCompletion_trimsOuterWhitespace() {
        assertEquals("https://x/v1/chat/completions", EndpointCompleter.completeEndpoint("  https://x  "))
    }

    @Test fun responsesCompletion_trimsOuterWhitespace() {
        assertEquals("https://x/v1/responses", EndpointCompleter.completeEndpoint("  https://x  ", ApiProviderType.OPENAI_RESPONSES))
    }

    @Test fun anthropicCompletion_trimsOuterWhitespace() {
        assertEquals("https://x/v1/messages", EndpointCompleter.completeEndpoint("  https://x  ", ApiProviderType.ANTHROPIC))
    }
}
