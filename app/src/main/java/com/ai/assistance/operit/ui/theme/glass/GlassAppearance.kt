package com.ai.assistance.operit.ui.theme.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported

data class GlassAppearance(
    val blurRadius: Float,
    val edgeWidth: Float,
    val highlightAlpha: Float,
    val shadowRadius: Float,
    val tintOpacity: Float,
    val conversationList: Boolean,
    val settingsCards: Boolean,
    val packageCards: Boolean,
) {
    companion object {
        fun from(
            values: ThemePreferenceValues,
            region: GlassRegion = GlassRegion.GLOBAL,
        ): GlassAppearance {
            val parameters = region.parameters(values)
            val supported = isLiquidGlassSupported()
            return GlassAppearance(
                blurRadius = parameters.getValue("glass_blur_radius").coerceIn(0f, 40f),
                edgeWidth = parameters.getValue("glass_edge_width").coerceIn(0f, 2f),
                highlightAlpha = parameters.getValue("glass_highlight_alpha").coerceIn(0f, 0.8f),
                shadowRadius = parameters.getValue("glass_shadow_radius").coerceIn(0f, 20f),
                tintOpacity = parameters.getValue("glass_tint_opacity").coerceIn(0f, 1f),
                conversationList = supported && values.requiredBoolean("glass_conversation_list"),
                settingsCards = supported && values.requiredBoolean("glass_settings_cards"),
                packageCards = supported && values.requiredBoolean("glass_package_cards"),
            )
        }
    }
}

@Composable
fun rememberGlassAppearance(region: GlassRegion = GlassRegion.GLOBAL): GlassAppearance {
    val values = LocalThemePreferenceSnapshot.current.values
    return remember(values, region) { GlassAppearance.from(values, region) }
}
