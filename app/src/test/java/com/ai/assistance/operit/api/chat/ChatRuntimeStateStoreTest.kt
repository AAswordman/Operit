package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.core.tools.CurrentChatRuntimeStateResultData
import com.ai.assistance.operit.data.model.InputProcessingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRuntimeStateStoreTest {
    @Test
    fun mapsConnectingToRequesting() {
        val chatId = uniqueChatId("requesting")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Connecting("connecting")
        )

        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("requesting", snapshot.phase.wireName)
        assertTrue(snapshot.phase.isActive)
    }

    @Test
    fun mapsSummarizingToSummarizing() {
        val chatId = uniqueChatId("summarizing")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Summarizing("summarizing")
        )

        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("summarizing", snapshot.phase.wireName)
        assertTrue(snapshot.phase.isActive)
    }

    @Test
    fun preservesCancelledUntilTheNextNonTerminalState() {
        val chatId = uniqueChatId("cancelled")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Processing("processing")
        )
        ChatRuntimeStateStore.markCancelled(ChatRuntimeSlot.MAIN, chatId)
        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Idle
        )

        val cancelled = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("cancelled", cancelled.phase.wireName)
        assertFalse(cancelled.phase.isActive)
        assertNull(cancelled.userState)
        val cancelledResult = CurrentChatRuntimeStateResultData(
            chatId = chatId,
            aiBehavior = cancelled.phase.wireName
        )
        assertFalse(cancelledResult.isIdle)
        assertFalse(cancelledResult.isActive)
        assertFalse(ChatRuntimeStateStore.globalSnapshot.value.activeChatIds.contains(chatId))
        assertTrue(
            ChatRuntimeStateStore.replayEvents().any { event ->
                event.session?.chatId == chatId && event.session.phase.wireName == "cancelled"
            }
        )

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Processing("next request")
        )

        val nextRequest = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("thinking", nextRequest.phase.wireName)
        assertTrue(nextRequest.phase.isActive)
    }

    private fun uniqueChatId(prefix: String): String {
        return "chat-runtime-state-test-$prefix-${System.nanoTime()}"
    }
}
