package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.model.MemoryAutoSaveOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryAutoSaveRunSummaryTest {

    @Test
    fun noProcessedGroupsIsIdle() {
        assertEquals(MemoryAutoSaveOutcome.IDLE, MemoryAutoSaveRunSummary().outcome())
    }

    @Test
    fun noMemoryExtractionIsNotReportedAsSaved() {
        val summary = MemoryAutoSaveRunSummary()
        summary.recordNoMemory()
        assertEquals(MemoryAutoSaveOutcome.NO_MEMORY_EXTRACTED, summary.outcome())
    }

    @Test
    fun anySavedGroupIsReportedAsSaved() {
        val summary = MemoryAutoSaveRunSummary()
        summary.recordNoMemory()
        summary.recordSaved()
        assertEquals(MemoryAutoSaveOutcome.SAVED, summary.outcome())
    }

    @Test
    fun failureTakesPriorityOverOtherGroups() {
        val summary = MemoryAutoSaveRunSummary()
        summary.recordSaved()
        summary.recordFailed()
        summary.recordNoMemory()
        assertEquals(MemoryAutoSaveOutcome.FAILED, summary.outcome())
    }
}
