package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio.source

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal enum class ThemeSourceToken { String, Comment, Keyword, Number, Function, Context }

private val javascriptKeywords = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
    "default", "delete", "do", "else", "export", "extends", "false", "finally", "for",
    "function", "if", "import", "in", "instanceof", "let", "new", "null", "of", "return",
    "static", "super", "switch", "this", "throw", "true", "try", "typeof", "undefined",
    "var", "void", "while", "with", "yield",
)
private val contextNames = setOf("module", "exports", "context", "Math", "JSON", "Object", "Array")

/** 只添加颜色 span，字符、换行和偏移不变，光标、查找和输入法组合仍由原文本框管理。 */
@Composable
internal fun rememberThemeSourceHighlighting(json: Boolean): VisualTransformation {
    val colors = MaterialTheme.colorScheme
    val stringColor = colors.primary
    val keywordColor = colors.tertiary
    val valueColor = colors.secondary
    val commentColor = colors.onSurfaceVariant
    return remember(json, stringColor, keywordColor, valueColor, commentColor) {
        VisualTransformation { source ->
            val styled = AnnotatedString.Builder(source)
            scanThemeSource(source.text, json) { kind, start, end ->
                val color = when (kind) {
                    ThemeSourceToken.String, ThemeSourceToken.Function -> stringColor
                    ThemeSourceToken.Keyword -> keywordColor
                    ThemeSourceToken.Number, ThemeSourceToken.Context -> valueColor
                    ThemeSourceToken.Comment -> commentColor
                }
                styled.addStyle(SpanStyle(color = color), start, end)
            }
            TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
        }
    }
}

/** 单次向前扫描，允许输入中的未闭合内容；字符串中的注释符号不会进入注释分支。 */
internal fun scanThemeSource(source: CharSequence, json: Boolean, emit: (ThemeSourceToken, Int, Int) -> Unit) {
    var index = 0
    while (index < source.length) {
        val start = index
        val current = source[index]
        when {
            current == '"' || (!json && (current == '\'' || current == '`')) -> {
                index = source.stringEnd(index, current)
                emit(ThemeSourceToken.String, start, index)
            }
            !json && current == '/' && index + 1 < source.length && source[index + 1] == '/' -> {
                index += 2
                while (index < source.length && source[index] != '\n' && source[index] != '\r') index++
                emit(ThemeSourceToken.Comment, start, index)
            }
            !json && current == '/' && index + 1 < source.length && source[index + 1] == '*' -> {
                index += 2
                while (index + 1 < source.length && !(source[index] == '*' && source[index + 1] == '/')) index++
                index = if (index + 1 < source.length) index + 2 else source.length
                emit(ThemeSourceToken.Comment, start, index)
            }
            !json && current.isDigit() -> {
                index = source.numberEnd(index)
                emit(ThemeSourceToken.Number, start, index)
            }
            !json && current.isIdentifierStart() -> {
                index++
                while (index < source.length && (source[index].isIdentifierStart() || source[index].isDigit())) index++
                val word = source.subSequence(start, index).toString()
                var next = index
                while (next < source.length && source[next].isWhitespace()) next++
                val kind = when {
                    word in javascriptKeywords -> ThemeSourceToken.Keyword
                    word in contextNames -> ThemeSourceToken.Context
                    next < source.length && source[next] == '(' -> ThemeSourceToken.Function
                    else -> null
                }
                if (kind != null) emit(kind, start, index)
            }
            else -> index++
        }
    }
}

private fun Char.isIdentifierStart(): Boolean = isLetter() || this == '_' || this == '$'

private fun CharSequence.stringEnd(start: Int, quote: Char): Int {
    var index = start + 1
    while (index < length) {
        when {
            this[index] == '\\' -> index = (index + 2).coerceAtMost(length)
            this[index] == quote -> return index + 1
            quote != '`' && (this[index] == '\n' || this[index] == '\r') -> return index
            else -> index++
        }
    }
    return length
}

private fun CharSequence.numberEnd(start: Int): Int {
    var index = start
    if (this[index] == '0' && index + 1 < length && this[index + 1] in "xXoObB") {
        index += 2
        while (index < length && (this[index].isDigit() || this[index] in "abcdefABCDEF_")) index++
    } else {
        while (index < length && (this[index].isDigit() || this[index] == '_')) index++
        if (index < length && this[index] == '.') {
            index++
            while (index < length && (this[index].isDigit() || this[index] == '_')) index++
        }
        if (index < length && this[index] in "eE") {
            index++
            if (index < length && this[index] in "+-") index++
            while (index < length && (this[index].isDigit() || this[index] == '_')) index++
        }
    }
    if (index < length && this[index] == 'n') index++
    return index
}
