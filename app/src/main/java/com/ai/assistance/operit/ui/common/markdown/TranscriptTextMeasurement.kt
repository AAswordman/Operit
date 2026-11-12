package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer

/** Bounded, page-owned cache for short tool labels that repeatedly leave and re-enter composition. */
internal val LocalTranscriptTextMeasurer = staticCompositionLocalOf<TextMeasurer?> { null }

@Composable
internal fun rememberToolLabelTextMeasurer(): TextMeasurer =
    LocalTranscriptTextMeasurer.current ?: rememberTextMeasurer()
