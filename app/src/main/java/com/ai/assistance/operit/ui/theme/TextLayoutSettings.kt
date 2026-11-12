package com.ai.assistance.operit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.UserPreferencesManager

/**
 * AI Markdown 文本布局设置。
 *
 * - lineHeightMultiplier：额外行距倍率，1f 为默认值
 * - letterSpacingSp：额外字距，单位 sp
 * - paragraphSpacingDp：段落之间额外增加的间距，单位 dp
 */
data class AiMarkdownTextLayoutSettings(
    val lineHeightMultiplier: Float = 1f,
    val letterSpacingSp: Float = 0f,
    val paragraphSpacingDp: Float = 12f
)

val LocalAiMarkdownTextLayoutSettings = compositionLocalOf {
    AiMarkdownTextLayoutSettings()
}
private val LocalAiMarkdownSettingsProvided = compositionLocalOf { false }

@Composable
fun ProvideAiMarkdownTextLayoutSettings(content: @Composable () -> Unit) {
    if (LocalAiMarkdownSettingsProvided.current) {
        content()
        return
    }
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val lineHeightMultiplier by preferencesManager.aiMarkdownLineHeightMultiplier.collectAsState(initial = 1f)
    val letterSpacing by preferencesManager.aiMarkdownLetterSpacing.collectAsState(initial = 0f)
    val paragraphSpacing by preferencesManager.aiMarkdownParagraphSpacing.collectAsState(initial = 12f)

    val settings = remember(lineHeightMultiplier, letterSpacing, paragraphSpacing) {
        AiMarkdownTextLayoutSettings(
            lineHeightMultiplier = lineHeightMultiplier,
            letterSpacingSp = letterSpacing,
            paragraphSpacingDp = paragraphSpacing
        )
    }

    CompositionLocalProvider(
        LocalAiMarkdownTextLayoutSettings provides settings,
        LocalAiMarkdownSettingsProvided provides true,
    ) {
        content()
    }
}
