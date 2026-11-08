package com.ai.assistance.operit.core.chat.protocol

/** 只投影聊天显示内容，原始协议负载仍由消息保存与后续请求链路持有。 */
internal class ProtocolMetaDisplayFilter(private val preserveCodeExamples: Boolean = true) {
    private var examples = CodeExampleContext()
    private var opening = StringBuilder()
    private var quote: Char? = null
    private var hiding = false
    private var closingIndex = 0
    private var literal: PendingLiteral? = null

    fun append(chunk: String): String {
        if (!hiding && literal == null && opening.isEmpty() && '<' !in chunk) {
            if (preserveCodeExamples) examples.append(chunk)
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
                opening = StringBuilder()
                if (!isProtocolOpening(tag)) {
                    emitVisible(tag, this)
                    continue
                }
                val context = if (preserveCodeExamples) examples.current() else null
                if (context == null) {
                    hiding = !tag.endsWith("/>")
                } else {
                    // 已闭合代码示例保留原文；候选协议先暂存，避免未闭合代码吞入真实负载。
                    literal = PendingLiteral(context, tag)
                }
            }
        }
    }

    /** Buffers are append-only; checkpoints retain their prefix without copying the message. */
    internal class Snapshot(
        internal val examples: CodeExampleContext,
        internal val opening: CharSequence,
        internal val openingLength: Int,
        internal val quote: Char?,
        internal val hiding: Boolean,
        internal val closingIndex: Int,
        internal val literal: LiteralSnapshot?,
    )

    internal class LiteralSnapshot(
        val context: CodeLiteralContext,
        val content: CharSequence,
        val contentLength: Int,
        val ticks: Int,
        val closingLine: String,
        val lineCandidate: Boolean,
        val closed: Boolean,
    )

    fun checkpoint(): Snapshot = Snapshot(
        examples.copy(), opening, opening.length, quote, hiding, closingIndex, literal?.checkpoint(),
    )

    fun restore(snapshot: Snapshot) {
        examples = snapshot.examples.copy()
        opening = StringBuilder().append(snapshot.opening, 0, snapshot.openingLength)
        quote = snapshot.quote
        hiding = snapshot.hiding
        closingIndex = snapshot.closingIndex
        literal = snapshot.literal?.let { PendingLiteral.restore(it) }
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
        opening = StringBuilder()
        quote = null
    }

    private fun rejectOpening(output: StringBuilder) {
        val nextOpening = opening.last() == '<'
        if (nextOpening) {
            emitVisible(opening.substring(0, opening.length - 1), output)
            opening = StringBuilder("<")
            quote = null
        } else flushOpening(output)
    }

    private fun flushOpening(output: StringBuilder) {
        emitVisible(opening.toString(), output)
        opening = StringBuilder()
        quote = null
    }

    private fun emitVisible(char: Char, output: StringBuilder) {
        if (preserveCodeExamples) examples.append(char)
        output.append(char)
    }

    private fun emitVisible(text: String, output: StringBuilder) {
        if (preserveCodeExamples) examples.append(text)
        output.append(text)
    }


    private class PendingLiteral(val context: CodeLiteralContext, opening: String) {
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

        fun checkpoint() = LiteralSnapshot(
            context, content, content.length, ticks, closingLine.toString(), lineCandidate, closed,
        )

        companion object {
            fun restore(snapshot: LiteralSnapshot): PendingLiteral = PendingLiteral(snapshot.context, "").also {
                it.content.append(snapshot.content, 0, snapshot.contentLength)
                it.ticks = snapshot.ticks
                it.closingLine.append(snapshot.closingLine)
                it.lineCandidate = snapshot.lineCandidate
                it.closed = snapshot.closed
            }
        }

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

        private fun fenceClosing(marker: String) = Regex(
            "^[ \\t]{0,3}" + Regex.escape(marker.first().toString()) +
                "{" + marker.length + ",}[ \\t]*\\r?$",
            RegexOption.MULTILINE,
        )

    }
}

internal fun protocolMetaDisplayText(content: String): String {
    if (!content.contains("<meta", ignoreCase = true)) return content
    val filter = ProtocolMetaDisplayFilter()
    return filter.append(content) + filter.finish()
}
