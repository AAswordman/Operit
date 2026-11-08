package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexContainsTest {

    @Test fun containsAnyToolLikeTag_detectsToolTag() {
        assertTrue(ChatMarkupRegex.containsAnyToolLikeTag("<tool name=\"run\">x</tool>"))
    }

    @Test fun containsAnyToolLikeTag_detectsToolResultTag() {
        assertTrue(ChatMarkupRegex.containsAnyToolLikeTag("<tool_result name=\"run\">x</tool_result>"))
    }

    @Test fun containsAnyToolLikeTag_returnsFalseForPlainText() {
        assertFalse(ChatMarkupRegex.containsAnyToolLikeTag("plain text"))
    }
}
