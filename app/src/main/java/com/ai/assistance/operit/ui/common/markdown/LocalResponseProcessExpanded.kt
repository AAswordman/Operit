package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.compositionLocalOf

/** A transcript-level fold can control the final message's process with the same toggle. */
internal val LocalResponseProcessExpanded = compositionLocalOf<Boolean?> { null }
