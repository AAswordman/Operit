package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatMessageDisplayMode {
    NORMAL,
    HIDDEN_PLACEHOLDER,
    ASSISTANT_INTERMEDIATE
}
