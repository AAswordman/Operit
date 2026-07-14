package com.ai.assistance.operit.ui.features.chat.components.style.input.classic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import android.widget.Toast
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.library.MemoryAutoSaveScheduler
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.MemoryAutoSaveCandidateRepository
import com.ai.assistance.operit.ui.common.icons.MaterialIconNameResolver
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuToggleHookParams
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuToggleDefinition
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuTogglePluginRegistry
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuToggleSlots
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.CharacterCardMemoryBindingSwitchConfirmDialog
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.CharacterCardModelBindingSwitchConfirmDialog
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ToolPromptManagerDialog
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import java.text.DecimalFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ai.assistance.operit.R

@Composable
fun ClassicChatSettingsBar(
    modifier: Modifier = Modifier,
    currentChatId: String?,
    featureStates: Map<String, Boolean>,
    onToggleFeature: (String) -> Unit,
    inputMenuRuntime: String = "main",
    permissionLevel: PermissionLevel,
    onSetPermissionLevel: (PermissionLevel) -> Unit,
    enableThinkingMode: Boolean,
    onToggleThinkingMode: () -> Unit,
    thinkingQualityLevel: Int,
    onThinkingQualityLevelChange: (Int) -> Unit,
    maxWindowSizeInK: Float,
    baseContextLengthInK: Float,
    maxContextLengthInK: Float,
    onContextLengthChange: (Float) -> Unit,
    enableMemoryAutoUpdate: Boolean,
    onToggleMemoryAutoUpdate: () -> Unit,
    enableMaxContextMode: Boolean,
    onToggleEnableMaxContextMode: () -> Unit,
    summaryTokenThreshold: Float,
    onSummaryTokenThresholdChange: (Float) -> Unit,
    onNavigateToUserPreferences: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    onNavigateToModelPrompts: () -> Unit,
    onNavigateToPackageManager: () -> Unit,
    isAutoReadEnabled: Boolean,
    onToggleAutoRead: () -> Unit,
    enableTools: Boolean,
    onToggleTools: () -> Unit,
    toolPromptVisibility: Map<String, Boolean>,
    onSaveToolPromptVisibilityMap: (Map<String, Boolean>) -> Unit,
    toolOrder: List<String> = emptyList(),
    onSaveToolOrder: (List<String>) -> Unit = {},
    disableStreamOutput: Boolean,
    onToggleDisableStreamOutput: () -> Unit,
    disableUserPreferenceDescription: Boolean,
    onToggleDisableUserPreferenceDescription: () -> Unit,
    onManualMemoryUpdate: () -> Unit,
    characterCardBoundChatModelConfigId: String? = null,
    characterCardBoundChatModelIndex: Int = 0,
    characterCardBoundMemoryProfileId: String? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val iconScale by
            animateFloatAsState(targetValue = if (showMenu) 1.2f else 1f, label = "iconScale")

    // 用于显示详情说明的状态，现在使用一个Pair来保存标题和内容
    var infoPopupContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showMemorySection by remember { mutableStateOf(false) }
    var showModelSection by remember { mutableStateOf(false) }
    var showToolsSection by remember { mutableStateOf(false) }
    var showBehaviorSection by remember { mutableStateOf(false) }
    var showPluginsSection by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showMemoryDropdown by remember { mutableStateOf(false) }
    var showThinkingDropdown by remember { mutableStateOf(false) }
    var showToolPromptManagerDialog by remember { mutableStateOf(false) }
    var showCharacterCardBindingSwitchConfirm by remember { mutableStateOf(false) }
    var pendingCharacterCardModelSelection by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var showCharacterCardMemoryBindingSwitchConfirm by remember { mutableStateOf(false) }
    var pendingCharacterCardMemorySelection by remember { mutableStateOf<String?>(null) }

    // 将模型选择逻辑封装到组件内部
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    val configMappingWithIndex by
            functionalConfigManager.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())
    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    LaunchedEffect(Unit) { configSummaries = modelConfigManager.getAllConfigSummaries() }
    val currentConfigMapping =
            configMappingWithIndex[FunctionType.CHAT] ?: FunctionConfigMapping(FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)
    val isModelSelectionLockedByCharacterCard = !characterCardBoundChatModelConfigId.isNullOrBlank()
    val isMemorySelectionLockedByCharacterCard = !characterCardBoundMemoryProfileId.isNullOrBlank()
    val effectiveCurrentConfigMapping =
            if (isModelSelectionLockedByCharacterCard) {
                FunctionConfigMapping(
                        characterCardBoundChatModelConfigId
                                ?: FunctionalConfigManager.DEFAULT_CONFIG_ID,
                        characterCardBoundChatModelIndex.coerceAtLeast(0)
                )
            } else {
                currentConfigMapping
            }
    
    // 获取上下文长度设置，用于显示在 MaxMode 描述中
    // 新增：用户偏好（记忆）选择逻辑
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val activeProfileId by
            userPreferencesManager.activeMemorySpaceIdFlow.collectAsState(initial = "default")
    var preferenceProfiles by remember { mutableStateOf<List<MemorySpace>>(emptyList()) }
    val effectiveCurrentProfileId =
        if (isMemorySelectionLockedByCharacterCard) {
            characterCardBoundMemoryProfileId ?: activeProfileId
        } else {
            activeProfileId
        }
    LaunchedEffect(Unit) {
        val profileIds = userPreferencesManager.memorySpaceListFlow.first()
        preferenceProfiles =
                profileIds.map { id -> userPreferencesManager.getMemorySpaceFlow(id).first() }
    }
    
    // 获取聊天设置按钮右边距设置
    val chatSettingsBarRightMargin by
            userPreferencesManager.chatSettingsButtonEndPadding.collectAsState(initial = 2f)
    val inputMenuToggles = InputMenuTogglePluginRegistry.changeVersion.collectAsState().value.let {
        InputMenuTogglePluginRegistry.createToggles(
            params = InputMenuToggleHookParams(
                context = context,
                chatId = currentChatId,
                featureStates = featureStates,
                onToggleFeature = onToggleFeature,
                runtime = inputMenuRuntime
            )
        )
    }
    val inputMenuTogglesBySlot = inputMenuToggles.groupBy { InputMenuToggleSlots.normalize(it.slot) }
    val defaultInputMenuToggles = inputMenuTogglesBySlot[InputMenuToggleSlots.DEFAULT].orEmpty()
    val generalInputMenuToggles = inputMenuTogglesBySlot[InputMenuToggleSlots.GENERAL].orEmpty()
    val pluginToggleCount = generalInputMenuToggles.size + defaultInputMenuToggles.size
    val pluginEnabledCount = (generalInputMenuToggles + defaultInputMenuToggles).count { it.isChecked }
    val currentProfileName =
        preferenceProfiles.find { it.id == effectiveCurrentProfileId }?.name ?: stringResource(R.string.not_selected)
    val currentConfig = configSummaries.find { it.id == effectiveCurrentConfigMapping.configId }
    val currentModelName =
        currentConfig?.let { config ->
            val validIndex = getValidModelIndex(config.modelName, effectiveCurrentConfigMapping.modelIndex)
            getModelByIndex(config.modelName, validIndex).ifEmpty { stringResource(R.string.not_selected) }
        } ?: stringResource(R.string.not_selected)
    val maxThinkingQualityLevel = ApiPreferences.MAX_THINKING_QUALITY_LEVEL
    val toolPermissionText =
        when (if (enableTools) permissionLevel else PermissionLevel.FORBID) {
            PermissionLevel.FORBID -> stringResource(R.string.agent_menu_permission_disabled)
            PermissionLevel.ASK -> stringResource(R.string.permission_level_ask)
            PermissionLevel.ALLOW -> stringResource(R.string.permission_level_allow)
        }
    val behaviorSummary =
        if (disableStreamOutput) {
            stringResource(R.string.agent_menu_behavior_non_streaming)
        } else {
            stringResource(R.string.agent_menu_behavior_streaming)
        }

    val buildMemoryAutoSaveDetail: suspend () -> String = {
        val pendingCandidateCount =
            MemoryAutoSaveCandidateRepository(context, effectiveCurrentProfileId).countPendingAndFailedCandidates()
        val minutesUntilNextSave =
            MemoryAutoSaveScheduler.getInstance()?.getMinutesUntilNextRun(effectiveCurrentProfileId)
                ?: MemorySearchSettingsPreferences(context, effectiveCurrentProfileId).loadAutoSaveIntervalMinutes().toLong()
        context.getString(
            R.string.memory_auto_update_runtime_status,
            pendingCandidateCount,
            minutesUntilNextSave
        )
    }

    val onSelectModel: (String, Int) -> Unit = { selectedId, modelIndex ->
        if (isModelSelectionLockedByCharacterCard) {
            val currentModelIndex =
                configSummaries.find { it.id == effectiveCurrentConfigMapping.configId }?.let { config ->
                    getValidModelIndex(config.modelName, effectiveCurrentConfigMapping.modelIndex)
                } ?: effectiveCurrentConfigMapping.modelIndex.coerceAtLeast(0)
            val isSameSelection =
                selectedId == effectiveCurrentConfigMapping.configId && modelIndex == currentModelIndex
            if (isSameSelection) {
                showModelDropdown = false
            } else {
                pendingCharacterCardModelSelection = selectedId to modelIndex
                showModelDropdown = false
                showCharacterCardBindingSwitchConfirm = true
            }
        } else {
            scope.launch {
                functionalConfigManager.setConfigForFunction(FunctionType.CHAT, selectedId, modelIndex)
                EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
            }
        }
    }

    val onSelectMemory: (String) -> Unit = { selectedId ->
        if (isMemorySelectionLockedByCharacterCard) {
            val isSameSelection = selectedId == effectiveCurrentProfileId
            if (isSameSelection) {
                showMemoryDropdown = false
            } else {
                pendingCharacterCardMemorySelection = selectedId
                showMemoryDropdown = false
                showCharacterCardMemoryBindingSwitchConfirm = true
            }
        } else {
            scope.launch {
                userPreferencesManager.setActiveMemorySpace(selectedId)
                // 用户偏好和记忆库绑定，可能影响AI行为，所以刷新服务
                EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
            }
        }
    }

    CharacterCardModelBindingSwitchConfirmDialog(
        visible = showCharacterCardBindingSwitchConfirm,
        onConfirm = {
            val selection = pendingCharacterCardModelSelection
            if (selection == null) {
                showCharacterCardBindingSwitchConfirm = false
                return@CharacterCardModelBindingSwitchConfirmDialog
            }
            scope.launch {
                val activePrompt = activePromptManager.getActivePrompt()
                val activeCard = when (activePrompt) {
                    is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCard(activePrompt.id)
                    is ActivePrompt.CharacterGroup -> null
                }
                if (activeCard != null) {
                    characterCardManager.updateCharacterCard(
                        activeCard.copy(
                            chatModelBindingMode = CharacterCardChatModelBindingMode.FIXED_CONFIG,
                            chatModelConfigId = selection.first,
                            chatModelIndex = selection.second.coerceAtLeast(0),
                        )
                    )
                }
                showModelDropdown = false
                showCharacterCardBindingSwitchConfirm = false
                pendingCharacterCardModelSelection = null
            }
        },
        onDismiss = {
            showCharacterCardBindingSwitchConfirm = false
            pendingCharacterCardModelSelection = null
        }
    )

    CharacterCardMemoryBindingSwitchConfirmDialog(
        visible = showCharacterCardMemoryBindingSwitchConfirm,
        onConfirm = {
            val profileId = pendingCharacterCardMemorySelection
            if (profileId.isNullOrBlank()) {
                showCharacterCardMemoryBindingSwitchConfirm = false
                return@CharacterCardMemoryBindingSwitchConfirmDialog
            }
            scope.launch {
                val activePrompt = activePromptManager.getActivePrompt()
                val activeCard = when (activePrompt) {
                    is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCard(activePrompt.id)
                    is ActivePrompt.CharacterGroup -> null
                }
                if (activeCard != null) {
                    characterCardManager.updateCharacterCard(
                        activeCard.copy(
                            memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.FIXED_PROFILE,
                            memoryProfileId = profileId,
                        )
                    )
                    EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
                }
                showMemoryDropdown = false
                showCharacterCardMemoryBindingSwitchConfirm = false
                pendingCharacterCardMemorySelection = null
            }
        },
        onDismiss = {
            showCharacterCardMemoryBindingSwitchConfirm = false
            pendingCharacterCardMemorySelection = null
        }
    )

    // The passed modifier will align this Box within its parent.
    Box(modifier = modifier.padding(end = chatSettingsBarRightMargin.dp)) {
        IconButton(
            onClick = {
                showMenu = !showMenu
                if (showMenu) {
                    return@IconButton
                }
                showModelDropdown = false
                showMemoryDropdown = false
                showThinkingDropdown = false
                showMemorySection = false
                showModelSection = false
                showToolsSection = false
                showBehaviorSection = false
                showPluginsSection = false
            },
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 2.dp)
                .size(28.dp)
                .align(Alignment.BottomEnd)
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.settings_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp).scale(iconScale)
            )
        }

        if (showMenu) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = {
                    showMenu = false
                    showModelDropdown = false // 关闭主菜单时也关闭模型菜单
                    showMemoryDropdown = false
                    showThinkingDropdown = false
                    showMemorySection = false
                    showModelSection = false
                    showToolsSection = false
                    showBehaviorSection = false
                    showPluginsSection = false
                },
                    properties =
                            PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(modifier = Modifier.padding(top = 0.dp, bottom = 76.dp)) {
                    Card(
                        modifier = Modifier.width(280.dp),
                        shape = RoundedCornerShape(8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surface.copy(
                                                            alpha = 0.95f
                                                    )
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                                modifier =
                                        Modifier.padding(vertical = 4.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            ClassicSettingsFoldSection(
                                title = stringResource(R.string.memory),
                                value = currentProfileName,
                                icon = Icons.Outlined.Portrait,
                                expanded = showMemorySection,
                                showTopDivider = false,
                                onExpandedChange = {
                                    showMemorySection = it
                                    if (!it) {
                                        showMemoryDropdown = false
                                    }
                                }
                            ) {
                            MemorySelectorItem(
                                preferenceProfiles = preferenceProfiles,
                                currentProfileId = effectiveCurrentProfileId,
                                onSelectMemory = onSelectMemory,
                                expanded = showMemoryDropdown,
                                onExpandedChange = { showMemoryDropdown = it },
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.memory) to context.getString(R.string.memory_desc)
                                        showMenu = false
                                    },
                                    onManageClick = {
                                        onNavigateToUserPreferences()
                                    showMenu = false
                                    }
                            )
                            inputMenuTogglesBySlot[InputMenuToggleSlots.MEMORY].orEmpty().forEach { toggle ->
                                InputMenuToggleSettingItem(
                                    toggle = toggle,
                                    onInfoClick = { title, description ->
                                        infoPopupContent = title to description
                                        showMenu = false
                                    }
                                )
                            }
                            SettingItem(
                                title = stringResource(R.string.memory_auto_update),
                                    icon =
                                            if (enableMemoryAutoUpdate) Icons.Rounded.Save
                                            else Icons.Outlined.Save,
                                    iconTint =
                                            if (enableMemoryAutoUpdate)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = enableMemoryAutoUpdate,
                                onToggle = onToggleMemoryAutoUpdate,
                                onInfoClick = {
                                    scope.launch {
                                        infoPopupContent =
                                            context.getString(R.string.memory_auto_update) to
                                                buildMemoryAutoSaveDetail()
                                    }
                                    showMenu = false
                                }
                            )
                            ActionSettingItem(
                                title = stringResource(R.string.manual_memory_update),
                                icon = Icons.Outlined.Save,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                onClick = {
                                    onManualMemoryUpdate()
                                    showMenu = false
                                },
                                onInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.manual_memory_update) to context.getString(R.string.manual_memory_update_desc)
                                    showMenu = false
                                }
                            )
                            SettingItem(
                                title = stringResource(R.string.disable_user_preference_description),
                                icon =
                                        if (disableUserPreferenceDescription) Icons.Outlined.Block
                                        else Icons.Outlined.Portrait,
                                iconTint =
                                        if (disableUserPreferenceDescription) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                isChecked = disableUserPreferenceDescription,
                                onToggle = onToggleDisableUserPreferenceDescription,
                                onInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.disable_user_preference_description) to
                                            context.getString(R.string.disable_user_preference_description_desc)
                                    showMenu = false
                                }
                            )
                            }

                            ClassicSettingsFoldSection(
                                title = stringResource(R.string.model),
                                value = currentModelName,
                                icon = Icons.Outlined.DataObject,
                                expanded = showModelSection,
                                onExpandedChange = {
                                    showModelSection = it
                                    if (!it) {
                                        showModelDropdown = false
                                        showThinkingDropdown = false
                                    }
                                }
                            ) {
                            ModelSelectorItem(
                                configSummaries = configSummaries,
                                currentConfigMapping = effectiveCurrentConfigMapping,
                                onSelectModel = onSelectModel,
                                expanded = showModelDropdown,
                                    onExpandedChange = { showModelDropdown = it },
                                    onManageClick = {
                                        onNavigateToModelConfig()
                                        showMenu = false
                                    },
                                    onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.model_config) to context.getString(R.string.model_config_desc)
                                        showMenu = false
                                    }
                            )
                            inputMenuTogglesBySlot[InputMenuToggleSlots.MODEL].orEmpty().forEach { toggle ->
                                InputMenuToggleSettingItem(
                                    toggle = toggle,
                                    onInfoClick = { title, description ->
                                        infoPopupContent = title to description
                                        showMenu = false
                                    }
                                )
                            }
                            ThinkingSettingsItem(
                                enableThinkingMode = enableThinkingMode,
                                onToggleThinkingMode = onToggleThinkingMode,
                                thinkingQualityLevel = thinkingQualityLevel,
                                maxThinkingQualityLevel = maxThinkingQualityLevel,
                                onThinkingQualityLevelChange = { level ->
                                    onThinkingQualityLevelChange(
                                        level.coerceIn(
                                            ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
                                            maxThinkingQualityLevel
                                        )
                                    )
                                },
                                thinkingSlotToggles = inputMenuTogglesBySlot[InputMenuToggleSlots.THINKING].orEmpty(),
                                expanded = showThinkingDropdown,
                                onExpandedChange = { showThinkingDropdown = it },
                                onInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.thinking_settings) to context.getString(R.string.thinking_settings_desc)
                                    showMenu = false
                                },
                                onThinkingModeInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.thinking_mode) to context.getString(R.string.thinking_mode_desc)
                                    showMenu = false
                                },
                                onThinkingQualityInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.thinking_quality) to context.getString(R.string.thinking_quality_desc)
                                    showMenu = false
                                },
                                onToggleInfoClick = { title, description ->
                                    infoPopupContent = title to description
                                    showMenu = false
                                }
                            )
                            SettingItem(
                                title = stringResource(R.string.max_mode_title),
                                icon = if (enableMaxContextMode) Icons.Rounded.Whatshot else Icons.Outlined.Whatshot,
                                iconTint = if (enableMaxContextMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                isChecked = enableMaxContextMode,
                                onToggle = onToggleEnableMaxContextMode,
                                onInfoClick = {
                                    val normalLengthText = if (baseContextLengthInK % 1f == 0f) {
                                        baseContextLengthInK.toInt().toString()
                                    } else {
                                        String.format("%.1f", baseContextLengthInK)
                                    }
                                    val maxLengthText = if (maxContextLengthInK % 1f == 0f) {
                                        maxContextLengthInK.toInt().toString()
                                    } else {
                                        String.format("%.1f", maxContextLengthInK)
                                    }
                                    infoPopupContent = context.getString(R.string.max_mode_title) to context.getString(
                                        R.string.max_mode_info,
                                        normalLengthText,
                                        maxLengthText
                                    )
                                    showMenu = false
                                }
                            )
                            }

                            ClassicSettingsFoldSection(
                                title = stringResource(R.string.agent_menu_tools),
                                value = toolPermissionText,
                                icon = Icons.Outlined.Security,
                                expanded = showToolsSection,
                                onExpandedChange = { showToolsSection = it }
                            ) {
                            ToolPermissionSettingItem(
                                enableTools = enableTools,
                                permissionLevel = permissionLevel,
                                onToggleTools = onToggleTools,
                                onSetPermissionLevel = onSetPermissionLevel,
                                onManageToolsClick = {
                                    showToolPromptManagerDialog = true
                                    showMenu = false
                                }
                            )
                            inputMenuTogglesBySlot[InputMenuToggleSlots.TOOLS].orEmpty().forEach { toggle ->
                                InputMenuToggleSettingItem(
                                    toggle = toggle,
                                    onInfoClick = { title, description ->
                                        infoPopupContent = title to description
                                        showMenu = false
                                    }
                                )
                            }
                            }

                            ClassicSettingsFoldSection(
                                title = stringResource(R.string.agent_menu_behavior),
                                value = behaviorSummary,
                                icon = Icons.Outlined.Speed,
                                expanded = showBehaviorSection,
                                onExpandedChange = { showBehaviorSection = it }
                            ) {
                            SettingItem(
                                title = stringResource(R.string.disable_stream_output),
                                icon = if (disableStreamOutput) Icons.Outlined.Block else Icons.Outlined.Speed,
                                iconTint =
                                        if (disableStreamOutput) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                isChecked = disableStreamOutput,
                                onToggle = onToggleDisableStreamOutput,
                                onInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.disable_stream_output) to
                                            context.getString(R.string.disable_stream_output_desc)
                                    showMenu = false
                                }
                            )
                            SettingItem(
                                title = stringResource(R.string.auto_read_message),
                                    icon =
                                            if (isAutoReadEnabled) Icons.AutoMirrored.Rounded.VolumeUp
                                            else Icons.AutoMirrored.Outlined.VolumeOff,
                                    iconTint =
                                            if (isAutoReadEnabled)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = isAutoReadEnabled,
                                onToggle = onToggleAutoRead,
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.auto_read_message) to context.getString(R.string.auto_read_desc)
                                    showMenu = false
                                }
                            )
                            }

                            ClassicSettingsFoldSection(
                                title = stringResource(R.string.agent_menu_plugins),
                                value = "$pluginEnabledCount/$pluginToggleCount",
                                icon = Icons.Outlined.Hub,
                                expanded = showPluginsSection,
                                onExpandedChange = { showPluginsSection = it }
                            ) {
                            generalInputMenuToggles.forEach { toggle ->
                                InputMenuToggleSettingItem(
                                    toggle = toggle,
                                    onInfoClick = { title, description ->
                                        infoPopupContent = title to description
                                        showMenu = false
                                    }
                                )
                            }
                            defaultInputMenuToggles.forEach { toggle ->
                                InputMenuToggleSettingItem(
                                    toggle = toggle,
                                    onInfoClick = { title, description ->
                                        infoPopupContent = title to description
                                        showMenu = false
                                    }
                                )
                            }
                            }
                        }
                    }
                }
            }
        }

        ToolPromptManagerDialog(
            visible = showToolPromptManagerDialog,
            toolPromptVisibility = toolPromptVisibility,
            toolOrder = toolOrder,
            onSaveToolPromptVisibilityMap = onSaveToolPromptVisibilityMap,
            onSaveToolOrder = onSaveToolOrder,
            onDismissRequest = { showToolPromptManagerDialog = false },
            onManagePackagesClick = {
                showToolPromptManagerDialog = false
                onNavigateToPackageManager()
            },
        )

        // 详情说明弹窗
        if (infoPopupContent != null) {
            Popup(
                alignment = Alignment.TopStart, // 将弹窗对齐到父布局的左上角
                onDismissRequest = { infoPopupContent = null },
                    properties =
                            PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                        modifier =
                                Modifier.padding(
                                        top = 0.dp,
                                        bottom = 76.dp,
                                        end = 40.dp
                                ) // 调整边距，使其显示在左侧
                ) {
                    Card(
                        modifier = Modifier.width(220.dp),
                        shape = RoundedCornerShape(8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surface.copy(
                                                            alpha = 0.95f
                                                    )
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = infoPopupContent!!.first,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = infoPopupContent!!.second,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    onToggle: () -> Unit,
    onInfoClick: () -> Unit
) {
    val context = LocalContext.current
    val stateDescription = if (isChecked) {
        context.getString(R.string.enabled)
    } else {
        context.getString(R.string.disabled)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp)
            .semantics {
                contentDescription = title
            }
            .toggleable(
                value = isChecked,
                enabled = isEnabled,
                onValueChange = { onToggle() },
                role = Role.Switch
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(16.dp)
                .clearAndSetSemantics {}
        )
        // 详情按钮（左侧）
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        // 文本
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color =
                if (isEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clearAndSetSemantics {}
        )
        // 开关
        Switch(
            checked = isChecked,
            onCheckedChange = null, // 由Row的toggleable处理
            enabled = isEnabled,
            modifier = Modifier
                .scale(0.65f)
                .clearAndSetSemantics {},
                colors =
                        SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingSliderItem(
    label: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    onInfoClick: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    decimalFormatPattern: String,
    unitText: String? = null
) {
    var sliderValue by remember { mutableStateOf(value) }
    val df = remember(decimalFormatPattern) { DecimalFormat(decimalFormatPattern) }
    var textValue by remember { mutableStateOf(df.format(value)) }
    val focusManager = LocalFocusManager.current

    // When the external value changes, sync the internal state
    LaunchedEffect(value) {
        sliderValue = value
        textValue = df.format(value)
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            // Info button
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            BasicTextField(
                value = textValue,
                onValueChange = { newText ->
                    textValue = newText
                    newText.toFloatOrNull()?.let {
                        sliderValue = it.coerceIn(valueRange)
                    }
                },
                modifier = Modifier
                    .width(50.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val finalValue = textValue.toFloatOrNull()?.coerceIn(valueRange) ?: sliderValue
                        onValueChange(finalValue)
                        textValue = df.format(finalValue)
                        focusManager.clearFocus()
                    }
                ),
                singleLine = true
            )

            // Here is the fix for alignment
            Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.CenterStart) {
                if (unitText != null) {
                    Text(
                        text = unitText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                textValue = df.format(it)
            },
            onValueChangeFinished = { onValueChange(sliderValue) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }
}

@Composable
private fun InputMenuToggleSettingItem(
    toggle: InputMenuToggleDefinition,
    onInfoClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val toggleTitle =
        if (toggle.titleRes != 0) stringResource(toggle.titleRes)
        else toggle.title.orEmpty()
    val toggleIcon = MaterialIconNameResolver.resolveOrDefault(toggle.icon, Icons.Outlined.Hub)
    SettingItem(
        title = toggleTitle,
        icon = toggleIcon,
        iconTint =
            if (!toggle.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            else if (toggle.isChecked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        isChecked = toggle.isChecked,
        isEnabled = toggle.isEnabled,
        onToggle = toggle.onToggle,
        onInfoClick = {
            val infoTitle =
                if (toggle.titleRes != 0) context.getString(toggle.titleRes)
                else toggle.title.orEmpty()
            val infoDescription =
                if (toggle.descriptionRes != 0) context.getString(toggle.descriptionRes)
                else toggle.description.orEmpty()
            onInfoClick(infoTitle, infoDescription)
        }
    )
}

@Composable
private fun ClassicSettingsFoldSection(
    title: String,
    value: String,
    icon: ImageVector,
    expanded: Boolean,
    showTopDivider: Boolean = true,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTopDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 2.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
        }
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp).clearAndSetSemantics {}
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector =
                    if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(bottom = 4.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ToolPermissionSettingItem(
    enableTools: Boolean,
    permissionLevel: PermissionLevel,
    onToggleTools: () -> Unit,
    onSetPermissionLevel: (PermissionLevel) -> Unit,
    onManageToolsClick: () -> Unit
) {
    val selectedLevel = if (enableTools) permissionLevel else PermissionLevel.FORBID

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.permission_level),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
            listOf(PermissionLevel.FORBID, PermissionLevel.ASK, PermissionLevel.ALLOW).forEach { level ->
                val isSelected = selectedLevel == level
                val label =
                    when (level) {
                        PermissionLevel.FORBID -> stringResource(R.string.agent_menu_permission_disabled)
                        PermissionLevel.ASK -> stringResource(R.string.permission_level_ask)
                        PermissionLevel.ALLOW -> stringResource(R.string.permission_level_allow)
                    }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp)
                            .clickable {
                                if (!enableTools && level != PermissionLevel.FORBID) {
                                    onToggleTools()
                                }
                                if (enableTools && level == PermissionLevel.FORBID) {
                                    onToggleTools()
                                }
                                if (permissionLevel != level) {
                                    onSetPermissionLevel(level)
                                }
                            },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color =
                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (level != PermissionLevel.ALLOW) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clickable(onClick = onManageToolsClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.manage_tools),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ThinkingSettingsItem(
    enableThinkingMode: Boolean,
    onToggleThinkingMode: () -> Unit,
    thinkingQualityLevel: Int,
    maxThinkingQualityLevel: Int,
    onThinkingQualityLevelChange: (Int) -> Unit,
    thinkingSlotToggles: List<InputMenuToggleDefinition>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onThinkingModeInfoClick: () -> Unit,
    onThinkingQualityInfoClick: () -> Unit,
    onToggleInfoClick: (String, String) -> Unit
) {
    val context = LocalContext.current

    @Composable
    fun ThinkingSubSettingItem(
        title: String,
        icon: ImageVector,
        iconTint: Color,
        isChecked: Boolean,
        onToggle: () -> Unit,
        onInfoClick: () -> Unit
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                    .toggleable(
                        value = isChecked,
                        onValueChange = { onToggle() },
                        role = Role.Switch
                    )
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp).clearAndSetSemantics {}
                )
                IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.details),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).clearAndSetSemantics {}
                )
                Switch(
                    checked = isChecked,
                    onCheckedChange = null,
                    modifier =
                        Modifier.align(Alignment.CenterVertically)
                            .scale(0.65f)
                            .clearAndSetSemantics {},
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }

    val expandStateDesc =
            if (expanded) stringResource(R.string.expanded) else stringResource(R.string.collapsed)

    val thinkingTypeText =
            when {
                enableThinkingMode -> stringResource(R.string.thinking_type_mode)
                else -> stringResource(R.string.thinking_type_off)
            }

    val stateText = buildString {
        append(stringResource(R.string.thinking_mode))
        append(": ")
        append(if (enableThinkingMode) context.getString(R.string.enabled) else context.getString(R.string.disabled))
    }
    val accessibilityDesc =
            "${stringResource(R.string.thinking_settings)}: $thinkingTypeText, $stateText, $expandStateDesc"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .semantics { contentDescription = accessibilityDesc }
                            .clickable { onExpandedChange(!expanded) }
                            .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp).clearAndSetSemantics {}
            )
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.thinking_settings) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = thinkingTypeText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {}
                )
            }
            Icon(
                imageVector =
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp)
            ) {
                ThinkingSubSettingItem(
                    title = stringResource(R.string.thinking_mode),
                    icon =
                        if (enableThinkingMode) Icons.Rounded.Psychology
                        else Icons.Outlined.Psychology,
                    iconTint =
                        if (enableThinkingMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    isChecked = enableThinkingMode,
                    onToggle = onToggleThinkingMode,
                    onInfoClick = onThinkingModeInfoClick
                )

                if (enableThinkingMode) {
                    Box(modifier = Modifier.padding(start = 28.dp)) {
                        SettingSliderItem(
                            label = stringResource(R.string.thinking_quality),
                            icon = Icons.Outlined.Speed,
                            value = thinkingQualityLevel.coerceIn(
                                ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
                                maxThinkingQualityLevel
                            ).toFloat(),
                            onValueChange = { newValue ->
                                val intValue = newValue.toInt().coerceIn(
                                    ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
                                    maxThinkingQualityLevel
                                )
                                onThinkingQualityLevelChange(intValue)
                            },
                            onInfoClick = onThinkingQualityInfoClick,
                            valueRange =
                                ApiPreferences.MIN_THINKING_QUALITY_LEVEL.toFloat()..
                                    maxThinkingQualityLevel.toFloat(),
                            steps = (maxThinkingQualityLevel -
                                ApiPreferences.MIN_THINKING_QUALITY_LEVEL - 1).coerceAtLeast(0),
                            decimalFormatPattern = "0"
                        )
                    }
                }

                thinkingSlotToggles.forEach { toggle ->
                    InputMenuToggleSettingItem(
                        toggle = toggle,
                        onInfoClick = onToggleInfoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun MemorySelectorItem(
    preferenceProfiles: List<MemorySpace>,
    currentProfileId: String,
    onSelectMemory: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onManageClick: () -> Unit
) {
    val currentProfile = preferenceProfiles.find { it.id == currentProfileId }

    val currentProfileName = currentProfile?.name ?: stringResource(R.string.not_selected)
    val expandStateDesc = if (expanded) stringResource(R.string.expanded) else stringResource(R.string.collapsed)
    val accessibilityDesc = "${stringResource(R.string.memory)}: $currentProfileName, $expandStateDesc"
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                .heightIn(min = 36.dp)
                .semantics {
                    contentDescription = accessibilityDesc
                }
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Portrait,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(16.dp)
                    .clearAndSetSemantics {}
            )
            // 详情按钮（左侧）
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.memory) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentProfile?.name ?: stringResource(R.string.not_selected),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {}
                )
            }
            Icon(
                    imageVector =
                            if (expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                preferenceProfiles.forEach { profile ->
                    val isSelected = profile.id == currentProfileId
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                    alpha = 0.1f
                                                            )
                                                    else Color.Transparent
                                            )
                            .clickable {
                                onSelectMemory(profile.id)
                                onExpandedChange(false)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = profile.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (preferenceProfiles.last() != profile) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.manage_config), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ModelSelectorItem(
    configSummaries: List<ModelConfigSummary>,
    currentConfigMapping: FunctionConfigMapping,
    onSelectModel: (String, Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onManageClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val context = LocalContext.current
    val showAutoGlmError: () -> Unit = {
        Toast.makeText(
            context,
            context.getString(R.string.chat_autoglm_warning),
            Toast.LENGTH_LONG
        ).show()
    }

    val currentConfig = configSummaries.find { it.id == currentConfigMapping.configId }
    var expandedConfigId by remember { mutableStateOf<String?>(null) } // 用于记录当前展开的配置的模型列表

    val currentModelName = currentConfig?.let { config ->
        val validIndex = getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
        getModelByIndex(config.modelName, validIndex)
    } ?: stringResource(R.string.not_selected)
    val effectiveExpanded = expanded
    val expandStateDesc =
            if (effectiveExpanded) stringResource(R.string.expanded) else stringResource(R.string.collapsed)
    val accessibilityDesc = "${stringResource(R.string.model)}: $currentModelName, $expandStateDesc"
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                .heightIn(min = 36.dp)
                .semantics {
                    contentDescription = accessibilityDesc
                }
                .clickable {
                    onExpandedChange(!expanded)
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.DataObject,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(16.dp)
                    .clearAndSetSemantics {}
            )
            // 详情按钮（左侧）
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.model) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 只显示选中的模型名称
                currentConfig?.let { config ->
                    val validIndex = getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
                    val selectedModel = getModelByIndex(config.modelName, validIndex)
                    Text(
                        text = selectedModel.ifEmpty { stringResource(R.string.not_selected) },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clearAndSetSemantics {}
                    )
                } ?: Text(
                    text = stringResource(R.string.not_selected),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {}
                )
            }
            Icon(
                imageVector = if (effectiveExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (effectiveExpanded) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                configSummaries.forEach { config ->
                    val isSelected = config.id == currentConfigMapping.configId
                    val modelList = getModelList(config.modelName)
                    val hasMultipleModels = modelList.size > 1
                    val isExpanded = expandedConfigId == config.id
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                        if (isSelected)
                                                                MaterialTheme.colorScheme.primary.copy(
                                                                        alpha = 0.1f
                                                                )
                                                        else Color.Transparent
                                                )
                                .clickable {
                                    if (hasMultipleModels) {
                                        // 如果有多个模型，切换展开状态
                                        expandedConfigId = if (isExpanded) null else config.id
                                    } else {
                                        // 如果只有一个模型，直接选择
                                        val singleModelName = modelList.firstOrNull().orEmpty()
                                        if (singleModelName.contains("autoglm", ignoreCase = true)) {
                                            showAutoGlmError()
                                        } else {
                                            onSelectModel(config.id, 0)
                                            onExpandedChange(false)
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = config.name,
                                        fontWeight =
                                                if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color =
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (hasMultipleModels) {
                                    Text(
                                        text = stringResource(R.string.chat_model_count, modelList.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = config.modelName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        // 如果有多个模型且已展开，显示模型列表
                        if (hasMultipleModels && isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                            ) {
                                // 计算有效索引一次，避免重复计算
                                val validIndex = getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
                                modelList.forEachIndexed { index, modelName ->
                                    val isModelSelected = isSelected && validIndex == index
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isModelSelected)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                if (modelName.contains("autoglm", ignoreCase = true)) {
                                                    showAutoGlmError()
                                                } else {
                                                    onSelectModel(config.id, index)
                                                    onExpandedChange(false)
                                                    expandedConfigId = null
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = modelName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isModelSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isModelSelected) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (index < modelList.size - 1) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (configSummaries.last() != config) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.manage_config), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionSettingItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier =
                Modifier.fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .padding(vertical = 2.dp)
                        .padding(horizontal = 3.dp)
                        .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .semantics {
                            contentDescription = title
                        }
                        .clickable(
                            onClick = onClick,
                            role = Role.Button
                        )
                        .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = iconTint, 
            modifier = Modifier
                .size(16.dp)
                .clearAndSetSemantics {}
        )
        // 详情按钮（左侧）
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        // 文本
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clearAndSetSemantics {}
        )
    }
}
