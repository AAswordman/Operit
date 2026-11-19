package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.data.model.ToolResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolExecutionMarkupTest {
    @Test
    fun finalResult_persistsInvocationIdentityAndDuration() {
        val result =
            ToolResult(
                toolName = "read_file",
                success = true,
                result = StringResultData("file contents"),
                callId = "call-2",
                invocationIndex = 2,
                executionDurationMs = 1_234L,
                executionState = ToolExecutionState.COMPLETED,
                isFinal = true,
            )

        val markup = ConversationMarkupManager.formatToolResultForMessage(result)

        assertTrue(markup.contains("""call_id="call-2""""))
        assertTrue(markup.contains("""invocation_index="2""""))
        assertTrue(markup.contains("""duration_ms="1234""""))
        assertTrue(markup.contains("""execution_state="completed""""))
        assertTrue(markup.contains("""final="true""""))
        assertTrue(markup.contains("file contents"))
    }

    @Test
    fun modelNormalization_removesOnlyExecutionMetadata() {
        val markup =
            """<tool_result_abc name="read_file" status="success" call_id="call-2" invocation_index="2" duration_ms="1234" execution_state="completed" final="true"><content>file contents</content></tool_result_abc>"""

        val normalized = stripToolExecutionMetadataFromToolResults(markup)

        assertFalse(normalized.contains("call_id"))
        assertFalse(normalized.contains("invocation_index"))
        assertFalse(normalized.contains("duration_ms"))
        assertFalse(normalized.contains("execution_state"))
        assertFalse(normalized.contains("""final="true""""))
        assertTrue(normalized.contains("""name="read_file""""))
        assertTrue(normalized.contains("""status="success""""))
        assertTrue(normalized.contains("<content>file contents</content>"))
    }
}
