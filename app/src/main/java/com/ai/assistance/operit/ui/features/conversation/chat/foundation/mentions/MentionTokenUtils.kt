package com.ai.assistance.operit.ui.features.conversation.chat.foundation.mentions

data class MentionTokenRange(
    val triggerChar: Char,
    val start: Int,
    val contentEndExclusive: Int,
    val endExclusive: Int,
) {
    val hasTrailingWhitespace: Boolean
        get() = endExclusive > contentEndExclusive
}

private val mentionTriggerChars = setOf('@', '/')

private fun isBaseMentionContinuation(char: Char): Boolean {
    return (char.code in 0..127 && char.isLetterOrDigit()) ||
        char == '.' ||
        char == '_' ||
        char == '%' ||
        char == '+' ||
        char == '-'
}

fun isMentionContinuation(char: Char): Boolean {
    return isMentionContinuation(char, '@')
}

fun isMentionContinuation(char: Char, triggerChar: Char): Boolean {
    return when (triggerChar) {
        '@' -> isBaseMentionContinuation(char) || char == '/' || char == '\\'
        '/' -> isBaseMentionContinuation(char)
        else -> isBaseMentionContinuation(char)
    }
}

private fun isValidMentionTrigger(text: String, index: Int, triggerChar: Char): Boolean {
    if (triggerChar !in mentionTriggerChars) return false
    if (index == 0) return true
    val previousChar = text[index - 1]
    return when (triggerChar) {
        '@' -> !isBaseMentionContinuation(previousChar)
        '/' -> previousChar.isWhitespace()
        else -> false
    }
}

fun findMentionTokens(text: String): List<MentionTokenRange> {
    if (text.none { it in mentionTriggerChars }) {
        return emptyList()
    }

    val tokens = mutableListOf<MentionTokenRange>()
    var index = 0
    while (index < text.length) {
        val triggerChar = text[index]
        if (triggerChar !in mentionTriggerChars) {
            index += 1
            continue
        }
        if (!isValidMentionTrigger(text, index, triggerChar)) {
            index += 1
            continue
        }

        var contentEnd = index + 1
        while (contentEnd < text.length && isMentionContinuation(text[contentEnd], triggerChar)) {
            contentEnd += 1
        }
        if (contentEnd == index + 1) {
            index += 1
            continue
        }

        val endExclusive =
            if (contentEnd < text.length && text[contentEnd].isWhitespace()) {
                contentEnd + 1
            } else {
                contentEnd
            }
        tokens += MentionTokenRange(triggerChar, index, contentEnd, endExclusive)
        index = contentEnd
    }
    return tokens
}

fun findCommittedMentionTokens(text: String): List<MentionTokenRange> {
    return findMentionTokens(text).filter(MentionTokenRange::hasTrailingWhitespace)
}

fun findMentionTokenEndingAtCursor(text: String, cursor: Int): MentionTokenRange? {
    val safeCursor = cursor.coerceIn(0, text.length)
    return findMentionTokens(text).firstOrNull { token ->
        safeCursor == token.contentEndExclusive || safeCursor == token.endExclusive
    }
}
