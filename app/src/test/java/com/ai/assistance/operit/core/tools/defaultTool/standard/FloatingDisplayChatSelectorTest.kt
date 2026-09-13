package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingDisplayChatSelectorTest {

    @Test
    fun select_floatingRuntimeFollowsWorkflowTargetChatInsteadOfForegroundChat() {
        // #555: a workflow sends to chat B while the user is looking at chat A.
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "chat-b",
                displayedChatId = "chat-a"
            )

        assertEquals("chat-b", result)
    }

    @Test
    fun select_floatingRuntimeFollowsTargetWhenNothingIsDisplayedYet() {
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "chat-b",
                displayedChatId = null
            )

        assertEquals("chat-b", result)
    }

    @Test
    fun select_mainRuntimeNeverSwitchesAwayFromForegroundChat() {
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.MAIN,
                targetChatId = "chat-b",
                displayedChatId = "chat-a"
            )

        assertNull(result)
    }

    @Test
    fun select_returnsNullWhenTargetAlreadyDisplayed() {
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "chat-a",
                displayedChatId = "chat-a"
            )

        assertNull(result)
    }

    @Test
    fun select_returnsNullWhenNoTargetChatIsGiven() {
        val floating =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = null,
                displayedChatId = "chat-a"
            )
        val main =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.MAIN,
                targetChatId = null,
                displayedChatId = "chat-a"
            )

        assertNull(floating)
        assertNull(main)
    }

    @Test
    fun select_returnsNullWhenTargetChatIsBlank() {
        val blank =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "   ",
                displayedChatId = "chat-a"
            )
        val empty =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "",
                displayedChatId = "chat-a"
            )

        assertNull(blank)
        assertNull(empty)
    }
}
