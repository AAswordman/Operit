package com.ai.assistance.operit.data.converter

import kotlinx.serialization.json.*

/**
 * 聊天记录格式检测器
 * 自动识别导入文件的格式
 */
object ChatFormatDetector {
    
    /**
     * 检测文件内容的格式
     * @param content 文件内容字符串
     * @return 检测到的格式
     */
    fun detectFormat(content: String): ChatFormat {
        if (content.isBlank()) {
            return ChatFormat.UNKNOWN
        }
        
        val trimmedContent = content.trim()
        
        // 检测 Markdown 格式
        if (isMarkdownFormat(trimmedContent)) {
            return ChatFormat.MARKDOWN
        }
        
        // 检测 CSV 格式
        if (isCsvFormat(trimmedContent)) {
            return ChatFormat.CSV
        }
        
        // 检测 JSON 格式
        if (trimmedContent.startsWith("{") || trimmedContent.startsWith("[")) {
            return detectJsonFormat(trimmedContent)
        }
        
        // 默认为纯文本
        return ChatFormat.PLAIN_TEXT
    }
    
    /**
     * 检测 JSON 的具体格式
     */
    private fun detectJsonFormat(content: String): ChatFormat {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val element = json.parseToJsonElement(content)
            
            when {
                // 检测是否为数组
                element is JsonArray -> {
                    if (element.isEmpty()) {
                        return ChatFormat.GENERIC_JSON
                    }
                    
                    val firstItem = element.first()
                    if (firstItem is JsonObject) {
                        detectJsonObjectFormat(firstItem)
                    } else {
                        ChatFormat.GENERIC_JSON
                    }
                }
                // 单个对象
                element is JsonObject -> {
                    detectJsonObjectFormat(element)
                }
                else -> ChatFormat.UNKNOWN
            }
        } catch (e: Exception) {
            ChatFormat.UNKNOWN
        }
    }
    
    /**
     * 检测 JSON 对象的具体格式
     */
    private fun detectJsonObjectFormat(obj: JsonObject): ChatFormat {
        val keys = obj.keys
        
        // Operit 格式特征: id, title, messages, createdAt
        if (keys.contains("id") && keys.contains("title") && 
            keys.contains("messages") && keys.contains("createdAt")) {
            return ChatFormat.OPERIT
        }
        
        // Claude 格式特征: uuid, chat_messages
        if (keys.contains("uuid") || keys.contains("chat_messages")) {
            return ChatFormat.CLAUDE
        }
        
        // 通用格式特征: role, content 或 messages 数组
        if (keys.contains("role") && keys.contains("content")) {
            return ChatFormat.GENERIC_JSON
        }
        
        if (keys.contains("messages")) {
            val messages = obj["messages"]
            if (messages is JsonArray && messages.isNotEmpty()) {
                val firstMsg = messages.first()
                if (firstMsg is JsonObject && 
                    firstMsg.containsKey("role") && 
                    firstMsg.containsKey("content")) {
                    return ChatFormat.GENERIC_JSON
                }
            }
        }
        
        return ChatFormat.GENERIC_JSON
    }
    
    /**
     * 检测是否为 Markdown 格式
     */
    private fun isMarkdownFormat(content: String): Boolean {
        val lines = content.lines()
        
        // 检测新版注释标记 (强匹配)
        val hasCommentMarkers = lines.any {
            val trimmed = it.trim()
            trimmed.startsWith("<!-- chat-info:") || trimmed.startsWith("<!-- msg:")
        }
        
        if (hasCommentMarkers) {
            return true
        }
        
        // 检测是否包含 Markdown 标题
        val hasMarkdownHeaders = lines.any { 
            it.trim().startsWith("#") 
        }
        
        // 检测是否包含常见的对话标记 (严格匹配)
        // 只匹配: ## Role 或 ## Role: 且整行只能有这些内容
        val hasDialogueMarkers = lines.any { line ->
            val trimmed = line.trim()
            trimmed.matches(Regex("^##\\s*(User|Assistant|AI|System|Model|用户|助手|系统|模型)[:：]?\\s*$", RegexOption.IGNORE_CASE))
        }
        
        return hasMarkdownHeaders && hasDialogueMarkers
    }
    
    /**
     * 检测是否为 CSV 格式
     */
    private fun isCsvFormat(content: String): Boolean {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return false
        
        // Operit's versioned CSV has a stable header and may contain multiline fields,
        // so relying on every physical line containing a comma is insufficient.
        val normalizedFirstLine = lines.first().removePrefix("\uFEFF").trim()
        if (normalizedFirstLine == ChatHistoryCsv.HEADER.joinToString(",")) {
            return true
        }

        // 检查第一行是否包含 CSV 头
        val firstLine = normalizedFirstLine.lowercase()
        val hasCsvHeaders = firstLine.contains("timestamp") || 
                           firstLine.contains("role") || 
                           firstLine.contains("content") ||
                           firstLine.contains("sender")
        
        // 检查是否有逗号分隔
        val hasCommaSeparators = lines.count { it.contains(",") } > lines.size / 2
        
        return hasCsvHeaders && hasCommaSeparators
    }
    
    /**
     * 根据文件扩展名推测格式
     */
    fun detectFormatByExtension(fileName: String): ChatFormat? {
        return when (fileName.substringAfterLast('.').lowercase()) {
            "json" -> null // JSON 需要进一步检测内容
            "md", "markdown" -> ChatFormat.MARKDOWN
            "csv" -> ChatFormat.CSV
            "txt" -> ChatFormat.PLAIN_TEXT
            "html", "htm" -> ChatFormat.UNKNOWN // HTML 暂不支持导入
            else -> null
        }
    }
}
