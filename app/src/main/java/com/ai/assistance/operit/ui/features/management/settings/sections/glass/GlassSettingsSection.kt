package com.ai.assistance.operit.ui.features.management.settings.sections.glass

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.ui.features.management.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.*
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDialog
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDivider
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchRow
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSwitchRow

/** 参数、区域开关和按钮配色共同进入主题编辑会话，保存前不会写入持久配置。 */
@Composable
internal fun GlassSettingsSection(editorSession: ThemeEditorSession, cardColors: CardColors) {
    val values by editorSession.values.collectAsState()
    val supported = isLiquidGlassSupported()
    val scope = rememberCoroutineScope()
    var colorTarget by remember { mutableStateOf<String?>(null) }
    val recentColors by editorSession.recentColorsFlow.collectAsState(initial = emptyList())
    val snapshot = LocalThemePreferenceSnapshot.current
    val colors = MaterialTheme.colorScheme
    val colorRows =
        listOf(
            Triple(
                "custom_navigation_drawer_background_color",
                R.string.glass_sidebar_background,
                colors.surface.toArgb(),
            ),
            Triple(
                "custom_navigation_drawer_accent_color",
                R.string.glass_sidebar_accent,
                colors.surfaceContainerHigh.toArgb(),
            ),
            Triple(
                "package_market_button_color",
                R.string.glass_market_button_color,
                colors.secondaryContainer.toArgb(),
            ),
            Triple(
                "package_add_button_color",
                R.string.glass_add_button_color,
                colors.primaryContainer.toArgb(),
            ),
        )
    val legacyColorFlags =
        mapOf(
            "custom_navigation_drawer_background_color" to
                "use_custom_navigation_drawer_background_color",
            "custom_navigation_drawer_accent_color" to "use_custom_navigation_drawer_accent_color",
        )

    var showGlobalParameters by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    ThemeWorkbenchCard(
        stringResource(R.string.glass_appearance_title),
        icon = Icons.Default.BlurOn,
    ) {
        ThemeWorkbenchRow(
            title = stringResource(R.string.theme_glass_global_parameters),
            value =
                stringResource(R.string.glass_value_dp, values.requiredFloat("glass_blur_radius")),
            subtitle = stringResource(R.string.theme_glass_global_description),
            onClick = { showGlobalParameters = true },
        )
        if (!supported)
            Text(
                stringResource(R.string.theme_glass_requires_android13),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        listOf(
                GlassRegion.CONVERSATION_LIST to "glass_conversation_list",
                GlassRegion.SETTINGS_CARDS to "glass_settings_cards",
                GlassRegion.PACKAGE_CARDS to "glass_package_cards",
            )
            .forEach { (region, key) ->
                ThemeWorkbenchDivider()
                ThemeWorkbenchSwitchRow(
                    stringResource(region.titleRes),
                    checked = values.requiredBoolean(key),
                    enabled = supported,
                    onCheckedChange = { editorSession.setBoolean(key, it) },
                )
                if (values.requiredBoolean(key)) GlassParametersEntry(editorSession, region)
            }
    }
    if (showGlobalParameters)
        GlassParametersDialog(editorSession, GlassRegion.GLOBAL) { showGlobalParameters = false }
    ThemeWorkbenchCard(stringResource(R.string.glass_button_colors), icon = Icons.Default.Palette) {
        ThemeWorkbenchRow(stringResource(R.string.glass_button_colors)) { showColors = true }
    }
    if (showColors)
        ThemeWorkbenchDialog(stringResource(R.string.glass_button_colors), { showColors = false }) {
            Column(Modifier.padding(16.dp)) {
                colorRows.forEachIndexed { index, (key, label, defaultColor) ->
                    if (index != 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(label),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = {
                                editorSession.setInt(key, null)
                                legacyColorFlags[key]?.let { editorSession.setBoolean(it, false) }
                            },
                            enabled = values.int(key) != null,
                        ) {
                            Text(stringResource(R.string.glass_default_color))
                        }
                        TextButton(onClick = { colorTarget = key }) {
                            Box(
                                Modifier.size(22.dp)
                                    .background(Color(values.int(key) ?: defaultColor), CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.glass_choose_color))
                        }
                    }
                }
            }
        }
    colorTarget?.let { key ->
        val row = colorRows.first { it.first == key }
        val selected = values.int(key) ?: row.third
        ColorPickerDialog(
            showColorPicker = true,
            currentColorPickerMode = "primary",
            primaryColorInput = selected,
            secondaryColorInput = selected,
            cursorUserBubbleColorInput = selected,
            bubbleUserBubbleColorInput = selected,
            bubbleAiBubbleColorInput = selected,
            bubbleUserTextColorInput = selected,
            bubbleAiTextColorInput = selected,
            recentColors = recentColors,
            onColorSelected = { primary, _, _, _, _, _, _ ->
                primary?.let { color ->
                    editorSession.setInt(key, color)
                    scope.launch { editorSession.addRecentColor(color) }
                    legacyColorFlags[key]?.let { editorSession.setBoolean(it, true) }
                }
                colorTarget = null
            },
            onDismiss = { colorTarget = null },
            titleOverride = stringResource(row.second),
        )
    }
}
