package com.ai.assistance.operit.ui.features.management.settings.toolpermissions

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.main.LocalTopBarTitleContent
import com.ai.assistance.operit.ui.main.TopBarTitleContent
import com.ai.assistance.operit.ui.main.components.LocalAppBarContentColor
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.permissions.ToolPermissionChange
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ToolPermissionPage() {
    val context = LocalContext.current
    val handler = remember { AIToolHandler.getInstance(context) }
    val system = remember { ToolPermissionSystem.getInstance(context) }
    val settings by system.settingsFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var lastChange by remember { mutableStateOf<ToolPermissionChange?>(null) }
    var saving by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<String?>(null) }
    var batch by remember { mutableStateOf(false) }
    var batchDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<PermissionLevel?>(null) }
    val isCurrent = LocalIsCurrentScreen.current
    val setTitle = LocalTopBarTitleContent.current
    val appBarColor = LocalAppBarContentColor.current

    // 标题旁只保留使用说明入口；外层统一管理返回键、页面标题的生命周期。
    LaunchedEffect(isCurrent, appBarColor) {
        if (isCurrent)
            setTitle(
                TopBarTitleContent {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.tool_permissions_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = appBarColor,
                        )
                        IconButton(onClick = { help = true }) {
                            Icon(
                                Icons.Outlined.HelpOutline,
                                stringResource(R.string.tool_permission_instruction_title),
                                tint = appBarColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            )
    }
    BackHandler(enabled = batch && !add && edit == null && !batchDialog && !help) {
        batch = false
        selected = emptySet()
    }

    fun applyChanges(
        master: PermissionLevel? = null,
        changes: Map<String, PermissionLevel?> = emptyMap(),
    ) {
        if (saving) return
        val current = settings ?: return
        if (
            (master == null || master == current.master) &&
                changes.none { (name, level) -> current.overrides[name] != level }
        )
            return
        saving = true
        scope.launch {
            try {
                val previous = system.updateSettings(master, changes)
                if (!previous.isEmpty) {
                    lastChange = previous
                    OKToast.success(context, R.string.tp_saved).show()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ToolPermissions", "保存工具权限设置失败", error)
                OKToast.error(context, R.string.tp_save_failed).show()
            } finally {
                saving = false
            }
        }
    }
    fun undoLastChange() {
        if (saving) return
        val previous = lastChange ?: return
        saving = true
        scope.launch {
            try {
                system.updateSettings(previous.master, previous.overrides)
                lastChange = null
                OKToast.success(context, R.string.tp_restored).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ToolPermissions", "撤销工具权限设置失败", error)
                OKToast.error(context, R.string.tp_save_failed).show()
            } finally {
                saving = false
            }
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        val current = settings
        if (current == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            val tools =
                remember(current.overrides.keys, isCurrent) {
                    (handler.getAllToolNames().filterNot {
                            it in setOf("package_proxy", "proxy", "search")
                        } + current.overrides.keys)
                        .distinct()
                        .sorted()
                }
            val descriptions =
                remember(tools) { tools.associateWith { handler.getToolDescription(it) } }
            val visible =
                current.overrides.keys.sorted().filter { name ->
                    (filter == null || current.overrides[name] == filter) &&
                        (name.contains(query, true) ||
                            descriptions.getValue(name).contains(query, true))
                }
            LaunchedEffect(current.overrides.keys) {
                selected = selected.intersect(current.overrides.keys)
            }
            LazyColumn(
                Modifier.fillMaxSize().imePadding(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = if (batch) 170.dp else 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.tp_default_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.tp_default_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PermissionChoice(
                                current.master,
                                enabled = !saving,
                                onSelect = { applyChanges(master = it) },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.tp_rules_count, current.overrides.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = !saving && (batch || current.overrides.isNotEmpty()),
                            onClick = {
                                batch = !batch
                                selected = emptySet()
                            },
                        ) {
                            Text(stringResource(if (batch) R.string.done else R.string.tp_batch))
                        }
                        FilledTonalIconButton(
                            enabled = !saving,
                            onClick = { add = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                stringResource(R.string.add_tool),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.tp_rules_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lastChange != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(enabled = !saving, onClick = ::undoLastChange) {
                                Text(stringResource(R.string.tp_undo_last))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    PermissionSearch(query, onQuery = { query = it })
                    PermissionFilters(filter, current.overrides, onSelect = { filter = it })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.tp_results_count, visible.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (batch)
                            TextButton(
                                enabled = !saving && visible.isNotEmpty(),
                                onClick = {
                                    selected =
                                        if (selected.containsAll(visible))
                                            selected - visible.toSet()
                                        else selected + visible
                                },
                            ) {
                                Text(stringResource(R.string.tp_select_visible))
                            }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (visible.isEmpty())
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    stringResource(
                                        when {
                                            current.overrides.isEmpty() -> R.string.tp_empty_rules
                                            query.isNotBlank() -> R.string.tp_no_matches
                                            else -> R.string.tp_empty_filter
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(enabled = !saving, onClick = { add = true }) {
                                    Text(stringResource(R.string.add_tool))
                                }
                            }
                        }
                    }
                items(visible, key = { it }) { name ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 2.dp),
                    ) {
                        PermissionToolRow(
                            name,
                            descriptions.getValue(name),
                            current.overrides[name],
                            current.master,
                            selected = if (batch) name in selected else null,
                            enabled = !saving,
                            onClick = {
                                if (batch)
                                    selected =
                                        if (name in selected) selected - name else selected + name
                                else edit = name
                            },
                        )
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (batch)
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.tp_selected_count, selected.size),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            TextButton(
                                enabled = !saving && selected.isNotEmpty(),
                                onClick = {
                                    applyChanges(changes = selected.associateWith { null })
                                    selected = emptySet()
                                },
                            ) {
                                Text(stringResource(R.string.tp_follow_default))
                            }
                            Button(
                                enabled = !saving && selected.isNotEmpty(),
                                onClick = { batchDialog = true },
                            ) {
                                Text(stringResource(R.string.tp_change))
                            }
                        }
                    }
            }
            edit?.let { name ->
                PermissionRuleDialog(
                    name,
                    current.overrides[name],
                    current.master,
                    onDismiss = { edit = null },
                    onSave = { level ->
                        edit = null
                        applyChanges(changes = mapOf(name to level))
                    },
                )
            }
            if (batchDialog)
                PermissionRuleDialog(
                    stringResource(R.string.tp_batch_title, selected.size),
                    null,
                    current.master,
                    allowUnchanged = true,
                    onDismiss = { batchDialog = false },
                    onSave = { level ->
                        batchDialog = false
                        applyChanges(changes = selected.associateWith { level })
                        selected = emptySet()
                    },
                )
            if (add)
                AddPermissionSheet(
                    tools,
                    descriptions,
                    current.overrides,
                    current.master,
                    onDismiss = { add = false },
                    onApply = { names, level ->
                        add = false
                        applyChanges(changes = names.associateWith { level })
                    },
                )
        }
        if (help)
            AlertDialog(
                onDismissRequest = { help = false },
                title = { Text(stringResource(R.string.tool_permission_instruction_title)) },
                text = {
                    Text(
                        stringResource(R.string.tp_help),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { help = false }) { Text(stringResource(R.string.done)) }
                },
            )
    }
}
