package com.ai.assistance.operit.ui.features.management.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.CardColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.*
import com.ai.assistance.operit.ui.features.management.settings.sections.glass.GlassParametersEntry
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDivider
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSwitchRow

@Composable
internal fun ThemeSettingsSidebarSection(
    cardColors: CardColors,
    editorSession: ThemeEditorSession,
    waterGlass: Boolean,
    buttonGlass: Boolean,
) {
    ThemeWorkbenchCard(
        stringResource(R.string.theme_navigation_drawer_title),
        icon = Icons.Default.ViewSidebar,
    ) {
        ThemeWorkbenchSwitchRow(
            stringResource(R.string.theme_navigation_drawer_water_glass),
            checked = waterGlass,
            enabled = isWaterGlassSupported(),
        ) {
            editorSession.setBoolean("navigation_drawer_water_glass", it)
        }
        ThemeWorkbenchDivider()
        ThemeWorkbenchSwitchRow(
            stringResource(R.string.theme_navigation_drawer_button_liquid_glass),
            checked = buttonGlass,
            enabled = isLiquidGlassSupported(),
        ) {
            editorSession.setBoolean("navigation_drawer_button_liquid_glass", it)
        }
    }
    ThemeWorkbenchCard(
        stringResource(R.string.theme_workbench_frosted_parameters),
        icon = Icons.Default.BlurOn,
    ) {
        GlassParametersEntry(
            editorSession,
            GlassRegion.SIDEBAR,
            stringResource(R.string.theme_glass_sidebar_parameters),
        )
        ThemeWorkbenchDivider()
        GlassParametersEntry(
            editorSession,
            GlassRegion.SIDEBAR_BUTTONS,
            stringResource(R.string.theme_glass_sidebar_button_parameters),
        )
    }
}
