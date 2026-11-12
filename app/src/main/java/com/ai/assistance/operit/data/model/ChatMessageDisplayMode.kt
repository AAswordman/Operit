package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatMessageDisplayMode {
    NORMAL,
    HIDDEN_PLACEHOLDER,
    COLLABORATION_EVENT,
    COLLABORATION_TASK,
    ASSISTANT_INTERMEDIATE;

    val isCollaborationEvent: Boolean
        get() = this == COLLABORATION_EVENT || this == COLLABORATION_TASK
}
