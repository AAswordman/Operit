package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.ChatStyleOption

@Composable
internal fun ThemeSettingsInputTab(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
) {
    val editorSession = shared.editorSession
    val values by editorSession.values.collectAsState()
    val inputStyle = values.requiredString("input_style")
    val chatInputTransparent = values.requiredBoolean("chat_input_transparent")
    val chatInputFloating = values.requiredBoolean("chat_input_floating")
    val chatInputLiquidGlass = values.requiredBoolean("chat_input_liquid_glass")
    val chatInputWaterGlass = values.requiredBoolean("chat_input_water_glass")

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.input_style_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(id = R.string.input_style_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatStyleOption(
                    title = stringResource(id = R.string.input_style_classic),
                    selected = inputStyle == UserPreferencesManager.INPUT_STYLE_CLASSIC,
                    modifier = Modifier.weight(1f),
                ) {
                    editorSession.setString(
                        "input_style",
                        UserPreferencesManager.INPUT_STYLE_CLASSIC,
                    )
                }

                ChatStyleOption(
                    title = stringResource(id = R.string.input_style_agent),
                    selected = inputStyle == UserPreferencesManager.INPUT_STYLE_AGENT,
                    modifier = Modifier.weight(1f),
                ) {
                    editorSession.setString(
                        "input_style",
                        UserPreferencesManager.INPUT_STYLE_AGENT,
                    )
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.theme_chat_input_transparent_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ThemeSettingsInputSwitch(
                title = stringResource(id = R.string.theme_chat_input_transparent),
                description = stringResource(id = R.string.theme_chat_input_transparent_desc),
                checked = chatInputTransparent,
                onCheckedChange = { editorSession.setBoolean("chat_input_transparent", it) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ThemeSettingsInputSwitch(
                title = stringResource(id = R.string.theme_chat_input_floating),
                description = stringResource(id = R.string.theme_chat_input_floating_desc),
                checked = chatInputFloating,
                onCheckedChange = { editorSession.setBoolean("chat_input_floating", it) },
            )

            if (chatInputTransparent) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ThemeSettingsInputSwitch(
                    title = stringResource(id = R.string.theme_chat_input_liquid_glass),
                    description = stringResource(id = R.string.theme_chat_input_liquid_glass_desc),
                    checked = chatInputLiquidGlass,
                    onCheckedChange = { editorSession.setBoolean("chat_input_liquid_glass", it) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ThemeSettingsInputSwitch(
                    title = stringResource(id = R.string.theme_chat_input_water_glass),
                    description = stringResource(id = R.string.theme_chat_input_water_glass_desc),
                    checked = chatInputWaterGlass,
                    onCheckedChange = { editorSession.setBoolean("chat_input_water_glass", it) },
                )
            }
        }
    }
}

@Composable
private fun ThemeSettingsInputSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
