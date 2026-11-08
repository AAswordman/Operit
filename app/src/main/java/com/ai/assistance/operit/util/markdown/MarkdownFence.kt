package com.ai.assistance.operit.util.markdown

/** 引用展开、代码渲染和历史摘要共用同一套围栏边界。 */
internal data class MarkdownFence(val delimiter: String, val language: String) {
    val closing = Regex("^[ \\t]{0,3}" + Regex.escape(delimiter.first().toString()) + "{" + delimiter.length + ",}[ \\t]*\\r?$", RegexOption.MULTILINE)

    data class Block(val fence: MarkdownFence, val start: Int, val end: Int, val bodyStart: Int, val bodyEnd: Int, val closed: Boolean) {
        fun body(source: String): String = source.substring(bodyStart, bodyEnd)
            .let { if (closed) it.removeSuffix("\n").removeSuffix("\r") else it }
    }

    companion object {
        private val opening = Regex("^[ \\t]{0,3}(`{3,}|~{3,})([^`~]*)\\r?$")
        fun parse(line: String): MarkdownFence? = opening.matchEntire(line)?.let {
            MarkdownFence(it.groupValues[1], it.groupValues[2].trim())
        }

        fun blocks(source: String): List<Block> {
            val lines = source.split('\n')
            val offsets = IntArray(lines.size + 1)
            lines.forEachIndexed { index, line -> offsets[index + 1] = offsets[index] + line.length + 1 }
            val blocks = mutableListOf<Block>()
            var index = 0
            while (index < lines.size) {
                val fence = parse(lines[index])
                if (fence == null) { index++; continue }
                val closing = (index + 1 until lines.size).firstOrNull { fence.closing.matches(lines[it]) }
                val next = closing?.plus(1) ?: lines.size
                blocks += Block(fence, offsets[index], offsets[next].coerceAtMost(source.length),
                    offsets[index + 1].coerceAtMost(source.length), closing?.let { offsets[it] } ?: source.length, closing != null)
                index = next
            }
            return blocks
        }
    }
}
