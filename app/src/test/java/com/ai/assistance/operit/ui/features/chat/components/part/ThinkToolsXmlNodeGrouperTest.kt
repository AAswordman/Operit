package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThinkToolsXmlNodeGrouperTest {
    @Test
    fun `tool group key remains stable when an earlier node changes its position`() {
        val thinkAndTools =
            listOf(
                xmlNode(101, "<think>planning</think>"),
                xmlNode(102, "<tool name=\"read_file\"><param name=\"path\">one</param></tool>"),
                xmlNode(103, "<tool name=\"read_file\"><param name=\"path\">two</param></tool>"),
            )
        val grouper =
            ThinkToolsXmlNodeGrouper(
                showThinkingProcess = true,
                toolCollapseMode = ToolCollapseMode.ALL,
            )

        val initialGroup = grouper.group(thinkAndTools, rendererId = "stream").singleGroup()
        val shiftedGroup =
            grouper.group(
                listOf(plainNode(1, "intro\n")) + thinkAndTools,
                rendererId = "stream",
            ).singleGroup()

        assertNotEquals(initialGroup.startIndex, shiftedGroup.startIndex)
        assertEquals(initialGroup.stableKey, shiftedGroup.stableKey)
    }

    @Test
    fun `result-only sequence remains directly visible in full collapse mode`() {
        val items =
            ThinkToolsXmlNodeGrouper(
                showThinkingProcess = true,
                toolCollapseMode = ToolCollapseMode.FULL,
            ).group(
                nodes =
                    listOf(
                        xmlNode(
                            401,
                            "<tool_result_26C6 name=\"read_file_part\" status=\"error\"><content>missing path</content></tool_result_26C6>",
                        ),
                    ),
                rendererId = "stream",
            )

        assertEquals(listOf(MarkdownGroupedItem.Single(0)), items)
    }

    @Test
    fun `think followed only by a result does not create a zero-tool group`() {
        val items =
            ThinkToolsXmlNodeGrouper(
                showThinkingProcess = true,
                toolCollapseMode = ToolCollapseMode.FULL,
            ).group(
                nodes =
                    listOf(
                        xmlNode(501, "<think>checking the file</think>"),
                        xmlNode(
                            502,
                            "<tool_result_26C6 name=\"read_file_part\" status=\"error\"><content>missing path</content></tool_result_26C6>",
                        ),
                    ),
                rendererId = "stream",
            )

        assertEquals(
            listOf(MarkdownGroupedItem.Single(0), MarkdownGroupedItem.Single(1)),
            items,
        )
    }

    private fun List<MarkdownGroupedItem>.singleGroup(): MarkdownGroupedItem.Group {
        return filterIsInstance<MarkdownGroupedItem.Group>().single()
    }

    private fun xmlNode(nodeId: Long, content: String): MarkdownNodeStable {
        return MarkdownNodeStable(
            type = MarkdownProcessorType.XML_BLOCK,
            content = content,
            children = emptyList(),
            nodeId = nodeId,
        )
    }

    private fun plainNode(nodeId: Long, content: String): MarkdownNodeStable {
        return MarkdownNodeStable(
            type = MarkdownProcessorType.PLAIN_TEXT,
            content = content,
            children = emptyList(),
            nodeId = nodeId,
        )
    }
}
