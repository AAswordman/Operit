package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexRandomTagTest {

    @Test fun generatedToolTag_hasExpectedPrefixLength() {
        val value = ChatMarkupRegex.generateRandomToolTagName()
        assertTrue(value.startsWith("tool_"))
        assertEquals(9, value.length)
    }

    @Test fun generatedToolResultTag_hasExpectedPrefixLength() {
        val value = ChatMarkupRegex.generateRandomToolResultTagName()
        assertTrue(value.startsWith("tool_result_"))
        assertEquals(16, value.length)
    }
}
