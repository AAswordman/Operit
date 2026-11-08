package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMarkupRegexOpeningTagTest {

    @Test fun extractOpeningTagName_readsStatusTag() {
        assertEquals("status", ChatMarkupRegex.extractOpeningTagName("<status type=\"ok\">"))
    }

    @Test fun extractOpeningTagName_readsTagAfterLeadingWhitespace() {
        assertEquals("meta", ChatMarkupRegex.extractOpeningTagName(" \n<meta provider=\"x\">"))
    }

    @Test fun extractOpeningTagName_returnsNullForBrokenTag() {
        assertNull(ChatMarkupRegex.extractOpeningTagName("<"))
    }
}
