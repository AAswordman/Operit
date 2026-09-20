package com.ai.assistance.operit.core.chat.hooks

enum class PromptTurnKind {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL_CALL,
    TOOL_RESULT,
    SUMMARY;

    companion object {
        fun fromRole(role: String): PromptTurnKind {
            return when (role.trim().lowercase()) {
                "system" -> SYSTEM
                "user" -> USER
                "assistant", "ai" -> ASSISTANT
                "tool", "tool_result" -> TOOL_RESULT
                "tool_call", "tool_use" -> TOOL_CALL
                "summary" -> SUMMARY
                else -> USER
            }
        }
    }
}

data class PromptTurn(
    val kind: PromptTurnKind,
    val content: String,
    val toolName: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
) {
    val role: String
        get() =
            when (kind) {
                PromptTurnKind.SYSTEM -> "system"
                PromptTurnKind.USER -> "user"
                PromptTurnKind.ASSISTANT -> "assistant"
                PromptTurnKind.TOOL_CALL -> "tool_call"
                PromptTurnKind.TOOL_RESULT -> "tool_result"
                PromptTurnKind.SUMMARY -> "summary"
            }

    companion object {
        fun fromRole(
            role: String,
            content: String,
            toolName: String? = null,
            metadata: Map<String, Any?> = emptyMap()
        ): PromptTurn {
            return PromptTurn(
                kind = PromptTurnKind.fromRole(role),
                content = content,
                toolName = toolName,
                metadata = metadata
            )
        }
    }
}

fun PromptTurn.withContent(newContent: String): PromptTurn {
    return if (newContent == content) this else copy(content = newContent)
}

fun List<PromptTurn>.appendUserTurnIfMissing(message: String): List<PromptTurn> {
    if (message.isBlank()) {
        return this
    }
    val lastTurn = lastOrNull()
    return if (lastTurn?.kind == PromptTurnKind.USER && lastTurn.content == message) {
        this
    } else {
        this + PromptTurn(kind = PromptTurnKind.USER, content = message)
    }
}

fun applyFinalizedCurrentUserTurn(
    preparedHistory: List<PromptTurn>,
    originalCurrentMessage: String,
    finalizedCurrentMessage: String,
    historyOnly: Boolean = false,
): List<PromptTurn> {
    // Prompt hooks must not turn a manual continuation into a new user message.
    if (historyOnly || finalizedCurrentMessage.isBlank()) {
        return preparedHistory
    }
    val lastTurn = preparedHistory.lastOrNull()
    return when {
        lastTurn?.kind == PromptTurnKind.USER && lastTurn.content == finalizedCurrentMessage ->
            preparedHistory
        lastTurn?.kind == PromptTurnKind.USER && lastTurn.content == originalCurrentMessage ->
            preparedHistory.dropLast(1) + lastTurn.copy(content = finalizedCurrentMessage)
        else -> preparedHistory.appendUserTurnIfMissing(finalizedCurrentMessage)
    }
}

fun List<PromptTurn>.mergeAdjacentTurns(
    shouldMerge: (PromptTurn, PromptTurn) -> Boolean = { previous, current ->
        previous.kind == current.kind &&
            previous.kind !in setOf(PromptTurnKind.SYSTEM, PromptTurnKind.TOOL_CALL, PromptTurnKind.TOOL_RESULT) &&
            previous.toolName == current.toolName
    }
): List<PromptTurn> {
    if (size <= 1) {
        return this
    }

    val merged = mutableListOf<PromptTurn>()
    for (turn in this) {
        val previous = merged.lastOrNull()
        if (previous != null && shouldMerge(previous, turn)) {
            merged[merged.lastIndex] =
                previous.copy(
                    content = previous.content + "\n" + turn.content,
                    metadata = if (turn.metadata.isEmpty()) previous.metadata else previous.metadata + turn.metadata
                )
        } else {
            merged.add(turn)
        }
    }
    return merged
}

fun List<Pair<String, String>>.toPromptTurns(): List<PromptTurn> {
    return map { (role, content) ->
        PromptTurn.fromRole(role = role, content = content)
    }
}

fun List<PromptTurn>.toRoleContentPairs(): List<Pair<String, String>> {
    return map { turn ->
        turn.role to turn.content
    }
}
