package com.ai.assistance.operit.ui.main.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.ui.theme.glass.rememberGlassAppearance
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported

data class NavigationDrawerAppearance(
    val containerColor: Color,
    val titleColor: Color,
    val statusAvailableColor: Color,
    val itemColor: Color,
    val buttonContainerColor: Color,
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
    val dividerColor: Color,
    val waterGlassEnabled: Boolean,
    val buttonLiquidGlassEnabled: Boolean,
)

@Composable
fun rememberNavigationDrawerAppearance(): NavigationDrawerAppearance {
    val snapshot = LocalThemePreferenceSnapshot.current
    val colors = MaterialTheme.colorScheme
    // 显式配色优先保留用户选择的原色，并按实际底色匹配文字对比度。
    val customBackground =
        if (snapshot.values.requiredBoolean("use_custom_navigation_drawer_background_color"))
            snapshot.values.int("custom_navigation_drawer_background_color")?.let(::Color)
        else null
    val customAccent =
        if (snapshot.values.requiredBoolean("use_custom_navigation_drawer_accent_color"))
            snapshot.values.int("custom_navigation_drawer_accent_color")?.let(::Color)
        else null
    val glass = rememberGlassAppearance(GlassRegion.SIDEBAR)
    val buttonGlass = rememberGlassAppearance(GlassRegion.SIDEBAR_BUTTONS)
    val waterEnabled = snapshot.navigationDrawerWaterGlass && isWaterGlassSupported()
    val buttonEnabled = snapshot.navigationDrawerButtonLiquidGlass && isLiquidGlassSupported()
    val container = customBackground ?: colors.surface
    val displayedContainer =
        if (waterEnabled) container.copy(alpha = glass.tintOpacity).compositeOver(colors.background)
        else container
    val foreground =
        if (customBackground != null) getTextColorForBackground(displayedContainer)
        else colors.onSurface
    val selected = customAccent ?: colors.surfaceContainerHigh
    val displayedSelected =
        if (buttonEnabled)
            selected.copy(alpha = buttonGlass.tintOpacity).compositeOver(colors.background)
        else selected
    return NavigationDrawerAppearance(
        containerColor = container,
        titleColor = foreground,
        statusAvailableColor = foreground,
        itemColor = foreground,
        buttonContainerColor = customBackground ?: colors.surfaceContainerLow,
        selectedContainerColor = selected,
        selectedContentColor =
            if (customAccent != null) getTextColorForBackground(displayedSelected)
            else colors.onSurface,
        dividerColor = colors.outlineVariant,
        waterGlassEnabled = waterEnabled,
        buttonLiquidGlassEnabled = buttonEnabled,
    )
}
