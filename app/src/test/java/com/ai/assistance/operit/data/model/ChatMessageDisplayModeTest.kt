package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageDisplayModeTest {

    @Test fun `NORMAL is a display mode`() {
        assertEquals(ChatMessageDisplayMode.NORMAL, ChatMessageDisplayMode.valueOf("NORMAL"))
    }

    @Test fun `HIDDEN_PLACEHOLDER is a display mode`() {
        assertEquals(ChatMessageDisplayMode.HIDDEN_PLACEHOLDER, ChatMessageDisplayMode.valueOf("HIDDEN_PLACEHOLDER"))
    }

    @Test fun `enum has exactly two values`() {
        assertEquals(2, ChatMessageDisplayMode.values().size)
    }

    @Test fun `valueOf is case sensitive`() {
        assertEquals(ChatMessageDisplayMode.NORMAL, ChatMessageDisplayMode.valueOf("NORMAL"))
        assertEquals(ChatMessageDisplayMode.HIDDEN_PLACEHOLDER, ChatMessageDisplayMode.valueOf("HIDDEN_PLACEHOLDER"))
    }

    @Test fun `name returns correct string`() {
        assertEquals("NORMAL", ChatMessageDisplayMode.NORMAL.name)
        assertEquals("HIDDEN_PLACEHOLDER", ChatMessageDisplayMode.HIDDEN_PLACEHOLDER.name)
    }
}
