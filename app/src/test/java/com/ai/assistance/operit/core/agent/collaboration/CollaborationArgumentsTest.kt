package com.ai.assistance.operit.core.agent.collaboration

import org.junit.Assert.*
import org.junit.Test

class CollaborationArgumentsTest {
    @Test fun legacyForkOptionCannotSilentlyInheritParentHistory() {
        val failure = runCatching {
            CollaborationArguments.validate("spawn_agent", listOf("task_name", "message", "fork_context"))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("fork_turns"))
    }

    @Test fun eachToolRejectsUnknownOrDuplicateParameters() {
        CollaborationArguments.validate("spawn_agent", listOf("task_name", "message", "fork_turns"))
        CollaborationArguments.validate("list_agents", emptyList())
        assertTrue(runCatching { CollaborationArguments.validate("wait_agent", listOf("timeout")) }.isFailure)
        assertTrue(runCatching { CollaborationArguments.validate("send_message", listOf("target", "target")) }.isFailure)
    }
}
