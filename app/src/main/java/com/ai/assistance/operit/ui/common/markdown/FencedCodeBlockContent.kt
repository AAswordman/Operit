package com.ai.assistance.operit.ui.common.markdown

internal data class FencedCodeBlockContent(val language: String, val code: String)

private val openingCodeFence = Regex("^[ \\t]{0,3}(`{3,}|~{3,})([^\\r\\n]*)\\r?$")

/** 展示和纯文本复制共用围栏边界，只去除外层标记，保留正文中的较短围栏。 */
internal fun extractFencedCodeBlockContent(content: String): FencedCodeBlockContent {
    val opening = openingCodeFence.matchEntire(content.substringBefore('\n'))
        ?: return FencedCodeBlockContent("", content)
    val delimiter = opening.groupValues[1]
    val bodyStart = content.indexOf('\n').let { if (it < 0) content.length else it + 1 }
    val closingFence = Regex(
        "^[ \\t]{0,3}" + Regex.escape(delimiter.first().toString()) +
            "{" + delimiter.length + ",}[ \\t]*\\r?$",
        RegexOption.MULTILINE,
    ).find(content, bodyStart)
    val bodyEnd = closingFence?.range?.first ?: content.length
    val code = content.substring(bodyStart, bodyEnd)
        .let { if (closingFence == null) it else it.removeSuffix("\n").removeSuffix("\r") }
    return FencedCodeBlockContent(opening.groupValues[2].trim(), code)
}
