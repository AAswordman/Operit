package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepseekResponsesPayloadAdapterTest {

    @Test
    fun `normalizes chat style functions and preserves native web search`() {
        val chatStyleRequest =
            JSONObject()
                .put("stream_options", JSONObject().put("include_usage", true))
                .put("tool_choice", "auto")
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "Use a tool")
                    )
                )
                .put(
                    "tools",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject()
                                        .put("name", "run_command")
                                        .put("description", "Run a command")
                                        .put(
                                            "parameters",
                                            JSONObject().put("type", "object")
                                        )
                                )
                        )
                        .put(JSONObject().put("type", "web_search"))
                )
        val request = OpenAIResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)

        DeepseekResponsesPayloadAdapter.normalizeRequest(request, fallbackInput = null)

        assertFalse(request.has("stream_options"))
        assertFalse(request.has("messages"))
        assertEquals("Use a tool", request.getJSONArray("input").getJSONObject(0).getString("content"))
        val tools = request.getJSONArray("tools")

        val functionTool = tools.getJSONObject(0)
        assertEquals("function", functionTool.getString("type"))
        assertEquals("run_command", functionTool.getString("name"))
        assertFalse(functionTool.has("function"))
        assertEquals("object", functionTool.getJSONObject("parameters").getString("type"))

        val webSearchTool = tools.getJSONObject(1)
        assertEquals("web_search", webSearchTool.getString("type"))
        assertFalse(webSearchTool.has("name"))
    }

    @Test
    fun `adds fallback input when conversion leaves input empty`() {
        val request =
            OpenAIResponsesPayloadAdapter.toResponsesRequest(
                JSONObject().put("messages", JSONArray())
            )

        DeepseekResponsesPayloadAdapter.normalizeRequest(

            request,
            fallbackInput = "What changed in the last response?"
        )

        val input = request.getJSONArray("input")
        assertEquals(1, input.length())
        val fallbackMessage = input.getJSONObject(0)
        assertEquals("message", fallbackMessage.getString("type"))
        assertEquals("user", fallbackMessage.getString("role"))
        assertEquals("What changed in the last response?", fallbackMessage.getString("content"))
    }

    @Test
    fun `completes unmatched function calls and preserves completed calls`() {
        val request =
            JSONObject().put(
                "input",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "function_call")
                            .put("call_id", "fc_done")
                            .put("name", "read_file")
                            .put("arguments", "{}")
                    )
                    .put(
                        JSONObject()
                            .put("type", "function_call_output")
                            .put("call_id", "fc_done")
                            .put("output", "contents")
                    )
                    .put(
                        JSONObject()
                            .put("type", "function_call")
                            .put("call_id", "fc_pending")
                            .put("name", "run_command")
                            .put("arguments", "{}")
                    )
            )

        DeepseekResponsesPayloadAdapter.normalizeRequest(request, fallbackInput = null)

        val input = request.getJSONArray("input")
        assertEquals(4, input.length())
        assertEquals("contents", input.getJSONObject(1).getString("output"))
        val cancellationOutput = input.getJSONObject(3)
        assertEquals("function_call_output", cancellationOutput.getString("type"))
        assertEquals("fc_pending", cancellationOutput.getString("call_id"))
        assertEquals("User cancelled", cancellationOutput.getString("output"))
    }

    @Test
    fun `generates a matching call id for legacy function calls without one`() {
        val request =
            JSONObject().put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("type", "function_call")
                        .put("name", "read_file")
                        .put("arguments", "{}")
                )
            )

        DeepseekResponsesPayloadAdapter.normalizeRequest(request, fallbackInput = null)

        val input = request.getJSONArray("input")
        val callId = input.getJSONObject(0).getString("call_id")
        assertTrue(callId.isNotBlank())
        assertEquals(callId, input.getJSONObject(1).getString("call_id"))
    }

    @Test
    fun `places assistant content before replayed function calls`() {
        val chatStyleRequest =
            JSONObject().put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "Read the file")
                    )
                    .put(
                        JSONObject()
                            .put("role", "assistant")
                            .put("content", "I will read it")
                            .put(
                                "tool_calls",
                                JSONArray().put(
                                    JSONObject()
                                        .put("id", "fc_read")
                                        .put("type", "function")
                                        .put(
                                            "function",
                                            JSONObject()
                                                .put("name", "read_file")
                                                .put("arguments", "{}")
                                        )
                                )
                            )
                    )
                    .put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", "fc_read")
                            .put("content", "file contents")
                    )
            )

        val request = OpenAIResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)
        DeepseekResponsesPayloadAdapter.normalizeRequest(request, fallbackInput = null)

        val input = request.getJSONArray("input")
        assertEquals("message", input.getJSONObject(0).getString("type"))
        assertEquals("user", input.getJSONObject(0).getString("role"))
        assertEquals("message", input.getJSONObject(1).getString("type"))
        assertEquals("assistant", input.getJSONObject(1).getString("role"))
        assertEquals("function_call", input.getJSONObject(2).getString("type"))
        assertEquals("fc_read", input.getJSONObject(2).getString("call_id"))
        assertEquals("function_call_output", input.getJSONObject(3).getString("type"))
        assertEquals("fc_read", input.getJSONObject(3).getString("call_id"))
    }
}
