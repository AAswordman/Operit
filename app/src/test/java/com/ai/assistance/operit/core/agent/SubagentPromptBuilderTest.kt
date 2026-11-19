package com.ai.assistance.operit.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentPromptBuilderTest {
    @Test
    fun profileInstructionsStayInSystemRoleAndOutOfTaskMessage() {
        val profile =
            AgentProfile(
                id = "explore",
                name = "Explore",
                description = "Search",
                mode = AgentMode.SUBAGENT,
                systemPrompt = "PROFILE-SYSTEM-INSTRUCTION",
            )

        val systemPrompt = SubagentPromptBuilder.buildSystemPrompt(profile)
        val taskPrompt = SubagentPromptBuilder.buildTaskPrompt("inspect the repository")

        assertTrue(systemPrompt.contains("PROFILE-SYSTEM-INSTRUCTION"))
        assertTrue(systemPrompt.contains("AVAILABLE_TOOLS_SECTION"))
        assertFalse(systemPrompt.contains("BEGIN_SELF_INTRODUCTION_SECTION"))
        assertTrue(systemPrompt.contains("Do not invoke the task tool"))
        assertEquals("inspect the repository", taskPrompt)
        assertFalse(taskPrompt.contains("PROFILE-SYSTEM-INSTRUCTION"))
    }
}
