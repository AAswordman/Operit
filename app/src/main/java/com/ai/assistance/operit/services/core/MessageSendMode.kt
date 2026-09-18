package com.ai.assistance.operit.services.core

enum class MessageSendMode {
    USER,
    CONTINUE,
    AUTO_CONTINUE;

    val isContinuation: Boolean
        get() = this != USER

    val usesPreviousTurnConfiguration: Boolean
        get() = this == AUTO_CONTINUE

    fun shouldReadComposer(isBackgroundSend: Boolean): Boolean =
        !isBackgroundSend && !isContinuation

    fun shouldAddUserMessage(
        persistTurn: Boolean,
        suppressUserMessageInHistory: Boolean,
        isGroupOrchestrationTurn: Boolean,
        hasContent: Boolean,
    ): Boolean =
        persistTurn &&
            !suppressUserMessageInHistory &&
            !isContinuation &&
            (!isGroupOrchestrationTurn || hasContent)

    suspend fun buildMessageContent(
        prebuiltMessageContent: String?,
        buildUserMessageContent: suspend () -> String,
    ): String =
        when {
            this == CONTINUE -> ""
            prebuiltMessageContent != null -> prebuiltMessageContent
            else -> buildUserMessageContent()
        }
}
