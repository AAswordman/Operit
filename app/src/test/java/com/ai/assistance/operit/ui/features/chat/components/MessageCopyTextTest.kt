package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCopyTextTest {

    @Test fun cleanMessageContentForCopy_removesMultipleOpenAiReasoningMetadata() {
        val content =
            """
            <meta provider="openai:responses_reasoning">first-payload</meta>
            <tool name="run"><param name="command">pwd</param></tool>
            <meta provider="openai:responses_reasoning">second-payload</meta>
            final answer
            """.trimIndent()

        assertEquals("final answer", cleanMessageContentForCopy(content))
    }

    @Test fun cleanMessageContentForCopy_removesGeminiThoughtSignature() {
        val content = "prefix<meta provider=\"gemini:thought_signature\">signature</meta>suffix"

        assertEquals("prefixsuffix", cleanMessageContentForCopy(content))
    }

    @Test fun cleanMessageContentForCopy_preservesOtherMetaAndMarkdown() {
        val content = "<meta provider=\"other\">value</meta>\n**answer**"

        assertEquals(content, cleanMessageContentForCopy(content))
    }

    @Test fun cleanMessageContentForCopy_preservesHtmlMetaBeforeInternalMetadata() {
        val content =
            "<meta charset=\"utf-8\">visible" +
                "<meta provider=\"openai:responses_reasoning\">payload</meta>answer"

        assertEquals("<meta charset=\"utf-8\">visibleanswer", cleanMessageContentForCopy(content))
    }
}