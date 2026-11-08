package com.ai.assistance.operit.ui.common.markdown.lazy

import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

/** 超长纯文本按固定长度拆分；不截断代理对，也不拆散链接、公式等内联结构。 */
internal fun splitPlainMarkdownNode(node: MarkdownNodeStable, maxChars: Int = 4096): List<MarkdownNodeStable> {
    require(maxChars >= 2)
    if (node.type != MarkdownProcessorType.PLAIN_TEXT || node.content.length <= maxChars) return listOf(node)
    if (node.children.any { it.type != MarkdownProcessorType.PLAIN_TEXT || it.children.isNotEmpty() }) return listOf(node)
    if (node.children.isNotEmpty() && node.children.joinToString("") { it.content } != node.content) return listOf(node)
    return buildList {
        var start = 0
        while (start < node.content.length) {
            var end = minOf(start + maxChars, node.content.length)
            if (end < node.content.length && node.content[end - 1].isHighSurrogate()) end--
            val content = node.content.substring(start, end)
            add(node.copy(content = content, children = node.children.take(1).map { it.copy(content = content) }))
            start = end
        }
    }
}
