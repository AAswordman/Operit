package com.ai.assistance.operit.ui.features.extensions.packages.screens.mcp.loading

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

val LocalMcpLoadingState = staticCompositionLocalOf<McpLoadingState> {
    error("LocalMcpLoadingState not provided")
}

object McpLoadingStateRegistry {
    @Volatile
    private var activeState: McpLoadingState? = null

    @Volatile
    private var activeScope: CoroutineScope? = null

    fun bind(state: McpLoadingState, scope: CoroutineScope) {
        activeState = state
        activeScope = scope
    }

    fun unbind(state: McpLoadingState) {
        if (activeState === state) {
            activeState = null
            activeScope = null
        }
    }

    fun getState(): McpLoadingState? = activeState

    fun getScope(): CoroutineScope? = activeScope
}
