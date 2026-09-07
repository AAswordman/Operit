package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.util.AppLogger
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Protocol-level regression tests for the OpenAI tool-call history conversion.
 *
 * Gateways that translate the OpenAI payload into another protocol (Poe onto Anthropic) enforce
 * that every `tool` message answers a call opened by the message right before it. See issue #1027.
 */
class OpenAiToolCallHistoryTest {

    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    @Before
    fun disableAndroidLogging() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun `connection test tool call probe answers the call it opened`() {
        val messages =
            buildMessages(ModelConfigConnectionTester.buildToolCallProbeHistory("echo"))

        assertEquals(
            listOf("system", "user", "assistant", "tool"),
            messages.roles()
        )
        assertToolResultsFollowTheirCalls(messages)
        assertEquals(
            messages.at(2).getJSONArray("tool_calls").getJSONObject(0).getString("id"),
            messages.at(3).getString("tool_call_id")
        )
        assertEquals("pong", messages.at(3).getString("content"))
    }

    @Test
    fun `unanswered calls are closed before the trailing tool result text`() {
        val messages =
            buildMessages(
                listOf(
                    PromptTurn(kind = PromptTurnKind.SYSTEM, content = "You are a helpful assistant."),
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

        assertToolResultsFollowTheirCalls(messages)
        assertEquals(
            listOf("system", "user", "assistant", "tool", "tool", "user"),
            messages.roles()
        )
        assertEquals("The second read was skipped.", messages.at(5).getString("content"))
    }

    @Test
    fun `tool results are paired by tool name when they come back out of order`() {
        val messages =
            buildMessages(
                listOf(
                    PromptTurn(kind = PromptTurnKind.USER, content = "Do both."),
                    PromptTurn(
                        kind = PromptTurnKind.ASSISTANT,
                        content = toolCall("list_files", "path" to ".") +
                            toolCall("calculate", "expression" to "1+1")
                    ),
                    // Denied or interrupted invocations are hoisted to the front of the result list,
                    // so the results do not necessarily arrive in call order.
                    PromptTurn(
                        kind = PromptTurnKind.TOOL_RESULT,
                        content = toolResult("calculate", "2") + toolResult("list_files", "a.txt")
                    )
                )
            )

        assertToolResultsFollowTheirCalls(messages)
        val toolCalls = messages.at(1).getJSONArray("tool_calls")
        val listFilesId = toolCalls.getJSONObject(0).getString("id")
        val calculateId = toolCalls.getJSONObject(1).getString("id")

        assertEquals(calculateId, messages.at(2).getString("tool_call_id"))
        assertEquals("2", messages.at(2).getString("content"))
        assertEquals(listFilesId, messages.at(3).getString("tool_call_id"))
        assertEquals("a.txt", messages.at(3).getString("content"))
    }

    @Test
    fun `package tool results are paired through the package proxy envelope`() {
        val messages =
            buildMessages(
                listOf(
                    PromptTurn(kind = PromptTurnKind.USER, content = "Draw it."),
                    PromptTurn(
                        kind = PromptTurnKind.ASSISTANT,
                        content = toolCall("qwen_draw:draw", "prompt" to "cat")
                    ),
                    PromptTurn(
                        kind = PromptTurnKind.TOOL_RESULT,
                        content = toolResult("qwen_draw:draw", "done")
                    )
                )
            )

        assertToolResultsFollowTheirCalls(messages)
        assertEquals(
            "package_proxy",
            messages.at(1).getJSONArray("tool_calls").getJSONObject(0)
                .getJSONObject("function").getString("name")
        )
        assertEquals("done", messages.at(2).getString("content"))
    }

    @Test
    fun `assistant tool call message omits empty content`() {
        val messages =
            buildMessages(ModelConfigConnectionTester.buildToolCallProbeHistory("echo"))

        assertFalse(messages.at(2).has("content"))
    }

    @Test
    fun `unmatched tool results are ignored without stealing a call id`() {
        val messages =
            buildMessages(
                listOf(
                    PromptTurn(kind = PromptTurnKind.USER, content = "Do both."),
                    PromptTurn(
                        kind = PromptTurnKind.ASSISTANT,
                        content = toolCall("first", "value" to "1") +
                            toolCall("second", "value" to "2")
                    ),
                    PromptTurn(
                        kind = PromptTurnKind.TOOL_RESULT,
                        content = toolResult("missing", "unmatched") + toolResult("first", "ok")
                    )
                )
            )

        val toolCalls = messages.at(1).getJSONArray("tool_calls")
        assertEquals(toolCalls.getJSONObject(0).getString("id"), messages.at(2).getString("tool_call_id"))
        assertEquals("ok", messages.at(2).getString("content"))
        assertEquals(toolCalls.getJSONObject(1).getString("id"), messages.at(3).getString("tool_call_id"))
        assertEquals("User cancelled", messages.at(3).getString("content"))
        assertEquals(4, messages.length())
    }

    @Test
    fun `built-in tool channel sends no tool call protocol fields`() {
        val history =
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
            )

        val messages = buildMessages(history, useToolCall = false)

        assertEquals(listOf("system", "user", "assistant", "user"), messages.roles())
        for (index in 0 until messages.length()) {
            val message = messages.at(index)
            assertFalse(message.has("tool_calls"))
            assertFalse(message.has("tool_call_id"))
        }
        assertTrue(messages.at(3).getString("content").contains("activated"))
    }

    private fun buildMessages(
        history: List<PromptTurn>,
        useToolCall: Boolean = true
    ): JSONArray {
        val provider =
            object : OpenAIProvider(
                apiEndpoint = "https://example.test/v1/chat/completions",
                apiKeyProvider = SingleApiKeyProvider("test-key"),
                modelName = "test-model",
                client = OkHttpClient(),
                enableToolCall = true
            ) {
                fun buildMessages(context: Context): JSONArray =
                    buildMessagesAndCountTokens(context, history, useToolCall = useToolCall).first
            }
        return provider.buildMessages(mock<Context>())
    }

    private fun toolCall(name: String, vararg params: Pair<String, String>): String {
        val body = params.joinToString("") { (key, value) ->
            "<param name=\"$key\">$value</param>"
        }
        return "<tool name=\"$name\">$body</tool>"
    }

    private fun toolResult(name: String, content: String): String =
        "<tool_result name=\"$name\" status=\"success\"><content>$content</content></tool_result>"

    private fun JSONArray.at(index: Int): JSONObject = getJSONObject(index)

    private fun JSONArray.roles(): List<String> =
        (0 until length()).map { at(it).getString("role") }

    /**
     * Mirrors what an Anthropic-backed gateway checks: the conversation may not open with an
     * assistant turn, every `tool` message must answer a call opened by the message right before
     * its run, and no other message may cut into that run.
     */
    private fun assertToolResultsFollowTheirCalls(messages: JSONArray) {
        val firstNonSystem = (0 until messages.length())
            .firstOrNull { messages.at(it).getString("role") != "system" }
        if (firstNonSystem != null) {
            assertEquals(
                "conversation must not open with an assistant turn",
                "user",
                messages.at(firstNonSystem).getString("role")
            )
        }

        val pendingCallIds = mutableSetOf<String>()
        for (index in 0 until messages.length()) {
            val message = messages.at(index)
            when (message.getString("role")) {
                "tool" -> {
                    val toolCallId = message.getString("tool_call_id")
                    assertTrue(
                        "message $index answers $toolCallId with no matching tool call before it",
                        pendingCallIds.remove(toolCallId)
                    )
                }

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
