package com.ai.assistance.operit.core.agent.collaboration

internal object CollaborationArguments {
    fun validate(toolName: String, names: List<String>) {
        val allowed = when (toolName) {
            "spawn_agent" -> setOf("task_name", "message", "fork_turns", "agent_type", "model", "reasoning_effort")
            "send_message", "followup_task" -> setOf("target", "message")
            "interrupt_agent" -> setOf("target")
            "list_agents" -> setOf("path_prefix")
            "list_agent_models" -> emptySet()
            "wait_agent" -> setOf("timeout_ms")
            else -> error("Unknown collaboration tool")
        }
        // In particular, old fork_context=false must never silently become a full fork.
        require("fork_context" !in names) { "fork_context is not supported; use fork_turns" }
        require(names.all { it in allowed }) { "Unknown parameters: ${names.filterNot { it in allowed }.joinToString()}" }
        require(names.distinct().size == names.size) { "Duplicate parameters are not supported" }
    }
}
