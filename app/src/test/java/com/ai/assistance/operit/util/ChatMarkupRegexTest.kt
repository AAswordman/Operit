package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.toolmarkup.ToolResultMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexTest {

    @Test fun toolTagName_acceptsBaseTool() {
        assertTrue(ChatMarkupRegex.isToolTagName("tool"))
    }

    @Test fun toolTagName_acceptsSuffix() {
        assertTrue(ChatMarkupRegex.isToolTagName("tool_code"))
    }

    @Test fun toolTagName_rejectsToolResult() {
        assertFalse(ChatMarkupRegex.isToolTagName("tool_result"))
    }

    @Test fun toolResultTagName_acceptsBaseToolResult() {
        assertTrue(ChatMarkupRegex.isToolResultTagName("tool_result"))
    }

    @Test fun toolResultTagName_acceptsSuffix() {
        assertTrue(ChatMarkupRegex.isToolResultTagName("tool_result_exec"))
    }

    @Test fun toolResultTagName_rejectsPlainTool() {
        assertFalse(ChatMarkupRegex.isToolResultTagName("tool"))
    }

    @Test fun normalizeToolLikeTagName_normalizesToolSuffix() {
        assertEquals("tool", ChatMarkupRegex.normalizeToolLikeTagName("tool_exec"))
    }

    @Test fun normalizeToolLikeTagName_normalizesToolResultSuffix() {
        assertEquals("tool_result", ChatMarkupRegex.normalizeToolLikeTagName("tool_result_exec"))
    }

    @Test fun normalizeToolLikeTagName_preservesUnknownTag() {
        assertEquals("status", ChatMarkupRegex.normalizeToolLikeTagName("status"))
    }

    @Test fun containsToolTag_detectsOpeningTag() {
        assertTrue(ChatMarkupRegex.containsToolTag("<tool name=\"run\"></tool>"))
    }

    @Test fun containsToolTag_rejectsToolResult() {
        assertFalse(ChatMarkupRegex.containsToolTag("<tool_result name=\"run\"></tool_result>"))
    }

    @Test fun containsToolResultTag_detectsOpeningTag() {
        assertTrue(ChatMarkupRegex.containsToolResultTag("<tool_result name=\"run\"></tool_result>"))
    }

    @Test fun containsAnyToolLikeTag_detectsEitherVariant() {
        assertTrue(ChatMarkupRegex.containsAnyToolLikeTag("<tool_result name=\"run\"></tool_result>"))
    }

    @Test fun extractOpeningTagName_readsFirstTag() {
        assertEquals("tool", ChatMarkupRegex.extractOpeningTagName("  <tool name=\"run\">"))
    }

    @Test fun extractOpeningTagName_returnsNullForPlainText() {
        assertNull(ChatMarkupRegex.extractOpeningTagName("plain text"))
    }

    @Test fun generateRandomToolTagName_usesToolPrefix() {
        assertTrue(ChatMarkupRegex.generateRandomToolTagName().startsWith("tool_"))
    }

    @Test fun generateRandomToolResultTagName_usesToolResultPrefix() {
        assertTrue(ChatMarkupRegex.generateRandomToolResultTagName().startsWith("tool_result_"))
    }

    @Test fun geminiMetaTag_wrapsSignature() {
        assertEquals(
            "<meta provider=\"gemini:thought_signature\">abc123</meta>",
            ChatMarkupRegex.geminiThoughtSignatureMetaTag("abc123")
        )
    }

    @Test fun extractGeminiThoughtSignature_readsSingleSignature() {
        assertEquals(
            "abc123",
            ChatMarkupRegex.extractGeminiThoughtSignature("<meta provider=\"gemini:thought_signature\">abc123</meta>")
        )
    }

    @Test fun extractGeminiThoughtSignature_usesLastMatchingSignature() {
        assertEquals(
            "second",
            ChatMarkupRegex.extractGeminiThoughtSignature(
                "<meta provider=\"gemini:thought_signature\">first</meta>" +
                    "<meta provider=\"gemini:thought_signature\">second</meta>"
            )
        )
    }

    @Test fun extractGeminiThoughtSignature_ignoresOtherProviders() {
        assertNull(ChatMarkupRegex.extractGeminiThoughtSignature("<meta provider=\"other\">value</meta>"))
    }

    @Test fun extractGeminiThoughtSignature_trimsBodyWhitespace() {
        assertEquals(
            "abc123",
            ChatMarkupRegex.extractGeminiThoughtSignature("<meta provider=\"gemini:thought_signature\">  abc123  </meta>")
        )
    }

    @Test fun removeGeminiThoughtSignatureMeta_removesMatchingTag() {
        assertEquals("content", ChatMarkupRegex.removeGeminiThoughtSignatureMeta("content<meta provider=\"gemini:thought_signature\">abc</meta>"))
    }

    @Test fun removeGeminiThoughtSignatureMeta_keepsOtherMeta() {
        val text = "<meta provider=\"other\">x</meta>"
        assertEquals(text, ChatMarkupRegex.removeGeminiThoughtSignatureMeta(text))
    }

    @Test fun removeGeminiThoughtSignatureMeta_isCaseInsensitiveOnProvider() {
        assertEquals("", ChatMarkupRegex.removeGeminiThoughtSignatureMeta("<meta provider=\"GEMINI:THOUGHT_SIGNATURE\">x</meta>"))
    }

    @Test fun toolCallPattern_extractsNameAndBody() {
        val match = ChatMarkupRegex.toolCallPattern.find("<tool name=\"run\">pwd</tool>")
        assertNotNull(match)
        assertEquals("run", match!!.groupValues[2])
        assertEquals("pwd", match.groupValues[3])
    }

    @Test fun toolTag_matchesSuffixVariant() {
        assertTrue(ChatMarkupRegex.toolTag.containsMatchIn("<tool_exec name=\"run\">pwd</tool_exec>"))
    }

    @Test fun toolSelfClosingTag_matchesSuffixVariant() {
        assertTrue(ChatMarkupRegex.toolSelfClosingTag.containsMatchIn("<tool_exec name=\"run\" />"))
    }

    @Test fun toolResultTagWithAttrs_extractsStatusBlock() {
        val match = ChatMarkupRegex.toolResultTagWithAttrs.find("<tool_result_exec status=\"ok\">done</tool_result_exec>")
        assertNotNull(match)
        assertEquals("done", match!!.groupValues[3])
    }

    @Test fun xmlToolRequestPattern_extractsDescriptionWhenPresent() {
        val match = ChatMarkupRegex.xmlToolRequestPattern.find("<tool name=\"run\" description=\"desc\">body</tool>")
        assertNotNull(match)
        assertEquals("run", match!!.groupValues[2])
        assertEquals("desc", match.groupValues[3])
        assertEquals("body", match.groupValues[4])
    }

    @Test fun toolResultBlock_keepsContentAndStatus() {
        val xml = "<tool_result name=\"run\" status=\"ok\"><content>done</content></tool_result>"
        val match = ChatMarkupRegex.toolResultTagWithAttrs.matchEntire(xml)
        assertNotNull(match)
        assertEquals("ok", ChatMarkupRegex.statusAttr.find(match!!.groupValues[2])!!.groupValues[1])
        assertEquals("done", ToolResultMarkup.contentFromBlock(xml))
    }
}
