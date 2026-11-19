package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.core.agent.AgentProfile
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.SubagentRunEntity
import kotlinx.serialization.json.Json

private val subagentProfileJson = Json { ignoreUnknownKeys = true }

internal fun resolveSubagentAgentName(run: SubagentRunEntity?): String {
    if (run == null) return ""
    return run.agentConfigSnapshot
        ?.let { snapshot ->
            runCatching {
                    subagentProfileJson.decodeFromString<AgentProfile>(snapshot).name.trim()
                }
                .getOrNull()
                ?.takeIf(String::isNotEmpty)
        }
        ?: run.agentProfileId
}

internal fun resolveSubagentTranscriptRoleName(
    message: ChatMessage,
    parentAgentName: String,
    subagentName: String,
    localizedUserRoleName: String,
): String {
    val storedName = message.roleName.trim()
    return when (message.sender) {
        "user" ->
            if (
                storedName.isEmpty() ||
                    storedName.equals("user", ignoreCase = true) ||
                    storedName == "用户" ||
                    storedName == localizedUserRoleName
            ) {
                parentAgentName.ifBlank { message.roleName }
            } else {
                message.roleName
            }

        "ai" ->
            if (storedName.isEmpty() || storedName.equals("Operit", ignoreCase = true)) {
                subagentName.ifBlank { message.roleName }
            } else {
                message.roleName
            }

        else -> message.roleName
    }
}
