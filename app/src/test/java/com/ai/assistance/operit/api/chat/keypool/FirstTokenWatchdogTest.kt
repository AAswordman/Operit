package com.ai.assistance.operit.api.chat.keypool

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class FirstTokenWatchdogTest {
    @Test
    fun cancellationOccursAtTenMinutesNotFive() = runTest {
        val call = mock(Call::class.java)
        val watchdog = FirstTokenWatchdog(this)
        watchdog.start(call)
        advanceTimeBy(599_999L)
        runCurrent()
        verify(call, never()).cancel()
        assertFalse(watchdog.timedOut)
        advanceTimeBy(1L)
        runCurrent()
        verify(call).cancel()
        assertTrue(watchdog.timedOut)
    }

    @Test
    fun recognizedOutputDisarmsTheDeadline() = runTest {
        val call = mock(Call::class.java)
        val watchdog = FirstTokenWatchdog(this)
        watchdog.start(call)
        advanceTimeBy(1000L)
        watchdog.markFirstToken()
        advanceTimeBy(600_000L)
        runCurrent()
        verify(call, never()).cancel()
        assertFalse(watchdog.timedOut)
    }

    @Test
    fun aNewAttemptDoesNotLeaveTheOldCallDeadlineArmed() = runTest {
        val first = mock(Call::class.java)
        val second = mock(Call::class.java)
        val watchdog = FirstTokenWatchdog(this)
        watchdog.start(first)
        advanceTimeBy(300_000L)
        watchdog.start(second)
        advanceTimeBy(300_000L)
        runCurrent()
        verify(first, never()).cancel()
        verify(second, never()).cancel()
        watchdog.cancel()
    }
}
