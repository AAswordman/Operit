package com.ai.assistance.operit.data.api

import com.ai.assistance.operit.data.config.model.ModelOption
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AntigravityModelClientTest {
    private val client = AntigravityModelClient(OkHttpClient())

    @Test
    fun parseModels_readsModelNameAndDisplayNameFromObjectResponse() {
        val payload = JSONObject(
            """
            {
              "models": {
                "source-b": {"modelName": "models/gemini-3.1-pro-high", "displayName": "Gemini 3.1 Pro High"},
                "source-a": {"modelName": "claude-sonnet-4-6", "displayName": "Claude Sonnet 4.6"},
                "duplicate": {"modelName": "claude-sonnet-4-6", "displayName": "Duplicate"}
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ModelOption("claude-sonnet-4-6", "Claude Sonnet 4.6"),
                ModelOption("gemini-3.1-pro-high", "Gemini 3.1 Pro High"),
            ),
            client.parseModels(payload),
        )
    }

    @Test
    fun parseModels_fallsBackToSourceIdWhenModelNameIsMissing() {
        val payload = JSONObject(
            """
            {
              "models": [
                {"id": "gemini-3.7-flash", "displayName": "Gemini 3.7 Flash"},
                {"modelName": "models/gpt-oss-120b"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ModelOption("gemini-3.7-flash", "Gemini 3.7 Flash"),
                ModelOption("gpt-oss-120b", "gpt-oss-120b"),
            ),
            client.parseModels(payload),
        )
    }
}
