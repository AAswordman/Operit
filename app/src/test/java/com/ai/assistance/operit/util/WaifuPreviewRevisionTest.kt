package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType.ROLLBACK
import com.ai.assistance.operit.util.stream.TextStreamEventType.SAVEPOINT
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.withEventChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaifuPreviewRevisionTest {
    private var previousLogEnabled = true

    @Before fun disableAndroidLog() {
        previousLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After fun restoreAndroidLog() {
        AppLogger.enableSystemLog = previousLogEnabled
    }

    private data class Bubble(val id: Int, val text: String)

    @Test fun firstPreviewCommitsWithTheSameIdentityAndSpeaksOnlyOnce() = runTest {
        val messages = mutableListOf<Bubble>()
        val spoken = mutableListOf<String>()
        var nextId = 0
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { Bubble(++nextId, it) },
            appendMessage = { message, _ -> spoken += message.text },
            removeMessages = {},
            reviseMessage = { message, text -> message.copy(text = text) },
        )
        store.apply(WaifuSegmentUpdate.Preview("首"))
        store.apply(WaifuSegmentUpdate.Preview("首句"))
        assertEquals(emptyList<String>(), spoken)
        store.apply(WaifuSegmentUpdate.Append("首句。"))
        assertEquals(listOf(Bubble(1, "首句。")), messages)
        assertEquals(listOf("首句。"), spoken)
    }

    @Test fun retryReplacesTheFirstPreviewBeforeItCanBeCommitted() = runTest {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val retry = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val updates = mutableListOf<WaifuSegmentUpdate>()
        val source = stream {
            events.emit(TextStreamEvent(SAVEPOINT, "attempt", 0))
            emit("old")
            retry.await()
            events.emit(TextStreamEvent(ROLLBACK, "attempt", 0, 3))
            emit("new")
            finish.await()
            emit("|")
        }.withEventChannel(events)
        val job = launch {
            streamWaifuUpdates(
                sourceStream = source,
                splitSnapshot = { text, final ->
                    val parts = text.split('|')
                    (if (final) parts else parts.dropLast(1)).filter { it.isNotEmpty() }
                },
                typingDelayMs = { _, _ -> 0 },
                nowMillis = { testScheduler.currentTime },
                firstSegmentPreview = { it.takeIf(String::isNotEmpty) },
            ).collect { updates += it }
        }
        runCurrent()
        assertEquals(WaifuSegmentUpdate.Preview("old"), updates.last())
        retry.complete(Unit)
        runCurrent()
        assertEquals(WaifuSegmentUpdate.Preview("new"), updates.last())
        finish.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertEquals(WaifuSegmentUpdate.Append("new"), updates.last())
        assertEquals(0, events.subscriptionCount)
    }

    @Test fun retractingAPreviewDeletesItsPersistedIdentity() = runTest {
        val messages = mutableListOf<Bubble>()
        val deleted = mutableListOf<Int>()
        val store = WaifuSegmentMessageStore(
            messages = messages,
            createMessage = { Bubble(7, it) },
            appendMessage = { _, _ -> },
            removeMessages = { removed -> deleted += removed.map { it.id } },
            reviseMessage = { message, text -> message.copy(text = text) },
        )
        store.apply(WaifuSegmentUpdate.Preview("失效预览"))
        store.apply(WaifuSegmentUpdate.Retain(0))
        assertEquals(emptyList<Bubble>(), messages)
        assertEquals(listOf(7), deleted)
    }
}
