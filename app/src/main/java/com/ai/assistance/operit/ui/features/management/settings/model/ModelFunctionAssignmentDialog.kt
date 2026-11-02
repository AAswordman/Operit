package com.ai.assistance.operit.ui.features.management.settings.model

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.config.model.ModelConfigSelection
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 功能列表与总结页共用真实绑定、思考偏好和能力测试窗口。 */
@Composable
fun ModelFunctionAssignmentDialog(
    functionType: FunctionType,
    saveCoordinator: ModelConfigSaveCoordinator,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val functionalManager = remember { FunctionalConfigManager(context) }
    val modelManager = remember { ModelConfigManager(context) }
    val mappings by functionalManager.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())
    val selection by modelManager.configSelectionFlow.collectAsState(initial = ModelConfigSelection())
    val pendingUpdates = remember { mutableListOf<Job>() }
    val updateMutex = remember { Mutex() }
    var saveFailure by remember { mutableStateOf<Exception?>(null) }
    var dismissing by remember { mutableStateOf(false) }

    suspend fun awaitUpdates() {
        while (pendingUpdates.isNotEmpty()) pendingUpdates.toList().joinAll()
        saveFailure?.let { throw it }
    }
    RegisterModelConfigSaveAction(saveCoordinator, "function-assignment:${functionType.name}") { _ -> awaitUpdates() }
    fun updateAssignment(action: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            // 绑定与思考偏好都读取完整映射，串行更新才能保留连续操作的结果。
            updateMutex.withLock {
                try {
                    action()
                    saveFailure = null
                    OKToast.success(context, R.string.config_saved, OKToast.LENGTH_SHORT).show()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    saveFailure = error
                    AppLogger.e("ModelFunctionAssignmentDialog", "保存功能分配失败", error)
                    OKToast.error(context, R.string.save_failed, OKToast.LENGTH_SHORT).show()
                }
            }
        }
        pendingUpdates.add(job)
        job.invokeOnCompletion { pendingUpdates.remove(job) }
        job.start()
    }
    fun dismissAfterSaving() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            try {
                // 窗口自己的作用域离开组合前，完成仍在写入的绑定和思考偏好。
                awaitUpdates()
                onDismiss()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ModelFunctionAssignmentDialog", "关闭功能分配窗口前保存失败", error)
                OKToast.error(context, R.string.save_failed, OKToast.LENGTH_SHORT).show()
            } finally {
                dismissing = false
            }
        }
    }
    val mapping = mappings[functionType]
    if (mapping == null || selection.allConfigs.isEmpty()) return
    val config = selection.allConfigs.find { it.id == mapping.configId }
    val groupName = selection.selectedGroup?.name ?: stringResource(R.string.ungrouped)
    val boundGroup = if (config != null && config.groupId != selection.selectedGroupId) {
        selection.groups.find { it.id == config.groupId }?.name ?: stringResource(R.string.ungrouped)
    } else null
    ModelWorkspaceDialog(title = getFunctionDisplayName(functionType), onDismiss = ::dismissAfterSaving) {
        ModelFunctionEditor(
            functionType = functionType,
            currentConfig = config,
            currentModelIndex = mapping.modelIndex,
            currentMapping = mapping,
            availableConfigs = selection.availableConfigs,
            selectedGroupName = groupName,
            boundConfigGroupName = boundGroup,
            onConfigSelected = { configId, modelIndex ->
                updateAssignment {
                    functionalManager.setConfigForFunction(functionType, configId, modelIndex)
                    if (functionType != FunctionType.MEMORY_EMBEDDING) EnhancedAIService.refreshServiceForFunction(context, functionType)
                }
            },
            onThinkingChanged = { enabled, option ->
                updateAssignment { functionalManager.setThinkingForFunction(functionType, enabled, option) }
            },
        )
    }
}