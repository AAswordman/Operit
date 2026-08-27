package com.ai.assistance.operit.ui.features.settings.sections

import android.annotation.SuppressLint
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.EndpointCompleter
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.api.chat.llmprovider.LlamaProvider
import com.ai.assistance.operit.api.chat.llmprovider.ModelListFetcher
import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgAiProviderRegistry
import com.ai.assistance.operit.ui.common.input.bringIntoViewOnImeFocus
import com.ai.assistance.operit.ui.features.settings.DebouncedModelConfigAutoSaveEffect
import com.ai.assistance.operit.ui.features.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.common.icons.providerLogoColorFilter
import com.ai.assistance.operit.ui.common.icons.rememberProviderLogoPainter
import com.ai.assistance.operit.ui.features.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.util.LocationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val TAG = "ModelApiSettings"

private val modelApiSettingsSaveMutex = Mutex()

private data class ProviderSelectionOption(
    val id: String,
    val displayName: String
)

@Composable
@SuppressLint("MissingPermission")
fun ModelApiSettingsSection(
        config: ModelConfigData,
        configManager: ModelConfigManager,
        saveCoordinator: ModelConfigSaveCoordinator,
        showNotification: (String) -> Unit,
        navigateToMnnModelDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 区域告警可见性
    var showRegionWarning by remember { mutableStateOf(false) }

    fun getDefaultModelName(providerTypeId: String): String {
        val providerType = ApiProviderType.fromProviderTypeId(providerTypeId) ?: return ""
        return ApiProviderConfigs.getDefaultModelName(providerType)
    }

    fun getEndpointOptions(providerTypeId: String): List<Pair<String, String>>? {
        val providerType = ApiProviderType.fromProviderTypeId(providerTypeId) ?: return null
        return ApiProviderConfigs.getEndpointOptions(providerType)
            ?.map { it.endpoint to it.label }
    }

    // 检查当前模型名称是否是某个提供商的默认值
    fun isDefaultModelName(modelName: String): Boolean {
        return ApiProviderConfigs.isDefaultModelName(modelName)
    }

    // API编辑状态
    var apiEndpointInput by remember(config.id) { mutableStateOf(config.apiEndpoint) }
    var apiKeyInput by remember(config.id) { mutableStateOf(config.apiKey) }
    var modelNameInput by remember(config.id) { mutableStateOf(config.modelName) }
    var selectedProviderTypeId by remember(config.id) { mutableStateOf(config.apiProviderTypeId) }
    var hasInitializedProviderEndpointSync by remember(config.id) { mutableStateOf(false) }
    var previousProviderTypeId by remember(config.id) { mutableStateOf(config.apiProviderTypeId) }
    val selectedApiProvider = ApiProviderType.fromProviderTypeId(selectedProviderTypeId)

    // MNN特定配置状态
    var mnnForwardTypeInput by remember(config.id) { mutableStateOf(config.mnnForwardType) }
    var mnnThreadCountInput by remember(config.id) { mutableStateOf(config.mnnThreadCount.toString()) }

    // llama.cpp 常用配置状态
    var llamaThreadCountInput by remember(config.id) { mutableStateOf(config.llamaThreadCount.toString()) }
    var llamaContextSizeInput by remember(config.id) { mutableStateOf(config.llamaContextSize.toString()) }
    var llamaGpuLayersInput by remember(config.id) { mutableStateOf(config.llamaGpuLayers.toString()) }

    // 图片处理配置状态
    var enableDirectImageProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectImageProcessing) }

    var enableDirectAudioProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectAudioProcessing) }

    var enableDirectVideoProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectVideoProcessing) }
    
    // Google Search Grounding 配置状态 (仅Gemini)
    var enableGoogleSearchInput by remember(config.id) { mutableStateOf(config.enableGoogleSearch) }

    // Claude 1小时提示缓存配置状态 (仅Claude)
    var enableClaude1hPromptCacheInput by remember(config.id) {
        mutableStateOf(config.enableClaude1hPromptCache)
    }
    
    // Tool Call配置状态
    var enableToolCallInput by remember(config.id) { mutableStateOf(config.enableToolCall) }

    data class ApiAutoSaveState(
        val apiEndpoint: String,
        val apiKey: String,
        val modelName: String,
        val providerTypeId: String,
        val provider: ApiProviderType,
        val mnnForwardType: Int,
        val mnnThreadCount: Int,
        val llamaThreadCount: Int,
        val llamaContextSize: Int,
        val llamaGpuLayers: Int,
        val enableDirectImageProcessing: Boolean,
        val enableDirectAudioProcessing: Boolean,
        val enableDirectVideoProcessing: Boolean,
        val enableGoogleSearch: Boolean,
        val enableClaude1hPromptCache: Boolean,
        val enableToolCall: Boolean,
    )

    // 保存设置的通用函数
    suspend fun persist(state: ApiAutoSaveState) {
        modelApiSettingsSaveMutex.withLock {
            withContext(Dispatchers.IO) {
                configManager.updateApiSettingsFull(
                    configId = config.id,
                    apiKey = state.apiKey,
                    apiEndpoint = state.apiEndpoint,
                    modelName = state.modelName,
                    apiProviderType = state.provider,
                    apiProviderTypeId = state.providerTypeId,
                    mnnForwardType = state.mnnForwardType,
                    mnnThreadCount = state.mnnThreadCount,
                    llamaThreadCount = state.llamaThreadCount,
                    llamaContextSize = state.llamaContextSize,
                    llamaGpuLayers = state.llamaGpuLayers,
                    enableDirectImageProcessing = state.enableDirectImageProcessing,
                    enableDirectAudioProcessing = state.enableDirectAudioProcessing,
                    enableDirectVideoProcessing = state.enableDirectVideoProcessing,
                    enableGoogleSearch = state.enableGoogleSearch,
                    enableClaude1hPromptCache = state.enableClaude1hPromptCache,
                    enableToolCall = state.enableToolCall,
                )

                EnhancedAIService.refreshAllServices(
                    configManager.appContext
                )
            }
        }
    }

    fun buildAutoSaveState(): ApiAutoSaveState {
        return ApiAutoSaveState(
            apiEndpoint = apiEndpointInput,
            apiKey = apiKeyInput,
            modelName = modelNameInput,
            providerTypeId = selectedProviderTypeId,
            provider = selectedApiProvider ?: ApiProviderType.OTHER,
            mnnForwardType = mnnForwardTypeInput,
            mnnThreadCount = mnnThreadCountInput.toIntOrNull() ?: 4,
            llamaThreadCount = llamaThreadCountInput.toIntOrNull()?.coerceAtLeast(1) ?: 4,
            llamaContextSize = llamaContextSizeInput.toIntOrNull()?.coerceAtLeast(1) ?: 2048,
            llamaGpuLayers = llamaGpuLayersInput.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            enableDirectImageProcessing = enableDirectImageProcessingInput,
            enableDirectAudioProcessing = enableDirectAudioProcessingInput,
            enableDirectVideoProcessing = enableDirectVideoProcessingInput,
            enableGoogleSearch = enableGoogleSearchInput,
            enableClaude1hPromptCache = enableClaude1hPromptCacheInput,
            enableToolCall = enableToolCallInput,
        )
    }

    suspend fun flushSettings(showSuccess: Boolean) {
        val state = buildAutoSaveState()
        try {
            AppLogger.d(
                TAG,
                "保存API设置: apiKey=${state.apiKey.take(5)}..., endpoint=${state.apiEndpoint}, model=${state.modelName}, providerType=${state.provider.name}"
            )
            persist(state)
            AppLogger.d(TAG, "API设置保存完成并刷新服务")
            if (showSuccess) {
                showNotification(context.getString(R.string.api_settings_saved))
            }
        } catch (e: Exception) {
            if (showSuccess) {
                showNotification((e.message ?: context.getString(R.string.save_failed)))
            } else {
                AppLogger.e(TAG, "API设置自动保存失败: ${e.message}", e)
            }
            throw e
        }
    }

    RegisterModelConfigSaveAction(
        coordinator = saveCoordinator,
        key = "api:${config.id}",
        action = { showSuccess -> flushSettings(showSuccess) }
    )

    DebouncedModelConfigAutoSaveEffect(
        effectKey = config.id,
        valueProvider = { buildAutoSaveState() },
        persist = { state -> persist(state) },
        onError = { e ->
            showNotification((e.message ?: context.getString(R.string.auto_save_failed)))
        }
    )

    // 根据API提供商获取默认的API端点URL
    fun getDefaultApiEndpoint(providerType: ApiProviderType): String {
        return ApiProviderConfigs.getDefaultApiEndpoint(providerType)
    }

    // 添加一个函数检查当前API端点是否为某个提供商的默认端点
    fun isDefaultApiEndpoint(endpoint: String): Boolean {
        return ApiProviderConfigs.isDefaultApiEndpoint(endpoint)
    }

    fun syncMoonshotModelForEndpoint(endpoint: String) {
        if (selectedApiProvider != ApiProviderType.MOONSHOT) {
            return
        }

        val moonshotDefaultModel = getDefaultModelName(ApiProviderType.MOONSHOT.name)
        val isKimiCodeEndpoint = endpoint.contains("api.kimi.com/coding/v1", ignoreCase = true)

        if (isKimiCodeEndpoint) {
            if (modelNameInput.isEmpty() || modelNameInput == moonshotDefaultModel) {
                modelNameInput = "kimi-for-coding"
            }
        } else if (modelNameInput == "kimi-for-coding") {
            modelNameInput = moonshotDefaultModel
        }
    }

    // 当API提供商改变时更新端点
    LaunchedEffect(selectedProviderTypeId) {
        AppLogger.d("ModelApiSettingsSection", "API提供商改变")
        if (
            selectedApiProvider == ApiProviderType.OPENAI ||
                selectedApiProvider == ApiProviderType.OPENAI_RESPONSES ||
                selectedApiProvider == ApiProviderType.OPENAI_RESPONSES_GENERIC ||
                selectedApiProvider == ApiProviderType.OPENAI_GENERIC ||
                selectedApiProvider == ApiProviderType.GOOGLE ||
                selectedApiProvider == ApiProviderType.GEMINI_GENERIC ||
                selectedApiProvider == ApiProviderType.ANTHROPIC ||
                selectedApiProvider == ApiProviderType.ANTHROPIC_GENERIC ||
                selectedApiProvider == ApiProviderType.MISTRAL ||
                selectedApiProvider == ApiProviderType.NVIDIA ||
                selectedApiProvider == ApiProviderType.NOUS_PORTAL
        ) {
            val inChina = LocationUtils.isDeviceInMainlandChina(context)
            showRegionWarning = inChina
            if (inChina) {
                AppLogger.d("ModelApiSettingsSection", "检测到位于中国大陆")
                showNotification(context.getString(R.string.overseas_provider_warning))
            } else {
                AppLogger.d("ModelApiSettingsSection", "检测到位于海外")
            }
        } else {
            showRegionWarning = false
        }

        val shouldSyncEndpointByProviderChange = hasInitializedProviderEndpointSync
        hasInitializedProviderEndpointSync = true
        if (!shouldSyncEndpointByProviderChange) {
            // 首次进入页面时保留持久化配置，避免把用户已选择的端点覆盖成默认值。
            return@LaunchedEffect
        }

        if (selectedApiProvider == null) {
            previousProviderTypeId = selectedProviderTypeId
            return@LaunchedEffect
        }

        val previousProvider = ApiProviderType.fromProviderTypeId(previousProviderTypeId)
        val previousDefaultEndpoint =
            previousProvider?.let { getDefaultApiEndpoint(it) }.orEmpty()
        val shouldApplyNewProviderDefault =
            apiEndpointInput.isEmpty() ||
                isDefaultApiEndpoint(apiEndpointInput) ||
                (previousDefaultEndpoint.isNotEmpty() && apiEndpointInput == previousDefaultEndpoint)

        if (shouldApplyNewProviderDefault) {
            apiEndpointInput = getDefaultApiEndpoint(selectedApiProvider)
        }
        previousProviderTypeId = selectedProviderTypeId
    }

    // 模型列表状态
    var isLoadingModels by remember { mutableStateOf(false) }
    var showModelsDialog by remember { mutableStateOf(false) }
    var modelsList by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }
    var showEndpointDialog by remember(config.id) { mutableStateOf(false) }

    // 检查是否未填写API密钥（仅用于UI显示）
    val isUsingDefaultApiKey = apiKeyInput.isBlank()
    val providerRequiresApiKey =
        ApiProviderConfigs.requiresApiKey(selectedProviderTypeId, apiEndpointInput)
    val isMnnProvider = selectedApiProvider == ApiProviderType.MNN
    val isLlamaProvider = selectedApiProvider == ApiProviderType.LLAMA_CPP
    val isToolPkgProvider = selectedApiProvider == null
    val canUseKeylessModelUi = isToolPkgProvider || !providerRequiresApiKey
    val canEditModelName =
        !isMnnProvider &&
            !isLlamaProvider &&
            (canUseKeylessModelUi || !isUsingDefaultApiKey)
    val canRequestModelList =
        isToolPkgProvider ||
            isMnnProvider ||
            isLlamaProvider ||
            (
                apiEndpointInput.isNotBlank() &&
                    (!providerRequiresApiKey || (!isUsingDefaultApiKey && apiKeyInput.isNotBlank()))
            )
    val endpointOptions = getEndpointOptions(selectedProviderTypeId)
    val selectableEndpointOptions =
        when {
            endpointOptions != null -> endpointOptions
            selectedApiProvider != null -> {
                val defaultEndpoint = getDefaultApiEndpoint(selectedApiProvider)
                if (defaultEndpoint.isNotBlank()) {
                    listOf(defaultEndpoint to defaultEndpoint)
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }

    suspend fun fetchAvailableModels(): Result<List<ModelOption>> {
        return when {
            isMnnProvider -> ModelListFetcher.getMnnLocalModels(context)
            isLlamaProvider -> ModelListFetcher.getLlamaLocalModels(context)
            isToolPkgProvider -> runCatching {
                val service =
                    AIServiceFactory.createService(
                        config =
                            config.copy(
                                apiKey = apiKeyInput,
                                apiEndpoint = apiEndpointInput,
                                modelName = modelNameInput,
                                apiProviderType = ApiProviderType.OTHER,
                                apiProviderTypeId = selectedProviderTypeId,
                                enableDirectImageProcessing = enableDirectImageProcessingInput,
                                enableDirectAudioProcessing = enableDirectAudioProcessingInput,
                                enableDirectVideoProcessing = enableDirectVideoProcessingInput,
                                enableGoogleSearch = enableGoogleSearchInput,
                                enableClaude1hPromptCache = enableClaude1hPromptCacheInput,
                                enableToolCall = enableToolCallInput
                            ),
                        modelConfigManager = configManager,
                        context = context
                    )
                try {
                    service.getModelsList(context).getOrThrow()
                } finally {
                    service.release()
                }
            }

            else ->
                ModelListFetcher.getModelsList(
                    context,
                    apiKeyInput,
                    apiEndpointInput,
                    selectedApiProvider ?: ApiProviderType.OPENAI_GENERIC
                )
        }
    }
    // 移除了强制锁定模型名称的逻辑，允许用户自由修改

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
            SettingsSectionHeader(
                    icon = Icons.Default.Api,
                    title = stringResource(R.string.api_settings)
            )

            var showApiProviderDialog by remember { mutableStateOf(false) }

            SettingsSelectorRow(
                    title = stringResource(R.string.api_provider),
                    subtitle = stringResource(R.string.select_api_provider),
                    value = getProviderDisplayName(selectedProviderTypeId, context),
                    onClick = { showApiProviderDialog = true }
            )

            if (showApiProviderDialog) {
                ApiProviderDialog(
                        onDismissRequest = { showApiProviderDialog = false },
                        onProviderSelected = { provider ->
                            selectedProviderTypeId = provider.id

                            // 对有默认模型名的供应商，视为“有强制内容”：切换时总是重置为该供应商默认模型名
                            val hasForcedModelName = getDefaultModelName(provider.id).isNotEmpty()
                            if (hasForcedModelName) {
                                modelNameInput = getDefaultModelName(provider.id)
                            } else if (modelNameInput.isEmpty() || isDefaultModelName(modelNameInput)) {
                                // 通用/无默认模型名的供应商仍沿用旧逻辑
                                modelNameInput = getDefaultModelName(provider.id)
                            }

                            showApiProviderDialog = false
                        }
                )
            }

            AnimatedVisibility(visible = showRegionWarning) {
                SettingsInfoBanner(text = stringResource(R.string.overseas_provider_warning))
            }

            if (isMnnProvider) {
                MnnSettingsBlock(
                        mnnForwardTypeInput = mnnForwardTypeInput,
                        onForwardTypeSelected = { mnnForwardTypeInput = it },
                        mnnThreadCountInput = mnnThreadCountInput,
                        onThreadCountChange = { input ->
                            if (input.isEmpty() || input.toIntOrNull() != null) {
                                mnnThreadCountInput = input
                            }
                        },
                        navigateToMnnModelDownload = navigateToMnnModelDownload
                )
            } else if (isLlamaProvider) {
                LlamaSettingsBlock(
                    llamaThreadCountInput = llamaThreadCountInput,
                    onThreadCountChange = { input ->
                        if (input.isEmpty() || input.toIntOrNull() != null) {
                            llamaThreadCountInput = input
                        }
                    },
                    llamaContextSizeInput = llamaContextSizeInput,
                    onContextSizeChange = { input ->
                        if (input.isEmpty() || input.toIntOrNull() != null) {
                            llamaContextSizeInput = input
                        }
                    },
                    llamaGpuLayersInput = llamaGpuLayersInput,
                    onGpuLayersChange = { input ->
                        if (input.isEmpty() || input.toIntOrNull() != null) {
                            llamaGpuLayersInput = input
                        }
                    }
                )
            } else {
                SettingsTextField(
                        title = stringResource(R.string.api_endpoint),
                        subtitle = stringResource(R.string.api_endpoint_placeholder),
                        value = apiEndpointInput,
                        onValueChange = {
                            apiEndpointInput = it.replace("\n", "").replace("\r", "").replace(" ", "")
                        },
                        enabled = true,
                        keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                        ),
                        trailingContent =
                                if (selectableEndpointOptions.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { showEndpointDialog = true }) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                } else {
                                    null
                                }
                )

                if (showEndpointDialog && selectableEndpointOptions.isNotEmpty()) {
                    Dialog(onDismissRequest = { showEndpointDialog = false }) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.api_endpoint),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                selectableEndpointOptions.forEach { (endpoint, label) ->
                                    val isSelected = apiEndpointInput == endpoint
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    apiEndpointInput = endpoint
                                                    syncMoonshotModelForEndpoint(endpoint)
                                                    showEndpointDialog = false
                                                }
                                                .background(
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surface
                                                    }
                                                )
                                                .padding(vertical = 10.dp, horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = endpoint,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (label.isNotBlank() && label != endpoint) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }

            val completedEndpoint =
                selectedApiProvider?.let {
                    EndpointCompleter.completeEndpoint(apiEndpointInput, it)
                } ?: apiEndpointInput
            if (completedEndpoint != apiEndpointInput) {
                Text(
                    text = stringResource(R.string.actual_request_url, completedEndpoint),
                    style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.endpoint_completion_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val apiKeyInteractionSource = remember { MutableInteractionSource() }
                val isApiKeyFocused by apiKeyInteractionSource.collectIsFocusedAsState()

                SettingsTextField(
                        title = stringResource(R.string.api_key),
                        subtitle =
                                if (isUsingDefaultApiKey)
                                        stringResource(R.string.api_key_placeholder_default)
                                else
                                        stringResource(R.string.api_key_placeholder_custom),
                        value = if (isUsingDefaultApiKey) "" else apiKeyInput,
                        onValueChange = {
                            val filteredInput = it.replace("\n", "").replace("\r", "").replace(" ", "")
                            apiKeyInput = filteredInput
                        },
                        keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                        ),
                        visualTransformation = if (isApiKeyFocused || apiKeyInput.isEmpty()) VisualTransformation.None else ApiKeyVisualTransformation(),
                        interactionSource = apiKeyInteractionSource
                )
            }
            SettingsTextField(
                    title = stringResource(R.string.model_name),
                    subtitle = when {
                        isMnnProvider -> stringResource(R.string.mnn_select_downloaded_model)
                        isLlamaProvider -> stringResource(R.string.llama_select_downloaded_model)
                        else -> stringResource(R.string.model_name_placeholder) + stringResource(R.string.model_name_multiple_hint)
                    },
                        value = modelNameInput,
                        onValueChange = {
                        if (canEditModelName) {
                                modelNameInput = it.replace("\n", "").replace("\r", "")
                            }
                        },
                    enabled = !isMnnProvider && !isLlamaProvider && canEditModelName,
                    trailingContent = {
                IconButton(
                        onClick = {
                            AppLogger.d(
                                    TAG,
                                    "模型列表按钮被点击 - API端点: $apiEndpointInput, API类型: $selectedProviderTypeId"
                            )
                            val gettingModelsText = context.getString(R.string.getting_models_list)
                            val unknownErrorText = context.getString(R.string.unknown_error)
                            val getModelsFailedText = context.getString(R.string.get_models_list_failed)
                            val defaultConfigNoModelsText = context.getString(R.string.default_config_no_models_list)
                            val fillEndpointKeyText = context.getString(R.string.fill_endpoint_and_key)
                            val modelsListSuccessText = context.getString(R.string.models_list_success)
                            
                            showNotification(gettingModelsText)

                            scope.launch {
                                if (canRequestModelList) {
                                    isLoadingModels = true
                                    modelLoadError = null
                                    AppLogger.d(
                                            TAG,
                                            "开始获取模型列表: 端点=$apiEndpointInput, API类型=$selectedProviderTypeId"
                                    )

                                    try {
                                        val result = fetchAvailableModels()
                                        if (result.isSuccess) {
                                            val models = result.getOrThrow()
                                            AppLogger.d(TAG, "模型列表获取成功，共 ${models.size} 个模型")
                                            modelsList = models
                                            showModelsDialog = true
                                            showNotification(modelsListSuccessText.format(models.size))
                                        } else {
                                            val errorMsg =
                                                    result.exceptionOrNull()?.message ?: unknownErrorText
                                            AppLogger.e(TAG, "模型列表获取失败: $errorMsg")
                                            modelLoadError = getModelsFailedText.format(errorMsg)
                                            showNotification(modelLoadError ?: getModelsFailedText.format(""))
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.e(TAG, "获取模型列表发生异常", e)
                                        modelLoadError = getModelsFailedText.format(e.message ?: "")
                                        showNotification(modelLoadError ?: getModelsFailedText.format(""))
                                    } finally {
                                        isLoadingModels = false
                                        AppLogger.d(TAG, "模型列表获取流程完成")
                                    }
                                } else if (!isToolPkgProvider && isUsingDefaultApiKey && providerRequiresApiKey) {
                                    AppLogger.d(TAG, "使用默认配置，不获取模型列表")
                                    showNotification(defaultConfigNoModelsText)
                                } else {
                                    AppLogger.d(TAG, "API端点或密钥为空")
                                    showNotification(fillEndpointKeyText)
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        colors =
                                IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                ),
                                enabled = canRequestModelList
                ) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = stringResource(R.string.get_models_list),
                                tint =
                                                if (!canRequestModelList && !isToolPkgProvider)
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.enable_direct_image_processing),
                subtitle = stringResource(R.string.enable_direct_image_processing_desc),
                checked = enableDirectImageProcessingInput,
                onCheckedChange = { enableDirectImageProcessingInput = it }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.enable_direct_audio_processing),
                subtitle = stringResource(R.string.enable_direct_audio_processing_desc),
                checked = enableDirectAudioProcessingInput,
                onCheckedChange = { enableDirectAudioProcessingInput = it }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.enable_direct_video_processing),
                subtitle = stringResource(R.string.enable_direct_video_processing_desc),
                checked = enableDirectVideoProcessingInput,
                onCheckedChange = { enableDirectVideoProcessingInput = it }
            )
            
            // Google Search Grounding 开关 (仅Gemini支持)
            if (selectedApiProvider == ApiProviderType.GOOGLE ||
                selectedApiProvider == ApiProviderType.GEMINI_GENERIC) {
                SettingsSwitchRow(
                        title = stringResource(R.string.enable_google_search),
                        subtitle = stringResource(R.string.enable_google_search_desc),
                            checked = enableGoogleSearchInput,
                            onCheckedChange = { enableGoogleSearchInput = it }
                    )
            }

            // Claude 1小时提示缓存开关 (仅Claude支持)
            if (selectedApiProvider == ApiProviderType.ANTHROPIC ||
                selectedApiProvider == ApiProviderType.ANTHROPIC_GENERIC) {
                SettingsSwitchRow(
                        title = stringResource(R.string.enable_claude_1h_prompt_cache),
                        subtitle = stringResource(R.string.enable_claude_1h_prompt_cache_desc),
                        checked = enableClaude1hPromptCacheInput,
                        onCheckedChange = { enableClaude1hPromptCacheInput = it }
                    )
            }
            
            // Tool Call 开关
            SettingsSwitchRow(
                title = stringResource(R.string.enable_tool_call),
                subtitle = stringResource(R.string.enable_tool_call_desc),
                checked = enableToolCallInput,
                onCheckedChange = { enableToolCallInput = it }
            )

        }
    }

    // 模型列表对话框
    if (showModelsDialog) {
        var searchQuery by remember { mutableStateOf("") }
        // 维护已选中的模型集合
        val selectedModels = remember {
            mutableStateOf(
                modelNameInput.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            )
        }
        val filteredModelsList =
                remember(searchQuery, modelsList) {
                    if (searchQuery.isEmpty()) modelsList
                    else modelsList.filter { it.id.contains(searchQuery, ignoreCase = true) }
                }

        Dialog(onDismissRequest = { showModelsDialog = false }) {
            Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 标题栏
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                stringResource(R.string.available_models_list),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                        )

                        FilledIconButton(
                                onClick = {
                                    scope.launch {
                                        if (canRequestModelList) {
                                            isLoadingModels = true
                                            try {
                                                val result = fetchAvailableModels()
                                                if (result.isSuccess) {
                                                    modelsList = result.getOrThrow()
                                                } else {
                                                    val errorMsg = result.exceptionOrNull()?.message ?: context.getString(R.string.unknown_error)
                                                    modelLoadError = context.getString(R.string.refresh_models_list_failed, errorMsg)
                                                    showNotification(modelLoadError ?: context.getString(R.string.refresh_models_failed))
                                                }
                                            } catch (e: Exception) {
                                                val errorMsg = e.message ?: context.getString(R.string.unknown_error)
                                                modelLoadError = context.getString(R.string.refresh_models_list_failed, errorMsg)
                                                showNotification(modelLoadError ?: context.getString(R.string.refresh_models_failed))
                                            } finally {
                                                isLoadingModels = false
                                            }
                                        }
                                    }
                                },
                                colors =
                                        IconButtonDefaults.filledIconButtonColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                contentColor =
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                modifier = Modifier.size(36.dp)
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.refresh_models_list),
                                        modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 搜索框 - 用普通的OutlinedTextField替代实验性的SearchBar
                    OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_models), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(R.string.search),
                                        modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                                Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.clear),
                                                modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier =
                                    Modifier.fillMaxWidth().padding(bottom = 12.dp).height(48.dp),
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor =
                                                    MaterialTheme.colorScheme.outline,
                                            focusedLeadingIconColor =
                                                    MaterialTheme.colorScheme.primary,
                                            unfocusedLeadingIconColor =
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    // 模型列表
                    if (modelsList.isEmpty()) {
                        Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.6f
                                                )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text = modelLoadError ?: stringResource(R.string.no_models_found),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(filteredModelsList.size) { index ->
                                val model = filteredModelsList[index]
                                val isSelected = selectedModels.value.contains(model.id)
                                
                                // 使用带Checkbox的Row实现多选
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clickable {
                                                            // 切换选中状态
                                                            val newSelection = selectedModels.value.toMutableSet()
                                                            if (isSelected) {
                                                                newSelection.remove(model.id)
                                                            } else {
                                                                newSelection.add(model.id)
                                                            }
                                                            selectedModels.value = newSelection
                                                        }
                                                        .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                val newSelection = selectedModels.value.toMutableSet()
                                                if (checked) {
                                                    newSelection.add(model.id)
                                                } else {
                                                    newSelection.remove(model.id)
                                                }
                                                selectedModels.value = newSelection
                                            },
                                            colors = CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.primary
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                            text = model.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                    MaterialTheme.colorScheme.primary 
                                                else 
                                                    MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (index < filteredModelsList.size - 1) {
                                    HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color =
                                                    MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                    ),
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 底部信息
                    if (filteredModelsList.isNotEmpty()) {
                        Text(
                                text =
                                        stringResource(R.string.models_displayed, filteredModelsList.size) +
                                                (if (searchQuery.isNotEmpty()) stringResource(R.string.models_displayed_filtered) else "") +
                                                (if (selectedModels.value.isNotEmpty()) " • ${selectedModels.value.size}" + stringResource(R.string.models_selected_suffix) else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                                fontSize = 12.sp
                        )
                    }

                    // 底部按钮
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        FilledTonalButton(
                                onClick = { showModelsDialog = false },
                                modifier = Modifier.height(36.dp)
                        ) { Text(stringResource(R.string.close), fontSize = 14.sp) }
                        
                        Button(
                                onClick = {
                                    // 将选中的模型用逗号连接
                                    val orderedSelection = modelsList.map { it.id }
                                        .filter { selectedModels.value.contains(it) }
                                    modelNameInput = orderedSelection.joinToString(",")
                                    if (selectedApiProvider == ApiProviderType.MNN) {
                                        AppLogger.d(TAG, "选择MNN模型: $modelNameInput")
                                    }
                                    showModelsDialog = false
                                },
                                modifier = Modifier.height(36.dp),
                                enabled = selectedModels.value.isNotEmpty()
                        ) { 
                            Text(
                                stringResource(R.string.confirm_action) + 
                                    if (selectedModels.value.isNotEmpty()) " (${selectedModels.value.size})" else "",
                                fontSize = 14.sp
                            ) 
                        }
                    }
                }
            }
        }
    }
}

private fun getBuiltInProviderDisplayName(provider: ApiProviderType, context: android.content.Context): String {
    return when (provider) {
        ApiProviderType.OPENAI -> context.getString(R.string.provider_openai)
        ApiProviderType.OPENAI_RESPONSES -> context.getString(R.string.provider_openai_responses)
        ApiProviderType.OPENAI_RESPONSES_GENERIC -> context.getString(R.string.provider_openai_responses_generic)
        ApiProviderType.OPENAI_GENERIC -> context.getString(R.string.provider_openai_generic)
        ApiProviderType.ANTHROPIC -> context.getString(R.string.provider_anthropic)
        ApiProviderType.ANTHROPIC_GENERIC -> context.getString(R.string.provider_anthropic_generic)
        ApiProviderType.GOOGLE -> context.getString(R.string.provider_google)
        ApiProviderType.GEMINI_GENERIC -> context.getString(R.string.provider_gemini_generic)
        ApiProviderType.BAIDU -> context.getString(R.string.provider_baidu)
        ApiProviderType.ALIYUN -> context.getString(R.string.provider_aliyun)
        ApiProviderType.XUNFEI -> context.getString(R.string.provider_xunfei)
        ApiProviderType.ZHIPU -> context.getString(R.string.provider_zhipu)
        ApiProviderType.BAICHUAN -> context.getString(R.string.provider_baichuan)
        ApiProviderType.MOONSHOT -> context.getString(R.string.provider_moonshot)
        ApiProviderType.MIMO -> context.getString(R.string.provider_mimo)
        ApiProviderType.DEEPSEEK -> context.getString(R.string.provider_deepseek)
        ApiProviderType.MISTRAL -> context.getString(R.string.provider_mistral)
        ApiProviderType.SILICONFLOW -> context.getString(R.string.provider_siliconflow)
        ApiProviderType.IFLOW -> context.getString(R.string.provider_iflow)
        ApiProviderType.OPENROUTER -> context.getString(R.string.provider_openrouter)
        ApiProviderType.OPENCODE -> context.getString(R.string.provider_opencode)
        ApiProviderType.FOUR_ROUTER -> context.getString(R.string.provider_4router)
        ApiProviderType.NOUS_PORTAL -> context.getString(R.string.provider_nous_portal)
        ApiProviderType.INFINIAI -> context.getString(R.string.provider_infiniai)
        ApiProviderType.ALIPAY_BAILING -> context.getString(R.string.provider_alipay_bailing)
        ApiProviderType.DOUBAO -> context.getString(R.string.provider_doubao)
        ApiProviderType.NVIDIA -> context.getString(R.string.provider_nvidia)
        ApiProviderType.LMSTUDIO -> context.getString(R.string.provider_lmstudio)
        ApiProviderType.OLLAMA -> context.getString(R.string.provider_ollama)
        ApiProviderType.OPENAI_LOCAL -> context.getString(R.string.provider_openai_local)
        ApiProviderType.MNN -> context.getString(R.string.provider_mnn)
        ApiProviderType.LLAMA_CPP -> context.getString(R.string.provider_llama_cpp)
        ApiProviderType.PPINFRA -> context.getString(R.string.provider_ppinfra)
        ApiProviderType.NOVITA -> context.getString(R.string.provider_novita)
        ApiProviderType.MINIMAX -> context.getString(R.string.provider_minimax)
        ApiProviderType.OTHER -> context.getString(R.string.provider_other)
    }
}

private fun getProviderDisplayName(providerTypeId: String, context: android.content.Context): String {
    val builtInProvider = ApiProviderType.fromProviderTypeId(providerTypeId)
    if (builtInProvider != null) {
        return getBuiltInProviderDisplayName(builtInProvider, context)
    }
    return ToolPkgAiProviderRegistry.get(providerTypeId)?.displayName ?: providerTypeId
}

private fun getProviderSelectionOptions(context: android.content.Context): List<ProviderSelectionOption> {
    val builtInProviders =
        ApiProviderType.values().map { provider ->
            ProviderSelectionOption(
                id = provider.name,
                displayName = getBuiltInProviderDisplayName(provider, context)
            )
        }
    val toolPkgProviders =
        ToolPkgAiProviderRegistry.list().map { provider ->
            ProviderSelectionOption(
                id = provider.providerId,
                displayName = provider.displayName
            )
        }
    return builtInProviders + toolPkgProviders
}


@Composable
internal fun SettingsSectionHeader(
        icon: ImageVector,
        title: String,
        subtitle: String? = null
) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SettingsInfoBanner(text: String) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
internal fun SettingsTextField(
    title: String,
    subtitle: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource? = null,
    valueFilter: ((String) -> String)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    unitText: String? = null,
    onClick: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val inputEnabled = enabled && !readOnly

    Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = backgroundColor,
                        tonalElevation = 0.dp
                ) {
                    BasicTextField(
                            value = value,
                            onValueChange = { newValue ->
                                if (!inputEnabled) return@BasicTextField
                                val filtered = valueFilter?.invoke(newValue) ?: newValue
                                onValueChange(filtered)
                            },
                            singleLine = singleLine,
                            enabled = inputEnabled,
                            keyboardOptions = keyboardOptions,
                            keyboardActions = keyboardActions,
                            visualTransformation = visualTransformation,
                            interactionSource = resolvedInteractionSource,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnImeFocus(),
                            textStyle =
                                    TextStyle(
                                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                    ),
                            decorationBox = { innerTextField ->
                                Row(
                                        modifier =
                                                Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (value.isEmpty()) {
                                            Text(
                                                    text = placeholder,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                    unitText?.let {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                    )
                }
                trailingContent?.invoke()
            }
        }
    }
}

@Composable
private fun SettingsSelectorRow(
        title: String,
        subtitle: String,
        value: String,
        onClick: () -> Unit
) {
    Surface(
            modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onClick() },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                            .padding(end = 8.dp)
                            .weight(0.5f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
            Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean = true
) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun MnnSettingsBlock(
        mnnForwardTypeInput: Int,
        onForwardTypeSelected: (Int) -> Unit,
        mnnThreadCountInput: String,
        onThreadCountChange: (String) -> Unit,
        navigateToMnnModelDownload: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsInfoBanner(text = stringResource(R.string.mnn_local_model_tip))

        navigateToMnnModelDownload?.let { navigate ->
            Button(
                    onClick = navigate,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
            ) {
                Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.mnn_model_download))
            }
        }

        var showForwardTypeDialog by remember { mutableStateOf(false) }

        SettingsSelectorRow(
                title = stringResource(R.string.mnn_forward_type),
                subtitle = stringResource(R.string.select),
                value = forwardTypeName(mnnForwardTypeInput),
                onClick = { showForwardTypeDialog = true }
        )

        if (showForwardTypeDialog) {
            Dialog(onDismissRequest = { showForwardTypeDialog = false }) {
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                                text = stringResource(R.string.mnn_forward_type),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 12.dp)
                        )
                        listOf(
                                0 to "CPU",
                                3 to "OpenCL",
                                4 to "Auto",
                                6 to "OpenGL",
                                7 to "Vulkan"
                        ).forEach { (type, name) ->
                            Surface(
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                onForwardTypeSelected(type)
                                                showForwardTypeDialog = false
                                            },
                                    shape = RoundedCornerShape(8.dp),
                                    color =
                                            if (mnnForwardTypeInput == type)
                                                    MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                        text = name,
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsTextField(
                title = stringResource(R.string.mnn_thread_count),
                value = mnnThreadCountInput,
                onValueChange = onThreadCountChange,
                placeholder = "4",
                keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                ),
                valueFilter = { input -> input.filter { it.isDigit() } }
        )
    }
}

@Composable
private fun LlamaSettingsBlock(
        llamaThreadCountInput: String,
        onThreadCountChange: (String) -> Unit,
        llamaContextSizeInput: String,
        onContextSizeChange: (String) -> Unit,
        llamaGpuLayersInput: String,
        onGpuLayersChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsInfoBanner(text = stringResource(R.string.llama_local_model_tip))

        SettingsInfoBanner(
            text =
                stringResource(R.string.llama_local_model_download_tip) +
                    "\n" +
                    stringResource(
                        R.string.llama_local_model_dir,
                        LlamaProvider.getModelsDir().absolutePath
                    )
        )

        SettingsTextField(
                title = stringResource(R.string.llama_thread_count),
                value = llamaThreadCountInput,
                onValueChange = onThreadCountChange,
                placeholder = "4",
                keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                ),
                valueFilter = { input -> input.filter { it.isDigit() } }
        )

        SettingsTextField(
                title = stringResource(R.string.llama_context_size),
                subtitle = stringResource(R.string.llama_context_size_subtitle),
                value = llamaContextSizeInput,
                onValueChange = onContextSizeChange,
                placeholder = "2048",
                keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                ),
                valueFilter = { input -> input.filter { it.isDigit() } }
        )

        SettingsTextField(
                title = stringResource(R.string.llama_gpu_layers),
                subtitle = stringResource(R.string.llama_gpu_layers_subtitle),
                value = llamaGpuLayersInput,
                onValueChange = onGpuLayersChange,
                placeholder = "0",
                keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                ),
                valueFilter = { input -> input.filter { it.isDigit() } }
        )

        SettingsInfoBanner(text = stringResource(R.string.llama_auto_config_tip))
    }
}

private fun forwardTypeName(type: Int): String {
    return when (type) {
        0 -> "CPU"
        3 -> "OpenCL"
        4 -> "Auto"
        6 -> "OpenGL"
        7 -> "Vulkan"
        else -> "CPU"
    }
}

@Composable
private fun ApiProviderDialog(
        onDismissRequest: () -> Unit,
        onProviderSelected: (ProviderSelectionOption) -> Unit
) {
    val context = LocalContext.current
    val providers = remember { getProviderSelectionOptions(context) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredProviders = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            providers
        } else {
            providers.filter { provider ->
                provider.displayName.contains(searchQuery, ignoreCase = true) ||
                    provider.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 标题和搜索框
                Text(
                        stringResource(R.string.select_api_provider_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 搜索框
                OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_providers), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                    modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.clear),
                                            modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                )

                // 提供商列表
                androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f)
                ) {
                    items(filteredProviders.size) { index ->
                        val provider = filteredProviders[index]
                        // 美化的提供商选项
                        Surface(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onProviderSelected(provider) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 提供商图标（优先显示品牌 Logo，缺失时回退到首字母色块）
                                val providerLogo =
                                        rememberProviderLogoPainter(provider.id, 32.dp)
                                if (providerLogo != null) {
                                    Box(
                                            modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                            getProviderColor(provider.id).copy(alpha = 0.14f),
                                                            CircleShape
                                                    ),
                                            contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                                painter = providerLogo,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                colorFilter = providerLogoColorFilter()
                                        )
                                    }
                                } else {
                                    Box(
                                            modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                            getProviderColor(provider.id),
                                                            CircleShape
                                                    ),
                                            contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                                text = provider.displayName.firstOrNull()?.toString() ?: "?",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                
                // 底部按钮
                Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

// 为不同提供商生成不同的颜色
@Composable
private fun getProviderColor(providerTypeId: String): androidx.compose.ui.graphics.Color {
    val provider = ApiProviderType.fromProviderTypeId(providerTypeId)
    if (provider == null) {
        val palette = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
        val paletteIndex = kotlin.math.abs(providerTypeId.lowercase().hashCode()) % palette.size
        return palette[paletteIndex]
    }
    return when (provider) {
        ApiProviderType.OPENAI -> MaterialTheme.colorScheme.primary
        ApiProviderType.OPENAI_RESPONSES -> MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        ApiProviderType.OPENAI_RESPONSES_GENERIC -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
        ApiProviderType.OPENAI_GENERIC -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        ApiProviderType.ANTHROPIC -> MaterialTheme.colorScheme.tertiary
        ApiProviderType.ANTHROPIC_GENERIC -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
        ApiProviderType.GOOGLE -> MaterialTheme.colorScheme.secondary
        ApiProviderType.GEMINI_GENERIC -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
        ApiProviderType.BAIDU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        ApiProviderType.ALIYUN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
        ApiProviderType.XUNFEI -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
        ApiProviderType.ZHIPU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        ApiProviderType.BAICHUAN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
        ApiProviderType.MOONSHOT -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
        ApiProviderType.MIMO -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.66f)
        ApiProviderType.DEEPSEEK -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        ApiProviderType.MISTRAL -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
        ApiProviderType.SILICONFLOW -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
        ApiProviderType.IFLOW -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
        ApiProviderType.OPENROUTER -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        ApiProviderType.OPENCODE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
        ApiProviderType.FOUR_ROUTER -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.56f)
        ApiProviderType.NOUS_PORTAL -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.52f)
        ApiProviderType.INFINIAI -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ApiProviderType.ALIPAY_BAILING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        ApiProviderType.DOUBAO -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
        ApiProviderType.NVIDIA -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        ApiProviderType.LMSTUDIO -> MaterialTheme.colorScheme.tertiary
        ApiProviderType.OLLAMA -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
        ApiProviderType.OPENAI_LOCAL -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
        ApiProviderType.MNN -> MaterialTheme.colorScheme.secondary
        ApiProviderType.LLAMA_CPP -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
        ApiProviderType.PPINFRA -> MaterialTheme.colorScheme.primaryContainer
        ApiProviderType.NOVITA -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
        ApiProviderType.MINIMAX -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
        ApiProviderType.OTHER -> MaterialTheme.colorScheme.surfaceVariant
    }
}
