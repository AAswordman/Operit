package com.ai.assistance.operit.core.memory.extraction

import com.ai.assistance.operit.data.model.ChatMessage

internal object ChatMemoryWindowPlanner {

    const val DEFAULT_WINDOW_MESSAGE_COUNT = 32
    const val MIN_WINDOW_MESSAGE_COUNT = 8
    const val MAX_WINDOW_MESSAGE_COUNT = 48

    data class Window(
        val messages: List<ChatMessage>,
        val sourceMessageCount: Int
    )

    fun plan(
        messages: List<ChatMessage>,
        windowMessageCount: Int,
        timeScope: ChatMemoryRebuildTimeScope
    ): List<Window> {
        val boundedWindowSize =
            windowMessageCount.coerceIn(MIN_WINDOW_MESSAGE_COUNT, MAX_WINDOW_MESSAGE_COUNT)
        val scopedMessages =
            when (timeScope) {
                ChatMemoryRebuildTimeScope.EntireChat -> messages
                is ChatMemoryRebuildTimeScope.InclusiveLocalRange ->
                    messages.filter { timeScope.contains(it.timestamp) }
            }
        val windows = mutableListOf<Window>()
        val pendingSourceMessages = mutableListOf<ChatMessage>()
        val pendingContextMessages = mutableListOf<ChatMessage>()
        var currentTurnUser: ChatMessage? = null

        fun emitWindow() {
            if (pendingSourceMessages.isEmpty() || currentTurnUser == null) return
            windows += Window(
                messages = pendingContextMessages.toList() + pendingSourceMessages.toList(),
                sourceMessageCount = pendingSourceMessages.size
            )
            pendingSourceMessages.clear()
            pendingContextMessages.clear()
        }

        fun beginWindow(contextUser: ChatMessage? = null) {
            contextUser?.let(pendingContextMessages::add)
        }

        scopedMessages.sortedBy { it.timestamp }.forEach { message ->
            when (message.sender) {
                "user" -> {
                    if (message.content.isBlank()) return@forEach
                    if (
                        pendingSourceMessages.isNotEmpty() &&
                            pendingSourceMessages.size >= boundedWindowSize - 1
                    ) {
                        emitWindow()
                    }
                    if (pendingSourceMessages.isEmpty()) {
                        beginWindow()
                    }
                    currentTurnUser = message
                    pendingSourceMessages += message
                }

                "ai", "assistant" -> {
                    if (message.content.isBlank()) return@forEach
                    val turnUser = currentTurnUser ?: return@forEach
                    if (pendingSourceMessages.size >= boundedWindowSize) {
                        // A group chat can have more assistant replies than the selected window
                        // size. Repeat only the prompting user turn as context for the next
                        // slice so every request remains bounded and every reply is retained.
                        emitWindow()
                        beginWindow(contextUser = turnUser)
                    }
                    pendingSourceMessages += message
                }
            }
        }
        emitWindow()
        return windows
    }
}
