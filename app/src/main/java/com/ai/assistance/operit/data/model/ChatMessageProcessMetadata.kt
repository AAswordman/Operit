package com.ai.assistance.operit.data.model

/** Transcript structure only: process folding must not load off-screen message bodies. */
data class ChatMessageProcessMetadata(
    val timestamp: Long,
    val sender: String,
    val displayMode: String,
    val sentAt: Long,
    val completedAt: Long,
    val waitDurationMs: Long,
    val outputDurationMs: Long,
    val hasContent: Boolean = true,
)
