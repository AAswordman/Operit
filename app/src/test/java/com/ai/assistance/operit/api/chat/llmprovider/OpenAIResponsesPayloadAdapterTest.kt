package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `non streaming response records web search call output item metadata`() {
        val response =
            JSONObject(
                """
                {
                  "status": "incomplete",
                  "output": [
                    {
                      "type": "web_search_call",
                      "id": "call_00_web",
                      "status": "completed",
                      "action": {
                        "type": "search",
                        "queries": ["deepseek responses web search"]
                      }
                    },
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "answer",
                          "annotations": []
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )

        val parsed = OpenAIResponsesPayloadAdapter.parseNonStreamingResponse(response)

        assertEquals(1, parsed.outputItemMetadataTags.size)
        assertTrue(
            parsed.outputItemMetadataTags.single()
                .contains("provider=\"openai:responses_output_item\"")
        )
    }

    @Test
    fun `responses request replays web search output item and removes display search markup`() {
        val webSearchItem =
            JSONObject()
                .put("type", "web_search_call")
                .put("id", "call_00_web")
                .put("status", "completed")
                .put(
                    "action",
                    JSONObject()
                        .put("type", "search")
                        .put("queries", JSONArray().put("deepseek responses web search"))
                )
        val outputItemMeta = OpenAIResponsesPayloadAdapter.createOutputItemMetadataTag(webSearchItem)!!
        val chatStyleRequest =
            JSONObject()
                .put("model", "deepseek-reasoner")
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "assistant")
                                .put(
                                    "content",
                                    outputItemMeta +
                                        "\n<search provider=\"deepseek\"><query>deepseek responses web search</query></search>" +
                                        "\nanswer"
                                )
                        )
                )

        val responsesRequest = OpenAIResponsesPayloadAdapter.toResponsesRequest(chatStyleRequest)
        val input = responsesRequest.getJSONArray("input")
        val replayedItem = input.getJSONObject(0)
        val visibleMessage = input.getJSONObject(1)

        assertEquals("web_search_call", replayedItem.getString("type"))
        assertEquals("call_00_web", replayedItem.getString("id"))
        assertEquals("search", replayedItem.getJSONObject("action").getString("type"))
        assertEquals(
            "deepseek responses web search",
            replayedItem.getJSONObject("action").getJSONArray("queries").getString(0)
        )
        assertEquals("message", visibleMessage.getString("type"))
        assertEquals("assistant", visibleMessage.getString("role"))
        assertEquals("answer", visibleMessage.getString("content"))
    }
}
