package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.util.LocalDateTimeSerializer
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class OperitChatArchive(
    val archiveType: String = ARCHIVE_TYPE,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val chats: List<OperitArchivedChat>,
    val subagentRuns: List<OperitArchivedSubagentRun> = emptyList(),
) {
    companion object {
        const val ARCHIVE_TYPE = "operit_chat_archive"
        const val CURRENT_FORMAT_VERSION = 5
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
    val displayOrder: Long = 0L,
    val workspace: String? = null,
    val workspaceEnv: String? = null,
    val parentChatId: String? = null,
    val chatKind: String? = null,
    val characterCardName: String? = null,
    val characterGroupId: String? = null,
    val locked: Boolean = false,
    val pinned: Boolean = false,
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
            group = group,
            displayOrder = displayOrder,
            workspace = workspace,
            workspaceEnv = workspaceEnv,
            parentChatId = parentChatId,
            chatKind =
                chatKind
                    ?: if (parentChatId == null) {
                        ChatKind.NORMAL.name
                    } else {
                        ChatKind.BRANCH.name
                    },
            characterCardName = characterCardName,
            characterGroupId = characterGroupId,
            locked = locked,
            pinned = pinned,
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
                group = history.group,
                displayOrder = history.displayOrder,
                workspace = history.workspace,
                workspaceEnv = history.workspaceEnv,
                parentChatId = history.parentChatId,
                chatKind = history.chatKind,
                characterCardName = history.characterCardName,
                characterGroupId = history.characterGroupId,
                locked = history.locked,
                pinned = history.pinned,
            )
        }
    }
}

@Serializable
data class OperitArchivedSubagentRun(
    val id: String,
    val parentChatId: String,
    val childChatId: String,
    val parentToolCallId: String? = null,
    val agentProfileId: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val error: String? = null,
    val agentConfigSnapshot: String? = null,
    val modelConfigIdSnapshot: String? = null,
    val modelIndexSnapshot: Int? = null,
    val modelRoundCount: Int = 0,
    val externalOwnerId: String? = null,
    val externalOwnerType: String? = null,
    val toolInvocationCount: Int = 0,
    val archivedAt: Long? = null,
) {
    fun toEntity(): SubagentRunEntity =
        SubagentRunEntity(
            id = id,
            parentChatId = parentChatId,
            childChatId = childChatId,
            parentToolCallId = parentToolCallId,
            agentProfileId = agentProfileId,
            title = title,
            status = status,
            createdAt = createdAt,
            startedAt = startedAt,
            completedAt = completedAt,
            error = error,
            agentConfigSnapshot = agentConfigSnapshot,
            modelConfigIdSnapshot = modelConfigIdSnapshot,
            modelIndexSnapshot = modelIndexSnapshot,
            externalOwnerId = externalOwnerId,
            externalOwnerType = externalOwnerType,
            modelRoundCount = modelRoundCount,
            toolInvocationCount = toolInvocationCount,
            archivedAt = archivedAt,
        )

    companion object {
        fun fromEntity(entity: SubagentRunEntity): OperitArchivedSubagentRun =
            OperitArchivedSubagentRun(
                id = entity.id,
                parentChatId = entity.parentChatId,
                childChatId = entity.childChatId,
                parentToolCallId = entity.parentToolCallId,
                agentProfileId = entity.agentProfileId,
                title = entity.title,
                status = entity.status,
                createdAt = entity.createdAt,
                startedAt = entity.startedAt,
                completedAt = entity.completedAt,
                error = entity.error,
                agentConfigSnapshot = entity.agentConfigSnapshot,
                modelConfigIdSnapshot = entity.modelConfigIdSnapshot,
                modelIndexSnapshot = entity.modelIndexSnapshot,
                externalOwnerId = entity.externalOwnerId,
                externalOwnerType = entity.externalOwnerType,
                modelRoundCount = entity.modelRoundCount,
                toolInvocationCount = entity.toolInvocationCount,
                archivedAt = entity.archivedAt,
            )
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
