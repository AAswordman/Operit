package com.ai.assistance.operit.util.text

/** 通用 XML 文本清理，保留聊天编辑与语音使用的既有清理行为。 */
object XmlTagCleaner {
    fun stripXmlLikeTags(text: String): String {
        var s = text
        // 匹配成对的标签 <tag>...</tag>
        val paired = Regex(
            pattern = "<([A-Za-z][A-Za-z0-9:_-]*)(\\s[^>]*)?>[\\s\\S]*?</\\1>",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        repeat(5) { _ ->
            val updated = s.replace(paired, "")
            if (updated == s) return@repeat
            s = updated
        }
        // 匹配自闭合标签 <tag />
        s = s.replace(
            Regex("<[A-Za-z][A-Za-z0-9:_-]*(\\s[^>]*)?/\\s*>", RegexOption.IGNORE_CASE),
            ""
        )
        // 匹配任何剩余的标签
        s = s.replace(
            Regex("</?[^>]+>", RegexOption.IGNORE_CASE),
            ""
        )
        return s.trim()
    }
}
