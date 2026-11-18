package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentToolLoopGuardTest {
    @Test
    fun exactInstruction_reachesThirdConsecutiveCall() {
        val guard = ToolExecutionManager.SubagentToolLoopGuard()
        val tool =
            AITool(
                name = "read_file",
                parameters =
                    listOf(
                        ToolParameter("path", "/workspace/a.kt"),
                        ToolParameter("environment", "linux"),
                    ),
            )

        assertEquals(1, guard.record(tool))
        assertEquals(2, guard.record(tool))
        assertEquals(3, guard.record(tool))
        assertEquals(4, guard.record(tool))
    }

    @Test
    fun parameterOrderDifference_resetsCount() {
        val guard = ToolExecutionManager.SubagentToolLoopGuard()
        val first =
            AITool(
                name = "tool",
                parameters = listOf(ToolParameter("a", "1"), ToolParameter("b", "2")),
            )
        val reordered =
            AITool(
                name = "tool",
                parameters = listOf(ToolParameter("b", "2"), ToolParameter("a", "1")),
            )

        assertEquals(1, guard.record(first))
        assertEquals(2, guard.record(first))
        assertEquals(1, guard.record(reordered))
    }

    @Test
    fun rawValueDifference_resetsCountWithoutNormalization() {
        val guard = ToolExecutionManager.SubagentToolLoopGuard()
        val compact =
            AITool(name = "tool", parameters = listOf(ToolParameter("query", "a b")))
        val extraSpace =
            AITool(name = "tool", parameters = listOf(ToolParameter("query", "a  b")))

        assertEquals(1, guard.record(compact))
        assertEquals(2, guard.record(compact))
        assertEquals(1, guard.record(extraSpace))
    }

    @Test
    fun toolNameDifference_resetsCount() {
        val guard = ToolExecutionManager.SubagentToolLoopGuard()
        val first = AITool(name = "read_file")
        val second = AITool(name = "search_files")

        assertEquals(1, guard.record(first))
        assertEquals(2, guard.record(first))
        assertEquals(1, guard.record(second))
    }

    @Test
    fun batchPreviewFindsThirdCallWithoutMutatingGuard() {
        val guard = ToolExecutionManager.SubagentToolLoopGuard()
        val calls =
            List(4) {
                AITool(
                    name = "read_file",
                    parameters = listOf(ToolParameter("path", "same")),
                )
            }

        assertEquals(2, guard.firstReviewIndex(calls, threshold = 3))
        assertEquals(1, guard.record(calls[0]))
        assertEquals(2, guard.record(calls[1]))
        assertEquals(0, guard.firstReviewIndex(calls.drop(2), threshold = 3))
        assertEquals(3, guard.record(calls[2]))
    }
}
