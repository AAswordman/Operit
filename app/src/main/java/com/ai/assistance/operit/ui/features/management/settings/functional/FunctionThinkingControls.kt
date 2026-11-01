package com.ai.assistance.operit.ui.features.management.settings.functional

import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceMenu
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.model.ThinkingQualityControl
import com.ai.assistance.operit.api.chat.model.ThinkingQualityMappingRegistry
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping

@Composable
internal fun FunctionThinkingControls(
    currentConfig: ModelConfigSummary?,
    currentModelIndex: Int,
    currentMapping: FunctionConfigMapping,
    onThinkingChanged: (Boolean, String) -> Unit,
) {
    val config = currentConfig ?: return
    val modelName = getModelByIndex(config.modelName, currentModelIndex).ifBlank { config.modelName }
    val mapping = ThinkingQualityMappingRegistry.resolve(
        providerTypeId = config.apiProviderTypeId,
        modelName = modelName,
        apiEndpoint = config.apiEndpoint,
        thinkingConfigurations = config.thinkingConfigurations,
    )
    if (mapping.control == ThinkingQualityControl.UNSUPPORTED) return
    val selectedOptionId = mapping.resolveOptionId(currentMapping.thinkingOptionId, config.thinkingOptionId)
    val enabled = currentMapping.enableThinking || mapping.reasoningRequired
    var expanded by remember(config.id, modelName) { mutableStateOf(false) }
    val label = mapping.optionFor(selectedOptionId)?.displayLabel
        ?: selectedOptionId.ifBlank { stringResource(R.string.function_summary_thinking_default_level) }

    Spacer(Modifier.height(12.dp))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Psychology, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.function_thinking_title), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (mapping.control == ThinkingQualityControl.LEVELS && mapping.options.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            enabled = enabled,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.function_summary_thinking_level), Modifier.size(18.dp))
                        }
                        ModelWorkspaceMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            mapping.options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayLabel) },
                                    leadingIcon = { if (option.id == selectedOptionId) Icon(Icons.Default.Check, null) },
                                    onClick = {
                                        expanded = false
                                        onThinkingChanged(true, option.id)
                                    },
                                )
                            }
                        }
                    }
                }
                Switch(
                    checked = enabled,
                    enabled = !mapping.reasoningRequired,
                    onCheckedChange = { onThinkingChanged(it, selectedOptionId) },
                )
            }
            if (mapping.reasoningRequired) {
                Text(
                    stringResource(R.string.function_thinking_required),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}