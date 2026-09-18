package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.ChatMarkupRegex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

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
        assertEquals("partial\nStep error: failed", failed.result.toString())
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

    private fun result(text: String) = ToolResult("read_file", true, StringResultData(text))
    private fun content(xml: String): String =
        checkNotNull(ChatMarkupRegex.contentTag.find(xml)).groupValues[1]
}
