package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle

// Output transformations run while the field is presenting edits, so malformed delimiters must
// not trigger repeated whole-line searches.
private const val MAX_INLINE_TOKEN_LENGTH = 512

internal enum class MarkdownSyntaxKind {
    HEADING,
    EMPHASIS,
    CODE,
    LINK,
    QUOTE,
    MARKER,
    COMMENT,
    HTML,
}

private data class MarkdownSyntaxColors(
    val heading: Color,
    val emphasis: Color,
    val code: Color,
    val link: Color,
    val quote: Color,
    val marker: Color,
    val comment: Color,
    val html: Color,
) {
    fun colorFor(kind: MarkdownSyntaxKind): Color =
        when (kind) {
            MarkdownSyntaxKind.HEADING -> heading
            MarkdownSyntaxKind.EMPHASIS -> emphasis
            MarkdownSyntaxKind.CODE -> code
            MarkdownSyntaxKind.LINK -> link
            MarkdownSyntaxKind.QUOTE -> quote
            MarkdownSyntaxKind.MARKER -> marker
            MarkdownSyntaxKind.COMMENT -> comment
            MarkdownSyntaxKind.HTML -> html
        }
}

@Composable
internal fun rememberMarkdownSyntaxOutputTransformation(): OutputTransformation {
    val colorScheme = MaterialTheme.colorScheme
    val colors =
        remember(
            colorScheme.primary,
            colorScheme.secondary,
            colorScheme.tertiary,
            colorScheme.onSurfaceVariant,
        ) {
            MarkdownSyntaxColors(
                heading = colorScheme.tertiary,
                emphasis = colorScheme.tertiary,
                code = colorScheme.primary,
                link = colorScheme.primary,
                quote = colorScheme.secondary,
                marker = colorScheme.secondary,
                comment = colorScheme.onSurfaceVariant,
                html = colorScheme.secondary,
            )
        }

    return remember(colors) {
        OutputTransformation {
            // Style annotations preserve source offsets, so selection, IME composition, and the
            // field's own scroll state continue to use the same text layout.
            scanMarkdownSyntax(asCharSequence()) { kind, start, end ->
                addStyle(SpanStyle(color = colors.colorFor(kind)), start, end)
            }
        }
    }
}

internal fun scanMarkdownSyntax(
    source: CharSequence,
    emit: (kind: MarkdownSyntaxKind, start: Int, end: Int) -> Unit,
) {
    var lineStart = 0
    var fenceMarker: Char? = null
    var fenceLength = 0
    var inHtmlComment = false

    while (lineStart < source.length) {
        val newline = source.findChar('\n', lineStart)
        val rawLineEnd = if (newline >= 0) newline else source.length
        val lineEnd =
            if (rawLineEnd > lineStart && source[rawLineEnd - 1] == '\r') {
                rawLineEnd - 1
            } else {
                rawLineEnd
            }

        if (inHtmlComment) {
            val commentEnd = source.findSequence("-->", lineStart, lineEnd)
            if (commentEnd < 0) {
                emitRange(emit, MarkdownSyntaxKind.COMMENT, lineStart, lineEnd)
            } else {
                val afterComment = commentEnd + 3
                emitRange(emit, MarkdownSyntaxKind.COMMENT, lineStart, afterComment)
                inHtmlComment = scanInlineSyntax(source, afterComment, lineEnd, emit)
            }
            lineStart = nextLineStart(newline, source.length)
            continue
        }

        val blockStart = source.blockStart(lineStart, lineEnd)
        val activeFenceMarker = fenceMarker
        if (activeFenceMarker != null) {
            emitRange(emit, MarkdownSyntaxKind.CODE, lineStart, lineEnd)
            if (
                blockStart >= 0 &&
                    source.isClosingFence(blockStart, lineEnd, activeFenceMarker, fenceLength)
            ) {
                fenceMarker = null
                fenceLength = 0
            }
            lineStart = nextLineStart(newline, source.length)
            continue
        }

        if (blockStart >= 0) {
            val openingFenceLength = source.fenceRunLength(blockStart, lineEnd)
            val marker = if (blockStart < lineEnd) source[blockStart] else null
            val validInfoString =
                marker != '`' || !source.contains('`', blockStart + openingFenceLength, lineEnd)
            if (openingFenceLength >= 3 && validInfoString) {
                fenceMarker = source[blockStart]
                fenceLength = openingFenceLength
                emitRange(emit, MarkdownSyntaxKind.CODE, lineStart, lineEnd)
                lineStart = nextLineStart(newline, source.length)
                continue
            }
        }

        inHtmlComment = scanMarkdownLine(source, lineStart, lineEnd, blockStart, emit)
        lineStart = nextLineStart(newline, source.length)
    }
}

private fun scanMarkdownLine(
    source: CharSequence,
    lineStart: Int,
    lineEnd: Int,
    blockStart: Int,
    emit: (MarkdownSyntaxKind, Int, Int) -> Unit,
): Boolean {
    if (blockStart < 0 || blockStart >= lineEnd) {
        return scanInlineSyntax(source, lineStart, lineEnd, emit)
    }

    var contentStart = blockStart
    if (source[contentStart] == '>') {
        val quoteStart = contentStart
        do {
            contentStart++
            if (contentStart < lineEnd && source[contentStart] == ' ') {
                contentStart++
            }
        } while (contentStart < lineEnd && source[contentStart] == '>')
        emitRange(emit, MarkdownSyntaxKind.QUOTE, quoteStart, contentStart)
        contentStart = source.skipWhitespace(contentStart, lineEnd)
    }

    val headingEnd = source.headingMarkerEnd(contentStart, lineEnd)
    if (headingEnd > contentStart) {
        emitRange(emit, MarkdownSyntaxKind.MARKER, contentStart, headingEnd)
        val headingTextStart = source.skipWhitespace(headingEnd, lineEnd)
        emitRange(emit, MarkdownSyntaxKind.HEADING, headingTextStart, lineEnd)
        return false
    }

    if (source.isThematicBreak(contentStart, lineEnd)) {
        emitRange(emit, MarkdownSyntaxKind.MARKER, contentStart, lineEnd)
        return false
    }

    val listMarkerEnd = source.listMarkerEnd(contentStart, lineEnd)
    if (listMarkerEnd > contentStart) {
        emitRange(emit, MarkdownSyntaxKind.MARKER, contentStart, listMarkerEnd)
        contentStart = source.skipWhitespace(listMarkerEnd, lineEnd)
        val taskMarkerEnd = source.taskMarkerEnd(contentStart, lineEnd)
        if (taskMarkerEnd > contentStart) {
            emitRange(emit, MarkdownSyntaxKind.MARKER, contentStart, taskMarkerEnd)
            contentStart = source.skipWhitespace(taskMarkerEnd, lineEnd)
        }
    }

    return scanInlineSyntax(source, contentStart, lineEnd, emit)
}

private fun scanInlineSyntax(
    source: CharSequence,
    start: Int,
    end: Int,
    emit: (MarkdownSyntaxKind, Int, Int) -> Unit,
): Boolean {
    var index = start
    while (index < end) {
        when {
            source[index] == '\\' -> {
                index = (index + 2).coerceAtMost(end)
            }

            source.hasPrefix("<!--", index, end) -> {
                val commentEnd = source.findSequence("-->", index + 4, end)
                if (commentEnd < 0) {
                    emitRange(emit, MarkdownSyntaxKind.COMMENT, index, end)
                    return true
                }
                val afterComment = commentEnd + 3
                emitRange(emit, MarkdownSyntaxKind.COMMENT, index, afterComment)
                index = afterComment
            }

            source[index] == '`' -> {
                val markerLength = source.runLength(index, end, '`')
                val searchEnd = inlineTokenEnd(index, end)
                val closingStart =
                    source.findMatchingRun('`', markerLength, index + markerLength, searchEnd)
                if (closingStart >= 0) {
                    val codeEnd = closingStart + markerLength
                    emitRange(emit, MarkdownSyntaxKind.CODE, index, codeEnd)
                    index = codeEnd
                } else {
                    index += markerLength
                }
            }

            source[index] == '[' ||
                (source[index] == '!' && index + 1 < end && source[index + 1] == '[') -> {
                val linkEnd = source.linkEnd(index, end)
                if (linkEnd > index) {
                    emitRange(emit, MarkdownSyntaxKind.LINK, index, linkEnd)
                    index = linkEnd
                } else {
                    index++
                }
            }

            source[index] == '<' -> {
                val autolinkEnd = source.autolinkEnd(index, end)
                if (autolinkEnd > index) {
                    emitRange(emit, MarkdownSyntaxKind.LINK, index, autolinkEnd)
                    index = autolinkEnd
                } else {
                    val htmlEnd = source.htmlTagEnd(index, end)
                    if (htmlEnd > index) {
                        emitRange(emit, MarkdownSyntaxKind.HTML, index, htmlEnd)
                        index = htmlEnd
                    } else {
                        index++
                    }
                }
            }

            source[index] == '*' || source[index] == '_' || source[index] == '~' -> {
                val marker = source[index]
                val markerRunLength = source.runLength(index, end, marker)
                val markerLength = emphasisMarkerLength(marker, markerRunLength)
                if (
                    markerLength > 0 &&
                        source.canOpenEmphasis(index, markerLength, end)
                ) {
                    val emphasisEnd = source.emphasisEnd(index, markerLength, end)
                    if (emphasisEnd > index) {
                        emitRange(emit, MarkdownSyntaxKind.EMPHASIS, index, emphasisEnd)
                        index = emphasisEnd
                    } else {
                        index += markerRunLength
                    }
                } else {
                    index += markerRunLength
                }
            }

            source[index] == '|' -> {
                emitRange(emit, MarkdownSyntaxKind.MARKER, index, index + 1)
                index++
            }

            else -> index++
        }
    }
    return false
}

private fun CharSequence.blockStart(start: Int, end: Int): Int {
    var index = start
    var spaces = 0
    while (index < end && this[index] == ' ' && spaces < 4) {
        index++
        spaces++
    }
    return if (spaces <= 3) index else -1
}

private fun CharSequence.fenceRunLength(start: Int, end: Int): Int {
    if (start >= end || (this[start] != '`' && this[start] != '~')) return 0
    return runLength(start, end, this[start])
}

private fun CharSequence.isClosingFence(
    start: Int,
    end: Int,
    marker: Char,
    openingLength: Int,
): Boolean {
    if (start >= end || this[start] != marker) return false
    val runLength = runLength(start, end, marker)
    if (runLength < openingLength) return false
    var index = start + runLength
    while (index < end) {
        if (!this[index].isWhitespace()) return false
        index++
    }
    return true
}

private fun CharSequence.headingMarkerEnd(start: Int, end: Int): Int {
    var index = start
    while (index < end && this[index] == '#' && index - start < 6) {
        index++
    }
    val markerLength = index - start
    return if (markerLength in 1..6 && (index == end || this[index].isWhitespace())) index else -1
}

private fun CharSequence.isThematicBreak(start: Int, end: Int): Boolean {
    if (start >= end || this[start] !in charArrayOf('*', '-', '_')) return false
    val marker = this[start]
    var markerCount = 0
    for (index in start until end) {
        when {
            this[index] == marker -> markerCount++
            this[index] == ' ' || this[index] == '\t' -> Unit
            else -> return false
        }
    }
    return markerCount >= 3
}

private fun CharSequence.listMarkerEnd(start: Int, end: Int): Int {
    if (start >= end) return -1
    if (
        this[start] in charArrayOf('-', '+', '*') &&
            start + 1 < end &&
            this[start + 1].isWhitespace()
    ) {
        return start + 1
    }

    var index = start
    while (index < end && this[index].isDigit() && index - start < 9) {
        index++
    }
    if (
        index > start &&
            index < end &&
            this[index] in charArrayOf('.', ')') &&
            index + 1 < end &&
            this[index + 1].isWhitespace()
    ) {
        return index + 1
    }
    return -1
}

private fun CharSequence.taskMarkerEnd(start: Int, end: Int): Int {
    if (
        start + 2 < end &&
            this[start] == '[' &&
            this[start + 1] in charArrayOf(' ', 'x', 'X') &&
            this[start + 2] == ']' &&
            (start + 3 == end || this[start + 3].isWhitespace())
    ) {
        return start + 3
    }
    return -1
}

private fun CharSequence.linkEnd(start: Int, end: Int): Int {
    val searchEnd = inlineTokenEnd(start, end)
    val openingBracket = if (this[start] == '!') start + 1 else start
    val labelEnd = findUnescaped(']', openingBracket + 1, searchEnd)
    if (labelEnd < 0 || labelEnd + 1 >= searchEnd) return -1

    return when (this[labelEnd + 1]) {
        '(' -> closingParenthesisEnd(labelEnd + 1, searchEnd)
        '[' -> {
            val referenceEnd = findUnescaped(']', labelEnd + 2, searchEnd)
            if (referenceEnd >= 0) referenceEnd + 1 else -1
        }
        else -> -1
    }
}

private fun CharSequence.closingParenthesisEnd(start: Int, end: Int): Int {
    var index = start
    var depth = 0
    var quote: Char? = null
    while (index < end) {
        val current = this[index]
        if (current == '\\') {
            index += 2
            continue
        }
        val activeQuote = quote
        if (activeQuote != null) {
            if (current == activeQuote) quote = null
        } else {
            when (current) {
                '\'', '"' -> quote = current
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index + 1
                }
            }
        }
        index++
    }
    return -1
}

private fun CharSequence.htmlTagEnd(start: Int, end: Int): Int {
    val searchEnd = inlineTokenEnd(start, end)
    if (start + 1 >= searchEnd) return -1
    val first = this[start + 1]
    if (!(first.isLetter() || first in charArrayOf('/', '!', '?'))) return -1

    var index = start + 2
    var quote: Char? = null
    while (index < searchEnd) {
        val current = this[index]
        if (current == '\\') {
            index += 2
            continue
        }
        val activeQuote = quote
        if (activeQuote != null) {
            if (current == activeQuote) quote = null
        } else {
            when (current) {
                '\'', '"' -> quote = current
                '>' -> return index + 1
            }
        }
        index++
    }
    return -1
}

private fun CharSequence.autolinkEnd(start: Int, end: Int): Int {
    val closingBracket = findUnescaped('>', start + 1, inlineTokenEnd(start, end))
    if (closingBracket < 0) return -1

    var hasSchemeSeparator = false
    var hasEmailSeparator = false
    for (index in start + 1 until closingBracket) {
        val current = this[index]
        if (current.isWhitespace() || current == '<') return -1
        if (current == ':') hasSchemeSeparator = true
        if (current == '@') hasEmailSeparator = true
    }
    return if (hasSchemeSeparator || hasEmailSeparator) closingBracket + 1 else -1
}

private fun emphasisMarkerLength(marker: Char, availableRun: Int): Int {
    return when (marker) {
        '~' -> if (availableRun >= 2) 2 else -1
        '*', '_' -> availableRun.coerceAtMost(3)
        else -> -1
    }
}

private fun CharSequence.canOpenEmphasis(start: Int, markerLength: Int, end: Int): Boolean {
    val contentStart = start + markerLength
    if (contentStart >= end || this[contentStart].isWhitespace()) return false
    if (this[start] == '_' && start > 0 && this[start - 1].isLetterOrDigit()) return false
    return true
}

private fun CharSequence.emphasisEnd(start: Int, markerLength: Int, end: Int): Int {
    val marker = this[start]
    val contentStart = start + markerLength
    val searchEnd = inlineTokenEnd(start, end)
    var closingStart = findMatchingRun(marker, markerLength, contentStart, searchEnd)
    while (closingStart >= 0) {
        val afterClosing = closingStart + markerLength
        val validClosing =
            closingStart > contentStart &&
                !this[closingStart - 1].isWhitespace() &&
                (marker != '_' || afterClosing == end || !this[afterClosing].isLetterOrDigit())
        if (validClosing) return afterClosing
        closingStart = findMatchingRun(marker, markerLength, afterClosing, searchEnd)
    }
    return -1
}

private fun CharSequence.findMatchingRun(
    marker: Char,
    length: Int,
    start: Int,
    end: Int,
): Int {
    var index = start
    while (index < end) {
        if (this[index] == '\\') {
            index += 2
            continue
        }
        if (this[index] == marker) {
            val candidateLength = runLength(index, end, marker)
            if (candidateLength == length) return index
            index += candidateLength
        } else {
            index++
        }
    }
    return -1
}

private fun CharSequence.findUnescaped(target: Char, start: Int, end: Int): Int {
    var index = start
    while (index < end) {
        if (this[index] == '\\') {
            index += 2
        } else if (this[index] == target) {
            return index
        } else {
            index++
        }
    }
    return -1
}

private fun CharSequence.contains(target: Char, start: Int, end: Int): Boolean {
    for (index in start until end) {
        if (this[index] == target) return true
    }
    return false
}

private fun CharSequence.findChar(target: Char, start: Int): Int {
    for (index in start until length) {
        if (this[index] == target) return index
    }
    return -1
}

private fun CharSequence.findSequence(target: String, start: Int, end: Int): Int {
    var index = start
    while (index + target.length <= end) {
        if (hasPrefix(target, index, end)) return index
        index++
    }
    return -1
}

private fun CharSequence.hasPrefix(target: String, start: Int, end: Int): Boolean {
    if (start < 0 || start + target.length > end) return false
    for (offset in target.indices) {
        if (this[start + offset] != target[offset]) return false
    }
    return true
}

private fun CharSequence.runLength(start: Int, end: Int, marker: Char): Int {
    var index = start
    while (index < end && this[index] == marker) index++
    return index - start
}

private fun CharSequence.skipWhitespace(start: Int, end: Int): Int {
    var index = start
    while (index < end && this[index].isWhitespace()) index++
    return index
}

private fun emitRange(
    emit: (MarkdownSyntaxKind, Int, Int) -> Unit,
    kind: MarkdownSyntaxKind,
    start: Int,
    end: Int,
) {
    if (start < end) emit(kind, start, end)
}

private fun nextLineStart(newline: Int, textLength: Int): Int =
    if (newline >= 0) newline + 1 else textLength

private fun inlineTokenEnd(start: Int, lineEnd: Int): Int =
    (start + MAX_INLINE_TOKEN_LENGTH).coerceAtMost(lineEnd)
