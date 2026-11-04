package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.WaifuSegmentUpdate.Append
import com.ai.assistance.operit.util.WaifuSegmentUpdate.Retain
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType.ROLLBACK
import com.ai.assistance.operit.util.stream.TextStreamEventType.SAVEPOINT
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.streamOf
import com.ai.assistance.operit.util.stream.withEventChannel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaifuStreamUpdatesTest {
    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidLog() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    // The production revision/typing pipeline is exercised directly. A separator stands in for
    // native Markdown parsing so these regressions run on JVM hosts without libstreamnative.
    private fun TestScope.updates(source: Stream<String>, charDelayMs: Int = 100) =
        streamWaifuUpdates(
            sourceStream = source,
            splitSnapshot = { content, final ->
                val pieces = content.split('|')
                (if (final) pieces else pieces.dropLast(1)).filter { it.isNotEmpty() }
            },
            typingDelayMs = { length, first ->
                WaifuMessageProcessor.calculateTypingDelayMs(length, charDelayMs, first)
            },
            nowMillis = { testScheduler.currentTime },
        )

    @Test
    fun rollbackRetractsAnAlreadyDisplayedSentence() = runTest {
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val retry = CompletableDeferred<Unit>()
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", 0))
            emit("old|")
            retry.await()
            revisions.emit(TextStreamEvent(ROLLBACK, "attempt", 0, 4))
            emit("new|")
        }.withEventChannel(revisions)
        val job = launch { updates(source).collect { received += it } }
        runCurrent()
        assertEquals(listOf(Append("old")), received)

        retry.complete(Unit)
        advanceUntilIdle()
        job.join()

        assertEquals(listOf(Append("old"), Retain(0), Append("new")), received)
        assertEquals(0, revisions.subscriptionCount)
    }

    @Test
    fun retryPreservesPrefixIdentityDeletesOldBubbleAndClearsPendingTyping() = runTest {
        data class Bubble(val id: Int, val text: String)
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val retry = CompletableDeferred<Unit>()
        val messages = mutableListOf<Bubble>()
        val persisted = linkedMapOf(99 to Bubble(99, "earlier turn"))
        val removedIds = mutableListOf<Int>()
        val spoken = mutableListOf<Pair<String, Boolean>>()
        var nextId = 0
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { text -> Bubble(++nextId, text) },
            appendMessage = { message, interrupt ->
                persisted[message.id] = message
                spoken += message.text to interrupt
            },
            removeMessages = { removed ->
                removed.forEach { message ->
                    removedIds += message.id
                    persisted.remove(message.id)
                }
            },
        )
        val source = stream {
            emit("keep|")
            revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", 5))
            emit("old|stale|")
            retry.await()
            revisions.emit(TextStreamEvent(ROLLBACK, "attempt", 5, 10))
            emit("new|tail|")
        }.withEventChannel(revisions)
        val job = launch { updates(source).collect { store.apply(it) } }
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("keep", "old"), messages.map { it.text })
        val prefix = messages.first()

        retry.complete(Unit)
        advanceUntilIdle()
        job.join()

        assertEquals(listOf("keep", "new", "tail"), messages.map { it.text })
        assertSame(prefix, messages.first())
        assertEquals(listOf(2), removedIds)
        assertEquals(listOf(99, 1, 3, 4), persisted.keys.toList())
        assertEquals(
            listOf("keep" to true, "old" to false, "new" to true, "tail" to false),
            spoken,
        )
        assertEquals(0, revisions.subscriptionCount)
    }

    @Test
    fun finalHistoryWinsWhenAllEventsAreStillQueued() = runTest {
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", 0))
            emit("discarded|")
            revisions.emit(TextStreamEvent(ROLLBACK, "attempt", 0, 10))
            emit("answer|")
            // No suspension: event subscribers have not had a turn on this dispatcher.
        }.withEventChannel(revisions)

        updates(source).collect { received += it }

        assertEquals(listOf(Append("answer")), received)
        assertEquals(0, revisions.subscriptionCount)
    }

    @Test
    fun rollbackBeforeItsRawChunkDoesNotDisplayTheDiscardedChunk() = runTest {
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", 0))
            revisions.emit(TextStreamEvent(ROLLBACK, "attempt", 0, 5))
            yield()
            emit("lost|")
            emit("kept|")
        }.withEventChannel(revisions)

        updates(source).collect { received += it }

        assertEquals(listOf(Append("kept")), received)
    }

    @Test
    fun repeatedRetriesRetractEachFailedAttempt() = runTest {
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val firstRetry = CompletableDeferred<Unit>()
        val secondRetry = CompletableDeferred<Unit>()
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            revisions.emit(TextStreamEvent(SAVEPOINT, "first", 0))
            emit("first|")
            firstRetry.await()
            revisions.emit(TextStreamEvent(ROLLBACK, "first", 0, 6))
            revisions.emit(TextStreamEvent(SAVEPOINT, "second", 6))
            emit("second|")
            secondRetry.await()
            revisions.emit(TextStreamEvent(ROLLBACK, "second", 6, 7))
            emit("final|")
        }.withEventChannel(revisions)
        val job = launch { updates(source).collect { received += it } }
        runCurrent()
        firstRetry.complete(Unit)
        runCurrent()
        assertEquals(listOf(Append("first"), Retain(0), Append("second")), received)

        secondRetry.complete(Unit)
        advanceUntilIdle()
        job.join()

        assertEquals(
            listOf(Append("first"), Retain(0), Append("second"), Retain(0), Append("final")),
            received,
        )
    }

    @Test
    fun addingLaterSentencesDoesNotRestartTheCurrentTypingDelay() = runTest {
        val moreText = CompletableDeferred<Unit>()
        val received = mutableListOf<Pair<Long, WaifuSegmentUpdate>>()
        val source = stream {
            emit("first|second|")
            moreText.await()
            emit("third|")
        }
        val job = launch { updates(source).collect { received += testScheduler.currentTime to it } }
        runCurrent()
        advanceTimeBy(300)
        moreText.complete(Unit)
        runCurrent()
        advanceTimeBy(299)
        assertEquals(listOf(0L to Append("first")), received)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(600L to Append("second"), received.last())
        advanceUntilIdle()
        job.join()
        assertEquals(
            listOf(0L to Append("first"), 600L to Append("second"), 1100L to Append("third")),
            received,
        )
    }

    @Test
    fun rollbackAtTypingDeadlinePreventsSendingTheAbandonedSentence() = runTest {
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            emit("keep|")
            revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", 5))
            emit("old|")
            delay(300)
            revisions.emit(TextStreamEvent(ROLLBACK, "attempt", 5, 4))
            // The rollback must cancel the due sentence even before replacement text arrives.
            delay(500)
            emit("new|")
        }.withEventChannel(revisions)

        updates(source).collect { received += it }

        assertEquals(listOf(Append("keep"), Append("new")), received)
        assertFalse(received.contains(Append("old")))
    }

    @Test
    fun cancellationRetractsFinalizerRollbackAndDoesNotDrainTypingQueue() = runTest {
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val received = mutableListOf<WaifuSegmentUpdate>()
        var producerFinished = false
        val source = stream {
            try {
                revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", 0))
                emit("old|pending|")
                awaitCancellation()
            } finally {
                producerFinished = true
                assertTrue(revisions.tryEmit(TextStreamEvent(ROLLBACK, "attempt", 0, 12)))
            }
        }.withEventChannel(revisions)
        val job = launch { updates(source).collect { received += it } }
        runCurrent()
        assertEquals(listOf(Append("old")), received)

        job.cancelAndJoin()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue(producerFinished)
        assertEquals(listOf(Append("old"), Retain(0)), received)
        assertEquals(0, revisions.subscriptionCount)
    }

    @Test
    fun cancellationDuringBubblePersistenceRetractsThePartiallySavedMessage() = runTest {
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val messages = mutableListOf<String>()
        val persisted = mutableListOf<String>()
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { text -> text },
            appendMessage = { message, _ ->
                persisted += message
                awaitCancellation()
            },
            removeMessages = { removed -> persisted.removeAll(removed.toSet()) },
        )
        val source = stream {
            try {
                revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", 0))
                emit("old|pending|")
                awaitCancellation()
            } finally {
                revisions.tryEmit(TextStreamEvent(ROLLBACK, "attempt", 0, 12))
            }
        }.withEventChannel(revisions)
        val job = launch { updates(source).collect { store.apply(it) } }
        runCurrent()
        assertEquals(listOf("old"), messages)
        assertEquals(listOf("old"), persisted)

        job.cancelAndJoin()

        assertTrue(messages.isEmpty())
        assertTrue(persisted.isEmpty())
        assertEquals(0, revisions.subscriptionCount)
    }

    @Test
    fun providerFailureRetractsInvalidBubbleAndPropagatesOriginalException() = runTest {
        val revisions = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val failNow = CompletableDeferred<Unit>()
        val expected = IOException("provider interrupted")
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            revisions.emit(TextStreamEvent(SAVEPOINT, "attempt", 0))
            emit("old|")
            failNow.await()
            revisions.emit(TextStreamEvent(ROLLBACK, "attempt", 0, 4))
            throw expected
        }.withEventChannel(revisions)
        val result = async {
            try {
                updates(source).collect { received += it }
                null
            } catch (error: IOException) {
                error
            }
        }
        runCurrent()
        failNow.complete(Unit)

        assertPropagatedException(expected, requireNotNull(result.await()))
        assertEquals(listOf(Append("old"), Retain(0)), received)
        assertEquals(0, revisions.subscriptionCount)
    }

    @Test
    fun finalSnapshotIncludesUnfinishedSentenceOnce() = runTest {
        val finish = CompletableDeferred<Unit>()
        val received = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            emit("complete|unfinished")
            finish.await()
        }
        val job = launch { updates(source).collect { received += it } }
        runCurrent()
        assertEquals(listOf(Append("complete")), received)

        finish.complete(Unit)
        advanceUntilIdle()
        job.join()

        assertEquals(listOf(Append("complete"), Append("unfinished")), received)
    }

    @Test
    fun typingDelayStillCapsAtThreeSeconds() = runTest {
        val received = mutableListOf<Long>()
        updates(streamOf("first|long sentence|"), charDelayMs = 1000).collect {
            received += testScheduler.currentTime
        }
        assertEquals(listOf(0L, 3000L), received)
    }

    @Test
    fun emptyStreamProducesNoBubbles() = runTest {
        val received = mutableListOf<WaifuSegmentUpdate>()
        updates(streamOf()).collect { received += it }
        assertTrue(received.isEmpty())
    }
}
