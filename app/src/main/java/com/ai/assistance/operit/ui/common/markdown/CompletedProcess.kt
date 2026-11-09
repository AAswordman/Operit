package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

/** Keep the entire answer after the last activity block, including headings and paragraphs. */
internal fun completedProcessEnd(nodes: List<MarkdownNodeStable>): Int {
    val end = nodes.indexOfLast { node ->
        if (node.type != MarkdownProcessorType.XML_BLOCK) false
        else {
            val tag = ChatMarkupRegex.extractOpeningTagName(node.content)?.lowercase()
            tag == "think" || tag == "thinking" || tag == "search" ||
                ChatMarkupRegex.isToolTagName(tag) || ChatMarkupRegex.isToolResultTagName(tag)
        }
    }
    if (end < 0) return -1
    // A tool-only or interrupted response must stay visible rather than become an empty answer.
    val answerStart = (end + 1 until nodes.size).firstOrNull { index ->
        val node = nodes[index]
        val tag = if (node.type == MarkdownProcessorType.XML_BLOCK) {
            ChatMarkupRegex.extractOpeningTagName(node.content)?.lowercase()
        } else null
        tag != "meta" && tag != "status" &&
            (node.content.isNotBlank() || node.children.any { child -> child.content.isNotBlank() })
    } ?: return -1
    return answerStart - 1
}
