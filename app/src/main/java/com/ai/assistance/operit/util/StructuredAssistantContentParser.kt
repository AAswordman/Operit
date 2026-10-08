package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.streamnative.NativeXmlSplitter

object StructuredAssistantContentParser {

    enum class BlockKind {
        TEXT,
        XML,
    }

    data class Block(
        val kind: BlockKind,
        val rawContent: String,
        val content: String,
        val tagName: String? = null,
        val rawTagName: String? = null,
        val attrs: Map<String, String> = emptyMap(),
        val closed: Boolean = true,
    )

    fun parse(content: String): List<Block> {
        if (content.isEmpty()) {
            return emptyList()
        }

        val splitSegments = NativeXmlSplitter.splitXmlTag(content)
        if (splitSegments.isEmpty()) {
            return listOf(
                Block(
                    kind = BlockKind.TEXT,
                    rawContent = content,
                    content = content,
                )
            )
        }

        return splitSegments.mapNotNull(::buildBlock)
    }

    private fun buildBlock(segment: List<String>): Block? {
        val kind = segment.getOrNull(0) ?: return null
        val rawContent = segment.getOrNull(1).orEmpty()
        if (rawContent.isEmpty()) {
            return null
        }

        if (kind == "text") {
            return Block(
                kind = BlockKind.TEXT,
                rawContent = rawContent,
                content = rawContent,
            )
        }

        val rawTagName = ChatMarkupRegex.extractOpeningTagName(rawContent)
        val tagName = ChatMarkupRegex.normalizeToolLikeTagName(rawTagName) ?: rawTagName
        return Block(
            kind = BlockKind.XML,
            rawContent = rawContent,
            content = extractXmlInnerContent(rawContent, rawTagName, tagName),
            tagName = tagName,
            rawTagName = rawTagName,
            attrs = extractXmlAttributes(rawContent),
            closed = isXmlFullyClosed(rawContent, rawTagName),
        )
    }

    private fun extractXmlInnerContent(
        xml: String,
        rawTagName: String?,
        normalizedTagName: String?,
    ): String {
        val effectiveTagName =
            when {
                !rawTagName.isNullOrBlank() -> rawTagName
                !normalizedTagName.isNullOrBlank() -> normalizedTagName
                else -> return xml
            }
        if (ThinkingMarkup.isThinkingTag(effectiveTagName)) {
            return ThinkingMarkup.body(xml)
        }
        val startTag = "<$effectiveTagName"
        val startTagIndex = xml.indexOf(startTag)
        if (startTagIndex < 0) {
            return xml
        }

        val startTagEnd = xml.indexOf('>', startTagIndex)
        if (startTagEnd < 0) {
            return xml
        }

        val endTag = "</$effectiveTagName>"
        val endIndex = xml.lastIndexOf(endTag)
        val contentEndExclusive =
            if (endIndex > startTagEnd) {
                endIndex
            } else {
                xml.length
            }
        return xml.substring(startTagEnd + 1, contentEndExclusive)
    }

    private fun extractXmlAttributes(xml: String): Map<String, String> {
        val trimmed = xml.trim()
        val startTagEnd = trimmed.indexOf('>')
        if (startTagEnd <= 0) {
            return emptyMap()
        }

        val startTag = trimmed.substring(0, startTagEnd + 1)
        val attrs = linkedMapOf<String, String>()
        Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(['"])([\s\S]*?)\2""")
            .findAll(startTag)
            .forEach { match ->
                val name = match.groupValues.getOrNull(1)?.trim().orEmpty()
                val value = match.groupValues.getOrNull(3).orEmpty()
                if (name.isNotBlank()) {
                    attrs[name] = value
                }
            }
        return attrs
    }

    private fun isXmlFullyClosed(xml: String, rawTagName: String?): Boolean {
        val trimmed = xml.trim()
        if (trimmed.endsWith("/>")) {
            return true
        }

        val effectiveTagName = rawTagName ?: ChatMarkupRegex.extractOpeningTagName(trimmed) ?: return false
        if (ThinkingMarkup.isThinkingTag(effectiveTagName)) {
            return ThinkingMarkup.isClosed(trimmed)
        }
        return trimmed.contains("</$effectiveTagName>")
    }
}
