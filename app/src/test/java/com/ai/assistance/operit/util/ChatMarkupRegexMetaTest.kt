package com.ai.assistance.operit.util

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexMetaTest {

    @Test fun extractSignature_ignoresEmptyBody() {
        assertNull(ChatMarkupRegex.extractGeminiThoughtSignature("<meta provider=\"gemini:thought_signature\">   </meta>"))
    }

    @Test fun removeSignature_preservesTrailingText() {
        assertEquals("prefixsuffix", ChatMarkupRegex.removeGeminiThoughtSignatureMeta("prefix<meta provider=\"gemini:thought_signature\">a</meta>suffix"))
    }

    @Test fun removeSignature_removesMultipleMatchingTags() {
        assertEquals("body", ChatMarkupRegex.removeGeminiThoughtSignatureMeta("<meta provider=\"gemini:thought_signature\">a</meta>body<meta provider=\"gemini:thought_signature\">b</meta>"))
    }

    @Test fun extractSignature_returnsLastAmongMixedMetaTags() {
        assertEquals(
            "target",
            ChatMarkupRegex.extractGeminiThoughtSignature(
                "<meta provider=\"other\">x</meta><meta provider=\"gemini:thought_signature\">target</meta>"
            )
        )
    }

    @Test fun removeOpenAiReasoning_preservesVoidMetaBeforeMatchingTag() {
        val content =
            "<meta charset=\"utf-8\">visible" +
                "<meta provider=\"openai:responses_reasoning\">payload</meta>answer"

        assertEquals(
            "<meta charset=\"utf-8\">visibleanswer",
            ChatMarkupRegex.removeOpenAiResponsesReasoningMeta(content)
        )
    }

    @Test fun removeOpenAiReasoning_ignoresProviderTextInMetaBody() {
        val content = "<meta>provider=\"openai:responses_reasoning\"</meta>"

        assertEquals(content, ChatMarkupRegex.removeOpenAiResponsesReasoningMeta(content))
    }

    @Test fun webSearchMetadata_roundTripsAndIsRemovedFromModelVisibleContent() {
        val metadata =
            ChatMarkupRegex.openAiResponsesWebSearchMetaTag(
                "eyJ0eXBlIjoid2ViX3NlYXJjaF9jYWxsIn0="
            )
        val content = "answer$metadata"

        assertEquals(
            listOf("eyJ0eXBlIjoid2ViX3NlYXJjaF9jYWxsIn0="),
            ChatMarkupRegex.extractOpenAiResponsesWebSearchPayloads(content)
        )
        assertEquals(content, ChatMarkupRegex.removeOpenAiResponsesReasoningMeta(content))
        assertEquals("answer", ChatMarkupRegex.removeOpenAiResponsesWebSearchMeta(content))
    }

    @Test fun webSearchMetadata_parsesAsReadOnlyServerToolRecord() {
        val json =
            """{"type":"web_search_call","id":"ws_1","status":"completed","action":{"type":"search","query":"Operit"}}"""
        val payload = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val record =
            ChatMarkupRegex.parseOpenAiResponsesServerToolCall(
                ChatMarkupRegex.openAiResponsesWebSearchMetaTag(payload)
            )!!

        assertEquals("ws_1", record.callId)
        assertEquals("web_search", record.toolType)
        assertEquals("completed", record.status)
        assertEquals("search", record.actionType)
        assertEquals("Operit", record.actionSummary)
        assertTrue(record.rawJson.contains("web_search_call"))
    }

    @Test fun invalidWebSearchMetadata_doesNotCreateServerToolRecord() {
        val payload = Base64.getEncoder().encodeToString(
            """{"type":"web_search_call","status":"completed"}""".toByteArray(Charsets.UTF_8)
        )

        assertNull(
            ChatMarkupRegex.parseOpenAiResponsesServerToolCall(
                ChatMarkupRegex.openAiResponsesWebSearchMetaTag(payload)
            )
        )
    }
}
