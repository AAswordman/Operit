package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.core.tools.PackageActivationErrorCode
import com.ai.assistance.operit.core.tools.PackageActivationErrorData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FileApplyResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkupManagerFailureTest {
    @Test
    fun `activation failures appear once for every structured error code`() {
        val message = "激活包失败：缺少必需环境变量 GITHUB_TOKEN"
        for (code in PackageActivationErrorCode.values()) {
            val result = ToolResult(
                toolName = "use_package",
                success = false,
                result = PackageActivationErrorData(code, "github", message),
                error = message,
            )
            val markup = ConversationMarkupManager.formatToolResultForMessage(result)
            assertTrue(markup.contains("status=\"error\""))
            assertTrue(markup.contains("<error>$message</error>"))
            assertEquals(1, Regex(Regex.escape(message)).findAll(markup).count())
        }
    }

    @Test
    fun `identical messages are compared after existing whitespace normalization`() {
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("probe", false, StringResultData("  same error\n"), error = "same error"),
        )
        assertTrue(markup.contains("<error>same error</error>"))
    }

    @Test
    fun `terminal details survive alongside a different failure summary`() {
        val output = "stdout: complete\nstderr: detail\nexitCode=7"
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("terminal_exec", false, StringResultData(output), error = "command failed"),
        )
        assertTrue(markup.contains("<error>command failed\n\n$output</error>"))
    }

    @Test
    fun `details containing the summary are not discarded`() {
        val detail = "command failed\nstdout: useful output\nexitCode=7"
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("terminal_exec", false, StringResultData(detail), error = "command failed"),
        )
        assertTrue(markup.contains("command failed\n\n$detail"))
    }

    @Test
    fun `repeated successful output is unchanged`() {
        val output = "same line\nsame line"
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("terminal_exec", true, StringResultData(output)),
        )
        assertTrue(markup.contains("status=\"success\""))
        assertTrue(markup.contains("<content>$output</content>"))
    }
    @Test
    fun environmentDecoratedDuplicateKeepsSourceAndAppearsOnce() {
        val message = "File already exists"
        for (environment in listOf("android", "linux", "repo:documents", "custom-source")) {
            val detail = "[$environment] $message"
            val markup = ConversationMarkupManager.formatToolResultForMessage(
                ToolResult("create_file", false,
                    FileOperationData("create", environment, "/a", false, message), error = message),
            )
            assertTrue(markup.contains("<error>$detail</error>"))
            assertEquals(1, Regex(Regex.escape(message)).findAll(markup).count())
        }
    }

    @Test
    fun structuredFileFailureOwnsItsMessageAndSource() {
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("create_file", false,
                FileOperationData("create", "android", "/a", false, "File exists"),
                error = "Generic file operation failure"),
        )
        assertTrue(markup.contains("<error>[android] File exists</error>"))
        assertEquals(0, Regex("Generic file operation failure").findAll(markup).count())
    }

    @Test
    fun sameErrorFromDifferentSourcesIsNotCollapsed() {
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("read_file", false, StringResultData("[linux] Not found"), error = "[android] Not found"),
        )
        assertTrue(markup.contains("[android] Not found\n\n[linux] Not found"))
    }
    @Test
    fun failedPatchKeepsItsSourceAndIndependentDiff() {
        val operation = FileOperationData("apply", "repo:documents", "/a", false, "Patch rejected")
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("apply_file", false,
                FileApplyResultData(operation, "request context", "-old\n+new"),
                error = "Patch rejected"),
        )
        assertTrue(markup.contains("[repo:documents] Patch rejected\n\n-old\n+new\nrequest context"))
        assertEquals(1, Regex("Patch rejected").findAll(markup).count())
    }

    @Test
    fun failedPatchWithoutExtraOutputProducesOnlyItsCanonicalMessage() {
        val operation = FileOperationData("apply", "linux", "/a", false, "Patch rejected")
        val markup = ConversationMarkupManager.formatToolResultForMessage(
            ToolResult("apply_file", false, FileApplyResultData(operation, "Error: Patch rejected"), error = "Patch rejected"),
        )
        assertTrue(markup.contains("<error>[linux] Patch rejected</error>"))
    }
}
