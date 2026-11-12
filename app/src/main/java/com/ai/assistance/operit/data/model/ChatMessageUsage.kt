package com.ai.assistance.operit.data.model

/** Selected assistant reply usage without loading message or variant bodies. */
data class ChatMessageUsage(
    val provider: String,
    val modelName: String,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
)
