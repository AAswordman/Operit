package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexPatternTest {

    @Test fun namePattern_extractsToolName() {
        assertEquals("run", ChatMarkupRegex.namePattern.find("<tool name=\"run\">")!!.groupValues[1])
    }

    @Test fun toolParamPattern_extractsParamNameAndBody() {
        val match = ChatMarkupRegex.toolParamPattern.find("<param name=\"path\">/tmp</param>")!!
        assertEquals("path", match.groupValues[1])
        assertEquals("/tmp", match.groupValues[2])
    }

    @Test fun anyXmlTag_matchesSimpleTag() {
        assertTrue(ChatMarkupRegex.anyXmlTag.containsMatchIn("<hello>"))
    }

    @Test fun pruneToolResultContentPattern_matchesStatusPayload() {
        assertTrue(ChatMarkupRegex.pruneToolResultContentPattern.containsMatchIn("<tool_result status=\"ok\">body</tool_result>"))
    }
}
