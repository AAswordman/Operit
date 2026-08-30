package com.ai.nonoassistance.provider.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.nonoassistance.provider.AIProvider
import com.ai.nonoassistance.provider.ModelInfo
import com.ai.nonoassistance.provider.ModelPricing

/**
 * Model Selector — dropdown for choosing an AI model with pricing info.
 *
 * Features:
 * - Lists all available models from a provider
 * - Shows pricing (input/output per 1M tokens) for each model
 * - Indicates tool calling and thinking support
 * - Searchable (optional)
 *
 * Used in settings UI for model configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    provider: AIProvider?,
    models: List<ModelInfo>,
    pricingMap: Map<String, ModelPricing>,
    selectedModelId: String?,
    onModelSelected: (ModelInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = models.find { it.id == selectedModelId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedModel?.displayName ?: selectedModelId ?: "Select model",
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                val pricing = pricingMap[model.id]
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = model.displayName.ifEmpty { model.id },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            ModelPricingDisplay(
                                pricing = pricing,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            // Show capabilities
                            val capabilities = buildList {
                                if (model.supportsToolCalling) add("Tools")
                                if (model.supportsThinking) add("Thinking")
                            }
                            if (capabilities.isNotEmpty()) {
                                Text(
                                    text = capabilities.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    }
                )
            }

            if (models.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (provider != null) "No models available" else "Select a provider first",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { expanded = false }
                )
            }
        }
    }
}
