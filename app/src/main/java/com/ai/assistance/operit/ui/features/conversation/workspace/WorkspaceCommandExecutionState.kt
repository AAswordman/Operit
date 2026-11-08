package com.ai.assistance.operit.ui.features.conversation.workspace

data class WorkspaceCommandExecutionState(
    val workspacePath: String,
    val commandLabel: String,
    val commandText: String,
    val sessionId: String,
    val usesDedicatedSession: Boolean,
    val outputEntries: List<String> = emptyList(),
    val isRunning: Boolean = true,
    val isVisible: Boolean = true,
    val isCancelling: Boolean = false
)

fun String.toWorkspaceCommandOutputEntries(): List<String> {
    if (isEmpty()) return emptyList()
    return replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
}
