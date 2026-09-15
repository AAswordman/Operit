package com.ai.assistance.operit.ui.features.packages.dialogs

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.javascript.JsToolManager
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptExecutionDialog(
        packageName: String,
        tool: PackageTool,
        packageManager: PackageManager,
        initialResult: ToolResult?,
        onExecuted: (ToolResult) -> Unit,
        onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scriptText by remember(tool) { mutableStateOf(tool.script) }
    var paramValues by remember(tool) { mutableStateOf(tool.parameters.associate { it.name to "" }) }
    var executing by remember { mutableStateOf(false) }
    var executionResults by remember { mutableStateOf<List<ToolResult>>(emptyList()) }

    LaunchedEffect(initialResult) {
        if (initialResult != null) {
            executionResults = listOf(initialResult)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                // 紧凑的标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val fullToolId = "${packageName}:${tool.name}"
                        Text(
                            text = stringResource(R.string.script_execution),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "ID: $fullToolId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // 脚本编辑器
                    Text(
                        text = stringResource(R.string.script_code),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    ) {
                        TextField(
                            value = scriptText,
                            onValueChange = { newValue -> scriptText = newValue },
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            placeholder = {
                                Text(
                                    stringResource(R.string.script_code_placeholder),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        )
                    }

                    // 参数输入
                    if (tool.parameters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.script_params_config),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        tool.parameters.forEach { param ->
                            val normalizedParamName = param.name.trim().lowercase()
                            val isPromptParameter =
                                param.type.equals("string", ignoreCase = true) &&
                                    (normalizedParamName == "prompt" ||
                                        normalizedParamName.endsWith("_prompt"))

                            OutlinedTextField(
                                value = paramValues[param.name] ?: "",
                                onValueChange = { value ->
                                    paramValues = paramValues.toMutableMap().apply {
                                        put(param.name, value)
                                    }
                                },
                                label = {
                                    Text("${param.name}${if (param.required) " *" else ""}")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = !isPromptParameter,
                                minLines = if (isPromptParameter) 3 else 1,
                                placeholder = { Text(param.description.resolve(context)) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // 执行结果
                    if (executionResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.execution_result),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(executionResults) { result ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (result.success) 
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else 
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (result.success) result.result.toString() else stringResource(R.string.script_error, result.error ?: ""),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    FilledTonalButton(
                        onClick = {
                            executing = true
                            executionResults = emptyList()
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val missingParams = tool.parameters
                                        .filter { it.required }
                                        .map { it.name }
                                        .filter { paramValues[it].isNullOrEmpty() }

                                    if (missingParams.isNotEmpty()) {
                                        val missingResult = ToolResult(
                                            toolName = "${packageName}:${tool.name}",
                                            success = false,
                                            result = StringResultData(""),
                                            error = context.getString(R.string.script_missing_params, missingParams.joinToString(", "))
                                        )
                                        withContext(Dispatchers.Main) {
                                            executionResults = listOf(missingResult)
                                            onExecuted(missingResult)
                                        }
                                    } else {
                                        val parameters = paramValues.map { (name, value) ->
                                            ToolParameter(name = name, value = value)
                                        }

                                        val aiTool = AITool(
                                            name = "${packageName}:${tool.name}",
                                            parameters = parameters
                                        )

                                        val interpreter = JsToolManager.getInstance(context, packageManager)

                                        interpreter
                                            .executeScript(scriptText, aiTool)
                                            .catch { e ->
                                                AppLogger.e("ScriptExecutionDialog", "Flow collection error", e)
                                                val errorResult = ToolResult(
                                                    toolName = "${packageName}:${tool.name}",
                                                    success = false,
                                                    result = StringResultData(""),
                                                    error = context.getString(R.string.script_flow_error, e.message ?: "")
                                                )
                                                withContext(Dispatchers.Main) {
                                                    executionResults = executionResults + errorResult
                                                    onExecuted(errorResult)
                                                }
                                            }
                                            .onCompletion {
                                                withContext(Dispatchers.Main) {
                                                    executing = false
                                                }
                                            }
                                            .collect { result ->
                                                withContext(Dispatchers.Main) {
                                                    executionResults = executionResults + result
                                                    onExecuted(result)
                                                }
                                            }
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("ScriptExecutionDialog", "Failed to execute script", e)
                                    withContext(Dispatchers.Main) {
                                        val finalError = ToolResult(
                                            toolName = "${packageName}:${tool.name}",
                                            success = false,
                                            result = StringResultData(""),
                                            error = context.getString(R.string.script_execution_error, e.message ?: "")
                                        )
                                        executionResults = executionResults + finalError
                                        onExecuted(finalError)
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) { executing = false }
                                }
                            }
                        },
                        enabled = !executing
                    ) {
                        if (executing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.script_executing))
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.script_execute))
                        }
                    }
                }
            }
        }
    }
}
