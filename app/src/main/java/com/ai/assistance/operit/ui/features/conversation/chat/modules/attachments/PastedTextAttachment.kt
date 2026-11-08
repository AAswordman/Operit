package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments

import androidx.compose.ui.text.input.TextFieldValue

internal fun extractInsertedText(
    previous: TextFieldValue,
    proposed: TextFieldValue,
): String? {
    val previousText = previous.text
    val selectionStart = previous.selection.start.coerceIn(0, previousText.length)
    val selectionEnd = previous.selection.end.coerceIn(0, previousText.length)
    val insertionStart = minOf(selectionStart, selectionEnd)
    val insertionEnd = maxOf(selectionStart, selectionEnd)
    val prefix = previousText.substring(0, insertionStart)
    val suffix = previousText.substring(insertionEnd)

    if (!proposed.text.startsWith(prefix) || !proposed.text.endsWith(suffix)) return null

    val pastedEnd = proposed.text.length - suffix.length
    if (pastedEnd < prefix.length) return null

    return proposed.text.substring(prefix.length, pastedEnd)
}

internal fun extractClipboardPastedText(
    previous: TextFieldValue,
    proposed: TextFieldValue,
    clipboardText: String,
): String? {
    if (clipboardText.isEmpty()) return null

    val insertedText = extractInsertedText(previous, proposed) ?: return null
    return insertedText.takeIf {
        normalizeLineEndings(it) == normalizeLineEndings(clipboardText)
    }
}

private fun normalizeLineEndings(text: String): String {
    return text.replace("\r\n", "\n").replace('\r', '\n')
}
