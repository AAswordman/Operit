package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.streamnative.NativeMarkdownSplitter

/**
 * 把消息内容解析为适合直接粘贴的纯文本，复用 [parseMarkdownToNodes] 产出的同一份 AST，
 * 保证"复制出来的内容"和屏幕上渲染的内容共享同一套边界判断（代码块/表格/公式的起止位置等）。
 */
internal suspend fun markdownToPlainTextForCopy(
    markdown: String,
    latexToPlainText: (List<String>) -> List<String> = { it },
): String {
    val nodes = parseMarkdownToNodes(markdown).map { it.toStableNode() }
    val latexSegments = mutableListOf<String>()
    val rendered = joinBlocks(nodes, latexSegments)
    // 折叠"空白行"：2 个以上换行（中间可能夹着空格/全角空格等水平空白，例如模型输出里
    // 残留的一行全角空格会被解析成一个空 PLAIN_TEXT 块）统一折叠成一个空行，
    // 否则纯 \n{3,} 规则会被中间的空白字符打断，导致公式块上方多出一个空行。
    val normalized = rendered.replace(Regex("\\h*\\n(?:\\h*\\n)+\\h*"), "\n\n").trim()

    if (latexSegments.isEmpty()) return normalized

    val convertedLatex = latexToPlainText(latexSegments)
    return latexSegments.indices.fold(normalized) { text, index ->
        text.replace(
            latexPlaceholder(index),
            convertedLatex.getOrElse(index) { latexSegments[index] }
        )
    }
}

/**
 * 逐个拼接顶层节点：相邻的列表项（有序/无序混排也算）之间只用单个换行，
 * 其余相邻块之间空一行；HTML_BREAK 节点本身不参与渲染，只是原始 Markdown
 * 里空行数量的标记，最终统一交给上面的空行折叠逻辑处理。
 */
private fun joinBlocks(nodes: List<MarkdownNodeStable>, latexSegments: MutableList<String>): String {
    val contentNodes = nodes.filterNot { it.type == MarkdownProcessorType.HTML_BREAK }
    val builder = StringBuilder()
    contentNodes.forEachIndexed { index, node ->
        if (index > 0) {
            val previousType = contentNodes[index - 1].type
            val bothListItems = node.type.isListItem() && previousType.isListItem()
            builder.append(if (bothListItems) "\n" else "\n\n")
        }
        builder.append(renderBlockNode(node, latexSegments))
    }
    return builder.toString()
}

private fun MarkdownProcessorType.isListItem(): Boolean =
    this == MarkdownProcessorType.ORDERED_LIST || this == MarkdownProcessorType.UNORDERED_LIST

private fun renderBlockNode(node: MarkdownNodeStable, latexSegments: MutableList<String>): String {
    return when (node.type) {
        MarkdownProcessorType.HTML_BREAK,
        MarkdownProcessorType.HORIZONTAL_RULE -> ""
        MarkdownProcessorType.CODE_BLOCK -> renderCodeBlock(node.content)
        MarkdownProcessorType.TABLE -> renderTable(node.content, latexSegments)
        MarkdownProcessorType.BLOCK_LATEX ->
            registerLatex(extractLatexContent(node.content.trim()).trim(), latexSegments)
        MarkdownProcessorType.XML_BLOCK -> node.content
        MarkdownProcessorType.IMAGE -> extractLinkText(node.content)
        MarkdownProcessorType.HEADER -> renderInlineBlock(node, latexSegments).trimStart('#', ' ')
        MarkdownProcessorType.UNORDERED_LIST -> "• " + renderInlineBlock(node, latexSegments)
        else -> renderInlineBlock(node, latexSegments)
    }
}

private fun renderInlineBlock(node: MarkdownNodeStable, latexSegments: MutableList<String>): String {
    if (node.children.isEmpty()) return node.content
    return node.children.joinToString("") { it.plainInlineText(latexSegments) }
}

private fun MarkdownNodeStable.plainInlineText(latexSegments: MutableList<String>): String {
    return when (type) {
        MarkdownProcessorType.INLINE_LATEX, MarkdownProcessorType.BLOCK_LATEX ->
            registerLatex(extractLatexContent(content.trim()).trim(), latexSegments)
        MarkdownProcessorType.LINK -> {
            val text = resolvedChildrenText(this, latexSegments)
            val url = extractLinkUrl(content)
            if (url.isBlank()) text else "$text ($url)"
        }
        else -> resolvedChildrenText(this, latexSegments)
    }
}

private fun resolvedChildrenText(node: MarkdownNodeStable, latexSegments: MutableList<String>): String {
    val children = resolveNestedInlineChildren(node)
    if (children.isEmpty()) return resolveNestedInlineText(node)
    return children.joinToString("") { it.plainInlineText(latexSegments) }
}

private fun renderCodeBlock(content: String): String {
    val codeLines = content.trim().lines()
    val firstLine = codeLines.firstOrNull().orEmpty()
    val language = if (firstLine.startsWith("```")) firstLine.removePrefix("```").trim() else ""
    val codeContent = codeLines
        .dropWhile { it.startsWith("```") }
        .dropLastWhile { it.endsWith("```") }
        .joinToString("\n")
    return if (language.isNotBlank()) "----$language-----\n$codeContent" else codeContent
}

private fun renderTable(content: String, latexSegments: MutableList<String>): String {
    return parseTable(content).rows.joinToString("\n") { row ->
        row.joinToString("\t") { cell -> renderRawInlineText(cell, latexSegments) }
    }
}

private fun renderRawInlineText(raw: String, latexSegments: MutableList<String>): String {
    val nodes = NativeMarkdownSplitter.parseInlineToStableNodes(raw)
    if (nodes.isEmpty()) return raw
    return nodes.joinToString("") { it.plainInlineText(latexSegments) }
}

private fun registerLatex(formula: String, latexSegments: MutableList<String>): String {
    val placeholder = latexPlaceholder(latexSegments.size)
    latexSegments += formula
    return placeholder
}

private fun latexPlaceholder(index: Int): String = "$index"
