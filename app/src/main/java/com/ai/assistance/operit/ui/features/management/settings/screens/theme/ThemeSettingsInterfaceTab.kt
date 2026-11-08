package com.ai.assistance.operit.ui.features.management.settings.screens.theme

import androidx.compose.material3.CardColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ai.assistance.operit.ui.features.management.settings.sections.ThemeSettingsSidebarSection
import com.ai.assistance.operit.ui.features.management.settings.sections.glass.GlassSettingsSection

@Composable
internal fun ThemeSettingsInterfaceTab(shared: ThemeSettingsShared, cardColors: CardColors) {
    val values by shared.editorSession.values.collectAsState()
    ThemeSettingsInputTab(shared = shared, cardColors = cardColors)
    ThemeSettingsSidebarSection(
        cardColors = cardColors,
        editorSession = shared.editorSession,
        waterGlass = values.requiredBoolean("navigation_drawer_water_glass"),
        buttonGlass = values.requiredBoolean("navigation_drawer_button_liquid_glass"),
    )
    GlassSettingsSection(editorSession = shared.editorSession, cardColors = cardColors)
}
