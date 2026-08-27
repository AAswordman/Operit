package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.withContent

/** Utility functions for chat message handling */
object ChatUtils {
    fun stripGeminiThoughtSignatureMeta(content: String): String {
        return ChatMarkupRegex.removeGeminiThoughtSignatureMeta(content)
    }

    fun stripGeminiThoughtSignatureMeta(messages: List<Pair<String, String>>): List<Pair<String, String>> {
        return messages.map { (role, content) ->
            role to stripGeminiThoughtSignatureMeta(content)
        }
    }

    fun stripGeminiThoughtSignatureMetaTurns(messages: List<PromptTurn>): List<PromptTurn> {
        return messages.map { turn ->
            turn.withContent(stripGeminiThoughtSignatureMeta(turn.content))
        }
    }

    fun stripOpenAiResponsesReasoningMeta(content: String): String {
        return ChatMarkupRegex.removeOpenAiResponsesReasoningMeta(content)
    }

    fun stripOpenAiResponsesReasoningMetaTurns(messages: List<PromptTurn>): List<PromptTurn> {
        return messages.map { turn ->
            turn.withContent(stripOpenAiResponsesReasoningMeta(turn.content))
        }
    }

    fun isGeminiProviderModel(providerModel: String): Boolean {
        return when (providerModel.substringBefore(":").uppercase()) {
            "GOOGLE", "GEMINI_GENERIC" -> true
            else -> false
        }
    }

    fun isOpenAIResponsesProviderModel(providerModel: String): Boolean {
        return providerModel.substringBefore(":").uppercase() == "OPENAI_RESPONSES"
    }

    /** 过滤掉内容中的思考部分和搜索来源 移除<think></think>、<thinking></thinking>和<search></search>标签及其中的内容，并处理未闭合的情况 */
    fun removeThinkingContent(content: String): String {
        // 使用正则表达式匹配<think>、<thinking>和<search>标签及其内容
        // 这个正则表达式会匹配以下情况：
        // 1. <think>...</think> (正常闭合的标签)
        // 2. <think>... (未闭合，直到字符串末尾)
        // 3. <thinking>...</thinking> (正常闭合的标签)
        // 4. <thinking>... (未闭合，直到字符串末尾)
        // 5. <search>...</search> (正常闭合的标签)
        // 6. <search>... (未闭合，直到字符串末尾)
        // \\z 匹配字符串的绝对末尾
        val thinkPattern = "<think(?:ing)?>.*?(</think(?:ing)?>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
        val searchPattern = "<search>.*?(</search>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
        return content.replace(thinkPattern, "").replace(searchPattern, "").trim()
    }

    /**
     * 提取think标签内的内容（用于DeepSeek的reasoning_content）
     * @param content 包含think标签的内容
     * @return Pair(移除think标签后的内容, think标签内的内容)
     */
    fun extractThinkingContent(content: String): Pair<String, String> {
        val thinkPattern = "<think(?:ing)?>([\\s\\S]*?)</think(?:ing)?>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val thinkMatches = thinkPattern.findAll(content)
        
        // 收集所有think标签内的内容
        val thinkingContent = thinkMatches.joinToString("\n") { it.groupValues[1].trim() }
        
        // 移除think标签和search标签
        val contentWithoutThink = content
            .replace(thinkPattern, "")
            .replace("<search>.*?(</search>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL), "")
            .trim()
        
        return Pair(contentWithoutThink, thinkingContent)
    }

    /**
     * 估算给定文本的token数量
     * @param text 要估算token的文本
     * @return 估算的token数量
     */
    fun estimateTokenCount(text: String): Long {
        // 简单估算：中文每个字约1.5个token，英文每4个字符约1个token
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        val otherCharCount = text.length - chineseCharCount
        return (chineseCharCount * 1.5 + otherCharCount * 0.25).toLong()
    }

    /**
     * 从 AI 响应中提取 JSON 对象部分
     * AI 可能会在 JSON 前后添加说明文字或使用 ```json 代码块，需要提取出纯净的 JSON
     */
    fun extractJson(response: String): String {
        var text = response.trim()
        
        // 处理 markdown 代码块格式 ```json ... ```
        if (text.startsWith("```")) {
            val lines = text.lines()
            text = lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        
        // 寻找第一个 { 和最后一个 }
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        
        return if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            text.substring(firstBrace, lastBrace + 1)
        } else {
            // 如果没找到完整的 JSON 结构，返回原始字符串
            text
        }
    }

    /**
     * 从 AI 响应中提取 JSON 数组部分
     * AI 可能会在 JSON 前后添加说明文字或使用 ```json 代码块，需要提取出纯净的 JSON
     */
    fun extractJsonArray(response: String): String {
        var text = response.trim()
        
        // 处理 markdown 代码块格式 ```json ... ```
        if (text.startsWith("```")) {
            val lines = text.lines()
            text = lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        
        // 寻找第一个 [ 和最后一个 ]
        val firstBracket = text.indexOf('[')
        val lastBracket = text.lastIndexOf(']')
        
        return if (firstBracket != -1 && lastBracket != -1 && firstBracket < lastBracket) {
            text.substring(firstBracket, lastBracket + 1)
        } else {
            // 如果没找到完整的 JSON 结构，返回原始字符串
            text
        }
    }
}
