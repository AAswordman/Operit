package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.util.ThinkingMarkup

/** 可视化编辑器的文本/XML 片段，未闭合状态在切换编辑模式时保留。 */
data class ParsedMessagePart(
    val type: PartType,
    val content: String,
    val tag: String? = null,
    val attributes: String? = null,
    val closed: Boolean = true,
)

enum class PartType { TEXT, XML, SPACING }

/** 标签周围的换行和缩进仅用于重组原文，不生成可视化文本输入框。 */
private fun createEditorTextPart(content: String, xmlAdjacent: Boolean): ParsedMessagePart =
    ParsedMessagePart(
        type = if (xmlAdjacent && content.isBlank()) PartType.SPACING else PartType.TEXT,
        content = content,
    )

private val editorOpeningTag = Regex("<([a-zA-Z0-9_-]+)([^<>]*)>")
private val editorXmlBlock = Regex(
    "<([a-zA-Z0-9_-]+)([^>]*)>([\\s\\S]*?)</\\1>",
    RegexOption.DOT_MATCHES_ALL,
)

fun parseMessageContentForEditor(content: String): List<ParsedMessagePart> {
    val parts = mutableListOf<ParsedMessagePart>()
    var cursor = 0
    var searchFrom = 0

    while (searchFrom < content.length) {
        val opening = editorOpeningTag.find(content, searchFrom) ?: break
        val start = opening.range.first
        searchFrom = opening.range.last + 1
        // 自闭合标签继续作为原始文本保留，避免编辑器为它补上结束标签。
        if (opening.value.endsWith("/>")) continue
        val tag = opening.groupValues[1]
        val attributes = opening.groupValues[2]
        val part: ParsedMessagePart
        val end: Int

        if (ThinkingMarkup.isThinkingTag(tag)) {
            // 使用与渲染相同的 token 边界，思考正文中的普通结束标签不参与配对。
            val block = ThinkingMarkup.blockAt(content, start) ?: continue
            part = ParsedMessagePart(
                type = PartType.XML,
                content = content.substring(block.bodyStart, block.bodyEnd),
                tag = tag,
                attributes = attributes,
                closed = block.closed,
            )
            end = block.end
        } else {
            val block = editorXmlBlock.matchAt(content, start) ?: continue
            part = ParsedMessagePart(PartType.XML, block.groupValues[3], tag, attributes)
            end = block.range.last + 1
        }

        if (start > cursor) {
            parts.add(createEditorTextPart(content.substring(cursor, start), xmlAdjacent = true))
        }
        parts.add(part)
        cursor = end
        searchFrom = end
    }

    if (cursor < content.length) {
        parts.add(createEditorTextPart(content.substring(cursor), xmlAdjacent = parts.isNotEmpty()))
    }
    return parts
}

/** 点击标签保存时生成一次 token，后续重组仅使用已保存的属性。 */
fun createMessageEditorXmlPart(
    content: String,
    tagName: String,
    attributes: String,
): ParsedMessagePart {
    val name = tagName.trim()
    val trimmedAttributes = attributes.trim()
    if (!ThinkingMarkup.isThinkingTag(name)) {
        return ParsedMessagePart(PartType.XML, content, name, trimmedAttributes)
    }

    val token = ThinkingMarkup.tokenOf(trimmedAttributes)
    val thinkingAttributes = if (token == null) {
        val tokenAttribute = "token=\"${ThinkingMarkup.newToken()}\""
        if (trimmedAttributes.isEmpty()) tokenAttribute else "$trimmedAttributes $tokenAttribute"
    } else {
        trimmedAttributes
    }
    return ParsedMessagePart(PartType.XML, content, name.lowercase(), thinkingAttributes)
}

fun recomposeMessageFromParts(parts: List<ParsedMessagePart>): String =
    parts.joinToString(separator = "") { part ->
        if (part.type != PartType.XML) {
            part.content
        } else {
            val attributes = part.attributes.orEmpty()
            val spacedAttributes = if (attributes.isNotEmpty() && !attributes.first().isWhitespace()) {
                " $attributes"
            } else {
                attributes
            }
            val token = if (ThinkingMarkup.isThinkingTag(part.tag.orEmpty())) {
                ThinkingMarkup.tokenOf(attributes)
            } else {
                null
            }
            val closingTag = when {
                !part.closed -> ""
                token != null -> "</${part.tag} token=\"$token\">"
                else -> "</${part.tag}>"
            }
            "<${part.tag}$spacedAttributes>${part.content}$closingTag"
        }
    }
