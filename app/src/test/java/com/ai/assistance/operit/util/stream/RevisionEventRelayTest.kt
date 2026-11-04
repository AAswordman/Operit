package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.assertPropagatedException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RevisionEventRelayTest {
    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    @Test
    fun queuedFinalRollbackIsForwardedBeforeCompletionOnOneDispatcher() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val relayed = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val savepoint = TextStreamEvent(TextStreamEventType.SAVEPOINT, "attempt", 4)
        val rollback = TextStreamEvent(TextStreamEventType.ROLLBACK, "attempt", 4, 9)
        val expectedEvents = listOf(savepoint, rollback)
        val chunks = mutableListOf<String>()
        val rollbacks = mutableListOf<String>()
        var completedContent: String? = null
        val source =
            stream {
                assertEquals(1, events.subscriptionCount)
                emit("kept")
                events.emit(savepoint)
                emit("discarded")
                events.emit(rollback)
                // runTest uses one StandardTestDispatcher. None of these UNLIMITED sends yields,
                // so both events are queued for the subscribed relay when the text stream ends.
                assertEquals(emptyList<TextStreamEvent>(), relayed.replayCache)
            }.withEventChannel(events)

        source.collectWithRevisionRelay(
            eventChannel = relayed,
            roundStartLength = 0,
            onRollback = { rollbacks += it },
            onCompletion = { content ->
                assertEquals(expectedEvents, relayed.replayCache)
                assertEquals(0, events.subscriptionCount)
                completedContent = content
            },
            onChunk = { chunks += it },
        )

        assertEquals(listOf("kept", "discarded"), chunks)
        assertEquals(listOf("kept"), rollbacks)
        assertEquals("kept", completedContent)
        assertEquals(expectedEvents, relayed.replayCache)
        assertTrue("completion must not require closing the provider channel", events.tryEmit(savepoint))
    }

    @Test
    fun livePrefixAndQueuedLaterRoundEventsAreShiftedOnceInOrder() = runTest {
        val relayed = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val firstEvents = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val secondEvents = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val firstSavepoint = TextStreamEvent(TextStreamEventType.SAVEPOINT, "first", 0)
        val firstRollback = TextStreamEvent(TextStreamEventType.ROLLBACK, "first", 0, 3)
        val secondSavepoint = TextStreamEvent(TextStreamEventType.SAVEPOINT, "second", 0)
        val secondRollback = TextStreamEvent(TextStreamEventType.ROLLBACK, "second", 0, 4)
        val repeatedRollback = TextStreamEvent(TextStreamEventType.ROLLBACK, "second", 4, 5)
        val rawText = StringBuilder()
        val completedRounds = mutableListOf<String>()
        val firstRound =
            stream {
                firstEvents.emit(firstSavepoint)
                emit("bad")
                firstEvents.emit(firstRollback)
                emit("kept")
            }.withEventChannel(firstEvents)

        firstRound.collectWithRevisionRelay(
            eventChannel = relayed,
            roundStartLength = rawText.length,
            onRollback = {},
            onCompletion = { completedRounds += it },
            onChunk = { rawText.append(it) },
        )
        val roundStartLength = rawText.length
        assertEquals(7, roundStartLength)
        val secondRound =
            stream {
                secondEvents.emit(secondSavepoint)
                // Let the live relay process a prefix. Completion must only publish its tail.
                yield()
                assertEquals(
                    listOf(firstSavepoint, firstRollback, secondSavepoint.shiftedBy(7)),
                    relayed.replayCache,
                )
                emit("lost")
                secondEvents.emit(secondRollback)
                emit("again")
                secondEvents.emit(repeatedRollback)
                emit("answer")
            }.withEventChannel(secondEvents)

        secondRound.collectWithRevisionRelay(
            eventChannel = relayed,
            roundStartLength = roundStartLength,
            onRollback = {},
            onCompletion = { completedRounds += it },
            onChunk = { rawText.append(it) },
        )

        assertEquals(listOf("kept", "answer"), completedRounds)
        assertEquals(
            listOf(
                firstSavepoint,
                firstRollback,
                secondSavepoint.copy(contentLength = 7),
                secondRollback.copy(contentLength = 7),
                repeatedRollback.copy(contentLength = 11),
            ),
            relayed.replayCache,
        )
        assertEquals("keptanswer", applyRevisionEvents(rawText.toString(), relayed.replayCache))
        assertEquals(0, firstEvents.subscriptionCount)
        assertEquals(0, secondEvents.subscriptionCount)
    }

    @Test
    fun textFailureDrainsQueuedEventsAndPropagatesOriginalFailure() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val relayed = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val savepoint = TextStreamEvent(TextStreamEventType.SAVEPOINT, "attempt", 0)
        val rollback = TextStreamEvent(TextStreamEventType.ROLLBACK, "attempt", 0, 7)
        val expected = IOException("provider interrupted")
        var completedContent: String? = null
        val source =
            stream {
                events.emit(savepoint)
                emit("partial")
                events.emit(rollback)
                throw expected
            }.withEventChannel(events)

        try {
            source.collectWithRevisionRelay(
                eventChannel = relayed,
                roundStartLength = 12,
                onRollback = {},
                onCompletion = { completedContent = it },
                onChunk = {},
            )
            fail("the original provider failure must propagate")
        } catch (actual: IOException) {
            assertPropagatedException(expected, actual)
        }

        assertEquals(listOf(savepoint.shiftedBy(12), rollback.shiftedBy(12)), relayed.replayCache)
        assertEquals("", completedContent)
        assertEquals(0, events.subscriptionCount)
    }

    @Test
    fun cancellationDrainsProviderFinalizerEventsAndRemovesSubscription() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val relayed = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val savepoint = TextStreamEvent(TextStreamEventType.SAVEPOINT, "attempt", 0)
        val rollback = TextStreamEvent(TextStreamEventType.ROLLBACK, "attempt", 0, 7)
        val producerWaiting = CompletableDeferred<Unit>()
        val expected = CancellationException("caller cancelled")
        var completedContent: String? = null
        var completionCause: Throwable? = null
        val source =
            stream {
                try {
                    events.emit(savepoint)
                    emit("partial")
                    producerWaiting.complete(Unit)
                    awaitCancellation()
                } finally {
                    assertTrue(events.tryEmit(rollback))
                }
            }.withEventChannel(events)
        val collection = launch {
            source.collectWithRevisionRelay(
                eventChannel = relayed,
                roundStartLength = 5,
                onRollback = {},
                onCompletion = { content ->
                    assertEquals(
                        listOf(savepoint.shiftedBy(5), rollback.shiftedBy(5)),
                        relayed.replayCache,
                    )
                    completedContent = content
                },
                onChunk = {},
            )
        }
        collection.invokeOnCompletion { completionCause = it }
        producerWaiting.await()

        collection.cancel(expected)
        collection.join()

        assertTrue(collection.isCancelled)
        assertSame(expected, completionCause)
        assertEquals("", completedContent)
        assertEquals(0, events.subscriptionCount)
    }

    @Test
    fun liveRelayFailurePropagatesWithoutReemittingItsDeliveredEvents() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val relayed = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val savepoint = TextStreamEvent(TextStreamEventType.SAVEPOINT, "attempt", 0)
        val rollback = TextStreamEvent(TextStreamEventType.ROLLBACK, "attempt", 0, 7)
        val expected = IOException("rollback callback failed")
        var completedContent: String? = null
        val source =
            stream {
                events.emit(savepoint)
                emit("partial")
                events.emit(rollback)
                yield()
                fail("the failing live relay must cancel text collection")
            }.withEventChannel(events)

        try {
            source.collectWithRevisionRelay(
                eventChannel = relayed,
                roundStartLength = 0,
                onRollback = { throw expected },
                onCompletion = { completedContent = it },
                onChunk = {},
            )
            fail("the relay failure must propagate")
        } catch (actual: IOException) {
            assertPropagatedException(expected, actual)
        }

        assertEquals(listOf(savepoint, rollback), relayed.replayCache)
        assertEquals("", completedContent)
        assertEquals(0, events.subscriptionCount)
    }

    @Test
    fun eventChannelCancellationPropagatesAndStillFinalizesRound() = runTest {
        val events = MutableSharedStreamImpl<TextStreamEvent>(replay = Int.MAX_VALUE)
        val relayed = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val expected = CancellationException("provider event channel cancelled")
        var completedContent: String? = null
        val source =
            stream {
                emit("partial")
                events.close(expected)
                // An event-channel failure must stop a text producer that has not completed.
                awaitCancellation()
            }.withEventChannel(events)

        try {
            source.collectWithRevisionRelay(
                eventChannel = relayed,
                roundStartLength = 0,
                onRollback = {},
                onCompletion = { completedContent = it },
                onChunk = {},
            )
            fail("event channel cancellation must propagate")
        } catch (actual: CancellationException) {
            assertPropagatedException(expected, actual)
        }

        assertEquals("partial", completedContent)
        assertEquals(0, events.subscriptionCount)
    }

    @Test
    fun plainTextStreamForwardsChunksAndFinalizesContent() = runTest {
        val relayed = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val chunks = mutableListOf<String>()
        var completedContent: String? = null

        streamOf("alpha", "beta").collectWithRevisionRelay(
            eventChannel = relayed,
            roundStartLength = 20,
            onRollback = { fail("a plain stream has no revision events") },
            onCompletion = { completedContent = it },
            onChunk = { chunks += it },
        )

        assertEquals(listOf("alpha", "beta"), chunks)
        assertEquals("alphabeta", completedContent)
        assertEquals(emptyList<TextStreamEvent>(), relayed.replayCache)
    }
}
