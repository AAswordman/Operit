package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ai.assistance.operit.ui.features.chat.components.timeline.shouldSplitChatMessage
import com.ai.assistance.operit.ui.common.markdown.lazy.markdownNodeIdentityKeys
import com.ai.assistance.operit.ui.common.markdown.lazy.splitPlainMarkdownNode

class ThinkToolsRowsTest {
    private fun node(content: String, xml: Boolean = true) = MarkdownNodeStable(
        type = if (xml) MarkdownProcessorType.XML_BLOCK else MarkdownProcessorType.PLAIN_TEXT,
        content = content,
        children = emptyList(),
    )

    private fun tools(count: Int): List<MarkdownNodeStable> = buildList {
        repeat(count) { index ->
            add(node("<tool name=\"read_file\"><param name=\"path\">sample-$index</param></tool>"))
            add(node("<tool_result name=\"read_file\" status=\"success\"><content>result-$index</content></tool_result>"))
        }
    }

    private fun grouper() = ThinkToolsXmlNodeGrouper(
        showThinkingProcess = true,
    )

    @Test
    fun oneLargeGroup_keepsEveryToolAndResultAsIndependentRows() {
        val nodes = tools(100)
        val grouper = grouper()
        val groups = grouper.group(nodes, "test")
        val group = groups.single() as MarkdownGroupedItem.Group
        val rows = buildThinkToolsRows(groups, nodes, mapOf(group.stableKey to true)) {
            grouper.describeGroup(it, nodes) { null }
        }
        assertEquals(201, rows.size)
        assertEquals(nodes.indices.toList(), rows.filterIsInstance<ThinkToolsRow.Content>().map { it.index })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun manyExpandedGroups_keepLateResultsReachableWithoutCollapsingEarlierGroups() {
        val nodes = buildList {
            repeat(50) { index ->
                add(node("<think>思考 $index</think>"))
                addAll(tools(2))
                add(node("正文分隔 $index", xml = false))
            }
        }
        val grouper = grouper()
        val groups = grouper.group(nodes, "test")
        val headers = groups.filterIsInstance<MarkdownGroupedItem.Group>()
        val overrides = headers.associate { it.stableKey to true }
        val rows = buildThinkToolsRows(groups, nodes, overrides) {
            grouper.describeGroup(it, nodes) { null }
        }
        assertEquals(50, rows.filterIsInstance<ThinkToolsRow.Header>().size)
        assertEquals(nodes.indices.toList(), rows.filterIsInstance<ThinkToolsRow.Content>().map { it.index })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun manualCollapse_overridesStreamingAutoExpansionWithoutHidingOtherGroups() {
        val nodes = tools(4)
        val groups = listOf(
            MarkdownGroupedItem.Group(0, 3, "first"),
            MarkdownGroupedItem.Group(4, 7, "second"),
        )
        val rows = buildThinkToolsRows(groups, nodes, mapOf("first" to false, "second" to true)) {
            ThinkToolsGroupInfo(it, 2, 0, autoExpand = true)
        }
        assertEquals(listOf(false, true), rows.filterIsInstance<ThinkToolsRow.Header>().map { it.expanded })
        assertEquals(listOf(4, 5, 6, 7), rows.filterIsInstance<ThinkToolsRow.Content>().map { it.index })
    }

    @Test
    fun appendToStreamingGroup_preservesExistingRowKeys() {
        val grouper = grouper()
        fun rows(nodes: List<MarkdownNodeStable>): List<ThinkToolsRow> {
            val groups = grouper.group(nodes, "test")
            return buildThinkToolsRows(groups, nodes, groups.filterIsInstance<MarkdownGroupedItem.Group>().associate {
                it.stableKey to true
            }) { grouper.describeGroup(it, nodes) { null } }
        }
        val before = rows(tools(12))
        val after = rows(tools(13))
        assertEquals(before.map { it.key }, after.take(before.size).map { it.key })
    }

    @Test
    fun messageSplitting_preservesShortMessagesAndIncludesLongPlainText() {
        assertFalse(shouldSplitChatMessage(tools(11)))
        assertTrue(shouldSplitChatMessage(tools(12)))
        assertTrue(shouldSplitChatMessage(listOf(node("正文".repeat(20_000), xml = false))))
    }

    @Test
    fun protocolMetadataRemoval_preservesGroupAndCardIdentities() {
        val markup = listOf(node("<think token=\"sample\">思考</think>")) + tools(2)
        val before = listOf(node("<meta>协议</meta>")) + markup
        assertEquals(markdownNodeIdentityKeys(markup), markdownNodeIdentityKeys(before).drop(1))
        val grouper = grouper()
        val keyBefore = grouper.group(before, "stream").filterIsInstance<MarkdownGroupedItem.Group>().single().stableKey
        val keyAfter = grouper.group(markup, "static").filterIsInstance<MarkdownGroupedItem.Group>().single().stableKey
        assertEquals(keyBefore, keyAfter)
    }

    @Test
    fun hiddenNodes_doNotReserveBubbleRowsAroundVisibleBody() {
        val nodes = listOf(
            node("\n\n", xml = false),
            node("<think>隐藏思考</think>"),
            node("<status type=\"completion\"/>"),
            node("Plan of Action\n完整正文", xml = false),
            node("<meta provider=\"openai:responses_reasoning\">协议</meta>"),
            node("\n", xml = false),
        )
        val groups = nodes.indices.map { MarkdownGroupedItem.Single(it) }
        val rows = buildThinkToolsRows(
            groups, nodes, emptyMap(), showThinkingProcess = false, showStatusTags = false,
        ) { error("正文没有工具分组") }

        val content = rows.single() as ThinkToolsRow.Content
        assertEquals(3, content.index)
        assertEquals("${markdownNodeIdentityKeys(nodes)[3]}/part-0", content.key)
        assertEquals(nodes[3], content.node)
    }

    @Test
    fun largeHiddenProtocol_doesNotSplitShortVisibleReply() {
        val nodes = listOf(
            node("Plan of Action\n正文", xml = false),
            node("<meta provider=\"openai:responses_reasoning\">${"x".repeat(40_000)}</meta>"),
        )
        val visibleNodes = nodes.filter {
            isVisibleChatMarkdownNode(it, showThinkingProcess = true, showStatusTags = true)
        }

        assertEquals(listOf(nodes.first()), visibleNodes)
        assertFalse(shouldSplitChatMessage(visibleNodes))
        val rows = buildThinkToolsRows(nodes.indices.map { MarkdownGroupedItem.Single(it) }, nodes, emptyMap()) {
            error("正文没有工具分组")
        }
        assertEquals(listOf(0), rows.filterIsInstance<ThinkToolsRow.Content>().map { it.index })
    }

    @Test
    fun literalMetaExamplesAndUnknownXml_remainVisible() {
        val example = "<meta provider=\"openai:responses_reasoning\">字面示例</meta>"
        val nodes = listOf(
            node(example, xml = false),
            node(example, xml = false).copy(type = MarkdownProcessorType.CODE_BLOCK),
            node("<meta provider=\"custom\">自定义内容</meta>"),
        )
        val rows = buildThinkToolsRows(nodes.indices.map { MarkdownGroupedItem.Single(it) }, nodes, emptyMap()) {
            error("正文没有工具分组")
        }

        assertEquals(listOf(0, 1, 2), rows.filterIsInstance<ThinkToolsRow.Content>().map { it.index })
    }

    @Test
    fun blankPlainTextChunks_doNotReserveTheFirstBubbleSlice() {
        val nodes = listOf(node(" ".repeat(4096) + "Plan of Action", xml = false))
        val rows = buildThinkToolsRows(listOf(MarkdownGroupedItem.Single(0)), nodes, emptyMap()) {
            error("正文没有工具分组")
        }

        val content = rows.single() as ThinkToolsRow.Content
        assertEquals(0, content.index)
        assertEquals("${markdownNodeIdentityKeys(nodes)[0]}/part-1", content.key)
        assertEquals("Plan of Action", content.node?.content)
    }

    @Test
    fun longPlainText_keepsAllContentWithoutSplittingEmojiSurrogates() {
        val text = "123" + "😀" + "abcdef"
        val chunks = splitPlainMarkdownNode(node(text, xml = false), maxChars = 4)
        assertEquals(text, chunks.joinToString("") { it.content })
        assertTrue(chunks.all { it.content.length <= 4 })
        assertTrue(chunks.dropLast(1).none { it.content.last().isHighSurrogate() })
    }
}
