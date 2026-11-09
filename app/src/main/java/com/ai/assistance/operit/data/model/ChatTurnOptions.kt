package com.ai.assistance.operit.data.model

data class ChatTurnOptions(
    val useComposerReply: Boolean = true,
    val persistTurn: Boolean = true,
    val notifyReply: Boolean? = null,
    val hideUserMessage: Boolean = false,
    val disableWarning: Boolean = false
)
