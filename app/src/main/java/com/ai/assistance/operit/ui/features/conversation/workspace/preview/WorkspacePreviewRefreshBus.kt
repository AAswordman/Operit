package com.ai.assistance.operit.ui.features.conversation.workspace.preview

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class WorkspacePreviewRefreshEvent(
    val workspacePath: String,
    val workspaceEnv: String?,
    val affectedPaths: List<String>,
    val source: String
)

object WorkspacePreviewRefreshBus {
    private val _events =
        MutableSharedFlow<WorkspacePreviewRefreshEvent>(
            extraBufferCapacity = 32
        )

    val events = _events.asSharedFlow()

    fun tryEmit(event: WorkspacePreviewRefreshEvent) {
        _events.tryEmit(event)
    }
}
