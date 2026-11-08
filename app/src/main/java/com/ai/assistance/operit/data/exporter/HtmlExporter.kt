package com.ai.assistance.operit.data.exporter

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.io.StringWriter
import java.io.Writer
import java.time.format.DateTimeFormatter

/**
 * HTML 格式导出器
 */
object HtmlExporter {

    private const val HTML_CONTENT_WRITE_CHUNK_CHARACTER_COUNT = 64 * 1024
    private const val CONTENT_PROGRESS_CHARACTER_COUNT = 256 * 1024
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 导出单个对话为 HTML
     */
    fun exportSingle(context: Context, chatHistory: ChatHistory): String {
        val writer = StringWriter()
        writeSingleToWriter(context, chatHistory, writer)
        return writer.toString()
    }

    /**
     * 导出多个对话为 HTML
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
            writeConversationToWriter(context, writer, chatHistory)
        }
        writeMultipleFooter(context, writer)
        return writer.toString()
    }

    /**
     * 写入多个对话的 HTML 头部。
     */
    fun writeMultipleHeader(
        context: Context,
        chatHistories: List<ChatHistory>,
        totalMessageCount: Int,
        writer: Writer,
    ) {
        appendHtmlHeader(writer, context.getString(R.string.html_export_title))

        writer.appendLine("<div class=\"export-info\">")
        writer.appendLine("  <h1>${context.getString(R.string.html_export_title)}</h1>")
        writer.appendLine("  <p><strong>${context.getString(R.string.html_export_time)}:</strong> ${java.time.LocalDateTime.now().format(dateFormatter)}</p>")
        writer.appendLine("  <p><strong>${context.getString(R.string.html_export_conversation_count)}:</strong> ${chatHistories.size}</p>")
        writer.appendLine("  <p><strong>${context.getString(R.string.html_export_total_messages)}:</strong> $totalMessageCount</p>")
        writer.appendLine("</div>")
        writer.appendLine("<hr>")
    }

    /**
     * 写入对话之间的 HTML 分隔线。
     */
    fun writeConversationSeparator(writer: Writer, index: Int) {
        if (index > 0) {
            writer.appendLine("<hr class=\"conversation-divider\">")
        }
    }

    /**
     * 写入单个对话。
     */
    fun writeSingleToWriter(
        context: Context,
        chatHistory: ChatHistory,
        writer: Writer,
        onContentCharactersWritten: (Long) -> Unit = {},
    ) {
        appendHtmlHeader(writer, chatHistory.title)
        writeConversationToWriter(context, writer, chatHistory, onContentCharactersWritten)
        appendHtmlFooter(context, writer)
    }

    /**
     * 写入单个对话内容，不包含 HTML 文档头尾。
     */
    fun writeConversationToWriter(
        context: Context,
        writer: Writer,
        chatHistory: ChatHistory,
        onContentCharactersWritten: (Long) -> Unit = {},
    ) {
        appendChatContent(context, writer, chatHistory, onContentCharactersWritten)
    }

    /**
     * 写入多个对话的 HTML 尾部。
     */
    fun writeMultipleFooter(context: Context, writer: Writer) {
        appendHtmlFooter(context, writer)
    }
    
    /**
     * 添加 HTML 头部
     */
    private fun appendHtmlHeader(writer: Writer, title: String) {
        writer.appendLine("<!DOCTYPE html>")
        writer.appendLine("<html lang=\"zh-CN\">")
        writer.appendLine("<head>")
        writer.appendLine("  <meta charset=\"UTF-8\">")
        writer.appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        writer.appendLine("  <title>$title</title>")
        writer.appendLine("  <style>")
        writer.appendLine(getCss())
        writer.appendLine("  </style>")
        writer.appendLine("</head>")
        writer.appendLine("<body>")
        writer.appendLine("<div class=\"container\">")
    }
    
    /**
     * 添加对话内容
     */
    private fun appendChatContent(
        context: Context,
        writer: Writer,
        chatHistory: ChatHistory,
        onContentCharactersWritten: (Long) -> Unit,
    ) {
        writer.appendLine("<div class=\"conversation\">")
        writer.appendLine("  <div class=\"conversation-header\">")
        writer.appendLine("    <h2>${escapeHtml(chatHistory.title)}</h2>")
        writer.appendLine("    <div class=\"metadata\">")
        writer.appendLine("      <span><strong>${context.getString(R.string.html_export_created)}:</strong> ${chatHistory.createdAt.format(dateFormatter)}</span>")
        writer.appendLine("      <span><strong>${context.getString(R.string.html_export_updated)}:</strong> ${chatHistory.updatedAt.format(dateFormatter)}</span>")
        if (chatHistory.group != null) {
            writer.appendLine("      <span><strong>${context.getString(R.string.html_export_group)}:</strong> ${escapeHtml(chatHistory.group)}</span>")
        }
        writer.appendLine("      <span><strong>${context.getString(R.string.html_export_message_count)}:</strong> ${chatHistory.messages.size}</span>")
        writer.appendLine("    </div>")
        writer.appendLine("  </div>")
        writer.appendLine("  <div class=\"messages\">")

        for (message in chatHistory.messages) {
            appendMessageHtml(context, writer, message, onContentCharactersWritten)
        }

        writer.appendLine("  </div>")
        writer.appendLine("</div>")
    }
    
    /**
     * 添加单条消息
     */
    private fun appendMessageHtml(
        context: Context,
        writer: Writer,
        message: ChatMessage,
        onContentCharactersWritten: (Long) -> Unit,
    ) {
        val messageClass = if (message.sender == "user") "user" else "assistant"
        val icon = if (message.sender == "user") "👤" else "🤖"
        val role = if (message.sender == "user") {
            context.getString(R.string.export_user)
        } else {
            context.getString(R.string.export_assistant)
        }

        writer.appendLine("    <div class=\"message $messageClass\">")
        writer.appendLine("      <div class=\"message-header\">")
        writer.appendLine("        <span class=\"role\">$icon $role</span>")
        if (message.modelName.isNotEmpty() && message.modelName != "markdown" && message.modelName != "unknown") {
            writer.appendLine("        <span class=\"model\">${escapeHtml(message.modelName)}</span>")
        }
        writer.appendLine("      </div>")
        writer.appendLine("      <div class=\"message-content\">")
        writer.append("        ")
        writeFormattedContent(writer, message.content, onContentCharactersWritten)
        writer.appendLine()
        writer.appendLine("      </div>")
        writer.appendLine("    </div>")
    }
    
    /**
     * 添加 HTML 尾部
     */
    private fun appendHtmlFooter(context: Context, writer: Writer) {
        writer.appendLine("</div>")
        writer.appendLine("<footer>")
        writer.appendLine("  <p>${context.getString(R.string.html_export_footer)}</p>")
        writer.appendLine("</footer>")
        writer.appendLine("</body>")
        writer.appendLine("</html>")
    }
    
    /**
     * 获取 CSS 样式
     */
    private fun getCss(): String {
        return """
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Helvetica', 'Arial', sans-serif;
            background: #f5f5f5;
            color: #333;
            line-height: 1.6;
        }
        .container {
            max-width: 900px;
            margin: 0 auto;
            padding: 20px;
            background: white;
            min-height: 100vh;
        }
        .export-info {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
        }
        .export-info h1 {
            margin-bottom: 15px;
            color: #2c3e50;
        }
        .export-info p {
            margin: 5px 0;
        }
        .conversation {
            margin-bottom: 40px;
        }
        .conversation-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px;
            border-radius: 8px 8px 0 0;
        }
        .conversation-header h2 {
            margin-bottom: 10px;
        }
        .metadata {
            display: flex;
            flex-wrap: wrap;
            gap: 15px;
            font-size: 14px;
            opacity: 0.9;
        }
        .messages {
            background: #fafafa;
            padding: 20px;
            border-radius: 0 0 8px 8px;
        }
        .message {
            background: white;
            margin-bottom: 15px;
            padding: 15px;
            border-radius: 8px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
        }
        .message.user {
            border-left: 4px solid #667eea;
        }
        .message.assistant {
            border-left: 4px solid #764ba2;
        }
        .message-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
            padding-bottom: 8px;
            border-bottom: 1px solid #eee;
        }
        .role {
            font-weight: 600;
            font-size: 16px;
        }
        .model {
            font-size: 12px;
            color: #666;
            background: #f0f0f0;
            padding: 3px 8px;
            border-radius: 4px;
        }
        .message-content {
            white-space: pre-wrap;
            word-wrap: break-word;
        }
        .conversation-divider {
            border: none;
            border-top: 2px dashed #ddd;
            margin: 40px 0;
        }
        hr {
            border: none;
            border-top: 1px solid #eee;
            margin: 20px 0;
        }
        footer {
            text-align: center;
            padding: 20px;
            color: #666;
            font-size: 14px;
        }
        code {
            background: #f4f4f4;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Courier New', monospace;
        }
        pre {
            background: #f4f4f4;
            padding: 10px;
            border-radius: 5px;
            overflow-x: auto;
            margin: 10px 0;
        }
        """.trimIndent()
    }
    
    /**
     * 转义 HTML 特殊字符
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    /**
     * 逐段格式化内容（保留换行，转义HTML）。
     */
    private fun writeFormattedContent(
        writer: Writer,
        content: String,
        onContentCharactersWritten: (Long) -> Unit,
    ) {
        var inCodeBlock = false
        var lineStart = 0

        while (lineStart <= content.length) {
            val lineBreakStart = findLineBreakStart(content, lineStart)
            val lineEnd = lineBreakStart ?: content.length
            if (startsWithCodeFence(content, lineStart, lineEnd)) {
                reportCharacters(lineEnd - lineStart, onContentCharactersWritten)
                if (inCodeBlock) {
                    writer.append("</code></pre>")
                    inCodeBlock = false
                } else {
                    writer.append("<pre><code>")
                    inCodeBlock = true
                }
            } else if (inCodeBlock) {
                writeEscapedRange(writer, content, lineStart, lineEnd, onContentCharactersWritten)
                writer.appendLine()
            } else {
                writeEscapedRange(writer, content, lineStart, lineEnd, onContentCharactersWritten)
                writer.append("<br>")
            }

            if (lineBreakStart == null) {
                break
            }
            val lineBreakLength = if (
                content[lineBreakStart] == '\r' &&
                    lineBreakStart + 1 < content.length &&
                    content[lineBreakStart + 1] == '\n'
            ) {
                2
            } else {
                1
            }
            reportCharacters(lineBreakLength, onContentCharactersWritten)
            lineStart = lineBreakStart + lineBreakLength
        }

        if (inCodeBlock) {
            writer.append("</code></pre>")
        }
    }

    private fun writeEscapedRange(
        writer: Writer,
        content: String,
        start: Int,
        end: Int,
        onContentCharactersWritten: (Long) -> Unit,
    ) {
        if (start == end) {
            return
        }

        // 调试意图：逐字符拼接转义结果会让超长 HTML 消息在格式化阶段持续扩容和复制缓冲区，造成长时间无可见进度；普通文本区间直接写入 Writer，只为特殊字符生成实体字符串。
        var offset = start
        var processedSinceProgress = 0
        while (offset < end) {
            val escapedCharacter = when (content[offset]) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> null
            }

            if (escapedCharacter != null) {
                writer.write(escapedCharacter)
                offset++
                processedSinceProgress++
            } else {
                val plainTextEnd = findNextHtmlEscapeCharacter(
                    content = content,
                    start = offset,
                    end = minOf(end, offset + HTML_CONTENT_WRITE_CHUNK_CHARACTER_COUNT),
                )
                val plainTextLength = plainTextEnd - offset
                writer.write(content, offset, plainTextLength)
                offset = plainTextEnd
                processedSinceProgress += plainTextLength
            }

            if (processedSinceProgress >= CONTENT_PROGRESS_CHARACTER_COUNT) {
                onContentCharactersWritten(processedSinceProgress.toLong())
                processedSinceProgress = 0
            }
        }

        if (processedSinceProgress > 0) {
            onContentCharactersWritten(processedSinceProgress.toLong())
        }
    }

    private fun findNextHtmlEscapeCharacter(
        content: String,
        start: Int,
        end: Int,
    ): Int {
        var offset = start
        while (offset < end && !isHtmlEscapeCharacter(content[offset])) {
            offset++
        }
        return offset
    }

    private fun isHtmlEscapeCharacter(character: Char): Boolean {
        return when (character) {
            '&', '<', '>', '"', '\'' -> true
            else -> false
        }
    }

    private fun reportCharacters(
        characterCount: Int,
        onContentCharactersWritten: (Long) -> Unit,
    ) {
        var remaining = characterCount
        while (remaining > 0) {
            val chunk = minOf(remaining, CONTENT_PROGRESS_CHARACTER_COUNT)
            onContentCharactersWritten(chunk.toLong())
            remaining -= chunk
        }
    }

    private fun startsWithCodeFence(content: String, start: Int, end: Int): Boolean {
        var firstNonWhitespace = start
        while (firstNonWhitespace < end && content[firstNonWhitespace].isWhitespace()) {
            firstNonWhitespace++
        }
        return firstNonWhitespace + 3 <= end && content.startsWith("```", firstNonWhitespace)
    }

    private fun findLineBreakStart(content: String, start: Int): Int? {
        // 调试意图：分别查找两种换行符会在只有一种换行符的长文本上反复扫描剩余全文，退化为 O(n²)；单次顺序扫描保证每个字符只检查一次。
        var offset = start
        while (offset < content.length) {
            when (content[offset]) {
                '\r', '\n' -> return offset
            }
            offset++
        }
        return null
    }
}
