package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.compositionLocalOf

internal enum class ResponseMessageSection { ALL, HEADER, BODY }

/** Keep the original style's identity header mounted outside the process fold. */
internal val LocalResponseMessageSection = compositionLocalOf { ResponseMessageSection.ALL }
