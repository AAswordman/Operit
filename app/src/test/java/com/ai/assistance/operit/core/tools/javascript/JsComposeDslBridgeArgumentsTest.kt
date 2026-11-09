package com.ai.assistance.operit.core.tools.javascript

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the WebView ToolPkg bridge contract behind issue #1345.
 *
 * The bridge used to hand a plugin's `addJavascriptInterface` method the whole
 * positional argument list as one payload, so `updateMemory(oldTitle, newTitle,
 * content)` saw `oldTitle` as an array and the other two parameters as
 * `undefined`. These tests pin the wire format that keeps the declared
 * `(...args: unknown[])` contract honest.
 */
class JsComposeDslBridgeArgumentsTest {

    private fun argsJsonOf(vararg values: Any?): String =
        JSONArray().apply { values.forEach { put(it) } }.toString()

    @Test
    fun `positional argument lists survive the bridge JSON round trip`() {
        val samples =
            listOf(
                "正文, 含逗号",
                "line1\nline2\r\nline3",
                "<b>HTML</b> & \"quotes\" and 'single'",
                "中文标点：，。！？；",
                "{\"id\":\"memo_1\",\"title\":\"标题, 带逗号\",\"content\":\"a\\nb\"}",
                "emoji 🎉 and tab\there",
                "backslash \\ and closing </script> tag"
            )

        samples.forEach { sample ->
            val decoded = JsJavaBridgeDelegates.parsePlainJsonArray(argsJsonOf(sample))
            assertNotNull("payload should parse: $sample", decoded)
            assertEquals("payload should decode to one argument: $sample", 1, decoded!!.size)
            assertEquals("argument must round trip verbatim: $sample", sample, decoded[0])
        }
    }

    @Test
    fun `argument list keeps every positional value distinct`() {
        val argsJson =
            argsJsonOf(
                "旧标题",
                "新标题",
                "正文, 含逗号\n和 <b>HTML</b>，中文标点：，。"
            )

        val decoded = JsJavaBridgeDelegates.parsePlainJsonArray(argsJson)

        assertNotNull(decoded)
        assertEquals(3, decoded!!.size)
        assertEquals("旧标题", decoded[0])
        assertEquals("新标题", decoded[1])
        assertEquals("正文, 含逗号\n和 <b>HTML</b>，中文标点：，。", decoded[2])
    }

    @Test
    fun `structured argument keeps object shape instead of a stringified wrapper`() {
        val argsJson = "[{\"id\":\"memo_1\",\"old_title\":\"旧标题\",\"content\":\"正文\"}]"

        val decoded = JsJavaBridgeDelegates.parsePlainJsonArray(argsJson)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.size)
        val payload = decoded[0] as Map<*, *>
        assertEquals("memo_1", payload["id"])
        assertEquals("旧标题", payload["old_title"])
    }

    @Test
    fun `empty argument list decodes to a zero argument call`() {
        val decoded = JsJavaBridgeDelegates.parsePlainJsonArray("[]")

        assertNotNull(decoded)
        assertEquals(0, decoded!!.size)
    }

    @Test
    fun `non array payload is rejected so the caller can report the offending call`() {
        assertNull(JsJavaBridgeDelegates.parsePlainJsonArray("{\"id\":\"memo_1\"}"))
        assertNull(JsJavaBridgeDelegates.parsePlainJsonArray("\"not-an-array\""))
        assertNull(JsJavaBridgeDelegates.parsePlainJsonArray("<b>html</b>"))
    }

    @Test
    fun `host bridge spreads a positional argument list into the handler`() {
        val script = buildComposeDslContextBridgeDefinition()

        assertTrue(
            "invokeAction must distinguish an argument list from a single payload",
            script.contains("payloadIsArgumentList")
        )
        assertTrue(
            "the handler must receive the page arguments positionally",
            script.contains("handler.apply(null, payload)")
        )
        assertTrue(
            "single-payload Compose actions keep their existing unwrapping",
            script.contains("__composeTextFieldPayload")
        )
    }

    @Test
    fun `dispatch script forwards the argument list flag to the host bridge`() {
        val script = buildComposeDslRuntimeWrappedScript("")

        assertTrue(
            "dispatch must read the argument list flag the host sends",
            script.contains("__action_payload_is_argument_list")
        )
        assertTrue(
            "dispatch must pass the flag on to invokeAction",
            script.contains("__payloadIsArgumentList")
        )
        assertTrue(
            "the flag only applies to array payloads",
            script.contains("Array.isArray(__payload)")
        )
    }

    @Test
    fun `dispatch script leaves plugin state alone when the action fails`() {
        val script = buildComposeDslRuntimeWrappedScript("")

        // The dispatch function is shared by every Compose action, and async actions run
        // concurrently. Rolling state back here would also undo writes made by actions that
        // succeeded while this one was awaiting, so failure rollback stays out of this path.
        assertTrue(
            "dispatch must not snapshot or restore plugin state",
            !script.contains("__operit_snapshot_state") &&
                !script.contains("__operit_restore_state")
        )
    }
}
