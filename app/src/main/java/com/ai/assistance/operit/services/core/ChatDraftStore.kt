package com.ai.assistance.operit.services.core

/** Keeps the non-text parts of the composer with the same chat as its text draft. */
internal class ChatDraftStore<T>(private val empty: T) {
    private var activeChatId: String? = null
    private val drafts = mutableMapOf<String?, T>()
    private val targets = mutableMapOf<String?, Target>()

    internal class Target internal constructor(internal var chatId: String?) {
        internal var valid = true
    }

    @Synchronized
    fun captureTarget(): Target = targets.getOrPut(activeChatId) { Target(activeChatId) }

    /** Returns the active draft; background completions update only their captured owner. */
    @Synchronized
    fun update(target: Target, current: T, transform: (T) -> T): T {
        if (!target.valid) throw kotlinx.coroutines.CancellationException("Chat draft was deleted")
        if (target.chatId == activeChatId) return transform(current)
        val updated = transform(drafts[target.chatId] ?: empty)
        if (updated == empty) drafts.remove(target.chatId) else drafts[target.chatId] = updated
        return current
    }

    @Synchronized
    fun switchTo(chatId: String?, current: T): T {
        val previous = activeChatId
        if (previous == chatId) return current
        if (previous != null) {
            if (current == empty) drafts.remove(previous) else drafts[previous] = current
        }
        activeChatId = chatId
        if (chatId == null) return empty
        if (previous == null) {
            // A draft started before the first chat is created belongs to that new chat.
            targets.remove(null)?.let { target ->
                target.chatId = chatId
                targets[chatId] = target
            }
        }
        return drafts.remove(chatId) ?: if (previous == null) current else empty
    }

    @Synchronized
    fun remove(chatId: String): Boolean {
        drafts.remove(chatId)
        targets.remove(chatId)?.valid = false
        if (activeChatId != chatId) return false
        activeChatId = null
        return true
    }
}
