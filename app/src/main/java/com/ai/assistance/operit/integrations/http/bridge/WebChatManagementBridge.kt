package com.ai.assistance.operit.integrations.http.bridge

import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.integrations.http.WebChatReorderItem
import com.ai.assistance.operit.integrations.http.WebChatSummary
import com.ai.assistance.operit.integrations.http.WebDeleteGroupRequest
import com.ai.assistance.operit.integrations.http.WebRenameGroupRequest
import com.ai.assistance.operit.integrations.http.WebUpdateChatRequest
import com.ai.assistance.operit.services.ChatServiceCore

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
        val normalizedFolderId = request.folderId?.trim()?.takeIf { it.isNotBlank() }
        val normalizedGroup = request.group?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCharacterCardName = request.characterCardName?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCharacterGroupId = request.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        val currentBinding = if (request.updateGroup) currentChatMeta(chatId) else null
        val folderCharacterCardName =
            if (request.updateBinding) {
                normalizedCharacterCardName
            } else {
                normalizedCharacterCardName ?: currentBinding?.characterCardName
            }
        val folderCharacterGroupId =
            if (request.updateBinding) {
                normalizedCharacterGroupId
            } else {
                normalizedCharacterGroupId ?: currentBinding?.characterGroupId
            }
        val resolvedFolderId =
            if (request.updateGroup) {
                normalizedGroup?.let {
                    chatHistoryManager.resolveOrCreateLegacyFolderId(
                        groupName = it,
                        characterCardName = folderCharacterCardName,
                        characterGroupId = folderCharacterGroupId,
                    )
                }
            } else {
                normalizedFolderId
            }

        val updated =
            chatHistoryManager.updateChatFromWeb(
                chatId = chatId,
                title = normalizedTitle,
                updateFolder = request.updateFolder || request.updateGroup,
                folderId = resolvedFolderId,
                locked = request.locked.takeIf { request.updateLocked },
                pinned = request.pinned.takeIf { request.updatePinned },
                updateBinding = request.updateBinding,
                characterCardName = normalizedCharacterCardName,
                characterGroupId = normalizedCharacterGroupId,
            )
        if (!updated) {
            return null
        }
        if (request.updateBinding) {
            if (core.currentChatId.value == chatId) {
                activePromptManager.activateForChatBinding(
                    characterCardName = normalizedCharacterCardName,
                    characterGroupId = normalizedCharacterGroupId
                )
            }
        }
        return currentChatMeta(chatId)?.let { buildChatSummary(it) }
    }

    suspend fun reorderChats(
        items: List<WebChatReorderItem>,
        expectedItems: List<WebChatReorderItem>?,
    ): Boolean {
        val currentHistories = chatHistoryManager.getChatHistoriesSnapshot()
        val historiesById = currentHistories.associateBy { it.id }
        if (
            items.map { it.chatId }.distinct().size != items.size ||
                items.any { it.chatId !in historiesById }
        ) {
            return false
        }

        val orderedHistories = resolveReorderItems(items, historiesById) ?: return false
        val expectedHistories =
            if (expectedItems == null) {
                val requestedIds = items.mapTo(hashSetOf()) { it.chatId }
                currentHistories.filter { it.id in requestedIds }
            } else {
                if (
                    expectedItems.map { it.chatId }.distinct().size != expectedItems.size ||
                        expectedItems.mapTo(hashSetOf()) { it.chatId } !=
                            items.mapTo(hashSetOf()) { it.chatId }
                ) {
                    return false
                }
                resolveReorderItems(expectedItems, historiesById) ?: return false
            }

        if (
            expectedItems == null &&
                !isSingleAdjacentSwap(expectedHistories, orderedHistories)
        ) {
            return false
        }
        return chatHistoryManager.reorderProjectedChats(
            expectedHistories = expectedHistories,
            orderedHistories = orderedHistories,
        )
    }

    suspend fun renameGroup(request: WebRenameGroupRequest): Boolean {
        val folderId = resolveGroupMutationFolderId(
            stableFolderId = request.folderId,
            legacyGroupName = request.oldName,
            characterCardName = request.characterCardName,
            characterGroupId = request.characterGroupId,
        ) ?: return false
        return chatHistoryManager.renameFolderIfExists(folderId, request.newName)
    }

    suspend fun deleteGroup(request: WebDeleteGroupRequest): Boolean {
        val folderId = resolveGroupMutationFolderId(
            stableFolderId = request.folderId,
            legacyGroupName = request.groupName,
            characterCardName = request.characterCardName,
            characterGroupId = request.characterGroupId,
        ) ?: return false
        if (request.deleteChats) {
            return chatHistoryManager.deleteFolderWithChatsIfExists(
                folderId = folderId,
                characterCardName = request.characterCardName,
                characterGroupId = request.characterGroupId,
            )
        }
        return chatHistoryManager.deleteFolderIfExists(folderId)
    }

    private suspend fun resolveGroupMutationFolderId(
        stableFolderId: String?,
        legacyGroupName: String,
        characterCardName: String?,
        characterGroupId: String?,
    ): String? {
        val normalizedFolderId = stableFolderId?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedFolderId != null) {
            return normalizedFolderId
        }
        return chatHistoryManager.findLegacyFolderId(
            groupName = legacyGroupName,
            characterCardName = characterCardName,
            characterGroupId = characterGroupId,
        )
    }

    private suspend fun resolveReorderItems(
        items: List<WebChatReorderItem>,
        historiesById: Map<String, ChatHistory>,
    ): List<ChatHistory>? {
        val resolved = ArrayList<ChatHistory>(items.size)
        for (item in items) {
            val history = historiesById[item.chatId] ?: return null
            val stableFolderId = item.folderId?.trim()?.takeIf { it.isNotBlank() }
            val legacyGroupName = item.group?.trim()?.takeIf { it.isNotBlank() }
            val folderId =
                stableFolderId
                    ?: legacyGroupName?.let {
                        chatHistoryManager.findLegacyFolderId(
                            groupName = it,
                            characterCardName = history.characterCardName,
                            characterGroupId = history.characterGroupId,
                        )
                    }
            if (stableFolderId == null && legacyGroupName != null && folderId == null) {
                return null
            }
            resolved +=
                history.copy(
                    displayOrder = item.displayOrder,
                    group = null,
                    folderId = folderId,
                )
        }
        return resolved
    }

    private fun isSingleAdjacentSwap(
        expected: List<ChatHistory>,
        ordered: List<ChatHistory>,
    ): Boolean {
        val folderIds = expected.mapTo(linkedSetOf()) { it.folderId }
        if (ordered.mapTo(linkedSetOf()) { it.folderId } != folderIds) {
            return false
        }
        var changedFolderCount = 0
        for (folderId in folderIds) {
            val before = expected.filter { it.folderId == folderId }.map { it.id }
            val after = ordered.filter { it.folderId == folderId }.map { it.id }
            if (before == after) {
                continue
            }
            val changedIndices = before.indices.filter { before[it] != after.getOrNull(it) }
            if (
                changedIndices.size != 2 ||
                    changedIndices[1] != changedIndices[0] + 1 ||
                    before[changedIndices[0]] != after[changedIndices[1]] ||
                    before[changedIndices[1]] != after[changedIndices[0]]
            ) {
                return false
            }
            changedFolderCount++
        }
        return changedFolderCount <= 1
    }

}
