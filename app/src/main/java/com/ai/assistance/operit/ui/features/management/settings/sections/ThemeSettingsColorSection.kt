package com.ai.assistance.operit.ui.features.management.settings.sections

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
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.management.settings.components.ColorSelectionItem
import com.ai.assistance.operit.ui.features.management.settings.components.ThemeModeOption
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.theme.getTextColorForBackground

@Composable
internal fun ThemeSettingsColorCustomizationSection(
    cardColors: CardColors,
    editorSession: ThemeEditorSession,
    useCustomColorsInput: Boolean,
    primaryColorInput: Int,
    secondaryColorInput: Int,
    onColorModeInput: String,
    onShowColorPicker: (String) -> Unit,
) {
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
