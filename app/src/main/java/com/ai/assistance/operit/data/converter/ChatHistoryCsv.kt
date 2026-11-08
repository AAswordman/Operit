package com.ai.assistance.operit.data.converter

import com.ai.assistance.operit.data.chat.folders.ChatFolder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.chat.archive.OperitArchivedChat
import com.ai.assistance.operit.data.chat.archive.OperitArchivedMessageVariant
import java.io.Reader
import java.io.Writer
import java.time.format.DateTimeFormatter

/**
 * Operit 完整聊天记录导出的版本化 CSV 表示。
 *
 * 每个会话由一条会话行、消息行以及可选的变体行组成。
 * 内容直接写入目标 Writer，避免为大消息额外创建转义后的字符串副本。
 */
object ChatHistoryCsv {
    const val FORMAT_VERSION = "2"
    const val RECORD_CHAT = "chat"
    const val RECORD_MESSAGE = "message"
    const val RECORD_VARIANT = "variant"

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    val HEADER: List<String> = listOf(
        "format_version",
        "record_type",
        "chat_id",
        "title",
        "created_at",
        "updated_at",
        "chat_input_tokens",
        "chat_output_tokens",
        "current_window_size",
        "group",
        "display_order",
        "workspace",
        "workspace_env",
        "parent_chat_id",
        "character_card_name",
        "character_group_id",
        "locked",
        "pinned",
        "archived",
        "message_timestamp",
        "message_order_index",
        "sender",
        "message_sections",
        "role_name",
        "selected_variant_index",
        "variant_count",
        "provider",
        "model_name",
        "input_tokens",
        "output_tokens",
        "cached_input_tokens",
        "sent_at",
        "output_duration_ms",
        "wait_duration_ms",
        "completed_at",
        "display_mode",
        "is_favorite",
        "variant_index",
        "variant_sections",
        "variant_role_name",
        "variant_provider",
        "variant_model_name",
        "variant_input_tokens",
        "variant_output_tokens",
        "variant_cached_input_tokens",
        "variant_sent_at",
        "variant_output_duration_ms",
        "variant_wait_duration_ms",
        "variant_completed_at",
        "folder_id",
        "last_used_at",
        "folder_path",
        "usage_breakdown_json",
    )

    private val columnIndex = HEADER.withIndex().associate { it.value to it.index }

    /** 识别结构化片段和旧正文格式，文件夹字段允许来自加入文件夹功能之前的备份。 */
    fun supportsHeader(header: List<String>): Boolean {
        val optionalColumns = setOf(
            "message_sections", "variant_sections", "folder_id", "last_used_at", "folder_path", "archived",
            "usage_breakdown_json",
        )
        val requiredColumns = HEADER.filterNot { it in optionalColumns }
        val hasMessageColumns =
            ("message_sections" in header && "variant_sections" in header) ||
                ("message_content" in header && "variant_content" in header)
        return header.distinct().size == header.size &&
            header.containsAll(requiredColumns) && hasMessageColumns
    }

    fun writeHeader(writer: Writer) {
        writeRow(writer, HEADER)
    }

    /** 独立记录空文件夹及父子关系，避免只从对话行重建目录。 */
    fun writeFolder(writer: Writer, folder: ChatFolder) {
        val row = emptyRow()
        put(row, "format_version", FORMAT_VERSION)
        put(row, "record_type", "folder")
        put(row, "folder_id", folder.id)
        put(row, "folder_path", Json.encodeToString(folder))
        writeRow(writer, row)
    }

    fun writeChat(writer: Writer, history: OperitArchivedChat) {
        val row = emptyRow()
        put(row, "format_version", FORMAT_VERSION)
        put(row, "record_type", RECORD_CHAT)
        put(row, "chat_id", history.id)
        put(row, "title", history.title)
        put(row, "created_at", history.createdAt.format(dateFormatter))
        put(row, "updated_at", history.updatedAt.format(dateFormatter))
        put(row, "chat_input_tokens", history.inputTokens.toString())
        put(row, "chat_output_tokens", history.outputTokens.toString())
        put(row, "current_window_size", history.currentWindowSize.toString())
        put(row, "usage_breakdown_json", history.usageBreakdownJson)
        put(row, "group", history.group)
        put(row, "display_order", history.displayOrder.toString())
        put(row, "workspace", history.workspace)
        put(row, "workspace_env", history.workspaceEnv)
        put(row, "parent_chat_id", history.parentChatId)
        put(row, "character_card_name", history.characterCardName)
        put(row, "character_group_id", history.characterGroupId)
        put(row, "locked", history.locked.toString())
        put(row, "pinned", history.pinned.toString())
        put(row, "archived", history.archived.toString())
        put(row, "folder_id", history.folderId)
        put(row, "last_used_at", history.lastUsedAt.toString())
        put(row, "folder_path", Json.encodeToString(history.folderPath))
        writeRow(writer, row)
    }

    fun writeMessage(
        writer: Writer,
        chatId: String,
        orderIndex: Int,
        message: ChatMessage,
    ) {
        val row = emptyRow()
        put(row, "format_version", FORMAT_VERSION)
        put(row, "record_type", RECORD_MESSAGE)
        put(row, "chat_id", chatId)
        put(row, "message_timestamp", message.timestamp.toString())
        put(row, "message_order_index", orderIndex.toString())
        put(row, "sender", message.sender)
        put(row, "message_sections", MessageSectionStorage.encode(message.resolvedSections()))
        put(row, "role_name", message.roleName)
        put(row, "selected_variant_index", message.selectedVariantIndex.toString())
        put(row, "variant_count", message.variantCount.toString())
        put(row, "provider", message.provider)
        put(row, "model_name", message.modelName)
        put(row, "input_tokens", message.inputTokens.toString())
        put(row, "output_tokens", message.outputTokens.toString())
        put(row, "cached_input_tokens", message.cachedInputTokens.toString())
        put(row, "sent_at", message.sentAt.toString())
        put(row, "output_duration_ms", message.outputDurationMs.toString())
        put(row, "wait_duration_ms", message.waitDurationMs.toString())
        put(row, "completed_at", message.completedAt.toString())
        put(row, "display_mode", message.displayMode.name)
        put(row, "is_favorite", message.isFavorite.toString())
        writeRow(writer, row)
    }

    fun writeVariant(
        writer: Writer,
        chatId: String,
        messageTimestamp: Long,
        variant: OperitArchivedMessageVariant,
    ) {
        val row = emptyRow()
        put(row, "format_version", FORMAT_VERSION)
        put(row, "record_type", RECORD_VARIANT)
        put(row, "chat_id", chatId)
        put(row, "message_timestamp", messageTimestamp.toString())
        put(row, "variant_index", variant.variantIndex.toString())
        put(row, "variant_sections", MessageSectionStorage.encode(variant.sections))
        put(row, "variant_role_name", variant.roleName)
        put(row, "variant_provider", variant.provider)
        put(row, "variant_model_name", variant.modelName)
        put(row, "variant_input_tokens", variant.inputTokens.toString())
        put(row, "variant_output_tokens", variant.outputTokens.toString())
        put(row, "variant_cached_input_tokens", variant.cachedInputTokens.toString())
        put(row, "variant_sent_at", variant.sentAt.toString())
        put(row, "variant_output_duration_ms", variant.outputDurationMs.toString())
        put(row, "variant_wait_duration_ms", variant.waitDurationMs.toString())
        put(row, "variant_completed_at", variant.completedAt.toString())
        writeRow(writer, row)
    }

    /** 写入一行兼容 RFC 4180 的 CSV，避免创建转义后的字段副本。 */
    fun writeRow(writer: Writer, values: List<String?>) {
        values.forEachIndexed { index, value ->
            if (index > 0) {
                writer.write(','.code)
            }
            writeField(writer, value.orEmpty())
        }
        writer.write('\n'.code)
    }

    private fun emptyRow(): MutableList<String?> = MutableList(HEADER.size) { null }

    private fun put(row: MutableList<String?>, name: String, value: String?) {
        row[columnIndex.getValue(name)] = value
    }

    private fun writeField(writer: Writer, value: String) {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) {
            writer.write(value)
            return
        }

        writer.write('"'.code)
        var plainStart = 0
        value.forEachIndexed { index, character ->
            if (character == '"') {
                if (index > plainStart) {
                    writer.write(value, plainStart, index - plainStart)
                }
                writer.write("\"\"")
                plainStart = index + 1
            }
        }
        if (plainStart < value.length) {
            writer.write(value, plainStart, value.length - plainStart)
        }
        writer.write('"'.code)
    }

    /** 支持带引号多行字段的小型 RFC 4180 流式读取器。 */
    class RowReader(private val reader: Reader) {
        private var pendingCharacter = NO_PENDING_CHARACTER

        fun nextRow(): List<String>? {
            val fields = ArrayList<String>()
            val field = StringBuilder()
            var inQuotes = false
            var fieldStarted = false
            var sawCharacter = false

            while (true) {
                val code = readCharacter()
                if (code < 0) {
                    if (inQuotes) {
                        throw IllegalArgumentException("Unterminated quoted CSV field")
                    }
                    if (!sawCharacter && fields.isEmpty() && field.isEmpty()) {
                        return null
                    }
                    fields += field.toString()
                    return fields
                }

                sawCharacter = true
                val character = code.toChar()
                if (inQuotes) {
                    if (character == '"') {
                        val nextCode = readCharacter()
                        if (nextCode == '"'.code) {
                            field.append('"')
                        } else {
                            inQuotes = false
                            pendingCharacter = nextCode
                        }
                    } else {
                        field.append(character)
                    }
                    continue
                }

                when (character) {
                    '"' -> {
                        if (fieldStarted || field.isNotEmpty()) {
                            throw IllegalArgumentException("Unexpected quote in CSV field")
                        }
                        inQuotes = true
                        fieldStarted = true
                    }

                    ',' -> {
                        fields += field.toString()
                        field.setLength(0)
                        fieldStarted = false
                    }

                    '\n' -> {
                        fields += field.toString()
                        return fields
                    }

                    '\r' -> {
                        val nextCode = readCharacter()
                        if (nextCode >= 0 && nextCode != '\n'.code) {
                            pendingCharacter = nextCode
                        }
                        fields += field.toString()
                        return fields
                    }

                    else -> {
                        field.append(character)
                        fieldStarted = true
                    }
                }
            }
        }

        private fun readCharacter(): Int {
            if (pendingCharacter != NO_PENDING_CHARACTER) {
                val result = pendingCharacter
                pendingCharacter = NO_PENDING_CHARACTER
                return result
            }
            return reader.read()
        }

        private companion object {
            const val NO_PENDING_CHARACTER = -2
        }
    }
}
