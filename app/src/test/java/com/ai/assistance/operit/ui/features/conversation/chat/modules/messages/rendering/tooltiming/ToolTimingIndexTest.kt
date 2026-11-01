package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming

import com.ai.assistance.operit.core.chat.tools.timing.ToolExecutionTiming
import com.ai.assistance.operit.data.model.tooltiming.ToolCallExecution
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.*
import org.junit.Test

class ToolTimingIndexTest {
    private fun node(xml: String) = MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, xml, emptyList())
    private fun tool() = node("<tool name=\"read_file\"></tool>")
    private fun result(closed: Boolean = true) = node(
        "<tool_result name=\"read_file\" status=\"success\"><content>正文</content>" +
            if (closed) "</tool_result>" else ""
    )
    private fun record(index: Int, start: Long, duration: Long? = null, end: Long? = null, stopped: Boolean = false) =
        ToolCallExecution(index, ToolExecutionTiming.clockId, start, duration, end, stopped)

    @Test
    fun parallelSameNameCallsKeepOwnersAndCountOverlapOnce() {
        val nodes = listOf(tool(), tool(), result(), result())
        val records = listOf(record(1, 1_000L, 2_000L), record(0, 1_000L, 5_000L))
        val saved = ToolTimingIndex.parse(nodes, records)
        assertEquals(5_000L, saved.group(0, nodes.lastIndex).elapsedMillis(100_000L, false))
        assertEquals(2_000L, saved.tool(1)?.elapsedMillis(100_000L, false))
        assertTrue(saved.group(0, nodes.lastIndex).complete)
    }

    @Test
    fun multipleBatchesExcludeTimeSpentGeneratingNextCall() {
        val nodes = listOf(tool(), result(), node("<think>模型继续思考</think>"), tool(), result())
        val records = listOf(record(0, 1_000L, 2_000L), record(1, 20_000L, 3_000L))
        assertEquals(5_000L, ToolTimingIndex.parse(nodes, records).group(0, nodes.lastIndex).elapsedMillis(90_000L, false))
    }

    @Test
    fun liveTimerAdvancesWithoutNewMarkdownAndStopsAfterCompletion() {
        val nodes = listOf(tool())
        val running = checkNotNull(ToolTimingIndex.parse(nodes, listOf(record(0, 1_000L))).tool(0))
        assertEquals(3_000L, running.elapsedMillis(4_000L, true))
        assertEquals(8_000L, running.elapsedMillis(9_000L, true))
        assertTrue(running.ticking(true))
        assertNull(running.elapsedMillis(4_000L, false))
        val fixed = checkNotNull(ToolTimingIndex.parse(nodes + result(), listOf(record(0, 1_000L, 5_000L))).tool(0))
        assertEquals(5_000L, fixed.elapsedMillis(500_000L, false))
        assertFalse(fixed.ticking(true))
    }

    @Test
    fun cancellationFreezesPartialDurationWithoutClaimingCompletion() {
        val summary = checkNotNull(ToolTimingIndex.parse(listOf(tool()), listOf(record(0, 1_000L, end = 4_000L, stopped = true))).tool(0))
        assertTrue(summary.stopped)
        assertFalse(summary.complete)
        assertEquals(3_000L, summary.elapsedMillis(100_000L, false))
    }

    @Test
    fun legacyResultKeepsCompletionButDoesNotInventDuration() {
        val summary = checkNotNull(ToolTimingIndex.parse(listOf(tool(), result())).tool(0))
        assertTrue(summary.complete)
        assertNull(summary.elapsedMillis(100_000L, false))
    }

    @Test
    fun distinctClockDomainsDoNotMergePersistedIntervals() {
        val summary = ToolTimingSummary(listOf(
            ToolCallTiming(0, true, "previous-process", 1_000L, 2_000L),
            ToolCallTiming(1, true, "current-process", 1_000L, 3_000L),
        ))
        assertEquals(5_000L, summary.elapsedMillis(100_000L, false))
    }

    @Test
    fun truncatedResultCannotMarkTheBlockComplete() {
        val nodes = listOf(tool(), result(false))
        assertFalse(checkNotNull(ToolTimingIndex.parse(nodes, listOf(record(0, 1_000L, 2_000L))).tool(0)).complete)
    }
    @Test
    fun parallelSubsecondGroupRetainsMillisecondsInsteadOfSummingOverlaps() {
        val nodes = listOf(tool(), tool(), result(), result())
        val saved = ToolTimingIndex.parse(nodes, listOf(record(0, 10_000L, 175L), record(1, 10_050L, 125L)))
        assertEquals(175L, saved.group(0, nodes.lastIndex).elapsedMillis(100_000L, false))
        assertEquals(125L, saved.tool(1)?.elapsedMillis(100_000L, false))
        assertTrue(saved.group(0, nodes.lastIndex).complete)
    }
}
