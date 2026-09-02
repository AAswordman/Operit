package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.stream.StreamCollector
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun resultMarkupIsDeliveredToDisplayPersistenceAndLiveCollector() = runTest {
        val persisted = mutableListOf<String>()
        val live = mutableListOf<String>()
        val markup = "<tool_result_26C6 name=\"read_file_part\" status=\"error\"><content>missing path</content></tool_result_26C6>"

        ToolExecutionManager.emitToolResultMarkup(
            content = markup,
            collector = object : StreamCollector<String> {
                override suspend fun emit(value: String) {
                    live += value
                }
            },
            onDisplayMarkupEmitted = { persisted += it },
        )

        assertEquals(listOf("$markup\n"), persisted)
        assertEquals(persisted, live)
    }

    @Test
    fun parallelResultMarkupEmissionKeepsPersistenceAndLiveOrderAligned() = runTest {
        val persisted = mutableListOf<String>()
        val live = mutableListOf<String>()
        val mutex = Mutex()

        coroutineScope {
            (1..12)
                .map { index ->
                    async {
                        ToolExecutionManager.emitToolResultMarkup(
                            content = "<tool_result_${index} name=\"read_file_part\" status=\"success\"><content>$index</content></tool_result_${index}>",
                            collector = object : StreamCollector<String> {
                                override suspend fun emit(value: String) {
                                    yield()
                                    live += value
                                }
                            },
                            onDisplayMarkupEmitted = { value ->
                                persisted += value
                                yield()
                            },
                            emissionMutex = mutex,
                        )
                    }
                }
                .awaitAll()
        }

        assertEquals(persisted, live)
        assertEquals(12, persisted.size)
    }

    @Test
    fun orderedEmitterDelaysLaterCompletionUntilEarlierInvocationFinishes() = runTest {
        val persisted = mutableListOf<String>()
        val live = mutableListOf<String>()
        val emitter =
            ToolExecutionManager.OrderedToolResultEmitter(
                collector =
                    object : StreamCollector<String> {
                        override suspend fun emit(value: String) {
                            live += value
                        }
                    },
                onDisplayMarkupEmitted = { persisted += it },
            )
        val firstBuffer = ToolExecutionManager.ToolResultMarkupBuffer()
        val secondBuffer = ToolExecutionManager.ToolResultMarkupBuffer()
        firstBuffer.record(
            ToolResult(
                toolName = "grep_context",
                success = true,
                result = StringResultData("first result"),
            ),
        )
        secondBuffer.record(
            ToolResult(
                toolName = "read_file_part",
                success = true,
                result = StringResultData("second result"),
            ),
        )

        emitter.complete(index = 1, buffer = secondBuffer)
        assertTrue(live.isEmpty())

        emitter.complete(index = 0, buffer = firstBuffer)

        assertEquals(persisted, live)
        assertEquals(2, live.size)
        assertTrue(live[0].contains("first result"))
        assertTrue(live[1].contains("second result"))
    }

    @Test
    fun bufferedToolResultMarkupStaysWithinInvocationDisplayBudget() = runTest {
        val live = mutableListOf<String>()
        val buffer = ToolExecutionManager.ToolResultMarkupBuffer()
        buffer.record(
            ToolResult(
                toolName = "read_file",
                success = true,
                result = StringResultData("x".repeat(128 * 1024)),
            ),
        )

        buffer.flushTo(
            collector =
                object : StreamCollector<String> {
                    override suspend fun emit(value: String) {
                        live += value
                    }
                },
            onDisplayMarkupEmitted = {},
            displayBudget = ToolExecutionManager.ToolResultDisplayBudget(),
        )

        assertEquals(1, live.size)
        assertTrue(live.single().length <= 16 * 1024 + 1)
        assertTrue(live.single().contains("[工具结果过长，已截断]"))
    }
    @Test
    fun sharedDisplayBudgetCompactsResultsAfterTheConversationLimit() = runTest {
        val live = mutableListOf<String>()
        val sharedBudget = ToolExecutionManager.ToolResultDisplayBudget()

        repeat(17) { index ->
            val buffer = ToolExecutionManager.ToolResultMarkupBuffer()
            buffer.record(
                ToolResult(
                    toolName = "read_file_$index",
                    success = true,
                    result = StringResultData("x".repeat(16 * 1024)),
                ),
            )
            ToolExecutionManager.OrderedToolResultEmitter(
                collector =
                    object : StreamCollector<String> {
                        override suspend fun emit(value: String) {
                            live += value
                        }
                    },
                onDisplayMarkupEmitted = {},
                displayBudget = sharedBudget,
            ).complete(index = 0, buffer = buffer)
        }

        assertEquals(17, live.size)
        assertTrue(live.last().contains("[工具结果已从聊天显示中省略，以避免长任务占用过多内存。]"))
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
