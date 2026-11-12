package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.config.SystemPromptConfig

object SubagentPromptBuilder {
    fun buildSystemPrompt(profile: AgentProfile): String =
        """
        AGENT PROFILE:
        ${profile.systemPrompt}

        ${SystemPromptConfig.SUBTASK_AGENT_PROMPT_TEMPLATE}

        This is a child task. Do not invoke the task tool or create another Subagent.
        """.trimIndent()

    fun buildTaskPrompt(prompt: String): String {
        require(prompt.isNotBlank()) { "Subagent prompt must not be blank" }
        return prompt
    }
}
