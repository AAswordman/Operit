package com.ai.assistance.operit.data.exporter

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.io.StringWriter
import java.io.Writer
import java.time.format.DateTimeFormatter

/**
 * 纯文本格式导出器
 */
object TextExporter {

    private const val CONTENT_WRITE_CHUNK_CHARACTER_COUNT = 64 * 1024
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 导出单个对话为纯文本
     */
    fun exportSingle(context: Context, chatHistory: ChatHistory): String {
        val writer = StringWriter()
        writeSingleToWriter(context, chatHistory, writer)
        return writer.toString()
    }

    /**
     * 导出多个对话为纯文本
     */
    fun exportMultiple(context: Context, chatHistories: List<ChatHistory>): String {
        val writer = StringWriter()
        writeMultipleHeader(
            context = context,
            chatHistories = chatHistories,
            totalMessageCount = chatHistories.sumOf { it.messages.size },
            writer = writer,
        )
        chatHistories.forEachIndexed { index, chatHistory ->
            writeConversationSeparator(writer, index)
            writeSingleToWriter(context, chatHistory, writer)
        }
        writeMultipleFooter(context, writer)
        return writer.toString()
    }

    /**
     * 写入多个对话的纯文本头部。
     *
     * 长文本导出使用 Writer，避免把所有结果拼接成一个大字符串。
     */
    fun writeMultipleHeader(
        context: Context,
        chatHistories: List<ChatHistory>,
        totalMessageCount: Int,
        writer: Writer,
    ) {
        // 总览信息
        writer.appendLine("=".repeat(60))
        writer.appendLine(context.getString(R.string.export_chat_history).center(60))
        writer.appendLine("=".repeat(60))
        writer.appendLine()
        writer.appendLine(
            context.getString(
                R.string.export_export_time,
                java.time.LocalDateTime.now().format(dateFormatter)
            )
        )
        writer.appendLine(context.getString(R.string.export_conversation_count, chatHistories.size))
        writer.appendLine(context.getString(R.string.export_total_message_count, totalMessageCount))
        writer.appendLine()
        writer.appendLine("=".repeat(60))
        writer.appendLine()
        writer.appendLine()
    }

    /**
     * 写入对话之间的分隔内容。
     */
    fun writeConversationSeparator(writer: Writer, index: Int) {
        if (index > 0) {
            writer.appendLine()
            writer.appendLine()
        }
    }

    /**
     * 写入单个对话。
     *
     * 长文本按固定大小写入，避免 Writer 调用方再次创建整段正文副本。
     */
    fun writeSingleToWriter(
        context: Context,
        chatHistory: ChatHistory,
        writer: Writer,
        onContentCharactersWritten: (Long) -> Unit = {},
    ) {
        // 调试意图：直接拼接整份文本会在 StringBuilder 扩容时复制已有内容，超长导出会抬高峰值内存。
        writer.appendLine("=".repeat(60))
        writer.appendLine(chatHistory.title.center(60))
        writer.appendLine("=".repeat(60))
        writer.appendLine()

        writer.appendLine(
            context.getString(
                R.string.export_created_time,
                chatHistory.createdAt.format(dateFormatter)
            )
        )
        writer.appendLine(
            context.getString(
                R.string.export_updated_time,
                chatHistory.updatedAt.format(dateFormatter)
            )
        )
        if (chatHistory.group != null) {
            writer.appendLine(context.getString(R.string.export_group, chatHistory.group))
        }
        writer.appendLine(context.getString(R.string.export_message_count, chatHistory.messages.size))
        writer.appendLine()
        writer.appendLine("-".repeat(60))
        writer.appendLine()

        for ((index, message) in chatHistory.messages.withIndex()) {
            if (index > 0) {
                writer.appendLine()
            }
            writeMessage(context, writer, message, onContentCharactersWritten)
        }

        writer.appendLine()
        writer.appendLine("=".repeat(60))
    }

    /**
     * 写入多个对话的纯文本尾部。
     */
    fun writeMultipleFooter(context: Context, writer: Writer) {
        writer.appendLine()
        writer.appendLine()
        writer.appendLine(context.getString(R.string.export_completed))
    }

    /**
     * 添加单条消息
     */
    private fun writeMessage(
        context: Context,
        writer: Writer,
        message: ChatMessage,
        onContentCharactersWritten: (Long) -> Unit,
    ) {
        val roleIcon = if (message.sender == "user") "👤" else "🤖"
        val roleText =
            if (message.sender == "user") {
                context.getString(R.string.export_user)
            } else {
                context.getString(R.string.export_assistant)
            }

        writer.appendLine("[$roleIcon $roleText]")

        if (message.modelName.isNotEmpty() && message.modelName != "markdown" && message.modelName != "unknown") {
            writer.appendLine(context.getString(R.string.export_model, message.modelName))
        }

        writer.appendLine()
        writeContent(writer, message.content, onContentCharactersWritten)
        writer.appendLine()
        writer.appendLine("-".repeat(60))
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

    /**
     * 字符串居中扩展函数
     */
    private fun String.center(width: Int): String {
        if (this.length >= width) return this
        val padding = width - this.length
        val leftPad = padding / 2
        val rightPad = padding - leftPad
        return " ".repeat(leftPad) + this + " ".repeat(rightPad)
    }
}
