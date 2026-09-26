package com.ai.assistance.operit.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** 数据库 sections 列和 CSV 单元格共用的纯 JSON 数组格式。 */
object MessageSectionStorage {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(sections: List<MessageSection>): String = json.encodeToString(sections)
    fun decode(stored: String, legacyContent: String = ""): List<MessageSection> {
        if (stored.isNotEmpty()) {
            val decoded = try {
                decodeStored(stored)
            } catch (_: Exception) {
                null
            }
            if (decoded != null && (decoded.isNotEmpty() || legacyContent.isEmpty())) return decoded
        }
        if (legacyContent.isEmpty()) return emptyList()
        return try {
            decodeLegacy(legacyContent)
        } catch (_: Exception) {
            try {
                MessageSectionCodec.parse(legacyContent)
            } catch (_: Exception) {
                listOf(MessageSection.Text(legacyContent))
            }
        }
    }

    private fun decodeStored(stored: String): List<MessageSection> {
        if (stored.startsWith("operit-sections:1:")) return decodeLegacy(stored)
        return when (val element = json.parseToJsonElement(stored)) {
            is kotlinx.serialization.json.JsonArray ->
                json.decodeFromJsonElement<List<MessageSection>>(element)
            is JsonObject -> decodeLegacySections(
                element["sections"]
                    ?: throw kotlinx.serialization.SerializationException("Missing sections field")
            )
            else -> throw kotlinx.serialization.SerializationException(
                "Expected a sections array or envelope"
            )
        }
    }


    /** 只在旧库升级、旧备份导入时调用，保留已写入的开发版前缀记录。 */
    fun decodeLegacy(content: String): List<MessageSection> {
        val marker = "operit-sections:1:"
        if (!content.startsWith(marker)) return MessageSectionCodec.parse(content)
        val envelope = json.parseToJsonElement(content.removePrefix(marker)).jsonObject
        return decodeLegacySections(envelope.getValue("sections"))
    }

    internal fun decodeLegacySections(element: JsonElement): List<MessageSection> {
        return element.jsonArray.map { item ->
            val fields = item.jsonObject.toMutableMap()
            val name = fields.getValue("type").jsonPrimitive.content.substringAfterLast('.')
            val type = when (name) {
                "Text" -> "text"
                "Thinking" -> "thinking"
                "ToolCall" -> "tool_call"
                "ToolResult" -> "tool_result"
                "Protocol" -> "protocol"
                "Search" -> "search"
                "Status" -> "status"
                else -> name
            }
            fields["type"] = JsonPrimitive(type)
            json.decodeFromJsonElement<MessageSection>(JsonObject(fields))
        }
    }
}
