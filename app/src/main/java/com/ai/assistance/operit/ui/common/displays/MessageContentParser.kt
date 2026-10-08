package com.ai.assistance.operit.ui.common.displays

import com.ai.assistance.operit.util.ChatMarkupRegex

/**
 * A utility class to parse message content with XML markup.
 * Extracts tool requests, executions, and results from message content.
 */
class MessageContentParser {
    companion object {
        // XML markup patterns
        public val xmlStatusPattern = ChatMarkupRegex.xmlStatusPattern
        private val xmlToolRequestPattern = ChatMarkupRegex.xmlToolRequestPattern

        // 添加缺失的工具名称和参数解析模式
        public val namePattern = ChatMarkupRegex.namePattern
        public val toolParamPattern = ChatMarkupRegex.toolParamPattern
    }
}