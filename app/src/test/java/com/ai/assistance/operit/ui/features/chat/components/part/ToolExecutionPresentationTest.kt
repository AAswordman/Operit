package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.tools.ToolExecutionTimingSnapshot
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.ui.common.markdown.toolInvocationIndexAt
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.StreamLogger
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolExecutionPresentationTest {
    @Test
    fun aSpawnRowReportsTheDispatchRatherThanWhatTheRunBecame() {
        // A spawn call that ran is the hand-over itself: a run that later failed, finished or was
        // stopped by an app restart is not this row's business, so the row never reads its state.
        for (state in ToolExecutionState.values()) {
            assertFalse(subagentSpawnCallNeverRan(runExists = true, fallbackState = state))
            assertEquals(
                state == ToolExecutionState.NOT_EXECUTED,
                subagentSpawnCallNeverRan(runExists = false, fallbackState = state),
            )
        }
        // Only a call that never started is a failure the spawn row owns.
        assertTrue(subagentSpawnCallNeverRan(false, ToolExecutionState.NOT_EXECUTED))
        assertFalse(subagentSpawnCallNeverRan(false, ToolExecutionState.COMPLETED))
        assertFalse(subagentSpawnCallNeverRan(false, ToolExecutionState.RUNNING))
    }

    @Test
    fun steeredAssistantUsesNewScopeAndMessageLocalResultIndices() {
        val sequence = com.ai.assistance.operit.core.chat.AssistantToolSequence("before")
        repeat(4) { sequence.nextIndex.getAndIncrement() }
        sequence.startMessage("after")
        assertEquals("after", sequence.scopeId)
        val index = sequence.nextIndex.getAndIncrement()
        val result = ToolResult(
            toolName = "grep_code", success = true, result = StringResultData("matching lines"),
            callId = "after-call", invocationIndex = index, isFinal = true,
            executionState = ToolExecutionState.COMPLETED,
        )
        val markup = com.ai.assistance.operit.api.chat.enhance.ConversationMarkupManager
            .formatToolResultForMessage(result)
        val content = """<tool name="grep_code"></tool>""" + markup
        assertEquals(0, index)
        assertEquals("matching lines", parsePersistedToolExecutions(content).getValue(0).resultText)
        assertFalse(shouldRenderStandaloneToolResult(markup))
        sequence.startMessage("third")
        assertEquals(0, sequence.nextIndex.getAndIncrement())
        assertEquals("third", sequence.scopeId)
    }

    @Test
    fun previouslyStoredSteeredResultsAreMatchedWithoutChangingMessageText() {
        val content = """
            <tool name="grep_code"></tool>
            <tool name="grep_code"></tool>
            <tool_result name="grep_code" final="true" status="success" invocation_index="5"><content>B</content></tool_result>
            <tool_result name="grep_code" final="true" status="success" invocation_index="4"><content>A</content></tool_result>
        """.trimIndent()
        val executions = parsePersistedToolExecutions(content)
        assertEquals("A", executions.getValue(0).resultText)
        assertEquals("B", executions.getValue(1).resultText)
    }

    @Test
    fun reversedSameNameResults_areMatchedByInvocationIndex() {
        val content =
            """
            <tool name="read_file"><param name="path">/a</param></tool>
            <tool name="read_file"><param name="path">/b</param></tool>
            <tool_result_x name="read_file" status="success" call_id="b" invocation_index="1" duration_ms="2200" execution_state="completed" final="true"><content>B</content></tool_result_x>
            <tool_result_y name="read_file" status="success" call_id="a" invocation_index="0" duration_ms="800" execution_state="completed" final="true"><content>A</content></tool_result_y>
            """.trimIndent()

        val executions = parsePersistedToolExecutions(content)

        assertEquals(800L, executions.getValue(0).durationMs)
        assertEquals("a", executions.getValue(0).callId)
        assertEquals("A", executions.getValue(0).resultText)
        assertEquals(2_200L, executions.getValue(1).durationMs)
        assertEquals("b", executions.getValue(1).callId)
        assertEquals("B", executions.getValue(1).resultText)
    }

    @Test
    fun notExecutedResult_hasNoSyntheticDuration() {
        val content =
            """<tool_result_x name="write_file" status="error" call_id="a" invocation_index="0" execution_state="not_executed" final="true"><content><error>User cancelled</error></content></tool_result_x>"""

        val execution = parsePersistedToolExecutions(content).getValue(0)

        assertEquals(ToolExecutionState.NOT_EXECUTED, execution.state)
        assertNull(execution.durationMs)
    }

    @Test
    fun timedFileResult_keepsStructuredDiffPresentation() {
        val result =
            """
            [android] Updated file
            <file-diff details="Updated file" path="/workspace/src/Main.kt"><![CDATA[
            @@
            -old
            +new
            ]]></file-diff>
            """.trimIndent()

        val diff =
            requireNotNull(
                parseFileDiffResult(
                    toolName = "edit_file",
                    isSuccess = true,
                    result = result,
                )
            )

        assertEquals("/workspace/src/Main.kt", diff.path)
        assertEquals("Updated file", diff.details)
        assertEquals("@@\n-old\n+new", diff.diffContent)
    }

    @Test
    fun subagentTaskResult_extractsFullDecodedFinalText() {
        val result =
            """
            <task id="task-1" state="completed">
              <summary>Inspect auth</summary>
              <task_result>Found &lt;AuthManager&gt; &amp; its callers.
            Second line.</task_result>
            </task>
            """.trimIndent()

        assertEquals(
            "Found <AuthManager> & its callers.\nSecond line.",
            extractSubagentTaskResult(result),
        )
    }

    @Test
    fun subagentTaskRow_separatesAgentNameFromStatusSummary() {
        val content =
            buildSubagentTaskRowContent(
                agentName = "explore",
                durationText = "21.5 秒",
                statusText = "已完成 · 调用了 10 次工具",
            )

        assertEquals("explore", content.title)
        assertEquals("21.5 秒 · 已完成 · 调用了 10 次工具", content.summary)
    }

    @Test
    fun continuedSubagentCard_resolvesRunByStableTaskIdInsteadOfNewCallId() {
        assertEquals(
            SubagentRunLookup.TaskId(taskId = "task-original", parentChatId = "parent"),
            resolveSubagentRunLookup(
                requestedTaskId = "task-original",
                parentChatId = "parent",
                callId = "call-new",
            ),
        )
        assertEquals(
            SubagentRunLookup.ParentCall(parentChatId = "parent", callId = "call-first"),
            resolveSubagentRunLookup(
                requestedTaskId = null,
                parentChatId = "parent",
                callId = "call-first",
            ),
        )
    }

    @Test
    fun toolOrdinal_countsOnlyEarlierToolRequests() {
        val nodes =
            listOf(
                node("<think>plan</think>"),
                node("""<tool name="read_file"></tool>"""),
                node("""<tool_result_x name="read_file"></tool_result_x>"""),
                node("""<tool name="read_file"></tool>"""),
            )

        assertNull(toolInvocationIndexAt(nodes, 0))
        assertEquals(0, toolInvocationIndexAt(nodes, 1))
        assertNull(toolInvocationIndexAt(nodes, 2))
        assertEquals(1, toolInvocationIndexAt(nodes, 3))
    }

    @Test
    fun subagentToolDisplay_keepsLastInvocationWhileThinking() {
        assertEquals(
            "grep_code",
            resolveSubagentDisplayedTool(
                childProcessingState = InputProcessingState.Processing("Thinking"),
                lastToolName = "grep_code",
            ),
        )
    }

    @Test
    fun proxyTaskPresentation_usesForwardedSubagentMetadata() {
        val resolved =
            resolveToolRequestPresentation(
                rawToolName = "proxy",
                params =
                    mapOf(
                        "tool_name" to "task",
                        "params" to
                            """{&quot;subagent_type&quot;:&quot;explore&quot;,&quot;title&quot;:&quot;Trace auth&quot;,&quot;task_id&quot;:&quot;task-1&quot;}""",
                    ),
            )

        assertEquals("task", resolved.toolName)
        assertEquals("explore", resolved.forwardedParams["subagent_type"])
        assertEquals("Trace auth", resolved.forwardedParams["title"])
        assertEquals("task-1", resolved.forwardedParams["task_id"])
    }

    @Test
    fun proxyFileEditPresentation_usesResolvedToolName() {
        val resolved =
            resolveToolRequestPresentation(
                rawToolName = "proxy",
                params = mapOf("tool_name" to "edit_file", "params" to "{}"),
            )

        assertEquals("edit_file", resolved.toolName)
    }

    @Test
    fun toolOrdinal_ignoresMalformedToolRequests() {
        val nodes =
            listOf(
                node("""<tool><param name="path">/bad</param></tool>"""),
                node("""<tool name="read_file"><param name="path">/real</param></tool>"""),
            )

        assertNull(toolInvocationIndexAt(nodes, 0))
        assertEquals(0, toolInvocationIndexAt(nodes, 1))
    }

    @Test
    fun legacyResult_bodyMetadataTextDoesNotHideStandaloneResult() {
        val content =
            """<tool_result name="read_file" status="success"><content>source contains invocation_index="2"</content></tool_result>"""

        assertTrue(shouldRenderStandaloneToolResult(content))
    }

    @Test
    fun onlyValidFinalTimedResultHidesStandaloneResult() {
        assertFalse(
            shouldRenderStandaloneToolResult(
                """<tool_result_x name="read_file" status="success" invocation_index="0" final="true"><content>A</content></tool_result_x>"""
            )
        )
        assertTrue(
            shouldRenderStandaloneToolResult(
                """<tool_result_x name="read_file" status="success" invocation_index="0"><content>A</content></tool_result_x>"""
            )
        )
        assertTrue(
            shouldRenderStandaloneToolResult(
                """<tool_result_x name="read_file" status="success" invocation_index="-1" final="true"><content>A</content></tool_result_x>"""
            )
        )
    }

    @Test
    fun subagentToolDisplay_prefersCurrentToolOverPreviousInvocation() {
        assertEquals(
            "read_file",
            resolveSubagentDisplayedTool(
                childProcessingState = InputProcessingState.ExecutingTool("read_file"),
                lastToolName = "grep_code",
            ),
        )
    }

    @Test
    fun subagentToolDisplay_hasNoToolBeforeFirstInvocation() {
        assertNull(
            resolveSubagentDisplayedTool(
                childProcessingState = InputProcessingState.Processing("Thinking"),
                lastToolName = null,
            ),
        )
    }

    @Test
    fun persistedCallIdentityRejectsSnapshotFromAnotherVariant() {
        val live =
            ToolExecutionTimingSnapshot(
                callId = "new-call",
                toolName = "read_file",
                state = ToolExecutionState.COMPLETED,
            )
        val currentPersisted = persistedExecution(callId = "new-call")
        val oldPersisted = persistedExecution(callId = "old-call")

        assertSame(
            live,
            resolveLiveToolExecution(
                liveExecution = live,
                persistedExecution = null,
                allowUnmatchedLiveExecution = true,
            ),
        )
        assertNull(
            resolveLiveToolExecution(
                liveExecution = live,
                persistedExecution = null,
                allowUnmatchedLiveExecution = false,
            )
        )
        assertSame(
            live,
            resolveLiveToolExecution(
                liveExecution = live,
                persistedExecution = currentPersisted,
                allowUnmatchedLiveExecution = false,
            ),
        )
        assertNull(
            resolveLiveToolExecution(
                liveExecution = live,
                persistedExecution = oldPersisted,
                allowUnmatchedLiveExecution = true,
            )
        )
    }

    @Test
    fun invalidatedToolReservation_keepsLaterExecutionAlignedWithRenderedOrdinal() = runBlocking {
        val nextInvocationIndex = AtomicInteger(0)
        val invalidatedContent =
            """
            <think>Example only: <tool name="nested_thought"/></think>
            <tool name="invalidated_complete"><param name="content"><![CDATA[<tool name="nested_example"/>]]></param></tool>
            <tool name="invalidated_self_closing"/>
            ```xml
            <tool name="fenced_example"></tool>
            ```
            <tool><param name="path">/malformed</param></tool>
            <tool name="invalidated_repaired"></tool>
            """.trimIndent()
        val invalidatedInvocationCount =
            try {
                StreamLogger.setEnabled(false)
                ToolExecutionManager.countDisplayedToolInvocations(invalidatedContent)
            } finally {
                StreamLogger.setEnabled(true)
            }
        ToolExecutionManager.reserveToolInvocationIndices(
            nextInvocationIndex,
            invalidatedInvocationCount,
        )
        val validInvocationIndex = nextInvocationIndex.getAndIncrement()
        val nodes =
            listOf(
                node("""<think>Example only: <tool name="nested_thought"/></think>"""),
                node("""<tool name="invalidated_complete"><param name="content"><![CDATA[<tool name="nested_example"/>]]></param></tool>"""),
                node("""<tool name="invalidated_self_closing"/>""", MarkdownProcessorType.PLAIN_TEXT),
                node("""<tool name="fenced_example"></tool>""", MarkdownProcessorType.CODE_BLOCK),
                node("""<tool><param name="path">/malformed</param></tool>"""),
                node("""<tool name="invalidated_repaired"></tool>"""),
                node("""<tool name="read_file"></tool>"""),
            )

        assertEquals(2, invalidatedInvocationCount)
        assertEquals(validInvocationIndex, toolInvocationIndexAt(nodes, 6))
        assertEquals(2, validInvocationIndex)
    }

    @Test
    fun fileEditResults_useStructuredDiffPresentation() {
        listOf("apply_file", "create_file", "edit_file").forEach { toolName ->
            val diff =
                parseFileDiffResult(
                    toolName = toolName,
                    result =
                        """<file-diff path="src/Test.kt" details="updated"><![CDATA[+added
-removed]]></file-diff>""",
                    isSuccess = true,
                )

            assertNotNull(diff)
            assertEquals("src/Test.kt", diff?.path)
            assertEquals("updated", diff?.details)
            assertEquals("+added\n-removed", diff?.diffContent)
        }
        assertNull(
            parseFileDiffResult(
                toolName = "read_file",
                result = """<file-diff path="src/Test.kt"><![CDATA[+added]]></file-diff>""",
                isSuccess = true,
            )
        )
        assertNull(
            parseFileDiffResult(
                toolName = "apply_file",
                result = """<file-diff path="src/Test.kt"><![CDATA[+added]]></file-diff>""",
                isSuccess = false,
            )
        )
        assertNull(
            parseFileDiffResult(
                toolName = "apply_file",
                result = """<file-diff path="src/Test.kt"><![CDATA[+truncated""",
                isSuccess = true,
            )
        )
        assertNull(
            parseFileDiffResult(
                toolName = "apply_file",
                result = """<file-diff path="src/Test.kt"><![CDATA[+changed]]>""",
                isSuccess = true,
            )
        )
        assertNull(
            parseFileDiffResult(
                toolName = "apply_file",
                result = """<file-diff><![CDATA[+added]]></file-diff>""",
                isSuccess = true,
            )
        )
    }

    private fun node(
        content: String,
        type: MarkdownProcessorType = MarkdownProcessorType.XML_BLOCK,
    ) =
        MarkdownNodeStable(
            type = type,
            content = content,
            children = emptyList(),
        )

    private fun persistedExecution(callId: String) =
        PersistedToolExecution(
            callId = callId,
            toolName = "read_file",
            state = ToolExecutionState.COMPLETED,
            durationMs = 100L,
            success = true,
            resultText = "result",
        )

    @Test
    fun theTaskAReaderSeesIsWhatTheCallerHandedOver() {
        assertEquals(
            "Investigate the parser",
            readSubagentCallMessage("spawn_agent", "Investigate the parser"),
        )
        // A raw parameter arrives either wrapped in CDATA or escaped, and both read as the message.
        assertEquals(
            "Check \"quoted\" names",
            readSubagentCallMessage("spawn_agent", "Check &quot;quoted&quot; names"),
        )
        assertEquals(
            "Check <the> file",
            readSubagentCallMessage("spawn_agent", "<![CDATA[Check <the> file]]>"),
        )
        assertEquals("spaced", readSubagentCallMessage("spawn_agent", "  spaced  "))

        // Anything that is not a spawn call has no task of its own, and neither has an empty one.
        assertNull(readSubagentCallMessage("task", "Investigate the parser"))
        assertNull(readSubagentCallMessage("spawn_agent", "   "))
        assertNull(readSubagentCallMessage("spawn_agent", null))
        assertNull(readSubagentCallMessage("spawn_agent", "<![CDATA[]]>"))
    }
}
