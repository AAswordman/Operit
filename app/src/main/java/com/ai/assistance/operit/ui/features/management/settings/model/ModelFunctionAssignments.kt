package com.ai.assistance.operit.ui.features.management.settings.model

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSelection
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.management.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.management.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** 列表只调整排列，绑定与测试继续使用原功能编辑器。 */
@Composable
fun ModelFunctionAssignments(currentConfigId: String, saveCoordinator: ModelConfigSaveCoordinator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val functionalManager = remember { FunctionalConfigManager(context) }
    val modelManager = remember { ModelConfigManager(context) }
    val mappings by functionalManager.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())
    val selection by modelManager.configSelectionFlow.collectAsState(initial = ModelConfigSelection())
    var onlyCurrent by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var editingFunction by remember { mutableStateOf<FunctionType?>(null) }
    var showReset by remember { mutableStateOf(false) }
    val pendingUpdates = remember { mutableListOf<Job>() }
    var saveFailure by remember { mutableStateOf<Exception?>(null) }
    RegisterModelConfigSaveAction(saveCoordinator, "function-assignments") { _ ->
        pendingUpdates.toList().joinAll()
        saveFailure?.let { throw it }
    }
    fun updateAssignment(action: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                action()
                saveFailure = null
                OKToast.success(context, R.string.config_saved, OKToast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveFailure = e
                AppLogger.e("ModelFunctionAssignments", "保存功能分配失败", e)
                OKToast.error(context, R.string.save_failed, OKToast.LENGTH_SHORT).show()
            }
        }
        pendingUpdates.add(job)
        job.invokeOnCompletion { pendingUpdates.remove(job) }
        job.start()
    }
    val names = FunctionType.values().associateWith { getFunctionDisplayName(it) }
    val visibleFunctions = FunctionType.values().filter { function ->
        val mapping = mappings[function]
        val config = selection.allConfigs.find { it.id == mapping?.configId }
        val boundModel = if (config != null && mapping != null) getModelByIndex(config.modelName, getValidModelIndex(config.modelName, mapping.modelIndex)) else ""
        (!onlyCurrent || mapping?.configId == currentConfigId) &&
            (query.isBlank() || (names.getValue(function) + " " + config?.name.orEmpty() + " " + boundModel).contains(query, ignoreCase = true))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.model_workspace_function_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !onlyCurrent, onClick = { onlyCurrent = false }, label = { Text(stringResource(R.string.model_workspace_all_functions)) })
            FilterChip(selected = onlyCurrent, onClick = { onlyCurrent = true }, label = { Text(stringResource(R.string.model_workspace_current_functions)) })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(if (searching) Icons.Default.Close else Icons.Default.Search, stringResource(R.string.model_workspace_function_search))
            }
        }
        if (searching) {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.model_workspace_function_search)) })
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
            Column {
                visibleFunctions.forEachIndexed { index, function ->
                    val mapping = mappings[function]
                    val config = selection.allConfigs.find { it.id == mapping?.configId }
                    val isCurrent = config?.id == currentConfigId
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    Row(
                        Modifier.fillMaxWidth().background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface)
                            .clickable { editingFunction = function }.heightIn(min = 76.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(functionIcon(function), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(names.getValue(function), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(config?.name ?: stringResource(R.string.not_configured), modifier = Modifier.widthIn(max = 132.dp), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(if (config != null && mapping != null) getModelByIndex(config.modelName, getValidModelIndex(config.modelName, mapping.modelIndex)) else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (visibleFunctions.isEmpty()) {
                    Text(stringResource(R.string.model_workspace_no_functions), Modifier.padding(18.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(stringResource(R.string.model_workspace_function_count, visibleFunctions.size, FunctionType.values().size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { showReset = true }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.reset_all_functions_to_default))
        }
    }
    editingFunction?.let { function ->
        ModelFunctionAssignmentDialog(function, saveCoordinator) { editingFunction = null }
    }
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.reset_all_functions_to_default)) },
            text = { Text(stringResource(R.string.model_workspace_reset_functions_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    updateAssignment {
                        functionalManager.resetAllFunctionConfigs()
                        EnhancedAIService.refreshAllServices(context)
                        showReset = false
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun functionIcon(function: FunctionType) = when (function) {
    FunctionType.CHAT -> Icons.Default.ChatBubbleOutline
    FunctionType.SUMMARY -> Icons.Default.Summarize
    FunctionType.TITLE_GENERATION -> Icons.Default.Title
    FunctionType.MEMORY -> Icons.Default.Memory
    FunctionType.MEMORY_EMBEDDING -> Icons.Default.Storage
    FunctionType.UI_CONTROLLER -> Icons.Default.TouchApp
    FunctionType.TRANSLATION -> Icons.Default.Translate
    FunctionType.GREP -> Icons.Default.Search
    FunctionType.ROLE_RESPONSE_PLANNER -> Icons.Default.People
    FunctionType.IMAGE_RECOGNITION -> Icons.Default.Image
    FunctionType.AUDIO_RECOGNITION -> Icons.Default.Mic
    FunctionType.VIDEO_RECOGNITION -> Icons.Default.Videocam
}