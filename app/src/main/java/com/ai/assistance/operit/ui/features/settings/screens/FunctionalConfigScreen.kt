package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.ai.assistance.operit.util.AssetCopyUtils
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkBuilder
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionalConfigScreen(
        onBackPressed: () -> Unit = {},
        onNavigateToModelConfig: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 配置管理器
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }

    // 配置映射状态
    val configMapping =
            functionalConfigManager.functionConfigMappingFlow.collectAsState(initial = emptyMap())
    val configMappingWithIndex =
            functionalConfigManager.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())

    // 配置摘要列表
    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }

    // UI状态
    var isLoading by remember { mutableStateOf(true) }
    var showSaveSuccess by remember { mutableStateOf(false) }

    // 加载配置摘要
    LaunchedEffect(Unit) {
        isLoading = true
        configSummaries = modelConfigManager.getAllConfigSummaries()
        isLoading = false
    }

    CustomScaffold() { paddingValues ->
        if (isLoading) {
            Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(paddingValues)
                                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.7f
                                                    )
                                    )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = stringResource(id = R.string.functional_model_config_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                    text = stringResource(id = R.string.functional_model_config_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            )

                            HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .clickable { onNavigateToModelConfig() }
                                                    .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                    Text(
                                            text = stringResource(id = R.string.manage_all_model_configs),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = stringResource(id = R.string.manage_model_configs_desc),
                                            tint = MaterialTheme.colorScheme.primary
                                    )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 功能类型列表
                items(FunctionType.values()) { functionType ->
                    val currentConfigMapping =
                            configMappingWithIndex.value[functionType]
                                    ?: FunctionConfigMapping(FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)
                    val currentConfig = configSummaries.find { it.id == currentConfigMapping.configId }

                    FunctionConfigCard(
                            functionType = functionType,
                            currentConfig = currentConfig,
                            currentModelIndex = currentConfigMapping.modelIndex,
                            availableConfigs = configSummaries,
                            onConfigSelected = { configId, modelIndex ->
                                scope.launch {
                                    functionalConfigManager.setConfigForFunction(
                                            functionType,
                                            configId,
                                            modelIndex
                                    )
                                    // 刷新服务实例
                                    EnhancedAIService.refreshServiceForFunction(
                                            context,
                                            functionType
                                    )
                                    showSaveSuccess = true
                                }
                            }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    // 重置按钮
                    OutlinedButton(
                            onClick = {
                                scope.launch {
                                    functionalConfigManager.resetAllFunctionConfigs()
                                    // 刷新所有服务实例
                                    EnhancedAIService.refreshAllServices(context)
                                    showSaveSuccess = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.reset_all_functions_to_default))
                    }

                    // 成功提示
                    androidx.compose.animation.AnimatedVisibility(
                            visible = showSaveSuccess,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primaryContainer
                                        )
                        ) {
                            Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = stringResource(id = R.string.config_saved),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LaunchedEffect(showSaveSuccess) {
                            kotlinx.coroutines.delay(2000)
                            showSaveSuccess = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FunctionConfigCard(
        functionType: FunctionType,
        currentConfig: ModelConfigSummary?,
        currentModelIndex: Int,
        availableConfigs: List<ModelConfigSummary>,
        onConfigSelected: (String, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var expandedConfigId by remember { mutableStateOf<String?>(null) } // 记录当前展开的配置的模型列表
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelConfigManager = remember { ModelConfigManager(context) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }

    var mediaSupportWarningResId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(functionType, currentConfig?.id) {
        mediaSupportWarningResId = null
        if (
            functionType != FunctionType.IMAGE_RECOGNITION &&
            functionType != FunctionType.AUDIO_RECOGNITION &&
            functionType != FunctionType.VIDEO_RECOGNITION
        ) {
            return@LaunchedEffect
        }

        val configId = currentConfig?.id ?: FunctionalConfigManager.DEFAULT_CONFIG_ID
        val fullConfig: ModelConfigData = try {
            modelConfigManager.getModelConfigFlow(configId).first()
        } catch (e: Exception) {
            return@LaunchedEffect
        }

        val isSupported = when (functionType) {
            FunctionType.IMAGE_RECOGNITION -> fullConfig.enableDirectImageProcessing
            FunctionType.AUDIO_RECOGNITION -> fullConfig.enableDirectAudioProcessing
            FunctionType.VIDEO_RECOGNITION -> fullConfig.enableDirectVideoProcessing
            else -> true
        }

        if (!isSupported) {
            mediaSupportWarningResId = when (functionType) {
                FunctionType.IMAGE_RECOGNITION -> R.string.functional_config_warning_image_unsupported
                FunctionType.AUDIO_RECOGNITION -> R.string.functional_config_warning_audio_unsupported
                FunctionType.VIDEO_RECOGNITION -> R.string.functional_config_warning_video_unsupported
                else -> null
            }
        }
    }

    val showAutoGlmError: () -> Unit = {
        if (functionType == FunctionType.CHAT) {
            Toast.makeText(
                context,
                context.getString(R.string.chat_autoglm_warning),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(testResult) {
        if (testResult != null) {
            kotlinx.coroutines.delay(5000)
            testResult = null
        }
    }

    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border =
                    BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 功能标题和描述
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = getFunctionDisplayName(functionType),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )

                Text(
                        text = getFunctionDescription(functionType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 当前配置
                Surface(
                        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text = stringResource(
                                        id = R.string.current_config_label,
                                        currentConfig?.name ?: stringResource(id = R.string.default_config)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                            )

                            if (currentConfig != null) {
                                val modelList = getModelList(currentConfig.modelName)
                                val displayModel = if (modelList.size > 1) {
                                    getModelByIndex(currentConfig.modelName, currentModelIndex)
                                } else {
                                    currentConfig.modelName
                                }
                                Text(
                                        text = stringResource(id = R.string.model_label, displayModel),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = mediaSupportWarningResId != null
                            ) {
                                val warningResId = mediaSupportWarningResId
                                if (warningResId != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(id = warningResId),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            
                            // 测试结果显示
                            androidx.compose.animation.AnimatedVisibility(
                                visible = testResult != null,
                                enter = fadeIn() + slideInHorizontally(),
                                exit = fadeOut() + slideOutHorizontally()
                            ) {
                                testResult?.let { result ->
                                    val isSuccess = result.isSuccess
                                    val message =
                                            if (isSuccess) result.getOrNull() ?: stringResource(id = R.string.test_connection_success)
                                            else stringResource(id = R.string.test_connection_failed, result.exceptionOrNull()?.message?.take(30) ?: "")
                                    val color =
                                            if (isSuccess) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                    val icon =
                                            if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                                icon,
                                                contentDescription = null,
                                                tint = color,
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                                message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = color,
                                                maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 测试按钮
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isTestingConnection = true
                                        testResult = null
                                        val cleanupTasks = mutableListOf<() -> Unit>()
                                        try {
                                            val configId =
                                                    currentConfig?.id
                                                            ?: FunctionalConfigManager.DEFAULT_CONFIG_ID
                                            val fullConfig =
                                                    modelConfigManager.getModelConfigFlow(configId).first()

                                            // 根据 modelIndex 选择具体的模型
                                            val actualIndex = getValidModelIndex(fullConfig.modelName, currentModelIndex)
                                            val selectedModelName = getModelByIndex(fullConfig.modelName, actualIndex)
                                            val configWithSelectedModel = fullConfig.copy(modelName = selectedModelName)

                                            val service =
                                                    AIServiceFactory.createService(
                                                            config = configWithSelectedModel,
                                                            modelConfigManager = modelConfigManager,
                                                            context = context
                                                    )

                                            val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
                                            val result = when (functionType) {
                                                FunctionType.SUMMARY -> {
                                                    val enhancedService = EnhancedAIService.getInstance(context)
                                                    val sampleMessages =
                                                        listOf(
                                                            "user" to "Connection test: summarize this short dialog.",
                                                            "assistant" to "Sure, I can help with summaries."
                                                        )
                                                    enhancedService.generateSummary(sampleMessages)
                                                }
                                                FunctionType.TITLE_GENERATION -> {
                                                    val enhancedService = EnhancedAIService.getInstance(context)
                                                    enhancedService.generateConversationTitle(
                                                        userText = context.getString(R.string.functional_config_test_title_generation_user_text)
                                                    )
                                                }
                                                FunctionType.TRANSLATION -> {
                                                    val enhancedService = EnhancedAIService.getInstance(context)
                                                    enhancedService.translateText("Connection test: translate me.")
                                                }
                                                FunctionType.IMAGE_RECOGNITION -> {
                                                    val imageFile = AssetCopyUtils.copyAssetToCache(context, "test/1.jpg")
                                                    val imageId = ImagePoolManager.addImage(imageFile.absolutePath)
                                                    if (imageId == "error") {
                                                        throw IllegalStateException("Failed to create test image")
                                                    }
                                                    cleanupTasks.add {
                                                        ImagePoolManager.removeImage(imageId)
                                                        runCatching { imageFile.delete() }
                                                    }
                                                    val prompt =
                                                        buildString {
                                                            append(MediaLinkBuilder.image(context, imageId))
                                                            append("\n")
                                                            append(context.getString(R.string.conversation_analyze_image_prompt))
                                                        }
                                                    val parameters =
                                                        modelConfigManager.getModelParametersForConfig(configWithSelectedModel.id)
                                                    val buffer = StringBuilder()
                                                    service.sendMessage(
                                                        context,
                                                        listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                                                        parameters,
                                                        stream = false,
                                                        enableRetry = false
                                                    )
                                                        .collect { chunk -> buffer.append(chunk) }
                                                    buffer.toString()
                                                }
                                                FunctionType.AUDIO_RECOGNITION -> {
                                                    val audioFile = AssetCopyUtils.copyAssetToCache(context, "test/1.mp3")
                                                    val audioId = MediaPoolManager.addMedia(audioFile.absolutePath, "audio/mpeg")
                                                    if (audioId == "error") {
                                                        throw IllegalStateException("Failed to create test audio")
                                                    }
                                                    cleanupTasks.add {
                                                        MediaPoolManager.removeMedia(audioId)
                                                        runCatching { audioFile.delete() }
                                                    }
                                                    val prompt =
                                                        buildString {
                                                            append(MediaLinkBuilder.audio(context, audioId))
                                                            append("\n")
                                                            append(context.getString(R.string.conversation_analyze_audio_prompt))
                                                        }
                                                    val parameters =
                                                        modelConfigManager.getModelParametersForConfig(configWithSelectedModel.id)
                                                    val buffer = StringBuilder()
                                                    service.sendMessage(
                                                        context,
                                                        listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                                                        parameters,
                                                        stream = false,
                                                        enableRetry = false
                                                    )
                                                        .collect { chunk -> buffer.append(chunk) }
                                                    buffer.toString()
                                                }
                                                FunctionType.VIDEO_RECOGNITION -> {
                                                    val videoFile = AssetCopyUtils.copyAssetToCache(context, "test/1.mp4")
                                                    val videoId = MediaPoolManager.addMedia(videoFile.absolutePath, "video/mp4")
                                                    if (videoId == "error") {
                                                        throw IllegalStateException("Failed to create test video")
                                                    }
                                                    cleanupTasks.add {
                                                        MediaPoolManager.removeMedia(videoId)
                                                        runCatching { videoFile.delete() }
                                                    }
                                                    val prompt =
                                                        buildString {
                                                            append(MediaLinkBuilder.video(context, videoId))
                                                            append("\n")
                                                            append(context.getString(R.string.conversation_analyze_video_prompt))
                                                        }
                                                    val parameters =
                                                        modelConfigManager.getModelParametersForConfig(configWithSelectedModel.id)
                                                    val buffer = StringBuilder()
                                                    service.sendMessage(
                                                        context,
                                                        listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                                                        parameters,
                                                        stream = false,
                                                        enableRetry = false
                                                    )
                                                        .collect { chunk -> buffer.append(chunk) }
                                                    buffer.toString()
                                                }
                                                FunctionType.GREP -> {
                                                    val prompt =
                                                        FunctionalPrompts.grepContextSelectPrompt(
                                                            intent = context.getString(R.string.functional_config_test_grep_intent),
                                                            displayPath = context.getString(R.string.functional_config_test_grep_display_path),
                                                            candidatesDigest = context.getString(R.string.functional_config_test_grep_candidates_digest),
                                                            maxResults = 1,
                                                            useEnglish = useEnglish
                                                        )
                                                    val parameters =
                                                        modelConfigManager.getModelParametersForConfig(configWithSelectedModel.id)
                                                    val buffer = StringBuilder()
                                                    service.sendMessage(
                                                        context,
                                                        listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                                                        parameters,
                                                        stream = false,
                                                        enableRetry = false
                                                    )
                                                        .collect { chunk -> buffer.append(chunk) }
                                                    buffer.toString()
                                                }
                                                FunctionType.UI_CONTROLLER -> {
                                                    val systemPrompt = FunctionalPrompts.uiControllerPrompt(useEnglish)
                                                    val userPrompt =
                                                        context.getString(R.string.functional_config_test_ui_controller_prompt)
                                                    val parameters =
                                                        modelConfigManager.getModelParametersForConfig(configWithSelectedModel.id)
                                                    val buffer = StringBuilder()
                                                    service.sendMessage(
                                                        context,
                                                        listOf(
                                                            PromptTurn(kind = PromptTurnKind.SYSTEM, content = systemPrompt),
                                                            PromptTurn(kind = PromptTurnKind.USER, content = userPrompt)
                                                        ),
                                                        parameters,
                                                        stream = false,
                                                        enableRetry = false
                                                    ).collect { chunk -> buffer.append(chunk) }
                                                    buffer.toString()
                                                }
                                                FunctionType.MEMORY -> {
                                                    val existingMemoriesPrompt =
                                                        FunctionalPrompts.knowledgeGraphNoExistingMemoriesMessage(useEnglish)
                                                    val existingFoldersPrompt =
                                                        FunctionalPrompts.knowledgeGraphExistingFoldersPrompt(emptyList(), useEnglish)
                                                    val systemPrompt =
                                                        FunctionalPrompts.buildKnowledgeGraphExtractionPrompt(
                                                            duplicatesPromptPart = "",
                                                            existingMemoriesPrompt = existingMemoriesPrompt,
                                                            existingFoldersPrompt = existingFoldersPrompt,
                                                            useEnglish = useEnglish
                                                        )
                                                    val userPrompt =
                                                        context.getString(R.string.functional_config_test_memory_prompt)
                                                    val parameters =
                                                        modelConfigManager.getModelParametersForConfig(configWithSelectedModel.id)
                                                    val buffer = StringBuilder()
                                                    service.sendMessage(
                                                        context,
                                                        listOf(
                                                            PromptTurn(kind = PromptTurnKind.SYSTEM, content = systemPrompt),
                                                            PromptTurn(kind = PromptTurnKind.USER, content = userPrompt)
                                                        ),
                                                        parameters,
                                                        stream = false,
                                                        enableRetry = false
                                                    ).collect { chunk -> buffer.append(chunk) }
                                                    buffer.toString()
                                                }
                                                FunctionType.CHAT -> {
                                                    val parameters =
                                                        modelConfigManager.getModelParametersForConfig(configWithSelectedModel.id)
                                                    val buffer = StringBuilder()
                                                    service.sendMessage(
                                                        context,
                                                        listOf(PromptTurn(kind = PromptTurnKind.USER, content = "Hi")),
                                                        parameters,
                                                        stream = false,
                                                        enableRetry = false
                                                    )
                                                        .collect { chunk -> buffer.append(chunk) }
                                                    buffer.toString()
                                                }
                                                FunctionType.ROLE_RESPONSE_PLANNER -> {
                                                    val parameters =
                                                        modelConfigManager.getModelParametersForConfig(configWithSelectedModel.id)
                                                    val prompt =
                                                        "Connection test: return {\"order\":[{\"id\":\"test\",\"speak\":true}]} only."
                                                    val buffer = StringBuilder()
                                                    service.sendMessage(
                                                        context,
                                                        listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                                                        parameters,
                                                        stream = false,
                                                        enableRetry = false
                                                    )
                                                        .collect { chunk -> buffer.append(chunk) }
                                                    buffer.toString()
                                                }
                                            }
                                            testResult = Result.success(result)
                                        } catch (e: Exception) {
                                            testResult = Result.failure(e)
                                        } finally {
                                            cleanupTasks.forEach { task -> runCatching { task() } }
                                        }
                                        isTestingConnection = false
                                    }
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                            Icons.Default.Dns,
                                            contentDescription = stringResource(id = R.string.test_connection_desc),
                                            modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(id = R.string.test), style = MaterialTheme.typography.bodySmall)
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Icon(
                                    imageVector =
                                            if (expanded) Icons.Default.KeyboardArrowUp
                                            else Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(id = R.string.expand_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 配置列表
            Box(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                                text = stringResource(id = R.string.select_config),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        availableConfigs.forEach { config ->
                            val isSelected =
                                    config.id ==
                                            (currentConfig?.id
                                                    ?: FunctionalConfigManager.DEFAULT_CONFIG_ID)
                            val modelList = getModelList(config.modelName)
                            val hasMultipleModels = modelList.size > 1
                            val isExpanded = expandedConfigId == config.id

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                        modifier =
                                                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                                    if (hasMultipleModels) {
                                                        // 如果有多个模型，切换展开状态
                                                        expandedConfigId = if (isExpanded) null else config.id
                                                    } else {
                                                        // 如果只有一个模型，直接选择
                                                        val singleModelName = modelList.firstOrNull().orEmpty()
                                                        if (functionType == FunctionType.CHAT && singleModelName.contains("autoglm", ignoreCase = true)) {
                                                            showAutoGlmError()
                                                        } else {
                                                            onConfigSelected(config.id, 0)
                                                            expanded = false
                                                        }
                                                    }
                                                },
                                        shape = RoundedCornerShape(8.dp),
                                        color =
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface,
                                        border =
                                                BorderStroke(
                                                        width = if (isSelected) 0.dp else 0.5.dp,
                                                        color =
                                                                if (isSelected)
                                                                        MaterialTheme.colorScheme.primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .outlineVariant.copy(
                                                                                alpha = 0.5f
                                                                        )
                                                )
                                ) {
                                    Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected && !hasMultipleModels) {
                                            Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = stringResource(id = R.string.selected_desc),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                    text = config.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight =
                                                            if (isSelected) FontWeight.Bold
                                                            else FontWeight.Normal,
                                                    color =
                                                            if (isSelected)
                                                                    MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.onSurface
                                            )

                                            if (hasMultipleModels) {
                                                Text(
                                                        text = stringResource(R.string.functional_config_model_count, modelList.size),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Text(
                                                        text = config.modelName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        
                                        if (hasMultipleModels) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                
                                // 如果有多个模型且已展开，显示模型列表
                                androidx.compose.animation.AnimatedVisibility(visible = hasMultipleModels && isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                                    ) {
                                        // 计算有效索引一次，避免重复计算
                                        val validIndex = getValidModelIndex(config.modelName, currentModelIndex)
                                        modelList.forEachIndexed { index, modelName ->
                                            val isModelSelected = isSelected && validIndex == index
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .clickable {
                                                        if (functionType == FunctionType.CHAT && modelName.contains("autoglm", ignoreCase = true)) {
                                                            showAutoGlmError()
                                                        } else {
                                                            onConfigSelected(config.id, index)
                                                            expanded = false
                                                            expandedConfigId = null
                                                        }
                                                    },
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isModelSelected)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isModelSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = stringResource(id = R.string.selected_desc),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                    }
                                                    Text(
                                                        text = modelName,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isModelSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isModelSelected)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
            )
        }
    }
}

// 获取功能类型的显示名称
@Composable
fun getFunctionDisplayName(functionType: FunctionType): String {
    return when (functionType) {
        FunctionType.CHAT -> stringResource(id = R.string.function_type_chat)
        FunctionType.SUMMARY -> stringResource(id = R.string.function_type_summary)
        FunctionType.TITLE_GENERATION -> stringResource(id = R.string.function_type_title_generation)
        FunctionType.MEMORY -> stringResource(id = R.string.function_type_memory)
        FunctionType.UI_CONTROLLER -> stringResource(id = R.string.function_type_ui_controller)
        FunctionType.TRANSLATION -> stringResource(id = R.string.function_type_translation)
        FunctionType.GREP -> stringResource(id = R.string.function_type_grep)
        FunctionType.ROLE_RESPONSE_PLANNER -> stringResource(id = R.string.function_type_role_response_planner)
        FunctionType.IMAGE_RECOGNITION -> stringResource(id = R.string.function_type_image_recognition)
        FunctionType.AUDIO_RECOGNITION -> stringResource(id = R.string.function_type_audio_recognition)
        FunctionType.VIDEO_RECOGNITION -> stringResource(id = R.string.function_type_video_recognition)
    }
}

// 获取功能类型的描述
@Composable
fun getFunctionDescription(functionType: FunctionType): String {
    return  when (functionType) {
        FunctionType.CHAT -> stringResource(id = R.string.function_desc_chat)
        FunctionType.SUMMARY -> stringResource(id = R.string.function_desc_summary)
        FunctionType.TITLE_GENERATION -> stringResource(id = R.string.function_desc_title_generation)
        FunctionType.MEMORY -> stringResource(id = R.string.function_desc_memory)
        FunctionType.UI_CONTROLLER -> stringResource(id = R.string.function_desc_ui_controller)
        FunctionType.TRANSLATION -> stringResource(id = R.string.function_desc_translation)
        FunctionType.GREP -> stringResource(id = R.string.function_desc_grep)
        FunctionType.ROLE_RESPONSE_PLANNER -> stringResource(id = R.string.function_desc_role_response_planner)
        FunctionType.IMAGE_RECOGNITION -> stringResource(id = R.string.function_desc_image_recognition)
        FunctionType.AUDIO_RECOGNITION -> stringResource(id = R.string.function_desc_audio_recognition)
        FunctionType.VIDEO_RECOGNITION -> stringResource(id = R.string.function_desc_video_recognition)
    }
}
