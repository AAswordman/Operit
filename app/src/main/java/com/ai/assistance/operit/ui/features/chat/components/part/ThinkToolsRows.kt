package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.ui.common.markdown.lazy.markdownNodeIdentityKeys
import com.ai.assistance.operit.ui.common.markdown.lazy.splitPlainMarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

internal data class ThinkToolsGroupInfo(
    val group: MarkdownGroupedItem.Group,
    val toolCount: Int,
    val searchCount: Int,
    val autoExpand: Boolean,
)

internal sealed interface ThinkToolsRow {
    val key: String

    data class Header(val info: ThinkToolsGroupInfo, val expanded: Boolean) : ThinkToolsRow {
        override val key: String get() = "header-${info.group.stableKey}"
    }

    data class Content(val index: Int, val inGroup: Boolean, override val key: String, val node: MarkdownNodeStable? = null) : ThinkToolsRow
}

/** 组标题和组内节点属于同一滚动区域，单个大组也不会退化为一个超高条目。 */
internal fun buildThinkToolsRows(
    groups: List<MarkdownGroupedItem>,
    nodes: List<MarkdownNodeStable>,
    overrides: Map<String, Boolean>,
    showThinkingProcess: Boolean = true,
    showStatusTags: Boolean = true,
    describeGroup: (MarkdownGroupedItem.Group) -> ThinkToolsGroupInfo,
): List<ThinkToolsRow> = buildList {
    val nodeKeys = markdownNodeIdentityKeys(nodes)
    for (item in groups) {
        when (item) {
            is MarkdownGroupedItem.Single -> {
                if (item.index in nodes.indices) {
                    val node = nodes[item.index]
                    if (!isVisibleChatMarkdownNode(node, showThinkingProcess, showStatusTags)) continue
                    if (node.type == MarkdownProcessorType.PLAIN_TEXT) {
                        splitPlainMarkdownNode(node).forEachIndexed { piece, chunk ->
                            if (isVisibleChatMarkdownNode(chunk, showThinkingProcess, showStatusTags)) {
                                add(ThinkToolsRow.Content(item.index, false, "${nodeKeys[item.index]}/part-$piece", chunk))
                            }
                        }
                    } else {
                        add(ThinkToolsRow.Content(item.index, false, nodeKeys[item.index]))
                    }
                }
            }
            is MarkdownGroupedItem.Group -> {
                val info = describeGroup(item)
                val expanded = overrides[item.stableKey] ?: info.autoExpand
                add(ThinkToolsRow.Header(info, expanded))
                if (expanded) {
                    for (index in item.startIndex..item.endIndexInclusive.coerceAtMost(nodes.lastIndex)) {
                        if (index in nodes.indices &&
                            nodes[index].type == MarkdownProcessorType.XML_BLOCK &&
                            isVisibleChatMarkdownNode(nodes[index], showThinkingProcess, showStatusTags)
                        ) {
                            add(ThinkToolsRow.Content(index, true, nodeKeys[index]))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun thinkToolsGroupTitle(info: ThinkToolsGroupInfo): String = when {
    info.group.stableKey.startsWith("tools-only-") -> stringResource(R.string.tools_group_title_with_count, info.toolCount)
    info.group.stableKey.startsWith("search-only-") -> stringResource(R.string.search_group_title)
    info.searchCount > 0 && info.toolCount > 0 -> stringResource(R.string.thinking_search_tools_group_title_with_count, info.toolCount)
    info.searchCount > 0 -> stringResource(R.string.thinking_search_group_title)
    else -> stringResource(R.string.thinking_tools_group_title_with_count, info.toolCount)
}
