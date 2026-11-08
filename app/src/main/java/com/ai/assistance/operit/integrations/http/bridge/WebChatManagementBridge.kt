package com.ai.assistance.operit.integrations.http.bridge

import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.integrations.http.WebChatSummary
import com.ai.assistance.operit.integrations.http.WebDeleteGroupRequest
import com.ai.assistance.operit.integrations.http.WebRenameGroupRequest
import com.ai.assistance.operit.integrations.http.WebUpdateChatRequest
import com.ai.assistance.operit.integrations.http.WebChatReorderItem
import com.ai.assistance.operit.services.ChatServiceCore
import kotlinx.coroutines.flow.first

internal class WebChatManagementBridge(
    private val core: ChatServiceCore,
    private val chatHistoryManager: ChatHistoryManager,
    private val activePromptManager: ActivePromptManager
) {
    suspend fun updateChat(
        chatId: String,
        request: WebUpdateChatRequest,
        currentChatMeta: suspend (String) -> ChatHistory?,
        buildChatSummary: suspend (ChatHistory) -> WebChatSummary
    ): WebChatSummary? {
        val normalizedTitle = request.title?.trim()?.takeIf { it.isNotBlank() }
        val normalizedGroup = request.group?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCharacterCardName = request.characterCardName?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCharacterGroupId = request.characterGroupId?.trim()?.takeIf { it.isNotBlank() }

        if (normalizedTitle != null) {
            chatHistoryManager.updateChatTitle(chatId, normalizedTitle)
        }
        if (request.updateGroup) {
            val current = currentChatMeta(chatId) ?: return null
            chatHistoryManager.updateChatOrderAndGroup(
                listOf(current.copy(group = normalizedGroup))
            )
        }
        if (request.updateLocked && request.locked != null) {
            chatHistoryManager.updateChatLocked(chatId, request.locked)
        }
        if (request.updatePinned && request.pinned != null) {
            chatHistoryManager.updateChatPinned(chatId, request.pinned)
        }
        if (request.updateBinding) {
            chatHistoryManager.updateChatCharacterBinding(
                chatId = chatId,
                characterCardName = normalizedCharacterCardName,
                characterGroupId = normalizedCharacterGroupId
            )
            if (core.currentChatId.value == chatId) {
                activePromptManager.activateForChatBinding(
                    characterCardName = normalizedCharacterCardName,
                    characterGroupId = normalizedCharacterGroupId
                )
            }
        }
        return currentChatMeta(chatId)?.let { buildChatSummary(it) }
    }

    suspend fun reorderChats(items: List<WebChatReorderItem>): Boolean {
        val historiesById = chatHistoryManager.chatHistoriesFlow.first().associateBy { it.id }
        val reordered = items.mapNotNull { item ->
            historiesById[item.chatId]?.copy(
                displayOrder = item.displayOrder,
                group = item.group?.trim()?.takeIf { it.isNotBlank() }
            )
        }
        if (reordered.size != items.size) {
            return false
        }
        chatHistoryManager.updateChatOrderAndGroup(reordered)
        return true
    }

    suspend fun renameGroup(request: WebRenameGroupRequest) {
        chatHistoryManager.updateGroupName(
            oldName = request.oldName.trim(),
            newName = request.newName.trim(),
            characterCardName = request.characterCardName?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun deleteGroup(request: WebDeleteGroupRequest) {
        chatHistoryManager.deleteGroup(
            groupName = request.groupName.trim(),
            deleteChats = request.deleteChats,
            characterCardName = request.characterCardName?.trim()?.takeIf { it.isNotBlank() }
        )
    }
}
