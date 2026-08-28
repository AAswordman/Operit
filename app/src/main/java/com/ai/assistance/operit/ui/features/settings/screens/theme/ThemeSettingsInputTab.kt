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
import com.ai.assistance.operit.ui.features.settings.components.ChatStyleOption

@Composable
internal fun ThemeSettingsInputTab(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
) {
    val editorSession = shared.editorSession
    val values by editorSession.values.collectAsState()
    val inputStyleDefinition = NativeThemeEditorDefinitionV1.inputStyle
    val inputAppearanceDefinition = NativeThemeEditorDefinitionV1.inputAppearance
    val inputStyle = values.requiredString(inputStyleDefinition.field)
    val visibleAppearanceControls =
        inputAppearanceDefinition.controls.filter { control -> control.isVisible(values) }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = inputStyleDefinition.titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(id = inputStyleDefinition.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                inputStyleDefinition.options.forEach { option ->
                    ChatStyleOption(
                        title = stringResource(id = option.titleRes),
                        selected = inputStyle == option.value,
                        modifier = Modifier.weight(1f),
                    ) {
                        editorSession.setString(inputStyleDefinition.field, option.value)
                    }
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = inputAppearanceDefinition.titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            visibleAppearanceControls.forEachIndexed { index, control ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                ThemeSettingsInputSwitch(
                    title = stringResource(id = control.titleRes),
                    description = stringResource(id = control.descriptionRes),
                    checked = values.requiredBoolean(control.field),
                    onCheckedChange = { editorSession.setBoolean(control.field, it) },
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
