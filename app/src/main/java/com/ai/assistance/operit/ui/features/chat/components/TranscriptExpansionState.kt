package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalTranscriptChatId = staticCompositionLocalOf<String?> { null }
internal val LocalTranscriptMessageId = staticCompositionLocalOf<Long?> { null }

/**
 * Remembers which transcript sections the user expanded, per conversation.
 *
 * `rememberSaveable` cannot hold this for the transcript. A conversation that is left behind — a
 * Subagent conversation opened from a card, another chat, anything — has its transcript taken out of
 * the composition, and a saved value is dropped as soon as its composable is forgotten. Keeping the
 * expansion state outside that composition is what lets a conversation come back the way it was.
 */
internal object TranscriptExpansionState {
    private val expandedIdsByChat = mutableStateMapOf<String, Set<String>>()

    fun isExpanded(chatId: String?, id: String): Boolean = id in expandedIds(chatId)

    fun expandedIds(chatId: String?): Set<String> = expandedIdsByChat[chatId.orEmpty()].orEmpty()

    /** Drops a conversation's sections, so deleting a chat does not leave its ids behind. */
    fun clear(chatId: String?) {
        expandedIdsByChat.remove(chatId.orEmpty())
    }

    fun setExpanded(chatId: String?, id: String, expanded: Boolean) {
        val chat = chatId.orEmpty()
        val current = expandedIdsByChat[chat].orEmpty()
        val updated = if (expanded) current + id else current - id
        if (updated != current) expandedIdsByChat[chat] = updated
    }

    fun expand(chatId: String?, id: String) = setExpanded(chatId, id, true)

    fun toggle(chatId: String?, id: String) = setExpanded(chatId, id, !isExpanded(chatId, id))
}
