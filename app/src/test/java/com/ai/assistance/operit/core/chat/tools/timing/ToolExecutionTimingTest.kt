package com.ai.assistance.operit.core.chat.tools.timing

import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.core.chat.tools.timing.ToolTimingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ToolExecutionTimingTest {
    @Test
    fun permissionWaitIsExcludedFromExecution() = runBlocking {
        var now = 10_000L
        val emitted = mutableListOf<ToolCallExecution>()
        val timing = ToolExecutionTiming(1, { emitted += it }, now = { now })
        timing.queued()
        now = 60_000L
        timing.start(0)
        now = 62_500L
        timing.completed(0)
        timing.finish()
        assertEquals(2_500L, emitted.last().durationMs)
        assertFalse(emitted.last().stopped)
    }

    @Test
    fun rejectedInvocationHasNoExecutionInterval() = runBlocking {
        val emitted = mutableListOf<ToolCallExecution>()
        val timing = ToolExecutionTiming(1, { emitted += it }, now = { 50_000L })
        timing.queued()
        timing.completed(0)
        timing.finish()
        assertTrue(emitted.all { it.startedMs == null })
        assertEquals(0L, emitted.last().durationMs)
    }

    @Test
    fun cancellationFreezesUnfinishedCallAndLeavesCompletedCallIntact() = runBlocking {
        var now = 4_000L
        val emitted = mutableListOf<ToolCallExecution>()
        val timing = ToolExecutionTiming(2, { emitted += it }, now = { now })
        timing.queued()
        timing.start(0)
        timing.start(1)
        now = 6_000L
        timing.completed(0)
        now = 8_000L
        timing.finish()
        assertEquals(2_000L, emitted.last { it.callIndex == 0 }.durationMs)
        assertFalse(emitted.last { it.callIndex == 0 }.stopped)
        assertTrue(emitted.last { it.callIndex == 1 }.stopped)
        assertEquals(8_000L, emitted.last().endedMs)
        assertNull(emitted.last().durationMs)
    }

    @Test
    fun laterBatchUsesDistinctCallIndicesAndStateRetainsEarlierResults() = runBlocking {
        val state = ToolTimingState()
        val first = ToolExecutionTiming(1, { state.update(it) }, now = { 1_000L })
        first.queued()
        first.start(0)
        first.completed(0)
        val second = ToolExecutionTiming(2, { state.update(it) }, callOffset = 1, now = { 8_000L })
        second.queued()
        second.start(1)
        second.completed(1)
        assertEquals(listOf(0, 1, 2), state.records.value.map { it.callIndex })
        assertNotNull(state.records.value[0].durationMs)
        assertNull(state.records.value[1].durationMs)
        assertNotNull(state.records.value[2].durationMs)
    }
    @Test
    fun stoppingStateRejectsLateCompletionWithoutLosingCompletedCalls() {
        val state = ToolTimingState()
        state.update(ToolCallExecution(0, "clock", 1_000L, 2_000L, 3_000L))
        state.update(ToolCallExecution(1, "clock", 1_000L))
        state.stopActive(4_000L)
        state.update(ToolCallExecution(1, "clock", 1_000L, 9_000L, 10_000L))
        assertEquals(2_000L, state.records.value[0].durationMs)
        assertEquals(4_000L, state.records.value[1].endedMs)
        assertTrue(state.records.value[1].stopped)
        assertNull(state.records.value[1].durationMs)
    }
    @Test
    fun subsecondExecutionIsStoredWithoutSecondRounding() = runBlocking {
        var now = 10_000L
        val state = ToolTimingState()
        val timing = ToolExecutionTiming(1, { state.update(it) }, now = { now })
        timing.queued()
        timing.start(0)
        now += 175L
        timing.completed(0)
        assertEquals(175L, state.records.value.single().durationMs)
    }
}
