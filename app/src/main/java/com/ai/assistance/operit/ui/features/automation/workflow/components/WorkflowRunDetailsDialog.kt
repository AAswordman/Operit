package com.ai.assistance.operit.ui.features.automation.workflow.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.workflow.runtime.classifyWorkflowIssue
import com.ai.assistance.operit.core.workflow.runtime.readableWorkflowFailure
import com.ai.assistance.operit.data.repository.WorkflowRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ai.assistance.operit.core.workflow.model.WorkflowExecutionIssue
import com.ai.assistance.operit.core.workflow.model.WorkflowExecutionRecord
import com.ai.assistance.operit.core.workflow.model.WorkflowNodeRunStatus

@StringRes
internal fun WorkflowExecutionIssue.descriptionRes(): Int = when (this) {
    WorkflowExecutionIssue.TIMEOUT -> R.string.workflow_issue_timeout
    WorkflowExecutionIssue.NETWORK -> R.string.workflow_issue_network
    WorkflowExecutionIssue.AUTHENTICATION -> R.string.workflow_issue_auth
    WorkflowExecutionIssue.QUOTA -> R.string.workflow_issue_quota
    WorkflowExecutionIssue.PERMISSION -> R.string.workflow_issue_permission
    WorkflowExecutionIssue.TOOL_MISSING -> R.string.workflow_issue_tool_missing
    WorkflowExecutionIssue.INVALID_INPUT -> R.string.workflow_issue_input
    WorkflowExecutionIssue.REFERENCE -> R.string.workflow_issue_reference
    WorkflowExecutionIssue.BUSY -> R.string.workflow_issue_busy
    WorkflowExecutionIssue.CANCELLED -> R.string.workflow_issue_cancelled
    WorkflowExecutionIssue.CYCLE -> R.string.workflow_issue_cycle
    WorkflowExecutionIssue.NO_TRIGGER -> R.string.workflow_issue_trigger
    WorkflowExecutionIssue.RUNTIME -> R.string.workflow_issue_runtime
    WorkflowExecutionIssue.UNKNOWN -> R.string.workflow_issue_unknown
}

/** 列表与节点菜单共用执行报告；旧记录不推测节点结果，新记录直接展示结构化信息。 */
@Composable
fun WorkflowRunDetailsDialog(
    workflowId: String,
    nodeId: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { WorkflowRepository(context.applicationContext) }
    var records by remember(workflowId) { mutableStateOf<List<WorkflowExecutionRecord>>(emptyList()) }
    var loading by remember(workflowId) { mutableStateOf(true) }
    var loadFailed by remember(workflowId) { mutableStateOf(false) }
    var selectedIndex by remember(workflowId) { mutableIntStateOf(0) }
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    LaunchedEffect(workflowId) {
        repository.getExecutionRecords(workflowId).fold(
            onSuccess = { records = it },
            onFailure = { loadFailed = true },
        )
        loading = false
    }
    val record = records.getOrNull(selectedIndex)
    val nodeNames = record?.nodes.orEmpty().associate { it.nodeId to it.nodeName }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_run_details)) },
        text = {
            when {
                loading -> CircularProgressIndicator()
                loadFailed -> Text(stringResource(R.string.workflow_run_load_failed))
                record == null -> Text(stringResource(R.string.workflow_execution_log_empty))
                else -> Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    if (records.size > 1) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(enabled = selectedIndex < records.lastIndex, onClick = { selectedIndex++ }) {
                                Text(stringResource(R.string.workflow_run_older))
                            }
                            Text(stringResource(R.string.workflow_run_position, selectedIndex + 1, records.size))
                            TextButton(enabled = selectedIndex > 0, onClick = { selectedIndex-- }) {
                                Text(stringResource(R.string.workflow_run_newer))
                            }
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text(record.workflowName, fontWeight = FontWeight.SemiBold)
                            Text(formatter.format(Date(record.startedAt)), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(if (record.success) R.string.workflow_execution_success else R.string.workflow_execution_failed))
                            Text(
                                stringResource(R.string.workflow_run_duration, (record.finishedAt - record.startedAt).coerceAtLeast(0) / 1000.0),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!record.success) {
                                val failedNodes = record.nodes.filter { it.status == WorkflowNodeRunStatus.FAILED }
                                if (failedNodes.isNotEmpty()) {
                                    Text(stringResource(R.string.workflow_run_failed_nodes, failedNodes.joinToString { it.nodeName }))
                                }
                                val issue = record.issue ?: classifyWorkflowIssue(record.failureReason ?: record.message)
                                Text(stringResource(issue.descriptionRes()), color = MaterialTheme.colorScheme.error)
                                if (failedNodes.isEmpty()) {
                                    val reason = readableWorkflowFailure(record.failureReason ?: record.message, nodeNames)
                                    if (reason.isNotBlank()) SelectionContainer {
                                        Text(stringResource(R.string.workflow_failure_reason, reason), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (record.nodes.isEmpty()) {
                            item { Text(stringResource(R.string.workflow_run_legacy)) }
                        }
                        items(record.nodes.filter { nodeId == null || it.nodeId == nodeId }, key = { it.nodeId }) { node ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(node.nodeName, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(when (node.status) {
                                        WorkflowNodeRunStatus.SUCCESS -> R.string.workflow_run_node_success
                                        WorkflowNodeRunStatus.FAILED -> R.string.workflow_run_node_failed
                                        WorkflowNodeRunStatus.SKIPPED -> R.string.workflow_run_node_skipped
                                        WorkflowNodeRunStatus.NOT_RUN -> R.string.workflow_run_node_not_run
                                        WorkflowNodeRunStatus.CANCELLED -> R.string.workflow_run_node_cancelled
                                    }))
                                    if (node.startedAt != null && node.finishedAt != null) {
                                        Text(
                                            stringResource(R.string.workflow_run_duration, (node.finishedAt - node.startedAt).coerceAtLeast(0) / 1000.0),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    node.actionName?.takeIf { it.isNotBlank() }?.let { action ->
                                        Text(stringResource(R.string.workflow_failure_action, action), style = MaterialTheme.typography.bodySmall)
                                    }
                                    node.issue?.let { Text(stringResource(it.descriptionRes()), color = MaterialTheme.colorScheme.error) }
                                    if (node.status == WorkflowNodeRunStatus.FAILED) {
                                        val reason = readableWorkflowFailure(node.failureReason, nodeNames)
                                        if (reason.isNotBlank()) SelectionContainer {
                                            Text(stringResource(R.string.workflow_failure_reason, reason), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    node.resultSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                                        Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
