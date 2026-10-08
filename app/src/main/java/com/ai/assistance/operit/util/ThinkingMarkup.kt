package com.ai.assistance.operit.util

import java.security.SecureRandom

/** 思考边界由适配层生成，模型输出的普通结束标签只作为思考正文。 */
object ThinkingMarkup {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val random = SecureRandom()
    private val openingTag = Regex(
        """<(think(?:ing)?)(?=\s|/?>)[^<>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val closingTag = Regex(
        """</(think(?:ing)?)(?=\s|>)[^<>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val attribute = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(['"])([\s\S]*?)\2""")

    data class Block(
        val start: Int,
        val bodyStart: Int,
        val bodyEnd: Int,
        val end: Int,
        val closed: Boolean,
    )

    fun newToken(): String = buildString {
        repeat(4 + random.nextInt(2)) {
            append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
    }

    fun openTag(token: String): String = "<think token=\"$token\">"

    fun closeTag(token: String): String = "</think token=\"$token\">"

    fun wrap(content: String): String {
        val token = newToken()
        return openTag(token) + content + closeTag(token)
    }

    fun isThinkingTag(name: String): Boolean =
        name.equals("think", ignoreCase = true) ||
            name.equals("thinking", ignoreCase = true)

    /** 完整解析属性，避免把 data-token 或其他属性值里的 token 当成边界。 */
    fun tokenOf(tag: String): String? = attribute.findAll(tag)
        .firstOrNull { it.groupValues[1].equals("token", ignoreCase = true) }
        ?.groupValues?.get(3)

    /** 只接受同名且 token 完全相同的闭合标签，token 区分大小写。 */
    fun blockAt(content: String, start: Int = 0): Block? {
        val opening = openingTag.find(content, start)?.takeIf { it.range.first == start } ?: return null
        val bodyStart = opening.range.last + 1
        if (opening.value.endsWith("/>")) {
            return Block(start, bodyStart, bodyStart, bodyStart, true)
        }
        val name = opening.groupValues[1]
        val token = tokenOf(opening.value)
        val close = closingTag.findAll(content, bodyStart).firstOrNull { candidate ->
            candidate.groupValues[1].equals(name, ignoreCase = true) &&
                if (token == null) {
                    candidate.value.equals("</$name>", ignoreCase = true)
                } else {
                    tokenOf(candidate.value) == token
                }
        }
        // 流式未闭合时，剩余内容仍属于思考，避免内部伪结束标签后的文本漏到正文。
        return Block(
            start = start,
            bodyStart = bodyStart,
            bodyEnd = close?.range?.first ?: content.length,
            end = close?.range?.last?.plus(1) ?: content.length,
            closed = close != null,
        )
    }

    fun body(content: String): String {
        val block = blockAt(content) ?: return content
        return content.substring(block.bodyStart, block.bodyEnd)
    }

    fun isClosed(content: String): Boolean = blockAt(content)?.closed == true

    fun remove(content: String, replacement: String = ""): String = separate(content, replacement).first

    /** 未闭合的思考也只进入思考字段，历史回传不能把它重新当作正文。 */
    fun extract(content: String): Pair<String, List<String>> = separate(content, "")

    private fun separate(content: String, replacement: String): Pair<String, List<String>> {
        if (!content.contains("<think", ignoreCase = true)) {
            return content to emptyList()
        }
        val visible = StringBuilder(content.length)
        val thoughts = mutableListOf<String>()
        var cursor = 0
        var searchFrom = 0
        while (searchFrom < content.length) {
            val opening = openingTag.find(content, searchFrom) ?: break
            searchFrom = opening.range.last + 1
            if (opening.value.endsWith("/>")) continue
            val block = requireNotNull(blockAt(content, opening.range.first))
            visible.append(content, cursor, block.start)
            visible.append(replacement)
            thoughts.add(content.substring(block.bodyStart, block.bodyEnd))
            cursor = block.end
            searchFrom = cursor
        }
        visible.append(content, cursor, content.length)
        return visible.toString() to thoughts
    }
}
