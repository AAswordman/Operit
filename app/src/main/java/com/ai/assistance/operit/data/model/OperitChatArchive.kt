package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.util.LocalDateTimeSerializer
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class OperitChatArchive(
    val archiveType: String = ARCHIVE_TYPE,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val folders: List<OperitArchivedFolder> = emptyList(),
    val chats: List<OperitArchivedChat>,
) {
    companion object {
        const val ARCHIVE_TYPE = "operit_chat_archive"
        const val CURRENT_FORMAT_VERSION = 4
    }
}

@Serializable
data class OperitArchivedFolder(
    val id: String,
    val name: String,
    val parentFolderId: String? = null,
    val displayOrder: Long = 0L,
    val createdAt: Long,
) {
    fun toEntity(): ChatFolderEntity =
        ChatFolderEntity(
            id = id,
            name = name,
            parentFolderId = parentFolderId,
            displayOrder = displayOrder,
            createdAt = createdAt,
        )

    companion object {
        fun fromEntity(entity: ChatFolderEntity): OperitArchivedFolder =
            OperitArchivedFolder(
                id = entity.id,
                name = entity.name,
                parentFolderId = entity.parentFolderId,
                displayOrder = entity.displayOrder,
                createdAt = entity.createdAt,
            )
    }
}

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
    val folderId: String? = null,
    val displayOrder: Long = 0L,
    val workspace: String? = null,
    val workspaceEnv: String? = null,
    val parentChatId: String? = null,
    val characterCardName: String? = null,
    val characterGroupId: String? = null,
    val locked: Boolean = false,
    val pinned: Boolean = false,
    val isFavorite: Boolean? = null,
) {
    fun toChatHistory(resolvedFavorite: Boolean, folderId: String? = this.folderId): ChatHistory {
        return ChatHistory(
            id = id,
            title = title,
            messages = messages.map { it.baseMessage },
            createdAt = createdAt,
            updatedAt = updatedAt,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            currentWindowSize = currentWindowSize,
            folderId = folderId,
            displayOrder = displayOrder,
            workspace = workspace,
            workspaceEnv = workspaceEnv,
            parentChatId = parentChatId,
            characterCardName = characterCardName,
            characterGroupId = characterGroupId,
            locked = locked,
            pinned = pinned,
            isFavorite = resolvedFavorite,
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
                group = null,
                folderId = history.folderId,
                displayOrder = history.displayOrder,
                workspace = history.workspace,
                workspaceEnv = history.workspaceEnv,
                parentChatId = history.parentChatId,
                characterCardName = history.characterCardName,
                characterGroupId = history.characterGroupId,
                locked = history.locked,
                pinned = history.pinned,
                isFavorite = history.isFavorite,
            )
        }
    }
}

@Serializable
data class OperitArchivedMessage(
    val baseMessage: ChatMessage,
    val variants: List<OperitArchivedMessageVariant> = emptyList(),
)

@Serializable
data class OperitArchivedMessageVariant(
    val variantIndex: Int,
    val content: String,
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
            content = content,
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
                content = entity.content,
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
