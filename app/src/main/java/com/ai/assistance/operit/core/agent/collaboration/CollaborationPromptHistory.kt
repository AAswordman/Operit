package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.agent.SubagentResultExtractor

internal object CollaborationPromptHistory {
    const val EVENT_METADATA = "operit.collaboration.event"
    const val TASK_METADATA = "operit.collaboration.new_task"
    const val INTERMEDIATE_METADATA = "operit.assistant.intermediate"
    fun canMergeUserTurns(previous: PromptTurn, current: PromptTurn): Boolean =
        previous.kind == PromptTurnKind.USER && current.kind == PromptTurnKind.USER &&
            previous.toolName == current.toolName &&
            previous.metadata[EVENT_METADATA] != true && current.metadata[EVENT_METADATA] != true &&
            previous.metadata[INTERMEDIATE_METADATA] != true && current.metadata[INTERMEDIATE_METADATA] != true

    fun select(history: List<PromptTurn>, fork: AgentFork): List<PromptTurn> {
        val systems = history.filter { it.kind == PromptTurnKind.SYSTEM }
        if (fork == AgentFork.None) return systems
        val selected = if (fork is AgentFork.LastTurns) {
            val starts = history.indices.filter {
                history[it].kind == PromptTurnKind.USER &&
                    history[it].metadata[INTERMEDIATE_METADATA] != true &&
                    (history[it].metadata[EVENT_METADATA] != true || history[it].metadata[TASK_METADATA] == true)
            }
            val first = starts.takeLast(fork.count).firstOrNull() ?: 0
            systems + history.drop(first).filter { it.kind != PromptTurnKind.SYSTEM }
        } else history
        return selected.mapNotNull { turn ->
            when {
                turn.metadata[EVENT_METADATA] == true -> null
                turn.metadata[INTERMEDIATE_METADATA] == true -> null
                turn.kind == PromptTurnKind.TOOL_CALL || turn.kind == PromptTurnKind.TOOL_RESULT -> null
                turn.kind == PromptTurnKind.ASSISTANT -> {
                    val finalText = SubagentResultExtractor.extract(turn.content, "")
                    finalText.takeIf { it.isNotBlank() }?.let {
                        // Provider reasoning/tool metadata belongs to the parent's execution.
                        turn.copy(content = it, metadata = emptyMap(), toolName = null)
                    }
                }
                else -> turn
            }
        }
    }

    /** XML replay splits one stored assistant into several provider turns. Preserve its origin. */
    fun restoreSplitAssistantMetadata(
        segments: List<PromptTurn>,
        sourceMetadata: Map<String, Any?>,
    ): List<PromptTurn> {
        val lastTool = segments.indexOfLast {
            it.kind == PromptTurnKind.TOOL_CALL || it.kind == PromptTurnKind.TOOL_RESULT
        }
        return segments.mapIndexed { index, segment ->
            val intermediate = sourceMetadata[INTERMEDIATE_METADATA] == true ||
                segment.kind == PromptTurnKind.USER ||
                (segment.kind == PromptTurnKind.ASSISTANT && index < lastTool)
            segment.copy(metadata = sourceMetadata + segment.metadata +
                if (intermediate) mapOf(INTERMEDIATE_METADATA to true) else emptyMap())
        }
    }

    fun inheritedPrefix(history: List<CollaborationTurn>, path: String): List<CollaborationTurn> {
        if (history.isEmpty()) return history
        // A separate summary turn also prevents adjacent parent/child USER turns being merged.
        // Codex forks retain user inputs and final answers, not intermediate tool/reasoning turns.
        return history + CollaborationTurn(
            PromptTurnKind.SUMMARY.name,
            "End of inherited parent context. You are $path. The conversation above is background " +
                "only; do not execute its parent task. The following NEW_TASK addressed to $path " +
                "is your assignment.",
        )
    }
}
