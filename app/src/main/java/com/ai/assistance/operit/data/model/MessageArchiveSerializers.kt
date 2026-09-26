package com.ai.assistance.operit.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 新备份只有 sections；版本 2 和旧聊天数组的 content 在入口一次转换。 */
private fun decodeSectionsObject(decoder: JsonDecoder): JsonObject {
    val fields = decoder.decodeJsonElement().jsonObject.toMutableMap()
    val oldContent = fields.remove("content")
    fields["sections"] = if (fields.containsKey("sections")) {
        decoder.json.encodeToJsonElement(
            MessageSectionStorage.decodeLegacySections(fields.getValue("sections"))
        )
    } else {
        decoder.json.encodeToJsonElement(
            MessageSectionStorage.decodeLegacy(oldContent?.jsonPrimitive?.content.orEmpty())
        )
    }
    return JsonObject(fields)
}

@Serializable
private data class StoredChatMessage(
    val sender: String,
    val sections: List<MessageSection>,
    val timestamp: Long = ChatMessageTimestampAllocator.next(),
    val roleName: String = "",
    val selectedVariantIndex: Int = 0,
    val variantCount: Int = 1,
    val provider: String = "",
    val modelName: String = "",
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val sentAt: Long = 0L,
    val outputDurationMs: Long = 0L,
    val waitDurationMs: Long = 0L,
    val completedAt: Long = 0L,
    val displayMode: ChatMessageDisplayMode = ChatMessageDisplayMode.NORMAL,
    val isFavorite: Boolean = false,
)

object ChatMessageSerializer : KSerializer<ChatMessage> {
    override val descriptor: SerialDescriptor = StoredChatMessage.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ChatMessage) {
        encoder.encodeSerializableValue(StoredChatMessage.serializer(), StoredChatMessage(
            sender = value.sender,
            sections = value.resolvedSections(),
            timestamp = value.timestamp,
            roleName = value.roleName,
            selectedVariantIndex = value.selectedVariantIndex,
            variantCount = value.variantCount,
            provider = value.provider,
            modelName = value.modelName,
            inputTokens = value.inputTokens,
            outputTokens = value.outputTokens,
            cachedInputTokens = value.cachedInputTokens,
            sentAt = value.sentAt,
            outputDurationMs = value.outputDurationMs,
            waitDurationMs = value.waitDurationMs,
            completedAt = value.completedAt,
            displayMode = value.displayMode,
            isFavorite = value.isFavorite,
        ))
    }

    override fun deserialize(decoder: Decoder): ChatMessage {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Message sections require JSON")
        val stored = jsonDecoder.json.decodeFromJsonElement(
            StoredChatMessage.serializer(), decodeSectionsObject(jsonDecoder)
        )
        return ChatMessage(
            content = MessageSectionCodec.render(stored.sections),
            sender = stored.sender,
            sections = stored.sections,
            timestamp = stored.timestamp,
            roleName = stored.roleName,
            selectedVariantIndex = stored.selectedVariantIndex,
            variantCount = stored.variantCount,
            provider = stored.provider,
            modelName = stored.modelName,
            inputTokens = stored.inputTokens,
            outputTokens = stored.outputTokens,
            cachedInputTokens = stored.cachedInputTokens,
            sentAt = stored.sentAt,
            outputDurationMs = stored.outputDurationMs,
            waitDurationMs = stored.waitDurationMs,
            completedAt = stored.completedAt,
            displayMode = stored.displayMode,
            isFavorite = stored.isFavorite,
        )
    }
}

@Serializable
private data class StoredMessageVariant(
    val variantIndex: Int,
    val sections: List<MessageSection>,
    val roleName: String = "",
    val provider: String = "",
    val modelName: String = "",
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val sentAt: Long = 0L,
    val outputDurationMs: Long = 0L,
    val waitDurationMs: Long = 0L,
    val completedAt: Long = 0L,
)

object ArchivedMessageVariantSerializer : KSerializer<OperitArchivedMessageVariant> {
    override val descriptor: SerialDescriptor = StoredMessageVariant.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OperitArchivedMessageVariant) {
        encoder.encodeSerializableValue(StoredMessageVariant.serializer(), StoredMessageVariant(
            variantIndex = value.variantIndex,
            sections = value.sections,
            roleName = value.roleName,
            provider = value.provider,
            modelName = value.modelName,
            inputTokens = value.inputTokens,
            outputTokens = value.outputTokens,
            cachedInputTokens = value.cachedInputTokens,
            sentAt = value.sentAt,
            outputDurationMs = value.outputDurationMs,
            waitDurationMs = value.waitDurationMs,
            completedAt = value.completedAt,
        ))
    }

    override fun deserialize(decoder: Decoder): OperitArchivedMessageVariant {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Message sections require JSON")
        val stored = jsonDecoder.json.decodeFromJsonElement(
            StoredMessageVariant.serializer(), decodeSectionsObject(jsonDecoder)
        )
        return OperitArchivedMessageVariant(
            variantIndex = stored.variantIndex,
            sections = stored.sections,
            roleName = stored.roleName,
            provider = stored.provider,
            modelName = stored.modelName,
            inputTokens = stored.inputTokens,
            outputTokens = stored.outputTokens,
            cachedInputTokens = stored.cachedInputTokens,
            sentAt = stored.sentAt,
            outputDurationMs = stored.outputDurationMs,
            waitDurationMs = stored.waitDurationMs,
            completedAt = stored.completedAt,
        )
    }
}

