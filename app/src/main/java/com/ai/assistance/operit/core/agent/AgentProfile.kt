package com.ai.assistance.operit.core.agent

import kotlinx.serialization.Serializable

@Serializable
enum class AgentMode {
    PRIMARY,
    SUBAGENT,
    ALL,
}

@Serializable
data class AgentProfile(
    val id: String,
    val name: String,
    val description: String,
    val mode: AgentMode,
    val systemPrompt: String,
    val modelConfigId: String? = null,
    val modelIndex: Int? = null,
    val hidden: Boolean = false,
    val enabled: Boolean = true,
)
