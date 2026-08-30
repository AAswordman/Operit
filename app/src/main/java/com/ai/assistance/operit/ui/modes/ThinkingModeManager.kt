package com.ai.assistance.operit.ui.modes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ThinkingModeManager — manages thinking/reasoning mode toggle.
 *
 * Per PROJECT_PLAN.md §12:
 * - Thinking mode: displays collapsible "AI is thinking..." block separate from actual answer
 * - Toggle on/off per request
 * - Must handle models that don't support thinking gracefully
 * - Thinking content shown separately from response content
 */
class ThinkingModeManager {

    private val _thinkingEnabled = MutableStateFlow(false)
    val thinkingEnabled: StateFlow<Boolean> = _thinkingEnabled.asStateFlow()

    private val _thinkingContent = MutableStateFlow("")
    val thinkingContent: StateFlow<String> = _thinkingContent.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    /**
     * Toggle thinking mode on/off.
     */
    fun toggle() {
        _thinkingEnabled.value = !_thinkingEnabled.value
    }

    /**
     * Set thinking mode explicitly.
     */
    fun setEnabled(enabled: Boolean) {
        _thinkingEnabled.value = enabled
    }

    /**
     * Check if thinking is currently enabled.
     */
    fun isEnabled(): Boolean = _thinkingEnabled.value

    /**
     * Start thinking phase — called when AI begins reasoning.
     */
    fun startThinking() {
        _isThinking.value = true
        _thinkingContent.value = ""
    }

    /**
     * Update thinking content — called as thinking chunks arrive.
     */
    fun updateThinkingContent(content: String) {
        _thinkingContent.value = content
    }

    /**
     * Append to thinking content — called for streaming thinking.
     */
    fun appendThinkingContent(chunk: String) {
        _thinkingContent.value += chunk
    }

    /**
     * End thinking phase — called when AI starts generating actual response.
     */
    fun endThinking() {
        _isThinking.value = false
    }

    /**
     * Get the current thinking content.
     */
    fun getThinkingContent(): String = _thinkingContent.value

    /**
     * Clear thinking content.
     */
    fun clearThinking() {
        _thinkingContent.value = ""
        _isThinking.value = false
    }

    /**
     * Check if a provider/model supports thinking.
     * Returns true if thinking is supported, false otherwise.
     * Used to gracefully handle models that don't support thinking.
     */
    fun isThinkingSupported(providerId: String, modelId: String): Boolean {
        // Known thinking-capable models
        val thinkingModels = setOf(
            "deepseek-reasoner",
            "o1", "o1-mini", "o1-preview",
            "o3", "o3-mini",
            "gemini-2.5-pro", "gemini-2.5-flash",
            "claude-3-opus", "claude-3.5-sonnet"
        )

        // Known thinking-capable providers (all models)
        val thinkingProviders = setOf(
            "openai", "anthropic"
        )

        return thinkingModels.any { modelId.contains(it, ignoreCase = true) } ||
                thinkingProviders.any { providerId.contains(it, ignoreCase = true) }
    }
}
