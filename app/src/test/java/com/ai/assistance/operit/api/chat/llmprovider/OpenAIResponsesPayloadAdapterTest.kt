package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * OpenAI Responses/chat 兼容解析的 usage 计数门测试（评审 P1-5/P2-1）：
 * - 按字段存在判断：显式全零 payload 也是“已观察到的 usage”（返回非 null，
 *   字段为 0），不按 “>0” 过滤；
 * - 全程 Long 解析，大于 Int 范围的 usage 不截断；
 * - usage 对象完全缺失/无任何相关字段 → null（未观察到）。
 */
class OpenAIResponsesPayloadAdapterTest {

    @Test
    fun `server web search is optional and keeps existing function tools`() {
        val functionTool = JSONObject("""{"type":"function","name":"read_file"}""")
        val disabledRequest = JSONObject().put("tools", JSONArray().put(functionTool))
        OpenAIResponsesPayloadAdapter.appendServerWebSearchTool(disabledRequest, enabled = false)
        assertEquals(1, disabledRequest.getJSONArray("tools").length())

        val enabledRequest = JSONObject(disabledRequest.toString())
        OpenAIResponsesPayloadAdapter.appendServerWebSearchTool(enabledRequest, enabled = true)
        val tools = enabledRequest.getJSONArray("tools")
        assertEquals(2, tools.length())
        assertEquals("function", tools.getJSONObject(0).getString("type"))
        assertEquals("web_search", tools.getJSONObject(1).getString("type"))

        OpenAIResponsesPayloadAdapter.appendServerWebSearchTool(enabledRequest, enabled = true)
        assertEquals(2, enabledRequest.getJSONArray("tools").length())
    }

    @Test
    fun `explicit zero payload is observed usage with zero fields`() {
        val counts =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject("""{"prompt_tokens": 0, "completion_tokens": 0}""")
            )!!
        assertEquals(0L, counts.totalInputTokens)
        assertEquals(0L, counts.outputTokens)
        assertEquals(0L, counts.cachedInputTokens)
        assertEquals(0L, counts.actualInputTokens)
    }

    @Test
    fun `zero cached split with non-zero totals is parsed`() {
        val counts =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject(
                    """{"prompt_tokens": 100, "completion_tokens": 50, "prompt_tokens_details": {"cached_tokens": 0}}"""
                )
            )!!
        assertEquals(100L, counts.totalInputTokens)
        assertEquals(100L, counts.actualInputTokens)
        assertEquals(0L, counts.cachedInputTokens)
        assertEquals(50L, counts.outputTokens)
    }

    @Test
    fun `values beyond int range stay exact instead of truncating`() {
        val counts =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject(
                    """{"prompt_tokens": 5000000000, "completion_tokens": 4000000000}"""
                )
            )!!
        assertEquals(5_000_000_000L, counts.totalInputTokens)
        assertEquals(4_000_000_000L, counts.outputTokens)
    }

    @Test
    fun `usage absent or without any token fields returns null`() {
        assertNull(OpenAIResponsesPayloadAdapter.parseUsageCounts(null))
        assertNull(OpenAIResponsesPayloadAdapter.parseUsageCounts(JSONObject("{}")))
        assertNull(
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject("""{"other": "x"}""")
            )
        )
    }

    @Test
    fun `web search output is preserved as hidden replay metadata`() {
        val outputItem =
            JSONObject(
                """{"type":"web_search_call","id":"ws_1","status":"completed","action":{"type":"search","query":"operit"}}"""
            )
        val parsed =
            OpenAIResponsesPayloadAdapter.parseNonStreamingResponse(
                JSONObject("""{"output":[${outputItem}]}""")
            )

        assertEquals(1, parsed.webSearchMetadataTags.size)
        val payload = parsed.webSearchMetadataTags.single()
            .substringAfter(">")
            .substringBefore("</meta>")
        val decoded = String(Base64.getDecoder().decode(payload), Charsets.UTF_8)
        assertEquals(outputItem.toString(), JSONObject(decoded).toString())
    }

    @Test
    fun `web search metadata is replayed as the original input item`() {
        val outputItem =
            JSONObject(
                """{"type":"web_search_call","id":"ws_2","status":"completed","action":{"type":"search","query":"history"}}"""
            )
        val metadataTag =
            OpenAIResponsesPayloadAdapter.parseNonStreamingResponse(
                JSONObject("""{"output":[${outputItem}]}""")
            ).webSearchMetadataTags.single()
        val chatStyleRequest = JSONObject().apply {
            put("model", "deepseek-chat")
            put(
                "messages",
                org.json.JSONArray()
                    .put(JSONObject().put("role", "assistant").put("content", "answer$metadataTag"))
            )
        }
        val request = OpenAIResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)

        assertEquals(outputItem.toString(), request.getJSONArray("input").getJSONObject(0).toString())
    }

    @Test
    fun `deepseek plaintext reasoning is preserved and replayed before function calls`() {
        val reasoningContent =
            JSONArray().put(
                JSONObject()
                    .put("type", "reasoning_text")
                    .put("text", "I need to inspect the workspace first.")
            )
        val reasoningItem =
            JSONObject()
                .put("type", "reasoning")
                .put("id", "rs_plain_1")
                .put("content", reasoningContent)
        val metadataTag =
            OpenAIResponsesPayloadAdapter.parseNonStreamingResponse(
                JSONObject("""{"output":[$reasoningItem]}"""),
                ApiProviderType.DEEPSEEK
            ).reasoningMetadataTags.single()
        val chatStyleRequest = singleToolContinuationRequest(
            assistantContent =
                "<think>I need to inspect the workspace first.</think>" +
                    "I will inspect the workspace.$metadataTag",
            callId = "call_plain_1",
            toolName = "list_files",
            arguments = "{\"path\":\"/workspace\"}"
        )

        val input = OpenAIResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)
            .getJSONArray("input")

        assertEquals("reasoning", input.getJSONObject(0).getString("type"))
        assertEquals("rs_plain_1", input.getJSONObject(0).getString("id"))
        assertEquals(
            reasoningContent.toString(),
            input.getJSONObject(0).getJSONArray("content").toString()
        )
        assertFalse(input.getJSONObject(0).has("encrypted_content"))
        assertFalse(input.getJSONObject(0).has("summary"))
        assertEquals("message", input.getJSONObject(1).getString("type"))
        assertEquals(
            "I will inspect the workspace.",
            input.getJSONObject(1).getString("content")
        )
        assertEquals("function_call", input.getJSONObject(2).getString("type"))
        assertEquals("call_plain_1", input.getJSONObject(2).getString("call_id"))
        assertEquals("function_call_output", input.getJSONObject(3).getString("type"))
        assertEquals("call_plain_1", input.getJSONObject(3).getString("call_id"))
    }

    @Test
    fun `encrypted reasoning metadata remains replayable before function calls`() {
        val reasoningItem =
            JSONObject()
                .put("type", "reasoning")
                .put("id", "rs_encrypted_1")
                .put("encrypted_content", "encrypted-reasoning")
                .put(
                    "summary",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "summary_text")
                            .put("text", "Inspecting the workspace.")
                    )
                )
        val metadataTag =
            OpenAIResponsesPayloadAdapter.parseNonStreamingResponse(
                JSONObject("""{"output":[$reasoningItem]}""")
            ).reasoningMetadataTags.single()
        val chatStyleRequest = singleToolContinuationRequest(
            assistantContent = "I will inspect the workspace.$metadataTag",
            callId = "call_encrypted_1",
            toolName = "list_files",
            arguments = "{}"
        )

        val input = OpenAIResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)
            .getJSONArray("input")

        assertEquals("reasoning", input.getJSONObject(0).getString("type"))
        assertEquals("rs_encrypted_1", input.getJSONObject(0).getString("id"))
        assertEquals("encrypted-reasoning", input.getJSONObject(0).getString("encrypted_content"))
        assertEquals("message", input.getJSONObject(1).getString("type"))
        assertEquals("function_call", input.getJSONObject(2).getString("type"))
        assertEquals("function_call_output", input.getJSONObject(3).getString("type"))
    }

    @Test
    fun `assistant message precedes function calls so outputs stay adjacent with matching ids`() {
        val firstCallId = "daxkrp0vn"
        val secondCallId = "daxkro1vn"
        val assistantMessage = JSONObject().apply {
            put("role", "assistant")
            put("content", "I will inspect both locations.")
            put(
                "tool_calls",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("id", firstCallId)
                            put("type", "function")
                            put(
                                "function",
                                JSONObject()
                                    .put("name", "query_memory")
                                    .put("arguments", "{\"query\":\"history\"}")
                            )
                        }
                    )
                    .put(
                        JSONObject().apply {
                            put("id", secondCallId)
                            put("type", "function")
                            put(
                                "function",
                                JSONObject()
                                    .put("name", "list_files")
                                    .put("arguments", "{\"path\":\"/tmp\"}")
                            )
                        }
                    )
            )
        }
        val request = JSONObject().apply {
            put("messages", JSONArray().apply {
                put(assistantMessage)
                put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", firstCallId)
                        .put("content", "memory result")
                )
                put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", secondCallId)
                        .put("content", "file result")
                )
            })
        }

        val input = OpenAIResponsesPayloadAdapter.toResponsesRequest(request).getJSONArray("input")

        assertEquals("message", input.getJSONObject(0).getString("type"))
        assertEquals("function_call", input.getJSONObject(1).getString("type"))
        assertEquals(firstCallId, input.getJSONObject(1).getString("call_id"))
        assertEquals("function_call", input.getJSONObject(2).getString("type"))
        assertEquals(secondCallId, input.getJSONObject(2).getString("call_id"))
        assertEquals("function_call_output", input.getJSONObject(3).getString("type"))
        assertEquals(firstCallId, input.getJSONObject(3).getString("call_id"))
        assertEquals("function_call_output", input.getJSONObject(4).getString("type"))
        assertEquals(secondCallId, input.getJSONObject(4).getString("call_id"))
    }

    private fun singleToolContinuationRequest(
        assistantContent: String,
        callId: String,
        toolName: String,
        arguments: String
    ): JSONObject = JSONObject().apply {
        put(
            "messages",
            JSONArray()
                .put(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", assistantContent)
                        .put(
                            "tool_calls",
                            JSONArray().put(
                                JSONObject()
                                    .put("id", callId)
                                    .put("type", "function")
                                    .put(
                                        "function",
                                        JSONObject()
                                            .put("name", toolName)
                                            .put("arguments", arguments)
                                    )
                            )
                        )
                )
                .put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", callId)
                        .put("content", "workspace result")
                )
        )
    }

}
