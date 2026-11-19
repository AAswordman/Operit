package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import org.junit.Assert.*
import org.junit.Test

class CollaborationCheckpointTest {
    private val task = PromptTurn(PromptTurnKind.USER, "Generate a token, do not summarize", metadata = mapOf(
        CollaborationPromptHistory.EVENT_METADATA to true,
        CollaborationPromptHistory.TASK_METADATA to true,
    ))

    @Test fun originalTaskAndAcceptedCorrectionsSurviveAnIncorrectSummary() {
        val correction = PromptTurn(PromptTurnKind.USER, "Use twelve characters")
        val durable = CollaborationCheckpoint.durableHistory("Incorrect claim: task is to summarize", listOf(task, correction))
        assertEquals(listOf(task, correction), durable.drop(1))
        val resumed = CollaborationCheckpoint.resumeHistory(emptyList(), durable)
        assertEquals(listOf(task, correction), resumed.takeLast(2))
        assertFalse(durable.any { it.content.contains("Continue the current") })
    }

    @Test fun summarySourceQuotesRawTasksInsteadOfReplayingTheirRole() {
        val source = CollaborationCheckpoint.summaryInput(listOf(task))
        assertEquals(1, source.size)
        assertTrue(source.single().content.contains("quoted conversation data"))
        assertTrue(source.single().content.contains("\"content\":\"Generate a token"))
        assertFalse(source.contains(task))
    }

    @Test fun nextExecutionOnlyRetainsItsOwnInputsAndPreservesHistoryOrder() {
        val oldCheckpoint = CollaborationCheckpoint.durableHistory("old progress", listOf(task))
        val oldFinal = PromptTurn(PromptTurnKind.ASSISTANT, "FIRST_COMPLETE abcdef123456")
        val newTask = task.copy(content = "Recall the prior answer, do not generate another token")
        val restored = oldCheckpoint + oldFinal + newTask
        assertEquals(oldFinal, restored[restored.lastIndex - 1])
        val nextCheckpoint = CollaborationCheckpoint.durableHistory("new progress", listOf(newTask))
        assertEquals(listOf(newTask), nextCheckpoint.filter { it.kind == PromptTurnKind.USER })
        assertTrue(runCatching { CollaborationCheckpoint.durableHistory("lost task", emptyList()) }.isFailure)
    }
}
