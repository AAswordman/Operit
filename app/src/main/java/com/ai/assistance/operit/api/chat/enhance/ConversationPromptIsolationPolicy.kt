package com.ai.assistance.operit.api.chat.enhance

internal object ConversationPromptIsolationPolicy {
    fun resolveSystemTemplate(
        isSubTask: Boolean,
        requestTemplate: String?,
        globalTemplate: String,
    ): String =
        if (isSubTask) {
            requestTemplate.orEmpty()
        } else {
            requestTemplate ?: globalTemplate
        }

    fun allowPersonalContext(isSubTask: Boolean): Boolean = !isSubTask
}
