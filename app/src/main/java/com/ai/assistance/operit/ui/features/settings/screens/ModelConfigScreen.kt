package com.ai.assistance.operit.ui.features.settings.screens

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.api.CodexAuthManager
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.ApiKeyPoolAvailabilityTester
import com.ai.assistance.operit.api.chat.llmprovider.ChatConfigReadiness
import com.ai.assistance.operit.api.chat.llmprovider.ChatConfigReadinessIssue
import com.ai.assistance.operit.api.chat.llmprovider.ModelConfigConnectionTester
import com.ai.assistance.operit.api.chat.llmprovider.ModelConnectionTestType
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityMappingRegistry
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.settings.DebouncedModelConfigAutoSaveEffect
import com.ai.assistance.operit.ui.features.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.ui.features.settings.rememberModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.settings.sections.AdvancedSettingsSection
import com.ai.assistance.operit.ui.features.settings.sections.ModelApiSettingsSection
import com.ai.assistance.operit.ui.features.settings.sections.ModelParametersSection
import com.ai.assistance.operit.ui.features.settings.sections.SettingsInfoBanner
import com.ai.assistance.operit.ui.features.settings.sections.SettingsSectionHeader
import com.ai.assistance.operit.ui.features.settings.sections.SettingsSwitchRow
import com.ai.assistance.operit.ui.features.settings.sections.SettingsTextField
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgAiProviderRegistry
import com.ai.assistance.operit.ui.main.navigation.RegisterRouteBackGuard
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class HeaderPreset(val nameResId: Int, val headers: Map<String, String>)

enum class ModelConfigEntryMode {
    STANDARD,
    CHAT_ONBOARDING
}

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelConfigScreen(
    navigateToMnnModelDownload: (() -> Unit)? = null,
    entryMode: ModelConfigEntryMode = ModelConfigEntryMode.STANDARD
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configManager = remember { ModelConfigManager(context) }
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val codexAuthManager = remember { CodexAuthManager.getInstance(context) }
    val codexAuthState by codexAuthManager.authState.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val saveCoordinator = rememberModelConfigSaveCoordinator()
    val snackbarHostState = remember { SnackbarHostState() }

    // 配置状态
    val configList = configManager.configListFlow.collectAsState(initial = listOf("default")).value
    // 进入页面时默认选中“对话功能”当前绑定的模型配置
    var selectedConfigId by remember { mutableStateOf(ModelConfigManager.DEFAULT_CONFIG_ID) }
    val selectedConfig = remember { mutableStateOf<ModelConfigData?>(null) }
    val keyAvailabilityTester =
        remember(selectedConfigId) {
            ApiKeyPoolAvailabilityTester(selectedConfigId, configManager)
        }
    var hasInitializedSelection by remember { mutableStateOf(false) }
    var isCompletingOnboarding by remember { mutableStateOf(false) }

    // 配置名称映射
    val configNameMap = remember { mutableStateMapOf<String, String>() }

    // UI状态
    var showAddConfigDialog by remember { mutableStateOf(false) }
    var showRenameConfigDialog by remember { mutableStateOf(false) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var newConfigName by remember { mutableStateOf("") }
    var renameConfigName by remember { mutableStateOf("") }
    var confirmMessage by remember { mutableStateOf("") }

    // 连接测试状态
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<ConnectionTestItem>?>(null) }
    var connectionTestJob by remember { mutableStateOf<Job?>(null) }
    var activeConnectionTestService by remember { mutableStateOf<AIService?>(null) }

    DisposableEffect(keyAvailabilityTester) {
        onDispose { keyAvailabilityTester.close() }
    }

    // 初始化配置，并默认定位到“对话功能模型”所使用的配置
    LaunchedEffect(Unit) {
        val chatConfigId = functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
        val availableConfigIds = configManager.configListFlow.first()
        selectedConfigId =
            availableConfigIds.firstOrNull { it == chatConfigId }
                ?: availableConfigIds.firstOrNull()
                ?: ModelConfigManager.DEFAULT_CONFIG_ID
        hasInitializedSelection = true
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
        configManager.getModelConfigFlow(selectedConfigId).collect { config ->
            selectedConfig.value = config
        }
    }

    // 显示通知消息
    fun showNotification(message: String) {
        confirmMessage = message
        showSaveSuccessMessage = true
        scope.launch {
            kotlinx.coroutines.delay(3000)
            showSaveSuccessMessage = false
        }
    }

    fun showOnboardingError(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    if (entryMode == ModelConfigEntryMode.CHAT_ONBOARDING) {
        RegisterRouteBackGuard {
            if (!hasInitializedSelection || isCompletingOnboarding) {
                return@RegisterRouteBackGuard false
            }

            val targetConfigId = selectedConfigId
            isCompletingOnboarding = true
            try {
                saveCoordinator.flushAll(showSuccess = false)
                if (selectedConfigId != targetConfigId) {
                    showOnboardingError(
                        context.getString(R.string.onboarding_config_apply_failed)
                    )
                    return@RegisterRouteBackGuard false
                }

                val targetConfig = configManager.getModelConfig(targetConfigId)
                if (targetConfig == null) {
                    showOnboardingError(
                        context.getString(R.string.onboarding_config_not_found)
                    )
                    return@RegisterRouteBackGuard false
                }

                val currentMapping =
                    functionalConfigManager.getConfigMappingForFunction(FunctionType.CHAT)
                val targetModelIndex =
                    if (currentMapping.configId == targetConfigId) {
                        currentMapping.modelIndex
                    } else {
                        0
                    }
                val registeredPluginProviderIds =
                    ToolPkgAiProviderRegistry.list().mapTo(mutableSetOf()) { it.providerId }
                val readiness =
                    ChatConfigReadiness.evaluate(
                        config = targetConfig,
                        modelIndex = targetModelIndex,
                        registeredPluginProviderIds = registeredPluginProviderIds,
                        codexAuthenticated = codexAuthState != null,
                    )
                if (!readiness.isReady) {
                    val messageResId =
                        when (readiness.issue) {
                            ChatConfigReadinessIssue.PROVIDER_MISSING ->
                                R.string.onboarding_config_provider_missing
                            ChatConfigReadinessIssue.PROVIDER_UNAVAILABLE ->
                                R.string.onboarding_config_provider_unavailable
                            ChatConfigReadinessIssue.ENDPOINT_INVALID ->
                                R.string.onboarding_config_endpoint_invalid
                            ChatConfigReadinessIssue.MODEL_MISSING ->
                                R.string.onboarding_config_model_missing
                            ChatConfigReadinessIssue.CODEX_LOGIN_REQUIRED ->
                                R.string.onboarding_config_codex_login_required
                            ChatConfigReadinessIssue.API_KEY_MISSING ->
                                R.string.onboarding_config_api_key_missing
                            ChatConfigReadinessIssue.API_KEY_INVALID ->
                                R.string.onboarding_config_api_key_invalid
                            null -> R.string.onboarding_config_apply_failed
                        }
                    showOnboardingError(context.getString(messageResId))
                    return@RegisterRouteBackGuard false
                }

                functionalConfigManager.setConfigForFunction(
                    FunctionType.CHAT,
                    targetConfigId,
                    targetModelIndex
                )
                val savedMapping =
                    functionalConfigManager.getConfigMappingForFunction(FunctionType.CHAT)
                check(
                    savedMapping.configId == targetConfigId &&
                        savedMapping.modelIndex == targetModelIndex
                )
                EnhancedAIService.refreshServiceForFunction(
                    context.applicationContext,
                    FunctionType.CHAT
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("ModelConfigScreen", "首次引导配置保存或绑定失败", e)
                showOnboardingError(
                    context.getString(R.string.onboarding_config_apply_failed)
                )
                false
            } finally {
                isCompletingOnboarding = false
            }
        }
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

    // 主界面内容
    CustomScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.select_model_config),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            OutlinedButton(
                                onClick = { showAddConfigDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp),
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    stringResource(R.string.new_action),
                                    fontSize = 12.sp,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        val selectedConfigName =
                            configNameMap[selectedConfigId]
                                ?: stringResource(R.string.default_profile)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            tonalElevation = 0.5.dp,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedConfigName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                @OptIn(ExperimentalAnimationApi::class)
                                AnimatedContent(
                                    targetState = isDropdownExpanded,
                                    transitionSpec = {
                                        fadeIn() + scaleIn() with fadeOut() + scaleOut()
                                    }
                                ) { expanded ->
                                    Icon(
                                        if (expanded) Icons.Default.KeyboardArrowUp
                                        else Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.select_config),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        FlowRow(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedConfigId != "default") {
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

                                                selectedConfigId = configManager.configListFlow
                                                    .first()
                                                    .firstOrNull()
                                                    ?: ModelConfigManager.DEFAULT_CONFIG_ID
                                                showNotification(context.getString(R.string.config_deleted))
                                            } catch (e: Exception) {
                                                AppLogger.e(
                                                    "ModelConfigScreen",
                                                    "删除模型配置失败: $deletingConfigId",
                                                    e,
                                                )
                                                showNotification(context.getString(R.string.save_failed))
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
                                                        val result =
                                                            if (item.success) {
                                                                Result.success(Unit)
                                                            } else {
                                                                Result.failure(
                                                                    Exception(item.error ?: "Unknown error")
                                                                )
                                                            }
                                                        results.add(
                                                            ConnectionTestItem(
                                                                labelResId = item.type.toLabelResId(),
                                                                result = result
                                                            )
                                                        )
                                                    }
                                                } ?: run {
                                                    results.add(
                                                        ConnectionTestItem(
                                                            R.string.test_item_chat,
                                                            Result.failure<Unit>(
                                                                Exception(
                                                                    context.getString(
                                                                        R.string.no_config_selected
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                results.add(
                                                    ConnectionTestItem(
                                                        R.string.test_item_chat,
                                                        Result.failure<Unit>(e)
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
                                            val isSuccess = item.result.isSuccess
                                            val statusText =
                                                if (isSuccess) {
                                                    context.getString(R.string.test_connection_success)
                                                } else {
                                                    context.getString(
                                                        R.string.test_connection_failed,
                                                        item.result.exceptionOrNull()?.message ?: ""
                                                    )
                                                }
                                            val contentColor =
                                                if (isSuccess) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error
                                            val icon =
                                                if (isSuccess) Icons.Default.CheckCircle
                                                else Icons.Default.Warning

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
                                                    if (isSuccess) {
                                                        Text(
                                                            text = statusText,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = contentColor
                                                        )
                                                    }
                                                }
                                                if (!isSuccess) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = statusText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = contentColor
                                                    )
                                                }
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

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier.width(280.dp),
                        properties = PopupProperties(focusable = true)
                    ) {
                        configList.forEach { configId ->
                            val configName =
                                configNameMap[configId] ?: stringResource(R.string.unnamed_profile)
                            val isSelected = configId == selectedConfigId

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = configName,
                                        fontWeight =
                                            if (isSelected) FontWeight.SemiBold
                                            else FontWeight.Normal,
                                        color =
                                            if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon =
                                    if (isSelected) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = stringResource(R.string.selected_desc),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                onClick = {
                                    selectedConfigId = configId
                                    isDropdownExpanded = false
                                },
                                colors =
                                    MenuDefaults.itemColors(
                                        textColor =
                                            if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                    ),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            if (configId != configList.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }

            selectedConfig.value?.let { config ->
                item {
                    ModelApiSettingsSection(
                        config = config,
                        configManager = configManager,
                        saveCoordinator = saveCoordinator,
                        showNotification = { message -> showNotification(message) },
                        navigateToMnnModelDownload = navigateToMnnModelDownload
                    )
                }

                item {
                    ContextSummarySettingsSection(
                        config = config,
                        configManager = configManager,
                        scope = scope,
                        showNotification = { message -> showNotification(message) }
                    )
                }

                item {
                    ThinkingConfigurationsSection(
                        config = config,
                        configManager = configManager,
                        saveCoordinator = saveCoordinator,
                        showNotification = { message -> showNotification(message) }
                    )
                }

                item {
                    ModelParametersSection(
                        config = config,
                        configManager = configManager,
                        showNotification = { message -> showNotification(message) }
                    )
                }

                item {
                    CustomHeadersSettingsSection(
                        config = config,
                        configManager = configManager,
                        saveCoordinator = saveCoordinator,
                        showNotification = { message -> showNotification(message) }
                    )
                }

                item {
                    AdvancedSettingsSection(
                        config = config,
                        configManager = configManager,
                        saveCoordinator = saveCoordinator,
                        keyAvailabilityTester = keyAvailabilityTester,
                        showNotification = { message -> showNotification(message) }
                    )
                }
            }

            if (showSaveSuccessMessage) {
                item {
                    AnimatedVisibility(
                        visible = showSaveSuccessMessage,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = confirmMessage,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // 新建配置对话框
        if (showAddConfigDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddConfigDialog = false
                    newConfigName = ""
                },
                title = {
                    Text(
                        stringResource(R.string.new_model_config),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.new_model_config_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newConfigName,
                            onValueChange = { newConfigName = it },
                            label = {
                                Text(
                                    stringResource(R.string.model_config_name),
                                    fontSize = 12.sp
                                )
                            },
                            placeholder = {
                                Text(
                                    stringResource(R.string.model_config_name_placeholder),
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
                            if (newConfigName.isNotBlank()) {
                                scope.launch {
                                    val configId = configManager.createConfig(newConfigName)
                                    selectedConfigId = configId
                                    showAddConfigDialog = false
                                    newConfigName = ""
                                    showNotification(context.getString(R.string.new_config_created))
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(stringResource(R.string.create_action), fontSize = 13.sp) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddConfigDialog = false
                            newConfigName = ""
                        }
                    ) { Text(stringResource(R.string.cancel_action), fontSize = 13.sp) }
                },
                shape = RoundedCornerShape(12.dp)
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
                                    showNotification(context.getString(R.string.config_renamed))
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

        if (isCompletingOnboarding) {
            Surface(
                modifier = Modifier.matchParentSize().clickable(enabled = true) {},
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
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
    val required: Boolean = false,
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
    val value: String = ""
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
        "lastSegmentRegex" to "后段正则"
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ThinkingConfigurationsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    saveCoordinator: ModelConfigSaveCoordinator,
    showNotification: (String) -> Unit
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
    var sectionExpanded by rememberSaveable(config.id) { mutableStateOf(false) }
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
        onError = { e -> showNotification(e.message ?: saveFailedText) }
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { sectionExpanded = !sectionExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.thinking_config_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.thinking_config_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    imageVector = if (sectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (sectionExpanded) "收起" else "展开",
                    modifier = Modifier.size(24.dp)
                )
            }
            AnimatedVisibility(visible = sectionExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
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
    SettingsSwitchRow(title = "始终开启思考", subtitle = "即使滑块关闭，也写入开启参数", checked = rule.required, onCheckedChange = { onRuleChange(rule.copy(required = it)) })
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
        ExposedDropdownMenu(
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
private fun ThinkingCompactOptionsEditor(
    options: List<ThinkingOptionEditor>,
    defaultPath: String,
    onOptionsChange: (List<ThinkingOptionEditor>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("滑块档位", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onOptionsChange(options + ThinkingOptionEditor(path = defaultPath)) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加档位")
            }
        }
        options.forEachIndexed { index, option ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("档位 ${index + 1}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { onOptionsChange(options.toMutableList().also { it.removeAt(index) }) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
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
        if (rule.required) {
            ruleObject.put("required", true)
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
        required = optBoolean("required", optBoolean("reasoningRequired", false)),
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
    showNotification: (String) -> Unit
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
            showNotification(e.message ?: saveFailedText)
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

                        DropdownMenu(
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

@Composable
private fun ContextSummarySettingsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    scope: CoroutineScope,
    showNotification: (String) -> Unit
) {
    val latestConfig by rememberUpdatedState(config)
    var contextLengthInput by remember(config.id) { mutableStateOf(formatFloatValue(config.contextLength)) }
    var maxContextLengthInput by remember(config.id) { mutableStateOf(formatFloatValue(config.maxContextLength)) }
    var contextError by remember { mutableStateOf<String?>(null) }

    var enableSummary by remember(config.id) { mutableStateOf(config.enableSummary) }
    var summaryTokenThresholdInput by remember(config.id) { mutableStateOf(formatFloatValue(config.summaryTokenThreshold)) }
    var enableSummaryByMessageCount by remember(config.id) { mutableStateOf(config.enableSummaryByMessageCount) }
    var summaryMessageCountThresholdInput by remember(config.id) { mutableStateOf(config.summaryMessageCountThreshold.toString()) }
    var summaryError by remember { mutableStateOf<String?>(null) }

    var contextExpanded by rememberSaveable { mutableStateOf(false) }
    var summaryExpanded by rememberSaveable { mutableStateOf(false) }

    val errorValidContextLength = stringResource(id = R.string.model_config_error_valid_context_length)
    val errorValidMaxContextLength = stringResource(id = R.string.model_config_error_valid_max_context_length)
    val errorSaveFailed = stringResource(id = R.string.model_config_error_save_failed)
    val errorSummaryThresholdRange = stringResource(id = R.string.model_config_error_summary_threshold_range)
    val errorValidMessageCount = stringResource(id = R.string.model_config_error_valid_message_count)

    LaunchedEffect(config.id, config.contextLength) {
        contextLengthInput = formatFloatValue(config.contextLength)
    }
    LaunchedEffect(config.id, config.maxContextLength) {
        maxContextLengthInput = formatFloatValue(config.maxContextLength)
    }
    LaunchedEffect(config.id, config.enableSummary) {
        enableSummary = config.enableSummary
    }
    LaunchedEffect(config.id, config.summaryTokenThreshold) {
        summaryTokenThresholdInput = formatFloatValue(config.summaryTokenThreshold)
    }
    LaunchedEffect(config.id, config.enableSummaryByMessageCount) {
        enableSummaryByMessageCount = config.enableSummaryByMessageCount
    }
    LaunchedEffect(config.id, config.summaryMessageCountThreshold) {
        summaryMessageCountThresholdInput = config.summaryMessageCountThreshold.toString()
    }

    LaunchedEffect(config.id) {
        snapshotFlow { contextLengthInput to maxContextLengthInput }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest { (contextText, maxText) ->
                val contextValue = contextText.toFloatOrNull()
                val maxValue = maxText.toFloatOrNull()

                when {
                    contextValue == null || contextValue <= 0f -> {
                        contextError = errorValidContextLength
                    }

                    maxValue == null || maxValue <= 0f -> {
                        contextError = errorValidMaxContextLength
                    }

                    else -> {
                        val current = latestConfig
                        val isNoOp =
                            current.contextLength == contextValue &&
                                    current.maxContextLength == maxValue
                        if (isNoOp) {
                            contextError = null
                            return@collectLatest
                        }

                        try {
                            configManager.updateContextSettings(
                                configId = current.id,
                                contextLength = contextValue,
                                maxContextLength = maxValue,
                                enableMaxContextMode = current.enableMaxContextMode
                            )
                            contextError = null
                        } catch (e: Exception) {
                            contextError = e.message ?: errorSaveFailed
                        }
                    }
                }
            }
    }

    LaunchedEffect(config.id) {
        snapshotFlow {
            listOf(
                enableSummary,
                summaryTokenThresholdInput,
                enableSummaryByMessageCount,
                summaryMessageCountThresholdInput
            )
        }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest {
                val current = latestConfig
                if (!enableSummary) {
                    if (current.enableSummary) {
                        try {
                            configManager.updateSummarySettings(
                                configId = current.id,
                                enableSummary = false,
                                summaryTokenThreshold = current.summaryTokenThreshold,
                                enableSummaryByMessageCount = current.enableSummaryByMessageCount,
                                summaryMessageCountThreshold = current.summaryMessageCountThreshold
                            )
                            summaryError = null
                        } catch (e: Exception) {
                            summaryError = e.message ?: errorSaveFailed
                        }
                    }
                    return@collectLatest
                }

                val threshold = summaryTokenThresholdInput.toFloatOrNull()
                val messageCount = summaryMessageCountThresholdInput.toIntOrNull()

                when {
                    threshold == null || threshold <= 0f || threshold >= 1f -> {
                        summaryError = errorSummaryThresholdRange
                    }

                    enableSummaryByMessageCount && (messageCount == null || messageCount <= 0) -> {
                        summaryError = errorValidMessageCount
                    }

                    else -> {
                        val nextMessageCount =
                            if (enableSummaryByMessageCount) messageCount
                                ?: current.summaryMessageCountThreshold
                            else current.summaryMessageCountThreshold

                        val isNoOp =
                            current.enableSummary == enableSummary &&
                                    current.summaryTokenThreshold == threshold &&
                                    current.enableSummaryByMessageCount == enableSummaryByMessageCount &&
                                    current.summaryMessageCountThreshold == nextMessageCount
                        if (isNoOp) {
                            summaryError = null
                            return@collectLatest
                        }

                        try {
                            configManager.updateSummarySettings(
                                configId = current.id,
                                enableSummary = enableSummary,
                                summaryTokenThreshold = threshold,
                                enableSummaryByMessageCount = enableSummaryByMessageCount,
                                summaryMessageCountThreshold = nextMessageCount
                            )
                            summaryError = null
                        } catch (e: Exception) {
                            summaryError = e.message ?: errorSaveFailed
                        }
                    }
                }
            }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { contextExpanded = !contextExpanded }
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.settings_context_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (contextExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (contextExpanded) stringResource(id = R.string.model_config_collapse) else stringResource(id = R.string.model_config_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = contextExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsInfoBanner(text = stringResource(id = R.string.settings_context_card_content))

                        SettingsTextField(
                            title = stringResource(id = R.string.settings_context_length),
                            subtitle = stringResource(id = R.string.settings_context_length_subtitle),
                            value = contextLengthInput,
                            onValueChange = {
                                contextLengthInput = it
                                contextError = null
                            },
                            unitText = "K",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            )
                        )

                        SettingsTextField(
                            title = stringResource(id = R.string.settings_max_context_length),
                            subtitle = stringResource(id = R.string.settings_max_context_length_subtitle),
                            value = maxContextLengthInput,
                            onValueChange = {
                                maxContextLengthInput = it
                                contextError = null
                            },
                            unitText = "K",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            )
                        )

                        contextError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

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
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { summaryExpanded = !summaryExpanded }
                ) {
                    Icon(
                        imageVector = Icons.Default.Summarize,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.settings_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (summaryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (summaryExpanded) stringResource(id = R.string.model_config_collapse) else stringResource(id = R.string.model_config_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = summaryExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsSwitchRow(
                            title = stringResource(id = R.string.settings_enable_summary),
                            subtitle = stringResource(id = R.string.settings_enable_summary_desc),
                            checked = enableSummary,
                            onCheckedChange = { enableSummary = it }
                        )

                        SettingsTextField(
                            title = stringResource(id = R.string.settings_summary_threshold),
                            subtitle = stringResource(id = R.string.settings_summary_threshold_subtitle),
                            value = summaryTokenThresholdInput,
                            onValueChange = {
                                summaryTokenThresholdInput = it
                                summaryError = null
                            },
                            enabled = enableSummary,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            )
                        )

                        SettingsSwitchRow(
                            title = stringResource(id = R.string.settings_enable_summary_by_message_count),
                            subtitle = stringResource(id = R.string.settings_enable_summary_by_message_count_desc),
                            checked = enableSummaryByMessageCount,
                            onCheckedChange = { enableSummaryByMessageCount = it },
                            enabled = enableSummary
                        )

                        SettingsTextField(
                            title = stringResource(id = R.string.settings_summary_message_count_threshold),
                            subtitle = stringResource(id = R.string.settings_summary_message_count_threshold_subtitle),
                            value = summaryMessageCountThresholdInput,
                            onValueChange = {
                                summaryMessageCountThresholdInput = it
                                summaryError = null
                            },
                            unitText = stringResource(id = R.string.model_config_unit_items),
                            enabled = enableSummary && enableSummaryByMessageCount,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            )
                        )

                        summaryError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ConnectionTestItem(
    val labelResId: Int,
    val result: Result<Unit>
)

private fun ModelConnectionTestType.toLabelResId(): Int {
    return when (this) {
        ModelConnectionTestType.CHAT -> R.string.test_item_chat
        ModelConnectionTestType.TOOL_CALL -> R.string.test_item_toolcall
        ModelConnectionTestType.IMAGE -> R.string.test_item_image
        ModelConnectionTestType.AUDIO -> R.string.test_item_audio
        ModelConnectionTestType.VIDEO -> R.string.test_item_video
    }
}

private fun formatFloatValue(value: Float): String {
    return if (value % 1f == 0f) value.toInt().toString() else String.format("%.2f", value)
}
