package com.ai.assistance.operit.ui.features.chat.viewmodel

/**
 * State of the explicit "AI summary title" action triggered from the chat history long-press menu.
 *
 * A chat without an entry in the state map is idle. [Failed.message] is already user-facing text so
 * the menu can render it without touching resources. Success needs no state: the new title shows up
 * in the list and in the menu header, and a toast reports it.
 */
sealed interface AiTitleGenerationState {
    data object Running : AiTitleGenerationState

    data class Failed(val message: String) : AiTitleGenerationState
}
