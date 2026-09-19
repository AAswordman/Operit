package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

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
import androidx.compose.material3.Text
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
import com.ai.assistance.operit.ui.components.CustomScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val viewModel: LogcatViewModel = viewModel(factory = LogcatViewModel.Factory(context))
    val isSaving by viewModel.isSaving.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val exportOptions by viewModel.exportOptions.collectAsState()

    CustomScaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.logcat_export_title)) })
        },
        snackbarHost = {
            saveResult?.let {
                Snackbar(modifier = Modifier.padding(16.dp)) {
                    Text(it)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.logcat_management),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.logcat_description),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.saveLogsToFile() },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.logcat_save_to_file))
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearLogs() },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.logcat_clear_all))
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.logcat_export_filters_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.logcat_export_filters_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
            }
        }
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
