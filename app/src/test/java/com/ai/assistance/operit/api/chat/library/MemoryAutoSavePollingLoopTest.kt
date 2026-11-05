package com.ai.assistance.operit.api.chat.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryAutoSavePollingLoopTest {

    @Test
    fun tickFailureDoesNotStopLaterTicks() = runTest {        var tickCount = 0
        val errors = mutableListOf<Throwable>()
        val loop =
            MemoryAutoSavePollingLoop(
                tickIntervalMs = 1_000L,
                onTick = {
                    tickCount += 1
                    if (tickCount == 1) {
                        throw IllegalStateException("transient failure")
                    }
                },
                onError = { errors.add(it) }
            )

        val job = launch { loop.run() }
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, tickCount)
        assertEquals(1, errors.size)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, tickCount)

        job.cancel()
    }

    @Test
    fun cancellingTheJobStopsTheLoopWithoutReportingAnError() = runTest {
        var tickCount = 0
        val errors = mutableListOf<Throwable>()
        val loop =
            MemoryAutoSavePollingLoop(
                tickIntervalMs = 1_000L,
                onTick = { tickCount += 1 },
                onError = { errors.add(it) }
            )

        val job = launch { loop.run() }
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, tickCount)

        job.cancel()
        advanceTimeBy(10_000L)
        runCurrent()

        assertEquals(1, tickCount)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun startupFailureDoesNotPreventTicks() = runTest {
        var tickCount = 0
        val errors = mutableListOf<Throwable>()
        val loop =
            MemoryAutoSavePollingLoop(
                tickIntervalMs = 1_000L,
                onStart = { throw IllegalStateException("recovery failed") },
                onTick = { tickCount += 1 },
                onError = { errors.add(it) }
            )

        val job = launch { loop.run() }
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(1, tickCount)
        assertEquals(1, errors.size)

        job.cancel()
    }

    @Test
    fun cancellationFromATickIsRethrownAndEndsTheLoop() = runTest {
        var tickCount = 0
        val loop =
            MemoryAutoSavePollingLoop(
                tickIntervalMs = 1_000L,
                onTick = {
                    tickCount += 1
                    throw CancellationException("stop requested")
                },
                onError = { throw AssertionError("cancellation must not be reported as failure", it) }
            )

        val job = launch { loop.run() }
        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(job.isCancelled)
        assertEquals(1, tickCount)
    }
}
