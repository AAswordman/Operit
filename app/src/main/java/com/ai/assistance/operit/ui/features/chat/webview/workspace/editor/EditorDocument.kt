package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.text.SpannableStringBuilder
import kotlin.math.max
import kotlin.math.min

private const val EDITOR_TAB_SPACES = 4
private const val ZERO_WIDTH_JOINER = 0x200D
private const val COMBINING_KEYCAP = 0x20E3
private const val MAX_SYMBOL_BACKTRACK_CODE_POINTS = 12

internal fun editorCellWidth(char: Char): Int {
    if (char == '\t') {
        return EDITOR_TAB_SPACES
    }
    return editorCellWidth(char.code)
}

internal fun editorCellWidth(codePoint: Int): Int {
    if (codePoint == '\t'.code) {
        return EDITOR_TAB_SPACES
    }
    return if (isWideEditorCodePoint(codePoint) || isEmojiLikeCodePoint(codePoint)) 2 else 1
}

internal fun editorCodeUnitLength(
    text: CharSequence,
    offset: Int,
    limit: Int = text.length
): Int {
    val safeLimit = limit.coerceIn(0, text.length)
    val safeOffset = offset.coerceIn(0, safeLimit)
    if (safeOffset >= safeLimit) {
        return 0
    }
    val current = text[safeOffset]
    return if (
        Character.isHighSurrogate(current) &&
            safeOffset + 1 < safeLimit &&
            Character.isLowSurrogate(text[safeOffset + 1])
    ) {
        2
    } else {
        1
    }
}

internal fun editorNextCharacterOffset(
    text: CharSequence,
    offset: Int,
    limit: Int = text.length
): Int {
    val safeLimit = limit.coerceIn(0, text.length)
    val safeOffset = offset.coerceIn(0, safeLimit)
    return (safeOffset + editorCodeUnitLength(text, safeOffset, safeLimit)).coerceAtMost(safeLimit)
}

internal fun editorPreviousCharacterOffset(text: CharSequence, offset: Int): Int {
    val safeOffset = offset.coerceIn(0, text.length)
    if (safeOffset <= 0) {
        return 0
    }
    val candidate = safeOffset - 1
    return if (
        candidate > 0 &&
            Character.isLowSurrogate(text[candidate]) &&
            Character.isHighSurrogate(text[candidate - 1])
    ) {
        candidate - 1
    } else {
        candidate
    }
}

internal fun editorNextSymbolOffset(
    text: CharSequence,
    offset: Int,
    limit: Int = text.length
): Int {
    val safeLimit = limit.coerceIn(0, text.length)
    val safeOffset = offset.coerceIn(0, safeLimit)
    if (safeOffset >= safeLimit) {
        return safeLimit
    }

    val firstChar = text[safeOffset]
    if (firstChar == '\n' || firstChar == '\t') {
        return (safeOffset + 1).coerceAtMost(safeLimit)
    }

    val firstCodePoint = Character.codePointAt(text, safeOffset)
    var end = editorNextCharacterOffset(text, safeOffset, safeLimit)

    if (isRegionalIndicator(firstCodePoint) && end < safeLimit) {
        val nextCodePoint = Character.codePointAt(text, end)
        if (isRegionalIndicator(nextCodePoint)) {
            end = (end + Character.charCount(nextCodePoint)).coerceAtMost(safeLimit)
        }
    }

    while (end < safeLimit) {
        val codePoint = Character.codePointAt(text, end)
        when {
            codePoint == ZERO_WIDTH_JOINER -> {
                end = (end + Character.charCount(codePoint)).coerceAtMost(safeLimit)
                if (end < safeLimit) {
                    end = editorNextCharacterOffset(text, end, safeLimit)
                }
            }

            isVariationSelector(codePoint) ||
                isEmojiModifier(codePoint) ||
                isCombiningMark(codePoint) ||
                codePoint == COMBINING_KEYCAP -> {
                end = (end + Character.charCount(codePoint)).coerceAtMost(safeLimit)
            }

            else -> return end
        }
    }

    return end
}

internal fun editorPreviousSymbolOffset(text: CharSequence, offset: Int): Int {
    val safeOffset = offset.coerceIn(0, text.length)
    if (safeOffset <= 0) {
        return 0
    }

    var probe = safeOffset
    repeat(MAX_SYMBOL_BACKTRACK_CODE_POINTS) {
        probe = editorPreviousCharacterOffset(text, probe)
        if (editorNextSymbolOffset(text, probe) >= safeOffset) {
            return probe
        }
        if (probe <= 0) {
            return 0
        }
    }

    return editorPreviousCharacterOffset(text, safeOffset)
}

internal fun editorCellWidth(
    text: CharSequence,
    offset: Int,
    limit: Int = text.length
): Int {
    val safeLimit = limit.coerceIn(0, text.length)
    val safeOffset = offset.coerceIn(0, safeLimit)
    if (safeOffset >= safeLimit) {
        return 0
    }
    if (text[safeOffset] == '\t') {
        return EDITOR_TAB_SPACES
    }

    val firstCodePoint = Character.codePointAt(text, safeOffset)
    if (editorCellWidth(firstCodePoint) >= 2) {
        return 2
    }

    val end = editorNextSymbolOffset(text, safeOffset, safeLimit)
    var index = editorNextCharacterOffset(text, safeOffset, safeLimit)
    while (index < end) {
        val codePoint = Character.codePointAt(text, index)
        if (
            isEmojiLikeCodePoint(codePoint) ||
                isEmojiModifier(codePoint) ||
                isRegionalIndicator(codePoint) ||
                isVariationSelector(codePoint) ||
                codePoint == ZERO_WIDTH_JOINER ||
                codePoint == COMBINING_KEYCAP
        ) {
            return 2
        }
        index += Character.charCount(codePoint)
    }

    return 1
}

internal fun editorSnapOffsetToCharacterBoundary(text: CharSequence, offset: Int): Int {
    val safeOffset = offset.coerceIn(0, text.length)
    return if (
        safeOffset in 1 until text.length &&
            Character.isLowSurrogate(text[safeOffset]) &&
            Character.isHighSurrogate(text[safeOffset - 1])
    ) {
        safeOffset - 1
    } else {
        safeOffset
    }
}

internal fun editorExpandOffsetToCharacterEnd(text: CharSequence, offset: Int): Int {
    val safeOffset = offset.coerceIn(0, text.length)
    return if (
        safeOffset in 1 until text.length &&
            Character.isLowSurrogate(text[safeOffset]) &&
            Character.isHighSurrogate(text[safeOffset - 1])
    ) {
        (safeOffset + 1).coerceAtMost(text.length)
    } else {
        safeOffset
    }
}

private fun isWideEditorCodePoint(codePoint: Int): Boolean {
    return when {
        codePoint in 0x4E00..0x9FFF -> true
        codePoint in 0x3400..0x4DBF -> true
        codePoint in 0x20000..0x2A6DF -> true
        codePoint in 0x2A700..0x2B73F -> true
        codePoint in 0x2B740..0x2B81F -> true
        codePoint in 0x2B820..0x2CEAF -> true
        codePoint in 0x3040..0x309F -> true
        codePoint in 0x30A0..0x30FF -> true
        codePoint in 0xAC00..0xD7AF -> true
        codePoint in 0xFF00..0xFFEF -> true
        codePoint in 0x3000..0x303F -> true
        else -> false
    }
}

private fun isEmojiLikeCodePoint(codePoint: Int): Boolean {
    return when {
        codePoint in 0x1F000..0x1FAFF -> true
        codePoint in 0x2600..0x27BF -> true
        else -> false
    }
}

private fun isEmojiModifier(codePoint: Int): Boolean {
    return codePoint in 0x1F3FB..0x1F3FF
}

private fun isRegionalIndicator(codePoint: Int): Boolean {
    return codePoint in 0x1F1E6..0x1F1FF
}

private fun isVariationSelector(codePoint: Int): Boolean {
    return codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF
}

private fun isCombiningMark(codePoint: Int): Boolean {
    return when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }
}

internal data class EditorEditCommand(
    val start: Int,
    val beforeText: String,
    val afterText: String,
    val beforeSelectionStart: Int,
    val beforeSelectionEnd: Int,
    val afterSelectionStart: Int,
    val afterSelectionEnd: Int
)

internal class EditorDocument(initialText: String = "") {
    private val buffer = SpannableStringBuilder(initialText)
    private val undoStack = ArrayDeque<EditorEditCommand>()
    private val redoStack = ArrayDeque<EditorEditCommand>()
    private val lineStarts = mutableListOf<Int>()

    var selectionStart: Int = 0
        private set
    var selectionEnd: Int = 0
        private set
    var composingStart: Int = -1
        private set
    var composingEnd: Int = -1
        private set
    var version: Int = 0
        private set
    var maxLineCells: Int = 0
        private set

    private data class OffsetEdit(
        val position: Int,
        val removedLength: Int,
        val insertedLength: Int
    )

    init {
        rebuildMetadata()
        collapseSelection(length())
    }

    fun text(): CharSequence = buffer

    fun textString(): String = buffer.toString()

    fun length(): Int = buffer.length

    fun lineCount(): Int = lineStarts.size.coerceAtLeast(1)

    fun hasSelection(): Boolean = selectionStart != selectionEnd

    fun hasComposingRegion(): Boolean = composingStart >= 0 && composingEnd >= composingStart

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun setText(newText: String, clearHistory: Boolean = true) {
        if (newText == buffer.toString()) {
            if (clearHistory) {
                clearHistory()
            }
            clearComposingRegion()
            collapseSelection(newText.length)
            return
        }
        replaceRangeInternal(
            start = 0,
            end = buffer.length,
            replacement = newText,
            afterSelectionStart = newText.length,
            afterSelectionEnd = newText.length,
            recordHistory = false
        )
        if (clearHistory) {
            clearHistory()
        }
    }

    fun replaceAllText(newText: String) {
        replaceRangeInternal(
            start = 0,
            end = buffer.length,
            replacement = newText,
            afterSelectionStart = newText.length,
            afterSelectionEnd = newText.length,
            recordHistory = true
        )
    }

    fun insertTextAtCursor(text: String) {
        val range = activeReplaceRange()
        val cursor = range.first + text.length
        replaceRangeInternal(
            start = range.first,
            end = range.last,
            replacement = text,
            afterSelectionStart = cursor,
            afterSelectionEnd = cursor,
            recordHistory = true
        )
    }

    fun insertTextProgrammatically(start: Int, text: String) {
        val safeStart = start.coerceIn(0, buffer.length)
        val cursor = safeStart + text.length
        replaceRangeInternal(
            start = safeStart,
            end = safeStart,
            replacement = text,
            afterSelectionStart = cursor,
            afterSelectionEnd = cursor,
            recordHistory = true
        )
    }

    fun applyCompletion(prefixLength: Int, replacement: String) {
        val cursor = selectionEnd
        val start = (cursor - prefixLength).coerceAtLeast(0)
        replaceRangeInternal(
            start = start,
            end = cursor,
            replacement = replacement,
            afterSelectionStart = start + replacement.length,
            afterSelectionEnd = start + replacement.length,
            recordHistory = true
        )
    }

    fun replaceSelection(text: String, recordHistory: Boolean = true) {
        val range = activeReplaceRange()
        val cursor = range.first + text.length
        replaceRangeInternal(
            start = range.first,
            end = range.last,
            replacement = text,
            afterSelectionStart = cursor,
            afterSelectionEnd = cursor,
            recordHistory = recordHistory
        )
    }

    fun setComposingText(text: String) {
        val range = activeReplaceRange()
        val end = range.first + text.length
        replaceRangeInternal(
            start = range.first,
            end = range.last,
            replacement = text,
            afterSelectionStart = end,
            afterSelectionEnd = end,
            recordHistory = false
        )
        composingStart = range.first
        composingEnd = end
    }

    fun finishComposingText() {
        clearComposingRegion()
    }

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        if (buffer.isEmpty()) return
        val start =
            editorSnapOffsetToCharacterBoundary(
                buffer,
                (selectionEnd - beforeLength).coerceAtLeast(0)
            )
        val end =
            editorExpandOffsetToCharacterEnd(
                buffer,
                (selectionEnd + afterLength).coerceAtMost(buffer.length)
            )
        if (start == end) return
        replaceRangeInternal(
            start = start,
            end = end,
            replacement = "",
            afterSelectionStart = start,
            afterSelectionEnd = start,
            recordHistory = true
        )
    }

    fun deleteBackward() {
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        if (selectionEnd <= 0) return
        val deleteStart = previousCursorOffset(selectionEnd)
        replaceRangeInternal(
            start = deleteStart,
            end = selectionEnd,
            replacement = "",
            afterSelectionStart = deleteStart,
            afterSelectionEnd = deleteStart,
            recordHistory = true
        )
    }

    fun deleteForward() {
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        if (selectionEnd >= buffer.length) return
        val deleteEnd = nextCursorOffset(selectionEnd)
        replaceRangeInternal(
            start = selectionEnd,
            end = deleteEnd,
            replacement = "",
            afterSelectionStart = selectionEnd,
            afterSelectionEnd = selectionEnd,
            recordHistory = true
        )
    }

    fun deleteSurroundingCodePoints(beforeLength: Int, afterLength: Int) {
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        if (buffer.isEmpty()) return

        var start = selectionEnd
        repeat(beforeLength.coerceAtLeast(0)) {
            start = editorPreviousCharacterOffset(buffer, start)
        }

        var end = selectionEnd
        repeat(afterLength.coerceAtLeast(0)) {
            end = editorNextCharacterOffset(buffer, end)
        }

        if (start == end) return
        replaceRangeInternal(
            start = start,
            end = end,
            replacement = "",
            afterSelectionStart = start,
            afterSelectionEnd = start,
            recordHistory = true
        )
    }

    fun insertNewlineWithIndent() {
        val cursor = selectionEnd
        val currentLine = getLineForOffset(cursor)
        val lineStart = getLineStart(currentLine)
        val lineEnd = getLineEnd(currentLine)
        val currentLineText = buffer.subSequence(lineStart, lineEnd)

        val indent = buildString {
            for (i in currentLineText.indices) {
                val char = currentLineText[i]
                if (char == ' ' || char == '\t') {
                    append(char)
                } else {
                    break
                }
            }
        }

        val shouldIncreaseIndent = cursor > 0 && buffer[cursor - 1] in charArrayOf('{', '[', '(')
        val insertion = if (shouldIncreaseIndent) {
            "\n${indent}    \n$indent"
        } else {
            "\n$indent"
        }

        val insertionCursor = if (shouldIncreaseIndent) {
            cursor + 1 + indent.length + 4
        } else {
            cursor + insertion.length
        }

        replaceRangeInternal(
            start = cursor,
            end = cursor,
            replacement = insertion,
            afterSelectionStart = insertionCursor,
            afterSelectionEnd = insertionCursor,
            recordHistory = true
        )
    }

    fun unindentSelection(spaces: Int = EDITOR_TAB_SPACES): Boolean {
        val (firstLine, lastLine) = selectedLineBounds()
        val rangeStart = getLineStart(firstLine)
        val rangeEnd = getLineEnd(lastLine)
        val original = buffer.substring(rangeStart, rangeEnd)
        val edits = mutableListOf<OffsetEdit>()
        val removeSpaces = spaces.coerceAtLeast(1)
        val lines = original.split('\n')
        val updated = buildString {
            var oldLineStart = rangeStart
            lines.forEachIndexed { index, line ->
                val removeLength = when {
                    line.startsWith('\t') -> 1
                    else -> line.take(removeSpaces).takeWhile { it == ' ' }.length
                }
                if (removeLength > 0) {
                    edits += OffsetEdit(oldLineStart, removeLength, 0)
                    append(line.substring(removeLength))
                } else {
                    append(line)
                }
                if (index != lines.lastIndex) {
                    append('\n')
                    oldLineStart += line.length + 1
                }
            }
        }
        if (edits.isEmpty()) return false

        replaceRangeInternal(
            start = rangeStart,
            end = rangeEnd,
            replacement = updated,
            afterSelectionStart = mapOffsetAfterEdits(selectionStart, edits),
            afterSelectionEnd = mapOffsetAfterEdits(selectionEnd, edits),
            recordHistory = true
        )
        return true
    }

    fun toggleLineComment(prefix: String): Boolean {
        if (prefix.isEmpty()) return false
        val commentCurrentLine = !hasSelection()
        val (firstLine, lastLine) = selectedLineBounds()
        val rangeStart = getLineStart(firstLine)
        val rangeEnd = getLineEnd(lastLine)
        val original = buffer.substring(rangeStart, rangeEnd)
        val lines = original.split('\n')
        val nonBlankLines = lines.filter { it.any { character -> !character.isWhitespace() } }
        if (nonBlankLines.isEmpty() && !commentCurrentLine) return false

        val allCommented = nonBlankLines.isNotEmpty() && nonBlankLines.all { line ->
            val firstNonWhitespace = line.indexOfFirst { !it.isWhitespace() }
            firstNonWhitespace >= 0 && line.startsWith(prefix, firstNonWhitespace)
        }
        val edits = mutableListOf<OffsetEdit>()
        val updated = buildString {
            var oldLineStart = rangeStart
            lines.forEachIndexed { index, line ->
                val firstNonWhitespace = line.indexOfFirst { !it.isWhitespace() }
                if (firstNonWhitespace < 0) {
                    append(line)
                    if (commentCurrentLine) {
                        val inserted = "$prefix "
                        edits += OffsetEdit(oldLineStart + line.length, 0, inserted.length)
                        append(inserted)
                    }
                } else if (allCommented) {
                    val prefixEnd = firstNonWhitespace + prefix.length
                    val hasSpaceAfterPrefix =
                        line.startsWith(prefix, firstNonWhitespace) &&
                            prefixEnd < line.length &&
                            line[prefixEnd] == ' '
                    val removeLength = prefix.length + if (hasSpaceAfterPrefix) 1 else 0
                    if (line.startsWith(prefix, firstNonWhitespace)) {
                        edits += OffsetEdit(oldLineStart + firstNonWhitespace, removeLength, 0)
                        append(line.removeRange(firstNonWhitespace, firstNonWhitespace + removeLength))
                    } else {
                        append(line)
                    }
                } else {
                    val inserted = "$prefix "
                    edits += OffsetEdit(oldLineStart + firstNonWhitespace, 0, inserted.length)
                    append(line.substring(0, firstNonWhitespace))
                    append(inserted)
                    append(line.substring(firstNonWhitespace))
                }
                if (index != lines.lastIndex) {
                    append('\n')
                    oldLineStart += line.length + 1
                }
            }
        }
        if (edits.isEmpty()) return false

        replaceRangeInternal(
            start = rangeStart,
            end = rangeEnd,
            replacement = updated,
            afterSelectionStart = mapOffsetAfterEdits(selectionStart, edits),
            afterSelectionEnd = mapOffsetAfterEdits(selectionEnd, edits),
            recordHistory = true
        )
        return true
    }

    fun toggleBlockComment(startMarker: String, endMarker: String): Boolean {
        if (startMarker.isEmpty() || endMarker.isEmpty()) return false

        val hadSelection = hasSelection()
        val line = getLineForOffset(selectionEnd)
        val start = if (hadSelection) min(selectionStart, selectionEnd) else getLineStart(line)
        val end = if (hadSelection) max(selectionStart, selectionEnd) else getLineEnd(line)

        val selected = buffer.substring(start, end)
        val alreadyCommented =
            selected.startsWith(startMarker) && selected.endsWith(endMarker)
        val replacement = if (alreadyCommented) {
            selected.removePrefix(startMarker).removeSuffix(endMarker)
        } else {
            "$startMarker$selected$endMarker"
        }
        if (replacement == selected) return false

        val reversedSelection = selectionStart > selectionEnd
        val newStart = start
        val newEnd = start + replacement.length
        val newCursor = if (alreadyCommented) (selectionEnd - startMarker.length).coerceIn(newStart, newEnd)
            else selectionEnd + startMarker.length
        replaceRangeInternal(
            start = start,
            end = end,
            replacement = replacement,
            afterSelectionStart = if (!hadSelection) newCursor else if (reversedSelection) newEnd else newStart,
            afterSelectionEnd = if (!hadSelection) newCursor else if (reversedSelection) newStart else newEnd,
            recordHistory = true
        )
        return true
    }

    private fun selectedLineBounds(): Pair<Int, Int> {
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        val lastOffset = if (end > start) end - 1 else start
        return getLineForOffset(start) to getLineForOffset(lastOffset)
    }

    private fun mapOffsetAfterEdits(offset: Int, edits: List<OffsetEdit>): Int {
        var delta = 0
        for (edit in edits.sortedBy { it.position }) {
            if (offset < edit.position) break
            if (edit.removedLength > 0 && offset < edit.position + edit.removedLength) {
                return edit.position + delta
            }
            delta += edit.insertedLength - edit.removedLength
        }
        return offset + delta
    }

    fun setSelection(start: Int, end: Int) {
        selectionStart = editorSnapOffsetToCharacterBoundary(buffer, start)
        selectionEnd = editorSnapOffsetToCharacterBoundary(buffer, end)
        clearComposingRegion()
    }

    fun collapseSelection(offset: Int) {
        setSelection(offset, offset)
    }

    fun selectWordAt(offset: Int) {
        if (buffer.isEmpty()) {
            collapseSelection(0)
            return
        }
        val safeOffset = offset.coerceIn(0, buffer.length)
        val anchor = when {
            safeOffset < buffer.length && isWordChar(buffer[safeOffset]) -> safeOffset
            safeOffset > 0 && isWordChar(buffer[safeOffset - 1]) -> safeOffset - 1
            safeOffset < buffer.length -> safeOffset
            else -> buffer.length - 1
        }

        if (!isWordChar(buffer[anchor])) {
            setSelection(anchor, min(anchor + 1, buffer.length))
            return
        }

        var start = anchor
        var end = anchor + 1
        while (start > 0 && isWordChar(buffer[start - 1])) {
            start--
        }
        while (end < buffer.length && isWordChar(buffer[end])) {
            end++
        }
        setSelection(start, end)
    }

    fun selectAll() {
        setSelection(0, buffer.length)
    }

    fun undo(): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        applyCommand(command, reverse = true, targetStack = redoStack)
        return true
    }

    fun redo(): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        applyCommand(command, reverse = false, targetStack = undoStack)
        return true
    }

    fun getLineForOffset(offset: Int): Int {
        if (lineStarts.isEmpty()) return 0
        val safeOffset = offset.coerceIn(0, buffer.length)
        var low = 0
        var high = lineStarts.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val lineStart = lineStarts[mid]
            val nextStart = if (mid == lineStarts.lastIndex) buffer.length + 1 else lineStarts[mid + 1]
            when {
                safeOffset < lineStart -> high = mid - 1
                safeOffset >= nextStart -> low = mid + 1
                else -> return mid
            }
        }
        return lineStarts.lastIndex
    }

    fun getLineStart(line: Int): Int {
        if (lineStarts.isEmpty()) return 0
        return lineStarts[line.coerceIn(0, lineStarts.lastIndex)]
    }

    fun getLineEnd(line: Int): Int {
        if (lineStarts.isEmpty()) return 0
        val safeLine = line.coerceIn(0, lineStarts.lastIndex)
        val nextLineStart = if (safeLine == lineStarts.lastIndex) buffer.length else lineStarts[safeLine + 1]
        val rawEnd = (nextLineStart - 1).coerceAtLeast(lineStarts[safeLine])
        return if (rawEnd < buffer.length && rawEnd >= 0 && buffer[rawEnd] == '\n') rawEnd else nextLineStart
    }

    fun getLineText(line: Int): CharSequence {
        val start = getLineStart(line)
        val end = getLineEnd(line)
        return buffer.subSequence(start, end)
    }

    fun getCellColumnForOffset(offset: Int): Int {
        val safeOffset = editorSnapOffsetToCharacterBoundary(buffer, offset)
        val line = getLineForOffset(safeOffset)
        val lineStart = getLineStart(line)
        val lineEnd = getLineEnd(line)
        var cells = 0
        var i = lineStart
        while (i < safeOffset && i < lineEnd) {
            val next = editorNextSymbolOffset(buffer, i, lineEnd)
            if (next > safeOffset) {
                break
            }
            cells += editorCellWidth(buffer, i, lineEnd)
            i = next
        }
        return cells
    }

    fun getOffsetForLineAndCellColumn(line: Int, targetCells: Int): Int {
        val safeLine = line.coerceIn(0, lineCount() - 1)
        val lineStart = getLineStart(safeLine)
        val lineEnd = getLineEnd(safeLine)
        if (targetCells <= 0) return lineStart

        var cells = 0
        var offset = lineStart
        while (offset < lineEnd) {
            val nextOffset = editorNextSymbolOffset(buffer, offset, lineEnd)
            val cellWidth = editorCellWidth(buffer, offset, lineEnd)
            val nextCells = cells + cellWidth
            if (targetCells < nextCells) {
                return if (targetCells - cells >= cellWidth / 2f) nextOffset else offset
            }
            cells = nextCells
            offset = nextOffset
        }
        return lineEnd
    }

    fun previousCursorOffset(offset: Int): Int {
        return editorPreviousSymbolOffset(buffer, offset)
    }

    fun nextCursorOffset(offset: Int): Int {
        val safeOffset = editorSnapOffsetToCharacterBoundary(buffer, offset)
        return editorNextSymbolOffset(buffer, safeOffset)
    }

    fun lineDigits(): Int = max(1, lineCount().toString().length)

    private fun activeReplaceRange(): IntRange {
        return if (hasComposingRegion()) {
            composingStart..composingEnd
        } else {
            normalizedSelection()
        }
    }

    private fun normalizedSelection(): IntRange {
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        return start..end
    }

    private fun replaceRangeInternal(
        start: Int,
        end: Int,
        replacement: String,
        afterSelectionStart: Int,
        afterSelectionEnd: Int,
        recordHistory: Boolean
    ) {
        val safeStart = start.coerceIn(0, buffer.length)
        val safeEnd = end.coerceIn(safeStart, buffer.length)
        val beforeText = buffer.subSequence(safeStart, safeEnd).toString()

        if (!recordHistory && beforeText == replacement) {
            selectionStart = afterSelectionStart.coerceIn(0, buffer.length)
            selectionEnd = afterSelectionEnd.coerceIn(0, buffer.length)
            clearComposingRegion()
            return
        }

        val command = if (recordHistory) {
            EditorEditCommand(
                start = safeStart,
                beforeText = beforeText,
                afterText = replacement,
                beforeSelectionStart = selectionStart,
                beforeSelectionEnd = selectionEnd,
                afterSelectionStart = afterSelectionStart,
                afterSelectionEnd = afterSelectionEnd
            )
        } else {
            null
        }

        buffer.replace(safeStart, safeEnd, replacement)
        selectionStart = afterSelectionStart.coerceIn(0, buffer.length)
        selectionEnd = afterSelectionEnd.coerceIn(0, buffer.length)
        clearComposingRegion()
        rebuildMetadata()
        version++

        if (recordHistory && command != null) {
            undoStack.addLast(command)
            redoStack.clear()
        }
    }

    private fun applyCommand(
        command: EditorEditCommand,
        reverse: Boolean,
        targetStack: ArrayDeque<EditorEditCommand>
    ) {
        val replaceStart = command.start.coerceIn(0, buffer.length)
        val currentEnd = if (reverse) {
            (replaceStart + command.afterText.length).coerceIn(replaceStart, buffer.length)
        } else {
            (replaceStart + command.beforeText.length).coerceIn(replaceStart, buffer.length)
        }

        val replacement = if (reverse) command.beforeText else command.afterText
        buffer.replace(replaceStart, currentEnd, replacement)

        if (reverse) {
            selectionStart = command.beforeSelectionStart.coerceIn(0, buffer.length)
            selectionEnd = command.beforeSelectionEnd.coerceIn(0, buffer.length)
        } else {
            selectionStart = command.afterSelectionStart.coerceIn(0, buffer.length)
            selectionEnd = command.afterSelectionEnd.coerceIn(0, buffer.length)
        }

        clearComposingRegion()
        rebuildMetadata()
        version++
        targetStack.addLast(command)
    }

    private fun clearComposingRegion() {
        composingStart = -1
        composingEnd = -1
    }

    private fun rebuildMetadata() {
        lineStarts.clear()
        lineStarts.add(0)

        var currentCells = 0
        var maxCells = 0
        var index = 0
        while (index < buffer.length) {
            val char = buffer[index]
            if (char == '\n') {
                maxCells = max(maxCells, currentCells)
                if (index + 1 <= buffer.length) {
                    lineStarts.add(index + 1)
                }
                currentCells = 0
                index++
            } else {
                currentCells += editorCellWidth(buffer, index)
                index = editorNextSymbolOffset(buffer, index)
            }
        }
        maxCells = max(maxCells, currentCells)
        maxLineCells = maxCells
        if (lineStarts.isEmpty()) {
            lineStarts.add(0)
        }
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }
}
