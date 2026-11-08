package com.ai.assistance.operit.core.chat.hooks

import android.content.Context
import com.ai.assistance.operit.data.character.ActivePrompt
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import kotlinx.coroutines.flow.first

private const val ACTIVE_PROMPT_TYPE_CHARACTER_CARD = "character_card"
private const val ACTIVE_PROMPT_TYPE_CHARACTER_GROUP = "character_group"

suspend fun buildActivePromptHookMetadata(
    context: Context,
    chatId: String? = null,
    roleCardId: String? = null
): Map<String, Any?> {
    val appContext = context.applicationContext
    val activePrompt = resolveHookActivePrompt(appContext, chatId, roleCardId)
    return mapOf("activePrompt" to activePromptToMetadata(appContext, activePrompt))
}

private suspend fun resolveHookActivePrompt(
    context: Context,
    chatId: String?,
    roleCardId: String?
): ActivePrompt {
    val boundChat = resolveBoundChat(context, chatId)
    val boundGroupId = boundChat?.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
    if (boundGroupId != null) {
        return ActivePrompt.CharacterGroup(boundGroupId)
    }

    val resolvedRoleCardId = roleCardId?.trim()?.takeIf { it.isNotBlank() }
    if (resolvedRoleCardId != null) {
        return ActivePrompt.CharacterCard(resolvedRoleCardId)
    }

    val boundCardName = boundChat?.characterCardName?.trim()?.takeIf { it.isNotBlank() }
    if (boundCardName != null) {
        val boundCardId =
            CharacterCardManager.getInstance(context)
                .findCharacterCardByName(boundCardName)
                ?.id
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        if (boundCardId != null) {
            return ActivePrompt.CharacterCard(boundCardId)
        }
    }

    return ActivePromptManager.getInstance(context).getActivePrompt()
}

private suspend fun resolveBoundChat(
    context: Context,
    chatId: String?
): ChatHistory? {
    val normalizedChatId = chatId?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return ChatHistoryManager.getInstance(context)
        .chatHistoriesFlow
        .first()
        .firstOrNull { it.id == normalizedChatId }
}

private suspend fun activePromptToMetadata(
    context: Context,
    activePrompt: ActivePrompt
): Map<String, Any?> {
    return when (activePrompt) {
        is ActivePrompt.CharacterCard -> {
            val name =
                runCatching {
                    CharacterCardManager.getInstance(context).getCharacterCard(activePrompt.id).name
                }.getOrDefault("")
            mapOf(
                "type" to ACTIVE_PROMPT_TYPE_CHARACTER_CARD,
                "id" to activePrompt.id,
                "name" to name
            )
        }

        is ActivePrompt.CharacterGroup -> {
            val name =
                CharacterGroupCardManager.getInstance(context)
                    .getCharacterGroupCard(activePrompt.id)
                    ?.name
                    .orEmpty()
            mapOf(
                "type" to ACTIVE_PROMPT_TYPE_CHARACTER_GROUP,
                "id" to activePrompt.id,
                "name" to name
            )
        }
    }
}
