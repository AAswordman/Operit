package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessagePageMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDisplayPageMetadataTest {
    @Test
    fun latestPageSelectsTenMessagesFromEightyMetadataRows() {
        val messages = (1L..80L).map { timestamp ->
            ChatMessagePageMetadata(
                sender = if (timestamp % 2L == 1L) "user" else "ai",
                timestamp = timestamp,
            )
        }
        val latest = resolveDisplayPageRangesFromMetadata(messages).last()
        assertEquals(71L, latest.startTimestampInclusive)
        assertEquals(80L, latest.endTimestampInclusive)
        assertEquals(10, latest.endIndexExclusive - latest.startIndex)
    }

    @Test
    fun summaryStartsPageBeforeFiveUserMessages() {
        val messages = listOf("user", "ai", "user", "ai", "summary", "ai", "user", "ai")
            .mapIndexed { index, sender -> ChatMessagePageMetadata(sender, index.toLong() + 1L) }
        val pages = resolveDisplayPageRangesFromMetadata(messages)
        assertEquals(2, pages.size)
        assertEquals(5L, pages.last().startTimestampInclusive)
        assertEquals(8L, pages.last().endTimestampInclusive)
        assertEquals(4L, pages.first().endTimestampInclusive)
    }

    @Test
    fun emptyAndAiOnlyHistoriesKeepTheirBoundaries() {
        assertTrue(resolveDisplayPageRangesFromMetadata(emptyList()).isEmpty())
        val messages = (1L..3L).map { ChatMessagePageMetadata("ai", it) }
        val page = resolveDisplayPageRangesFromMetadata(messages).single()
        assertEquals(1L, page.startTimestampInclusive)
        assertEquals(3L, page.endTimestampInclusive)
    }
}
