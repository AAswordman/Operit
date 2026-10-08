package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.util.ThinkingMarkup
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream

/** 展开的思考正文与外层片段共用配对规则，避免标签示例截断流式显示。 */
internal fun createThinkMarkdownCharStream(
    xmlStream: Stream<String>,
    tagName: String,
): Stream<Char> = stream {
    val openingTag = StringBuilder()
    val closingPrefix = "</$tagName"
    val pendingClosingTag = StringBuilder()
    var startTagClosed = false
    var reachedEndTag = false

    suspend fun flushPendingClosingTag() {
        pendingClosingTag.toString().forEach { emit(it) }
        pendingClosingTag.setLength(0)
    }

    xmlStream.collect { chunk ->
        chunk.forEach { ch ->
            if (reachedEndTag) return@forEach

            if (!startTagClosed) {
                openingTag.append(ch)
                if (ch == '>') {
                    startTagClosed = true
                }
                return@forEach
            }

            // 新的尖括号结束前一个不完整候选，真正的结束标记仍可独立配对。
            if (ch == '<' && pendingClosingTag.isNotEmpty()) {
                flushPendingClosingTag()
            }
            if (ch != '<' && pendingClosingTag.isEmpty()) {
                emit(ch)
                return@forEach
            }

            pendingClosingTag.append(ch)
            val index = pendingClosingTag.lastIndex
            val isClosingCandidate = when {
                index < closingPrefix.length -> ch.equals(closingPrefix[index], ignoreCase = true)
                index == closingPrefix.length -> ch == '>' || ch.isWhitespace()
                else -> true
            }
            if (!isClosingCandidate) {
                flushPendingClosingTag()
            } else if (ch == '>') {
                // 复用静态思考解析，普通结束标签、错误 token 和其他属性都不能越过边界。
                if (ThinkingMarkup.isClosed(openingTag.toString().trimStart() + pendingClosingTag)) {
                    pendingClosingTag.setLength(0)
                    reachedEndTag = true
                } else {
                    flushPendingClosingTag()
                }
            }
        }
    }

    // 上游正常结束但思考尚未闭合时，不完整的结束标签仍是正文的一部分。
    if (!reachedEndTag) {
        flushPendingClosingTag()
    }
}
