package com.ai.assistance.operit.data.exporter

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.io.StringWriter
import java.io.Writer
import java.time.format.DateTimeFormatter

/**
 * Markdown 格式导出器
 */
object MarkdownExporter {
    private const val CONTENT_WRITE_CHUNK_CHARACTER_COUNT = 64 * 1024
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * 导出单个对话为 Markdown
     */
    fun exportSingle(context: Context, chatHistory: ChatHistory): String {
        val writer = StringWriter()
        writeSingleToWriter(context, chatHistory, writer)
        return writer.toString()
    }
    /** Writes one conversation directly to the destination writer. */
    fun writeSingleToWriter(
        context: Context,
        chatHistory: ChatHistory,
        writer: Writer,
        messageCount: Int = chatHistory.messages.size,
        onContentCharactersWritten: (Long) -> Unit = {},
    ) {
        writeSingleHeaderToWriter(
            context = context,
            chatHistory = chatHistory,
            writer = writer,
            messageCount = messageCount,
        )

        for (message in chatHistory.messages) {
            writeMessageToWriter(writer, message, onContentCharactersWritten)
        }
    }

    /** Writes the metadata and heading portion of one conversation. */
    fun writeSingleHeaderToWriter(
        context: Context,
        chatHistory: ChatHistory,
        writer: Writer,
        messageCount: Int = chatHistory.messages.size,
    ) {
        // 结构化元数据注释 (简化格式)
        // 格式: key=value, key=value
        val metaParts = mutableListOf<String>()
        metaParts.add("id=${chatHistory.id}")
        metaParts.add("title=${chatHistory.title}")
        metaParts.add("created=${chatHistory.createdAt.format(dateFormatter)}")
        metaParts.add("updated=${chatHistory.updatedAt.format(dateFormatter)}")
        if (chatHistory.group != null) {
            metaParts.add("group=${chatHistory.group}")
        }
        writer.appendLine("<!-- chat-info: ${metaParts.joinToString(", ")} -->")

        // YAML Front Matter (保留用于兼容性和可读性)
        writer.appendLine("---")
        writer.appendLine("title: ${chatHistory.title}")
        writer.appendLine("created: ${chatHistory.createdAt.format(dateFormatter)}")
        writer.appendLine("updated: ${chatHistory.updatedAt.format(dateFormatter)}")
        if (chatHistory.group != null) {
            writer.appendLine("group: ${chatHistory.group}")
        }
        writer.appendLine("messages: $messageCount")
        writer.appendLine("---")
        writer.appendLine()

        // 标题
        writer.appendLine("# ${chatHistory.title}")
        writer.appendLine()

        // 元信息
        writer.appendLine(context.getString(R.string.markdown_export_created_time, chatHistory.createdAt.format(dateFormatter)))
        writer.appendLine(context.getString(R.string.markdown_export_updated_time, chatHistory.updatedAt.format(dateFormatter)))
        if (chatHistory.group != null) {
            writer.appendLine(context.getString(R.string.markdown_export_group, chatHistory.group))
        }
        writer.appendLine()
        writer.appendLine("---")
        writer.appendLine()
    }

    /** Writes one message without requiring the complete conversation in memory. */
    fun writeMessageToWriter(
        writer: Writer,
        message: ChatMessage,
        onContentCharactersWritten: (Long) -> Unit = {},
    ) {
        appendMessage(writer, message, onContentCharactersWritten)
    }

    
    /**
     * 导出多个对话为 Markdown
     */
    fun exportMultiple(context: Context, chatHistories: List<ChatHistory>): String {
        val writer = StringWriter()

        writer.appendLine(context.getString(R.string.markdown_export_title))
        writer.appendLine()
        writer.appendLine(context.getString(R.string.markdown_export_export_time, java.time.LocalDateTime.now().format(dateFormatter)))
        writer.appendLine(context.getString(R.string.markdown_export_conversation_count, chatHistories.size))
        writer.appendLine(context.getString(R.string.markdown_export_total_messages, chatHistories.sumOf { it.messages.size }))
        writer.appendLine()
        writer.appendLine("---")
        writer.appendLine()

        for ((index, chatHistory) in chatHistories.withIndex()) {
            if (index > 0) {
                writer.appendLine()
                writer.appendLine("---")
                writer.appendLine()
            }

            writeSingleToWriter(context, chatHistory, writer)
        }

        return writer.toString()
    }
    
    /**
     * 添加单条消息
     */
    private fun appendMessage(
        writer: Writer,
        message: ChatMessage,
        onContentCharactersWritten: (Long) -> Unit,
    ) {
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

        writer.appendLine("<!-- msg: ${msgParts.joinToString(", ")} -->")

        // 角色标题 (保留用于可读性)
        val roleIcon = if (message.sender == "user") "👤" else "🤖"
        val roleText = if (message.sender == "user") "User" else "Assistant"
        writer.appendLine("## $roleIcon $roleText")
        writer.appendLine()

        // 消息元数据（可选，视觉展示）
        if (message.modelName.isNotEmpty() && message.modelName != "markdown" && message.modelName != "unknown") {
            writer.appendLine("*Model: ${message.modelName}*")
            writer.appendLine()
        }

        // 消息内容
        writeContent(writer, message.content, onContentCharactersWritten)
        writer.appendLine()
    }

    private fun writeContent(
        writer: Writer,
        content: String,
        onContentCharactersWritten: (Long) -> Unit,
    ) {
        var offset = 0
        while (offset < content.length) {
            val chunkLength = minOf(
                CONTENT_WRITE_CHUNK_CHARACTER_COUNT,
                content.length - offset,
            )
            writer.write(content, offset, chunkLength)
            onContentCharactersWritten(chunkLength.toLong())
            offset += chunkLength
        }
        writer.appendLine()
    }
}
