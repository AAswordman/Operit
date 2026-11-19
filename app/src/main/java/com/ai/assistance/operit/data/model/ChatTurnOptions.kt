package com.ai.assistance.operit.data.model

data class ChatTurnOptions(
    val useComposerReply: Boolean = true,
    val persistTurn: Boolean = true,
    val notifyReply: Boolean? = null,
    val hideUserMessage: Boolean = false,
    val disableWarning: Boolean = false,
    /** Stable in-process identity for callers that need the terminal result of this exact turn. */
    val turnId: String? = null,
    val isSubTask: Boolean = false,
    /** Host-owned v2 collaboration turns accept mailbox steering without enabling it for v1. */
    val isCollaborationAgent: Boolean = false,
    val collaborationHistory: List<com.ai.assistance.operit.core.chat.hooks.PromptTurn> = emptyList(),
    val collaborationHistoryCutoff: Long? = null,
    /** Request-only system role content. It is never persisted as a user transcript message. */
    val systemPromptOverride: String? = null,
    /** Optional transcript-only role label for the persisted prompt sender. */
    val userRoleNameOverride: String? = null,
    /** Optional transcript-only role label for the persisted assistant response. */
    val assistantRoleNameOverride: String? = null,
)
