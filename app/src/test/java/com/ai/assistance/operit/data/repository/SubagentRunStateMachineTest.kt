package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.SubagentRunStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentRunStateMachineTest {
    @Test
    fun terminalResultCannotBeOverwrittenByLateCancellationOrFailure() {
        val cancelOrigins =
            SubagentRunStateMachine.allowedOrigins(SubagentRunStatus.CANCELLED)
        val failureOrigins =
            SubagentRunStateMachine.allowedOrigins(SubagentRunStatus.FAILED)

        assertFalse(SubagentRunStatus.COMPLETED in cancelOrigins)
        assertFalse(SubagentRunStatus.COMPLETED in failureOrigins)
        assertTrue(SubagentRunStatus.RUNNING in cancelOrigins)
    }

    @Test
    fun terminalTaskCanStartAnExplicitContinuation() {
        val runningOrigins =
            SubagentRunStateMachine.allowedOrigins(SubagentRunStatus.RUNNING)

        assertTrue(SubagentRunStatus.COMPLETED in runningOrigins)
        assertTrue(SubagentRunStatus.FAILED in runningOrigins)
        assertTrue(SubagentRunStatus.CANCELLED in runningOrigins)
        assertTrue(SubagentRunStatus.INTERRUPTED in runningOrigins)
    }
}
