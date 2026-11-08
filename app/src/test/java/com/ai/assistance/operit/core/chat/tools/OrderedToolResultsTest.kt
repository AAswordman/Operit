package com.ai.assistance.operit.core.chat.tools

import com.ai.assistance.operit.util.toolmarkup.ToolResultMarkup
import com.ai.assistance.operit.core.tools.TerminalCommandResultData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.javascript.JsToolResultJson
import com.ai.assistance.operit.core.tools.javascript.extractJsExecutionFailure
import org.json.JSONObject
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import com.ai.assistance.operit.core.chat.protocol.ConversationMarkupManager

class OrderedToolResultsTest {
    @Test
    fun `same-name calls publish in invocation order despite reversed completion`() = runBlocking {
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(3) { emitted.add(it) }
        val secondFinished = CompletableDeferred<Unit>()
        val first = async {
            secondFinished.await()
            assertTrue(emitted.isEmpty())
            ordered.complete(0, result("a"))
        }
        val second = async {
            ordered.complete(1, result("b"))
            secondFinished.complete(Unit)
        }
        first.await()
        second.await()
        // Publish a ready prefix without waiting for the final call of the batch.
        assertEquals(listOf("a", "b"), emitted.map(::content))
        ordered.complete(2, result("c"))
        val batch = ordered.finish()
        assertEquals(listOf("a", "b", "c"), batch.results.map { it.result.toString() })
        assertEquals(emitted.joinToString("\n"), batch.message)
    }

    @Test
    fun `denials keep original positions and identical calls retain separate slots`() = runBlocking {
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(3) { emitted.add(it) }
        val denied = ToolResult("read_file", false, StringResultData(""), "denied")
        ordered.complete(1, denied)
        ordered.complete(2, result("identical"))
        assertTrue(emitted.isEmpty())
        ordered.complete(0, result("identical"))
        val batch = ordered.finish()
        assertEquals(3, emitted.size)
        assertEquals(listOf(true, false, true), batch.results.map { it.success })
        assertEquals("identical", content(emitted[0]))
        assertEquals("<error>denied</error>", content(emitted[1]))
        assertEquals("identical", content(emitted[2]))
    }

    @Test
    fun `multi-part tool emits one aggregate shared with model history`() = runBlocking {
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(1) { emitted.add(it) }
        val finalResult = ToolExecutionManager.aggregateToolResults(
            "read_file", flowOf(result("first part"), result("second part"))
        )
        ordered.complete(0, finalResult)
        val batch = ordered.finish()
        assertEquals(1, ChatMarkupRegex.toolResultAnyPattern.findAll(batch.message).count())
        assertEquals("first part\nsecond part", content(emitted.single()))
        assertEquals(emitted.single(), batch.message)
    }

    @Test
    fun `empty tool and failed tool both publish a final result`() = runBlocking {
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(2) { emitted.add(it) }
        val empty = ToolExecutionManager.aggregateToolResults("read_file", emptyFlow())
        val failed = ToolExecutionManager.aggregateToolResults(
            "read_file",
            flowOf(result("partial"), ToolResult("read_file", false, StringResultData(""), "failed"))
        )
        ordered.complete(1, failed)
        ordered.complete(0, empty)
        val batch = ordered.finish()
        assertEquals(2, emitted.size)
        assertTrue(batch.results.all { !it.success })
        assertEquals("The tool execution returned no results.", empty.error)
        assertEquals("partial", failed.result.toString())
        assertEquals("<error>failed\n\npartial</error>", content(emitted[1]))
        assertEquals(emitted.joinToString("\n"), batch.message)
    }

    @Test
    fun `cancellation does not fabricate a completed result from partial output`() = runBlocking {
        try {
            ToolExecutionManager.aggregateToolResults("read_file", flow {
                emit(result("partial"))
                throw CancellationException("cancelled")
            })
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    @Test
    fun `empty batch contains no markup`() = runBlocking {
        val batch = OrderedToolResults(0) { error("Unexpected output") }.finish()
        assertTrue(batch.results.isEmpty())
        assertEquals("", batch.message)
    }

    @Test
    fun `tool payload with content tags stays identical in live output and model history`() = runBlocking {
        val payload = "开头</content><content>示例</content>结尾"
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(1) { emitted.add(it) }
        ordered.complete(0, result(payload))
        val batch = ordered.finish()
        assertEquals(payload, content(emitted.single()))
        assertEquals(payload, content(batch.message))
    }

    @Test
    fun `single failed tool publishes its error only once to both histories`() = runBlocking {
        val message = "params must be a valid JSON object. The target tool was not executed."
        val finalResult = ToolExecutionManager.aggregateToolResults(
            "super_admin:terminal",
            flowOf(ToolResult("super_admin:terminal", false, StringResultData(""), message)),
        )
        assertEquals("", finalResult.result.toString())
        assertEquals(message, finalResult.error)
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(1) { emitted.add(it) }
        ordered.complete(0, finalResult)
        val batch = ordered.finish()
        assertEquals("<error>$message</error>", content(emitted.single()))
        assertEquals(emitted.single(), batch.message)
    }

    @Test
    fun `final failure keeps partial output and its distinct diagnostic body`() = runBlocking {
        val finalResult = ToolExecutionManager.aggregateToolResults(
            "shell", flowOf(result("progress"), ToolResult("shell", false, StringResultData("stderr: line 9"), "failed")),
        )
        val formatted = ConversationMarkupManager.formatToolResultForMessage(finalResult)
        assertEquals("<error>failed\n\nprogress\nstderr: line 9</error>", content(formatted))
        assertFalse(finalResult.success)
    }

    @Test
    fun `earlier step failure stays separate from the final error`() = runBlocking {
        val finalResult = ToolExecutionManager.aggregateToolResults(
            "shell", flowOf(
                ToolResult("shell", false, StringResultData(""), "first"),
                result("progress"),
                ToolResult("shell", false, StringResultData(""), "last"),
            ),
        )
        assertEquals("Step error: first\nprogress", finalResult.result.toString())
        assertEquals("<error>last\n\nStep error: first\nprogress</error>",
            content(ConversationMarkupManager.formatToolResultForMessage(finalResult)))
    }

    @Test
    fun `successful final result still retains earlier step errors`() = runBlocking {
        val finalResult = ToolExecutionManager.aggregateToolResults(
            "shell", flowOf(ToolResult("shell", false, StringResultData(""), "first"), result("done")),
        )
        assertTrue(finalResult.success)
        assertEquals("Step error: first\ndone", finalResult.result.toString())
        assertEquals(finalResult.result.toString(), content(ConversationMarkupManager.formatToolResultForMessage(finalResult)))
    }

    @Test
    fun `failure without an error field retains its existing marker`() = runBlocking {
        val finalResult = ToolExecutionManager.aggregateToolResults(
            "shell", flowOf(ToolResult("shell", false, StringResultData(""))),
        )
        assertFalse(finalResult.success)
        assertEquals("Step error: Unknown error", finalResult.result.toString())
    }

    @Test
    fun `nonzero terminal exit preserves diagnostics and metadata in both histories`() = runBlocking {
        val output = "运行前输出\nTraceback: 参数校验失败\n退出前输出"
        val terminal = TerminalCommandResultData(
            command = "diagnostic-command", output = output, exitCode = 2, sessionId = "session",
        )
        val message = "终端命令以非零状态退出（exitCode=2）"
        val finalResult = ToolExecutionManager.aggregateToolResults(
            "terminal_exec", flowOf(ToolResult("terminal_exec", false, terminal, message)),
        )
        assertFalse(finalResult.success)
        assertEquals(message, finalResult.error)
        assertEquals(terminal.toString().trim(), finalResult.result.toString())
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(1) { emitted.add(it) }
        ordered.complete(0, finalResult)
        val batch = ordered.finish()
        val payload = content(emitted.single())
        assertTrue(payload.contains(output))
        assertTrue(payload.contains("Exit Code: 2"))
        assertTrue(payload.contains("Session: session"))
        assertEquals(1, Regex(Regex.escape(message)).findAll(payload).count())
        assertEquals(emitted.single(), batch.message)
    }

    @Test
    fun `terminal package failure keeps its structured output through aggregation`() = runBlocking {
        val output = "stdout: 已读取配置\nstderr: 文件不存在"
        val terminal = TerminalCommandResultData(
            command = "diagnostic-command", output = output, exitCode = 1, sessionId = "package-session",
        )
        val envelope = JSONObject()
            .put("success", false)
            .put("message", "终端命令以非零状态退出（exitCode=1）")
            .put("data", JSONObject(JsToolResultJson.encodeStructured(terminal).toString()))
        val failure = requireNotNull(extractJsExecutionFailure(envelope.toString()))
        val finalResult = ToolExecutionManager.aggregateToolResults(
            "super_admin:terminal",
            flowOf(ToolResult("super_admin:terminal", false, StringResultData(failure.dataText), failure.message)),
        )
        assertFalse(finalResult.success)
        val data = JSONObject(finalResult.result.toString())
        assertEquals(output, data.getString("output"))
        assertEquals(1, data.getInt("exitCode"))
        assertEquals("package-session", data.getString("sessionId"))
        assertEquals(terminal.command, data.getString("command"))
        val formatted = content(ConversationMarkupManager.formatToolResultForMessage(finalResult))
        assertTrue(formatted.contains(finalResult.result.toString()))
        assertEquals(1, Regex(Regex.escape(failure.message)).findAll(formatted).count())
    }

    @Test
    fun structuredFileErrorIsCanonicalBeforeFinalMarkupIsGenerated() = runBlocking {
        val raw = ToolResult("create_file", false,
            FileOperationData("create", "android", "/a", false, "File exists"), error = "File exists")
        val finalResult = ToolExecutionManager.aggregateToolResults("create_file", flowOf(raw))
        assertEquals("[android] File exists", finalResult.error)
        assertEquals("", finalResult.result.toString())
        val emitted = mutableListOf<String>()
        val ordered = OrderedToolResults(1) { emitted += it }
        ordered.complete(0, finalResult)
        val batch = ordered.finish()
        assertEquals("<error>[android] File exists</error>", content(batch.message))
        assertEquals(emitted.single(), batch.message)
    }

    @Test
    fun structuredErrorsFromDifferentStepsKeepTheirOwnSources() = runBlocking {
        val first = ToolResult("write_file", false,
            FileOperationData("write", "android", "/a", false, "Denied"), error = "Denied")
        val last = ToolResult("write_file", false,
            FileOperationData("write", "linux", "/a", false, "Denied"), error = "Denied")
        val finalResult = ToolExecutionManager.aggregateToolResults("write_file", flowOf(first, result("progress"), last))
        assertEquals("[linux] Denied", finalResult.error)
        assertEquals("Step error: [android] Denied\nprogress", finalResult.result.toString())
        assertEquals("<error>[linux] Denied\n\nStep error: [android] Denied\nprogress</error>",
            content(ConversationMarkupManager.formatToolResultForMessage(finalResult)))
    }

    @Test
    fun earlierFailureRetainsIndependentCommandOutput() = runBlocking {
        val first = ToolResult("shell", false, StringResultData("stderr: details"), error = "first")
        val finalResult = ToolExecutionManager.aggregateToolResults("shell", flowOf(first, result("done")))
        assertTrue(finalResult.success)
        assertEquals("Step error: first\n\nstderr: details\ndone", finalResult.result.toString())
    }

    private fun result(text: String) = ToolResult("read_file", true, StringResultData(text))
    private fun content(xml: String): String =
        ToolResultMarkup.contentFromBlock(xml)
}
