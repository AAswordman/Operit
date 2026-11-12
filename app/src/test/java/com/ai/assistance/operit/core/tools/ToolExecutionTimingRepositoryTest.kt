package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolExecutionTimingRepositoryTest {
    @Test
    fun sameNameInvocations_keepIndependentStatesAndDurations() {
        val scopeId = "same-name-${System.nanoTime()}"
        val first = invocation(callId = "first", invocationIndex = 0)
        val second = invocation(callId = "second", invocationIndex = 1)

        ToolExecutionTimingRepository.register(scopeId, first)
        ToolExecutionTimingRepository.register(scopeId, second)
        ToolExecutionTimingRepository.markWaitingExecution(scopeId, first)
        ToolExecutionTimingRepository.markRunning(scopeId, first, startedAtElapsedMs = 100L)
        ToolExecutionTimingRepository.markFinished(
            scopeId = scopeId,
            invocation = first,
            result = result("A"),
            durationMs = 800L,
            state = ToolExecutionState.COMPLETED,
        )

        val firstSnapshot = ToolExecutionTimingRepository.get(scopeId, 0)
        val secondSnapshot = ToolExecutionTimingRepository.get(scopeId, 1)

        assertEquals("first", firstSnapshot?.callId)
        assertEquals(ToolExecutionState.COMPLETED, firstSnapshot?.state)
        assertEquals(800L, firstSnapshot?.durationMs)
        assertEquals("A", firstSnapshot?.resultText)
        assertEquals("second", secondSnapshot?.callId)
        assertEquals(ToolExecutionState.WAITING_EXECUTION, secondSnapshot?.state)
        assertNull(secondSnapshot?.durationMs)
    }

    private fun invocation(callId: String, invocationIndex: Int) =
        ToolInvocation(
            tool = AITool(name = "read_file"),
            rawText = """<tool name="read_file"></tool>""",
            responseLocation = 0..0,
            callId = callId,
            invocationIndex = invocationIndex,
        )

    private fun result(text: String) =
        ToolResult(
            toolName = "read_file",
            success = true,
            result = StringResultData(text),
        )
}
