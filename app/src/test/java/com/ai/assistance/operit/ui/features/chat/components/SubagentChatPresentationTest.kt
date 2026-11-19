package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.core.agent.AgentMode
import com.ai.assistance.operit.core.agent.AgentProfile
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.SubagentRunEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentChatPresentationTest {
    @Test
    fun `profile snapshot name is used instead of profile id`() {
        val profile =
            AgentProfile(
                id = "custom-review",
                name = "审查专家",
                description = "Review",
                mode = AgentMode.SUBAGENT,
                systemPrompt = "Review carefully",
            )

        assertEquals(
            "审查专家",
            resolveSubagentAgentName(
                SubagentRunEntity(
                    id = "task-1",
                    parentChatId = "parent-1",
                    childChatId = "child-1",
                    agentProfileId = profile.id,
                    title = "Review",
                    status = "COMPLETED",
                    createdAt = 1L,
                    agentConfigSnapshot = Json.encodeToString(profile),
                )
            ),
        )
    }

    @Test
    fun `legacy default transcript names are replaced for subagent display`() {
        assertEquals(
            "Rainy",
            resolveSubagentTranscriptRoleName(
                message = ChatMessage(sender = "user", roleName = "用户"),
                parentAgentName = "Rainy",
                subagentName = "Explore",
                localizedUserRoleName = "用户",
            ),
        )
        assertEquals(
            "Explore",
            resolveSubagentTranscriptRoleName(
                message = ChatMessage(sender = "ai", roleName = "Operit"),
                parentAgentName = "Rainy",
                subagentName = "Explore",
                localizedUserRoleName = "用户",
            ),
        )
    }

    @Test
    fun `explicit transcript names are preserved`() {
        assertEquals(
            "Original role",
            resolveSubagentTranscriptRoleName(
                message = ChatMessage(sender = "user", roleName = "Original role"),
                parentAgentName = "Rainy",
                subagentName = "Explore",
                localizedUserRoleName = "用户",
            ),
        )
    }
}
