package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RevisionAppliedTextTest {

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
    fun plainStreamWithoutEventsIsConcatenated() = runBlocking {
        val chunks = listOf("alpha", "beta", "gamma")

        val collected = chunks.asStream().collectRevisionAppliedText()

        assertEquals("alphabetagamma", collected)
    }

    @Test
    fun rollbackRemovesTheAbortedAttemptFromTheCollectedText() {
        val chunks = listOf("<tool_L6Gi", "<tool_AB12 name=\"device_info\">", "\n</tool_AB12>")

        val collected =
            applyRevisionEvents(
                chunks.joinToString(separator = ""),
                listOf(
                    TextStreamEvent(TextStreamEventType.SAVEPOINT, "attempt", contentLength = 0),
                    TextStreamEvent(
                        TextStreamEventType.ROLLBACK,
                        "attempt",
                        contentLength = 0,
                        discardedLength = "<tool_L6Gi".length
                    )
                )
            )

        assertEquals("<tool_AB12 name=\"device_info\">\n</tool_AB12>", collected)
    }

    @Test
    fun rollbackKeepsTextProducedBeforeTheSavepoint() {
        val collected =
            applyRevisionEvents(
                "kept prefix<tool_L6Gi",
                listOf(
                    TextStreamEvent(TextStreamEventType.SAVEPOINT, "attempt", contentLength = 11),
                    TextStreamEvent(
                        TextStreamEventType.ROLLBACK,
                        "attempt",
                        contentLength = 11,
                        discardedLength = "<tool_L6Gi".length
                    )
                )
            )

        assertEquals("kept prefix", collected)
    }

    @Test
    fun collectForwardsEveryChunk() = runBlocking {
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val source =
            stream {
                events.emit(TextStreamEvent(TextStreamEventType.SAVEPOINT, "attempt", 0))
                emit("<tool_L6Gi")
                events.emit(
                    TextStreamEvent(
                        TextStreamEventType.ROLLBACK,
                        "attempt",
                        contentLength = 0,
                        discardedLength = "<tool_L6Gi".length
                    )
                )
                emit("final")
            }.withEventChannel(events)
        val forwarded = mutableListOf<String>()

        val collected = source.collectRevisionAppliedText { chunk -> forwarded += chunk }

        assertEquals("final", collected)
        assertEquals(listOf("<tool_L6Gi", "final"), forwarded)
    }

    /**
     * One response can contain several provider requests, and each provider numbers its revision
     * events from the start of its own request. EnhancedAIService shifts them into the coordinate
     * space of the whole response; a rollback of a later round must not touch earlier rounds.
     */
    @Test
    fun rollbackOfALaterRoundKeepsEarlierRounds() {
        val roundOne = "<tool_AB12 name=\"device_info\">\n</tool_AB12>"
        val abortedRoundTwo = "PREFIX-LEAK-"
        val roundTwo = "设备信息已读取完成。"
        val rawContent = roundOne + abortedRoundTwo + roundTwo

        val collected =
            applyRevisionEvents(
                rawContent,
                listOf(
                    TextStreamEvent(TextStreamEventType.SAVEPOINT, "round1", contentLength = 0),
                    TextStreamEvent(
                        TextStreamEventType.SAVEPOINT,
                        "round2",
                        contentLength = roundOne.length,
                        0
                    ),
                    TextStreamEvent(
                        TextStreamEventType.ROLLBACK,
                        "round2",
                        contentLength = roundOne.length,
                        discardedLength = abortedRoundTwo.length
                    )
                )
            )

        assertEquals(roundOne + roundTwo, collected)
    }

    @Test
    fun shiftedByMovesBothEventCoordinates() {
        val event =
            TextStreamEvent(
                TextStreamEventType.ROLLBACK,
                "attempt",
                contentLength = 5,
                discardedLength = 7
            )

        assertEquals(25, event.shiftedBy(20).contentLength)
        assertEquals(7, event.shiftedBy(20).discardedLength)
    }
}
