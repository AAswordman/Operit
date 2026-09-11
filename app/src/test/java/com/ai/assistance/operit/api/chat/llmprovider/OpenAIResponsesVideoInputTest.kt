package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpenAIResponsesVideoInputTest {
    @Test
    fun mapsChatCompletionsVideoUrlObjectToResponsesInputVideo() {
        val request = chatRequest(
            JSONArray()
                .put(
                    JSONObject().apply {
                        put("type", "video_url")
                        put(
                            "video_url",
                            JSONObject().apply {
                                put("url", "data:video/mp4;base64,ZmFrZQ==")
                            },
                        )
                    },
                )
                .put(
                    JSONObject().apply {
                        put("type", "text")
                        put("text", "描述一下该视频")
                    },
                ),
        )

        val content = convertedContent(request)
        assertEquals(2, content.length())
        assertEquals("input_video", content.getJSONObject(0).getString("type"))
        assertEquals(
            "data:video/mp4;base64,ZmFrZQ==",
            content.getJSONObject(0).getString("video_url"),
        )
        assertEquals("input_text", content.getJSONObject(1).getString("type"))
        assertEquals("描述一下该视频", content.getJSONObject(1).getString("text"))
    }

    @Test
    fun preservesResponsesInputVideoStringUrl() {
        val request = chatRequest(
            JSONArray().put(
                JSONObject().apply {
                    put("type", "input_video")
                    put("video_url", "data:video/mp4;base64,ZmFrZQ==")
                },
            ),
        )

        val video = convertedContent(request).getJSONObject(0)
        assertEquals("input_video", video.getString("type"))
        assertEquals("data:video/mp4;base64,ZmFrZQ==", video.getString("video_url"))
    }

    @Test
    fun preservesResponsesInputVideoNestedUrl() {
        val request = chatRequest(
            JSONArray().put(
                JSONObject().apply {
                    put("type", "input_video")
                    put(
                        "video_url",
                        JSONObject().apply {
                            put("url", "https://example.test/clip.mp4")
                        },
                    )
                },
            ),
        )

        val video = convertedContent(request).getJSONObject(0)
        assertEquals("input_video", video.getString("type"))
        assertEquals("https://example.test/clip.mp4", video.getString("video_url"))
    }

    @Test
    fun dropsEmptyVideoUrlInsteadOfEmittingBlankPart() {
        val request = chatRequest(
            JSONArray()
                .put(
                    JSONObject().apply {
                        put("type", "video_url")
                        put("video_url", JSONObject())
                    },
                )
                .put(
                    JSONObject().apply {
                        put("type", "text")
                        put("text", "only text remains")
                    },
                ),
        )

        val content = convertedContent(request)
        assertEquals(1, content.length())
        assertEquals("input_text", content.getJSONObject(0).getString("type"))
        assertFalse(content.toString().contains("input_video"))
    }

    private fun chatRequest(content: JSONArray): JSONObject =
        JSONObject().apply {
            put("model", "gpt-5.4")
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", content)
                    },
                ),
            )
        }

    private fun convertedContent(request: JSONObject): JSONArray {
        val converted = OpenAIResponsesPayloadAdapter.toResponsesRequest(request)
        return converted.getJSONArray("input").getJSONObject(0).getJSONArray("content")
    }
}
