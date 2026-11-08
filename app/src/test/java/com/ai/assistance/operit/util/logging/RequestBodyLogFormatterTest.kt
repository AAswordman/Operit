package com.ai.assistance.operit.util.logging

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestBodyLogFormatterTest {
    @Test
    fun `formatting preserves the complete source request`() {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", "完整原文".repeat(100)))
        val tools = JSONArray().put(JSONObject().put("name", "read_file"))
        val body = JSONObject().put("model", "model").put("messages", messages).put("tools", tools)
            .put("audio", JSONObject().put("data", "BASE64".repeat(500)))
            .put("image", JSONObject().put("url", "data:image/png;base64,BASE64"))
        val before = body.toString()

        val formatted = RequestBodyLogFormatter.format(body)

        assertEquals(before, body.toString())
        assertSame(messages, body.getJSONArray("messages"))
        assertSame(tools, body.getJSONArray("tools"))
        assertFalse(formatted.contains("BASE64"))
        assertTrue(formatted.contains("text omitted"))
        assertTrue(formatted.contains("media data omitted"))
    }

    @Test
    fun `large history reads a bounded sample and keeps every source turn`() {
        val messages = TrackingArray()
        val content = "历史原文".repeat(300)
        repeat(15_194) { index ->
            messages.put(JSONObject().put("role", "user").put("content", "$index:$content"))
        }
        val body = JSONObject().put("model", "model").put("messages", messages)

        val formatted = RequestBodyLogFormatter.format(body)

        assertTrue(formatted.length <= RequestBodyLogFormatter.MAX_LOG_CHARACTERS)
        assertTrue(messages.readCount <= 8)
        assertTrue(formatted.contains("middle entries omitted"))
        assertEquals(15_194, messages.length())
        assertEquals("0:$content", messages.getJSONObject(0).getString("content"))
        assertEquals("15193:$content", messages.getJSONObject(15_193).getString("content"))
    }

    @Test
    fun `log preview never serializes the original request`() {
        val body = object : JSONObject() {
            override fun toString(): String = error("不应序列化原始请求来生成日志")
            override fun toString(indentFactor: Int): String = error("不应格式化原始请求来生成日志")
        }.apply {
            put("model", "model")
            put("messages", JSONArray().put(JSONObject().put("content", "原文")))
        }

        assertTrue(RequestBodyLogFormatter.format(body).contains("原文"))
    }

    @Test
    fun `nested and wide payloads stay bounded`() {
        var nested = JSONObject().put("text", "原文".repeat(10_000))
        repeat(50) { nested = JSONObject().put("child", nested) }
        val nestedLog = RequestBodyLogFormatter.format(JSONObject().put("nested", nested))
        assertTrue(nestedLog.length <= RequestBodyLogFormatter.MAX_LOG_CHARACTERS)
        assertTrue(nestedLog.contains("object omitted"))

        val body = JSONObject()
        repeat(100) { body.put("field$it", "参数".repeat(10_000)) }
        val formatted = RequestBodyLogFormatter.format(body)
        assertTrue(formatted.length <= RequestBodyLogFormatter.MAX_LOG_CHARACTERS)
        assertTrue(formatted.contains("_omitted_fields"))
        assertEquals(100, body.length())
    }

    private class TrackingArray : JSONArray() {
        var readCount = 0
            private set

        override fun get(index: Int): Any {
            readCount += 1
            return super.get(index)
        }
    }
}
