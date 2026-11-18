package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.core.agent.AgentMode
import com.ai.assistance.operit.core.agent.AgentProfile
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemToolPromptsSubagentTest {
    @Test
    fun customProfilesAreExposedToTheMainAgentTaskTool() {
        val custom =
            AgentProfile(
                id = "code_review",
                name = "Code Review",
                description = "Performs adversarial code review.",
                mode = AgentMode.SUBAGENT,
                systemPrompt = "Review the assigned change.",
            )

        val tool =
            SystemToolPrompts.buildSubagentTools(
                    useChinese = false,
                    profiles = listOf(custom),
                )
                .tools
                .single()

        assertTrue(tool.description.contains("code_review"))
        assertTrue(tool.description.contains("Performs adversarial code review."))
        assertTrue(
            tool.parametersStructured
                .orEmpty()
                .single { it.name == "subagent_type" }
                .description
                .contains("code_review")
        )
    }
}
