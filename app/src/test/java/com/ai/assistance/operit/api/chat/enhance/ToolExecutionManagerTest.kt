package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolExecutionManagerTest {
    @Test
    fun structuredErrorCodeTakesPriorityOverLegacyMessageClassification() {
        val result = failedResult(
            error = "Tool did not start.",
            errorCode = "overlay_permission_required"
        )

        assertEquals(
            "overlay_permission_required",
            ToolExecutionManager.resolveRuntimeErrorCode(result)
        )
    }

    @Test
    fun legacyErrorsKeepExistingFallbackClassification() {
        assertEquals(
            "invalid_arguments",
            ToolExecutionManager.resolveRuntimeErrorCode(
                failedResult(error = "Invalid parameters: missing path")
            )
        )
        assertEquals(
            "tool_execution_failed",
            ToolExecutionManager.resolveRuntimeErrorCode(
                failedResult(error = "Filesystem operation failed")
            )
        )
    }

    private fun failedResult(error: String, errorCode: String? = null) =
        ToolResult(
            toolName = "read_file",
            success = false,
            result = StringResultData(""),
            error = error,
            errorCode = errorCode
        )
}
