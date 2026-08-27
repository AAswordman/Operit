package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ConditionNode
import com.ai.assistance.operit.data.model.LogicNode
import com.ai.assistance.operit.data.model.WorkflowNode
import com.ai.assistance.operit.data.model.WorkflowNodeConnection

/**
 * 连接菜单对话框
 * 显示可连接的目标节点列表，支持创建和删除连接
 */
@Composable
fun ConnectionMenuDialog(
    sourceNode: WorkflowNode,
    allNodes: List<WorkflowNode>,
    existingConnections: List<WorkflowNodeConnection>,
    onCreateConnection: (targetNodeId: String) -> Unit,
    onDeleteConnection: (connectionId: String) -> Unit,
    onUpdateConnectionCondition: (connectionId: String, condition: String?) -> Unit,
    onDismiss: () -> Unit
) {
    // 获取从源节点出发的所有连接
    val connectionsFromSource = existingConnections.filter { 
        it.sourceNodeId == sourceNode.id 
    }
    
    // 获取可以连接的目标节点（排除自己和已连接的节点）
    val connectedTargetIds = connectionsFromSource.map { it.targetNodeId }.toSet()
    val availableTargets = allNodes.filter { 
        it.id != sourceNode.id && it.id !in connectedTargetIds
    }

    var editingConnectionId by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.workflow_manage_connections))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.workflow_source_node_format, sourceNode.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 已有连接列表
                if (connectionsFromSource.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.workflow_existing_connections),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    items(connectionsFromSource) { connection ->
                        val targetNode = allNodes.find { it.id == connection.targetNodeId }
                        if (targetNode != null) {
                            ExistingConnectionItem(
                                sourceNode = sourceNode,
                                connection = connection,
                                targetNode = targetNode,
                                onEditCondition = { editingConnectionId = connection.id },
                                onDelete = { onDeleteConnection(connection.id) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // 可连接的节点列表
                if (availableTargets.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.workflow_select_target_node),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(availableTargets) { targetNode ->
                        AvailableTargetItem(
                            targetNode = targetNode,
                            onConnect = { onCreateConnection(targetNode.id) }
                        )
                    }
                } else {
                    if (connectionsFromSource.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.workflow_no_connectable_nodes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workflow_close))
            }
        }
    )

    val editingConnection = editingConnectionId?.let { id ->
        connectionsFromSource.firstOrNull { it.id == id }
    }
    editingConnection?.let { connection ->
        val targetNode = allNodes.find { it.id == connection.targetNodeId }
        if (targetNode != null) {
            ConnectionConditionDialog(
                sourceNode = sourceNode,
                targetNode = targetNode,
                initialCondition = connection.condition,
                onConfirm = { newCondition ->
                    onUpdateConnectionCondition(connection.id, newCondition)
                    editingConnectionId = null
                },
                onDismiss = { editingConnectionId = null }
            )
        }
    }
}

/**
 * 已有连接项
 */
@Composable
private fun ExistingConnectionItem(
    sourceNode: WorkflowNode,
    connection: WorkflowNodeConnection,
    targetNode: WorkflowNode,
    onEditCondition: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (targetNode.type == "trigger") "🎯" else "⚙️",
                    style = MaterialTheme.typography.bodyLarge
                )
                Column {
                    Text(
                        text = targetNode.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.workflow_connection_condition_arrow_format,
                            conditionToDisplayText(sourceNode, connection.condition)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEditCondition
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.workflow_edit_connection_condition)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.workflow_delete_connection)
                    )
                }
            }
        }
    }
}

private enum class ConnectionConditionMode {
    DEFAULT,
    FALSE,
    CUSTOM
}

@Composable
private fun conditionToDisplayText(sourceNode: WorkflowNode, condition: String?): String {
    val c = condition?.trim().orEmpty()
    val isConditionLike = sourceNode is ConditionNode || sourceNode is LogicNode

    if (c.isBlank()) {
        return if (isConditionLike) {
            stringResource(R.string.workflow_connection_condition_default_true)
        } else {
            stringResource(R.string.workflow_connection_condition_default_none)
        }
    }

    return when (c.lowercase()) {
        "true" -> "true"
        "false" -> "false"
        else -> stringResource(R.string.workflow_connection_condition_regex_format, c)
    }
}

@Composable
private fun ConnectionConditionDialog(
    sourceNode: WorkflowNode,
    targetNode: WorkflowNode,
    initialCondition: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val initialTrimmed = initialCondition?.trim().orEmpty()
    val initialMode = when {
        initialTrimmed.isBlank() -> ConnectionConditionMode.DEFAULT
        initialTrimmed.equals("false", ignoreCase = true) -> ConnectionConditionMode.FALSE
        else -> ConnectionConditionMode.CUSTOM
    }

    var mode by remember { mutableStateOf(initialMode) }
    var custom by remember { mutableStateOf(if (initialMode == ConnectionConditionMode.CUSTOM) initialTrimmed else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = stringResource(R.string.workflow_edit_connection_condition_title))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${sourceNode.name} → ${targetNode.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val defaultLabel =
                    if (sourceNode is ConditionNode || sourceNode is LogicNode) {
                        stringResource(R.string.workflow_connection_condition_default_true_branch)
                    } else {
                        stringResource(R.string.workflow_connection_condition_default_no_condition)
                    }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = mode == ConnectionConditionMode.DEFAULT,
                        onClick = { mode = ConnectionConditionMode.DEFAULT }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(defaultLabel)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = mode == ConnectionConditionMode.FALSE,
                        onClick = { mode = ConnectionConditionMode.FALSE }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.workflow_connection_condition_false_branch))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = mode == ConnectionConditionMode.CUSTOM,
                        onClick = { mode = ConnectionConditionMode.CUSTOM }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.workflow_connection_condition_custom))
                }

                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    enabled = mode == ConnectionConditionMode.CUSTOM,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = when (mode) {
                        ConnectionConditionMode.DEFAULT -> null
                        ConnectionConditionMode.FALSE -> "false"
                        ConnectionConditionMode.CUSTOM -> custom.trim().ifBlank { null }
                    }
                    onConfirm(result)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}

/**
 * 可连接的目标节点项
 */
@Composable
private fun AvailableTargetItem(
    targetNode: WorkflowNode,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (targetNode.type == "trigger") "🎯" else "⚙️",
                    style = MaterialTheme.typography.bodyLarge
                )
                Column {
                    Text(
                        text = targetNode.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (targetNode.description.isNotEmpty()) {
                        Text(
                            text = targetNode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            
            IconButton(
                onClick = onConnect,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.workflow_create_connection)
                )
            }
        }
    }
}

