package com.ai.assistance.operit.ui.features.management.settings.screens

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.management.settings.model.*
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceMenu
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.api.chat.keypool.ApiKeyPoolAvailabilityTester
import com.ai.assistance.operit.api.chat.model.ModelConfigConnectionTester
import com.ai.assistance.operit.api.chat.model.ModelConnectionTestOutcome
import com.ai.assistance.operit.api.chat.model.ModelConnectionTestType
import com.ai.assistance.operit.ui.features.management.settings.components.ExpandableStatusText
import com.ai.assistance.operit.api.chat.model.ThinkingQualityMappingRegistry
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigGroup
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.ui.features.management.settings.DebouncedModelConfigAutoSaveEffect
import com.ai.assistance.operit.ui.features.management.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.management.settings.contextsummary.ModelContextSummaryContent
import com.ai.assistance.operit.ui.features.management.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.ui.features.management.settings.rememberModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.management.settings.keypool.ApiKeyPoolSettingsSection
import com.ai.assistance.operit.ui.features.management.settings.sections.AdvancedSettingsSection
import com.ai.assistance.operit.ui.features.management.settings.model.ModelConfigPreviewDialog
import com.ai.assistance.operit.ui.features.management.settings.model.buildModelConfigPreview
import com.ai.assistance.operit.ui.features.management.settings.sections.ModelApiSettingsSection
import com.ai.assistance.operit.ui.features.management.settings.sections.ModelParametersSection
import com.ai.assistance.operit.ui.features.management.settings.sections.SettingsInfoBanner
import com.ai.assistance.operit.ui.features.management.settings.sections.SettingsSwitchRow
import com.ai.assistance.operit.ui.features.management.settings.sections.SettingsTextField
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.ai.assistance.operit.ui.features.management.settings.model.ModelFunctionAssignments
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceChoiceDialog
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceSection
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceTab
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceTabs
import com.ai.assistance.operit.ui.features.management.settings.model.resolveWorkspaceConfig

private data class HeaderPreset(val nameResId: Int, val headers: Map<String, String>)
private data class ConfigOrganizerRow(
    val key: String,
    val groupId: String?,
    val config: ModelConfigSummary? = null,
    val group: ModelConfigGroup? = null
)


private val headerPresets =
    listOf(
        HeaderPreset(
            nameResId = R.string.headers_preset_android_browser,
            headers =
                mapOf(
                    "User-Agent" to
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                )
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_desktop_browser_win,
            headers =
                mapOf(
                    "User-Agent" to
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
                )
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_lang_zh,
            headers = mapOf("Accept-Language" to "zh-CN,zh;q=0.9")
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_lang_en,
            headers = mapOf("Accept-Language" to "en-US,en;q=0.9")
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_cmcc_gateway,
            headers = mapOf("X-Forwarded-For" to "211.136.1.10", "Via" to "CMNET")
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_us_la,
            headers =
                mapOf(
                    "Accept-Language" to "en-US,en;q=0.9",
                    "X-Forwarded-For" to "38.107.226.5"
                )
        )
    )

private fun parseHeaderEntries(headersJson: String): List<Pair<String, String>> {
    return runCatching {
        if (headersJson.isBlank() || headersJson == "{}") {
            emptyList()
        } else {
            val jsonObject = JSONObject(headersJson)
            buildList {
                for (key in jsonObject.keys()) {
                    add(key to jsonObject.getString(key))
                }
            }
        }
    }.getOrElse { emptyList() }
}

private fun serializeHeaderEntries(headers: List<Pair<String, String>>): String {
    return JSONObject().apply {
        headers.forEach { (key, value) ->
            val normalizedKey = key.trim()
            if (normalizedKey.isNotEmpty()) {
                put(normalizedKey, value)
            }
        }
    }.toString()
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ModelConfigScreen(
    navigateToMnnModelDownload: (() -> Unit)? = null,
    initialTab: ModelWorkspaceTab = ModelWorkspaceTab.CONNECTION,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configManager = remember { ModelConfigManager(context) }
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val scope = rememberCoroutineScope()
    var workspaceTab by rememberSaveable { mutableStateOf(initialTab) }
    val connectionListState = rememberLazyListState()
    val parameterListState = rememberLazyListState()
    val summaryListState = rememberLazyListState()
    val functionListState = rememberLazyListState()
    val listState = when (workspaceTab) {
        ModelWorkspaceTab.CONNECTION -> connectionListState
        ModelWorkspaceTab.PARAMETERS -> parameterListState
        ModelWorkspaceTab.SUMMARY -> summaryListState
        ModelWorkspaceTab.FUNCTIONS -> functionListState
    }
    var savingSelection by remember { mutableStateOf(false) }
    val saveCoordinator = rememberModelConfigSaveCoordinator()
    // 分组、候选和编辑记忆使用同一次持久化快照，避免异步列表将选择重置为首项。
    var workspaceSelection by remember { mutableStateOf<com.ai.assistance.operit.data.model.ModelConfigSelection?>(null) }
    val configSummaries = workspaceSelection?.allConfigs.orEmpty()
    val configList = configSummaries.map { it.id }
    val configGroups = workspaceSelection?.groups.orEmpty()
    val selectedGroupId = workspaceSelection?.selectedGroupId
    val visibleConfigIds = workspaceSelection?.availableConfigs.orEmpty().map { it.id }
    // 进入页面时默认选中“对话功能”当前绑定的模型配置
    var selectedConfigId by remember { mutableStateOf("") }
    val selectedConfig = remember { mutableStateOf<ModelConfigData?>(null) }
    val keyAvailabilityTester =
        remember(selectedConfigId) {
            ApiKeyPoolAvailabilityTester(selectedConfigId, configManager)
        }
    var hasInitializedSelection by remember { mutableStateOf(false) }

    // 配置名称映射
    val configNameMap = remember { mutableStateMapOf<String, String>() }

    // UI状态
    var showRenameConfigDialog by remember { mutableStateOf(false) }
    var showConfigOrganizer by remember { mutableStateOf(false) }
    var showMoreActions by remember { mutableStateOf(false) }
    var configPreview by remember { mutableStateOf<String?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var renameConfigName by remember { mutableStateOf("") }

    // 连接测试状态
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<ConnectionTestItem>?>(null) }
    var connectionTestJob by remember { mutableStateOf<Job?>(null) }
    var activeConnectionTestService by remember { mutableStateOf<AIService?>(null) }

    DisposableEffect(keyAvailabilityTester) {
        onDispose { keyAvailabilityTester.close() }
    }

    LaunchedEffect(Unit) {
        configManager.configSelectionFlow.collectLatest { selection ->
            val enteringGroup = workspaceSelection == null || workspaceSelection?.selectedGroupId != selection.selectedGroupId
            val prompt = ActivePromptManager.getInstance(context).getActivePrompt()
            val card = (prompt as? ActivePrompt.CharacterCard)?.let {
                CharacterCardManager.getInstance(context).getCharacterCard(it.id)
            }
            val fixedConfigId = card?.takeIf {
                CharacterCardChatModelBindingMode.normalize(it.chatModelBindingMode) == CharacterCardChatModelBindingMode.FIXED_CONFIG
            }?.chatModelConfigId?.takeIf { it.isNotBlank() }
            val chatConfigId = fixedConfigId ?: functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
            val candidateIds = selection.availableConfigs.map { it.id }
            selectedConfigId = com.ai.assistance.operit.ui.features.management.settings.model.resolveWorkspaceConfig(
                candidateIds = candidateIds,
                currentConfigId = selectedConfigId.takeUnless { enteringGroup },
                activeConfigId = chatConfigId,
                rememberedConfigId = selection.workspaceConfigId,
            )
            workspaceSelection = selection
            hasInitializedSelection = true
        }
    }
    LaunchedEffect(hasInitializedSelection, selectedGroupId, selectedConfigId) {
        if (hasInitializedSelection && selectedConfigId.isNotEmpty()) {
            configManager.setWorkspaceSelectedConfig(selectedGroupId, selectedConfigId)
        }
    }
    // 加载所有配置名称
    LaunchedEffect(configList) {
        configList.forEach { id ->
            val config = configManager.getModelConfigFlow(id).first()
            configNameMap[id] = config.name
        }
    }

    // 加载选中的配置
    LaunchedEffect(selectedConfigId) {
        testResults = null
        selectedConfig.value = null
        if (selectedConfigId.isEmpty()) return@LaunchedEffect
        configManager.getModelConfigFlow(selectedConfigId).collect { config ->
            selectedConfig.value = config
        }
    }

    fun showNotification(message: String, kind: OKToast.Kind) {
        if (message.isBlank()) return
        OKToast.typed(context, message, kind, OKToast.LENGTH_SHORT).show()
    }



    DisposableEffect(lifecycleOwner, saveCoordinator) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    saveCoordinator.flushAllInBackground(showSuccess = false)
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 标签、配置或分组切换前完成保存，避免输入区离开组合时取消尚未写入的操作。
    fun afterSaving(change: suspend () -> Unit) {
        if (savingSelection) return
        savingSelection = true
        scope.launch {
            try {
                saveCoordinator.flushAll(showSuccess = false)
                change()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("ModelConfigScreen", "切换模型工作区前保存失败", e)
                showNotification(context.getString(R.string.save_failed) + ": " + e.message.orEmpty(), OKToast.Kind.Error)
            } finally {
                savingSelection = false
            }
        }
    }

    // 主界面内容
    CustomScaffold(
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border =
                        BorderStroke(
                            0.7.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        val selectedGroupName = configGroups.firstOrNull { it.id == selectedGroupId }?.name
                            ?: stringResource(R.string.ungrouped)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { afterSaving { showConfigOrganizer = true } }) {
                                Icon(Icons.Default.Folder, stringResource(R.string.model_config_manage_organization), tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(
                                Modifier.weight(1f)
                                    .clickable(enabled = visibleConfigIds.isNotEmpty() && !savingSelection) { isDropdownExpanded = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(selectedGroupName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(configNameMap[selectedConfigId] ?: stringResource(R.string.no_config_selected), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { isDropdownExpanded = true }, enabled = visibleConfigIds.isNotEmpty() && !savingSelection) {
                                Icon(Icons.Default.ExpandMore, stringResource(R.string.select_config))
                            }
                        }
                        FlowRow(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { showMoreActions = true },
                                enabled = selectedConfigId.isNotEmpty() && !savingSelection,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.model_workspace_more_actions))
                            }
                            if (selectedConfigId.isNotEmpty() &&
                                selectedConfigId != ModelConfigManager.DEFAULT_CONFIG_ID
                            ) {
                                TextButton(
                                    onClick = {
                                        renameConfigName = selectedConfig.value?.name ?: ""
                                        showRenameConfigDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.rename_action), fontSize = 14.sp)
                                }

                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val deletingConfigId = selectedConfigId
                                            try {
                                                saveCoordinator.flushAll(showSuccess = false)
                                                val affectedFunctions = configManager.deleteConfig(deletingConfigId)
                                                saveCoordinator.discardConfig(deletingConfigId)

                                                affectedFunctions.forEach { functionType ->
                                                    try {
                                                        EnhancedAIService.refreshServiceForFunction(
                                                            context.applicationContext,
                                                            functionType,
                                                        )
                                                    } catch (e: Exception) {
                                                        AppLogger.e(
                                                            "ModelConfigScreen",
                                                            "刷新删除配置影响的服务失败: $functionType",
                                                            e,
                                                        )
                                                    }
                                                }

                                                selectedConfigId = configManager.configSummariesFlow
                                                    .first()
                                                    .firstOrNull { it.groupId == selectedGroupId }
                                                    ?.id
                                                    ?: ""
                                                showNotification(context.getString(R.string.config_deleted), OKToast.Kind.Success)
                                            } catch (e: Exception) {
                                                AppLogger.e(
                                                    "ModelConfigScreen",
                                                    "删除模型配置失败: $deletingConfigId",
                                                    e,
                                                )
                                                showNotification(context.getString(R.string.save_failed), OKToast.Kind.Error)
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors =
                                        ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.delete_action), fontSize = 14.sp)
                                }
                            }

                            TextButton(
                                onClick = {
                                    if (isTestingConnection) {
                                        activeConnectionTestService?.cancelStreaming()
                                        connectionTestJob?.cancel()
                                        return@TextButton
                                    }

                                    connectionTestJob = scope.launch {
                                        try {
                                            isTestingConnection = true
                                            val results = mutableListOf<ConnectionTestItem>()
                                            try {
                                                saveCoordinator.flushAll(showSuccess = false)

                                                val latestConfig =
                                                    configManager.getModelConfig(selectedConfigId)

                                                latestConfig?.let { config ->
                                                    val report =
                                                        ModelConfigConnectionTester.run(
                                                            context = context,
                                                            modelConfigManager = configManager,
                                                            config = config,
                                                            onActiveServiceChanged = {
                                                                activeConnectionTestService = it
                                                            }
                                                        )

                                                    report.items.forEach { item ->
                                                        results.add(
                                                            ConnectionTestItem(
                                                                labelResId = item.type.toLabelResId(),
                                                                type = item.type,
                                                                outcome = item.outcome,
                                                                error = item.error
                                                            )
                                                        )
                                                    }
                                                } ?: run {
                                                    results.add(
                                                        ConnectionTestItem(
                                                            labelResId = R.string.test_item_chat,
                                                            type = ModelConnectionTestType.CHAT,
                                                            outcome = ModelConnectionTestOutcome.FAILED,
                                                            error = context.getString(
                                                                R.string.no_config_selected
                                                            )
                                                        )
                                                    )
                                                }
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                results.add(
                                                    ConnectionTestItem(
                                                        labelResId = R.string.test_item_chat,
                                                        type = ModelConnectionTestType.CHAT,
                                                        outcome = ModelConnectionTestOutcome.FAILED,
                                                        error = e.message
                                                    )
                                                )
                                            }
                                            testResults = results
                                        } finally {
                                            activeConnectionTestService = null
                                            isTestingConnection = false
                                            connectionTestJob = null
                                        }
                                    }
                                },
                                enabled = selectedConfigId.isNotEmpty() || isTestingConnection,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Dns,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(
                                        if (isTestingConnection) R.string.cancel
                                        else R.string.test_connection_desc
                                    ),
                                    fontSize = 14.sp
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = testResults != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            testResults?.let { results ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        results.forEachIndexed { index, item ->
                                            val statusText = item.statusText(context)
                                            val contentColor =
                                                when (item.outcome) {
                                                    ModelConnectionTestOutcome.PASSED ->
                                                        MaterialTheme.colorScheme.primary
                                                    ModelConnectionTestOutcome.UNVERIFIED ->
                                                        MaterialTheme.colorScheme.tertiary
                                                    ModelConnectionTestOutcome.FAILED ->
                                                        MaterialTheme.colorScheme.error
                                                }
                                            val icon =
                                                when (item.outcome) {
                                                    ModelConnectionTestOutcome.PASSED ->
                                                        Icons.Default.CheckCircle
                                                    ModelConnectionTestOutcome.UNVERIFIED ->
                                                        Icons.Default.Info
                                                    ModelConnectionTestOutcome.FAILED ->
                                                        Icons.Default.Warning
                                                }

                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = contentColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(item.labelResId),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                ExpandableStatusText(
                                                    text = statusText,
                                                    color = contentColor,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            if (index != results.lastIndex) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isDropdownExpanded) {
                        ModelWorkspaceChoiceDialog(
                            title = stringResource(R.string.select_model_config),
                            value = selectedConfigId,
                            choices = visibleConfigIds.map { it to (configNameMap[it] ?: context.getString(R.string.unnamed_profile)) },
                            onDismiss = { isDropdownExpanded = false },
                            onSelected = { configId -> afterSaving { selectedConfigId = configId; isDropdownExpanded = false } },
                        )
                    }
                }
            }

            stickyHeader(key = "workspace-tabs") {
                ModelWorkspaceTabs(selected = workspaceTab, onSelect = { tab ->
                    if (workspaceTab != tab) afterSaving { workspaceTab = tab }
                })
            }
            item(key = "workspace-editor:$selectedConfigId:$workspaceTab") {
                // 同一页签的编辑器保持在一个组合内，滚动不会销毁正在编辑的分区。
                key(selectedConfigId, workspaceTab) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (workspaceTab == ModelWorkspaceTab.FUNCTIONS) {
                            ModelFunctionAssignments(currentConfigId = selectedConfigId, saveCoordinator = saveCoordinator)
                        } else {
                            selectedConfig.value?.takeIf { it.id == selectedConfigId }?.let { config ->
                                when (workspaceTab) {
                                    ModelWorkspaceTab.CONNECTION -> {
                                        ModelApiSettingsSection(
                                            config = config,
                                            configManager = configManager,
                                            saveCoordinator = saveCoordinator,
                                            showNotification = ::showNotification,
                                            navigateToMnnModelDownload = navigateToMnnModelDownload,
                                            keyPoolContent = {
                                                ApiKeyPoolSettingsSection(config, configManager, saveCoordinator, keyAvailabilityTester)
                                            },
                                        )
                                        CustomHeadersSettingsSection(config, configManager, saveCoordinator, ::showNotification)
                                    }
                                    ModelWorkspaceTab.PARAMETERS -> {
                                        ModelParametersSection(
                                            config = config,
                                            configManager = configManager,
                                            saveCoordinator = saveCoordinator,
                                            showNotification = ::showNotification,
                                            thinkingContent = { ThinkingConfigurationsSection(config, configManager, saveCoordinator, ::showNotification) },
                                        )
                                        AdvancedSettingsSection(config, configManager, saveCoordinator, ::showNotification)
                                    }
                                    ModelWorkspaceTab.SUMMARY -> ModelContextSummaryContent(
                                        config = config,
                                        configManager = configManager,
                                        saveCoordinator = saveCoordinator,
                                        showNotification = ::showNotification,
                                    )
                                    ModelWorkspaceTab.FUNCTIONS -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }

        ModelWorkspaceMenu(expanded = showMoreActions, onDismissRequest = { showMoreActions = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.model_workspace_config_preview)) },
                onClick = {
                    afterSaving {
                        val config = requireNotNull(configManager.getModelConfig(selectedConfigId))
                        configPreview = buildModelConfigPreview(config)
                        showMoreActions = false
                    }
                },
            )
        }
        configPreview?.let { preview ->
            ModelConfigPreviewDialog(preview = preview, onDismiss = { configPreview = null })
        }
        if (showConfigOrganizer) {
            ConfigOrganizerDialog(
                summaries = configSummaries,
                groups = configGroups,
                selectedGroupId = selectedGroupId,
                onGroupConfirmed = { groupId ->
                    afterSaving {
                        scope.launch { configManager.setSelectedConfigGroup(groupId) }
                    }
                },
                configManager = configManager,
                onSelectConfig = { configId -> afterSaving { selectedConfigId = configId } },
                onDismiss = { showConfigOrganizer = false },
                showNotification = ::showNotification
            )
        }
        // 重命名配置对话框
        if (showRenameConfigDialog) {
            AlertDialog(
                onDismissRequest = {
                    showRenameConfigDialog = false
                    renameConfigName = ""
                },
                title = {
                    Text(
                        stringResource(R.string.rename_model_config),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.rename_model_config_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = renameConfigName,
                            onValueChange = { renameConfigName = it },
                            label = {
                                Text(
                                    stringResource(R.string.model_config_name),
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameConfigName.isNotBlank()) {
                                scope.launch {
                                    configManager.updateConfigBase(
                                        selectedConfigId,
                                        renameConfigName
                                    )
                                    configNameMap[selectedConfigId] = renameConfigName
                                    showRenameConfigDialog = false
                                    renameConfigName = ""
                                    showNotification(context.getString(R.string.config_renamed), OKToast.Kind.Success)
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(stringResource(R.string.confirm_rename), fontSize = 13.sp) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRenameConfigDialog = false
                            renameConfigName = ""
                        }
                    ) { Text(stringResource(R.string.cancel_action), fontSize = 13.sp) }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }

        }
    }
}

private data class ThinkingRuleEditor(
    val id: String = "",
    val enabled: Boolean = true,
    val providerIds: String = "",
    val matchField: String = "modelContains",
    val matchValues: String = "",
    val control: String = "levels",
    val parameterLabel: String = "",
    val enabledActions: List<ThinkingActionEditor> = emptyList(),
    val disabledActions: List<ThinkingActionEditor> = emptyList(),
    val options: List<ThinkingOptionEditor> = listOf(
        ThinkingOptionEditor(id = "low", label = "low", value = "low"),
        ThinkingOptionEditor(id = "high", label = "high", value = "high"),
        ThinkingOptionEditor(id = "max", label = "max", value = "max")
    )
)

private data class ThinkingActionEditor(
    val path: String = "",
    val value: String = ""
)

private data class ThinkingOptionEditor(
    val id: String = "",
    val label: String = "",
    val path: String = "",
    val value: String = "",
    val editorKey: String = UUID.randomUUID().toString()
)

private val thinkingControlChoices =
    listOf("levels" to "多档滑块", "toggle_only" to "仅开关")

private val thinkingMatchFieldChoices =
    listOf(
        "modelContains" to "模型包含",
        "modelPrefix" to "模型前缀",
        "modelSuffix" to "模型后缀",
        "modelRegex" to "模型正则",
        "firstSegment" to "斜杠前段",
        "lastSegmentPrefix" to "后段前缀",
        "lastSegmentContains" to "后段包含",
        "lastSegmentRegex" to "后段正则",
        "endpointSuffix" to "端点后缀"
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ThinkingConfigurationsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    saveCoordinator: ModelConfigSaveCoordinator,
    showNotification: (String, OKToast.Kind) -> Unit
) {
    val latestConfig by rememberUpdatedState(config)
    val parsedRules = remember(config.id, config.thinkingConfigurations) {
        runCatching { parseThinkingRuleEditors(config.thinkingConfigurations) }
    }
    var rules by remember(config.id, config.thinkingConfigurations) {
        mutableStateOf(parsedRules.getOrElse { emptyList() })
    }
    var configurationError by remember(config.id, config.thinkingConfigurations) {
        mutableStateOf(parsedRules.exceptionOrNull()?.message)
    }
    var editingRuleIndex by rememberSaveable(config.id) { mutableStateOf<Int?>(null) }
    var editingRule by remember(config.id) { mutableStateOf<ThinkingRuleEditor?>(null) }
    val saveFailedText = stringResource(R.string.save_failed)
    val invalidConfigText = stringResource(R.string.thinking_config_invalid_json)
    val saveMutex = remember(config.id) { Mutex() }
    val enabledRuleCount = rules.count { it.enabled }
    val controlSummary = rules.map { rule ->
        thinkingControlChoices.firstOrNull { it.first == rule.control }?.second ?: rule.control
    }.distinct().joinToString(" / ").ifEmpty { "未配置" }


    val latestRules by rememberUpdatedState(rules)
    suspend fun persistConfiguration(value: String) {
        val validationMessage = try {
            ThinkingQualityMappingRegistry.validateConfigurations(value)
            null
        } catch (error: Exception) {
            error.message ?: invalidConfigText
        }
        if (validationMessage != null) {
            configurationError = validationMessage
            throw IllegalArgumentException(validationMessage)
        }
        configurationError = null
        saveMutex.withLock {
            withContext(Dispatchers.IO) {
                configManager.updateThinkingConfigurations(latestConfig.id, value)
                EnhancedAIService.refreshAllServices(configManager.appContext)
            }
        }
    }

    RegisterModelConfigSaveAction(
        coordinator = saveCoordinator,
        key = "thinking-config:${config.id}",
        action = { _ -> persistConfiguration(serializeThinkingRuleEditors(latestRules)) }
    )
    DebouncedModelConfigAutoSaveEffect(
        effectKey = config.id,
        valueProvider = { serializeThinkingRuleEditors(rules) },
        persist = { value -> persistConfiguration(value) },
        onError = { e -> showNotification(e.message ?: saveFailedText, OKToast.Kind.Error) }
    )

    ModelWorkspaceSection(
        title = stringResource(R.string.thinking_config_title),
        subtitle = stringResource(R.string.thinking_config_subtitle),
        stateKey = "thinking:${config.id}",
    ) {
    Text(
        text = "$enabledRuleCount/${rules.size} 条启用 · $controlSummary",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SettingsInfoBanner(text = stringResource(R.string.thinking_config_rule_order_hint))
    configurationError?.let { message ->
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (rules.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ) {
            Text("当前模型配置没有思考规则。", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        rules.forEachIndexed { index, rule ->
            ThinkingRulePreviewCard(
                index = index,
                totalCount = rules.size,
                rule = rule,
                onClick = {
                    editingRuleIndex = index
                    editingRule = rule
                },
                onMoveUp = {
                    if (index > 0) {
                        rules = rules.toMutableList().also {
                            val movedRule = it.removeAt(index)
                            it.add(index - 1, movedRule)
                        }
                    }
                },
                onMoveDown = {
                    if (index < rules.lastIndex) {
                        rules = rules.toMutableList().also {
                            val movedRule = it.removeAt(index)
                            it.add(index + 1, movedRule)
                        }
                    }
                },
            )
        }
    }
    TextButton(
        onClick = {
            editingRuleIndex = null
            editingRule = ThinkingRuleEditor(
                id = "custom-thinking-${rules.size + 1}",
                parameterLabel = "reasoning_effort"
            )
        },
        modifier = Modifier.align(Alignment.End)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("添加思考配置")
    }
    }

    val currentEditingIndex = editingRuleIndex
    val currentEditingRule = editingRule
    if (currentEditingRule != null) {
        val isNewRule = currentEditingIndex == null
        AlertDialog(
            onDismissRequest = {
                editingRuleIndex = null
                editingRule = null
            },
            title = {
                Text(
                    if (isNewRule) "新建思考配置" else thinkingRulePreviewTitle(currentEditingRule)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    configurationError?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    ThinkingRuleEditForm(
                        rule = currentEditingRule,
                        onRuleChange = { nextRule -> editingRule = nextRule }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (currentEditingIndex == null) {
                            rules = rules + currentEditingRule
                        } else if (currentEditingIndex in rules.indices) {
                            rules = rules.toMutableList().also {
                                it[currentEditingIndex] = currentEditingRule
                            }
                        }
                        editingRuleIndex = null
                        editingRule = null
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                if (currentEditingIndex == null) {
                    TextButton(
                        onClick = {
                            editingRuleIndex = null
                            editingRule = null
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                } else {
                    TextButton(
                        onClick = {
                            rules = rules.toMutableList().also { it.removeAt(currentEditingIndex) }
                            editingRuleIndex = null
                            editingRule = null
                        }
                    ) {
                        Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

private fun thinkingRulePreviewTitle(rule: ThinkingRuleEditor): String {
    return rule.matchValues
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("、")
        .ifEmpty { "所有模型" }
}

@Composable
private fun ThinkingRulePreviewCard(
    index: Int,
    totalCount: Int,
    rule: ThinkingRuleEditor,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val controlText = thinkingControlChoices.firstOrNull { it.first == rule.control }?.second ?: rule.control
    val detail = if (rule.control == "levels") "${rule.options.size} 档" else "开关"
    val parameterText = rule.parameterLabel.trim().ifEmpty { "未设置请求路径" }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = (index + 1).toString(),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = thinkingRulePreviewTitle(rule),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = "$controlText · $detail · $parameterText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.thinking_config_rule_move_up),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = index < totalCount - 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.thinking_config_rule_move_down),
                    modifier = Modifier.size(20.dp),
                )
            }
            Icon(
                imageVector = if (rule.enabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (rule.enabled) "已启用" else "已停用",
                tint = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ThinkingRuleEditForm(rule: ThinkingRuleEditor, onRuleChange: (ThinkingRuleEditor) -> Unit) {
    SettingsSwitchRow(
        title = "启用此配置",
        subtitle = "匹配到模型时写入思考参数",
        checked = rule.enabled,
        onCheckedChange = { onRuleChange(rule.copy(enabled = it)) }
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThinkingChoiceField(value = rule.control, label = "控件", choices = thinkingControlChoices, onValueChange = { onRuleChange(rule.copy(control = it)) }, modifier = Modifier.weight(1f))
        ThinkingChoiceField(value = rule.matchField, label = "匹配方式", choices = thinkingMatchFieldChoices, onValueChange = { onRuleChange(rule.copy(matchField = it)) }, modifier = Modifier.weight(1f))
    }
    SettingsTextField(title = "匹配模型", subtitle = "多个值用逗号分隔", value = rule.matchValues, onValueChange = { onRuleChange(rule.copy(matchValues = it)) }, placeholder = "glm-, deepseek, gemini-2.5")
    SettingsTextField(title = "默认请求路径", value = rule.parameterLabel, onValueChange = { onRuleChange(rule.copy(parameterLabel = it)) }, placeholder = "reasoning_effort 或 thinking.type")
    // 自定义规则编辑器不再暴露始终开启选项。
    ThinkingCollapsibleEditor(title = "开启 / 关闭时写入", subtitle = "按请求路径写入固定值", initiallyExpanded = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThinkingCompactActionsEditor(title = "开启时写入", actions = rule.enabledActions, onActionsChange = { onRuleChange(rule.copy(enabledActions = it)) })
            ThinkingCompactActionsEditor(title = "关闭时写入", actions = rule.disabledActions, onActionsChange = { onRuleChange(rule.copy(disabledActions = it)) })
        }
    }
    if (rule.control == "levels") {
        ThinkingCollapsibleEditor(title = "滑块档位", subtitle = "${rule.options.size} 个档位，决定滑块长度", initiallyExpanded = false) {
            ThinkingCompactOptionsEditor(options = rule.options, defaultPath = rule.parameterLabel, onOptionsChange = { onRuleChange(rule.copy(options = it)) })
        }
    }
}

@Composable
private fun ThinkingCollapsibleEditor(title: String, subtitle: String, initiallyExpanded: Boolean, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "收起" else "展开", modifier = Modifier.size(20.dp))
            }
            AnimatedVisibility(visible = expanded) { Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { content() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinkingChoiceField(
    value: String,
    label: String,
    choices: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = choices.firstOrNull { it.first == value }?.second ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(displayValue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ModelWorkspaceMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            choices.forEach { (choiceValue, choiceLabel) ->
                DropdownMenuItem(
                    text = { Text(choiceLabel) },
                    onClick = {
                        onValueChange(choiceValue)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ThinkingCompactActionsEditor(
    title: String,
    actions: List<ThinkingActionEditor>,
    onActionsChange: (List<ThinkingActionEditor>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onActionsChange(actions + ThinkingActionEditor()) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加")
            }
        }
        if (actions.isEmpty()) {
            Text("未配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions.forEachIndexed { index, action ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsTextField(
                        title = "路径",
                        value = action.path,
                        onValueChange = { path -> onActionsChange(actions.toMutableList().also { it[index] = action.copy(path = path) }) },
                        placeholder = "请求参数路径"
                    )
                    SettingsTextField(
                        title = "值",
                        value = action.value,
                        onValueChange = { value -> onActionsChange(actions.toMutableList().also { it[index] = action.copy(value = value) }) },
                        placeholder = "写入值"
                    )
                }
                IconButton(onClick = { onActionsChange(actions.toMutableList().also { it.removeAt(index) }) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ConfigOrganizerDialog(
    summaries: List<ModelConfigSummary>,
    groups: List<ModelConfigGroup>,
    selectedGroupId: String?,
    onGroupConfirmed: (String?) -> Unit,
    configManager: ModelConfigManager,
    onSelectConfig: (String) -> Unit,
    onDismiss: () -> Unit,
    showNotification: (String, OKToast.Kind) -> Unit
) {
    val scope = rememberCoroutineScope()
    val ungroupedName = stringResource(R.string.ungrouped)
    val saveGroupOrderFailed = stringResource(R.string.model_config_save_group_order_failed)
    val saveConfigOrderFailed = stringResource(R.string.model_config_save_order_failed)
    var rows by remember { mutableStateOf(emptyList<ConfigOrganizerRow>()) }
    var orderedGroups by remember(groups) { mutableStateOf(groups) }
    var collapsedGroups by remember { mutableStateOf<Set<String?>?>(null) }
    LaunchedEffect(configManager) {
        configManager.collapsedConfigGroupsFlow.collect { collapsedGroups = it }
    }
    val collapsedGroupIds = collapsedGroups.orEmpty()
    var pendingGroupId by remember(selectedGroupId) { mutableStateOf(selectedGroupId) }
    var editingGroup by remember { mutableStateOf<ModelConfigGroup?>(null) }
    var targetGroupForConfig by remember { mutableStateOf<String?>(null) }
    var creatingConfig by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var groupPendingDeletion by remember { mutableStateOf<ModelConfigGroup?>(null) }
    var nameInput by remember { mutableStateOf("") }

    fun buildRows(): List<ConfigOrganizerRow> = buildList {
        add(ConfigOrganizerRow("group:ungrouped", null))
        if (null !in collapsedGroupIds) {
            summaries.filter { it.groupId == null }.forEach {
                add(ConfigOrganizerRow("config:${it.id}", null, config = it))
            }
        }
        orderedGroups.forEach { group ->
            add(ConfigOrganizerRow("group:${group.id}", group.id, group = group))
            if (group.id !in collapsedGroupIds) {
                summaries.filter { it.groupId == group.id }.forEach {
                    add(ConfigOrganizerRow("config:${it.id}", group.id, config = it))
                }
            }
        }
    }

    LaunchedEffect(summaries, orderedGroups, collapsedGroups) {
        if (collapsedGroups != null) rows = buildRows()
    }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val moving = rows.getOrNull(from.index) ?: return@rememberReorderableLazyListState
        val target = rows.getOrNull(to.index) ?: return@rememberReorderableLazyListState
        if (moving.config == null) {
            val movingGroup = moving.group ?: return@rememberReorderableLazyListState
            val targetGroup = target.group ?: return@rememberReorderableLazyListState
            val reordered = orderedGroups.toMutableList().apply {
                val fromGroupIndex = indexOfFirst { it.id == movingGroup.id }
                val toGroupIndex = indexOfFirst { it.id == targetGroup.id }
                if (fromGroupIndex >= 0 && toGroupIndex >= 0) {
                    add(toGroupIndex, removeAt(fromGroupIndex))
                }
            }
            orderedGroups = reordered
            scope.launch {
                runCatching { configManager.reorderConfigGroups(reordered.map { it.id }) }
                    .onFailure { showNotification(it.message ?: saveGroupOrderFailed, OKToast.Kind.Error) }
            }
            return@rememberReorderableLazyListState
        }

        val updated = rows.toMutableList().apply {
            removeAt(from.index)
            val targetPosition = indexOfFirst { it.key == target.key }
            // 拖动库报告越过中心的目标项，向下移动时插在目标之后，确保能移动到列表末尾。
            val insertionIndex = when {
                target.config == null -> targetPosition + 1
                from.index < to.index -> targetPosition + 1
                else -> targetPosition
            }
            add(insertionIndex.coerceIn(0, size), moving)
        }
        rows = updated
        var currentGroupId: String? = null
        val visiblePlacements = buildList {
            updated.forEach { row ->
                if (row.config == null) currentGroupId = row.groupId
                else add(row.config.id to currentGroupId)
            }
        }
        val visibleIds = visiblePlacements.map { it.first }.toSet()
        val placements = visiblePlacements + summaries
            .filterNot { it.id in visibleIds }
            .map { it.id to it.groupId }
        scope.launch {
            runCatching { configManager.updateConfigOrganization(orderedGroups, placements) }
                .onFailure { showNotification(it.message ?: saveConfigOrderFailed, OKToast.Kind.Error) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.model_config_manage_organization),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            editingGroup = null
                            targetGroupForConfig = null
                            creatingConfig = false
                            nameInput = ""
                            showNameDialog = true
                        }
                    ) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = stringResource(R.string.model_config_new_group)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { _, row ->
                        if (row.config == null) {
                            ReorderableItem(reorderableState, key = row.key) { isDragging ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        when {
                                            isDragging -> MaterialTheme.colorScheme.surfaceVariant
                                            pendingGroupId == row.groupId -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .clickable { pendingGroupId = row.groupId }
                                    .padding(top = 10.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (row.groupId in collapsedGroupIds) Icons.Default.KeyboardArrowRight
                                    else Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.model_config_toggle_group),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            scope.launch {
                                                configManager.setConfigGroupCollapsed(
                                                    row.groupId, row.groupId !in collapsedGroupIds
                                                )
                                            }
                                        }
                                )
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        R.string.model_config_group_item_count,
                                        row.group?.name ?: ungroupedName,
                                        summaries.count { it.groupId == row.groupId }
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        editingGroup = null
                                        targetGroupForConfig = row.groupId
                                        creatingConfig = true
                                        nameInput = ""
                                        showNameDialog = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) { Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(
                                            R.string.model_config_add_to_group
                                        ),
                                        modifier = Modifier.size(18.dp)
                                    ) }
                                row.group?.let { group ->
                                    IconButton(
                                        onClick = {
                                            editingGroup = group
                                            targetGroupForConfig = null
                                            creatingConfig = false
                                            nameInput = group.name
                                            showNameDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(
                                                R.string.model_config_rename_group
                                            ),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { groupPendingDeletion = group },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(
                                                R.string.model_config_delete_group
                                            ),
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                row.group?.let {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = stringResource(R.string.model_config_drag_group),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp).longPressDraggableHandle().padding(8.dp)
                                    )
                                }
                            }
                            }
                        } else {
                            ReorderableItem(reorderableState, key = row.key) { isDragging ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .clickable { pendingGroupId = row.groupId }
                                        .padding(start = 28.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(row.config.name, modifier = Modifier.weight(1f))
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = stringResource(R.string.model_config_drag_config),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp).longPressDraggableHandle().padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                val pendingGroupName =
                    orderedGroups.firstOrNull { it.id == pendingGroupId }?.name ?: ungroupedName
                Text(
                    stringResource(R.string.model_config_current_selection, pendingGroupName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel_action))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // 确认分组只改变筛选；由页面按当前绑定和组内记忆选择配置。
                            onGroupConfirmed(pendingGroupId)
                            onDismiss()
                        }
                    ) { Text(stringResource(R.string.confirm_action)) }
                }
            }
        }
    }

    groupPendingDeletion?.let { group ->
        AlertDialog(
            onDismissRequest = { groupPendingDeletion = null },
            title = { Text(stringResource(R.string.model_config_delete_group)) },
            text = {
                Text(
                    stringResource(
                        R.string.model_config_delete_group_confirmation,
                        group.name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        groupPendingDeletion = null
                        scope.launch { configManager.deleteConfigGroup(group.id) }
                    }
                ) {
                    Text(stringResource(R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { groupPendingDeletion = null }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = {
                Text(
                    when {
                        editingGroup != null ->
                            stringResource(R.string.model_config_rename_group)
                        creatingConfig -> stringResource(R.string.new_model_config)
                        else -> stringResource(R.string.model_config_new_group)
                    }
                )
            },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.model_config_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nameInput.isNotBlank(),
                    onClick = {
                        scope.launch {
                            when {
                                editingGroup != null -> configManager.renameConfigGroup(editingGroup!!.id, nameInput)
                                creatingConfig -> {
                                    val id = configManager.createConfig(nameInput, targetGroupForConfig)
                                    onSelectConfig(id)
                                }
                                else -> configManager.createConfigGroup(nameInput)
                            }
                            showNameDialog = false
                        }
                    }
                ) { Text(stringResource(R.string.confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }
}

@Composable
private fun ThinkingCompactOptionsEditor(
    options: List<ThinkingOptionEditor>,
    defaultPath: String,
    onOptionsChange: (List<ThinkingOptionEditor>) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onOptionsChange(
            options.toMutableList().also {
                it.add(to.index, it.removeAt(from.index))
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("滑块档位", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onOptionsChange(options + ThinkingOptionEditor(path = defaultPath)) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加档位")
            }
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(
                items = options,
                key = { _, option -> option.editorKey },
            ) { index, option ->
                ReorderableItem(
                    reorderableState,
                    key = option.editorKey,
                    animateItemModifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                        placementSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                    )
                ) { isDragging ->
                    var expanded by rememberSaveable(option.editorKey) {
                        mutableStateOf(index == 0)
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isDragging) {
                                    Modifier.shadow(8.dp, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            ),
                        shape = RoundedCornerShape(8.dp),
                        // Keep the dragged item opaque so its elevation shadow remains clean over translucent surfaces.
                        color = if (isDragging) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                        }
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "拖动排序",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .longPressDraggableHandle(),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { expanded = !expanded }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        "档位 ${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!expanded && option.label.isNotBlank()) {
                                        Text(
                                            option.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onOptionsChange(options.toMutableList().also { it.removeAt(index) }) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(
                                    onClick = { expanded = !expanded },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (expanded) "收起" else "展开",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            AnimatedVisibility(visible = expanded) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SettingsTextField(
                                        title = "显示名",
                                        value = option.label,
                                        onValueChange = { value -> onOptionsChange(options.toMutableList().also { it[index] = option.copy(label = value) }) },
                                        placeholder = "例如：高"
                                    )
                                    SettingsTextField(
                                        title = "写入路径",
                                        value = option.path,
                                        onValueChange = { value -> onOptionsChange(options.toMutableList().also { it[index] = option.copy(path = value) }) },
                                        placeholder = defaultPath
                                    )
                                    SettingsTextField(
                                        title = "写入值",
                                        value = option.value,
                                        onValueChange = { value -> onOptionsChange(options.toMutableList().also { it[index] = option.copy(value = value) }) },
                                        placeholder = "例如：high"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseThinkingRuleEditors(raw: String): List<ThinkingRuleEditor> {
    val array = thinkingRulesArray(raw)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(item.toThinkingRuleEditor(index))
        }
    }
}

private fun serializeThinkingRuleEditors(rules: List<ThinkingRuleEditor>): String {
    val array = JSONArray()
    rules.forEachIndexed { index, rule ->
        val ruleObject = JSONObject()
        ruleObject.put("id", rule.id.trim().ifEmpty { "custom-thinking-${index + 1}" })
        if (!rule.enabled) {
            ruleObject.put("enabled", false)
        }
        thinkingStringArray(splitThinkingCsv(rule.providerIds)).takeIf { it.length() > 0 }?.let {
            ruleObject.put("providers", it)
        }
        splitThinkingCsv(rule.matchValues).takeIf { it.isNotEmpty() }?.let { values ->
            ruleObject.put("match", JSONObject().put(rule.matchField, thinkingStringArray(values)))
        }
        ruleObject.put("control", rule.control)
        rule.parameterLabel.trim().takeIf { it.isNotEmpty() }?.let {
            ruleObject.put("parameterLabel", it)
        }
        thinkingActionsArray(rule.enabledActions).takeIf { it.length() > 0 }?.let {
            ruleObject.put("enable", it)
        }
        thinkingActionsArray(rule.disabledActions).takeIf { it.length() > 0 }?.let {
            ruleObject.put("disable", it)
        }
        if (rule.control == "levels") {
            val optionsArray = JSONArray()
            rule.options.forEachIndexed { optionIndex, option ->
                val valueText = option.value.trim()
                val path = option.path.trim().ifEmpty { rule.parameterLabel.trim() }
                if (option.id.isBlank() && option.label.isBlank() && path.isBlank() && valueText.isBlank()) {
                    return@forEachIndexed
                }
                val optionObject = JSONObject()
                optionObject.put(
                    "id",
                    option.id.trim().ifEmpty {
                        valueText.ifEmpty { "option-${optionIndex + 1}" }
                    }
                )
                option.label.trim().takeIf { it.isNotEmpty() }?.let { optionObject.put("label", it) }
                path.takeIf { it.isNotEmpty() }?.let { optionObject.put("path", it) }
                optionObject.put("value", parseThinkingEditorValue(valueText))
                optionsArray.put(optionObject)
            }
            ruleObject.put("options", optionsArray)
        }
        array.put(ruleObject)
    }
    return array.toString()
}

private fun thinkingRulesArray(raw: String): JSONArray {
    val text = raw.trim().ifEmpty { "[]" }
    return when {
        text.startsWith("[") -> JSONArray(text)
        text.startsWith("{") -> {
            val objectValue = JSONObject(text)
            objectValue.optJSONArray("rules") ?: JSONArray().put(objectValue)
        }
        else -> JSONArray(text)
    }
}

private fun JSONObject.toThinkingRuleEditor(index: Int): ThinkingRuleEditor {
    val matchPair = firstThinkingMatchPair()
    return ThinkingRuleEditor(
        id = optString("id", "custom-thinking-${index + 1}"),
        enabled = optBoolean("enabled", true),
        providerIds = (stringArrayValues("providers") + stringArrayValues("providerTypeIds"))
            .distinct()
            .joinToString(", "),
        matchField = matchPair.first,
        matchValues = matchPair.second.joinToString(", "),
        control = when (optString("control", "levels")) {
            "toggle", "toggle_only" -> "toggle_only"
            else -> "levels"
        },
        parameterLabel = optString("parameterLabel", optString("label", "")),
        enabledActions = actionEditors("enable", "enabledActions", "on"),
        disabledActions = actionEditors("disable", "disabledActions", "off"),
        options = optionEditors()
    )
}

private fun JSONObject.firstThinkingMatchPair(): Pair<String, List<String>> {
    val match = optJSONObject("match") ?: JSONObject()
    thinkingMatchFieldChoices.forEach { (field, _) ->
        val values = match.stringArrayValues(field) + stringArrayValues(field)
        if (values.isNotEmpty()) return field to values
    }
    return "modelContains" to emptyList()
}

private fun JSONObject.actionEditors(vararg keys: String): List<ThinkingActionEditor> =
    keys.flatMap { key -> actionEditorsFromValue(opt(key)) }

private fun actionEditorsFromValue(value: Any?): List<ThinkingActionEditor> {
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                val item = value.optJSONObject(index) ?: continue
                item.toActionEditor()?.let(::add)
            }
        }
        is JSONObject -> listOfNotNull(value.toActionEditor())
        else -> emptyList()
    }
}

private fun JSONObject.toActionEditor(): ThinkingActionEditor? {
    val path = optString("path", "").trim()
    if (path.isEmpty()) return null
    return ThinkingActionEditor(path = path, value = thinkingEditorValueText(opt("value")))
}

private fun JSONObject.optionEditors(): List<ThinkingOptionEditor> {
    val array = optJSONArray("options") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val option = array.optJSONObject(index) ?: continue
            add(
                ThinkingOptionEditor(
                    id = option.optString("id", ""),
                    label = option.optString("label", option.optString("id", "")),
                    path = option.optString("path", ""),
                    value = thinkingEditorValueText(option.opt("value"))
                )
            )
        }
    }
}

private fun JSONObject.stringArrayValues(key: String): List<String> {
    val value = opt(key)
    return when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                value.optString(index, "").trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        is String -> splitThinkingCsv(value)
        else -> emptyList()
    }
}

private fun thinkingStringArray(values: List<String>): JSONArray =
    JSONArray().apply {
        values.forEach { put(it) }
    }

private fun thinkingActionsArray(actions: List<ThinkingActionEditor>): JSONArray {
    val array = JSONArray()
    actions.forEach { action ->
        val path = action.path.trim()
        if (path.isNotEmpty()) {
            array.put(
                JSONObject()
                    .put("path", path)
                    .put("value", parseThinkingEditorValue(action.value))
            )
        }
    }
    return array
}

private fun splitThinkingCsv(value: String): List<String> =
    value.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

private fun parseThinkingEditorValue(value: String): Any {
    val text = value.trim()
    if (text.equals("true", ignoreCase = true)) return true
    if (text.equals("false", ignoreCase = true)) return false
    if (text.equals("null", ignoreCase = true)) return JSONObject.NULL
    text.toIntOrNull()?.let { return it }
    text.toLongOrNull()?.let { return it }
    if (text.contains('.')) {
        text.toDoubleOrNull()?.let { return it }
    }
    if (text.startsWith("{") && text.endsWith("}")) {
        runCatching { JSONObject(text) }.onSuccess { return it }
    }
    if (text.startsWith("[") && text.endsWith("]")) {
        runCatching { JSONArray(text) }.onSuccess { return it }
    }
    return text
}

private fun thinkingEditorValueText(value: Any?): String =
    when (value) {
        null -> ""
        JSONObject.NULL -> "null"
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomHeadersSettingsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    saveCoordinator: ModelConfigSaveCoordinator,
    showNotification: (String, OKToast.Kind) -> Unit
) {
    val latestConfig by rememberUpdatedState(config)
    var headers by remember(config.id) { mutableStateOf(parseHeaderEntries(config.customHeaders)) }
    var headersExpanded by rememberSaveable(config.id) { mutableStateOf(false) }
    var showHeaderPresetsMenu by remember { mutableStateOf(false) }
    val saveFailedText = stringResource(R.string.save_failed)
    val saveMutex = remember(config.id) { Mutex() }

    LaunchedEffect(config.id, config.customHeaders) {
        headers = parseHeaderEntries(config.customHeaders)
    }

    suspend fun persistHeaders(serializedHeaders: String) {
        saveMutex.withLock {
            withContext(Dispatchers.IO) {
                configManager.updateCustomHeaders(latestConfig.id, serializedHeaders)
                EnhancedAIService.refreshAllServices(configManager.appContext)
            }
        }
    }

    RegisterModelConfigSaveAction(
        coordinator = saveCoordinator,
        key = "headers:${config.id}",
        action = { _ ->
            persistHeaders(serializeHeaderEntries(headers))
        }
    )

    DebouncedModelConfigAutoSaveEffect(
        effectKey = config.id,
        valueProvider = { serializeHeaderEntries(headers) },
        persist = { serializedHeaders -> persistHeaders(serializedHeaders) },
        onError = { e ->
            showNotification(e.message ?: saveFailedText, OKToast.Kind.Error)
        }
    )

    val configuredHeadersCount = headers.count { it.first.trim().isNotEmpty() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { headersExpanded = !headersExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_custom_headers),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_custom_headers_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (configuredHeadersCount > 0) {
                    Text(
                        text =
                            stringResource(
                                R.string.headers_configured_count,
                                configuredHeadersCount
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector =
                        if (headersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = headersExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = { showHeaderPresetsMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.headers_load_preset))
                            }

                            OutlinedButton(onClick = { headers = headers + ("" to "") }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.headers_add_header))
                            }
                        }

                        ModelWorkspaceMenu(
                            expanded = showHeaderPresetsMenu,
                            onDismissRequest = { showHeaderPresetsMenu = false }
                        ) {
                            headerPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(preset.nameResId)) },
                                    onClick = {
                                        val mergedHeaders =
                                            headers.associate { it.first to it.second }.toMutableMap()
                                        mergedHeaders.putAll(preset.headers)
                                        headers = mergedHeaders.toList()
                                        showHeaderPresetsMenu = false
                                    }
                                )
                            }
                        }
                    }

                    headers.forEachIndexed { index, header ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = header.first,
                                onValueChange = { newValue ->
                                    headers =
                                        headers.toMutableList().also {
                                            it[index] = newValue to header.second
                                        }
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.headers_key_label)) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = header.second,
                                onValueChange = { newValue ->
                                    headers =
                                        headers.toMutableList().also {
                                            it[index] = header.first to newValue
                                        }
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.headers_value_label)) },
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    headers = headers.toMutableList().apply { removeAt(index) }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription =
                                        stringResource(R.string.headers_delete_header)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ConnectionTestItem(
    val labelResId: Int,
    val type: ModelConnectionTestType,
    val outcome: ModelConnectionTestOutcome,
    val error: String? = null
) {
    fun statusText(context: android.content.Context): String {
        return when (outcome) {
            ModelConnectionTestOutcome.PASSED ->
                context.getString(
                    when (type) {
                        ModelConnectionTestType.IMAGE ->
                            R.string.test_media_image_understood
                        ModelConnectionTestType.AUDIO ->
                            R.string.test_media_audio_understood
                        ModelConnectionTestType.VIDEO ->
                            R.string.test_media_video_understood
                        else -> R.string.test_connection_success
                    }
                )
            ModelConnectionTestOutcome.UNVERIFIED ->
                context.getString(
                    when (type) {
                        ModelConnectionTestType.IMAGE ->
                            R.string.test_media_image_unverified
                        ModelConnectionTestType.AUDIO ->
                            R.string.test_media_audio_unverified
                        ModelConnectionTestType.VIDEO ->
                            R.string.test_media_video_unverified
                        else -> R.string.test_media_unverified
                    }
                )
            ModelConnectionTestOutcome.FAILED ->
                context.getString(
                    R.string.test_connection_failed,
                    error ?: ""
                )
        }
    }
}

private fun ModelConnectionTestType.toLabelResId(): Int {
    return when (this) {
        ModelConnectionTestType.CHAT -> R.string.test_item_chat
        ModelConnectionTestType.TOOL_CALL -> R.string.test_item_toolcall
        ModelConnectionTestType.IMAGE -> R.string.test_item_image
        ModelConnectionTestType.AUDIO -> R.string.test_item_audio
        ModelConnectionTestType.VIDEO -> R.string.test_item_video
    }
}
