package com.ai.assistance.operit.core.agent

internal object SubagentToolPolicy {
    private val forbiddenToolNames =
        setOf(
            "task",
            "create_new_chat",
            "send_message_to_ai",
            "send_message_to_ai_streaming",
        )

    fun isForbidden(toolName: String): Boolean = toolName in forbiddenToolNames
}
