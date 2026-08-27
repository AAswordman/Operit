package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
