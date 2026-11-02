package com.ai.assistance.operit.ui.features.conversation.memory.settings

import com.ai.assistance.operit.core.memory.extraction.MemoryAutoSaveQueueStatus

data class MemorySettingsPolicy(
    val profileAutoUpdateEnabled: Boolean = true,
    val profileAutoUpdateLocked: Boolean = false,
    val cloudEmbeddingEnabled: Boolean = true,
)

data class MemorySettingsChat(val id: String, val title: String)

data class MemorySettingsDetails(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val error: String? = null,
    val spaceName: String = "",
    val policy: MemorySettingsPolicy = MemorySettingsPolicy(),
    val hasLegacyEmbeddingConfiguration: Boolean = false,
    val queue: MemoryAutoSaveQueueStatus = MemoryAutoSaveQueueStatus(),
    val nextRunAtMs: Long = 0L,
    val chats: List<MemorySettingsChat> = emptyList(),
)
