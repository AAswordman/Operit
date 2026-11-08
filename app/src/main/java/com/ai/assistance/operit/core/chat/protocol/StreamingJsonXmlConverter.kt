package com.ai.assistance.operit.core.chat.protocol

/**
 * 流式 JSON 到 XML 的转换器
 * 专门用于将 Tool Call 的 arguments JSON 流增量转换为 XML 格式
 * 例如: {"arg1": "val"} -> <param name="arg1">val</param>
 */
class StreamingJsonXmlConverter {

    /**
     * XML 流事件
     */
    sealed class Event {
        data class Tag(val text: String) : Event()
        data class Content(val text: String) : Event()
    }

    private enum class State {
        WAIT_BRACE,      // 等待起始 {
        WAIT_KEY_QUOTE,  // 等待 Key 的起始 "
        READ_KEY,        // 读取 Key 内容
        WAIT_COLON,      // 等待 :
        WAIT_VALUE,      // 等待 Value
        READ_STRING,     // 读取 String Value
        READ_PRIMITIVE,  // 读取 Primitive Value
        ESCAPE,          // 转义字符处理
        UNICODE_ESCAPE,  // Unicode 转义
        WAIT_COMMA       // 等待 , 或 }
    }

    private var state = State.WAIT_BRACE
    private val buffer = StringBuilder()
    private var unicodeCount = 0
    private var primitiveNestingDepth = 0
    private var primitiveInString = false
    private var primitiveEscape = false
    private var keyEscape = false
    private var readingComplexValue = false
    private var hasOpenParam = false

    private fun resetPrimitiveTracking() {
        primitiveNestingDepth = 0
        primitiveInString = false
        primitiveEscape = false
        readingComplexValue = false
    }

    private fun emitPrimitiveParam(events: MutableList<Event>) {
        events.add(Event.Content(escapeXml(buffer.toString())))
        events.add(Event.Tag("</param>"))
        hasOpenParam = false
        buffer.setLength(0)
        resetPrimitiveTracking()
    }

    private fun canFinalizePrimitiveOnFlush(): Boolean {
        if (state != State.READ_PRIMITIVE || buffer.isEmpty()) return false
        if (!readingComplexValue) return true
        return primitiveNestingDepth == 0 && !primitiveInString && !primitiveEscape
    }

    /**
     * 当前是否存在未闭合的 <param>。
     * 若为 true，说明参数内容仍可能是半截，调用方不应补 </tool>。
     */
    fun hasUnfinishedParam(): Boolean = hasOpenParam

    /**
     * 处理 JSON 块并返回 XML 事件列表
     */
    fun feed(chunk: String): List<Event> {
        val events = mutableListOf<Event>()

        for (c in chunk) {
            when (state) {
                State.WAIT_BRACE -> if (c == '{') state = State.WAIT_KEY_QUOTE
                State.WAIT_KEY_QUOTE -> {
                    if (c == '"') {
                        state = State.READ_KEY
                        keyEscape = false
                        buffer.setLength(0)
                    } else if (c == '}') {
                        // 对象结束
                        state = State.WAIT_BRACE
                    }
                }
                State.READ_KEY -> {
                    if (keyEscape) {
                        buffer.append(c)
                        keyEscape = false
                    } else {
                        when (c) {
                            '\\' -> keyEscape = true
                            '"' -> {
                                events.add(Event.Tag("\n  <param name=\"${buffer}\">"))
                                hasOpenParam = true
                                state = State.WAIT_COLON
                            }
                            else -> buffer.append(c)
                        }
                    }
                }
                State.WAIT_COLON -> if (c == ':') state = State.WAIT_VALUE
                State.WAIT_VALUE -> {
                    if (!c.isWhitespace()) {
                        if (c == '"') {
                            state = State.READ_STRING
                        } else {
                            state = State.READ_PRIMITIVE
                            buffer.setLength(0)
                            buffer.append(c)
                            readingComplexValue = c == '[' || c == '{'
                            primitiveNestingDepth = if (readingComplexValue) 1 else 0
                            primitiveInString = false
                            primitiveEscape = false
                        }
                    }
                }
                State.READ_STRING -> {
                    if (c == '"') {
                        state = State.WAIT_COMMA
                        events.add(Event.Tag("</param>"))
                        hasOpenParam = false
                    } else if (c == '\\') {
                        state = State.ESCAPE
                    } else {
                        events.add(Event.Content(escapeXml(c.toString())))
                    }
                }
                State.ESCAPE -> {
                    if (c == 'u') {
                        state = State.UNICODE_ESCAPE
                        unicodeCount = 0
                        buffer.setLength(0)
                    } else {
                        val unescaped = when (c) {
                            'n' -> "\n"
                            'r' -> "\r"
                            't' -> "\t"
                            'b' -> "\b"
                            'f' -> "\u000c"
                            '\"' -> "\""
                            '\\' -> "\\"
                            '/' -> "/"
                            else -> c.toString()
                        }
                        events.add(Event.Content(escapeXml(unescaped)))
                        state = State.READ_STRING
                    }
                }
                State.UNICODE_ESCAPE -> {
                    buffer.append(c)
                    unicodeCount++
                    if (unicodeCount == 4) {
                        try {
                            val code = buffer.toString().toInt(16)
                            events.add(Event.Content(escapeXml(code.toChar().toString())))
                        } catch (_: Exception) { }
                        state = State.READ_STRING
                    }
                }
                State.READ_PRIMITIVE -> {
                    if (readingComplexValue) {
                        if (primitiveInString) {
                            buffer.append(c)
                            if (primitiveEscape) {
                                primitiveEscape = false
                            } else if (c == '\\') {
                                primitiveEscape = true
                            } else if (c == '"') {
                                primitiveInString = false
                            }
                        } else {
                            when (c) {
                                '"' -> {
                                    primitiveInString = true
                                    buffer.append(c)
                                }

                                '[', '{' -> {
                                    primitiveNestingDepth++
                                    buffer.append(c)
                                }

                                ']', '}' -> {
                                    primitiveNestingDepth--
                                    buffer.append(c)

                                    if (primitiveNestingDepth == 0) {
                                        emitPrimitiveParam(events)
                                        state = State.WAIT_COMMA
                                    }
                                }

                                else -> buffer.append(c)
                            }
                        }
                    } else {
                        if (c == ',' || c == '}' || c.isWhitespace()) {
                            emitPrimitiveParam(events)

                            if (c == ',') state = State.WAIT_KEY_QUOTE
                            else if (c == '}') state = State.WAIT_BRACE
                            else state = State.WAIT_COMMA
                        } else {
                            buffer.append(c)
                        }
                    }
                }
                State.WAIT_COMMA -> {
                    if (c == ',') state = State.WAIT_KEY_QUOTE
                    else if (c == '}') state = State.WAIT_BRACE
                }
            }
        }
        return events
    }

    /**
     * 刷新缓冲区，处理剩余的原始值
     */
    fun flush(): List<Event> {
        val events = mutableListOf<Event>()
        if (canFinalizePrimitiveOnFlush()) {
            emitPrimitiveParam(events)
        }
        return events
    }

    /**
     * XML 转义辅助函数
     */
    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
    }
}
