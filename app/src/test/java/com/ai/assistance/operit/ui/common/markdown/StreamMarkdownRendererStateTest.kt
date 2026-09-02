package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMarkdownRendererStateTest {
    @Test
    fun `releasing completed xml stream clears replay content`() = runTest {
        val stream = MutableSharedStream<String>(replay = Int.MAX_VALUE)
        stream.emit("completed tool payload")
        val xmlNodeStreams = mutableMapOf<Int, Stream<String>>(7 to stream)

        releaseXmlNodeStream(xmlNodeStreams, 7)

        assertTrue(xmlNodeStreams.isEmpty())
        assertTrue(stream.replayCache.isEmpty())
    }

    @Test
    fun `same length replacement does not reuse a stale render node`() {
        val conversionCache = mutableMapOf<Int, StableNodeConversionCacheEntry>()
        val thinkingNode = MarkdownNode(
            type = MarkdownProcessorType.XML_BLOCK,
            initialContent = "<think>a</think>",
        )
        val toolResultNode = MarkdownNode(
            type = MarkdownProcessorType.XML_BLOCK,
            initialContent = "<tool>abc</tool>",
        )

        assertEquals(thinkingNode.content.length, toolResultNode.content.length)
        val first = stableNodeForRender(thinkingNode, 0, conversionCache)
        val replacement = stableNodeForRender(toolResultNode, 0, conversionCache)

        assertNotEquals(first.nodeId, replacement.nodeId)
        assertEquals(toolResultNode.nodeId, replacement.nodeId)
        assertEquals(toolResultNode.content.toString(), replacement.content)
    }
}
