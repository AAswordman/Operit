package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ChatEntity

internal object ChatDeletionGraphPolicy {
    fun selectDeletableChats(
        allChats: List<ChatEntity>,
        scopedChatIds: Set<String>,
    ): List<ChatEntity> {
        val protectedChatIds =
            allChats
                .asSequence()
                .filter { it.locked || it.id !in scopedChatIds }
                .mapTo(hashSetOf()) { it.id }
        var changed: Boolean
        do {
            changed = false
            allChats.forEach { chat ->
                if (chat.id in protectedChatIds) {
                    chat.parentChatId?.let { parentId ->
                        changed = protectedChatIds.add(parentId) || changed
                    }
                } else if (chat.parentChatId in protectedChatIds) {
                    changed = protectedChatIds.add(chat.id) || changed
                }
            }
        } while (changed)

        return allChats.filter { it.id in scopedChatIds && it.id !in protectedChatIds }
    }

    fun orderChildFirst(chats: List<ChatEntity>): List<ChatEntity> {
        val remaining = chats.toMutableList()
        val ordered = mutableListOf<ChatEntity>()
        while (remaining.isNotEmpty()) {
            val parentIds = remaining.mapNotNullTo(hashSetOf()) { it.parentChatId }
            val leaves = remaining.filter { it.id !in parentIds }
            check(leaves.isNotEmpty()) { "Chat parent graph contains a cycle" }
            ordered += leaves
            remaining.removeAll(leaves.toSet())
        }
        return ordered
    }
}
