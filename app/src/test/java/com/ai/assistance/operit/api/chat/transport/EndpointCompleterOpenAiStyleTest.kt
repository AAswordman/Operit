package com.ai.assistance.operit.api.chat.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointCompleterOpenAiStyleTest {

    @Test fun baseUrlWithCustomPort_completesNormally() {
        assertEquals(
            "http://localhost:8080/v1/chat/completions",
            EndpointCompleter.completeEndpoint("http://localhost:8080")
        )
    }

    @Test fun nestedV1PathWithPort_completesNormally() {
        assertEquals(
            "http://localhost:8080/proxy/v1/chat/completions",
            EndpointCompleter.completeEndpoint("http://localhost:8080/proxy/v1")
        )
    }

    @Test fun alreadyCustomPathWithPort_staysUnchanged() {
        val endpoint = "http://localhost:8080/custom/chat"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint))
    }
}
