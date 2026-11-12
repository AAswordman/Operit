package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.theme.applyFontFamilyToTypography
import com.ai.assistance.operit.ui.theme.resolveConfiguredFontFamily
import kotlinx.coroutines.flow.combine

internal data class BubbleAiLayoutSettings(
    val avatar: Boolean, val wide: Boolean, val thinking: Boolean, val status: Boolean,
)
internal data class BubbleAiFontSettings(
    val custom: Boolean, val type: String, val system: String, val path: String?,
)
internal data class BubbleAiIdentitySettings(
    val shape: String, val radius: Float, val provider: Boolean, val model: Boolean, val role: Boolean,
)
internal data class BubbleAiRenderSettings(
    val layout: BubbleAiLayoutSettings,
    val identity: BubbleAiIdentitySettings,
    val collapse: Boolean,
    val toolMode: ToolCollapseMode,
    val typography: Typography,
)
private data class BubbleAiPreferences(
    val layout: BubbleAiLayoutSettings,
    val font: BubbleAiFontSettings,
    val identity: BubbleAiIdentitySettings,
    val collapse: Boolean,
    val toolMode: ToolCollapseMode,
)

internal val LocalBubbleAiRenderSettings = staticCompositionLocalOf<BubbleAiRenderSettings?> { null }

/** One complete snapshot prevents newly visible blocks from laying out with default settings. */
@Composable
internal fun rememberBubbleAiRenderSettings(): BubbleAiRenderSettings? {
    val context = LocalContext.current
    val p = com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot.current
    val d = remember(context) { DisplayPreferencesManager.getInstance(context) }
    val collapse by d.collapseCompletedProcess.collectAsState(initial = true)
    val toolMode by d.toolCollapseMode.collectAsState(initial = ToolCollapseMode.ALL)
    val value = BubbleAiPreferences(
        BubbleAiLayoutSettings(p.bubbleShowAvatar, p.bubbleWideLayoutEnabled, p.showThinkingProcess, p.showStatusTags),
        BubbleAiFontSettings(p.bubbleAiUseCustomFont, p.bubbleAiFontType, p.bubbleAiSystemFontName, p.bubbleAiCustomFontPath),
        BubbleAiIdentitySettings(p.avatarShape, p.avatarCornerRadius, p.showModelProvider, p.showModelName, p.showRoleName),
        collapse, toolMode,
    )
    val baseTypography = MaterialTheme.typography
    val typography = remember(context, value.font, baseTypography) {
        applyFontFamilyToTypography(
            baseTypography,
            resolveConfiguredFontFamily(context, value.font.custom, value.font.type,
                value.font.system, value.font.path),
        )
    }
    return remember(value, typography) {
        BubbleAiRenderSettings(value.layout, value.identity, value.collapse, value.toolMode, typography)
    }
}
