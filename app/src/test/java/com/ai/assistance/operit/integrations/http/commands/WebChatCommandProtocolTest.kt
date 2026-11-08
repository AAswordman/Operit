package com.ai.assistance.operit.integrations.http.commands

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class WebChatCommandProtocolTest {
    @Test fun validatesPortBoundariesAndCommandSpecificOptions() {
        for (port in listOf(1, 65535)) WebChatCommandRequest(1, "start-server", port, true).validate()
        for (port in listOf(0, -1, 65536)) {
            assertThrows(IllegalArgumentException::class.java) { WebChatCommandRequest(1, "start-server", port).validate() }
        }
        for (request in listOf(
            WebChatCommandRequest(2, "status"),
            WebChatCommandRequest(1, "unknown"),
            WebChatCommandRequest(1, "status", port = 80),
            WebChatCommandRequest(1, "token-show", login = true),
            WebChatCommandRequest(1, "start-server", example = "sync"),
            WebChatCommandRequest(1, "examples", example = "unknown"),
        )) assertThrows(IllegalArgumentException::class.java) { request.validate() }
    }

    @Test fun acceptsAllExamplesAndAllFourSpecificTypes() {
        WebChatCommandRequest(1, "examples").validate()
        for (kind in listOf("sync", "async", "health", "intent")) {
            WebChatCommandRequest(1, "examples", example = kind).validate()
        }
    }

    @Test fun loginUrlEncodesTheWholeTokenAsOneQueryValue() {
        val token = "abc+def&token=other?#中文'"
        val url = URI(webChatLoginUrl(8080, token))
        assertEquals("127.0.0.1", url.host)
        assertEquals(8080, url.port)
        assertNull(url.fragment)
        assertEquals(token, URLDecoder.decode(url.rawQuery.removePrefix("token="), "UTF-8"))
        assertFalse(url.rawQuery.contains('&'))
    }

    @Test fun onlyTheAppAndRealRootCanUseTheControlChannel() {
        assertTrue(trustedWebChatPeer(10234, 10234))
        assertTrue(trustedWebChatPeer(0, 10234))
        assertFalse(trustedWebChatPeer(2000, 10234))
        assertFalse(trustedWebChatPeer(10235, 10234))
    }

    @Test fun readsOneCompleteRequestAndRejectsTruncationOrOverflow() {
        assertEquals("{}", readWebChatCommand(ByteArrayInputStream("{}\nignored\n".toByteArray())))
        assertThrows(IllegalStateException::class.java) { readWebChatCommand(ByteArrayInputStream("{}".toByteArray())) }
        assertThrows(IllegalStateException::class.java) { readWebChatCommand(ByteArrayInputStream(ByteArray(16_385) { 65 })) }
    }
}
