package com.ai.assistance.operit.ui.features.extensions.packages.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.EnvVar
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ScriptExecutionTraceData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.javascript.JsToolManager
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui.PackageDialogHeader
import com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui.PackageDialogLayout
import com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui.PackageEnvironmentNotice
import com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui.PackageToolParameterField
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptExecutionDialog(
    packageName: String,
    tool: PackageTool,
    environmentVariables: List<EnvVar>,
    packageManager: PackageManager,
    initialResult: ToolResult?,
    onExecuted: (ToolResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnExecuted by rememberUpdatedState(onExecuted)
    val fullToolId = "$packageName:${tool.name}"
    var paramValues by remember(packageName, tool) {
        mutableStateOf(tool.parameters.associate { it.name to "" })
    }
    var executing by remember(packageName, tool) { mutableStateOf(false) }
    var executionResults by remember(packageName, tool) {
        // 初始结果仅在打开时恢复，执行回调更新父状态时不覆盖当前的结果列表。
        mutableStateOf(listOfNotNull(initialResult))
    }

    var showExecutionLogs by remember(packageName, tool) { mutableStateOf(false) }
    // 日志和中间输出使用 success=true 封装，不能当成脚本最终执行成功。
    val finalResults = remember(executionResults) {
        executionResults.filterNot { it.result is ScriptExecutionTraceData }
    }
    val executionTraces = remember(executionResults) {
        executionResults.mapNotNull { it.result as? ScriptExecutionTraceData }
    }
    PackageDialogLayout(onDismiss = onDismiss) { compact ->
        PackageDialogHeader(
            title = tool.name,
            icon = Icons.Default.PlayCircleOutline,
            compact = compact,
            onDismiss = onDismiss,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "ID: $fullToolId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val description = tool.description.resolve(context)
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PackageEnvironmentNotice(environmentVariables)
            if (tool.parameters.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.script_params_config),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                tool.parameters.forEach { parameter ->
                    key(parameter.name) {
                        PackageToolParameterField(
                            parameter = parameter,
                            value = paramValues.getValue(parameter.name),
                            onValueChange = { value ->
                                paramValues = paramValues.toMutableMap().apply {
                                    put(parameter.name, value)
                                }
                            },
                            enabled = !executing,
                        )
                    }
                }
            }
            if (finalResults.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.execution_result),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                finalResults.forEach { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.success) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (result.success) result.result.toString() else {
                                        stringResource(R.string.script_error, result.error.orEmpty())
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            if (executionTraces.isNotEmpty()) {
                TextButton(onClick = { showExecutionLogs = !showExecutionLogs }) {
                    Icon(
                        imageVector = if (showExecutionLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pkg_runtime_monitor_logs_title))
                }
                if (showExecutionLogs) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                    ) {
                        SelectionContainer {
                            Text(
                                text = executionTraces.joinToString("\n"),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pkg_close)) }
            Button(
                enabled = !executing,
                onClick = {
                    val missingParams = tool.parameters
                        .filter { it.required && paramValues.getValue(it.name).isEmpty() }
                        .map { it.name }
                    if (missingParams.isNotEmpty()) {
                        val missingResult = ToolResult(
                            toolName = fullToolId,
                            success = false,
                            result = StringResultData(""),
                            error = context.getString(R.string.script_missing_params, missingParams.joinToString(", ")),
                        )
                        executionResults = listOf(missingResult)
                        latestOnExecuted(missingResult)
                    } else {
                        // 固定本次调用参数并执行插件原始脚本，界面不再持有临时源码副本。
                        val aiTool = AITool(
                            name = fullToolId,
                            parameters = paramValues.map { (name, value) -> ToolParameter(name, value) },
                        )
                        executing = true
                        executionResults = emptyList()
                        showExecutionLogs = false
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    JsToolManager.getInstance(context, packageManager)
                                        .executeScript(tool.script, aiTool)
                                        .catch { error ->
                                            AppLogger.e("ScriptExecutionDialog", "脚本结果流收集失败", error)
                                            emit(
                                                ToolResult(
                                                    toolName = fullToolId,
                                                    success = false,
                                                    result = StringResultData(""),
                                                    error = context.getString(R.string.script_flow_error, error.message.orEmpty()),
                                                )
                                            )
                                        }
                                        .collect { result ->
                                            withContext(Dispatchers.Main) {
                                                executionResults = executionResults + result
                                                // 父页面只缓存最终结果，重新打开时不会把日志显示成成功。
                                                if (result.result !is ScriptExecutionTraceData) {
                                                    latestOnExecuted(result)
                                                }
                                            }
                                        }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                AppLogger.e("ScriptExecutionDialog", "脚本执行失败", error)
                                val result = ToolResult(
                                    toolName = fullToolId,
                                    success = false,
                                    result = StringResultData(""),
                                    error = context.getString(R.string.script_execution_error, error.message.orEmpty()),
                                )
                                executionResults = executionResults + result
                                latestOnExecuted(result)
                            } finally {
                                executing = false
                            }
                        }
                    }
                },
            ) {
                if (executing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.script_executing))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.run))
                }
            }
        }
    }
}