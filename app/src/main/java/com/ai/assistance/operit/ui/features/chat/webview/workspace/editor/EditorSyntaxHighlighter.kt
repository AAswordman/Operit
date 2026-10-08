package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageFactory
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageSupport
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

internal data class HighlightSnapshot(
    val version: Int,
    val colors: IntArray
)

internal class EditorSyntaxHighlighter(
    initialLanguage: String,
    private val onResult: (HighlightSnapshot) -> Unit
) {
    companion object {}

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val latestRequestedVersion = AtomicInteger(0)

    @Volatile
    private var released = false
    @Volatile
    private var language = initialLanguage.lowercase()
    @Volatile
    private var languageSupport = LanguageFactory.getLanguageSupport(language)
    @Volatile
    private var commentSyntax = CommentSyntaxRegistry.forLanguage(language)

    fun setLanguage(language: String) {
        this.language = language.lowercase()
        languageSupport = LanguageFactory.getLanguageSupport(this.language)
        commentSyntax = CommentSyntaxRegistry.forLanguage(this.language)
    }

    fun requestHighlight(text: String, version: Int) {
        if (released) {
            return
        }
        latestRequestedVersion.set(version)
        val activeSupport = languageSupport
        val activeCommentSyntax = commentSyntax

        try {
            executor.execute {
                if (released) {
                    return@execute
                }

                val colors = IntArray(text.length)
                if (text.isNotEmpty()) {
                    parseFullText(text, colors, activeSupport, activeCommentSyntax)
                }
                if (released || latestRequestedVersion.get() != version) {
                    return@execute
                }
                mainHandler.post {
                    if (!released && latestRequestedVersion.get() == version) {
                        onResult(HighlightSnapshot(version, colors))
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            // The view was released while a new highlight request was being scheduled.
        }
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    private fun parseFullText(
        text: String,
        colors: IntArray,
        support: LanguageSupport?,
        syntax: CommentSyntax?
    ) {
        if (support == null) {
            simpleHighlight(text, colors, syntax)
            return
        }

        var index = 0
        while (index < text.length) {
            val current = text[index]

            var handled = false
            if (syntax != null) {
                val blockStart = syntax.blockStart
                val blockEnd = syntax.blockEnd
                if (
                    !blockStart.isNullOrEmpty() &&
                        !blockEnd.isNullOrEmpty() &&
                        text.startsWith(blockStart, index)
                ) {
                    val end = text.indexOf(blockEnd, index + blockStart.length)
                    val commentEnd = if (end == -1) text.length else end + blockEnd.length
                    fill(colors, index, commentEnd, LanguageSupport.COMMENT_COLOR)
                    index = commentEnd
                    handled = true
                }

                if (!handled) {
                    val linePrefix = syntax.linePrefixes.firstOrNull { prefix ->
                        text.startsWith(prefix, index)
                    }
                    if (linePrefix != null) {
                        val end = text.indexOf('\n', index)
                        val commentEnd = if (end == -1) text.length else end
                        fill(colors, index, commentEnd, LanguageSupport.COMMENT_COLOR)
                        index = commentEnd
                        handled = true
                    }
                }
            }
            if (handled) continue

            if (support.isStringDelimiter(current)) {
                val start = index
                index = consumeString(text, start, support.getStringEscapeChar())
                fill(colors, start, index, LanguageSupport.STRING_COLOR)
                continue
            }

            if (
                current.isDigit() ||
                (current == '0' && index + 1 < text.length && text[index + 1] in charArrayOf('x', 'X', 'b', 'B'))
            ) {
                val start = index
                if (current == '0' && index + 1 < text.length) {
                    when (text[index + 1]) {
                        'x', 'X' -> {
                            index += 2
                            while (index < text.length && (text[index].isDigit() || text[index] in 'a'..'f' || text[index] in 'A'..'F')) {
                                index++
                            }
                        }
                        'b', 'B' -> {
                            index += 2
                            while (index < text.length && text[index] in charArrayOf('0', '1')) {
                                index++
                            }
                        }
                        else -> {
                            index = consumeNumber(text, index)
                        }
                    }
                } else {
                    index = consumeNumber(text, index)
                }
                if (index < text.length && text[index] in charArrayOf('L', 'l', 'F', 'f', 'D', 'd')) {
                    index++
                }
                fill(colors, start, index, LanguageSupport.NUMBER_COLOR)
                continue
            }

            if (Character.isJavaIdentifierStart(current)) {
                val start = index
                while (index < text.length && Character.isJavaIdentifierPart(text[index])) {
                    index++
                }
                val word = text.substring(start, index)
                var nextCharIndex = index
                while (nextCharIndex < text.length && (text[nextCharIndex] == ' ' || text[nextCharIndex] == '\t')) {
                    nextCharIndex++
                }
                val nextChar = if (nextCharIndex < text.length) text[nextCharIndex] else ' '
                val isCapitalized = word.isNotEmpty() && word[0].isUpperCase()
                val color = when {
                    support.getKeywords().contains(word) -> LanguageSupport.KEYWORD_COLOR
                    support.getBuiltInTypes().contains(word) -> LanguageSupport.TYPE_COLOR
                    support.getBuiltInVariables().contains(word) -> LanguageSupport.VARIABLE_COLOR
                    support.getBuiltInFunctions().contains(word) -> LanguageSupport.FUNCTION_COLOR
                    nextChar == '(' -> LanguageSupport.FUNCTION_COLOR
                    isCapitalized -> LanguageSupport.TYPE_COLOR
                    else -> LanguageSupport.VARIABLE_COLOR
                }
                fill(colors, start, index, color)
                continue
            }

            if ("+-*/%=&|<>!~^?:;,(){}[].".contains(current)) {
                if (index + 1 < text.length) {
                    val twoChars = text.substring(index, index + 2)
                    if (twoChars in listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "=>", "->", "::")) {
                        fill(colors, index, index + 2, LanguageSupport.OPERATOR_COLOR)
                        index += 2
                        continue
                    }
                }
                colors[index] = LanguageSupport.OPERATOR_COLOR
            } else {
                colors[index] = LanguageSupport.DEFAULT_COLOR
            }
            index++
        }
    }

    private fun simpleHighlight(text: String, colors: IntArray, syntax: CommentSyntax?) {
        var index = 0
        while (index < text.length) {
            val blockStart = syntax?.blockStart
            val blockEnd = syntax?.blockEnd
            when {
                !blockStart.isNullOrEmpty() &&
                    !blockEnd.isNullOrEmpty() &&
                    text.startsWith(blockStart, index) -> {
                    val end = text.indexOf(blockEnd, index + blockStart.length)
                    val commentEnd = if (end == -1) text.length else end + blockEnd.length
                    fill(colors, index, commentEnd, LanguageSupport.COMMENT_COLOR)
                    index = commentEnd
                }
                syntax?.linePrefixes?.any { prefix -> text.startsWith(prefix, index) } == true -> {
                    val end = text.indexOf('\n', index)
                    val commentEnd = if (end == -1) text.length else end
                    fill(colors, index, commentEnd, LanguageSupport.COMMENT_COLOR)
                    index = commentEnd
                }
                text[index] == '"' || text[index] == '\'' || text[index] == '`' -> {
                    val start = index
                    index = consumeString(text, start, '\\')
                    fill(colors, start, index, LanguageSupport.STRING_COLOR)
                }
                text[index].isDigit() -> {
                    val start = index
                    index = consumeNumber(text, index)
                    fill(colors, start, index, LanguageSupport.NUMBER_COLOR)
                }
                else -> {
                    colors[index] = LanguageSupport.DEFAULT_COLOR
                    index++
                }
            }
        }
    }

    private fun consumeString(text: String, startIndex: Int, escape: Char): Int {
        val quote = text[startIndex]
        val isTripleQuoted =
            startIndex + 2 < text.length &&
                text[startIndex + 1] == quote &&
                text[startIndex + 2] == quote
        val delimiter = quote.toString().repeat(if (isTripleQuoted) 3 else 1)
        var index = startIndex + delimiter.length

        while (index < text.length) {
            if (text.startsWith(delimiter, index)) {
                return index + delimiter.length
            }
            if (!isTripleQuoted && text[index] == escape && index + 1 < text.length) {
                index += 2
            } else {
                index++
            }
        }
        return text.length
    }

    private fun consumeNumber(text: String, startIndex: Int): Int {
        var index = startIndex
        while (
            index < text.length &&
            (text[index].isDigit() || text[index] == '.' || text[index] == 'e' || text[index] == 'E' || text[index] == '-' || text[index] == '+')
        ) {
            index++
        }
        return index
    }

    private fun fill(colors: IntArray, start: Int, end: Int, color: Int) {
        val safeEnd = end.coerceAtMost(colors.size)
        for (index in start until safeEnd) {
            colors[index] = color
        }
    }
}
