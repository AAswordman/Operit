package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.theme.ThemePreset
import com.ai.assistance.operit.ui.theme.themePresets

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeSettingsPresetsSection(
    cardColors: CardColors,
    preferencesManager: UserPreferencesManager,
    saveThemeSettingsWithCharacterCard: SaveThemeSettingsAction,
    primaryColorInput: Int,
    secondaryColorInput: Int,
) {
    ThemeSettingsSectionTitle(
        title = "主题预设",
        icon = Icons.Default.Palette,
    )

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "快速切换预定义配色方案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                themePresets.forEach { preset ->
                    ThemePresetChip(
                        preset = preset,
                        isSelected = primaryColorInput == preset.primaryArgb
                                && secondaryColorInput == preset.secondaryArgb,
                        onClick = {
                            saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    useCustomColors = true,
                                    customPrimaryColor = preset.primaryArgb,
                                    customSecondaryColor = preset.secondaryArgb,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePresetChip(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(preset.name) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(preset.primaryArgb)),
            )
        },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        ),
    )
}
