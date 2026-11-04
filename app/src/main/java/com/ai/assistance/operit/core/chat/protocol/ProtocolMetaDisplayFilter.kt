package com.ai.assistance.operit.core.chat.protocol

/** 只投影聊天显示内容，原始协议负载仍由消息保存与后续请求链路持有。 */
internal class ProtocolMetaDisplayFilter(private val preserveCodeExamples: Boolean = true) {
    private val visibleHistory = StringBuilder()
    private val opening = StringBuilder()
    private var quote: Char? = null
    private var hiding = false
    private var closingIndex = 0
    private var literal: PendingLiteral? = null

    fun append(chunk: String): String {
        if (!hiding && literal == null && opening.isEmpty() && '<' !in chunk) {
            visibleHistory.append(chunk)
            return chunk
        }
        return buildString {
            for (char in chunk) {
                val pendingLiteral = literal
                if (pendingLiteral != null && pendingLiteral.closesBefore(char)) {
                    emitVisible(pendingLiteral.content.toString(), this)
                    literal = null
                } else if (pendingLiteral != null) {
                    pendingLiteral.append(char)
                    if (pendingLiteral.closed) {
                        emitVisible(pendingLiteral.content.toString(), this)
                        literal = null
                    }
                    continue
                }
                if (hiding) {
                    closingIndex = when {
                        char.equals(CLOSE[closingIndex], ignoreCase = true) -> closingIndex + 1
                        char == '<' -> 1
                        else -> 0
                    }
                    if (closingIndex == CLOSE.length) {
                        hiding = false
                        closingIndex = 0
                    }
                    continue
                }
                if (opening.isEmpty()) {
                    if (char == '<') opening.append(char) else emitVisible(char, this)
                    continue
                }
                opening.append(char)
                if (opening.length <= OPEN.length) {
                    if (!OPEN.startsWith(opening.toString(), ignoreCase = true)) rejectOpening(this)
                    continue
                }
                if (opening.length == OPEN.length + 1 && !char.isWhitespace() && char != '>') {
                    rejectOpening(this)
                    continue
                }
                if (quote != null) {
                    if (char == quote) quote = null
                    continue
                }
                if (char.code == 39 || char.code == 34) {
                    quote = char
                    continue
                }
                if (char != '>') continue

                val tag = opening.toString()
                opening.clear()
                if (!isProtocolOpening(tag)) {
                    emitVisible(tag, this)
                    continue
                }
                val context = if (preserveCodeExamples) literalContext(visibleHistory) else null
                if (context == null) {
                    hiding = !tag.endsWith("/>")
                } else {
                    // 已闭合代码示例保留原文；候选协议先暂存，避免未闭合代码吞入真实负载。
                    literal = PendingLiteral(context, tag)
                }
            }
        }
    }

    fun finish(): String = buildString {
        literal?.let { pending ->
            val text = pending.content.toString()
            if (pending.closedAtEnd()) {
                emitVisible(text, this)
            } else {
                val projection = ProtocolMetaDisplayFilter(preserveCodeExamples = false)
                emitVisible(projection.append(text) + projection.finish(), this)
            }
        }
        literal = null
        // 已识别提供方的不完整开始标签继续隐藏，普通未完成文本照常保留。
        if (opening.isNotEmpty() && !isProtocolOpening(opening)) flushOpening(this)
        opening.clear()
        quote = null
    }

    /** 保存点仍使用原文长度，显示前缀重建后继续沿用尚未结束的隐藏状态。 */
    fun reset(rawPrefix: String): String {
        visibleHistory.clear()
        opening.clear()
        quote = null
        hiding = false
        closingIndex = 0
        literal = null
        return append(rawPrefix)
    }

    private fun rejectOpening(output: StringBuilder) {
        val nextOpening = opening.last() == '<'
        if (nextOpening) opening.setLength(opening.length - 1)
        flushOpening(output)
        if (nextOpening) opening.append('<')
    }

    private fun flushOpening(output: StringBuilder) {
        emitVisible(opening.toString(), output)
        opening.clear()
        quote = null
    }

    private fun emitVisible(char: Char, output: StringBuilder) {
        visibleHistory.append(char)
        output.append(char)
    }

    private fun emitVisible(text: String, output: StringBuilder) {
        visibleHistory.append(text)
        output.append(text)
    }

    private data class LiteralContext(val marker: String, val fenced: Boolean)

    private class PendingLiteral(val context: LiteralContext, opening: String) {
        val content = StringBuilder(opening)
        private var ticks = 0
        private val closingLine = StringBuilder()
        private var lineCandidate = false
        private val closingFence = if (context.fenced) fenceClosing(context.marker) else null
        var closed = false
            private set

        fun append(char: Char) {
            content.append(char)
            if (!context.fenced) {
                if (char == '`') {
                    ticks++
                } else {
                    ticks = 0
                }
                return
            }
            if (char == '\n') {
                closed = lineCandidate && closingFence!!.matches(closingLine)
                lineCandidate = true
                closingLine.clear()
            } else if (lineCandidate) {
                if (char == context.marker[0] || char == ' ' || char == '\t' || char == '\r') {
                    closingLine.append(char)
                } else {
                    lineCandidate = false
                    closingLine.clear()
                }
            }
        }

        fun closesBefore(char: Char): Boolean =
            !context.fenced && char != '`' && ticks == context.marker.length

        fun closedAtEnd(): Boolean = closed || if (context.fenced) {
            lineCandidate && closingFence!!.matches(closingLine)
        } else {
            ticks == context.marker.length
        }
    }

    companion object {
        private const val OPEN = "<meta"
        private const val CLOSE = "</meta>"
        private val PROVIDERS = setOf(
            "gemini:thought_signature", "openai:responses_reasoning", "openai:responses_output_item",
        )
        private val ATTRIBUTE = Regex(
            """([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(["'])([\s\S]*?)\2""",
        )

        private fun isProtocolOpening(tag: CharSequence): Boolean = ATTRIBUTE.findAll(tag).any {
            it.groupValues[1].equals("provider", ignoreCase = true) &&
                it.groupValues[3].lowercase() in PROVIDERS
        }

        private val CODE_BOUNDARY = Regex(
            """(`+|(?m)^[ \t]{0,3}~{3,})|<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?]]>|<([A-Za-z][A-Za-z0-9_.:-]*)(?=\s|/?>)""",
        )
        private val XML_TOKEN = Regex(
            """<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?]]>|</?([A-Za-z][A-Za-z0-9_.:-]*)(?=\s|/?>)(?:[^<>"']|"[^"]*"|'[^']*')*>""",
        )

        /** 独立 XML 的内容由其渲染器处理，其中的代码字符不能延续外部 Markdown 范围。 */
        private fun xmlBlockEnd(content: CharSequence, start: Int, tagName: String): Int? {
            // 复用匹配器，避免 Android 为每个 XML token 重新分配原生 Matcher。
            val tokens = XML_TOKEN.toPattern().matcher(content)
            if (!tokens.find(start) || tokens.start() != start) return null
            if (content[tokens.end() - 2] == '/') return tokens.end()
            var depth = 1
            while (tokens.find()) {
                if (!tagName.equals(tokens.group(1), ignoreCase = true)) continue
                if (content[tokens.start() + 1] == '/') {
                    depth--
                    if (depth == 0) return tokens.end()
                } else if (content[tokens.end() - 2] != '/') {
                    depth++
                }
            }
            // 前缀仍位于该 XML 内部，此时也没有可接续的外部 Markdown 代码范围。
            return null
        }

        private fun fenceClosing(marker: String) = Regex(
            "^[ \\t]{0,3}" + Regex.escape(marker.first().toString()) +
                "{" + marker.length + ",}[ \\t]*\\r?$",
            RegexOption.MULTILINE,
        )

        /** 仅在识别协议开始标签时扫描可见前缀，长协议正文按字符丢弃而不反复扫描。 */
        private fun literalContext(prefix: CharSequence): LiteralContext? {
            // 直接读取可见缓冲区，并在一次扫描中复用边界匹配器。
            val boundaries = CODE_BOUNDARY.toPattern().matcher(prefix)
            var scan = 0
            while (scan < prefix.length) {
                if (!boundaries.find(scan)) return null
                val markerStart = boundaries.start()
                val boundaryEnd = boundaries.end()
                var escapeStart = markerStart
                while (escapeStart > 0 && prefix[escapeStart - 1] == '\\') escapeStart--
                if ((markerStart - escapeStart) % 2 == 1) {
                    scan = boundaryEnd
                    continue
                }
                val tagName = boundaries.group(2)
                if (tagName != null) {
                    scan = xmlBlockEnd(prefix, markerStart, tagName) ?: return null
                    continue
                }
                val rawMarker = boundaries.group(1)
                if (rawMarker == null) {
                    // 注释和 CDATA 整段跳过，不把其中的反引号当作代码起点。
                    scan = boundaryEnd
                    continue
                }
                val marker = rawMarker.trimStart()
                val lineStart = prefix.lastIndexOf('\n', markerStart - 1) + 1
                val linePrefix = prefix.substring(lineStart, markerStart)
                val fenced = marker.length >= 3 && linePrefix.length <= 3 && linePrefix.all { it == ' ' }
                val bodyStart = if (fenced) prefix.indexOf('\n', boundaryEnd) else -1
                val closing = if (fenced && bodyStart >= 0) {
                    fenceClosing(marker).find(prefix, bodyStart + 1)
                } else if (!fenced) {
                    Regex("(?<!`)" + Regex.escape(marker) + "(?!`)").find(prefix, boundaryEnd)
                } else {
                    null
                }
                if (closing == null) return LiteralContext(marker, fenced)
                scan = closing.range.last + 1
            }
            return null
        }
    }
}

internal fun protocolMetaDisplayText(content: String): String {
    if (!content.contains("<meta", ignoreCase = true)) return content
    val filter = ProtocolMetaDisplayFilter()
    return filter.append(content) + filter.finish()
}
