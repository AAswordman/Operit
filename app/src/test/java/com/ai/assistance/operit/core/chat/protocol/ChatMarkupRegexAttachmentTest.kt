package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexAttachmentTest {

    @Test fun attachmentTag_matchesExplicitAttachmentBlock() {
        assertTrue(ChatMarkupRegex.attachmentTag.containsMatchIn("<attachment id=\"1\">a</attachment>"))
    }

    @Test fun attachmentSelfClosingTag_matchesSelfClosingAttachment() {
        assertTrue(ChatMarkupRegex.attachmentSelfClosingTag.containsMatchIn("<attachment id=\"1\" />"))
    }

    @Test fun workspaceAttachmentTag_matchesWorkspaceBlock() {
        assertTrue(ChatMarkupRegex.workspaceAttachmentTag.containsMatchIn("<workspace_attachment>data</workspace_attachment>"))
    }

    @Test fun proxySenderTag_extractsName() {
        assertEquals("bot", ChatMarkupRegex.proxySenderTag.find("<proxy_sender name=\"bot\" />")!!.groupValues[1])
    }
}
