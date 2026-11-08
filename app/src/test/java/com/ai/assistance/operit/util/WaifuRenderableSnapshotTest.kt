package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Test

class WaifuRenderableSnapshotTest {
    private val closed = intArrayOf(-1, 0, 0)

    @Test
    fun liveSnapshotHoldsOpenCodeTableAndImageBlocks() {
        val prefix = "before。"
        val content = prefix + "block content"
        for (type in listOf(
            MarkdownProcessorType.CODE_BLOCK,
            MarkdownProcessorType.TABLE,
            MarkdownProcessorType.IMAGE,
        )) {
            val events = intArrayOf(
                MarkdownProcessorType.PLAIN_TEXT.ordinal, 0, prefix.length,
                type.ordinal, prefix.length, content.length,
            )
            assertEquals(
                type.name,
                prefix,
                WaifuMessageProcessor.buildRenderableSnapshot(content, false, events),
            )
            assertEquals(
                type.name,
                content,
                WaifuMessageProcessor.buildRenderableSnapshot(content, false, events + closed),
            )
            assertEquals(
                type.name,
                content,
                WaifuMessageProcessor.buildRenderableSnapshot(content, true, events),
            )
        }
    }

    @Test
    fun switchingBlockTypeClosesThePreviousProtectedBlock() {
        val content = "codeafter。"
        val events = intArrayOf(
            MarkdownProcessorType.CODE_BLOCK.ordinal, 0, 4,
            MarkdownProcessorType.PLAIN_TEXT.ordinal, 4, content.length,
        )
        assertEquals(content, WaifuMessageProcessor.buildRenderableSnapshot(content, false, events))
    }

    @Test
    fun latexDelimitersAreRestoredOnlyWhenTheBlockCloses() {
        val content = "\$\$x^2\$\$"
        val events = intArrayOf(MarkdownProcessorType.BLOCK_LATEX.ordinal, 2, content.length - 2)
        assertEquals("", WaifuMessageProcessor.buildRenderableSnapshot(content, false, events))
        assertEquals(
            content,
            WaifuMessageProcessor.buildRenderableSnapshot(content, false, events + closed),
        )
        assertEquals(content, WaifuMessageProcessor.buildRenderableSnapshot(content, true, events))
    }

    @Test
    fun internalXmlIsFilteredAfterItsNativeBlockCloses() {
        val content = "<tool>private</tool>after。"
        val xmlEnd = content.indexOf("after")
        val events = intArrayOf(
            MarkdownProcessorType.XML_BLOCK.ordinal, 0, xmlEnd,
            -1, 0, 0,
            MarkdownProcessorType.PLAIN_TEXT.ordinal, xmlEnd, content.length,
        )
        assertEquals("after。", WaifuMessageProcessor.buildRenderableSnapshot(content, true, events))
    }

    @Test
    fun emptySnapshotProducesNoContent() {
        assertEquals("", WaifuMessageProcessor.buildRenderableSnapshot("", true, intArrayOf()))
    }
}
