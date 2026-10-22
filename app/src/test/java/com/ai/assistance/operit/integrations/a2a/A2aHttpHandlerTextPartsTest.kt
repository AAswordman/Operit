package com.ai.assistance.operit.integrations.a2a

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers A2A 1.0 Part kind recognition. `text`, `raw`, `url`, and `data` form the `content`
 * oneof, and `metadata`, `filename`, and `mediaType` are siblings of that oneof, so a text part
 * stays a text part when a client also sends `mediaType` (AAswordman/Operit#1306).
 */
class A2aHttpHandlerTextPartsTest {

    @Test
    fun `accepts a text part that also carries mediaType`() {
        val parts = JSONArray().put(
            JSONObject().put("text", "ping").put("mediaType", "text/plain")
        )

        assertEquals("ping", parseA2aTextParts(parts))
    }

    @Test
    fun `accepts a text part that carries every companion field the spec allows`() {
        val parts = JSONArray().put(
            JSONObject().apply {
                put("text", "ping")
                put("mediaType", "text/plain")
                put("filename", "note.txt")
                put("metadata", JSONObject().put("source", "client"))
            }
        )

        assertEquals("ping", parseA2aTextParts(parts))
    }

    @Test
    fun `accepts a text part without any companion field`() {
        val parts = JSONArray().put(JSONObject().put("text", "ping"))

        assertEquals("ping", parseA2aTextParts(parts))
    }

    @Test
    fun `joins multiple text parts with a newline`() {
        val parts = JSONArray()
            .put(JSONObject().put("text", "first"))
            .put(JSONObject().put("text", "second").put("mediaType", "text/plain"))

        assertEquals("first\nsecond", parseA2aTextParts(parts))
    }

    @Test
    fun `rejects a raw part and names the unsupported member`() {
        val parts = JSONArray().put(
            JSONObject().put("raw", "iVBORw0KGgo=").put("mediaType", "image/png")
        )

        val error = assertThrows(A2aProtocolException::class.java) {
            parseA2aTextParts(parts)
        }

        assertEquals("A2A server accepts text parts only, part[0] contains raw", error.message)
    }

    @Test
    fun `rejects a url part`() {
        val parts = JSONArray().put(
            JSONObject().put("url", "https://example.com/report.pdf")
        )

        val error = assertThrows(A2aProtocolException::class.java) {
            parseA2aTextParts(parts)
        }

        assertEquals("A2A server accepts text parts only, part[0] contains url", error.message)
    }

    @Test
    fun `rejects a data part`() {
        val parts = JSONArray().put(
            JSONObject().put("data", JSONObject().put("ticket", "REQ12312"))
        )

        val error = assertThrows(A2aProtocolException::class.java) {
            parseA2aTextParts(parts)
        }

        assertEquals("A2A server accepts text parts only, part[0] contains data", error.message)
    }

    @Test
    fun `rejects a part that carries two content members`() {
        val parts = JSONArray().put(
            JSONObject().put("text", "ping").put("url", "https://example.com/report.pdf")
        )

        val error = assertThrows(A2aProtocolException::class.java) {
            parseA2aTextParts(parts)
        }

        assertTrue(error.message.orEmpty().contains("exactly one of"))
    }

    @Test
    fun `rejects a part without any content member`() {
        val parts = JSONArray().put(JSONObject().put("mediaType", "text/plain"))

        val error = assertThrows(A2aProtocolException::class.java) {
            parseA2aTextParts(parts)
        }

        assertTrue(error.message.orEmpty().contains("exactly one of"))
    }

    @Test
    fun `accepts a text part whose unsupported sibling members are explicitly null`() {
        val parts = JSONArray().put(
            JSONObject().apply {
                put("text", "ping")
                put("mediaType", "text/plain")
                put("raw", JSONObject.NULL)
                put("data", JSONObject.NULL)
            }
        )

        assertEquals("ping", parseA2aTextParts(parts))
    }

    @Test
    fun `rejects a non-text part even when a text part is also present`() {
        val parts = JSONArray()
            .put(JSONObject().put("text", "ping"))
            .put(JSONObject().put("raw", "iVBORw0KGgo="))

        val error = assertThrows(A2aProtocolException::class.java) {
            parseA2aTextParts(parts)
        }

        assertEquals("A2A server accepts text parts only, part[1] contains raw", error.message)
    }
}
