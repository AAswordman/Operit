package com.ai.assistance.operit.ui.features.workflow.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.ExecutionStatus
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.workflow.viewmodel.WorkflowViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: WorkflowViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedWorkflowIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(viewModel.workflows, isSelectionMode) {
        if (!isSelectionMode) return@LaunchedEffect
        val existingIds = viewModel.workflows.map { it.id }.toSet()
        selectedWorkflowIds = selectedWorkflowIds.intersect(existingIds)
        if (viewModel.workflows.isEmpty()) {
            isSelectionMode = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadToolPkgWorkflowTemplates()
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
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(
                    visible = isFabMenuExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isSelectionMode) {
                            if (selectedCount > 0) {
                                SpeedDialAction(
                                    text = stringResource(R.string.workflow_delete_selected_with_count, selectedCount),
                                    icon = Icons.Default.Delete,
                                    onClick = {
                                        showDeleteSelectedDialog = true
                                        isFabMenuExpanded = false
                                    },
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            SpeedDialAction(
                                text = stringResource(R.string.exit_multi_select),
                                icon = Icons.Default.CheckCircle,
                                onClick = {
                                    selectedWorkflowIds = emptySet()
                                    isSelectionMode = false
                                    isFabMenuExpanded = false
                                }
                            )
                        } else {
                            SpeedDialAction(
                                text = stringResource(R.string.workflow_create_blank),
                                icon = Icons.Default.Add,
                                onClick = {
                                    showCreateDialog = true
                                    isFabMenuExpanded = false
                                }
                            )
                            SpeedDialAction(
                                text = stringResource(R.string.workflow_create_from_template),
                                icon = Icons.Outlined.PlayCircle,
                                onClick = {
                                    showTemplateDialog = true
                                    isFabMenuExpanded = false
                                }
                            )
                            SpeedDialAction(
                                text = stringResource(R.string.multi_select),
                                icon = Icons.Default.CheckCircle,
                                onClick = {
                                    selectedWorkflowIds = emptySet()
                                    isSelectionMode = true
                                    isFabMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (isFabMenuExpanded) 45f else 0f,
                        label = "fab_icon_rotation"
                    )
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.workflow_create),
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                viewModel.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                viewModel.workflows.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 极简图标
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚡",
                                style = MaterialTheme.typography.displayMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = stringResource(R.string.workflow_start_creation),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.workflow_automation_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        FilledTonalButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.height(48.dp),
                            contentPadding = PaddingValues(horizontal = 28.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.workflow_new),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
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
                                    onClearSelection = {
                                        selectedWorkflowIds = emptySet()
                                    }
                                )
                            }
                        }

                        // 工作流列表
                        items(
                            items = viewModel.workflows,
                            key = { it.id }
                        ) { workflow ->
                            val isSelected = selectedWorkflowIds.contains(workflow.id)
                            WorkflowCard(
                                workflow = workflow,
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
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onSelectionChange = { selected ->
                                    selectedWorkflowIds = if (selected) {
                                        selectedWorkflowIds + workflow.id
                                    } else {
                                        selectedWorkflowIds - workflow.id
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 错误提示
            viewModel.error?.let { error ->
                LaunchedEffect(error) {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(error)
                    viewModel.clearError()
                }
            }

            // 创建工作流对话框
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
                                isFabMenuExpanded = false
                                viewModel.deleteWorkflows(idsToDelete) {
                                    selectedWorkflowIds = emptySet()
                                    isSelectionMode = false
                                }
                            },
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
private fun WorkflowSelectionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
            }
        }
    }
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
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_select_template_type)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                        val subtitle =
                            buildString {
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
private fun TemplateTypeItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpeedDialAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = MaterialTheme.shapes.small,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Icon(icon, contentDescription = text)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowCard(
    workflow: Workflow,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChange: (Boolean) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!workflow.enabled) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.workflow_disabled),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectionChange(it) }
                        )
                    } else {
                        Switch(
                            checked = workflow.enabled,
                            onCheckedChange = onEnabledChange,
                            modifier = Modifier.scale(0.82f)
                        )
                    }
                }
            }
            
            // 描述
            if (workflow.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workflow.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 执行状态信息
            workflow.lastExecutionStatus?.let { status ->
                ExecutionStatusBar(
                    status = status,
                    lastExecutionTime = workflow.lastExecutionTime,
                    totalExecutions = workflow.totalExecutions,
                    successRate = if (workflow.totalExecutions > 0) {
                        (workflow.successfulExecutions.toFloat() / workflow.totalExecutions * 100).toInt()
                    } else 0
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // 底部信息栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 节点数量
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${workflow.nodes.size}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.workflow_node),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    
                    // 执行次数
                    if (workflow.totalExecutions > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "${workflow.totalExecutions}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                
                // 更新时间
                Text(
                    text = formatDate(workflow.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
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
            Icons.Filled.CheckCircle,
            stringResource(R.string.workflow_execution_success)
        )
        ExecutionStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.error,
            Icons.Filled.Error,
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
            .clip(MaterialTheme.shapes.small)
            .background(statusColor.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                statusIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = statusColor
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = statusColor
                )
                lastExecutionTime?.let {
                    Text(
                        text = formatRelativeTime(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        if (totalExecutions > 0 && status != ExecutionStatus.RUNNING) {
            Text(
                text = "$successRate%",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (successRate >= 80) {
                    MaterialTheme.colorScheme.tertiary
                } else if (successRate >= 50) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
fun CreateWorkflowDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
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
                onClick = { onCreate(name, description) },
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
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> stringResource(R.string.time_just_now)
        diff < 3600_000 -> stringResource(R.string.time_minutes_ago, diff / 60_000)
        diff < 86400_000 -> stringResource(R.string.time_hours_ago, diff / 3600_000)
        diff < 604800_000 -> stringResource(R.string.time_days_ago, diff / 86400_000)
        else -> formatDate(timestamp)
    }
}

