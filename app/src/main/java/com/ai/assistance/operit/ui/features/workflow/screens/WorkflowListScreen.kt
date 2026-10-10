package com.ai.assistance.operit.ui.features.workflow.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.ExecutionStatus
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.workflow.viewmodel.WorkflowViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkflowListScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: WorkflowViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedWorkflowIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importJson by remember { mutableStateOf("") }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.readWorkflowJson(uri) { content -> importJson = content }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadToolPkgWorkflowTemplates()
    }

    LaunchedEffect(viewModel.workflows, isSelectionMode) {
        if (!isSelectionMode) return@LaunchedEffect
        val existingIds = viewModel.workflows.map { it.id }.toSet()
        selectedWorkflowIds = selectedWorkflowIds.intersect(existingIds)
        if (viewModel.workflows.isEmpty()) {
            isSelectionMode = false
        }
    }

    val selectedCount = selectedWorkflowIds.size

    CustomScaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    snackbarData = data
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                WorkflowListHeader(
                    showMoreMenu = showMoreMenu,
                    onShowMoreMenuChange = { showMoreMenu = it },
                    onImport = {
                        showImportDialog = true
                        showMoreMenu = false
                    },
                    onCreateFromTemplate = {
                        showTemplateDialog = true
                        showMoreMenu = false
                    },
                    onCreate = {
                        showCreateDialog = true
                        showMoreMenu = false
                    },
                    onEnterSelection = {
                        selectedWorkflowIds = emptySet()
                        isSelectionMode = true
                        showMoreMenu = false
                    },
                    hasWorkflows = viewModel.workflows.isNotEmpty()
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                when {
                    viewModel.isLoading && viewModel.workflows.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    viewModel.workflows.isEmpty() -> {
                        EmptyWorkflowState(onCreate = { showCreateDialog = true })
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isSelectionMode) {
                                item {
                                    WorkflowSelectionBar(
                                        selectedCount = selectedCount,
                                        totalCount = viewModel.workflows.size,
                                        onSelectAll = {
                                            selectedWorkflowIds = viewModel.workflows.map { it.id }.toSet()
                                        },
                                        onClearSelection = { selectedWorkflowIds = emptySet() },
                                        onDeleteSelected = { showDeleteSelectedDialog = true }
                                    )
                                }
                            }

                            items(viewModel.workflows, key = { it.id }) { workflow ->
                                val isSelected = workflow.id in selectedWorkflowIds
                                WorkflowCard(
                                    workflow = workflow,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedWorkflowIds = if (isSelected) {
                                                selectedWorkflowIds - workflow.id
                                            } else {
                                                selectedWorkflowIds + workflow.id
                                            }
                                        } else {
                                            onNavigateToDetail(workflow.id)
                                        }
                                    },
                                    onEnabledChange = { enabled ->
                                        viewModel.setWorkflowEnabled(workflow.id, enabled)
                                    },
                                    onSelectionChange = { selected ->
                                        selectedWorkflowIds = if (selected) {
                                            selectedWorkflowIds + workflow.id
                                        } else {
                                            selectedWorkflowIds - workflow.id
                                        }
                                    },
                                    onOpen = { onNavigateToDetail(workflow.id) },
                                    onDuplicate = {
                                        viewModel.duplicateWorkflow(workflow.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            viewModel.error?.let { errorMessage ->
                LaunchedEffect(errorMessage) {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(errorMessage)
                    viewModel.clearError()
                }
            }

            if (showCreateDialog) {
                CreateWorkflowDialog(
                    onDismiss = { showCreateDialog = false },
                    onCreate = { name, description ->
                        viewModel.createWorkflow(name, description) { workflow ->
                            showCreateDialog = false
                            onNavigateToDetail(workflow.id)
                        }
                    }
                )
            }

            if (showImportDialog) {
                ImportWorkflowDialog(
                    jsonText = importJson,
                    onJsonTextChange = { importJson = it },
                    onSelectFile = {
                        importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onDismiss = {
                        showImportDialog = false
                        importJson = ""
                    },
                    onConfirm = {
                        viewModel.importWorkflowJson(importJson) { workflow ->
                            showImportDialog = false
                            importJson = ""
                            onNavigateToDetail(workflow.id)
                        }
                    }
                )
            }

            if (showTemplateDialog) {
                TemplateTypeDialog(
                    onDismiss = { showTemplateDialog = false },
                    toolPkgTemplates = viewModel.toolPkgWorkflowTemplates,
                    onSelectIntentChatBroadcastTemplate = {
                        showTemplateDialog = false
                        viewModel.createIntentChatBroadcastTemplateWorkflow(context) { workflow ->
                            onNavigateToDetail(workflow.id)
                        }
                    },
                    onSelectChatTemplate = {
                        showTemplateDialog = false
                        viewModel.createChatTemplateWorkflow(context) { workflow ->
                            onNavigateToDetail(workflow.id)
                        }
                    },
                    onSelectConditionTemplate = {
                        showTemplateDialog = false
                        viewModel.createConditionTemplateWorkflow(context) { workflow ->
                            onNavigateToDetail(workflow.id)
                        }
                    },
                    onSelectLogicAndTemplate = {
                        showTemplateDialog = false
                        viewModel.createLogicAndTemplateWorkflow(context) { workflow ->
                            onNavigateToDetail(workflow.id)
                        }
                    },
                    onSelectLogicOrTemplate = {
                        showTemplateDialog = false
                        viewModel.createLogicOrTemplateWorkflow(context) { workflow ->
                            onNavigateToDetail(workflow.id)
                        }
                    },
                    onSelectExtractTemplate = {
                        showTemplateDialog = false
                        viewModel.createExtractTemplateWorkflow(context) { workflow ->
                            onNavigateToDetail(workflow.id)
                        }
                    },
                    onSelectErrorBranchTemplate = {
                        showTemplateDialog = false
                        viewModel.createErrorBranchTemplateWorkflow(context) { workflow ->
                            onNavigateToDetail(workflow.id)
                        }
                    },
                    onSelectSpeechTriggerTemplate = {
                        showTemplateDialog = false
                        viewModel.createSpeechTriggerTemplateWorkflow(context) { workflow ->
                            onNavigateToDetail(workflow.id)
                        }
                    },
                    onSelectToolPkgTemplate = { template ->
                        showTemplateDialog = false
                        viewModel.importToolPkgWorkflowTemplate(
                            containerPackageName = template.containerPackageName,
                            templateId = template.templateId
                        ) { workflow ->
                            onNavigateToDetail(workflow.id)
                        }
                    }
                )
            }

            if (showDeleteSelectedDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteSelectedDialog = false },
                    title = { Text(stringResource(R.string.workflow_confirm_delete_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.workflow_confirm_delete_selected_workflows_message,
                                selectedCount
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val idsToDelete = selectedWorkflowIds.toList()
                                showDeleteSelectedDialog = false
                                viewModel.deleteWorkflows(idsToDelete) {
                                    selectedWorkflowIds = emptySet()
                                    isSelectionMode = false
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            enabled = selectedCount > 0
                        ) {
                            Text(stringResource(R.string.workflow_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteSelectedDialog = false }) {
                            Text(stringResource(R.string.workflow_close))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkflowListHeader(
    showMoreMenu: Boolean,
    onShowMoreMenuChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onCreateFromTemplate: () -> Unit,
    onCreate: () -> Unit,
    onEnterSelection: () -> Unit,
    hasWorkflows: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.workflow_list_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.workflow_automation_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onImport) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = stringResource(R.string.workflow_import_title)
                )
            }
            IconButton(onClick = onCreateFromTemplate) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.workflow_create_from_template)
                )
            }
            FilledTonalButton(
                onClick = onCreate,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.workflow_new))
            }
            if (hasWorkflows) {
                Box {
                    IconButton(onClick = { onShowMoreMenuChange(true) }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.menu)
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { onShowMoreMenuChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.multi_select)) },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            },
                            onClick = onEnterSelection
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWorkflowState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.workflow_start_creation),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.workflow_automation_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))
        FilledTonalButton(
            onClick = onCreate,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.workflow_new))
        }
    }
}

@Composable
private fun WorkflowSelectionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.workflow_selected_count, selectedCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            if (selectedCount < totalCount) {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.select_all_current_list))
                }
            }
            if (selectedCount > 0) {
                TextButton(onClick = onClearSelection) {
                    Text(stringResource(R.string.clear_selection))
                }
                TextButton(
                    onClick = onDeleteSelected,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.workflow_delete))
                }
            }
        }
    }
}

@Composable
fun WorkflowCard(
    workflow: Workflow,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChange: (Boolean) -> Unit = {},
    onOpen: () -> Unit = onClick,
    onDuplicate: () -> Unit = {}
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (workflow.enabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (workflow.enabled) {
                                stringResource(R.string.workflow_status_enabled)
                            } else {
                                stringResource(R.string.workflow_disabled)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (workflow.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                if (isSelectionMode) {
                    androidx.compose.material3.Checkbox(
                        checked = isSelected,
                        onCheckedChange = onSelectionChange
                    )
                } else {
                    Switch(
                        checked = workflow.enabled,
                        onCheckedChange = onEnabledChange
                    )
                }
            }

            if (workflow.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = workflow.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            workflow.lastExecutionStatus?.let { status ->
                Spacer(modifier = Modifier.height(12.dp))
                ExecutionStatusBar(
                    status = status,
                    lastExecutionTime = workflow.lastExecutionTime,
                    totalExecutions = workflow.totalExecutions,
                    successRate = if (workflow.totalExecutions > 0) {
                        (workflow.successfulExecutions.toFloat() / workflow.totalExecutions * 100).toInt()
                    } else {
                        0
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.workflow_node_count_format, workflow.nodes.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (workflow.totalExecutions > 0) {
                        Text(
                            text = stringResource(
                                R.string.workflow_execution_count_format,
                                workflow.totalExecutions
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatDate(workflow.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!isSelectionMode) {
                    FilledTonalButton(
                        onClick = onOpen,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(stringResource(R.string.workflow_open))
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.workflow_copy)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExecutionStatusBar(
    status: ExecutionStatus,
    lastExecutionTime: Long?,
    totalExecutions: Int,
    successRate: Int
) {
    val (statusColor, statusIcon, statusText) = when (status) {
        ExecutionStatus.SUCCESS -> Triple(
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.CheckCircle,
            stringResource(R.string.workflow_execution_success)
        )
        ExecutionStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.error,
            Icons.Default.Error,
            stringResource(R.string.workflow_execution_failed)
        )
        ExecutionStatus.RUNNING -> Triple(
            MaterialTheme.colorScheme.primary,
            Icons.Outlined.PlayCircle,
            stringResource(R.string.workflow_execution_running)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(statusColor.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
            lastExecutionTime?.let {
                Text(
                    text = formatDate(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
        if (totalExecutions > 0 && status != ExecutionStatus.RUNNING) {
            Text(
                text = "$successRate%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        }
    }
}

@Composable
private fun ImportWorkflowDialog(
    jsonText: String,
    onJsonTextChange: (String) -> Unit,
    onSelectFile: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_import_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.workflow_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = onJsonTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    placeholder = {
                        Text(stringResource(R.string.workflow_import_json_placeholder))
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                TextButton(onClick = onSelectFile) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.workflow_select_json_file))
                }
                Text(
                    text = stringResource(R.string.workflow_import_disabled_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = jsonText.isNotBlank()) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workflow_close))
            }
        }
    )
}

@Composable
private fun TemplateTypeDialog(
    onDismiss: () -> Unit,
    toolPkgTemplates: List<PackageManager.ToolPkgWorkflowTemplate>,
    onSelectIntentChatBroadcastTemplate: () -> Unit,
    onSelectChatTemplate: () -> Unit,
    onSelectConditionTemplate: () -> Unit,
    onSelectLogicAndTemplate: () -> Unit,
    onSelectLogicOrTemplate: () -> Unit,
    onSelectExtractTemplate: () -> Unit,
    onSelectErrorBranchTemplate: () -> Unit,
    onSelectSpeechTriggerTemplate: () -> Unit,
    onSelectToolPkgTemplate: (PackageManager.ToolPkgWorkflowTemplate) -> Unit
) {
    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_select_template_type)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemplateTypeItem(
                    title = stringResource(R.string.workflow_template_intent_chat_broadcast_title),
                    subtitle = stringResource(R.string.workflow_template_intent_chat_broadcast_desc),
                    onClick = onSelectIntentChatBroadcastTemplate
                )
                TemplateTypeItem(
                    title = stringResource(R.string.workflow_template_chat_title),
                    subtitle = stringResource(R.string.workflow_template_chat_desc),
                    onClick = onSelectChatTemplate
                )
                TemplateTypeItem(
                    title = stringResource(R.string.workflow_template_condition_title),
                    subtitle = stringResource(R.string.workflow_template_condition_desc),
                    onClick = onSelectConditionTemplate
                )
                TemplateTypeItem(
                    title = stringResource(R.string.workflow_template_logic_and_title),
                    subtitle = stringResource(R.string.workflow_template_logic_and_desc),
                    onClick = onSelectLogicAndTemplate
                )
                TemplateTypeItem(
                    title = stringResource(R.string.workflow_template_logic_or_title),
                    subtitle = stringResource(R.string.workflow_template_logic_or_desc),
                    onClick = onSelectLogicOrTemplate
                )
                TemplateTypeItem(
                    title = stringResource(R.string.workflow_template_extract_title),
                    subtitle = stringResource(R.string.workflow_template_extract_desc),
                    onClick = onSelectExtractTemplate
                )
                TemplateTypeItem(
                    title = stringResource(R.string.workflow_template_error_branch_title),
                    subtitle = stringResource(R.string.workflow_template_error_branch_desc),
                    onClick = onSelectErrorBranchTemplate
                )
                TemplateTypeItem(
                    title = stringResource(R.string.workflow_template_speech_trigger_title),
                    subtitle = stringResource(R.string.workflow_template_speech_trigger_desc),
                    onClick = onSelectSpeechTriggerTemplate
                )
                if (toolPkgTemplates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.workflow_template_toolpkg_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    toolPkgTemplates.forEach { template ->
                        val subtitle = buildString {
                            append(template.containerPackageName)
                            if (template.description.isNotBlank()) {
                                append(" · ")
                                append(template.description)
                            }
                        }
                        TemplateTypeItem(
                            title = template.displayName,
                            subtitle = subtitle,
                            onClick = { onSelectToolPkgTemplate(template) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workflow_close))
            }
        }
    )
}

@Composable
private fun TemplateTypeItem(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CreateWorkflowDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_create)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.workflow_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.workflow_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), description.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.workflow_action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workflow_close))
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
