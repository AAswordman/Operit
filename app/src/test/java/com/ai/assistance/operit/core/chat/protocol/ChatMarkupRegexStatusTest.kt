package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatMarkupRegexStatusTest {

    @Test fun xmlStatusPattern_extractsUuidWhenPresent() {
        val match = ChatMarkupRegex.xmlStatusPattern.find("<status type=\"ok\" uuid=\"u1\">body</status>")
        assertNotNull(match)
        assertEquals("u1", match!!.groupValues[2])
    }

    @Test fun xmlStatusPattern_extractsTitleWhenPresent() {
        val match = ChatMarkupRegex.xmlStatusPattern.find("<status type=\"ok\" title=\"hello\">body</status>")
        assertNotNull(match)
        assertEquals("hello", match!!.groupValues[3])
    }

    @Test fun xmlStatusPattern_extractsSubtitleWhenPresent() {
        val match = ChatMarkupRegex.xmlStatusPattern.find("<status type=\"ok\" subtitle=\"world\">body</status>")
        assertNotNull(match)
        assertEquals("world", match!!.groupValues[4])
    }
}
