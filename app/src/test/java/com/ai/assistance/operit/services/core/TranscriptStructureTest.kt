package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessageProcessMetadata
import org.junit.Assert.*
import org.junit.Test

class TranscriptStructureTest {
    @Test fun blankFinalLeavesAllProcessRowsVisible() {
        val rows = listOf(row(1), row(2, mode = "NORMAL", completedAt = 500).copy(hasContent = false))
        val structure = TranscriptStructure(rows)
        assertTrue(structure.turns.isEmpty())
        assertEquals(rows, structure.topLevelRows)
    }
    @Test fun retainedPageKeepsItsLoadedProcessButDropsEvictedTurn() {
        val structure = TranscriptStructure(listOf(
            row(1), row(2, mode = "NORMAL", completedAt = 500),
            row(3, sentAt = 20), row(4, mode = "NORMAL", sentAt = 20, completedAt = 600),
        ))
        assertEquals(setOf(1L, 2L), structure.retainedTimestamps(setOf(2L), listOf(1L, 2L, 3L, 4L)))
        assertEquals(setOf(3L, 4L), structure.retainedTimestamps(setOf(4L), listOf(1L, 2L, 3L, 4L)))
    }
    private fun row(
        timestamp: Long,
        sender: String = "ai",
        mode: String = "ASSISTANT_INTERMEDIATE",
        sentAt: Long = 10,
        completedAt: Long = 0,
    ) = ChatMessageProcessMetadata(timestamp, sender, mode, sentAt, completedAt, 20, 30)

    @Test fun completedTurnAlwaysLeavesFinalInTopLevel() {
        val rows = listOf(row(1, "user", "NORMAL")) +
            (2L..200L).map { row(it) } + row(201, mode = "NORMAL", completedAt = 500)
        val structure = TranscriptStructure(rows)
        assertEquals(listOf(1L, 201L), structure.topLevelRows.map { it.timestamp })
        assertEquals(201L, structure.turnByTimestamp.getValue(50).finalTimestamp)
        assertEquals(200, structure.turns.single().memberTimestamps.size)
    }

    @Test fun humanSteeringRemainsOutsideProcessButAgentMailBelongsInside() {
        val rows = listOf(
            row(1, "user", "COLLABORATION_TASK"),
            row(2),
            row(3, "user", "NORMAL"),
            row(4, "user", "COLLABORATION_EVENT"),
            row(5, mode = "NORMAL", completedAt = 500),
        )
        val structure = TranscriptStructure(rows)
        assertEquals(listOf(3L, 5L), structure.topLevelRows.map { it.timestamp })
        assertFalse(structure.turns.single().memberTimestamps.contains(3))
    }

    @Test fun unfinishedAndDifferentTurnsAreNotHidden() {
        val unfinished = listOf(row(1), row(2, mode = "NORMAL"))
        assertEquals(unfinished, TranscriptStructure(unfinished).topLevelRows)
        val different = listOf(row(1), row(2, mode = "NORMAL", sentAt = 20, completedAt = 500))
        assertEquals(different, TranscriptStructure(different).topLevelRows)
    }

    @Test fun compactionSummaryKeepsOneTurnSoItsProcessStaysWhole() {
        val rows = listOf(
            row(1), row(2, mode = "NORMAL", completedAt = 500),
            row(3, "summary", "NORMAL"),
            row(4, sentAt = 20), row(5, mode = "NORMAL", sentAt = 20, completedAt = 600),
        )
        val structure = TranscriptStructure(rows)
        assertEquals(1, structure.turns.size)
        assertEquals(setOf(1L, 2L, 4L, 5L), structure.turns.single().memberTimestamps)
        assertEquals(5L, structure.turnByTimestamp.getValue(1).finalTimestamp)
        // The halves are one process, so the elapsed time adds up instead of restarting.
        assertEquals(100L, structure.turns.single().durationMs)
        // The divider reporting the compression stays a row of its own.
        assertEquals(listOf(3L, 5L), structure.topLevelRows.map { it.timestamp })
        // Loading that divider keeps the whole process it belongs to.
        assertEquals(setOf(1L, 2L, 4L, 5L), structure.retainedTimestamps(setOf(5L), listOf(1L, 2L, 4L, 5L)))
    }

    @Test fun aHumanMessageStillEndsThePreviousProcess() {
        val rows = listOf(
            row(1), row(2, mode = "NORMAL", completedAt = 500),
            row(3, "summary", "NORMAL"), row(4, "user", "NORMAL"),
            row(5, sentAt = 20), row(6, mode = "NORMAL", sentAt = 20, completedAt = 600),
        )
        val structure = TranscriptStructure(rows)
        assertEquals(listOf(2L, 6L), structure.turns.map { it.finalTimestamp })
    }

    @Test fun anAgentCardBesideTheSummaryKeepsOneProcess() {
        val rows = listOf(
            row(1), row(2, mode = "NORMAL", completedAt = 500),
            row(3, "user", "COLLABORATION_EVENT"),
            row(4, "summary", "NORMAL"),
            row(5, sentAt = 20), row(6, mode = "NORMAL", sentAt = 20, completedAt = 600),
        )
        val structure = TranscriptStructure(rows)
        assertEquals(1, structure.turns.size)
        assertEquals(setOf(1L, 2L, 5L, 6L), structure.turns.single().memberTimestamps)
        assertEquals(6L, structure.turnByTimestamp.getValue(1).finalTimestamp)
        assertEquals(100L, structure.turns.single().durationMs)
        // The card the summary pushed into the gap, and the divider itself, stay visible outside.
        assertEquals(listOf(3L, 4L, 6L), structure.topLevelRows.map { it.timestamp })
    }

    @Test fun anAgentCardAfterTheSummaryStaysInsideTheProcess() {
        val rows = listOf(
            row(1), row(2, mode = "NORMAL", completedAt = 500),
            row(3, "summary", "NORMAL"),
            row(4, "user", "COLLABORATION_EVENT"),
            row(5, sentAt = 20), row(6, mode = "NORMAL", sentAt = 20, completedAt = 600),
        )
        val structure = TranscriptStructure(rows)
        assertEquals(1, structure.turns.size)
        assertEquals(setOf(1L, 2L, 4L, 5L, 6L), structure.turns.single().memberTimestamps)
        assertEquals(6L, structure.turnByTimestamp.getValue(1).finalTimestamp)
        assertEquals(listOf(3L, 6L), structure.topLevelRows.map { it.timestamp })
    }

    @Test fun anAgentCardAloneNeverGluesTwoTurnsTogether() {
        val rows = listOf(
            row(1), row(2, mode = "NORMAL", completedAt = 500),
            row(3, "user", "COLLABORATION_EVENT"),
            row(4, sentAt = 20), row(5, mode = "NORMAL", sentAt = 20, completedAt = 600),
        )
        assertEquals(2, TranscriptStructure(rows).turns.size)
    }

    @Test fun aHiddenPlaceholderStillEndsTheProcess() {
        val rows = listOf(
            row(1), row(2, mode = "NORMAL", completedAt = 500),
            row(3, "user", "HIDDEN_PLACEHOLDER"),
            row(4, "summary", "NORMAL"),
            row(5, sentAt = 20), row(6, mode = "NORMAL", sentAt = 20, completedAt = 600),
        )
        assertEquals(listOf(2L, 6L), TranscriptStructure(rows).turns.map { it.finalTimestamp })
    }

    @Test fun aTurnTheAppDeliveredForTheOwnerStillEndsTheProcess() {
        val rows = listOf(
            row(1), row(2, mode = "NORMAL", completedAt = 500),
            row(3, "user", "TOOL_DELIVERED"),
            row(4, "summary", "NORMAL"),
            row(5, sentAt = 20), row(6, mode = "NORMAL", sentAt = 20, completedAt = 600),
        )
        val structure = TranscriptStructure(rows)
        assertEquals(listOf(2L, 6L), structure.turns.map { it.finalTimestamp })
    }
}
