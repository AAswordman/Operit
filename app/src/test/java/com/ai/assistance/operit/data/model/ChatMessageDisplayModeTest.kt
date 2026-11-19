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

    @Test fun `steered assistant segments round trip by their stored name`() {
        assertEquals(ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE, ChatMessageDisplayMode.valueOf("ASSISTANT_INTERMEDIATE"))
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
