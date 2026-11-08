package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexToolResultTest {

    @Test fun toolResultTag_matchesSuffixVariant() {
        assertTrue(ChatMarkupRegex.toolResultTag.containsMatchIn("<tool_result_exec>done</tool_result_exec>"))
    }

    @Test fun toolResultSelfClosingTag_matchesSuffixVariant() {
        assertTrue(ChatMarkupRegex.toolResultSelfClosingTag.containsMatchIn("<tool_result_exec />"))
    }

    @Test fun toolResultAnyPattern_extractsBody() {
        val match = ChatMarkupRegex.toolResultAnyPattern.find("<tool_result>done</tool_result>")
        assertNotNull(match)
        assertEquals("done", match!!.groupValues[2])
    }
}
