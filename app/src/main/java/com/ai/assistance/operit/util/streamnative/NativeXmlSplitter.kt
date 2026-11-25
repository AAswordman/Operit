package com.ai.assistance.operit.util.streamnative

object NativeXmlSplitter {

    init {
        System.loadLibrary("streamnative")
    }

    private external fun nativeSplitXmlSegments(content: String): IntArray

    fun splitXmlTag(content: String): List<List<String>> {
        val results = mutableListOf<List<String>>()

        val segments = nativeSplitXmlSegments(content)
        if (segments.isEmpty()) return results

        var i = 0
        while (i + 2 < segments.size) {
            val type = segments[i]
            val start = segments[i + 1]
            val end = segments[i + 2]
            i += 3

            if (start < 0 || end < 0 || start > end || end > content.length) continue

            val chunk = content.substring(start, end)
            if (type == 1) {
                // 子代理工具名采用 `subagent:subagent_run` 的保留形式（包:函数），
                // 标签名必须允许冒号，否则该标签在历史解析里会退化成 unknown/text。
                // 与 C 层 StreamXmlPlugin.isTagNameContinuationChar、Kotlin 流式
                // StreamXmlPlugin 的标签名延续字符保持同一口径。
                val tagNameMatch = Regex("<([A-Za-z][A-Za-z0-9_:]*)[\\s>]").find(chunk)
                val tagName = tagNameMatch?.groupValues?.getOrNull(1) ?: "unknown"
                results.add(listOf(tagName, chunk))
            } else {
                if (chunk.isNotBlank()) {
                    results.add(listOf("text", chunk))
                }
            }
        }

        return results
    }
}
