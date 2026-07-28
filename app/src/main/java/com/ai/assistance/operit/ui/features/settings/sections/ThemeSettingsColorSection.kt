package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.ColorSelectionItem
import com.ai.assistance.operit.ui.features.settings.components.ThemeModeOption
import com.ai.assistance.operit.ui.features.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.theme.getTextColorForBackground

internal enum class ThemeSettingsColorContentMode {
    PALETTE,
    INTERFACE,
}


@Composable
internal fun ThemeSettingsColorCustomizationSection(
    cardColors: CardColors,
    editorSession: ThemeEditorSession,
    statusBarHiddenInput: Boolean,
    statusBarTransparentInput: Boolean,
    useCustomStatusBarColorInput: Boolean,
    customStatusBarColorInput: Int,
    toolbarTransparentInput: Boolean,
    useCustomAppBarColorInput: Boolean,
    customAppBarColorInput: Int,
    navigationDrawerWaterGlassInput: Boolean,
    navigationDrawerButtonLiquidGlassInput: Boolean,
    useCustomNavigationDrawerBackgroundColorInput: Boolean,
    navigationDrawerBackgroundColorInput: Int,
    useCustomNavigationDrawerAccentColorInput: Boolean,
    navigationDrawerAccentColorInput: Int,
    chatHeaderTransparentInput: Boolean,
    chatHeaderOverlayModeInput: Boolean,
    forceAppBarContentColorInput: Boolean,
    appBarContentColorModeInput: String,
    chatHeaderHistoryIconColorInput: Int,
    chatHeaderPipIconColorInput: Int,
    useCustomColorsInput: Boolean,
    primaryColorInput: Int,
    secondaryColorInput: Int,
    onColorModeInput: String,
    onShowColorPicker: (String) -> Unit,
    contentMode: ThemeSettingsColorContentMode,
) {
    val showPaletteControls = contentMode == ThemeSettingsColorContentMode.PALETTE
    val showInterfaceControls = contentMode == ThemeSettingsColorContentMode.INTERFACE

    ThemeSettingsSectionTitle(
        title = stringResource(id = R.string.theme_title_color),
        icon = Icons.Default.ColorLens,
    )

    if (showInterfaceControls) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_statusbar_color),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_statusbar_hidden),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.theme_statusbar_hidden_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = statusBarHiddenInput,
                    onCheckedChange = { editorSession.setBoolean("status_bar_hidden", it) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_statusbar_transparent),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (statusBarHiddenInput) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Text(
                        text = stringResource(id = R.string.theme_statusbar_transparent_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (statusBarHiddenInput) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                Switch(
                    checked = statusBarTransparentInput,
                    enabled = !statusBarHiddenInput,
                    onCheckedChange = { editorSession.setBoolean("status_bar_transparent", it) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_use_custom_statusbar_color),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (statusBarTransparentInput || statusBarHiddenInput) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Text(
                        text =
                            stringResource(id = R.string.theme_use_custom_statusbar_color_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (statusBarTransparentInput || statusBarHiddenInput) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                Switch(
                    checked = useCustomStatusBarColorInput,
                    enabled = !statusBarTransparentInput && !statusBarHiddenInput,
                    onCheckedChange = {
                        editorSession.setBoolean("use_custom_status_bar_color", it)
                    },
                )
            }

            if (useCustomStatusBarColorInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ColorSelectionItem(
                    title = stringResource(id = R.string.theme_statusbar_color),
                    color = Color(customStatusBarColorInput),
                    enabled = !statusBarTransparentInput && !statusBarHiddenInput,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!statusBarTransparentInput && !statusBarHiddenInput) {
                            onShowColorPicker("statusBar")
                        }
                    },
                )
            }
        }
    }
    }

    if (showInterfaceControls) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_toolbar_transparent),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_toolbar_transparent_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            stringResource(id = R.string.theme_toolbar_transparent_desc_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = toolbarTransparentInput,
                    onCheckedChange = { editorSession.setBoolean("toolbar_transparent", it) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_use_custom_appbar_color),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            stringResource(id = R.string.theme_use_custom_appbar_color_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = useCustomAppBarColorInput,
                    enabled = !toolbarTransparentInput,
                    onCheckedChange = { editorSession.setBoolean("use_custom_app_bar_color", it) },
                )
            }

            if (useCustomAppBarColorInput && !toolbarTransparentInput) {
                ColorSelectionItem(
                    title = stringResource(id = R.string.theme_appbar_color),
                    color = Color(customAppBarColorInput),
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onShowColorPicker("appBar") },
                )
            }
        }
    }
    }

    if (showInterfaceControls) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_navigation_drawer_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_navigation_drawer_water_glass),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            stringResource(
                                id = R.string.theme_navigation_drawer_water_glass_desc,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = navigationDrawerWaterGlassInput,
                    onCheckedChange = {
                        editorSession.setBoolean("navigation_drawer_water_glass", it)
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_navigation_drawer_button_liquid_glass),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            stringResource(
                                id = R.string.theme_navigation_drawer_button_liquid_glass_desc,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = navigationDrawerButtonLiquidGlassInput,
                    onCheckedChange = {
                        editorSession.setBoolean("navigation_drawer_button_liquid_glass", it)
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            stringResource(
                                id = R.string.theme_use_custom_navigation_drawer_background_color,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            stringResource(
                                id = R.string.theme_use_custom_navigation_drawer_background_color_desc,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = useCustomNavigationDrawerBackgroundColorInput,
                    onCheckedChange = {
                        editorSession.setBoolean(
                            "use_custom_navigation_drawer_background_color",
                            it,
                        )
                    },
                )
            }

            if (useCustomNavigationDrawerBackgroundColorInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ColorSelectionItem(
                    title = stringResource(id = R.string.theme_navigation_drawer_background_color),
                    color = Color(navigationDrawerBackgroundColorInput),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onShowColorPicker("navigationDrawerBackground") },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            stringResource(
                                id = R.string.theme_use_custom_navigation_drawer_accent_color,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            stringResource(
                                id = R.string.theme_use_custom_navigation_drawer_accent_color_desc,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = useCustomNavigationDrawerAccentColorInput,
                    onCheckedChange = {
                        editorSession.setBoolean(
                            "use_custom_navigation_drawer_accent_color",
                            it,
                        )
                    },
                )
            }

            if (useCustomNavigationDrawerAccentColorInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ColorSelectionItem(
                    title = stringResource(id = R.string.theme_navigation_drawer_accent_color),
                    color = Color(navigationDrawerAccentColorInput),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onShowColorPicker("navigationDrawerAccent") },
                )
            }
        }
    }
    }

    if (showInterfaceControls) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_chat_header_transparent_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_chat_header_transparent),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.theme_chat_header_transparent_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = chatHeaderTransparentInput,
                    onCheckedChange = { editorSession.setBoolean("chat_header_transparent", it) },
                )
            }

            if (chatHeaderTransparentInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.theme_chat_header_overlay_mode),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(id = R.string.theme_chat_header_overlay_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = chatHeaderOverlayModeInput,
                        onCheckedChange = {
                            editorSession.setBoolean("chat_header_overlay_mode", it)
                        },
                    )
                }
            }
        }
    }
    }

    if (showInterfaceControls) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_appbar_content_color_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_force_appbar_content_color),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.theme_force_appbar_content_color_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = forceAppBarContentColorInput,
                    onCheckedChange = {
                        editorSession.setBoolean("force_app_bar_content_color_enabled", it)
                    },
                )
            }

            if (forceAppBarContentColorInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(id = R.string.theme_appbar_content_color_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeModeOption(
                        title = stringResource(id = R.string.theme_appbar_content_color_light),
                        selected =
                            appBarContentColorModeInput ==
                                UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editorSession.setString(
                                "app_bar_content_color_mode",
                                UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
                            )
                        },
                    )
                    ThemeModeOption(
                        title = stringResource(id = R.string.theme_appbar_content_color_dark),
                        selected =
                            appBarContentColorModeInput ==
                                UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_DARK,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editorSession.setString(
                                "app_bar_content_color_mode",
                                UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_DARK,
                            )
                        },
                    )
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_chat_header_icons_color_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ColorSelectionItem(
                title = stringResource(id = R.string.theme_chat_header_history_icon_color),
                color = Color(chatHeaderHistoryIconColorInput),
                modifier = Modifier.fillMaxWidth(),
                onClick = { onShowColorPicker("historyIcon") },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ColorSelectionItem(
                title = stringResource(id = R.string.theme_chat_header_pip_icon_color),
                color = Color(chatHeaderPipIconColorInput),
                modifier = Modifier.fillMaxWidth(),
                onClick = { onShowColorPicker("pipIcon") },
            )
        }
    }
    }

    if (showPaletteControls) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_custom_color),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.theme_use_custom_color),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.theme_custom_color_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = useCustomColorsInput,
                    onCheckedChange = { enabled ->
                        editorSession.update { current ->
                            var updated = current.withBoolean("use_custom_colors", enabled)
                            if (enabled) {
                                updated =
                                    updated
                                        .withInt("custom_primary_color", primaryColorInput)
                                        .withInt("custom_secondary_color", secondaryColorInput)
                            }
                            updated
                        }
                    },
                )
            }

            if (useCustomColorsInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(id = R.string.theme_select_color),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ColorSelectionItem(
                        title = stringResource(id = R.string.theme_primary_color),
                        color = Color(primaryColorInput),
                        modifier = Modifier.weight(1f),
                        onClick = { onShowColorPicker("primary") },
                    )

                    ColorSelectionItem(
                        title = stringResource(id = R.string.theme_secondary_color),
                        color = Color(secondaryColorInput),
                        modifier = Modifier.weight(1f),
                        onClick = { onShowColorPicker("secondary") },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(id = R.string.theme_on_color_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeModeOption(
                        title = stringResource(id = R.string.theme_on_color_auto),
                        selected = onColorModeInput == UserPreferencesManager.ON_COLOR_MODE_AUTO,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editorSession.setString(
                                "on_color_mode",
                                UserPreferencesManager.ON_COLOR_MODE_AUTO,
                            )
                        },
                    )
                    ThemeModeOption(
                        title = stringResource(id = R.string.theme_on_color_light),
                        selected = onColorModeInput == UserPreferencesManager.ON_COLOR_MODE_LIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editorSession.setString(
                                "on_color_mode",
                                UserPreferencesManager.ON_COLOR_MODE_LIGHT,
                            )
                        },
                    )
                    ThemeModeOption(
                        title = stringResource(id = R.string.theme_on_color_dark),
                        selected = onColorModeInput == UserPreferencesManager.ON_COLOR_MODE_DARK,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editorSession.setString(
                                "on_color_mode",
                                UserPreferencesManager.ON_COLOR_MODE_DARK,
                            )
                        },
                    )
                }

                Text(
                    text = stringResource(id = R.string.theme_preview),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        val primaryColor = Color(primaryColorInput)
                        val onPrimaryColor = getTextColorForBackground(primaryColor)

                        Surface(
                            modifier =
                                Modifier.weight(1f).height(40.dp).padding(end = 8.dp),
                            color = primaryColor,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(id = R.string.theme_primary_button),
                                    color = onPrimaryColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        val secondaryColor = Color(secondaryColorInput)
                        val onSecondaryColor = getTextColorForBackground(secondaryColor)

                        Surface(
                            modifier = Modifier.weight(1f).height(40.dp),
                            color = secondaryColor,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(id = R.string.theme_secondary_button),
                                    color = onSecondaryColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(id = R.string.theme_contrast_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

            }
        }
    }
}

}
