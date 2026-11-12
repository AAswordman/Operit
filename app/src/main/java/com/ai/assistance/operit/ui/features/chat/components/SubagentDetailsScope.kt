package com.ai.assistance.operit.ui.features.chat.components

/** Surfaces without a detail host must not dispatch a dialog into another surface. */
internal val LocalSubagentDetailsEnabled =
    androidx.compose.runtime.staticCompositionLocalOf { true }
