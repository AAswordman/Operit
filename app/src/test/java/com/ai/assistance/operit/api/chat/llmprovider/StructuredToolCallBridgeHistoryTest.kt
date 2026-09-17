package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Protocol-level regression tests for the structured message builder shared by the local-model
 * providers. Same invariant as [OpenAiToolCallHistoryTest]: a `tool` message may only answer a call
 * opened by the message right before its run. See issue #1027.
 */
class StructuredToolCallBridgeHistoryTest {

    @Test
    fun `unanswered calls are closed before the trailing tool result text`() {
        val messages =
            buildMessages(
                listOf(
                    PromptTurn(kind = PromptTurnKind.USER, content = "Read both files."),
                    PromptTurn(
                        kind = PromptTurnKind.ASSISTANT,
                        content = toolCall("read_file", "path" to "a.txt") +
                            toolCall("read_file_part", "path" to "b.txt")
                    ),
                    PromptTurn(
                        kind = PromptTurnKind.TOOL_RESULT,
                        content = toolResult("read_file", "alpha") + "\nThe second read was skipped."
                    )
                )
            )

        assertEquals(listOf("user", "assistant", "tool", "tool", "user"), messages.roles())
        val placeholder = messages.at(3).getString("content")
        assertEquals(
            StructuredToolCallBridge.unmatchedToolResultContent(
                "tool_result_partial_batch",
                "read_file_part"
            ),
            placeholder
        )
        assertFalse(placeholder.contains("用户取消"))
        assertEquals("The second read was skipped.", messages.at(4).getString("content"))
        assertToolResultsFollowTheirCalls(messages)
    }

    @Test
    fun `same-name tool results with call_id are paired correctly even when reordered`() {
        // Two read_file calls with different params. Results carry call_id in reverse
        // completion order. call_id matching must attach each result to the correct call.
        val bodyA = """<param name="path">a.txt</param>"""
        val bodyB = """<param name="path">b.txt</param>"""
        val paramsA = StructuredToolCallBridge.canonicalParamsJson(bodyA)
        val paramsB = StructuredToolCallBridge.canonicalParamsJson(bodyB)
        val callIdA = StructuredToolCallBridge.stableCallId("read_file", paramsA, 0)
        val callIdB = StructuredToolCallBridge.stableCallId("read_file", paramsB, 1)

        val messages = buildMessages(
            listOf(
                PromptTurn(kind = PromptTurnKind.USER, content = "Read both."),
                PromptTurn(
                    kind = PromptTurnKind.ASSISTANT,
                    content = toolCall("read_file", "path" to "a.txt") +
                        toolCall("read_file", "path" to "b.txt")
                ),
                PromptTurn(
                    kind = PromptTurnKind.TOOL_RESULT,
                    // b.txt completes first, a.txt second — reverse of call order.
                    content = toolResult("read_file", "content-b", callId = callIdB) +
                        toolResult("read_file", "content-a", callId = callIdA)
                )
            )
        )

        val toolCalls = messages.at(1).getJSONArray("tool_calls")
        assertEquals(callIdA, toolCalls.getJSONObject(0).getString("id"))
        assertEquals(callIdB, toolCalls.getJSONObject(1).getString("id"))

        // Output must be in call order with correct content pairing.
        assertEquals(callIdA, messages.at(2).getString("tool_call_id"))
        assertEquals("content-a", messages.at(2).getString("content"))
        assertEquals(callIdB, messages.at(3).getString("tool_call_id"))
        assertEquals("content-b", messages.at(3).getString("content"))
    }

    @Test
    fun `consumeMatchingToolCalls pairs by call_id when available`() {
        // Direct unit test of the call_id matching logic in consumeMatchingToolCalls.
        val openToolCalls = mutableListOf(
            StructuredToolCallBridge.OpenToolCall("id-a", "read_file"),
            StructuredToolCallBridge.OpenToolCall("id-b", "read_file")
        )

        // Results in reverse order with call_ids.
        val matched = StructuredToolCallBridge.consumeMatchingToolCalls(
            openToolCalls,
            listOf("read_file", "read_file"),
            listOf("id-b", "id-a")
        )

        // Result 0 (call_id=id-b) should match call id-b.
        // Result 1 (call_id=id-a) should match call id-a.
        assertEquals(2, matched.size)
        assertEquals("id-b", matched[0].call.id)
        assertEquals(0, matched[0].resultIndex)
        assertEquals("id-a", matched[1].call.id)
        assertEquals(1, matched[1].resultIndex)
    }

    @Test
    fun `consumeMatchingToolCalls falls back to name when call_id missing`() {
        val openToolCalls = mutableListOf(
            StructuredToolCallBridge.OpenToolCall("id-a", "read_file"),
            StructuredToolCallBridge.OpenToolCall("id-b", "read_file")
        )

        // No call_ids — falls back to name matching (first-come-first-served).
        val matched = StructuredToolCallBridge.consumeMatchingToolCalls(
            openToolCalls,
            listOf("read_file", "read_file"),
            listOf(null, null)
        )

        assertEquals(2, matched.size)
        assertEquals("id-a", matched[0].call.id)
        assertEquals("id-b", matched[1].call.id)
    }

    @Test
    fun `same-name tool results without call_id fall back to name matching`() {
        // Legacy records: no call_id in tool_result XML. Pairing falls back to name-based
        // first-come-first-served. Content may be mispaired — this documents the limitation.
        val messages =
            buildMessages(
                listOf(
                    PromptTurn(kind = PromptTurnKind.USER, content = "Read both."),
                    PromptTurn(
                        kind = PromptTurnKind.ASSISTANT,
                        content = toolCall("read_file", "path" to "a.txt") +
                            toolCall("read_file", "path" to "b.txt")
                    ),
                    PromptTurn(
                        kind = PromptTurnKind.TOOL_RESULT,
                        content = toolResult("read_file", "content-b") +
                            toolResult("read_file", "content-a")
                    )
                )
            )

        val toolCalls = messages.at(1).getJSONArray("tool_calls")
        assertEquals(
            toolCalls.getJSONObject(0).getString("id"),
            messages.at(2).getString("tool_call_id")
        )
        assertEquals(
            toolCalls.getJSONObject(1).getString("id"),
            messages.at(3).getString("tool_call_id")
        )
        // Without call_id, first result goes to first call (may be semantically wrong).
        assertEquals("content-b", messages.at(2).getString("content"))
        assertEquals("content-a", messages.at(3).getString("content"))
    }

    @Test
    fun `tool results are emitted in tool_use order even when they come back reordered`() {
        // Call order: list_files first, calculate second.
        // Result order in XML: calculate first, list_files second (completion-time order).
        // Emitted tool messages must follow call order for strict prefix caches (issue #1159).
        // For different-name tools, sorting by call order also fixes content-to-id pairing.
        val messages =
            buildMessages(
                listOf(
                    PromptTurn(kind = PromptTurnKind.USER, content = "Do both."),
                    PromptTurn(
                        kind = PromptTurnKind.ASSISTANT,
                        content = toolCall("list_files", "path" to ".") +
                            toolCall("calculate", "expression" to "1+1")
                    ),
                    PromptTurn(
                        kind = PromptTurnKind.TOOL_RESULT,
                        content = toolResult("calculate", "2") + toolResult("list_files", "a.txt")
                    )
                )
            )

        val toolCalls = messages.at(1).getJSONArray("tool_calls")
        // First emitted tool message answers the first tool_use (list_files) with its content.
        assertEquals(
            toolCalls.getJSONObject(0).getString("id"),
            messages.at(2).getString("tool_call_id")
        )
        assertEquals("a.txt", messages.at(2).getString("content"))
        // Second emitted tool message answers the second tool_use (calculate) with its content.
        assertEquals(
            toolCalls.getJSONObject(1).getString("id"),
            messages.at(3).getString("tool_call_id")
        )
        assertEquals("2", messages.at(3).getString("content"))
        assertToolResultsFollowTheirCalls(messages)
    }

    @Test
    fun `unmatched result does not consume an unrelated open call`() {
        val openToolCalls =
            mutableListOf(
                StructuredToolCallBridge.OpenToolCall("first-id", "first"),
                StructuredToolCallBridge.OpenToolCall("second-id", "second")
            )

        val matched =
            StructuredToolCallBridge.consumeMatchingToolCalls(
                openToolCalls,
                listOf("missing", "first")
            )

        assertEquals(listOf(1), matched.map { it.resultIndex })
        assertEquals(listOf("first-id"), matched.map { it.call.id })
        assertEquals(listOf("second"), openToolCalls.map { it.matchingName })
    }

    @Test
    fun `Gemini package proxy result matches concrete tool and retains proxy identity`() {
        val geminiFunctionCall =
            JSONObject().apply {
                put("name", "package_proxy")
                put(
                    "args",
                    JSONObject().apply {
                        put("tool_name", "extended_http_tools:http_request")
                    }
                )
            }
        val openToolCalls =
            mutableListOf(
                StructuredToolCallBridge.OpenToolCall(
                    id = geminiFunctionCall.getString("name"),
                    matchingName = StructuredToolCallBridge.toolCallName(geminiFunctionCall),
                )
            )

        val matched =
            StructuredToolCallBridge.consumeMatchingToolCalls(
                openToolCalls,
                listOf("extended_http_tools:http_request")
            )

        assertEquals(1, matched.size)
        assertEquals("package_proxy", matched.single().call.id)
        assertTrue(openToolCalls.isEmpty())
    }

    @Test
    fun `Claude package proxy result matches concrete tool from input`() {
        val claudeToolUse =
            JSONObject().apply {
                put("type", "tool_use")
                put("name", "package_proxy")
                put(
                    "input",
                    JSONObject().apply {
                        put("tool_name", "extended_http_tools:http_request")
                    }
                )
            }

        val openToolCalls =
            mutableListOf(
                StructuredToolCallBridge.OpenToolCall(
                    id = "claude-tool-use-id",
                    matchingName = StructuredToolCallBridge.toolCallName(claudeToolUse),
                )
            )
        val matched =
            StructuredToolCallBridge.consumeMatchingToolCalls(
                openToolCalls,
                listOf("extended_http_tools:http_request")
            )

        assertEquals(1, matched.size)
        assertEquals("claude-tool-use-id", matched.single().call.id)
        assertTrue(openToolCalls.isEmpty())
    }

    @Test
    fun `OpenAI local proxy result matches concrete target from arguments`() {
        val openAiProxyCall =
            JSONObject().apply {
                put("id", "openai-local-proxy-id")
                put(
                    "function",
                    JSONObject().apply {
                        put("name", "proxy")
                        put(
                            "arguments",
                            JSONObject().apply {
                                put("tool_name", "read_file")
                                put("params", JSONObject().apply { put("path", "a.txt") }.toString())
                            }.toString()
                        )
                    }
                )
            }
        val openToolCalls =
            mutableListOf(
                StructuredToolCallBridge.OpenToolCall(
                    id = openAiProxyCall.getString("id"),
                    matchingName = StructuredToolCallBridge.toolCallName(openAiProxyCall),
                )
            )

        val matched =
            StructuredToolCallBridge.consumeMatchingToolCalls(
                openToolCalls,
                listOf("read_file")
            )

        assertEquals(1, matched.size)
        assertEquals("openai-local-proxy-id", matched.single().call.id)
        assertTrue(openToolCalls.isEmpty())
    }

    @Test
    fun `built-in tool channel compiles tool turns into plain roles`() {
        val compiled =
            StructuredToolCallBridge.compileHistoryForProvider(
                listOf(
                    PromptTurn(kind = PromptTurnKind.SYSTEM, content = "You are a helpful assistant."),
                    PromptTurn(kind = PromptTurnKind.USER, content = "Use the package."),
                    PromptTurn(
                        kind = PromptTurnKind.TOOL_CALL,
                        content = toolCall("use_package", "package_name" to "qwen_draw")
                    ),
                    PromptTurn(
                        kind = PromptTurnKind.TOOL_RESULT,
                        content = toolResult("use_package", "activated")
                    )
                ),
                useToolCall = false
            )

        assertEquals(
            listOf(
                PromptTurnKind.SYSTEM,
                PromptTurnKind.USER,
                PromptTurnKind.ASSISTANT,
                PromptTurnKind.USER
            ),
            compiled.map { it.kind }
        )
    }

    private fun buildMessages(history: List<PromptTurn>): JSONArray =
        JSONArray(
            StructuredToolCallBridge.buildMessagesJson(history, preserveThinkInHistory = false)
        )

    private fun toolCall(name: String, vararg params: Pair<String, String>): String {
        val body = params.joinToString("") { (key, value) ->
            "<param name=\"$key\">$value</param>"
        }
        return "<tool name=\"$name\">$body</tool>"
    }

    private fun toolResult(name: String, content: String, callId: String? = null): String {
        val callIdAttr = if (callId.isNullOrBlank()) "" else """ call_id="$callId""""
        return "<tool_result name=\"$name\" status=\"success\"$callIdAttr><content>$content</content></tool_result>"
    }

    private fun JSONArray.at(index: Int): JSONObject = getJSONObject(index)

    private fun JSONArray.roles(): List<String> =
        (0 until length()).map { at(it).getString("role") }

    private fun assertToolResultsFollowTheirCalls(messages: JSONArray) {
        val pendingCallIds = mutableSetOf<String>()
        for (index in 0 until messages.length()) {
            val message = messages.at(index)
            when (message.getString("role")) {
                "tool" ->
                    assertTrue(
                        "message $index answers a call that was never opened before it",
                        pendingCallIds.remove(message.getString("tool_call_id"))
                    )

                "assistant" -> {
                    assertTrue(
                        "message $index leaves calls $pendingCallIds unanswered",
                        pendingCallIds.isEmpty()
                    )
                    val toolCalls = message.optJSONArray("tool_calls") ?: JSONArray()
                    for (callIndex in 0 until toolCalls.length()) {
                        pendingCallIds.add(toolCalls.getJSONObject(callIndex).getString("id"))
                    }
                }

                else ->
                    assertTrue(
                        "message $index leaves calls $pendingCallIds unanswered",
                        pendingCallIds.isEmpty()
                    )
            }
        }
        assertTrue("history ends with unanswered calls $pendingCallIds", pendingCallIds.isEmpty())
    }
}
