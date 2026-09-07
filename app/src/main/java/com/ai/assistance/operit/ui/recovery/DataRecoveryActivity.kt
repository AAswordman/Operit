package com.ai.assistance.operit.ui.recovery

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.OperitUtilityTheme
import com.ai.assistance.operit.util.LocaleUtils

class DataRecoveryActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.getLocalizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OperitUtilityTheme {
                DataRecoveryScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DataRecoveryScreen() {
    val context = LocalContext.current
    val viewModel: DataRecoveryViewModel =
        viewModel(factory = DataRecoveryViewModel.Factory(context))
    val state by viewModel.state.collectAsState()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val snapshotPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pendingRestoreUri = uri
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_activity_data_recovery)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            item {
                StatusPanel(
                    isRunning = state.isRunning,
                    status = state.status,
                    error = state.error,
                    affectedRows = state.affectedRows
                )
            }

            item {
                RecoverySection(title = stringResource(R.string.data_recovery_raw_snapshot_section)) {
                    Text(
                        text = stringResource(R.string.data_recovery_raw_snapshot_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.exportRawSnapshot() },
                            enabled = !state.isRunning
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.data_recovery_export_snapshot))
                        }
                        OutlinedButton(
                            onClick = { snapshotPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                            enabled = !state.isRunning
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.data_recovery_import_snapshot))
                        }
                    }
                    state.lastSnapshotPath?.let { path ->
                        Spacer(modifier = Modifier.height(10.dp))
                        SelectionContainer {
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                    if (state.restoreCompleted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(onClick = { restartMainApp(context) }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.data_recovery_start_main_app))
                        }
                    }
                }
            }

            item {
                RecoverySection(title = stringResource(R.string.data_recovery_file_management)) {
                    val authority = "${context.packageName}.documents.data"
                    Text(
                        text = stringResource(
                            R.string.data_recovery_file_management_description,
                            authority
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                RecoverySection(title = stringResource(R.string.data_recovery_sql_executor)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { viewModel.setSqlText(DataRecoveryViewModel.SAFE_MESSAGES_QUERY) },
                            label = { Text(stringResource(R.string.data_recovery_messages_size)) },
                            leadingIcon = {
                                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        AssistChip(
                            onClick = { viewModel.setSqlText(DataRecoveryViewModel.SAFE_VARIANTS_QUERY) },
                            label = { Text(stringResource(R.string.data_recovery_variants_size)) },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        AssistChip(
                            onClick = { viewModel.setSqlText(DataRecoveryViewModel.SAFE_CHATS_QUERY) },
                            label = { Text(stringResource(R.string.data_recovery_chats_fields)) },
                            leadingIcon = {
                                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.sqlText,
                        onValueChange = viewModel::setSqlText,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        label = { Text(stringResource(R.string.data_recovery_sql_label)) },
                        minLines = 4,
                        maxLines = 8
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.runSql() },
                        enabled = !state.isRunning
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.data_recovery_execute))
                    }
                }
            }

            state.queryResult?.let { result ->
                item {
                    QueryResultPanel(result)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.data_recovery_import_snapshot_title)) },
            text = { Text(stringResource(R.string.data_recovery_import_snapshot_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        viewModel.restoreRawSnapshot(uri)
                    }
                ) {
                    Text(stringResource(R.string.data_recovery_import_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text(stringResource(R.string.data_recovery_cancel_action))
                }
            }
        )
    }
}

@Composable
private fun StatusPanel(
    isRunning: Boolean,
    status: String?,
    error: String?,
    affectedRows: Int?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Column {
                Text(
                    text = error ?: status ?: stringResource(R.string.data_recovery_started),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                )
                affectedRows?.let {
                    Text(
                        text = stringResource(R.string.data_recovery_affected_rows, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoverySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun QueryResultPanel(result: DataRecoveryViewModel.QueryResult) {
    RecoverySection(title = stringResource(R.string.data_recovery_query_result, result.rows.size)) {
        val horizontalScrollState = rememberScrollState()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .horizontalScroll(horizontalScrollState)
        ) {
            Column {
                ResultRow(values = result.columns, header = true)
                HorizontalDivider()
                result.rows.forEach { row ->
                    ResultRow(values = row, header = false)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ResultRow(values: List<String>, header: Boolean) {
    Row(
        modifier =
            Modifier
                .background(
                    if (header) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
                .padding(vertical = 6.dp)
    ) {
        values.forEach { value ->
            SelectionContainer {
                Text(
                    text = value,
                    modifier = Modifier.width(180.dp).padding(horizontal = 8.dp),
                    style =
                        if (header) {
                            MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
                        } else {
                            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        }
                )
            }
        }
    }
}

private fun restartMainApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (intent == null) {
        Toast.makeText(context, context.getString(R.string.data_recovery_launch_failed), Toast.LENGTH_LONG).show()
        return
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    (context as? Activity)?.finishAffinity()
    Process.killProcess(Process.myPid())
}
