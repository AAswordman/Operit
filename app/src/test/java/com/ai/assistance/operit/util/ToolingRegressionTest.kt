package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.toolmarkup.ToolResultMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolingRegressionTest {

    @Test fun toolResultAnyPattern_matchesToolResult() {
        assertTrue(ChatMarkupRegex.toolResultAnyPattern.containsMatchIn("<tool_result name=\"run\">done</tool_result>"))
    }

    @Test fun toolResultWithNameAnyPattern_extractsNameAndBody() {
        val match = ChatMarkupRegex.toolResultWithNameAnyPattern.find("<tool_result name=\"run\">done</tool_result>")
        assertEquals("run", match!!.groupValues[2])
        assertEquals("done", match.groupValues[3])
    }

    @Test fun toolOrToolResultBlock_matchesToolBlock() {
        assertTrue(ChatMarkupRegex.toolOrToolResultBlock.containsMatchIn("<tool name=\"run\">pwd</tool>"))
    }

    @Test fun statusPattern_extractsBody() {
        val match = ChatMarkupRegex.xmlStatusPattern.find("<status type=\"ok\">done</status>")
        assertEquals("ok", match!!.groupValues[1])
        assertEquals("done", match.groupValues[5])
    }

    @Test fun nameAttr_extractsNameAttribute() {
        assertEquals("run", ChatMarkupRegex.nameAttr.find("name=\"run\"")!!.groupValues[1])
    }

    @Test fun statusAttr_extractsStatusAttribute() {
        assertEquals("ok", ChatMarkupRegex.statusAttr.find("status=\"ok\"")!!.groupValues[1])
    }

    @Test fun typeAttr_extractsTypeAttribute() {
        assertEquals("loading", ChatMarkupRegex.typeAttr.find("type=\"loading\"")!!.groupValues[1])
    }

    @Test fun titleAttr_extractsTitleAttribute() {
        assertEquals("hello", ChatMarkupRegex.titleAttr.find("title=\"hello\"")!!.groupValues[1])
    }

    @Test fun subtitleAttr_extractsSubtitleAttribute() {
        assertEquals("world", ChatMarkupRegex.subtitleAttr.find("subtitle=\"world\"")!!.groupValues[1])
    }

    @Test fun toolAttr_extractsToolAttribute() {
        assertEquals("browser", ChatMarkupRegex.toolAttr.find("tool=\"browser\"")!!.groupValues[1])
    }

    @Test fun uuidAttr_extractsUuidAttribute() {
        assertEquals("123", ChatMarkupRegex.uuidAttr.find("uuid=\"123\"")!!.groupValues[1])
    }

    @Test fun toolResultBody_extractsInnerContent() {
        assertEquals("done", ToolResultMarkup.contentFromBody("<content>done</content>"))
    }

    @Test fun errorTag_extractsInnerContent() {
        assertEquals("boom", ChatMarkupRegex.errorTag.find("<error>boom</error>")!!.groupValues[1])
    }

    @Test fun statusTag_matchesStatusBlock() {
        assertTrue(ChatMarkupRegex.statusTag.containsMatchIn("<status type=\"ok\">done</status>"))
    }

    @Test fun statusSelfClosingTag_matchesBlock() {
        assertTrue(ChatMarkupRegex.statusSelfClosingTag.containsMatchIn("<status type=\"ok\" />"))
    }

    @Test fun thinkTag_matchesThinkBlock() {
        assertTrue(ChatMarkupRegex.thinkTag.containsMatchIn("<thinking>draft</thinking>"))
    }

    @Test fun thinkSelfClosingTag_matchesThinkBlock() {
        assertTrue(ChatMarkupRegex.thinkSelfClosingTag.containsMatchIn("<think />"))
    }

    @Test fun searchTag_matchesSearchBlock() {
        assertTrue(ChatMarkupRegex.searchTag.containsMatchIn("<search>q</search>"))
    }

    @Test fun searchSelfClosingTag_matchesSearchBlock() {
        assertTrue(ChatMarkupRegex.searchSelfClosingTag.containsMatchIn("<search />"))
    }

    @Test fun metaTag_matchesMetaBlock() {
        assertTrue(ChatMarkupRegex.metaTag.containsMatchIn("<meta provider=\"x\">y</meta>"))
    }

    @Test fun emotionTag_matchesEmotionBlock() {
        assertTrue(ChatMarkupRegex.emotionTag.containsMatchIn("<emotion>happy</emotion>"))
    }

    @Test fun memoryTag_matchesMemoryBlock() {
        assertTrue(ChatMarkupRegex.memoryTag.containsMatchIn("<memory>note</memory>"))
    }

    @Test fun replyToTag_extractsSenderAndTimestamp() {
        val match = ChatMarkupRegex.replyToTag.find("<reply_to sender=\"u\" timestamp=\"t\">body</reply_to>")
        assertEquals("u", match!!.groupValues[1])
        assertEquals("t", match.groupValues[2])
        assertEquals("body", match.groupValues[3])
    }

    @Test fun attachmentDataTag_extractsAttachmentPayload() {
        val match = ChatMarkupRegex.attachmentDataTag.find("<attachment id=\"1\" filename=\"a.txt\" type=\"text/plain\">abc</attachment>")
        assertEquals("1", match!!.groupValues[1])
        assertEquals("a.txt", match.groupValues[2])
        assertEquals("text/plain", match.groupValues[3])
        assertEquals("abc", match.groupValues[5])
    }

    @Test fun attachmentDataSelfClosingTag_extractsAttachmentPayload() {
        val match = ChatMarkupRegex.attachmentDataSelfClosingTag.find("<attachment id=\"1\" filename=\"a.txt\" type=\"text/plain\" content=\"abc\" />")
        assertEquals("1", match!!.groupValues[1])
        assertEquals("a.txt", match.groupValues[2])
        assertEquals("text/plain", match.groupValues[3])
    }
}
