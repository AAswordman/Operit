package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptExpansionStateTest {
    @Test
    fun expandingASectionIsKeptPerConversation() {
        TranscriptExpansionState.expand("chat-a", "process-section-1")
        TranscriptExpansionState.toggle("chat-a", "process-section-2")

        assertTrue(TranscriptExpansionState.isExpanded("chat-a", "process-section-1"))
        assertTrue(TranscriptExpansionState.isExpanded("chat-a", "process-section-2"))
        // A section opened in one conversation must not follow the user into another one.
        assertFalse(TranscriptExpansionState.isExpanded("chat-b", "process-section-1"))
        assertFalse(TranscriptExpansionState.isExpanded(null, "process-section-1"))

        TranscriptExpansionState.toggle("chat-a", "process-section-1")
        assertFalse(TranscriptExpansionState.isExpanded("chat-a", "process-section-1"))
        assertTrue(TranscriptExpansionState.isExpanded("chat-a", "process-section-2"))
    }

    @Test
    fun collapsingASectionTwiceStaysCollapsed() {
        TranscriptExpansionState.setExpanded("chat-c", "agent-event-7", true)
        TranscriptExpansionState.expand("chat-c", "agent-event-7")
        assertTrue(TranscriptExpansionState.isExpanded("chat-c", "agent-event-7"))

        TranscriptExpansionState.setExpanded("chat-c", "agent-event-7", false)
        assertFalse(TranscriptExpansionState.isExpanded("chat-c", "agent-event-7"))
        // Collapsing an id that was never opened stays a no-op.
        TranscriptExpansionState.setExpanded("chat-c", "agent-event-8", false)
        assertFalse(TranscriptExpansionState.isExpanded("chat-c", "agent-event-8"))
    }

    @Test
    fun deletingAConversationDropsOnlyThatConversationsSections() {
        TranscriptExpansionState.expand("chat-d", "process-section-1")
        TranscriptExpansionState.expand("chat-e", "process-section-1")

        TranscriptExpansionState.clear("chat-d")

        assertFalse(TranscriptExpansionState.isExpanded("chat-d", "process-section-1"))
        assertTrue(TranscriptExpansionState.isExpanded("chat-e", "process-section-1"))
    }
}
