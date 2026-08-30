package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiCompatibleContentPartsTest {
    @Test
    fun splitsMistralThinkingChunksFromVisibleText() {
        val parts = splitOpenAiCompatibleContent(
            JSONArray(
                """
                [
                  {
                    "type": "thinking",
                    "thinking": [
                      {"type": "text", "text": "First reasoning step. "},
                      {"type": "text", "text": "Second reasoning step."}
                    ]
                  },
                  {"type": "text", "text": "Final answer."}
                ]
                """.trimIndent()
            )
        )

        assertEquals("First reasoning step. Second reasoning step.", parts.reasoningContent)
        assertEquals("Final answer.", parts.regularContent)
    }

    @Test
    fun fallsBackToTextForEachStructuredReasoningChunk() {
        val parts = splitOpenAiCompatibleContent(
            JSONArray(
                """
                [
                  {"type": "thinking", "thinking": [{"type": "text", "text": "A"}]},
                  {"type": "reasoning", "text": "B"},
                  {"type": "output_text", "text": "C"}
                ]
                """.trimIndent()
            )
        )

        assertEquals("AB", parts.reasoningContent)
        assertEquals("C", parts.regularContent)
    }

    @Test
    fun preservesPlainTextContent() {
        val parts = splitOpenAiCompatibleContent("Plain response")

        assertEquals("", parts.reasoningContent)
        assertEquals("Plain response", parts.regularContent)
    }
}
