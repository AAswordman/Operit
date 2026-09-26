package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_variants",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId", "messageTimestamp"]),
        Index(value = ["chatId", "messageTimestamp", "variantIndex"], unique = true),
    ],
)
data class MessageVariantEntity(
    @PrimaryKey(autoGenerate = true) val variantId: Long = 0,
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val sections: String,
    val searchText: String,
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
) {
    fun applyTo(baseMessage: ChatMessage, variantCount: Int): ChatMessage {
        val parsed = MessageSectionStorage.decode(sections, searchText)
        return baseMessage.copy(
            content = MessageSectionCodec.render(parsed),
            sections = parsed,
            roleName = roleName.ifBlank { baseMessage.roleName },
            selectedVariantIndex = variantIndex,
            variantCount = variantCount,
            provider = provider,
            modelName = modelName,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            sentAt = sentAt,
            outputDurationMs = outputDurationMs,
            waitDurationMs = waitDurationMs,
            completedAt = completedAt,
        )
    }

    companion object {
        fun fromChatMessage(
            chatId: String,
            messageTimestamp: Long,
            variantIndex: Int,
            message: ChatMessage,
            variantId: Long = 0,
        ): MessageVariantEntity {
            val sections = message.resolvedSections()
            return MessageVariantEntity(
                variantId = variantId,
                chatId = chatId,
                messageTimestamp = messageTimestamp,
                variantIndex = variantIndex,
                sections = MessageSectionStorage.encode(sections),
                searchText = MessageSectionCodec.searchText(sections),
                roleName = message.roleName,
                provider = message.provider,
                modelName = message.modelName,
                inputTokens = message.inputTokens,
                outputTokens = message.outputTokens,
                cachedInputTokens = message.cachedInputTokens,
                sentAt = message.sentAt,
                outputDurationMs = message.outputDurationMs,
                waitDurationMs = message.waitDurationMs,
                completedAt = message.completedAt,
            )
        }
    }
}
