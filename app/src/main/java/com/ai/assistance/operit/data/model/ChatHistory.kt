package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.util.LocalDateTimeSerializer
import java.util.UUID
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ChatHistory(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val currentWindowSize: Long = 0L,
    /** 仅供旧归档转换器使用；运行时组织使用 folderId。 */
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
    val isFavorite: Boolean = false,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastMessageAt: LocalDateTime? = null,
    /** Room 运行时保留的绝对时间；不进入归档格式。 */
    @Transient val createdAtEpochMillis: Long? = null,
    /** Room 运行时保留的绝对时间；不进入归档格式。 */
    @Transient val lastMessageAtEpochMillis: Long? = null,
)
