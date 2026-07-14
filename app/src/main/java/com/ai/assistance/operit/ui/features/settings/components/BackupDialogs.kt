package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.converter.ChatFormat
import com.ai.assistance.operit.data.converter.ExportFormat
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.model.MemorySpace

@Composable
fun DeleteConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_delete_confirm_title)) },
        text = { Text(stringResource(R.string.backup_delete_confirm_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.backup_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
        }
    )
}

@Composable
fun MemoryImportStrategyDialog(
    onDismiss: () -> Unit,
    onConfirm: (ImportStrategy) -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(ImportStrategy.SKIP) }
    val texts = rememberMemoryImportStrategyTexts()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_memory_import_strategy_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = texts.question,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrategyOption(
                        title = texts.skipTitle,
                        description = texts.skipDesc,
                        selected = selectedStrategy == ImportStrategy.SKIP,
                        onClick = { selectedStrategy = ImportStrategy.SKIP }
                    )

                    StrategyOption(
                        title = texts.updateTitle,
                        description = texts.updateDesc,
                        selected = selectedStrategy == ImportStrategy.UPDATE,
                        onClick = { selectedStrategy = ImportStrategy.UPDATE }
                    )

                    StrategyOption(
                        title = texts.createNewTitle,
                        description = texts.createNewDesc,
                        selected = selectedStrategy == ImportStrategy.CREATE_NEW,
                        onClick = { selectedStrategy = ImportStrategy.CREATE_NEW }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedStrategy) }) {
                Text(stringResource(R.string.backup_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
        }
    )
}

@Composable
fun StrategyOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (selected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProfileSelectionDialog(
    title: String,
    profiles: List<MemorySpace>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onProfileSelected(profile.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProfileId == profile.id)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = if (selectedProfileId == profile.id)
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProfileId == profile.id,
                                onClick = { onProfileSelected(profile.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selectedProfileId == profile.id) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_cancel))
            }
        }
    )
}

@Composable
fun ExportFormatDialog(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_select_export_format)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.backup_select_format_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                item {
                    FormatOption(
                        format = ExportFormat.JSON,
                        title = stringResource(R.string.backup_format_json),
                        description = stringResource(R.string.backup_format_json_desc),
                        selected = selectedFormat == ExportFormat.JSON,
                        onClick = { onFormatSelected(ExportFormat.JSON) }
                    )
                }

                item {
                    FormatOption(
                        format = ExportFormat.MARKDOWN,
                        title = stringResource(R.string.backup_format_markdown),
                        description = stringResource(R.string.backup_format_markdown_desc),
                        selected = selectedFormat == ExportFormat.MARKDOWN,
                        onClick = { onFormatSelected(ExportFormat.MARKDOWN) }
                    )
                }

                item {
                    FormatOption(
                        format = ExportFormat.HTML,
                        title = stringResource(R.string.backup_format_html),
                        description = stringResource(R.string.backup_format_html_desc),
                        selected = selectedFormat == ExportFormat.HTML,
                        onClick = { onFormatSelected(ExportFormat.HTML) }
                    )
                }

                item {
                    FormatOption(
                        format = ExportFormat.TXT,
                        title = stringResource(R.string.backup_format_txt),
                        description = stringResource(R.string.backup_format_txt_desc),
                        selected = selectedFormat == ExportFormat.TXT,
                        onClick = { onFormatSelected(ExportFormat.TXT) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_cancel))
            }
        }
    )
}

@Composable
fun ImportFormatDialog(
    selectedFormat: ChatFormat,
    onFormatSelected: (ChatFormat) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_select_import_format)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.backup_import_format_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                item {
                    ImportFormatOption(
                        format = ChatFormat.OPERIT,
                        title = stringResource(R.string.backup_format_operit),
                        description = stringResource(R.string.backup_format_operit_desc),
                        selected = selectedFormat == ChatFormat.OPERIT,
                        onClick = { onFormatSelected(ChatFormat.OPERIT) }
                    )
                }

                item {
                    ImportFormatOption(
                        format = ChatFormat.CHATGPT,
                        title = stringResource(R.string.backup_format_chatgpt),
                        description = stringResource(R.string.backup_format_chatgpt_desc),
                        selected = selectedFormat == ChatFormat.CHATGPT,
                        onClick = { onFormatSelected(ChatFormat.CHATGPT) }
                    )
                }

                item {
                    ImportFormatOption(
                        format = ChatFormat.CHATBOX,
                        title = stringResource(R.string.backup_format_chatbox),
                        description = stringResource(R.string.backup_format_chatbox_desc),
                        selected = selectedFormat == ChatFormat.CHATBOX,
                        onClick = { onFormatSelected(ChatFormat.CHATBOX) }
                    )
                }

                item {
                    ImportFormatOption(
                        format = ChatFormat.MARKDOWN,
                        title = stringResource(R.string.backup_format_markdown),
                        description = stringResource(R.string.backup_format_markdown_desc),
                        selected = selectedFormat == ChatFormat.MARKDOWN,
                        onClick = { onFormatSelected(ChatFormat.MARKDOWN) }
                    )
                }

                item {
                    ImportFormatOption(
                        format = ChatFormat.GENERIC_JSON,
                        title = stringResource(R.string.backup_format_generic_json),
                        description = stringResource(R.string.backup_format_generic_json_desc),
                        selected = selectedFormat == ChatFormat.GENERIC_JSON,
                        onClick = { onFormatSelected(ChatFormat.GENERIC_JSON) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_cancel))
            }
        }
    )
}

@Composable
fun ImportFormatOption(
    format: ChatFormat,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (selected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FormatOption(
    format: ExportFormat,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (selected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ModelConfigExportWarningDialog(
    exportPath: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "⚠️ " + stringResource(R.string.backup_model_config_warning_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.backup_model_config_warning_contains),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SecurityWarningItem("🔑 " + stringResource(R.string.backup_model_config_warning_api_key))
                    SecurityWarningItem("🌐 " + stringResource(R.string.backup_model_config_warning_api_endpoint))
                    SecurityWarningItem("⚙️ " + stringResource(R.string.backup_model_config_warning_model_params))
                    SecurityWarningItem("🔧 " + stringResource(R.string.backup_model_config_warning_custom_params))
                }

                Spacer(modifier = Modifier.size(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📋 " + stringResource(R.string.backup_model_config_warning_security_tips),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.backup_model_config_warning_tips),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.size(4.dp))

                Text(
                    text = stringResource(R.string.backup_model_config_warning_export_path),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = exportPath,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onDismiss,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.backup_model_config_warning_confirm))
            }
        }
    )
}

@Composable
fun SecurityWarningItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
