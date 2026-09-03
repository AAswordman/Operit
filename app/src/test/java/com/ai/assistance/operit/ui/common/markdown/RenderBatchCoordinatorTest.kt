package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RenderBatchCoordinatorTest {
    @Test
    fun requestWhileBatchIsPending_isIncludedWithoutAnotherInput() = runTest {
        var flushCount = 0
        val coordinator =
            RenderBatchCoordinator(
                scope = this,
                intervalMs = 200,
                onFlush = { flushCount++ },
            )

        coordinator.requestUpdate()
        advanceTimeBy(100)
        coordinator.requestUpdate()
        advanceUntilIdle()

        assertEquals(1, flushCount)
    }
    @Test
    fun flushNow_appliesPendingUpdateWithoutWaiting() = runTest {
        var flushCount = 0
        val coordinator =
            RenderBatchCoordinator(
                scope = this,
                intervalMs = 200,
                onFlush = { flushCount++ },
            )

        coordinator.requestUpdate()
        coordinator.flushNow()
        advanceUntilIdle()

        assertEquals(1, flushCount)
    }

    @Test
    fun requestDuringFlush_isDrainedBeforeCoordinatorBecomesIdle() = runTest {

        var flushCount = 0
        lateinit var coordinator: RenderBatchCoordinator
        coordinator =
            RenderBatchCoordinator(
                scope = this,
                intervalMs = 200,
                onFlush = {
                    flushCount++
                    if (flushCount == 1) {
                        coordinator.requestUpdate()
                    }
                },
            )

        coordinator.requestUpdate()
        advanceUntilIdle()

        assertEquals(2, flushCount)
    }

    @Test
    fun toolXmlTailMutation_isRenderedWithoutAnotherInput() = runTest {
        val nodes = mutableStateListOf<MarkdownNode>()
        val renderNodes = mutableStateListOf<MarkdownNodeStable>()
        val nodeAnimationStates = mutableStateMapOf<String, Boolean>()
        val updater =
            BatchNodeUpdater(
                nodes = nodes,
                renderNodes = renderNodes,
                conversionCache = mutableMapOf(),
                nodeAnimationStates = nodeAnimationStates,
                xmlNodeStreams = mutableMapOf<Int, Stream<String>>(),
                rendererId = "tool-xml-test",
                scope = this,
            )
        val toolNode = MarkdownNode(type = MarkdownProcessorType.XML_BLOCK)
        nodes.add(toolNode)

        val prefix = "<tool name=\"shell\"><param name=\"command\">echo par"
        updater.appendBlockChunk(toolNode, prefix)
        advanceUntilIdle()
        assertEquals(prefix, renderNodes.single().content)
        assertEquals(toolNode.nodeId, renderNodes.single().nodeId)

        val tail = "tial</param></tool>"
        updater.appendBlockChunk(toolNode, tail)
        advanceUntilIdle()

        assertEquals(prefix + tail, renderNodes.single().content)
    }

    @Test
    fun equalLengthBlockNodeReplacement_isRendered() = runTest {
        val nodes = mutableStateListOf(MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, "x"))
        val renderNodes = mutableStateListOf<MarkdownNodeStable>()
        val updater =
            BatchNodeUpdater(
                nodes = nodes,
                renderNodes = renderNodes,
                conversionCache = mutableMapOf(),
                nodeAnimationStates = mutableStateMapOf(),
                xmlNodeStreams = mutableMapOf<Int, Stream<String>>(),
                rendererId = "equal-length-block-test",
                scope = this,
            )

        updater.requestUpdate()
        advanceUntilIdle()

        nodes[0] = MarkdownNode(MarkdownProcessorType.BLOCK_LATEX, "x")
        updater.requestStructuralUpdate(0)
        advanceUntilIdle()

        assertEquals(MarkdownProcessorType.BLOCK_LATEX, renderNodes.single().type)
    }

    @Test
    fun equalLengthChildNodeReplacement_isRendered() = runTest {
        val parent = MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, "x")
        parent.children.add(MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, "x"))
        val nodes = mutableStateListOf(parent)
        val renderNodes = mutableStateListOf<MarkdownNodeStable>()
        val updater =
            BatchNodeUpdater(
                nodes = nodes,
                renderNodes = renderNodes,
                conversionCache = mutableMapOf(),
                nodeAnimationStates = mutableStateMapOf(),
                xmlNodeStreams = mutableMapOf<Int, Stream<String>>(),
                rendererId = "equal-length-child-test",
                scope = this,
            )

        updater.requestUpdate()
        advanceUntilIdle()

        parent.children[0] = MarkdownNode(MarkdownProcessorType.INLINE_LATEX, "x")
        updater.requestStructuralUpdate(0)
        advanceUntilIdle()

        assertEquals(MarkdownProcessorType.INLINE_LATEX, renderNodes.single().children.single().type)
    }
}
