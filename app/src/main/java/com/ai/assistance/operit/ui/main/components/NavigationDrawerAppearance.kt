package com.ai.assistance.operit.ui.main.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    val defaultTitleColor = MaterialTheme.colorScheme.primary
    return NavigationDrawerAppearance(
        containerColor = MaterialTheme.colorScheme.surface,
        titleColor = defaultTitleColor,
        statusAvailableColor = defaultTitleColor,
        itemColor = MaterialTheme.colorScheme.onSurfaceVariant,
        buttonContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedContentColor = MaterialTheme.colorScheme.primary,
        dividerColor = defaultTitleColor.copy(alpha = 0.42f),
        waterGlassEnabled = false,
        buttonLiquidGlassEnabled = false,
    )
}
