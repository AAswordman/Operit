package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class LocatorVisibleEntriesTest {
    @Test fun hiddenEventLocatesNearbyReplyInsteadOfMinusOne() {
        val entries = listOf(
            preview("ASSISTANT_INTERMEDIATE").copy(timestamp = 10),
            preview("COLLABORATION_EVENT").copy(timestamp = 20),
            preview("NORMAL").copy(timestamp = 30),
        )
        assertEquals(1, locatorCurrentVisiblePosition(entries, 20))
        assertEquals(0, locatorCurrentVisiblePosition(entries, 10))
        assertEquals(1, locatorCurrentVisiblePosition(entries, 30))
    }

    @Test fun hiddenTailAndRemovedAnchorStayNearTheirReadingPosition() {
        val entries = listOf(
            preview("NORMAL").copy(timestamp = 10),
            preview("NORMAL").copy(timestamp = 30),
            preview("COLLABORATION_EVENT").copy(timestamp = 40),
        )
        assertEquals(1, locatorCurrentVisiblePosition(entries, 40))
        assertEquals(1, locatorCurrentVisiblePosition(entries, 29))
        assertEquals(-1, locatorCurrentVisiblePosition(emptyList(), 29))
        assertEquals(-1, locatorCurrentVisiblePosition(listOf(preview("COLLABORATION_EVENT")), 1))
    }

    @Test fun agentInputsAreExcludedWithoutHidingHumanSteering() {
        val entries = listOf(
            preview("NORMAL"),
            preview("COLLABORATION_EVENT"),
            preview("COLLABORATION_TASK"),
            preview("ASSISTANT_INTERMEDIATE").copy(sender = "ai"),
            preview("NORMAL"),
        )
        val visible = locatorVisibleEntries(entries)
        assertEquals(listOf("user", "ai", "user"), visible.map { it.sender })
        assertEquals(listOf(0, 3, 4), visible.map { it.messageIndex })
    }

    @Test fun searchResultsPreserveDatabaseIndicesAndNormalPlaceholders() {
        val entries = listOf(
            preview("COLLABORATION_EVENT").copy(messageIndex = 22),
            preview("HIDDEN_PLACEHOLDER").copy(messageIndex = 30),
            preview("future-mode").copy(messageIndex = 42),
        )
        assertEquals(listOf(30, 42), locatorVisibleEntries(entries).map { it.messageIndex })
    }

    private fun preview(mode: String) = ChatMessageLocatorPreview(
        timestamp = 1, sender = "user", previewContent = "content", contentLength = 7,
        displayMode = mode, isFavorite = false,
    )
}
