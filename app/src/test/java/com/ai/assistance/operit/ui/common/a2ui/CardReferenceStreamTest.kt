package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.assertPropagatedException
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType.ROLLBACK
import com.ai.assistance.operit.util.stream.TextStreamEventType.SAVEPOINT
import com.ai.assistance.operit.util.stream.applyRevisionEvents
import com.ai.assistance.operit.util.stream.collectRevisionAppliedText
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.withEventChannel
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CardReferenceStreamTest {
    private var previousLogEnabled = true

    @Before fun disableAndroidLog() {
        previousLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After fun restoreAndroidLog() {
        AppLogger.enableSystemLog = previousLogEnabled
    }

    @Test fun rollbackUsesExpandedCoordinatesAndPreservesEarlierText() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val reference = "```a2ui-ref\ncard-1\n```\n"
        val source = stream {
            emit("kept\n")
            events.emit(TextStreamEvent(SAVEPOINT, "attempt", 5))
            emit(reference.take(12))
            emit(reference.drop(12))
            events.emit(TextStreamEvent(ROLLBACK, "attempt", 5, reference.length))
            emit("retry")
        }.withEventChannel(events)

        val expanded = source.expandCardReferences { "expanded-card-".repeat(40) }
        assertEquals("kept\nretry", expanded.collectRevisionAppliedText())
        assertEquals(0, events.subscriptionCount)
    }

    @Test fun rollbackCanArriveBeforeTheDiscardedTextChunks() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val reference = "```a2ui-ref\ncard-1\n```\n"
        val source = stream {
            events.emit(TextStreamEvent(SAVEPOINT, "attempt", 0))
            events.emit(TextStreamEvent(ROLLBACK, "attempt", 0, reference.length))
            emit(reference)
            emit("correct")
        }.withEventChannel(events)

        assertEquals("correct", source.expandCardReferences { "obsolete" }.collectRevisionAppliedText())
    }

    @Test fun queuedFinalRollbackDoesNotNeedAnotherTextChunk() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val source = stream {
            emit("kept")
            events.emit(TextStreamEvent(SAVEPOINT, "attempt", 4))
            emit("discarded")
            events.emit(TextStreamEvent(ROLLBACK, "attempt", 4, 9))
        }.withEventChannel(events)

        assertEquals("kept", source.expandCardReferences { null }.collectRevisionAppliedText())
    }

    @Test fun failureFinalizerStillPublishesItsLastRevision() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val failure = IOException("request failed")
        val source = stream {
            emit("kept")
            events.emit(TextStreamEvent(SAVEPOINT, "attempt", 4))
            try {
                emit("discarded")
                throw failure
            } finally {
                events.emit(TextStreamEvent(ROLLBACK, "attempt", 4, 9))
            }
        }.withEventChannel(events).expandCardReferences { null }
        val raw = StringBuilder()
        try {
            source.collect { raw.append(it) }
            throw AssertionError("请求异常应继续传播")
        } catch (error: IOException) {
            // 协程堆栈恢复可能复制异常，按类型、消息和原始因果链核对传播。
            assertPropagatedException(failure, error)
        }
        val history = (source as TextStreamEventCarrier).eventChannel.replayCache
        assertEquals("kept", applyRevisionEvents(raw.toString(), history))
        assertEquals(0, events.subscriptionCount)
    }

    @Test fun successiveRetriesKeepRawOffsetsAfterEarlierDiscards() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val source = stream {
            emit("keep:")
            events.emit(TextStreamEvent(SAVEPOINT, "first", 5))
            emit("bad")
            events.emit(TextStreamEvent(ROLLBACK, "first", 5, 3))
            events.emit(TextStreamEvent(SAVEPOINT, "second", 8))
            emit("wrong")
            events.emit(TextStreamEvent(ROLLBACK, "second", 8, 5))
            emit("good")
        }.withEventChannel(events)
        assertEquals("keep:good", source.expandCardReferences { null }.collectRevisionAppliedText())
    }
}
