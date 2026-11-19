package com.ai.assistance.operit.services.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnDispatcherTest {
    @Test fun completedFastTurnRetainsItsStreamForALateDispatcher() = runTest {
        val registration = RegisteredChatTurn()
        val stream = com.ai.assistance.operit.util.stream.MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
        registration.started.complete("chat" to stream)
        stream.tryEmit("done")
        stream.close()
        registration.complete(ChatTurnTerminalSignal.Completed("turn", "chat", 123L))
        val (chatId, retained) = registration.started.await()
        org.junit.Assert.assertEquals("chat", chatId)
        org.junit.Assert.assertEquals(listOf("done"), retained.replayCache)
        assertTrue(registration.terminal.await() is ChatTurnTerminalSignal.Completed)
    }

    @Test fun failureBeforeStreamCreationEndsBothWaiters() = runTest {
        val registration = RegisteredChatTurn()
        registration.complete(ChatTurnTerminalSignal.Failed("turn", "chat", "prepare failed"))
        assertTrue(registration.terminal.await() is ChatTurnTerminalSignal.Failed)
        val failure = runCatching { registration.started.await() }.exceptionOrNull()
        org.junit.Assert.assertEquals("prepare failed", failure?.message)
    }

    @Test
    fun unlimitedTerminalWaitCanExceedFormerThreeMinuteLimit() = runTest {
        val terminalSignal = CompletableDeferred<ChatTurnTerminalSignal>()
        launch {
            delay(180_001L)
            terminalSignal.complete(
                ChatTurnTerminalSignal.Cancelled(
                    turnId = "turn",
                    chatId = "chat",
                )
            )
        }

        val result =
            awaitChatTurnTerminalSignal(
                terminalSignal = terminalSignal,
                responseTimeoutMs = null,
            )

        assertTrue(result is ChatTurnTerminalSignal.Cancelled)
    }
}
