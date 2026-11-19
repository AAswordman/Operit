package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.*
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.*
import org.junit.Test

class TranscriptMarkdownRowsTest {
    private val message = ChatMessage(timestamp = 20, sender = "ai", content = "x".repeat(5000))
    private val nodes = listOf(
        MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, "<think>${"analysis".repeat(400)}</think>", emptyList()),
        MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, "<tool name=\"read_file\">${"param".repeat(600)}</tool>", emptyList()),
        MarkdownNodeStable(MarkdownProcessorType.PLAIN_TEXT, "answer", emptyList()),
    )
    private val document = TranscriptMarkdownDocument(nodes, nodes.indices.map { MarkdownGroupedItem.Single(it) },
        listOf(null, 0, null), 1)
    private val row = TranscriptRow("message:20", 0, ResponseMessageSection.ALL)

    @Test fun splitsWithoutChangingOriginalMessageOrInvocationIndices() {
        val rows = transcriptMarkdownRows(listOf(row), listOf(message),
            mapOf(20L to TranscriptDocumentEntry(message.content, document)),
            true, false, { false }, {})
        assertEquals(3, rows.size)
        assertEquals("message:20", rows.first().key)
        assertTrue(rows.first().markdownSlice!!.first)
        assertTrue(rows.last().markdownSlice!!.last)
        assertSame(document, rows[1].markdownSlice!!.document)
        assertEquals(0, rows[1].markdownSlice!!.document.invocationIndices[1])
        assertEquals(5000, message.content.length)
    }

    @Test fun collapsedTurnAlsoHidesProcessInsideItsFinalMessage() {
        val group = ResponseProcessGroup(10, 0, 0, 10)
        val rows = transcriptMarkdownRows(listOf(row.copy(group = group)), listOf(message),
            mapOf(20L to TranscriptDocumentEntry(message.content, document)),
            true, true, { false }, {}, { false })
        assertEquals(1, rows.size)
        assertEquals(MarkdownGroupedItem.Single(2), rows.single().markdownSlice!!.item)
    }

    @Test fun expandedToolGroupProjectsChildrenAsSeparateRows() {
        val grouped = document.copy(items = listOf(
            MarkdownGroupedItem.Group(0, 1, "think-tools-0"), MarkdownGroupedItem.Single(2)))
        val rows = transcriptMarkdownRows(listOf(row), listOf(message),
            mapOf(20L to TranscriptDocumentEntry(message.content, grouped)),
            true, false, { true }, {})
        assertEquals(4, rows.size)
        assertTrue(rows[1].markdownSlice!!.indented)
        assertEquals(MarkdownGroupedItem.Single(1), rows[2].markdownSlice!!.item)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test fun preparingMessageKeepsStableFirstKey() {
        val rows = transcriptMarkdownRows(listOf(row), listOf(message), emptyMap(),
            true, false, { false }, {})
        assertEquals("message:20", rows.single().key)
        assertTrue(rows.single().preparingMarkdown)
    }

    @Test fun preparingNeighborDoesNotInvalidateExistingSlices() {
        val neighbor = message.copy(timestamp = 30)
        val base = listOf(row, row.copy(key = "message:30", messageIndex = 1))
        val messages = listOf(message, neighbor)
        val entry = TranscriptDocumentEntry(message.content, document)
        var toggled: String? = null
        val toggle: (String) -> Unit = { toggled = it }
        val before = transcriptMarkdownRows(base, messages, mapOf(20L to entry),
            true, true, { true }, toggle).filter { it.messageIndex == 0 }
        val after = transcriptMarkdownRows(base, messages, mapOf(20L to entry, 30L to entry),
            true, true, { true }, toggle).filter { it.messageIndex == 0 }
        assertEquals(before, after)
        after.first().markdownSlice!!.toggle()
        assertEquals("message:20:activity", toggled)
    }
}
