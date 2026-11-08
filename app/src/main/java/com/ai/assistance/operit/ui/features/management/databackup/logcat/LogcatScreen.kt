package com.ai.assistance.operit.ui.features.management.databackup.logcat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.databackup.components.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupCard
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupDialog
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupRow
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(navController: NavController? = null, header: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: LogcatViewModel = viewModel(factory = LogcatViewModel.Factory(context))
    val isSaving by viewModel.isSaving.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val exportOptions by viewModel.exportOptions.collectAsState()
    val logWritingEnabled by viewModel.logWritingEnabled.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val filterCount = listOf(exportOptions.excludeDebug, exportOptions.excludeSystem, exportOptions.errorContextOnly, exportOptions.hideSensitive, exportOptions.stripTimestamp).count { it }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { header() }
        DataBackupCard {
            Column(Modifier.padding(16.dp)) {
                LogExportFilterToggle(
                    title = stringResource(R.string.logcat_write_enabled),
                    subtitle = stringResource(R.string.logcat_write_enabled_description),
                    checked = logWritingEnabled,
                    enabled = true,
                    onCheckedChange = viewModel::setLogWritingEnabled,
                )
            }
        }
        DataBackupCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DataBackupSectionHeader(stringResource(R.string.data_backup_export_app_logs), stringResource(R.string.data_backup_export_app_logs_desc), Icons.Default.Description)
                Text(stringResource(R.string.data_backup_log_format), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = viewModel::saveLogsToFile, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.data_backup_export_logs))
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            DataBackupRow(
                title = stringResource(R.string.data_backup_log_filters),
                subtitle = if (filterCount == 0) stringResource(R.string.data_backup_filters_none) else stringResource(R.string.data_backup_filters_count, filterCount),
                icon = Icons.Default.FilterAlt,
                onClick = { showFilters = true },
            )
        }
        saveResult?.let { result ->
            Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.data_backup_log_directory), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showClearConfirm = true }, enabled = !isSaving, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.data_backup_clear_logs))
            }
        }
    }
    if (showFilters) {
        DataBackupDialog(
            onDismissRequest = { showFilters = false },
            title = { Text(stringResource(R.string.data_backup_log_filters)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.logcat_export_filters_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        LogExportFilterToggle(
                        title = stringResource(R.string.logcat_filter_exclude_debug),
                        subtitle = stringResource(R.string.logcat_filter_exclude_debug_desc),
                        checked = exportOptions.excludeDebug,
                        enabled = !isSaving,
                        onCheckedChange = { checked ->
                            viewModel.updateExportOptions { it.copy(excludeDebug = checked) }
                        }
                    )
                    LogExportFilterToggle(
                        title = stringResource(R.string.logcat_filter_exclude_system),
                        subtitle = stringResource(R.string.logcat_filter_exclude_system_desc),
                        checked = exportOptions.excludeSystem,
                        enabled = !isSaving,
                        onCheckedChange = { checked ->
                            viewModel.updateExportOptions { it.copy(excludeSystem = checked) }
                        }
                    )
                    LogExportFilterToggle(
                        title = stringResource(R.string.logcat_filter_error_context),
                        subtitle = stringResource(R.string.logcat_filter_error_context_desc),
                        checked = exportOptions.errorContextOnly,
                        enabled = !isSaving,
                        onCheckedChange = { checked ->
                            viewModel.updateExportOptions { it.copy(errorContextOnly = checked) }
                        }
                    )
                    LogExportFilterToggle(
                        title = stringResource(R.string.logcat_filter_hide_sensitive),
                        subtitle = stringResource(R.string.logcat_filter_hide_sensitive_desc),
                        checked = exportOptions.hideSensitive,
                        enabled = !isSaving,
                        onCheckedChange = { checked ->
                            viewModel.updateExportOptions { it.copy(hideSensitive = checked) }
                        }
                    )
                    LogExportFilterToggle(
                        title = stringResource(R.string.logcat_filter_strip_timestamp),
                        subtitle = stringResource(R.string.logcat_filter_strip_timestamp_desc),
                        checked = exportOptions.stripTimestamp,
                        enabled = !isSaving,
                        onCheckedChange = { checked ->
                            viewModel.updateExportOptions { it.copy(stripTimestamp = checked) }
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showFilters = false }) { Text(stringResource(R.string.data_backup_done)) } },
        )
    }
    if (showClearConfirm) {
        DataBackupDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.data_backup_clear_logs)) },
            text = { Text(stringResource(R.string.data_backup_clear_logs_confirm)) },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                Button(onClick = { showClearConfirm = false; viewModel.clearLogs() }, enabled = !isSaving, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.data_backup_clear_logs)) }
            },
        )
    }
}

@Composable
private fun LogExportFilterToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
