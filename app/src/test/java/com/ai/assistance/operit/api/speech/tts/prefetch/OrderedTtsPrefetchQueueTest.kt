package com.ai.assistance.operit.api.speech.tts.prefetch

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class OrderedTtsPrefetchQueueTest {
    @Test
    fun firstAudioUnlocksAllLaterRequestsWithoutWaitingForPlayback() = runBlocking {
        withTimeout(3_000) {
            val started = Channel<Int>(Channel.UNLIMITED)
            val played = Channel<Int>(Channel.UNLIMITED)
            val prepared = List(4) { CompletableDeferred<Unit>() }
            val firstPlaybackDone = CompletableDeferred<Boolean>()
            val released = mutableListOf<Int>()
            val queue = OrderedTtsPrefetchQueue<Int, Int>(
                prepare = { index -> started.send(index); prepared[index].await(); index },
                play = { index, _ ->
                    played.send(index)
                    if (index == 0) firstPlaybackDone.await() else true
                },
                release = { released += it },
                stopPlayback = { firstPlaybackDone.complete(false) },
                dispatcher = Dispatchers.Unconfined,
            )
            try {
                val reading = async(start = CoroutineStart.UNDISPATCHED) {
                    queue.speak(listOf(0, 1, 2, 3), interrupt = false)
                }
                assertEquals(0, started.receive())
                assertTrue(started.tryReceive().isFailure)
                prepared[0].complete(Unit)
                assertEquals(setOf(1, 2, 3), setOf(started.receive(), started.receive(), started.receive()))
                assertEquals(0, played.receive())
                assertFalse(reading.isCompleted)
                // 后句先返回也不能抢在第二句前播放。
                prepared[3].complete(Unit)
                prepared[2].complete(Unit)
                firstPlaybackDone.complete(true)
                assertTrue(played.tryReceive().isFailure)
                prepared[1].complete(Unit)
                assertTrue(reading.await())
                assertEquals(listOf(1, 2, 3), listOf(played.receive(), played.receive(), played.receive()))
                assertEquals(listOf(0, 1, 2, 3), released)
            } finally {
                queue.shutdown()
            }
        }
    }

    @Test
    fun newlyArrivedStreamingSentenceStartsDuringCurrentPlayback() = runBlocking {
        withTimeout(3_000) {
            val started = Channel<Int>(Channel.UNLIMITED)
            val played = Channel<Int>(Channel.UNLIMITED)
            val firstDone = CompletableDeferred<Boolean>()
            val queue = OrderedTtsPrefetchQueue<Int, Int>(
                prepare = { started.send(it); it },
                play = { index, _ -> played.send(index); if (index == 1) firstDone.await() else true },
                release = {},
                stopPlayback = { firstDone.complete(false) },
                dispatcher = Dispatchers.Unconfined,
            )
            try {
                val first = async(start = CoroutineStart.UNDISPATCHED) { queue.speak(listOf(1), false) }
                assertEquals(1, started.receive())
                assertEquals(1, played.receive())
                val second = async(start = CoroutineStart.UNDISPATCHED) { queue.speak(listOf(2), false) }
                assertEquals(2, started.receive())
                assertTrue(played.tryReceive().isFailure)
                firstDone.complete(true)
                assertTrue(first.await())
                assertTrue(second.await())
                assertEquals(2, played.receive())
            } finally {
                queue.shutdown()
            }
        }
    }

    @Test
    fun stopCancelsOutstandingPreparationAndNextReadingStillWorks() = runBlocking {
        withTimeout(3_000) {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val played = mutableListOf<Int>()
            val queue = OrderedTtsPrefetchQueue<Int, Int>(
                prepare = { index ->
                    if (index == 1) {
                        started.complete(Unit)
                        try { awaitCancellation() } finally { cancelled.complete(Unit) }
                    } else index
                },
                play = { index, _ -> played += index; true },
                release = {},
                stopPlayback = {},
                dispatcher = Dispatchers.Unconfined,
            )
            try {
                val oldReading = async(start = CoroutineStart.UNDISPATCHED) { queue.speak(listOf(1, 2), false) }
                started.await()
                queue.stop()
                cancelled.await()
                assertFalse(oldReading.await())
                assertTrue(queue.speak(listOf(3), true))
                assertEquals(listOf(3), played)
            } finally {
                queue.shutdown()
            }
        }
    }

    @Test
    fun failedSentenceStopsLaterAudioAndDoesNotKillConsumer() = runBlocking {
        withTimeout(3_000) {
            val played = mutableListOf<Int>()
            val queue = OrderedTtsPrefetchQueue<Int, Int>(
                prepare = { if (it == 2) throw IOException("合成失败") else it },
                play = { index, _ -> played += index; true },
                release = {},
                stopPlayback = {},
                dispatcher = Dispatchers.Unconfined,
            )
            try {
                try {
                    queue.speak(listOf(1, 2, 3), false)
                    fail("应保留合成失败结果")
                } catch (expected: IOException) {
                    assertEquals("合成失败", expected.message)
                }
                assertEquals(listOf(1), played)
                assertTrue(queue.speak(listOf(4), true))
                assertEquals(listOf(1, 4), played)
            } finally {
                queue.shutdown()
            }
        }
    }

    @Test
    fun cancellingCallerDiscardsItsAudioAndKeepsQueueUsable() = runBlocking {
        withTimeout(3_000) {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val queue = OrderedTtsPrefetchQueue<Int, Int>(
                prepare = {
                    if (it == 1) {
                        started.complete(Unit)
                        try { awaitCancellation() } finally { cancelled.complete(Unit) }
                    } else it
                },
                play = { _, _ -> true },
                release = {},
                stopPlayback = {},
                dispatcher = Dispatchers.Unconfined,
            )
            try {
                val oldReading = async(start = CoroutineStart.UNDISPATCHED) { queue.speak(listOf(1, 2), false) }
                started.await()
                oldReading.cancel()
                oldReading.join()
                cancelled.await()
                assertTrue(queue.speak(listOf(3), true))
            } finally {
                queue.shutdown()
            }
        }
    }
}