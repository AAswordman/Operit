package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexToolNameTest {

    @Test fun toolTag_acceptsUppercaseVariant() {
        assertTrue(ChatMarkupRegex.isToolTagName("TOOL_EXEC"))
    }

    @Test fun toolTag_rejectsNull() {
        assertFalse(ChatMarkupRegex.isToolTagName(null))
    }

    @Test fun toolResultTag_acceptsUppercaseVariant() {
        assertTrue(ChatMarkupRegex.isToolResultTagName("TOOL_RESULT_EXEC"))
    }

    @Test fun toolResultTag_rejectsNull() {
        assertFalse(ChatMarkupRegex.isToolResultTagName(null))
    }

    @Test fun normalizeNullTagName_returnsNull() {
        assertEquals(null, ChatMarkupRegex.normalizeToolLikeTagName(null))
    }
}
