package com.ai.assistance.operit.core.tools.parameters

/** 只解释解析失败，不修改可能包含终端命令的原始参数。 */
internal object ProxyParamsDiagnostics {
    fun describe(source: String, parserMessage: String? = null): String {
        fun position(offset: Int): String {
            val end = offset.coerceIn(0, source.length)
            val line = source.take(end).count { it == '\n' } + 1
            val previousLine = if (end == 0) -1 else source.lastIndexOf('\n', end - 1)
            return "line $line, column ${end - previousLine} (offset $end)"
        }
        fun failure(reason: String, offset: Int) =
            "params must be a valid JSON object: $reason at ${position(offset)}. The target tool was not executed."

        val first = source.indexOfFirst { !it.isWhitespace() }
        if (first < 0) return failure("empty input; pass an object such as {}", 0)
        if (source[first] != '{') {
            val type = when (source[first]) {
                '[' -> "array"
                '"', '\'' -> "string"
                't', 'f' -> "boolean"
                'n' -> "null"
                '-', in '0'..'9' -> "number"
                else -> "non-object input"
            }
            return failure("expected an object beginning with '{', received $type", first)
        }

        var quote: Char? = null
        var quoteStart = -1
        var escaped = false
        val brackets = ArrayDeque<Pair<Char, Int>>()
        source.forEachIndexed { index, char ->
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == quote) quote = null
            } else when (char) {
                '"', '\'' -> { quote = char; quoteStart = index }
                '{', '[' -> brackets.addLast(char to index)
                '}', ']' -> {
                    val expected = when (brackets.lastOrNull()?.first) {
                        '{' -> '}'
                        '[' -> ']'
                        else -> null
                    }
                    if (char != expected) return failure(
                        if (expected == null) "unexpected closing '$char'"
                        else "unexpected closing '$char'; expected '$expected'", index,
                    )
                    brackets.removeLast()
                }
            }
        }
        if (quote != null) {
            val kind = if (quote == '"') "double" else "single"
            return failure(
                "unterminated $kind-quoted string opened at ${position(quoteStart)}; check a missing closing quote or escape embedded quotes with a backslash",
                source.length,
            )
        }
        brackets.lastOrNull()?.let { (opening, start) ->
            val closing = if (opening == '{') '}' else ']'
            return failure("missing closing '$closing' for '$opening' opened at ${position(start)}", source.length)
        }

        // Android 与 JVM 的 org.json 使用不同的位置格式，只提取坐标和已知原因，不回显参数正文。
        val offset = Regex("(?:at character |\\bat )(\\d+)")
            .find(parserMessage.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: source.length
        val reasons = listOf(
            "Expected ':'", "Expected a ':' after a key", "Expected a ',' or '}'",
            "Expected a ',' or ']'", "Expected a name", "Names must be strings",
            "Missing value", "Unterminated string", "Unterminated object", "Unterminated array",
        )
        val reason = reasons.firstOrNull { parserMessage.orEmpty().startsWith(it) }
            ?: "invalid JSON syntax; check commas, colons, quotes, and escaping"
        return failure(reason, offset)
    }
}
