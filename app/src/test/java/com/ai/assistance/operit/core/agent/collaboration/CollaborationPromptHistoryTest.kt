package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.mergeAdjacentTurns
import org.junit.Assert.*
import org.junit.Test

class CollaborationPromptHistoryTest {
    @Test fun syntheticUserCannotAbsorbRealInputInEitherOrder() {
        val real = PromptTurn(PromptTurnKind.USER, "real task")
        val synthetic = PromptTurn(PromptTurnKind.USER, "status", metadata = mapOf(
            CollaborationPromptHistory.INTERMEDIATE_METADATA to true,
        ))
        assertFalse(CollaborationPromptHistory.canMergeUserTurns(real, synthetic))
        assertFalse(CollaborationPromptHistory.canMergeUserTurns(synthetic, real))
        for (history in listOf(listOf(real, synthetic), listOf(synthetic, real))) {
            assertEquals(listOf(real), CollaborationPromptHistory.select(history, AgentFork.LastTurns(1)))
        }
    }

    @Test fun restoredXmlReplayKeepsOnlyFinalAndDoesNotInventUserTurns() {
        val restored = CollaborationPromptHistory.restoreSplitAssistantMetadata(listOf(
            PromptTurn(PromptTurnKind.ASSISTANT, "I will inspect"),
            PromptTurn(PromptTurnKind.TOOL_CALL, "inspect"),
            PromptTurn(PromptTurnKind.TOOL_RESULT, "private details"),
            PromptTurn(PromptTurnKind.USER, "synthetic warning"),
            PromptTurn(PromptTurnKind.ASSISTANT, "final answer"),
        ), emptyMap())
        val selected = CollaborationPromptHistory.select(
            listOf(PromptTurn(PromptTurnKind.USER, "real task")) + restored, AgentFork.LastTurns(1),
        )
        assertEquals(listOf("real task", "final answer"), selected.map { it.content })
        val intermediate = CollaborationPromptHistory.restoreSplitAssistantMetadata(
            restored, mapOf(CollaborationPromptHistory.INTERMEDIATE_METADATA to true),
        )
        assertTrue(CollaborationPromptHistory.select(intermediate, AgentFork.All).isEmpty())
    }

    @Test fun countTasksBeforeDroppingCommunicationsAndToolRounds() {
        fun task(text: String) = PromptTurn(PromptTurnKind.USER, text, metadata = mapOf(
            CollaborationPromptHistory.EVENT_METADATA to true,
            CollaborationPromptHistory.TASK_METADATA to true,
        ))
        val selected = CollaborationPromptHistory.select(listOf(
            PromptTurn(PromptTurnKind.SYSTEM, "system"),
            task("old task"), PromptTurn(PromptTurnKind.ASSISTANT, "old final"),
            task("new task"),
            PromptTurn(PromptTurnKind.ASSISTANT, "checking now", metadata = mapOf(
                CollaborationPromptHistory.INTERMEDIATE_METADATA to true,
            )),
            PromptTurn(PromptTurnKind.TOOL_RESULT, "private tool output"),
            PromptTurn(PromptTurnKind.ASSISTANT, "<think>private reasoning</think>new final"),
        ), AgentFork.LastTurns(1))
        assertEquals(listOf("system", "new final"), selected.map { it.content })
    }

    @Test fun inheritedUserCannotBeMergedWithAnAgentMessage() {
        val user = PromptTurn(PromptTurnKind.USER, "real user")
        val message = PromptTurn(PromptTurnKind.USER, "agent", metadata = mapOf(
            CollaborationPromptHistory.EVENT_METADATA to true,
        ))
        assertFalse(CollaborationPromptHistory.canMergeUserTurns(user, message))
        assertFalse(CollaborationPromptHistory.canMergeUserTurns(message, user))
        assertTrue(CollaborationPromptHistory.canMergeUserTurns(user, user))
        assertEquals(listOf(PromptTurn(PromptTurnKind.SYSTEM, "policy")),
            CollaborationPromptHistory.select(listOf(PromptTurn(PromptTurnKind.SYSTEM, "policy"), user), AgentFork.None))
    }
    @Test fun forkKeepsParentRequestSeparateFromLastNamedAssignment() {
        val parent = CollaborationTurn("USER", "Create two child agents")
        val assignment = AgentMessage(
            "00000000-0000-0000-0000-000000000001", "/root", "/root/child",
            AgentMessageKind.NEW_TASK, "Read the inherited marker and reply without tools",
        )
        val request = (CollaborationPromptHistory.inheritedPrefix(listOf(parent), "/root/child")
            .map { it.toPromptTurn() } + PromptTurn(PromptTurnKind.USER, assignment.render()))
            .mergeAdjacentTurns { a, b -> a.kind == PromptTurnKind.USER && b.kind == PromptTurnKind.USER }
        assertEquals(3, request.size)
        assertEquals(parent.content, request.first().content)
        assertEquals(PromptTurnKind.SUMMARY, request[1].kind)
        assertTrue(request[1].content.contains("/root/child"))
        assertEquals(assignment.render(), request.last().content)
        assertTrue(request.last().content.endsWith(assignment.text))
    }

    @Test fun noForkAddsNoInheritedBoundary() {
        assertTrue(CollaborationPromptHistory.inheritedPrefix(emptyList(), "/root/child").isEmpty())
    }
}
