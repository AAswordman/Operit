package com.ai.assistance.operit.api.chat.transport

import com.ai.assistance.operit.data.config.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointCompleterHashControlTest {

    @Test fun defaultCompletion_hashBypassStripsHashOnly() {
        assertEquals("https://x/v1", EndpointCompleter.completeEndpoint("https://x/v1#"))
    }

    @Test fun anthropicCompletion_hashBypassStripsHashOnly() {
        assertEquals("https://x/anthropic", EndpointCompleter.completeEndpoint("https://x/anthropic#", ApiProviderType.ANTHROPIC))
    }

    @Test fun responsesCompletion_hashBypassStripsHashOnly() {
        assertEquals("https://x", EndpointCompleter.completeEndpoint("https://x#", ApiProviderType.OPENAI_RESPONSES))
    }
}
