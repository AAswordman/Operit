package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexSearchTest {

    @Test fun searchTag_isCaseInsensitive() {
        assertTrue(ChatMarkupRegex.searchTag.containsMatchIn("<SEARCH>q</SEARCH>"))
    }

    @Test fun thinkTag_isCaseInsensitive() {
        assertTrue(ChatMarkupRegex.thinkTag.containsMatchIn("<THINKING>x</THINKING>"))
    }

    @Test fun metaTag_doesNotMatchPlainText() {
        assertFalse(ChatMarkupRegex.metaTag.containsMatchIn("plain text"))
    }
}
