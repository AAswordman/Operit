package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** 聊天实体类，用于Room数据库存储聊天元数据 */
@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["folderId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChatFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
)
data class ChatEntity(
        @PrimaryKey val id: String = UUID.randomUUID().toString(),
        val title: String,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val inputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val currentWindowSize: Long = 0L,
        /** 旧分类诊断列。运行时组织使用 [folderId]。 */
        val group: String? = null,
        val folderId: String? = null,
        val displayOrder: Long = -createdAt,
        val workspace: String? = null,
        val workspaceEnv: String? = null,
        val parentChatId: String? = null,
        val characterCardName: String? = null,
        val characterGroupId: String? = null,
        val locked: Boolean = false,
        val pinned: Boolean = false,
        val isFavorite: Boolean = false,
        val lastMessageAt: Long? = null
) {
    /** 转换为ChatHistory对象（供UI层使用） */
    fun toChatHistory(messages: List<ChatMessage>): ChatHistory {
        val createdAt = Instant.ofEpochMilli(this.createdAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val updatedAt = Instant.ofEpochMilli(this.updatedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val lastMessageAt = this.lastMessageAt?.let {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        }

        return ChatHistory(
                id = id,
                title = title,
                messages = messages,
                createdAt = createdAt,
                updatedAt = updatedAt,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                currentWindowSize = currentWindowSize,
                group = null,
                folderId = folderId,
                displayOrder = displayOrder,
                workspace = workspace,
                workspaceEnv = workspaceEnv,
                parentChatId = parentChatId,
                characterCardName = characterCardName,
                characterGroupId = characterGroupId,
                locked = locked,
                pinned = pinned,
                isFavorite = isFavorite,
                lastMessageAt = lastMessageAt,
                createdAtEpochMillis = this.createdAt,
                lastMessageAtEpochMillis = this.lastMessageAt,
        )
    }

    companion object {
        /** 从ChatHistory创建ChatEntity */
        fun fromChatHistory(chatHistory: ChatHistory): ChatEntity {
            val now = System.currentTimeMillis()
            return ChatEntity(
                    id = chatHistory.id,
                    title = chatHistory.title,
                    createdAt =
                            chatHistory.createdAtEpochMillis
                                    ?: chatHistory
                                            .createdAt
                                            .atZone(ZoneId.systemDefault())
                                            .toInstant()
                                            .toEpochMilli(),
                    updatedAt =
                            chatHistory
                                    .updatedAt
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                    inputTokens = chatHistory.inputTokens,
                    outputTokens = chatHistory.outputTokens,
                    currentWindowSize = chatHistory.currentWindowSize,
                    group = null,
                    folderId = chatHistory.folderId,
                    displayOrder = if (chatHistory.displayOrder != 0L) chatHistory.displayOrder else -now,
                    workspace = chatHistory.workspace,
                    workspaceEnv = chatHistory.workspaceEnv,
                    parentChatId = chatHistory.parentChatId,
                    characterCardName = chatHistory.characterCardName,
                    characterGroupId = chatHistory.characterGroupId,
                    locked = chatHistory.locked,
                    pinned = chatHistory.pinned,
                    isFavorite = chatHistory.isFavorite,
                    lastMessageAt =
                            chatHistory.lastMessageAt?.let {
                                chatHistory.lastMessageAtEpochMillis
                                    ?: it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            }
            )
        }
    }
}
