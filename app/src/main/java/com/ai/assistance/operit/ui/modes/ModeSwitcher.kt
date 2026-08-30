package com.ai.assistance.operit.ui.modes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ModeSwitcher — manages the 4 UI modes and shared session state.
 *
 * Per PROJECT_PLAN.md §5:
 * - 4 modes: Chat, IDE, CLI, Image-gen
 * - Shared session/context across all modes
 * - Mode switching preserves session state
 *
 * The existing Operit app has Chat mode. This adds the abstraction for IDE, CLI, and Image-gen.
 */
class ModeSwitcher {

    private val _currentMode = MutableStateFlow(AppMode.CHAT)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /**
     * Switch to a different mode.
     * Preserves session state across switches.
     */
    fun switchMode(mode: AppMode) {
        if (_currentMode.value != mode) {
            _currentMode.value = mode
        }
    }

    /**
     * Get the current mode.
     */
    fun getCurrentMode(): AppMode = _currentMode.value

    /**
     * Update session state (shared across all modes).
     */
    fun updateSession(update: (SessionState) -> SessionState) {
        _sessionState.value = update(_sessionState.value)
    }

    /**
     * Get current session state.
     */
    fun getSession(): SessionState = _sessionState.value

    /**
     * Reset session state.
     */
    fun resetSession() {
        _sessionState.value = SessionState()
        _currentMode.value = AppMode.CHAT
    }
}

/**
 * Available application modes.
 */
enum class AppMode(val displayName: String, val icon: String) {
    CHAT("Chat", "💬"),
    IDE("IDE", "📝"),
    CLI("CLI", "⌨️"),
    IMAGE_GEN("Image Gen", "🎨")
}

/**
 * Shared session state across all modes.
 * Contains context that persists when switching between modes.
 */
data class SessionState(
    val sessionId: String = "",
    val messageCount: Int = 0,
    val totalTokensUsed: Long = 0,
    val currentProvider: String = "",
    val currentModel: String = "",
    val thinkingEnabled: Boolean = false,
    val activeMode: AppMode = AppMode.CHAT
)
