package com.ai.assistance.operit.data.exporter

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.time.format.DateTimeFormatter

/**
 * Markdown 格式导出器
 */
object MarkdownExporter {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * 导出单个对话为 Markdown
     */
    fun exportSingle(context: Context, chatHistory: ChatHistory, folderPath: String? = null): String {
        val sb = StringBuilder()

        // 结构化元数据注释 (简化格式)
        // 格式: key=value, key=value
        val metaParts = mutableListOf<String>()
        metaParts.add("id=${chatHistory.id}")
        metaParts.add("title=${chatHistory.title}")
        metaParts.add("created=${chatHistory.createdAt.format(dateFormatter)}")
        metaParts.add("updated=${chatHistory.updatedAt.format(dateFormatter)}")
        if (folderPath != null) {
            metaParts.add("folder=${folderPath}")
        }
        sb.appendLine("<!-- chat-info: ${metaParts.joinToString(", ")} -->")

        // YAML Front Matter (保留用于兼容性和可读性)
        sb.appendLine("---")
        sb.appendLine("title: ${chatHistory.title}")
        sb.appendLine("created: ${chatHistory.createdAt.format(dateFormatter)}")
        sb.appendLine("updated: ${chatHistory.updatedAt.format(dateFormatter)}")
        if (folderPath != null) {
            sb.appendLine("folder: $folderPath")
        }
        sb.appendLine("messages: ${chatHistory.messages.size}")
        sb.appendLine("---")
        sb.appendLine()

        // 标题
        sb.appendLine("# ${chatHistory.title}")
        sb.appendLine()

        // 元信息
        sb.appendLine(context.getString(R.string.markdown_export_created_time, chatHistory.createdAt.format(dateFormatter)))
        sb.appendLine(context.getString(R.string.markdown_export_updated_time, chatHistory.updatedAt.format(dateFormatter)))
        if (folderPath != null) {
            sb.appendLine(context.getString(R.string.markdown_export_group, folderPath))
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // 消息内容
        for (message in chatHistory.messages) {
            appendMessage(sb, message)
        }

        return sb.toString()
    }
    
    /**
     * 导出多个对话为 Markdown
     */
    fun exportMultiple(
        context: Context,
        chatHistories: List<ChatHistory>,
        folderPathsByChatId: Map<String, String> = emptyMap(),
    ): String {
        val sb = StringBuilder()

        sb.appendLine(context.getString(R.string.markdown_export_title))
        sb.appendLine()
        sb.appendLine(context.getString(R.string.markdown_export_export_time, java.time.LocalDateTime.now().format(dateFormatter)))
        sb.appendLine(context.getString(R.string.markdown_export_conversation_count, chatHistories.size))
        sb.appendLine(context.getString(R.string.markdown_export_total_messages, chatHistories.sumOf { it.messages.size }))
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        for ((index, chatHistory) in chatHistories.withIndex()) {
            if (index > 0) {
                sb.appendLine()
                sb.appendLine("---")
                sb.appendLine()
            }

            sb.append(exportSingle(context, chatHistory, folderPathsByChatId[chatHistory.id]))
        }

        return sb.toString()
    }
    
    /**
     * 添加单条消息
     */
    private fun appendMessage(sb: StringBuilder, message: ChatMessage) {
        // 消息元数据注释 (简化格式)
        val msgParts = mutableListOf<String>()
        
        // 角色直接作为第一个参数，或者使用 role=xxx
        // 为了简洁，我们使用 role=xxx，但导入时支持简写
        val role = if (message.sender == "user") "user" else "ai"
        msgParts.add(role) // 简写: <!-- msg: user -->
        
        if (message.modelName.isNotEmpty() && message.modelName != "markdown") {
            msgParts.add("model=${message.modelName}")
        }
        
        msgParts.add("timestamp=${message.timestamp}")
        
        sb.appendLine("<!-- msg: ${msgParts.joinToString(", ")} -->")
        
        // 角色标题 (保留用于可读性)
        val roleIcon = if (message.sender == "user") "👤" else "🤖"
        val roleText = if (message.sender == "user") "User" else "Assistant"
        sb.appendLine("## $roleIcon $roleText")
        sb.appendLine()
        
        // 消息元数据（可选，视觉展示）
        if (message.modelName.isNotEmpty() && message.modelName != "markdown" && message.modelName != "unknown") {
            sb.appendLine("*Model: ${message.modelName}*")
            sb.appendLine()
        }
        
        // 消息内容
        sb.appendLine(message.content)
        sb.appendLine()
    }
}
