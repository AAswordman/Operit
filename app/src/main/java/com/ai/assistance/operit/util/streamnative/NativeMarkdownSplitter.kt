package com.ai.assistance.operit.util.streamnative

import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

private fun Int.toMarkdownTypeOrNull(): MarkdownProcessorType? =
    MarkdownProcessorType.entries.getOrNull(this)

private fun IntArray.toInlineStableNodes(content: String): List<MarkdownNodeStable> {
    val nodes = ArrayList<MarkdownNodeStable>(size / 3)
    var index = 0

    while (index + 2 < size) {
        val typeOrdinal = this[index]
        val start = this[index + 1]
        val end = this[index + 2]
        index += 3

        if (typeOrdinal < 0 || start < 0 || end < start || end > content.length) {
            continue
        }

        val type = typeOrdinal.toMarkdownTypeOrNull() ?: MarkdownProcessorType.PLAIN_TEXT
        val nodeContent =
            if (type == MarkdownProcessorType.HTML_BREAK) {
                "\n"
            } else {
                content.substring(start, end)
            }
        nodes +=
            MarkdownNodeStable(
                type = type,
                content = nodeContent,
                children = emptyList()
            )
    }

    return nodes
}

object NativeMarkdownSplitter {

    init {
        System.loadLibrary("streamnative")
    }

    private external fun nativeCreateBlockSession(): Long
    private external fun nativeCreateInlineSession(): Long
    private external fun nativeDestroySession(handle: Long)
    private external fun nativePush(handle: Long, chunk: String): IntArray
    private external fun nativeFinish(handle: Long): IntArray

    class Session internal constructor(
        private val handle: Long,
    ) {
        fun push(chunk: String): IntArray = nativePush(handle, chunk)
        fun finish(): IntArray = nativeFinish(handle)
        fun destroy() = nativeDestroySession(handle)
    }

    fun createBlockSession(): Session = Session(nativeCreateBlockSession())
    fun createInlineSession(): Session = Session(nativeCreateInlineSession())

    fun parseInlineToStableNodes(content: String): List<MarkdownNodeStable> {
        if (content.isEmpty()) return emptyList()

        val session = createInlineSession()
        return try {
            val segments = session.push(content) + session.finish()
            segments.toInlineStableNodes(content)
        } finally {
            session.destroy()
        }
    }
}
