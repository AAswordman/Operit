package com.ai.assistance.operit.data.converter

import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageVariantEntity
import java.io.Reader
import java.io.Writer
import java.time.format.DateTimeFormatter

/**
 * Versioned CSV representation for complete Operit chat history exports.
 *
 * A chat is represented by one `chat` row, followed by its `message` rows and
 * the optional `variant` rows belonging to those messages. Content is written
 * directly to the destination writer so large messages do not create an
 * escaped String copy first.
 */
object ChatHistoryCsv {
    const val FORMAT_VERSION = "1"
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
        "message_timestamp",
        "message_order_index",
        "sender",
        "message_content",
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
        "variant_content",
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
    )

    private val columnIndex = HEADER.withIndex().associate { it.value to it.index }

    fun writeHeader(writer: Writer) {
        writeRow(writer, HEADER)
    }

    fun writeChat(writer: Writer, history: ChatHistory) {
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
        put(row, "group", history.group)
        put(row, "display_order", history.displayOrder.toString())
        put(row, "workspace", history.workspace)
        put(row, "workspace_env", history.workspaceEnv)
        put(row, "parent_chat_id", history.parentChatId)
        put(row, "character_card_name", history.characterCardName)
        put(row, "character_group_id", history.characterGroupId)
        put(row, "locked", history.locked.toString())
        put(row, "pinned", history.pinned.toString())
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
        put(row, "message_content", message.content)
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
        variant: MessageVariantEntity,
    ) {
        val row = emptyRow()
        put(row, "format_version", FORMAT_VERSION)
        put(row, "record_type", RECORD_VARIANT)
        put(row, "chat_id", chatId)
        put(row, "message_timestamp", messageTimestamp.toString())
        put(row, "variant_index", variant.variantIndex.toString())
        put(row, "variant_content", variant.content)
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

    /** Writes one RFC 4180-compatible row without creating escaped field copies. */
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

    /** Small streaming RFC 4180 reader that supports quoted multiline fields. */
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
