package com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.history

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListMotionStateTest {
    @Test
    fun duplicateConfirmationDoesNotDeleteTwiceWhileResultIsPending() = runTest {
        val state = ChatListMotionState()
        var calls = 0
        var completion: ((Boolean) -> Unit)? = null
        val operation: (String, (Boolean) -> Unit) -> Unit = { _, done -> calls++; completion = done }
        state.delete("chat", backgroundScope, false, operation)
        runCurrent()
        state.delete("chat", backgroundScope, false, operation)
        runCurrent()
        assertEquals(1, calls)
        assertTrue("等待存储结果时仍保留删除标记", "chat" in state.deletions.value)
        requireNotNull(completion)(false)
        assertFalse(state.deletions.value.containsKey("chat"))
    }

    @Test
    fun failureRestoresTheRowAndAllowsAnotherAttempt() = runTest {
        val state = ChatListMotionState()
        var calls = 0
        repeat(2) {
            state.delete("chat", backgroundScope, false) { _, done -> calls++; done(false) }
            runCurrent()
            assertTrue(state.deletions.value.isEmpty())
        }
        assertEquals(2, calls)
    }

    @Test
    fun completingOneDeletionKeepsAnotherPendingRow() = runTest {
        val state = ChatListMotionState()
        val completions = mutableMapOf<String, (Boolean) -> Unit>()
        for (id in listOf("first", "second")) {
            state.delete(id, backgroundScope, false) { chatId, done -> completions[chatId] = done }
        }
        runCurrent()
        requireNotNull(completions["first"])(true)
        assertEquals(setOf("second"), state.deletions.value.keys)
        requireNotNull(completions["second"])(true)
        assertTrue(state.deletions.value.isEmpty())
    }

    @Test
    fun finishingAnOldIntroNeverConsumesANewerChatRequest() {
        val state = ChatListMotionState()
        state.onChatCreated("old")
        state.onChatCreated("new")
        state.consumeIntro("old")
        assertEquals("new", state.newChatId.value)
        state.consumeIntro("new")
        assertNull(state.newChatId.value)
    }
}