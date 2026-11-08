package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.stream.Stream

sealed class MarkdownGroupedItem {
    data class Single(val index: Int) : MarkdownGroupedItem()

    data class Group(
        val startIndex: Int,
        val endIndexInclusive: Int,
        val stableKey: String
    ) : MarkdownGroupedItem()
}

interface MarkdownNodeGrouper {
    /** 分组和单独节点可共享消息级渲染信息，默认不增加额外行为。 */
    @Composable
    fun RenderScope(nodes: List<MarkdownNodeStable>, content: @Composable () -> Unit) { content() }

    fun group(nodes: List<MarkdownNodeStable>, rendererId: String): List<MarkdownGroupedItem>

    @Composable
    fun RenderGroup(
        group: MarkdownGroupedItem.Group,
        nodes: List<MarkdownNodeStable>,
        rendererId: String,
        isVisible: Boolean,
        isLastNode: Boolean,
        modifier: Modifier,
        textColor: Color,
        onLinkClick: ((String) -> Unit)?,
        xmlRenderer: XmlContentRenderer,
        xmlStreamResolver: (Int) -> Stream<String>?,
        fillMaxWidth: Boolean,
        fontSize: TextUnit
    )
}

object NoopMarkdownNodeGrouper : MarkdownNodeGrouper {
    override fun group(nodes: List<MarkdownNodeStable>, rendererId: String): List<MarkdownGroupedItem> {
        return nodes.indices.map { MarkdownGroupedItem.Single(it) }
    }

    @Composable
    override fun RenderGroup(
        group: MarkdownGroupedItem.Group,
        nodes: List<MarkdownNodeStable>,
        rendererId: String,
        isVisible: Boolean,
        isLastNode: Boolean,
        modifier: Modifier,
        textColor: Color,
        onLinkClick: ((String) -> Unit)?,
        xmlRenderer: XmlContentRenderer,
        xmlStreamResolver: (Int) -> Stream<String>?,
        fillMaxWidth: Boolean,
        fontSize: TextUnit
    ) {
    }
}
