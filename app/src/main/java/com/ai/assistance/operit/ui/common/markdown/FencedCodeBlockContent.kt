package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.markdown.MarkdownFence

internal data class FencedCodeBlockContent(val language: String, val code: String, val closed: Boolean = false)

/** 展示和纯文本复制共用围栏边界，只去除外层标记，保留正文中的较短围栏。 */
internal fun extractFencedCodeBlockContent(content: String): FencedCodeBlockContent {
    val opening = MarkdownFence.parse(content.substringBefore('\n'))
        ?: return FencedCodeBlockContent("", content)
    val bodyStart = content.indexOf('\n').let { if (it < 0) content.length else it + 1 }
    val closingFence = opening.closing.find(content, bodyStart)
    val bodyEnd = closingFence?.range?.first ?: content.length
    val code = content.substring(bodyStart, bodyEnd)
        .let { if (closingFence == null) it else it.removeSuffix("\n").removeSuffix("\r") }
    return FencedCodeBlockContent(opening.language, code, closingFence != null)
}
