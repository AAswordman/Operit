package com.ai.assistance.operit.services.core

import org.junit.Assert.*
import org.junit.Test
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageProcessMetadata
import kotlinx.coroutines.flow.MutableStateFlow

class TranscriptWindowPolicyTest {
    private fun message(timestamp: Long, content: String = "") =
        ChatMessage(timestamp = timestamp, sender = "user", content = content)
    private fun metadata(timestamp: Long, mode: String = "NORMAL", completed: Long = 0) =
        ChatMessageProcessMetadata(timestamp, "ai", mode, 10, completed, 0, 0)

    @Test fun olderPagesNeverUnloadTheAlreadyLoadedNewerMessages() {
        val latest = (400L..499L).map { message(it) }
        val older = mergeLoadedTranscriptMessages(latest, (200L..399L).map { message(it) })
        val oldest = mergeLoadedTranscriptMessages(older, (0L..199L).map { message(it) })
        assertEquals((0L..499L).toList(), oldest.map { it.timestamp })
        assertSame(latest.last(), oldest.last())
    }

    @Test fun newerPagesAlsoKeepOldHistoryAndDoNotOverwriteConcurrentUpdates() {
        val current = (0L..399L).map { message(it, "current") }
        val merged = mergeLoadedTranscriptMessages(current, (390L..499L).map { message(it, "fetched") })
        assertEquals(500, merged.size)
        assertSame(current[399], merged[399])
        assertEquals("fetched", merged.last().content)
    }

    @Test fun reloadKeepsTheEntireLoadedRangeNotTwoPages() {
        val structure = TranscriptStructure((0L..600L).map { metadata(it) })
        assertEquals((100L..600L).toList(),
            retainedTranscriptReloadTimestamps(structure, (100L..500L).toList(), false))
        assertEquals((100L..500L).toList(),
            retainedTranscriptReloadTimestamps(structure, (100L..500L).toList(), true))
    }

    @Test fun reloadRetainsOpenedProcessButDoesNotLoadUnopenedOrDeletedBodies() {
        val structure = TranscriptStructure(listOf(
            metadata(1, "ASSISTANT_INTERMEDIATE"),
            metadata(3, "ASSISTANT_INTERMEDIATE"),
            metadata(4, completed = 100),
            metadata(5),
        ))
        assertEquals(listOf(1L, 4L, 5L),
            retainedTranscriptReloadTimestamps(structure, listOf(1L, 2L, 4L), false))
        assertEquals(listOf(4L, 5L),
            retainedTranscriptReloadTimestamps(structure, listOf(4L), false))
    }

    @Test fun staleFailureCannotClearNewLoadingState() {
        val controller = CurrentChatWindowController()
        val old = controller.generation()
        assertTrue(controller.beginLoadingDisplayWindow())
        controller.reset()
        assertTrue(controller.beginLoadingDisplayWindow())
        controller.finishLoadingDisplayWindowFailure(old)
        assertTrue(controller.isLoadingDisplayWindow.value)
        assertFalse(controller.isCurrent(old))
    }

    @Test fun refreshCannotDiscardAPageLoadedDuringItsDatabaseRead() {
        val controller = CurrentChatWindowController()
        val flow = MutableStateFlow(listOf(message(10)))
        val expected = flow.value
        val generation = controller.generation()
        val page = mergeLoadedTranscriptMessages(flow.value, listOf(message(1)))
        controller.applyLoadResult(CurrentChatWindowLoadResult(page, true, false), flow)
        assertFalse(controller.tryApplyLoadResult(
            CurrentChatWindowLoadResult(expected, true, false), flow, generation, expected))
        assertEquals(listOf(1L, 10L), flow.value.map { it.timestamp })
        val retrySnapshot = flow.value
        assertTrue(controller.tryApplyLoadResult(
            CurrentChatWindowLoadResult(listOf(message(1, "edited"), message(10)), true, false),
            flow, generation, retrySnapshot))
        assertEquals("edited", flow.value.first().content)
        controller.reset()
        assertFalse(controller.tryApplyLoadResult(
            CurrentChatWindowLoadResult(expected, false, false), flow, generation))
    }
}
