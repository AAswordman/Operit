package com.ai.assistance.operit.ui.features.management.settings.components.speech

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenshotMonitor
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.WakeWordPreferences

@Composable
fun CompactSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun VoiceAutoAttachGrid(
    items: List<WakeWordPreferences.VoiceAutoAttachItem>,
    onItemsChange: (List<WakeWordPreferences.VoiceAutoAttachItem>) -> Unit
) {
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var createDialogVisible by remember { mutableStateOf(false) }

    val usedTypes = remember(items) { items.map { it.type }.toSet() }
    val missingTypes = remember(usedTypes) {
        WakeWordPreferences.VoiceAutoAttachType.entries.filterNot { usedTypes.contains(it) }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        items(items, key = { it.id }) { item ->
            VoiceAutoAttachTile(
                icon = voiceAutoAttachTypeIcon(item.type),
                title = voiceAutoAttachTypeTitle(item.type),
                description = voiceAutoAttachTypeDesc(item.type),
                enabled = item.enabled,
                keywordPreview = item.keywords,
                onClick = { editingItemId = item.id }
            )
        }

        if (missingTypes.isNotEmpty()) {
            item(key = "add") {
                VoiceAutoAttachAddTile(onClick = { createDialogVisible = true })
            }
        }
    }

    val editingItem = remember(editingItemId, items) {
        editingItemId?.let { id -> items.firstOrNull { it.id == id } }
    }
    if (editingItem != null) {
        VoiceAutoAttachItemDialog(
            item = editingItem,
            onDismiss = { editingItemId = null },
            onDelete = {
                onItemsChange(items.filterNot { it.id == editingItem.id })
                editingItemId = null
            },
            onSave = { updated ->
                val newItems = items.map { if (it.id == updated.id) updated else it }
                onItemsChange(newItems)
                editingItemId = null
            }
        )
    }

    if (createDialogVisible) {
        VoiceAutoAttachCreateDialog(
            availableTypes = missingTypes,
            onDismiss = { createDialogVisible = false },
            onCreate = { newItem ->
                onItemsChange(items + newItem)
                createDialogVisible = false
            }
        )
    }
}

@Composable
private fun VoiceAutoAttachTile(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    keywordPreview: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (keywordPreview.isNotBlank()) {
                Text(
                    text = keywordPreview,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VoiceAutoAttachAddTile(onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(R.string.voice_keyword_attachments_add_title),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VoiceAutoAttachItemDialog(
    item: WakeWordPreferences.VoiceAutoAttachItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (WakeWordPreferences.VoiceAutoAttachItem) -> Unit
) {
    var enabled by remember(item.id) { mutableStateOf(item.enabled) }
    var keywords by remember(item.id) { mutableStateOf(item.keywords) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = voiceAutoAttachTypeTitle(item.type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactSwitchRow(
                    title = stringResource(R.string.voice_keyword_attachments_enabled_title),
                    description = "",
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = keywords,
                    onValueChange = { keywords = it },
                    singleLine = true,
                    label = { Text(text = voiceAutoAttachKeywordLabel(item.type)) },
                    supportingText = { Text(text = stringResource(R.string.voice_keyword_attachments_keyword_supporting)) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(item.copy(enabled = enabled, keywords = keywords))
                }
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.delete))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceAutoAttachCreateDialog(
    availableTypes: List<WakeWordPreferences.VoiceAutoAttachType>,
    onDismiss: () -> Unit,
    onCreate: (WakeWordPreferences.VoiceAutoAttachItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember(availableTypes) { mutableStateOf(availableTypes.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.voice_keyword_attachments_add_item_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        value = selectedType?.let { voiceAutoAttachTypeTitle(it) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.voice_keyword_attachments_type_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableTypes.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(text = voiceAutoAttachTypeTitle(t)) },
                                onClick = {
                                    selectedType = t
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedType != null,
                onClick = {
                    val t = selectedType ?: return@TextButton
                    onCreate(
                        WakeWordPreferences.VoiceAutoAttachItem(
                            id = "item_${System.currentTimeMillis()}",
                            type = t,
                            enabled = true,
                            keywords = ""
                        )
                    )
                }
            ) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun voiceAutoAttachTypeTitle(type: WakeWordPreferences.VoiceAutoAttachType): String {
    return when (type) {
        WakeWordPreferences.VoiceAutoAttachType.SCREEN_OCR -> stringResource(R.string.voice_keyword_attachments_screen_title)
        WakeWordPreferences.VoiceAutoAttachType.NOTIFICATIONS -> stringResource(R.string.voice_keyword_attachments_notifications_title)
        WakeWordPreferences.VoiceAutoAttachType.LOCATION -> stringResource(R.string.voice_keyword_attachments_location_title)
        WakeWordPreferences.VoiceAutoAttachType.TIME -> stringResource(R.string.voice_keyword_attachments_time_title)
    }
}

@Composable
private fun voiceAutoAttachTypeDesc(type: WakeWordPreferences.VoiceAutoAttachType): String {
    return when (type) {
        WakeWordPreferences.VoiceAutoAttachType.SCREEN_OCR -> stringResource(R.string.voice_keyword_attachments_screen_desc)
        WakeWordPreferences.VoiceAutoAttachType.NOTIFICATIONS -> stringResource(R.string.voice_keyword_attachments_notifications_desc)
        WakeWordPreferences.VoiceAutoAttachType.LOCATION -> stringResource(R.string.voice_keyword_attachments_location_desc)
        WakeWordPreferences.VoiceAutoAttachType.TIME -> stringResource(R.string.voice_keyword_attachments_time_desc)
    }
}

@Composable
private fun voiceAutoAttachKeywordLabel(type: WakeWordPreferences.VoiceAutoAttachType): String {
    return when (type) {
        WakeWordPreferences.VoiceAutoAttachType.SCREEN_OCR -> stringResource(R.string.voice_keyword_attachments_screen_keyword_label)
        WakeWordPreferences.VoiceAutoAttachType.NOTIFICATIONS -> stringResource(R.string.voice_keyword_attachments_notifications_keyword_label)
        WakeWordPreferences.VoiceAutoAttachType.LOCATION -> stringResource(R.string.voice_keyword_attachments_location_keyword_label)
        WakeWordPreferences.VoiceAutoAttachType.TIME -> stringResource(R.string.voice_keyword_attachments_time_keyword_label)
    }
}

private fun voiceAutoAttachTypeIcon(type: WakeWordPreferences.VoiceAutoAttachType): ImageVector {
    return when (type) {
        WakeWordPreferences.VoiceAutoAttachType.SCREEN_OCR -> Icons.Filled.ScreenshotMonitor
        WakeWordPreferences.VoiceAutoAttachType.NOTIFICATIONS -> Icons.Filled.Notifications
        WakeWordPreferences.VoiceAutoAttachType.LOCATION -> Icons.Filled.LocationOn
        WakeWordPreferences.VoiceAutoAttachType.TIME -> Icons.Filled.Schedule
    }
}
