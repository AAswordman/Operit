package com.ai.assistance.operit.api.speech.tts.prefetch

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TtsSubmissionOrderTest {
    @Test
    fun nextSubmissionWaitsForQueueRegistrationRatherThanPlayback() = runBlocking {
        val order = TtsSubmissionOrder()
        val first = order.reserve(interrupt = true)
        val second = order.reserve(interrupt = false)
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) { second.awaitTurn() }
        assertFalse(awaiting.isCompleted)
        assertTrue(first.awaitTurn())
        first.markSubmitted()
        assertTrue(awaiting.await())
        second.markSubmitted()
    }

    @Test
    fun stopRejectsOldTurnsUntilNewInterruptingReading() = runBlocking {
        val order = TtsSubmissionOrder()
        val old = order.reserve(interrupt = true)
        order.stop()
        assertFalse(old.awaitTurn())
        assertFalse(order.reserve(interrupt = false).awaitTurn())
        val current = order.reserve(interrupt = true)
        assertTrue(current.awaitTurn())
        old.markSubmitted()
        assertFalse(old.failIfCurrent())
        assertTrue(current.awaitTurn())
        current.markSubmitted()
        assertTrue(current.failIfCurrent())
        assertFalse(order.reserve(interrupt = false).awaitTurn())
    }
}