package com.ai.assistance.operit.ui.features.management.settings.toolpermissions

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.permissions.PermissionLevel

@Composable
internal fun permissionLabel(level: PermissionLevel): String =
    stringResource(
        when (level) {
            PermissionLevel.ALLOW -> R.string.permission_level_allow
            PermissionLevel.ASK -> R.string.permission_level_ask
            PermissionLevel.FORBID -> R.string.permission_level_forbid
        }
    )

@Composable
private fun permissionColor(level: PermissionLevel): Color =
    when (level) {
        PermissionLevel.ALLOW -> MaterialTheme.colorScheme.primary
        PermissionLevel.ASK -> MaterialTheme.colorScheme.tertiary
        PermissionLevel.FORBID -> MaterialTheme.colorScheme.error
    }

@Composable
internal fun PermissionChoice(
    selected: PermissionLevel,
    enabled: Boolean = true,
    onSelect: (PermissionLevel) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PermissionLevel.entries.forEach { level ->
            val active = level == selected
            Surface(
                modifier =
                    Modifier.weight(1f)
                        .selectable(
                            active,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(level) },
                        ),
                shape = RoundedCornerShape(11.dp),
                color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (active) 1.dp else 0.dp,
            ) {
                Column(
                    Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        permissionLabel(level),
                        color =
                            if (active) permissionColor(level)
                            else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(
                            when (level) {
                                PermissionLevel.ALLOW -> R.string.tp_allow_hint
                                PermissionLevel.ASK -> R.string.tp_ask_hint
                                PermissionLevel.FORBID -> R.string.tp_forbid_hint
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PermissionFilters(
    selected: PermissionLevel?,
    rules: Map<String, PermissionLevel>,
    onSelect: (PermissionLevel?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (listOf<PermissionLevel?>(null) + PermissionLevel.entries).forEach { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelect(level) },
                label = {
                    Text(
                        "${if (level == null) stringResource(R.string.tp_all) else permissionLabel(level)} ${if (level == null) rules.size else rules.count { it.value == level }}"
                    )
                },
            )
        }
    }
}

@Composable
internal fun PermissionSearch(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.tp_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
internal fun PermissionToolRow(
    name: String,
    description: String,
    level: PermissionLevel?,
    master: PermissionLevel,
    selected: Boolean? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selected != null)
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (description.isNotBlank())
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        Surface(
            color = permissionColor(level ?: master).copy(alpha = 0.09f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                if (level == null) stringResource(R.string.tp_follow_default)
                else permissionLabel(level),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = permissionColor(level ?: master),
            )
        }
        if (selected == null)
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
internal fun PermissionRuleDialog(
    title: String,
    initial: PermissionLevel?,
    master: PermissionLevel,
    onDismiss: () -> Unit,
    onSave: (PermissionLevel?) -> Unit,
    allowUnchanged: Boolean = false,
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                (listOf<PermissionLevel?>(null) + PermissionLevel.entries).forEach { level ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(
                                selected == level,
                                role = Role.RadioButton,
                                onClick = { selected = level },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected == level, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (level == null)
                                stringResource(
                                    R.string.tp_follow_default_with_value,
                                    permissionLabel(master),
                                )
                            else permissionLabel(level)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = allowUnchanged || selected != initial,
                onClick = { onSave(selected) },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddPermissionSheet(
    tools: List<String>,
    descriptions: Map<String, String>,
    rules: Map<String, PermissionLevel>,
    master: PermissionLevel,
    onDismiss: () -> Unit,
    onApply: (Set<String>, PermissionLevel) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(PermissionLevel.ALLOW) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selectedOnly by remember { mutableStateOf(false) }
    val visible =
        tools.filter { name ->
            (!selectedOnly || name in selected) &&
                (name.contains(query, true) || descriptions.getValue(name).contains(query, true))
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.add_tool),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            PermissionChoice(target, onSelect = { target = it })
            PermissionSearch(query, onQuery = { query = it })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selectedOnly,
                    onClick = { selectedOnly = !selectedOnly },
                    label = { Text(stringResource(R.string.tp_selected_count, selected.size)) },
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = visible.isNotEmpty(),
                    onClick = {
                        selected =
                            if (selected.containsAll(visible)) selected - visible.toSet()
                            else selected + visible
                    },
                ) {
                    Text(stringResource(R.string.tp_select_visible))
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (visible.isEmpty())
                    item {
                        Text(
                            stringResource(R.string.tp_no_matches),
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                items(visible, key = { it }) { name ->
                    PermissionToolRow(
                        name,
                        descriptions.getValue(name),
                        rules[name],
                        master,
                        selected = name in selected,
                        onClick = {
                            selected = if (name in selected) selected - name else selected + name
                        },
                    )
                    if (rules[name] == target)
                        Text(
                            stringResource(R.string.tp_already_configured),
                            Modifier.padding(start = 60.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel_action))
                }
                Button(
                    enabled = selected.isNotEmpty(),
                    onClick = { onApply(selected, target) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.tp_apply_count, selected.size))
                }
            }
        }
    }
}
