package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.ChatMarkupRegex
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeepseekResponsesPayloadAdapterTest {

    @Test
    fun `plaintext reasoning is preserved and replayed before function calls`() {
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
            DeepseekResponsesPayloadAdapter.parseNonStreamingResponse(
                JSONObject("""{"output":[$reasoningItem]}""")
            ).reasoningMetadataTags.single()
        val chatStyleRequest = singleToolContinuationRequest(
            assistantContent =
                "<think>I need to inspect the workspace first.</think>" +
                    "I will inspect the workspace.$metadataTag",
            callId = "call_plain_1",
            toolName = "list_files",
            arguments = "{\"path\":\"/workspace\"}"
        )

        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)
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
    fun `encrypted reasoning is not emitted as deepseek reasoning metadata`() {
        val reasoningItem =
            JSONObject()
                .put("type", "reasoning")
                .put("id", "rs_encrypted_1")
                .put("encrypted_content", "encrypted-reasoning")
                .put("summary", JSONArray())

        val parsed =
            DeepseekResponsesPayloadAdapter.parseNonStreamingResponse(
                JSONObject("""{"output":[$reasoningItem]}""")
            )

        assertEquals(0, parsed.reasoningMetadataTags.size)
    }

    @Test
    fun `commentary thinking is preserved before the related function call`() {
        val commentaryContent =
            JSONArray().put(
                JSONObject()
                    .put("type", "output_text")
                    .put("text", "I need to activate the package before calling its tool.")
            )
        val commentaryItem =
            JSONObject()
                .put("type", "message")
                .put("id", "msg_commentary_1")
                .put("role", "assistant")
                .put("phase", "commentary")
                .put("content", commentaryContent)
        val parsed =
            DeepseekResponsesPayloadAdapter.parseNonStreamingResponse(
                JSONObject("""{"output":[$commentaryItem]}""")
            )
        assertEquals(0, parsed.reasoningChunks.size)
        assertEquals(0, parsed.textChunks.size)
        val metadataTag = parsed.outputItemMetadataTags.single()
        val chatStyleRequest = singleToolContinuationRequest(
            assistantContent = metadataTag,
            callId = "call_commentary_1",
            toolName = "use_package",
            arguments = "{\"package_name\":\"super_admin\"}"
        )

        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)
            .getJSONArray("input")

        assertEquals(3, input.length())
        assertEquals("reasoning", input.getJSONObject(0).getString("type"))
        assertEquals("msg_commentary_1", input.getJSONObject(0).getString("id"))
        val replayedContent = input.getJSONObject(0).getJSONArray("content")
        assertEquals("reasoning_text", replayedContent.getJSONObject(0).getString("type"))
        assertEquals(
            "I need to activate the package before calling its tool.",
            replayedContent.getJSONObject(0).getString("text")
        )
        assertEquals("function_call", input.getJSONObject(1).getString("type"))
        assertEquals("call_commentary_1", input.getJSONObject(1).getString("call_id"))
        assertEquals("function_call_output", input.getJSONObject(2).getString("type"))
        assertEquals("call_commentary_1", input.getJSONObject(2).getString("call_id"))
    }

    @Test
    fun `buffered commentary thinking is preserved when the completed item has no content`() {
        val commentaryItem =
            JSONObject()
                .put("type", "message")
                .put("role", "assistant")
                .put("phase", "commentary")
        val commentaryText = "I need to wait for the command before deciding the next action."
        val metadataTag =
            DeepseekResponsesPayloadAdapter.createStreamingCommentaryMetadataTag(
                commentaryItem,
                commentaryText
            )
                ?: throw AssertionError("Expected commentary metadata")
        val chatStyleRequest = singleToolContinuationRequest(
            assistantContent = metadataTag,
            callId = "call_buffered_commentary_1",
            toolName = "terminal_wait",
            arguments = "{}"
        )

        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)
            .getJSONArray("input")

        assertEquals(3, input.length())
        assertEquals("reasoning", input.getJSONObject(0).getString("type"))
        val replayedContent = input.getJSONObject(0).getJSONArray("content")
        assertEquals("reasoning_text", replayedContent.getJSONObject(0).getString("type"))
        assertEquals(commentaryText, replayedContent.getJSONObject(0).getString("text"))
        assertEquals("function_call", input.getJSONObject(1).getString("type"))
        assertEquals("function_call_output", input.getJSONObject(2).getString("type"))
    }

    /**
     * 评审 P1 回归：同一会话从 Chat Completions 端点切到 Responses 端点后，
     * Chat Completions 写入的 deepseek:server_tool_calls meta 必须在转换链路里被剥掉。
     * 否则整段 Base64 会作为 assistant 可见内容发给服务端，污染上下文并白烧 token。
     */
    @Test
    fun `deepseek server tool calls meta never leaks into responses input`() {
        val metaTag =
            ChatMarkupRegex.deepseekServerToolCallsMetaTag("W3siY2FsbHMiOltdfV0=")
        val chatStyleRequest =
            plainAssistantRequest("答案在这里。$metaTag")

        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)
            .getJSONArray("input")
        val serialized = input.toString()

        assertEquals(false, serialized.contains("deepseek:server_tool_calls"))
        assertEquals(false, serialized.contains("W3siY2FsbHMiOltdfV0="))
        assertEquals(true, serialized.contains("答案在这里。"))
    }

    /**
     * 闭环回归：用真实的 server_tool_calls 载荷走一遍「写入 meta → 读回」，
     * 证明剥离修复没有顺手破坏 Chat Completions 侧的回传能力。
     *
     * 单测只能覆盖到这一层；真实请求（能否 200、`<search>` 是否渲染）
     * 需要可用的 DeepSeek 联网搜索额度，本地无法完成。
     */
    @Test
    fun `chat completions meta round trip still decodes server tool calls`() {
        val serverToolCalls =
            JSONArray().put(
                JSONObject()
                    .put("role", "assistant")
                    .put(
                        "calls",
                        JSONArray().put(
                            JSONObject()
                                .put("kind", "search")
                                .put(
                                    "action",
                                    JSONObject()
                                        .put("type", "search")
                                        .put("queries", JSONArray().put("Kotlin 协程"))
                                )
                        )
                    )
            )
        // 与 DeepseekProvider.chatCompletionWebSearchReplayMetaTag 相同的编码方式
        val base64 =
            java.util.Base64.getEncoder()
                .encodeToString(serverToolCalls.toString().toByteArray(Charsets.UTF_8))
        val content = "答案在这里。${ChatMarkupRegex.deepseekServerToolCallsMetaTag(base64)}"

        // 1) Chat Completions 重建历史时能取回载荷
        val payloads = ChatMarkupRegex.extractDeepseekServerToolCallsPayloads(content)
        assertEquals(1, payloads.size)
        val decoded = JSONArray(String(java.util.Base64.getDecoder().decode(payloads.single()), Charsets.UTF_8))
        assertEquals(
            "Kotlin 协程",
            decoded.getJSONObject(0).getJSONArray("calls").getJSONObject(0)
                .getJSONObject("action").getJSONArray("queries").getString(0)
        )

        // 2) Responses 转换链路把同一段内容剥干净，只留可见正文
        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(
            plainAssistantRequest(content)
        ).getJSONArray("input").toString()
        assertEquals(false, input.contains(base64))
        assertEquals(false, input.contains("deepseek:server_tool_calls"))
        assertEquals(true, input.contains("答案在这里。"))
    }

    private fun plainAssistantRequest(assistantContent: String): JSONObject = JSONObject().apply {
        put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", assistantContent)
            )
        )
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
