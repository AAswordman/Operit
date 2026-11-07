package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingDisplayChatSelectorTest {

    // 默认关闭（followsTargetedSend = false）：保持文档约定，定向发送不切换 UI。

    @Test
    fun select_disabledByDefaultDoesNotSwitchFloatingWindow() {
        // #555 的可选修复默认关闭：提供 chatId 时只后台发送，悬浮窗留在当前对话。
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "chat-b",
                displayedChatId = "chat-a",
                followsTargetedSend = false
            )

        assertNull(result)
    }

    @Test
    fun select_disabledDoesNotSwitchEvenWhenNothingIsDisplayedYet() {
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "chat-b",
                displayedChatId = null,
                followsTargetedSend = false
            )

        assertNull(result)
    }

    // 开启设置后（followsTargetedSend = true）：悬浮窗跟随目标对话。

    @Test
    fun select_enabledFloatingRuntimeFollowsWorkflowTargetChatInsteadOfForegroundChat() {
        // #555: a workflow sends to chat B while the user is looking at chat A.
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "chat-b",
                displayedChatId = "chat-a",
                followsTargetedSend = true
            )

        assertEquals("chat-b", result)
    }

    @Test
    fun select_enabledFloatingRuntimeFollowsTargetWhenNothingIsDisplayedYet() {
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "chat-b",
                displayedChatId = null,
                followsTargetedSend = true
            )

        assertEquals("chat-b", result)
    }

    @Test
    fun select_enabledMainRuntimeNeverSwitchesAwayFromForegroundChat() {
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.MAIN,
                targetChatId = "chat-b",
                displayedChatId = "chat-a",
                followsTargetedSend = true
            )

        assertNull(result)
    }

    @Test
    fun select_enabledReturnsNullWhenTargetAlreadyDisplayed() {
        val result =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "chat-a",
                displayedChatId = "chat-a",
                followsTargetedSend = true
            )

        assertNull(result)
    }

    @Test
    fun select_enabledReturnsNullWhenNoTargetChatIsGiven() {
        val floating =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = null,
                displayedChatId = "chat-a",
                followsTargetedSend = true
            )
        val main =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.MAIN,
                targetChatId = null,
                displayedChatId = "chat-a",
                followsTargetedSend = true
            )

        assertNull(floating)
        assertNull(main)
    }

    @Test
    fun select_enabledReturnsNullWhenTargetChatIsBlank() {
        val blank =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "   ",
                displayedChatId = "chat-a",
                followsTargetedSend = true
            )
        val empty =
            FloatingDisplayChatSelector.select(
                runtimeSlot = ChatRuntimeSlot.FLOATING,
                targetChatId = "",
                displayedChatId = "chat-a",
                followsTargetedSend = true
            )

        assertNull(blank)
        assertNull(empty)
    }
}
