package com.ai.assistance.operit.api.chat.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointCompleterDefaultPathTest {

    @Test fun completeEndpoint_keepsFullChatPathUntouched() {
        val endpoint = "https://api.example.com/v1/chat/completions"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint))
    }

    @Test fun completeEndpoint_keepsResponsesPathUntouched() {
        val endpoint = "https://api.example.com/v1/responses"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint))
    }

    @Test fun completeEndpoint_keepsNonHttpUrlUntouched() {
        val endpoint = "file:///tmp/test"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint))
    }

    @Test fun completeEndpoint_keepsWhitespaceForInvalidInput() {
        val endpoint = " invalid "
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint))
    }
}
