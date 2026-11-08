package com.ai.assistance.operit.data.chat.archive

import com.ai.assistance.operit.data.chat.folders.ChatFolder
import com.ai.assistance.operit.data.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.data.model.MessageVariantEntity

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.OperitChatArchive")
@Serializable
data class OperitChatArchive(
    val archiveType: String = ARCHIVE_TYPE,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val chats: List<OperitArchivedChat>,
    val folders: List<ChatFolder> = emptyList(),
) {
    companion object {
        const val ARCHIVE_TYPE = "operit_chat_archive"
        const val CURRENT_FORMAT_VERSION = 3
    }
}

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.OperitArchivedChat")
@Serializable
data class OperitArchivedChat(
    val id: String,
    val title: String,
    val messages: List<OperitArchivedMessage>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val currentWindowSize: Long = 0L,
    val group: String? = null,
    val displayOrder: Long = 0L,
    val workspace: String? = null,
    val workspaceEnv: String? = null,
    val parentChatId: String? = null,
    val characterCardName: String? = null,
    val characterGroupId: String? = null,
    val locked: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val folderId: String? = null,
    val lastUsedAt: Long = 0L,
    val folderPath: List<ChatFolder> = emptyList(),
    val usageBreakdownJson: String? = null,
) {
    fun toChatHistory(): ChatHistory {
        return ChatHistory(
            id = id,
            title = title,
            messages = messages.map { it.baseMessage },
            createdAt = createdAt,
            updatedAt = updatedAt,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            currentWindowSize = currentWindowSize,
            usageBreakdownJson = usageBreakdownJson,
            group = group,
            displayOrder = displayOrder,
            workspace = workspace,
            workspaceEnv = workspaceEnv,
            parentChatId = parentChatId,
            characterCardName = characterCardName,
            characterGroupId = characterGroupId,
            locked = locked,
            pinned = pinned,
            archived = archived,
            folderId = folderId,
            lastUsedAt = lastUsedAt,
        )
    }

    companion object {
        fun fromChatHistory(
            history: ChatHistory,
            messages: List<OperitArchivedMessage>,
        ): OperitArchivedChat {
            return OperitArchivedChat(
                id = history.id,
                title = history.title,
                messages = messages,
                createdAt = history.createdAt,
                updatedAt = history.updatedAt,
                inputTokens = history.inputTokens,
                outputTokens = history.outputTokens,
                currentWindowSize = history.currentWindowSize,
                usageBreakdownJson = history.usageBreakdownJson,
                group = history.group,
                displayOrder = history.displayOrder,
                workspace = history.workspace,
                workspaceEnv = history.workspaceEnv,
                parentChatId = history.parentChatId,
                characterCardName = history.characterCardName,
                characterGroupId = history.characterGroupId,
                locked = history.locked,
                pinned = history.pinned,
                archived = history.archived,
                folderId = history.folderId,
                lastUsedAt = history.lastUsedAt,
            )
        }
    }
}

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.OperitArchivedMessage")
@Serializable
data class OperitArchivedMessage(
    val baseMessage: ChatMessage,
    val variants: List<OperitArchivedMessageVariant> = emptyList(),
)

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.OperitArchivedMessageVariant")
@Serializable(with = ArchivedMessageVariantSerializer::class)
data class OperitArchivedMessageVariant(
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
) {
    fun toEntity(chatId: String, messageTimestamp: Long): MessageVariantEntity {
        return MessageVariantEntity(
            chatId = chatId,
            messageTimestamp = messageTimestamp,
            variantIndex = variantIndex,
            sections = MessageSectionStorage.encode(sections),
            searchText = MessageSectionCodec.searchText(sections),
            roleName = roleName,
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
        fun fromEntity(entity: MessageVariantEntity): OperitArchivedMessageVariant {
            return OperitArchivedMessageVariant(
                variantIndex = entity.variantIndex,
                sections = MessageSectionStorage.decode(entity.sections, entity.searchText),
                roleName = entity.roleName,
                provider = entity.provider,
                modelName = entity.modelName,
                inputTokens = entity.inputTokens,
                outputTokens = entity.outputTokens,
                cachedInputTokens = entity.cachedInputTokens,
                sentAt = entity.sentAt,
                outputDurationMs = entity.outputDurationMs,
                waitDurationMs = entity.waitDurationMs,
                completedAt = entity.completedAt,
            )
        }
    }
}
