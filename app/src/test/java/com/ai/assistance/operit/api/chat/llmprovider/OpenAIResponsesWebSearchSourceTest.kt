package com.ai.assistance.operit.api.chat.llmprovider

import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIResponsesWebSearchSourceTest {

    @Test
    fun `collects web search action sources and final url citations`() {
        val response = JSONObject(
            """
            {
              "output": [
                {
                  "type": "web_search_call",
                  "status": "completed",
                  "action": {
                    "type": "search",
                    "query": "deepseek responses web search",
                    "sources": [
                      {
                        "type": "url",
                        "title": "DeepSeek Docs",
                        "url": "https://api-docs.deepseek.com/guides/responses_api",
                        "site_name": "DeepSeek"
                      }
                    ]
                  }
                },
                {
                  "type": "message",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "Result",
                      "annotations": [
                        {
                          "type": "url_citation",
                          "start_index": 0,
                          "end_index": 6,
                          "url": "https://developers.openai.com/api/docs/guides/tools-web-search",
                          "title": "Web search"
                        },
                        {
                          "type": "source",
                          "url": "https://example.com/non-document-annotation",
                          "title": "Not a documented Responses citation"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val xml = SearchSourceProbeProvider().sourceXml(response)

        assertTrue(xml.contains("""site_name="DeepSeek""""))
        assertTrue(xml.contains("""url="https://api-docs.deepseek.com/guides/responses_api""""))
        assertTrue(xml.contains("""type="url_citation""""))
        assertTrue(xml.contains("""start_index="0""""))
        assertTrue(xml.contains("""end_index="6""""))
        assertTrue(xml.contains("""url="https://developers.openai.com/api/docs/guides/tools-web-search""""))
        assertFalse(xml.contains("https://example.com/non-document-annotation"))
    }

    @Test
    fun `merge keeps one source per url while preserving extra metadata`() {
        val item = JSONObject(
            """
            {
              "type": "web_search_call",
              "action": {
                "type": "search",
                "sources": [
                  {
                    "type": "url",
                    "title": "Primary Title",
                    "url": "https://example.com/a",
                    "source": "Example"
                  }
                ]
              }
            }
            """.trimIndent()
        )
        val response = JSONObject(
            """
            {
              "output": [
                {
                  "type": "message",
                  "content": [
                    {
                      "type": "output_text",
                      "annotations": [
                        {
                          "type": "url_citation",
                          "title": "Citation Title",
                          "url": "https://example.com/a",
                          "start_index": 2
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val xml = SearchSourceProbeProvider().mergedSourceXml(item, response)

        assertEquals(1, Regex("<source ").findAll(xml).count())
        assertTrue(xml.contains("""title="Primary Title""""))
        assertTrue(xml.contains("""source="Example""""))
        assertTrue(xml.contains("""start_index="2""""))
    }

    @Test
    fun `collects deepseek web search queries without source urls`() {
        val item = JSONObject(
            """
            {
              "type": "web_search_call",
              "id": "call_00_example",
              "status": "completed",
              "action": {
                "type": "search",
                "queries": [
                  "Operit AI assistant official website",
                  "Operit AI assistant GitHub repository",
                  "ws_call_id=call_00_example"
                ]
              }
            }
            """.trimIndent()
        )

        val xml = SearchSourceProbeProvider().queryXml(item)

        assertEquals(2, Regex("<query>").findAll(xml).count())
        assertTrue(xml.contains("<query>Operit AI assistant official website</query>"))
        assertTrue(xml.contains("<query>Operit AI assistant GitHub repository</query>"))
        assertFalse(xml.contains("ws_call_id"))
    }

    @Test
    fun `collects web search action url as source`() {
        val item = JSONObject(
            """
            {
              "type": "web_search_call",
              "status": "completed",
              "action": {
                "type": "open_page",
                "url": "https://example.com/news",
                "name": "Example News",
                "pattern": "DeepSeek"
              }
            }
            """.trimIndent()
        )

        val xml = SearchSourceProbeProvider().actionSourceXml(item)

        assertTrue(xml.contains("""type="open_page""""))
        assertTrue(xml.contains("""url="https://example.com/news""""))
        assertTrue(xml.contains("""name="Example News""""))
        assertTrue(xml.contains("""pattern="DeepSeek""""))
    }

    private class SearchSourceProbeProvider : OpenAIProvider(
        apiEndpoint = "https://example.com/v1/chat/completions",
        apiKeyProvider = SingleApiKeyProvider("test-key"),
        modelName = "test-model",
        client = OkHttpClient(),
    ) {
        fun sourceXml(response: JSONObject): String {
            return renderSources(collectResponsesWebSearchSources(response))
        }

        fun mergedSourceXml(item: JSONObject, response: JSONObject): String {
            val action = item.optJSONObject("action")
            val sources = mergeResponsesWebSearchSources(
                primary = collectResponsesWebSearchActionSources(
                    action = action,
                    actionType = action?.optString("type", "")?.trim().orEmpty(),
                ) + collectResponsesWebSearchSourceArray(action?.optJSONArray("sources")),
                additional = collectResponsesWebSearchSources(response),
            )
            return renderSources(sources)
        }

        fun actionSourceXml(item: JSONObject): String {
            val action = item.optJSONObject("action")
            val sources = collectResponsesWebSearchActionSources(
                action = action,
                actionType = action?.optString("type", "")?.trim().orEmpty(),
            )
            return renderSources(sources)
        }

        fun queryXml(item: JSONObject): String {
            val action = item.optJSONObject("action")
            val queries = collectResponsesWebSearchQueries(
                action = action,
                actionType = action?.optString("type", "")?.trim().orEmpty(),
            )
            return buildString {
                queries.forEach { query ->
                    append("<query>")
                    append(query)
                    append("</query>")
                }
            }
        }

        private fun renderSources(sources: List<ResponsesWebSearchSource>): String {
            return buildString {
                sources.forEach { source ->
                    append("<source")
                    appendResponsesWebSearchSourceAttributes(this, source)
                    append(" />")
                }
            }
        }
    }
}
