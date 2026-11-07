package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeepseekResponsesPayloadAdapterTest {

    @Test
    fun `uploaded user image keeps its file reference and analysis question`() {
        val question = "Describe the objects and their relative positions."
        val request = JSONObject().put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "text").put("text", question))
                            .put(JSONObject().put("type", "file").put("file_id", "file-api-user-image"))
                    )
            )
        )

        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(request).getJSONArray("input")
        assertEquals(1, input.length())
        assertEquals("user", input.getJSONObject(0).getString("role"))
        val content = input.getJSONObject(0).getJSONArray("content")
        assertEquals(2, content.length())
        assertEquals("input_text", content.getJSONObject(0).getString("type"))
        assertEquals(question, content.getJSONObject(0).getString("text"))
        val image = content.getJSONObject(1)
        assertEquals("input_image", image.getString("type"))
        assertEquals("file-api-user-image", image.getString("file_id"))
        assertFalse(image.has("image_url"))
    }

    @Test
    fun `uploaded tool image remains in function output with its analysis text`() {
        val request = singleToolContinuationRequest(
            assistantContent = "",
            callId = "call_image",
            toolName = "read_file",
            arguments = "{\"path\":\"/sdcard/image.png\",\"direct_image\":true}"
        )
        request.getJSONArray("messages").getJSONObject(1).put(
            "content",
            JSONArray()
                .put(JSONObject().put("type", "file").put("file_id", "file-api-tool-image"))
                .put(JSONObject().put("type", "text").put("text", "Image from read_file"))
        )

        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(request).getJSONArray("input")
        assertEquals(2, input.length())
        assertEquals("function_call", input.getJSONObject(0).getString("type"))
        val functionOutput = input.getJSONObject(1)
        assertEquals("function_call_output", functionOutput.getString("type"))
        assertEquals("call_image", functionOutput.getString("call_id"))
        val output = functionOutput.getJSONArray("output")
        assertEquals(2, output.length())
        assertEquals("input_image", output.getJSONObject(0).getString("type"))
        assertEquals("file-api-tool-image", output.getJSONObject(0).getString("file_id"))
        assertFalse(output.getJSONObject(0).has("image_url"))
        assertEquals("Image from read_file", output.getJSONObject(1).getString("text"))
    }

    @Test
    fun `normalized input images preserve file references and URL detail`() {
        val imageUrl = "data:image/png;base64,aW1hZ2U="
        val request = JSONObject().put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "input_image").put("file_id", "file-api-normalized"))
                            .put(JSONObject().put("type", "input_image").put("image_url", imageUrl).put("detail", "low"))
                    )
            )
        )

        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(request).getJSONArray("input")
        val content = input.getJSONObject(0).getJSONArray("content")
        assertEquals(2, content.length())
        assertEquals("input_image", content.getJSONObject(0).getString("type"))
        assertEquals("file-api-normalized", content.getJSONObject(0).getString("file_id"))
        assertFalse(content.getJSONObject(0).has("image_url"))
        assertEquals("input_image", content.getJSONObject(1).getString("type"))
        assertEquals(imageUrl, content.getJSONObject(1).getString("image_url"))
        assertEquals("low", content.getJSONObject(1).getString("detail"))
        assertFalse(content.getJSONObject(1).has("file_id"))
    }

    @Test
    fun `inline image URL stays available after responses conversion`() {
        val imageUrl = "data:image/png;base64,aW1hZ2U="
        val request = JSONObject().put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject().put("url", imageUrl))
                        )
                    )
            )
        )

        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(request).getJSONArray("input")
        val content = input.getJSONObject(0).getJSONArray("content")
        assertEquals(1, content.length())
        assertEquals("input_image", content.getJSONObject(0).getString("type"))
        assertEquals(imageUrl, content.getJSONObject(0).getString("image_url"))
        assertFalse(content.getJSONObject(0).has("file_id"))
    }

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
    fun `legacy reasoning content is preserved and replayed before function calls`() {
        val reasoningContent =
            JSONArray().put(
                JSONObject()
                    .put("type", "reasoning_content")
                    .put("text", "I need to inspect the workspace first.")
            )
        val reasoningItem =
            JSONObject()
                .put("type", "reasoning")
                .put("id", "rs_legacy_1")
                .put("content", reasoningContent)
        val parsed =
            DeepseekResponsesPayloadAdapter.parseNonStreamingResponse(
                JSONObject("{\"output\":[$reasoningItem]}")
            )
        val metadataTag = parsed.reasoningMetadataTags.single()
        val chatStyleRequest = singleToolContinuationRequest(
            assistantContent = metadataTag,
            callId = "call_legacy_1",
            toolName = "list_files",
            arguments = "{\"path\":\"/workspace\"}"
        )

        val input = DeepseekResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)
            .getJSONArray("input")
        val replayedContent = input.getJSONObject(0).getJSONArray("content")

        assertEquals("reasoning_content", replayedContent.getJSONObject(0).getString("type"))
        assertEquals(
            "I need to inspect the workspace first.",
            replayedContent.getJSONObject(0).getString("text")
        )
        assertEquals("function_call", input.getJSONObject(1).getString("type"))
        assertEquals("function_call_output", input.getJSONObject(2).getString("type"))
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
