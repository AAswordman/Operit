package com.ai.assistance.operit.util.toolmarkup

import com.ai.assistance.operit.util.ChatMarkupRegex

/** 工具结果的外层边界与正文提取；正文中的 XML 和代码保持原样。 */
object ToolResultMarkup {
    private val contentWrapper = Regex(
        """\A\s*<content>([\s\S]*)</content>\s*\z""",
        RegexOption.IGNORE_CASE,
    )

    /** 输入是单个工具结果的 body，兼容历史记录中没有 content 包裹的正文。 */
    fun contentFromBody(body: String): String {
        val wrapper = contentWrapper.matchEntire(body)
        return if (wrapper != null) wrapper.groupValues[1] else body
    }

    /** 内部自闭合标签不代表工具结果闭合，多个结果也不能作为单个块解析。 */
    fun isCompleteBlock(block: String): Boolean {
        val xml = block.trim()
        if (ChatMarkupRegex.toolResultSelfClosingTag.matches(xml)) return true
        val result = ChatMarkupRegex.toolResultAnyPattern.find(xml)
        return result != null && result.range.first == 0 && result.range.last == xml.lastIndex
    }

    /** 输入是已经分离的完整工具结果块，禁止从混合消息跨块提取正文。 */
    fun contentFromBlock(block: String): String {
        val xml = block.trim()
        if (ChatMarkupRegex.toolResultSelfClosingTag.matches(xml)) {
            return ""
        }
        val result = requireNotNull(ChatMarkupRegex.toolResultAnyPattern.find(xml)) {
            "Expected one complete tool result block"
        }
        require(result.range.first == 0 && result.range.last == xml.lastIndex) {
            "Expected one complete tool result block"
        }
        return contentFromBody(result.groupValues[2])
    }
}
