package com.ai.assistance.operit.ui.features.chat.components.style.input.agent

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.library.MemoryAutoSaveScheduler
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.MemoryAutoSaveCandidateRepository
import com.ai.assistance.operit.ui.common.icons.MaterialIconNameResolver
import com.ai.assistance.operit.ui.common.animations.SimpleAnimatedVisibility
import com.ai.assistance.operit.ui.features.chat.components.AttachmentChip
import com.ai.assistance.operit.ui.features.chat.components.AttachmentSelectorPopupPanel
import com.ai.assistance.operit.ui.features.chat.components.FullscreenInputDialog
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.CharacterCardMemoryBindingSwitchConfirmDialog
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.CharacterCardModelBindingSwitchConfirmDialog
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuToggleHookParams
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuToggleDefinition
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuTogglePluginRegistry
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.InputMenuToggleSlots
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.PendingMessageQueuePanel
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.PendingQueueMessageItem
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ToolPromptManagerDialog
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.rememberMentionVisualTransformation
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported
import com.ai.assistance.operit.ui.theme.liquidGlass
import com.ai.assistance.operit.ui.theme.waterGlass
import com.ai.assistance.operit.util.ChatUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AgentChatInputSection(
    actualViewModel: ChatViewModel,
    userMessage: TextFieldValue,
    onUserMessageChange: (TextFieldValue) -> Unit,
    enableEnterToSend: Boolean = false,
    onSendMessage: () -> Unit,
    onQueueMessage: () -> Unit,
    onCancelMessage: () -> Unit,
    isLoading: Boolean,
    inputState: InputProcessingState = InputProcessingState.Idle,
    allowTextInputWhileProcessing: Boolean = false,
    onAttachmentRequest: (String) -> Unit = {},
    attachments: List<AttachmentInfo> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    onInsertAttachment: (AttachmentInfo) -> Unit = {},
    onAttachScreenContent: () -> Unit = {},
    onAttachNotifications: () -> Unit = {},
    onAttachLocation: () -> Unit = {},
    onAttachMemory: () -> Unit = {},
    onAttachPackage: (String) -> Unit = {},
    onTakePhoto: (Uri) -> Unit,
    hasBackgroundImage: Boolean = false,
    chatInputTransparent: Boolean = false,
    chatInputFloating: Boolean = false,
    chatInputLiquidGlass: Boolean = false,
    chatInputWaterGlass: Boolean = false,
    modifier: Modifier = Modifier,
    externalAttachmentPanelState: Boolean? = null,
    onAttachmentPanelStateChange: ((Boolean) -> Unit)? = null,
    showInputProcessingStatus: Boolean = true,
    enableTools: Boolean = true,
    replyToMessage: ChatMessage? = null,
    onClearReply: (() -> Unit)? = null,
    isWorkspaceOpen: Boolean = false,
    enableThinkingMode: Boolean = false,
    onToggleThinkingMode: () -> Unit = {},
    thinkingQualityLevel: Int = ApiPreferences.DEFAULT_THINKING_QUALITY_LEVEL,
    onThinkingQualityLevelChange: (Int) -> Unit = {},
    enableMaxContextMode: Boolean = false,
    onToggleEnableMaxContextMode: () -> Unit = {},
    currentChatId: String?,
    featureStates: Map<String, Boolean> = emptyMap(),
    onToggleFeature: (String) -> Unit = {},
    inputMenuRuntime: String = "main",
    permissionLevel: PermissionLevel = PermissionLevel.ASK,
    onSetPermissionLevel: (PermissionLevel) -> Unit = {},
    enableMemoryAutoUpdate: Boolean = false,
    onToggleMemoryAutoUpdate: () -> Unit = {},
    isAutoReadEnabled: Boolean = false,
    onToggleAutoRead: () -> Unit = {},
    onToggleTools: () -> Unit = {},
    disableStreamOutput: Boolean = false,
    onToggleDisableStreamOutput: () -> Unit = {},
    disableUserPreferenceDescription: Boolean = false,
    onToggleDisableUserPreferenceDescription: () -> Unit = {},
    onNavigateToUserPreferences: () -> Unit = {},
    onNavigateToPackageManager: () -> Unit = {},
    toolPromptVisibility: Map<String, Boolean> = emptyMap(),
    onSaveToolPromptVisibilityMap: (Map<String, Boolean>) -> Unit = {},
    toolOrder: List<String> = emptyList(),
    onSaveToolOrder: (List<String>) -> Unit = {},
    onManualMemoryUpdate: () -> Unit = {},
    onNavigateToModelConfig: () -> Unit = {},
    characterCardBoundChatModelConfigId: String? = null,
    characterCardBoundChatModelIndex: Int = 0,
    characterCardBoundMemoryProfileId: String? = null,
    pendingQueueMessages: List<PendingQueueMessageItem> = emptyList(),
    isPendingQueueExpanded: Boolean = true,
    onPendingQueueExpandedChange: (Boolean) -> Unit = {},
    onDeletePendingQueueMessage: (Long) -> Unit = {},
    onEditPendingQueueMessage: (Long) -> Unit = {},
    onSendPendingQueueMessage: (Long) -> Unit = {},
) {
    val showTokenLimitDialog = remember { mutableStateOf(false) }
    val showFullscreenInput = remember { mutableStateOf(false) }
    val showModelSelectorPopup = remember { mutableStateOf(false) }
    val showExtraSettingsPopup = remember { mutableStateOf(false) }
    var showCharacterCardBindingSwitchConfirm by remember { mutableStateOf(false) }
    var pendingCharacterCardModelSelection by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var showCharacterCardMemoryBindingSwitchConfirm by remember { mutableStateOf(false) }
    var pendingCharacterCardMemorySelection by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val characterCardManager = remember(context) { CharacterCardManager.getInstance(context) }
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    val functionalConfigManager = remember(context) { FunctionalConfigManager(context) }
    val modelConfigManager = remember(context) { ModelConfigManager(context) }
    val userPreferencesManager = remember(context) { UserPreferencesManager.getInstance(context) }
    val isProcessing =
        isLoading ||
            inputState is InputProcessingState.Connecting ||
            inputState is InputProcessingState.ExecutingTool ||
            inputState is InputProcessingState.ToolProgress ||
            inputState is InputProcessingState.Processing ||
            inputState is InputProcessingState.ProcessingToolResult ||
            inputState is InputProcessingState.Summarizing ||
            inputState is InputProcessingState.Receiving
    if (showTokenLimitDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showTokenLimitDialog.value = false
            },
            title = { Text(context.getString(R.string.token_limit_warning)) },
            text = { Text(context.getString(R.string.token_limit_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTokenLimitDialog.value = false
                        onSendMessage()
                    },
                ) { Text(context.getString(R.string.continue_send)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTokenLimitDialog.value = false
                    },
                ) {
                    Text(context.getString(R.string.cancel))
                }
            },
        )
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
                        ),
                    )
                }
                showModelSelectorPopup.value = false
                showCharacterCardBindingSwitchConfirm = false
                pendingCharacterCardModelSelection = null
            }
        },
        onDismiss = {
            showCharacterCardBindingSwitchConfirm = false
            pendingCharacterCardModelSelection = null
        },
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
                        ),
                    )
                    EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
                }
                showCharacterCardMemoryBindingSwitchConfirm = false
                pendingCharacterCardMemorySelection = null
            }
        },
        onDismiss = {
            showCharacterCardMemoryBindingSwitchConfirm = false
            pendingCharacterCardMemorySelection = null
        },
    )

    val inputTextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val mentionVisualTransformation = rememberMentionVisualTransformation(inputTextStyle)
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val toolProgressEvent by ToolProgressBus.progress.collectAsState()
    val currentModelName by actualViewModel.modelName.collectAsState()
    val configMappingWithIndex by
        functionalConfigManager.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())
    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    val activeProfileId by userPreferencesManager.activeMemorySpaceIdFlow.collectAsState(initial = "default")
    var preferenceProfiles by remember { mutableStateOf<List<MemorySpace>>(emptyList()) }
    val currentConfigMapping =
        configMappingWithIndex[FunctionType.CHAT]
            ?: FunctionConfigMapping(FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)
    val isModelSelectionLockedByCharacterCard = !characterCardBoundChatModelConfigId.isNullOrBlank()
    val isMemorySelectionLockedByCharacterCard = !characterCardBoundMemoryProfileId.isNullOrBlank()
    val effectiveConfigMapping =
        if (isModelSelectionLockedByCharacterCard) {
            FunctionConfigMapping(
                characterCardBoundChatModelConfigId ?: FunctionalConfigManager.DEFAULT_CONFIG_ID,
                characterCardBoundChatModelIndex.coerceAtLeast(0),
            )
        } else {
            currentConfigMapping
        }
    val effectiveProfileId =
        if (isMemorySelectionLockedByCharacterCard) {
            characterCardBoundMemoryProfileId ?: activeProfileId
        } else {
            activeProfileId
        }

    LaunchedEffect(Unit) {
        configSummaries = modelConfigManager.getAllConfigSummaries()
        val profileIds = userPreferencesManager.memorySpaceListFlow.first()
        preferenceProfiles =
            profileIds.map { profileId -> userPreferencesManager.getMemorySpaceFlow(profileId).first() }
    }

    LaunchedEffect(showModelSelectorPopup.value) {
        if (showModelSelectorPopup.value) {
            configSummaries = modelConfigManager.getAllConfigSummaries()
        }
    }

    val mappedModelName =
        configSummaries.find { it.id == effectiveConfigMapping.configId }?.let { config ->
            val validIndex = getValidModelIndex(config.modelName, effectiveConfigMapping.modelIndex)
            getModelByIndex(config.modelName, validIndex)
        }
    val displayModelName =
        if (isModelSelectionLockedByCharacterCard) {
            mappedModelName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_selected)
        } else {
            mappedModelName?.takeIf { it.isNotBlank() } ?: currentModelName
        }

    val currentWindowSize by actualViewModel.currentWindowSize.collectAsState()
    val maxWindowSizeInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val baseContextLengthInK by actualViewModel.baseContextLengthInK.collectAsState()
    val maxContextLengthInK by actualViewModel.maxContextLengthInK.collectAsState()
    val maxTokens = (maxWindowSizeInK * 1024).toLong().coerceAtLeast(0L)
    val userMessageTokens = remember(userMessage.text) { ChatUtils.estimateTokenCount(userMessage.text) }
    val projectedTokens = userMessageTokens.toLong() + currentWindowSize

    val isOverTokenLimit =
        if (maxTokens > 0) {
            projectedTokens > maxTokens
        } else {
            false
        }

    val hasDraftText = userMessage.text.isNotBlank()
    val canSendMessage = hasDraftText || attachments.isNotEmpty()
    val showQueueAction = isProcessing && hasDraftText
    val showCancelAction = isProcessing && !showQueueAction
    val sendButtonEnabled = true
    val tokenLimitWarning: @Composable () -> Unit = {
        if (isOverTokenLimit && canSendMessage && !showQueueAction) {
            Text(
                text =
                    context.getString(
                        R.string.token_limit_exceeded_message,
                        projectedTokens,
                        maxTokens,
                    ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 4.dp),
            )
        }
    }

    val voicePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                actualViewModel.launchFloatingModeIn(FloatingMode.FULLSCREEN, colorScheme, typography)
            } else {
                actualViewModel.showToast(context.getString(R.string.microphone_permission_denied_toast))
            }
        }

    val (showAttachmentPanel, setShowAttachmentPanel) =
        remember { mutableStateOf(externalAttachmentPanelState ?: false) }

    LaunchedEffect(externalAttachmentPanelState) {
        externalAttachmentPanelState?.let { setShowAttachmentPanel(it) }
    }

    LaunchedEffect(showAttachmentPanel) {
        onAttachmentPanelStateChange?.invoke(showAttachmentPanel)
    }

    fun handleEnterSendAction() {
        if (!canSendMessage) return
        if (showQueueAction) {
            onQueueMessage()
            setShowAttachmentPanel(false)
            return
        }
        if (isOverTokenLimit) {
            showTokenLimitDialog.value = true
            return
        }
        onSendMessage()
        setShowAttachmentPanel(false)
    }
    val onEnterToSendKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { keyEvent ->
        if (!enableEnterToSend) {
            false
        } else if (
            keyEvent.type == KeyEventType.KeyDown &&
            keyEvent.key == Key.Enter &&
            !keyEvent.isShiftPressed
        ) {
            handleEnterSendAction()
            true
        } else {
            false
        }
    }

    val isDarkTheme = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val darkModeInputColor =
        lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurface,
            0.08f,
        )

    val inputContainerColor =
        when {
            chatInputTransparent -> Color.Transparent
            isDarkTheme && hasBackgroundImage -> darkModeInputColor.copy(alpha = 0.82f)
            isDarkTheme -> darkModeInputColor
            hasBackgroundImage -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            else -> MaterialTheme.colorScheme.surface
        }
    val popupContainerColor =
        when {
            isDarkTheme && chatInputTransparent -> darkModeInputColor
            isDarkTheme -> inputContainerColor
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        }
    val queueContainerColor =
        when {
            chatInputTransparent -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            else -> inputContainerColor
        }
    val queueItemColor =
        when {
            chatInputTransparent -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            isDarkTheme -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            else -> MaterialTheme.colorScheme.surface
        }
    val modelLabel =
        if (displayModelName.isBlank()) {
            context.getString(R.string.model_config)
        } else {
            if (displayModelName.length > 26) {
                displayModelName.take(26) + "..."
            } else {
                displayModelName
            }
        }

    val onSelectModel: (String, Int) -> Unit = { selectedId, modelIndex ->
        if (isModelSelectionLockedByCharacterCard) {
            val currentModelIndex = configSummaries.find { it.id == effectiveConfigMapping.configId }?.let { config ->
                getValidModelIndex(config.modelName, effectiveConfigMapping.modelIndex)
            } ?: effectiveConfigMapping.modelIndex.coerceAtLeast(0)
            val isSameSelection =
                selectedId == effectiveConfigMapping.configId && modelIndex == currentModelIndex
            if (isSameSelection) {
                showModelSelectorPopup.value = false
            } else {
                pendingCharacterCardModelSelection = selectedId to modelIndex
                showModelSelectorPopup.value = false
                showCharacterCardBindingSwitchConfirm = true
            }
        } else {
            scope.launch {
                functionalConfigManager.setConfigForFunction(
                    FunctionType.CHAT,
                    selectedId,
                    modelIndex,
                )
                EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
                showModelSelectorPopup.value = false
            }
        }
    }

    val onModelSelectorClick = {
        showExtraSettingsPopup.value = false
        showModelSelectorPopup.value = !showModelSelectorPopup.value
    }

    val onSelectMemory: (String) -> Unit = { selectedId ->
        if (isMemorySelectionLockedByCharacterCard) {
            val isSameSelection = selectedId == effectiveProfileId
            if (!isSameSelection) {
                pendingCharacterCardMemorySelection = selectedId
                showCharacterCardMemoryBindingSwitchConfirm = true
            }
        } else {
            scope.launch {
                userPreferencesManager.setActiveMemorySpace(selectedId)
                EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
                showExtraSettingsPopup.value = false
            }
        }
    }

    val showProcessingStatus =
        showInputProcessingStatus &&
            inputState !is InputProcessingState.Idle &&
            inputState !is InputProcessingState.Completed &&
            inputState !is InputProcessingState.Error

    val (processingProgressColor, processingMessage, processingProgressValue) =
        if (showProcessingStatus) {
            val (progressColor, baseMessage) =
                when (inputState) {
                    is InputProcessingState.Connecting -> MaterialTheme.colorScheme.tertiary to inputState.message
                    is InputProcessingState.ExecutingTool ->
                        MaterialTheme.colorScheme.secondary to
                            context.getString(R.string.executing_tool, inputState.toolName)
                    is InputProcessingState.ToolProgress -> MaterialTheme.colorScheme.secondary to inputState.message
                    is InputProcessingState.Processing -> MaterialTheme.colorScheme.primary to inputState.message
                    is InputProcessingState.ProcessingToolResult ->
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f) to
                            context.getString(R.string.processing_tool_result, inputState.toolName)
                    is InputProcessingState.Summarizing -> MaterialTheme.colorScheme.tertiary to inputState.message
                    is InputProcessingState.Receiving -> MaterialTheme.colorScheme.secondary to inputState.message
                    else -> MaterialTheme.colorScheme.primary to ""
                }

            var message = baseMessage
            var progressValue =
                when (inputState) {
                    is InputProcessingState.Processing -> 0.3f
                    is InputProcessingState.Connecting -> 0.6f
                    is InputProcessingState.Summarizing -> 0.05f
                    is InputProcessingState.ToolProgress -> inputState.progress
                    else -> 1f
                }

            if (inputState is InputProcessingState.ExecutingTool) {
                val event = toolProgressEvent
                if (event != null && inputState.toolName.contains(event.toolName)) {
                    progressValue = event.progress
                    if (event.message.isNotBlank()) {
                        message = event.message
                    }
                }
            }

            if (inputState is InputProcessingState.Summarizing) {
                val event = toolProgressEvent
                if (event != null && event.toolName == ToolProgressBus.SUMMARY_PROGRESS_TOOL_NAME) {
                    progressValue = event.progress
                    if (event.message.isNotBlank()) {
                        message = event.message
                    }
                }
            }

            Triple(progressColor, message, progressValue.coerceIn(0f, 1f))
        } else {
            Triple(MaterialTheme.colorScheme.primary, "", 0f)
        }

    val floatingContainerModifier =
        if (chatInputFloating) {
            modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        } else {
            modifier
        }

    Surface(color = Color.Transparent, modifier = floatingContainerModifier) {
        Column {
            replyToMessage?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = context.getString(R.string.reply_message),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        val previewText =
                            message.content
                                .replace(Regex("<[^>]*>"), "")
                                .trim()
                                .let { if (it.length > 50) it.take(50) + "..." else it }
                        Text(
                            text = "${stringResource(R.string.reply_message)}: $previewText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        IconButton(onClick = { onClearReply?.invoke() }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = context.getString(R.string.cancel_reply),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            PendingMessageQueuePanel(
                queuedMessages = pendingQueueMessages,
                expanded = isPendingQueueExpanded,
                onExpandedChange = onPendingQueueExpandedChange,
                onDeleteMessage = onDeletePendingQueueMessage,
                onEditMessage = onEditPendingQueueMessage,
                onSendMessage = onSendPendingQueueMessage,
                containerColor = queueContainerColor,
                itemColor = queueItemColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            SimpleAnimatedVisibility(
                visible = showProcessingStatus,
            ) {
                if (processingMessage.isNotBlank()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = processingMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (attachments.isNotEmpty()) {
                LazyRow(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    items(attachments) { attachment ->
                        AttachmentChip(
                            attachmentInfo = attachment,
                            onRemove = { onRemoveAttachment(attachment.filePath) },
                            onInsert = { onInsertAttachment(attachment) },
                        )
                    }
                }
            }

            val inputCardShape =
                if (chatInputFloating) {
                    RoundedCornerShape(22.dp)
                } else {
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                }
            val inputLiquidGlassEnabled =
                chatInputTransparent && chatInputLiquidGlass && !chatInputWaterGlass && isLiquidGlassSupported()
            val inputWaterGlassEnabled =
                chatInputTransparent && chatInputWaterGlass && isWaterGlassSupported()
            val inputLiquidGlassTint =
                if (isDarkTheme) {
                    darkModeInputColor
                } else {
                    MaterialTheme.colorScheme.surface
                }
            val inputContainerEffectModifier =
                if (isDarkTheme) {
                    Modifier.topEdgeHighlight(
                        shape = inputCardShape,
                        lineColor =
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (chatInputFloating) 0.03f else 0.05f,
                            ),
                        glowColor =
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (chatInputFloating) 0.008f else 0.015f,
                            ),
                        glowHeight = if (chatInputFloating) 1.dp else 2.dp,
                    )
                } else {
                    Modifier.outerDiffuseShadow(
                        shape = inputCardShape,
                        spread = if (chatInputFloating) 3.dp else 6.dp,
                    )
                }

            if (chatInputTransparent) {
                // 透明模式：暗色顶部高光，亮色保持原阴影
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = if (chatInputFloating) 2.dp else 4.dp)
                            .then(inputContainerEffectModifier)
                            .waterGlass(
                                enabled = inputWaterGlassEnabled,
                                shape = inputCardShape,
                                containerColor = inputLiquidGlassTint,
                                shadowElevation = if (chatInputFloating) 12.dp else 18.dp,
                                borderWidth = 0.7.dp,
                                overlayAlphaBoost = if (chatInputFloating) 0.04f else 0.08f,
                            )
                            .liquidGlass(
                                enabled = inputLiquidGlassEnabled,
                                shape = inputCardShape,
                                containerColor = inputLiquidGlassTint,
                                shadowElevation = if (chatInputFloating) 12.dp else 18.dp,
                                borderWidth = 0.42.dp,
                                blurRadius = if (chatInputFloating) 16.dp else 20.dp,
                                overlayAlphaBoost = if (chatInputFloating) 0.06f else 0.10f,
                            )
                            .clip(inputCardShape)
                            .background(
                                if (inputLiquidGlassEnabled || inputWaterGlassEnabled) {
                                    Color.Transparent
                                } else {
                                    inputContainerColor
                                }
                            ),
                ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = userMessage,
                        onValueChange = onUserMessageChange,
                        visualTransformation = mentionVisualTransformation,
                        placeholder = {
                            Text(
                                if (isWorkspaceOpen) {
                                    context.getString(R.string.input_question_with_workspace)
                                } else {
                                    context.getString(R.string.input_question_hint)
                                },
                                style = inputTextStyle,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).onPreviewKeyEvent(onEnterToSendKeyEvent),
                        textStyle = inputTextStyle,
                        maxLines = 6,
                        minLines = 1,
                        singleLine = false,
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = if (enableEnterToSend) ImeAction.Send else ImeAction.Default
                            ),
                        keyboardActions =
                            if (enableEnterToSend) {
                                KeyboardActions(onSend = { handleEnterSendAction() })
                            } else {
                                KeyboardActions()
                            },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            IconButton(onClick = { showFullscreenInput.value = true }) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = stringResource(R.string.chat_fullscreen_input),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        enabled = !isProcessing || allowTextInputWhileProcessing,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier =
                                    Modifier
                                        .widthIn(min = 0.dp, max = 220.dp)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onModelSelectorClick),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = modelLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 160.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector =
                                            if (showModelSelectorPopup.value) {
                                                Icons.Default.KeyboardArrowUp
                                            } else {
                                                Icons.Default.KeyboardArrowDown
                                            },
                                        contentDescription = context.getString(R.string.select_model_config),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        Box(
                            modifier =
                                Modifier
                                    .padding(start = 6.dp)
                                    .size(34.dp)
                                    .clickable(
                                        enabled = true,
                                        onClick = {
                                            showModelSelectorPopup.value = false
                                            showExtraSettingsPopup.value = !showExtraSettingsPopup.value
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = context.getString(R.string.settings_options),
                                tint =
                                    if (showExtraSettingsPopup.value) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Box(
                            modifier =
                                Modifier
                                    .padding(start = 8.dp)
                                    .size(36.dp)
                                    .clickable(
                                        enabled = true,
                                        onClick = {
                                            showModelSelectorPopup.value = false
                                            showExtraSettingsPopup.value = false
                                            setShowAttachmentPanel(!showAttachmentPanel)
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = context.getString(R.string.add_attachment),
                                tint =
                                    if (showAttachmentPanel) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                    },
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        val actionButtonBackground =
                            when {
                                showCancelAction -> MaterialTheme.colorScheme.error
                                showQueueAction -> MaterialTheme.colorScheme.tertiary
                                canSendMessage ->
                                    if (isOverTokenLimit) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            }

                        val actionButtonIconTint =
                            when {
                                showCancelAction -> MaterialTheme.colorScheme.onError
                                showQueueAction -> MaterialTheme.colorScheme.onTertiary
                                canSendMessage ->
                                    if (isOverTokenLimit) {
                                        MaterialTheme.colorScheme.onSecondary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    }
                                else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            }

                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (showProcessingStatus) {
                                CircularProgressIndicator(
                                    progress = { processingProgressValue },
                                    modifier = Modifier.fillMaxSize(),
                                    color = processingProgressColor,
                                    trackColor = processingProgressColor.copy(alpha = 0.2f),
                                    strokeWidth = 2.dp,
                                )
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .background(actionButtonBackground, CircleShape)
                                        .clickable(
                                            enabled = sendButtonEnabled,
                                            onClick = {
                                                when {
                                                    showCancelAction -> onCancelMessage()
                                                    showQueueAction -> {
                                                        onQueueMessage()
                                                        setShowAttachmentPanel(false)
                                                    }
                                                    canSendMessage -> {
                                                        if (isOverTokenLimit) {
                                                            showTokenLimitDialog.value = true
                                                        } else {
                                                            onSendMessage()
                                                            setShowAttachmentPanel(false)
                                                        }
                                                    }
                                                    else -> {
                                                        actualViewModel.onFloatingButtonClick(
                                                            FloatingMode.FULLSCREEN,
                                                            voicePermissionLauncher,
                                                            colorScheme,
                                                            typography,
                                                        )
                                                    }
                                                }
                                            },
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector =
                                        when {
                                            showCancelAction -> Icons.Default.Close
                                            showQueueAction -> Icons.Default.Add
                                            canSendMessage -> Icons.AutoMirrored.Filled.Send
                                            else -> Icons.Default.Mic
                                        },
                                    contentDescription =
                                        when {
                                            showCancelAction -> context.getString(R.string.cancel)
                                            showQueueAction -> context.getString(R.string.chat_queue_add_message)
                                            canSendMessage -> context.getString(R.string.send)
                                            else -> context.getString(R.string.voice_input)
                                        },
                                    tint = actionButtonIconTint,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    tokenLimitWarning()
                }
            }
            } else {
                // 非透明模式：暗色顶部高光，亮色保持原阴影
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = if (chatInputFloating) 2.dp else 4.dp)
                            .then(inputContainerEffectModifier)
                            .waterGlass(
                                enabled = inputWaterGlassEnabled,
                                shape = inputCardShape,
                                containerColor = inputLiquidGlassTint,
                                shadowElevation = if (chatInputFloating) 12.dp else 18.dp,
                                borderWidth = 0.7.dp,
                                overlayAlphaBoost = if (chatInputFloating) 0.04f else 0.08f,
                            )
                            .liquidGlass(
                                enabled = inputLiquidGlassEnabled,
                                shape = inputCardShape,
                                containerColor = inputLiquidGlassTint,
                                shadowElevation = if (chatInputFloating) 12.dp else 18.dp,
                                borderWidth = 0.42.dp,
                                blurRadius = if (chatInputFloating) 16.dp else 20.dp,
                                overlayAlphaBoost = if (chatInputFloating) 0.06f else 0.10f,
                            )
                            .clip(inputCardShape)
                            .background(
                                if (inputLiquidGlassEnabled || inputWaterGlassEnabled) {
                                    Color.Transparent
                                } else {
                                    inputContainerColor
                                }
                            ),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = userMessage,
                            onValueChange = onUserMessageChange,
                            visualTransformation = mentionVisualTransformation,
                            placeholder = {
                                Text(
                                    if (isWorkspaceOpen) {
                                        context.getString(R.string.input_question_with_workspace)
                                    } else {
                                        context.getString(R.string.input_question_hint)
                                    },
                                    style = inputTextStyle,
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).onPreviewKeyEvent(onEnterToSendKeyEvent),
                            textStyle = inputTextStyle,
                            maxLines = 6,
                            minLines = 1,
                            singleLine = false,
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = if (enableEnterToSend) ImeAction.Send else ImeAction.Default
                                ),
                            keyboardActions =
                                if (enableEnterToSend) {
                                    KeyboardActions(onSend = { handleEnterSendAction() })
                                } else {
                                    KeyboardActions()
                                },
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                ),
                            shape = RoundedCornerShape(14.dp),
                            trailingIcon = {
                                IconButton(onClick = { showFullscreenInput.value = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = stringResource(R.string.chat_fullscreen_input),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            enabled = !isProcessing || allowTextInputWhileProcessing,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Transparent,
                                    modifier =
                                        Modifier
                                            .widthIn(min = 0.dp, max = 220.dp)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(onClick = onModelSelectorClick),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = modelLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 160.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector =
                                                if (showModelSelectorPopup.value) {
                                                    Icons.Default.KeyboardArrowUp
                                                } else {
                                                    Icons.Default.KeyboardArrowDown
                                                },
                                            contentDescription = context.getString(R.string.select_model_config),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                                Box(
                                    modifier =
                                        Modifier
                                            .padding(start = 6.dp)
                                            .size(34.dp)
                                            .clickable(
                                                enabled = true,
                                                onClick = {
                                                    showModelSelectorPopup.value = false
                                                    showExtraSettingsPopup.value = !showExtraSettingsPopup.value
                                                },
                                            ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = context.getString(R.string.settings_options),
                                    tint =
                                        if (showExtraSettingsPopup.value) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                                Box(
                                    modifier =
                                        Modifier
                                            .padding(start = 8.dp)
                                            .size(36.dp)
                                            .clickable(
                                                enabled = true,
                                                onClick = {
                                                    showModelSelectorPopup.value = false
                                                    showExtraSettingsPopup.value = false
                                                    setShowAttachmentPanel(!showAttachmentPanel)
                                                },
                                            ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = context.getString(R.string.add_attachment),
                                    tint =
                                        if (showAttachmentPanel) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                        },
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            val actionButtonBackground =
                                when {
                                    showCancelAction -> MaterialTheme.colorScheme.error
                                    showQueueAction -> MaterialTheme.colorScheme.tertiary
                                    canSendMessage ->
                                        if (isOverTokenLimit) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    else -> MaterialTheme.colorScheme.primary
                                }

                            val actionButtonIconTint =
                                when {
                                    showCancelAction -> MaterialTheme.colorScheme.onError
                                    showQueueAction -> MaterialTheme.colorScheme.onTertiary
                                    canSendMessage ->
                                        if (isOverTokenLimit) {
                                            MaterialTheme.colorScheme.onSecondary
                                        } else {
                                            MaterialTheme.colorScheme.onPrimary
                                        }
                                    else -> MaterialTheme.colorScheme.onPrimary
                                }

                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (showProcessingStatus) {
                                    CircularProgressIndicator(
                                        progress = { processingProgressValue },
                                        modifier = Modifier.fillMaxSize(),
                                        color = processingProgressColor,
                                        trackColor = processingProgressColor.copy(alpha = 0.2f),
                                        strokeWidth = 2.dp,
                                    )
                                }

                                Box(
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .background(actionButtonBackground, CircleShape)
                                            .clickable(
                                                enabled = sendButtonEnabled,
                                                onClick = {
                                                    when {
                                                        showCancelAction -> onCancelMessage()
                                                        showQueueAction -> {
                                                            onQueueMessage()
                                                            setShowAttachmentPanel(false)
                                                        }
                                                        canSendMessage -> {
                                                            if (isOverTokenLimit) {
                                                                showTokenLimitDialog.value = true
                                                            } else {
                                                                onSendMessage()
                                                                setShowAttachmentPanel(false)
                                                            }
                                                        }
                                                        else -> {
                                                            actualViewModel.onFloatingButtonClick(
                                                                FloatingMode.FULLSCREEN,
                                                                voicePermissionLauncher,
                                                                colorScheme,
                                                                typography,
                                                            )
                                                        }
                                                    }
                                                },
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector =
                                            when {
                                                showCancelAction -> Icons.Default.Close
                                                showQueueAction -> Icons.Default.Add
                                                canSendMessage -> Icons.AutoMirrored.Filled.Send
                                                else -> Icons.Default.Mic
                                            },
                                        contentDescription =
                                            when {
                                                showCancelAction -> context.getString(R.string.cancel)
                                                showQueueAction -> context.getString(R.string.chat_queue_add_message)
                                                canSendMessage -> context.getString(R.string.send)
                                                else -> context.getString(R.string.voice_input)
                                            },
                                        tint = actionButtonIconTint,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        tokenLimitWarning()
                    }
                }
            }

            AgentModelSelectorPopup(
                visible = showModelSelectorPopup.value,
                popupContainerColor = popupContainerColor,
                configSummaries = configSummaries,
                currentConfigMapping = effectiveConfigMapping,
                enableThinkingMode = enableThinkingMode,
                onToggleThinkingMode = onToggleThinkingMode,
                thinkingQualityLevel = thinkingQualityLevel,
                onThinkingQualityLevelChange = onThinkingQualityLevelChange,
                enableMaxContextMode = enableMaxContextMode,
                onToggleEnableMaxContextMode = onToggleEnableMaxContextMode,
                baseContextLengthInK = baseContextLengthInK,
                maxContextLengthInK = maxContextLengthInK,
                currentChatId = currentChatId,
                featureStates = featureStates,
                onToggleFeature = onToggleFeature,
                inputMenuRuntime = inputMenuRuntime,
                onSelectModel = onSelectModel,
                onManageModels = {
                    showModelSelectorPopup.value = false
                    onNavigateToModelConfig()
                },
                onDismiss = { showModelSelectorPopup.value = false },
            )

            AgentExtraSettingsPopup(
                visible = showExtraSettingsPopup.value,
                popupContainerColor = popupContainerColor,
                preferenceProfiles = preferenceProfiles,
                currentProfileId = effectiveProfileId,
                onSelectMemory = onSelectMemory,
                onManageMemory = {
                    showExtraSettingsPopup.value = false
                    onNavigateToUserPreferences()
                },
                enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                onToggleMemoryAutoUpdate = onToggleMemoryAutoUpdate,
                currentChatId = currentChatId,
                featureStates = featureStates,
                onToggleFeature = onToggleFeature,
                inputMenuRuntime = inputMenuRuntime,
                isAutoReadEnabled = isAutoReadEnabled,
                onToggleAutoRead = onToggleAutoRead,
                permissionLevel = permissionLevel,
                onSetPermissionLevel = onSetPermissionLevel,
                enableTools = enableTools,
                onToggleTools = onToggleTools,
                disableStreamOutput = disableStreamOutput,
                onToggleDisableStreamOutput = onToggleDisableStreamOutput,
                disableUserPreferenceDescription = disableUserPreferenceDescription,
                onToggleDisableUserPreferenceDescription = onToggleDisableUserPreferenceDescription,
                toolPromptVisibility = toolPromptVisibility,
                onSaveToolPromptVisibilityMap = onSaveToolPromptVisibilityMap,
                toolOrder = toolOrder,
                onSaveToolOrder = onSaveToolOrder,
                onNavigateToPackageManager = onNavigateToPackageManager,
                onManualMemoryUpdate = onManualMemoryUpdate,
                onDismiss = { showExtraSettingsPopup.value = false },
            )

            AttachmentSelectorPopupPanel(
                visible = showAttachmentPanel,
                containerColor = popupContainerColor,
                onAttachImage = { filePath -> onAttachmentRequest(filePath) },
                onAttachFile = { filePath -> onAttachmentRequest(filePath) },
                onAttachScreenContent = onAttachScreenContent,
                onAttachNotifications = onAttachNotifications,
                onAttachLocation = onAttachLocation,
                onAttachMemory = onAttachMemory,
                onAttachPackage = onAttachPackage,
                onTakePhoto = onTakePhoto,
                onDismiss = { setShowAttachmentPanel(false) },
            )

            if (showFullscreenInput.value) {
                FullscreenInputDialog(
                    value = userMessage,
                    onValueChange = onUserMessageChange,
                    onDismiss = { showFullscreenInput.value = false },
                    onConfirm = { showFullscreenInput.value = false },
                )
            }
        }
    }
}

@Composable
private fun AgentModelSelectorPopup(
    visible: Boolean,
    popupContainerColor: Color,
    configSummaries: List<ModelConfigSummary>,
    currentConfigMapping: FunctionConfigMapping,
    enableThinkingMode: Boolean,
    onToggleThinkingMode: () -> Unit,
    thinkingQualityLevel: Int,
    onThinkingQualityLevelChange: (Int) -> Unit,
    enableMaxContextMode: Boolean,
    onToggleEnableMaxContextMode: () -> Unit,
    baseContextLengthInK: Float,
    maxContextLengthInK: Float,
    currentChatId: String?,
    featureStates: Map<String, Boolean>,
    onToggleFeature: (String) -> Unit,
    inputMenuRuntime: String,
    onSelectModel: (String, Int) -> Unit,
    onManageModels: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var showThinkingDropdown by remember { mutableStateOf(false) }
    var infoPopupContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
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
    val currentConfig = configSummaries.find { it.id == currentConfigMapping.configId }
    val currentModelName = currentConfig?.let { config ->
        val validIndex = getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
        getModelByIndex(config.modelName, validIndex)
    }.orEmpty()
    val maxThinkingQualityLevel = ApiPreferences.MAX_THINKING_QUALITY_LEVEL

    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Card(
                modifier =
                    Modifier
                        .padding(bottom = 44.dp, end = 12.dp)
                        .width(300.dp)
                        .heightIn(max = 420.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                shape = RoundedCornerShape(8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = popupContainerColor,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    AgentThinkingSettingsItem(
                        popupContainerColor = popupContainerColor,
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
                                context.getString(R.string.thinking_settings) to
                                    context.getString(R.string.thinking_settings_desc)
                        },
                        onThinkingModeInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.thinking_mode) to
                                    context.getString(R.string.thinking_mode_desc)
                        },
                        onThinkingQualityInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.thinking_quality) to
                                    context.getString(R.string.thinking_quality_desc)
                        },
                        onToggleInfoClick = { title, description ->
                            infoPopupContent = title to description
                        },
                    )
                    inputMenuTogglesBySlot[InputMenuToggleSlots.MODEL].orEmpty().forEach { toggle ->
                        AgentInputMenuToggleSettingItem(
                            toggle = toggle,
                            onInfoClick = { title, description -> infoPopupContent = title to description },
                        )
                    }
                    inputMenuTogglesBySlot[InputMenuToggleSlots.TOOLS].orEmpty().forEach { toggle ->
                        AgentInputMenuToggleSettingItem(
                            toggle = toggle,
                            onInfoClick = { title, description -> infoPopupContent = title to description },
                        )
                    }
                    inputMenuTogglesBySlot[InputMenuToggleSlots.GENERAL].orEmpty().forEach { toggle ->
                        AgentInputMenuToggleSettingItem(
                            toggle = toggle,
                            onInfoClick = { title, description -> infoPopupContent = title to description },
                        )
                    }
                    AgentMaxContextSettingItem(
                        enableMaxContextMode = enableMaxContextMode,
                        onToggleEnableMaxContextMode = onToggleEnableMaxContextMode,
                        onInfoClick = {
                            val normalLengthText =
                                if (baseContextLengthInK % 1f == 0f) {
                                    baseContextLengthInK.toInt().toString()
                                } else {
                                    String.format("%.1f", baseContextLengthInK)
                                }
                            val maxLengthText =
                                if (maxContextLengthInK % 1f == 0f) {
                                    maxContextLengthInK.toInt().toString()
                                } else {
                                    String.format("%.1f", maxContextLengthInK)
                                }
                            infoPopupContent =
                                context.getString(R.string.max_mode_title) to
                                    context.getString(
                                        R.string.max_mode_info,
                                        normalLengthText,
                                        maxLengthText,
                                    )
                        },
                    )
                    AgentModelSelectorItem(
                        popupContainerColor = popupContainerColor,
                        configSummaries = configSummaries,
                        currentConfigMapping = currentConfigMapping,
                        onSelectModel = onSelectModel,
                        expanded = true,
                        onExpandedChange = {},
                        allowCollapse = false,
                        onManageClick = onManageModels,
                        onInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.model_config) to
                                    context.getString(R.string.model_config_desc)
                        },
                    )
                }
            }
            infoPopupContent?.let { content ->
                AgentInfoPopup(
                    popupContainerColor = popupContainerColor,
                    infoPopupContent = content,
                    onDismiss = { infoPopupContent = null },
                )
            }
        }
    }
}

@Composable
private fun AgentThinkingSettingsItem(
    popupContainerColor: Color,
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
    onToggleInfoClick: (String, String) -> Unit,
) {
    val thinkingTypeText =
        when {
            enableThinkingMode -> stringResource(R.string.thinking_type_mode)
            else -> stringResource(R.string.thinking_type_off)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Psychology,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.thinking_settings) + ":",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = thinkingTypeText,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }

    if (expanded) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(popupContainerColor)
                    .padding(horizontal = 12.dp),
        ) {
            AgentThinkingSubSettingItem(
                title = stringResource(R.string.thinking_mode),
                icon = if (enableThinkingMode) Icons.Rounded.Psychology else Icons.Outlined.Psychology,
                iconTint =
                    if (enableThinkingMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                isChecked = enableThinkingMode,
                onToggle = onToggleThinkingMode,
                onInfoClick = onThinkingModeInfoClick,
            )
            if (enableThinkingMode) {
                AgentThinkingSliderSettingItem(
                    label = stringResource(R.string.thinking_quality),
                    value = thinkingQualityLevel,
                    maxThinkingQualityLevel = maxThinkingQualityLevel,
                    onValueChange = onThinkingQualityLevelChange,
                    onInfoClick = onThinkingQualityInfoClick,
                )
            }
            thinkingSlotToggles.forEach { toggle ->
                AgentInputMenuToggleSettingItem(
                    toggle = toggle,
                    onInfoClick = onToggleInfoClick,
                )
            }
        }
    }
}

@Composable
private fun AgentThinkingSliderSettingItem(
    label: String,
    value: Int,
    maxThinkingQualityLevel: Int,
    onValueChange: (Int) -> Unit,
    onInfoClick: () -> Unit,
) {
    var sliderValue by remember { mutableStateOf(value.toFloat()) }

    LaunchedEffect(value, maxThinkingQualityLevel) {
        sliderValue = value.toFloat().coerceIn(
            ApiPreferences.MIN_THINKING_QUALITY_LEVEL.toFloat(),
            maxThinkingQualityLevel.toFloat()
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = sliderValue.roundToInt().coerceIn(
                    ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
                    maxThinkingQualityLevel
                ).toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                onValueChange(
                    sliderValue.roundToInt().coerceIn(
                        ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
                        maxThinkingQualityLevel
                    )
                )
            },
            valueRange =
                ApiPreferences.MIN_THINKING_QUALITY_LEVEL.toFloat()..
                    maxThinkingQualityLevel.toFloat(),
            steps = (maxThinkingQualityLevel -
                ApiPreferences.MIN_THINKING_QUALITY_LEVEL - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AgentThinkingSubSettingItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    isChecked: Boolean,
    onToggle: () -> Unit,
    onInfoClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isChecked) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    } else {
                        Color.Transparent
                    },
                )
                .toggleable(
                    value = isChecked,
                    onValueChange = { onToggle() },
                    role = Role.Switch,
                )
                .heightIn(min = 36.dp)
                .padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (isChecked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Switch(
                checked = isChecked,
                onCheckedChange = null,
                modifier = Modifier.scale(0.65f),
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun AgentMaxContextSettingItem(
    enableMaxContextMode: Boolean,
    onToggleEnableMaxContextMode: () -> Unit,
    onInfoClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .padding(horizontal = 12.dp)
                .toggleable(
                    value = enableMaxContextMode,
                    onValueChange = { onToggleEnableMaxContextMode() },
                    role = Role.Switch,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Whatshot,
            contentDescription = null,
            tint =
                if (enableMaxContextMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
            modifier = Modifier.size(16.dp),
        )
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.max_mode_title),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enableMaxContextMode,
            onCheckedChange = null,
            modifier = Modifier.scale(0.65f),
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
    }
}

@Composable
private fun AgentModelSelectorItem(
    popupContainerColor: Color,
    configSummaries: List<ModelConfigSummary>,
    currentConfigMapping: FunctionConfigMapping,
    onSelectModel: (String, Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    allowCollapse: Boolean = true,
    onManageClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val context = LocalContext.current
    val showAutoGlmError: () -> Unit = {
        Toast.makeText(
            context,
            context.getString(R.string.chat_autoglm_warning),
            Toast.LENGTH_LONG,
        ).show()
    }

    val currentConfig = configSummaries.find { it.id == currentConfigMapping.configId }
    var expandedConfigId by remember { mutableStateOf<String?>(null) }
    val currentModelName =
        currentConfig?.let { config ->
            val validIndex = getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
            getModelByIndex(config.modelName, validIndex)
        } ?: stringResource(R.string.not_selected)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .then(
                    if (allowCollapse) {
                        Modifier.clickable { onExpandedChange(!expanded) }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DataObject,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.model) + ":",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = currentModelName.ifEmpty { stringResource(R.string.not_selected) },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (allowCollapse) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }

    if (expanded) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(popupContainerColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (configSummaries.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_models_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else {
                configSummaries.forEach { config ->
                    val isSelected = config.id == currentConfigMapping.configId
                    val modelList = getModelList(config.modelName)
                    val hasMultipleModels = modelList.size > 1
                    val isExpanded = expandedConfigId == config.id

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .clickable {
                                        if (hasMultipleModels) {
                                            expandedConfigId = if (isExpanded) null else config.id
                                        } else {
                                            val singleModelName = modelList.firstOrNull().orEmpty()
                                            if (singleModelName.contains("autoglm", ignoreCase = true)) {
                                                showAutoGlmError()
                                            } else {
                                                onSelectModel(config.id, 0)
                                                onExpandedChange(false)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = config.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (hasMultipleModels) {
                                    Text(
                                        text = stringResource(R.string.chat_model_count, modelList.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                    )
                                    Icon(
                                        imageVector =
                                            if (isExpanded) {
                                                Icons.Default.KeyboardArrowUp
                                            } else {
                                                Icons.Default.KeyboardArrowDown
                                            },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Text(
                                        text = config.modelName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        if (hasMultipleModels && isExpanded) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(popupContainerColor)
                                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
                            ) {
                                val validIndex =
                                    getValidModelIndex(config.modelName, currentConfigMapping.modelIndex)
                                modelList.forEachIndexed { index, modelName ->
                                    val isModelSelected = isSelected && validIndex == index
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isModelSelected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    } else {
                                                        Color.Transparent
                                                    },
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
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            text = modelName,
                                            fontSize = 12.sp,
                                            fontWeight =
                                                if (isModelSelected) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                },
                                            color =
                                                if (isModelSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (index < modelList.size - 1) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                }
                            }
                        }
                        if (configSummaries.last() != config) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.manage_config),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun AgentExtraSettingsPopup(
    visible: Boolean,
    popupContainerColor: Color,
    preferenceProfiles: List<MemorySpace>,
    currentProfileId: String,
    onSelectMemory: (String) -> Unit,
    onManageMemory: () -> Unit,
    enableMemoryAutoUpdate: Boolean,
    onToggleMemoryAutoUpdate: () -> Unit,
    currentChatId: String?,
    featureStates: Map<String, Boolean>,
    onToggleFeature: (String) -> Unit,
    inputMenuRuntime: String,
    isAutoReadEnabled: Boolean,
    onToggleAutoRead: () -> Unit,
    permissionLevel: PermissionLevel,
    onSetPermissionLevel: (PermissionLevel) -> Unit,
    enableTools: Boolean,
    onToggleTools: () -> Unit,
    disableStreamOutput: Boolean,
    onToggleDisableStreamOutput: () -> Unit,
    disableUserPreferenceDescription: Boolean,
    onToggleDisableUserPreferenceDescription: () -> Unit,
    toolPromptVisibility: Map<String, Boolean>,
    onSaveToolPromptVisibilityMap: (Map<String, Boolean>) -> Unit,
    toolOrder: List<String> = emptyList(),
    onSaveToolOrder: (List<String>) -> Unit = {},
    onNavigateToPackageManager: () -> Unit,
    onManualMemoryUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var showMemoryDropdown by remember { mutableStateOf(false) }
    var showToolPermissionDropdown by remember { mutableStateOf(false) }
    var showBehaviorDropdown by remember { mutableStateOf(false) }
    var showPluginDropdown by remember { mutableStateOf(false) }
    var showToolPromptManagerDialog by remember { mutableStateOf(false) }
    var infoPopupContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
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
    val inputMenuTogglesBySlot = inputMenuToggles.groupBy { toggle -> InputMenuToggleSlots.normalize(toggle.slot) }
    val defaultInputMenuToggles = inputMenuTogglesBySlot[InputMenuToggleSlots.DEFAULT].orEmpty()
    val pluginInputMenuToggles =
        inputMenuTogglesBySlot[InputMenuToggleSlots.GENERAL].orEmpty() + defaultInputMenuToggles
    val scope = rememberCoroutineScope()

    val buildMemoryAutoSaveDetail: suspend () -> String = {
        val pendingCandidateCount =
            MemoryAutoSaveCandidateRepository(context, currentProfileId).countPendingAndFailedCandidates()
        val minutesUntilNextSave =
            MemoryAutoSaveScheduler.getInstance()?.getMinutesUntilNextRun(currentProfileId)
                ?: MemorySearchSettingsPreferences(context, currentProfileId).loadAutoSaveIntervalMinutes().toLong()
        context.getString(
            R.string.memory_auto_update_runtime_status,
            pendingCandidateCount,
            minutesUntilNextSave
        )
    }

    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Card(
                modifier =
                    Modifier
                        .padding(bottom = 44.dp, end = 12.dp)
                        .width(300.dp)
                        .heightIn(max = 420.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                shape = RoundedCornerShape(8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = popupContainerColor,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    AgentMemorySelectorItem(
                        preferenceProfiles = preferenceProfiles,
                        currentProfileId = currentProfileId,
                        onSelectMemory = onSelectMemory,
                        expanded = showMemoryDropdown,
                        onExpandedChange = { showMemoryDropdown = it },
                        onManageClick = onManageMemory,
                        enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                        onToggleMemoryAutoUpdate = onToggleMemoryAutoUpdate,
                        disableUserPreferenceDescription = disableUserPreferenceDescription,
                        onToggleDisableUserPreferenceDescription = onToggleDisableUserPreferenceDescription,
                        onManualMemoryUpdate = {
                            onManualMemoryUpdate()
                            onDismiss()
                        },
                        onInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.memory) to
                                    context.getString(R.string.memory_desc)
                        },
                        onMemoryAutoUpdateInfoClick = {
                            scope.launch {
                                infoPopupContent =
                                    context.getString(R.string.memory_auto_update) to
                                        buildMemoryAutoSaveDetail()
                            }
                        },
                        onDisableUserPreferenceDescriptionInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.disable_user_preference_description) to
                                    context.getString(R.string.disable_user_preference_description_desc)
                        },
                        onManualMemoryUpdateInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.manual_memory_update) to
                                    context.getString(R.string.manual_memory_update_desc)
                        },
                    )

                    inputMenuTogglesBySlot[InputMenuToggleSlots.MEMORY].orEmpty().forEach { toggle ->
                        AgentInputMenuToggleSettingItem(
                            toggle = toggle,
                            onInfoClick = { title, description -> infoPopupContent = title to description },
                        )
                    }

                    AgentToolsPermissionGroupItem(
                        enableTools = enableTools,
                        permissionLevel = permissionLevel,
                        expanded = showToolPermissionDropdown,
                        onExpandedChange = { showToolPermissionDropdown = it },
                        onSelectPermissionLevel = { level ->
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
                        toolSlotToggles = inputMenuTogglesBySlot[InputMenuToggleSlots.TOOLS].orEmpty(),
                        onToggleInfoClick = { title, description -> infoPopupContent = title to description },
                        onManageTools = { showToolPromptManagerDialog = true },
                        onInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.agent_menu_tools) to
                                    context.getString(R.string.auto_approve_desc)
                        },
                    )

                    AgentBehaviorSettingsGroupItem(
                        disableStreamOutput = disableStreamOutput,
                        onToggleDisableStreamOutput = onToggleDisableStreamOutput,
                        isAutoReadEnabled = isAutoReadEnabled,
                        onToggleAutoRead = onToggleAutoRead,
                        expanded = showBehaviorDropdown,
                        onExpandedChange = { showBehaviorDropdown = it },
                        onInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.agent_menu_behavior) to
                                    context.getString(R.string.disable_stream_output_desc)
                        },
                        onDisableStreamOutputInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.disable_stream_output) to
                                    context.getString(R.string.disable_stream_output_desc)
                        },
                        onAutoReadInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.auto_read_message) to
                                    context.getString(R.string.auto_read_desc)
                        },
                    )

                    AgentPluginSettingsGroupItem(
                        toggles = pluginInputMenuToggles,
                        expanded = showPluginDropdown,
                        onExpandedChange = { showPluginDropdown = it },
                        onToggleInfoClick = { title, description -> infoPopupContent = title to description },
                        onInfoClick = {
                            infoPopupContent =
                                context.getString(R.string.agent_menu_plugins) to
                                    context.getString(R.string.manage_packages)
                        },
                    )
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
                    onDismiss()
                    onNavigateToPackageManager()
                },
            )
            infoPopupContent?.let { content ->
                AgentInfoPopup(
                    popupContainerColor = popupContainerColor,
                    infoPopupContent = content,
                    onDismiss = { infoPopupContent = null },
                )
            }
        }
    }
}

@Composable
private fun AgentMemorySelectorItem(
    preferenceProfiles: List<MemorySpace>,
    currentProfileId: String,
    onSelectMemory: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onManageClick: () -> Unit,
    enableMemoryAutoUpdate: Boolean,
    onToggleMemoryAutoUpdate: () -> Unit,
    disableUserPreferenceDescription: Boolean,
    onToggleDisableUserPreferenceDescription: () -> Unit,
    onManualMemoryUpdate: () -> Unit,
    onInfoClick: () -> Unit,
    onMemoryAutoUpdateInfoClick: () -> Unit,
    onDisableUserPreferenceDescriptionInfoClick: () -> Unit,
    onManualMemoryUpdateInfoClick: () -> Unit,
) {
    val currentProfileName =
        preferenceProfiles.find { it.id == currentProfileId }?.name ?: stringResource(R.string.not_selected)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DataObject,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.memory) + ":",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = currentProfileName,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }

    if (expanded) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            preferenceProfiles.forEach { profile ->
                val isSelected = profile.id == currentProfileId
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .clickable { onSelectMemory(profile.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = profile.name,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (preferenceProfiles.last() != profile) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.manage_config),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                )
            }
            AgentSimpleToggleSettingItem(
                title = stringResource(R.string.memory_auto_update),
                icon = if (enableMemoryAutoUpdate) Icons.Rounded.Save else Icons.Outlined.Save,
                isChecked = enableMemoryAutoUpdate,
                onToggle = onToggleMemoryAutoUpdate,
                onInfoClick = onMemoryAutoUpdateInfoClick,
            )
            AgentSimpleToggleSettingItem(
                title = stringResource(R.string.disable_user_preference_description),
                icon = Icons.Outlined.DataObject,
                isChecked = disableUserPreferenceDescription,
                onToggle = onToggleDisableUserPreferenceDescription,
                onInfoClick = onDisableUserPreferenceDescriptionInfoClick,
            )
            AgentActionSettingItem(
                title = stringResource(R.string.manual_memory_update),
                icon = Icons.Outlined.Save,
                onClick = onManualMemoryUpdate,
                onInfoClick = onManualMemoryUpdateInfoClick,
            )
        }
    }
}

@Composable
private fun AgentSettingsGroupHeader(
    title: String,
    value: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun AgentToolsPermissionGroupItem(
    enableTools: Boolean,
    permissionLevel: PermissionLevel,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectPermissionLevel: (PermissionLevel) -> Unit,
    toolSlotToggles: List<InputMenuToggleDefinition>,
    onToggleInfoClick: (String, String) -> Unit,
    onManageTools: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val effectiveLevel = if (enableTools) permissionLevel else PermissionLevel.FORBID
    val valueText =
        when (effectiveLevel) {
            PermissionLevel.ALLOW -> stringResource(R.string.permission_level_allow)
            PermissionLevel.ASK -> stringResource(R.string.permission_level_ask)
            PermissionLevel.FORBID -> stringResource(R.string.agent_menu_permission_disabled)
        }

    AgentSettingsGroupHeader(
        title = stringResource(R.string.agent_menu_tools),
        value = valueText,
        icon = Icons.Outlined.Security,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onInfoClick = onInfoClick,
    )

    if (expanded) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            AgentPermissionSegmentedControl(
                selectedLevel = effectiveLevel,
                onSelectPermissionLevel = onSelectPermissionLevel,
            )
            toolSlotToggles.forEach { toggle ->
                AgentInputMenuToggleSettingItem(
                    toggle = toggle,
                    onInfoClick = onToggleInfoClick,
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageTools),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.manage_tools),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun AgentPermissionSegmentedControl(
    selectedLevel: PermissionLevel,
    onSelectPermissionLevel: (PermissionLevel) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.permission_level),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        listOf(PermissionLevel.FORBID, PermissionLevel.ASK, PermissionLevel.ALLOW).forEach { level ->
            val isSelected = selectedLevel == level
            val label =
                when (level) {
                    PermissionLevel.ALLOW -> stringResource(R.string.permission_level_allow)
                    PermissionLevel.ASK -> stringResource(R.string.permission_level_ask)
                    PermissionLevel.FORBID -> stringResource(R.string.agent_menu_permission_disabled)
                }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                        )
                        .border(
                            1.dp,
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                            },
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 10.dp)
                        .clickable { onSelectPermissionLevel(level) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (level != PermissionLevel.ALLOW) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
        }
    }
}

@Composable
private fun AgentBehaviorSettingsGroupItem(
    disableStreamOutput: Boolean,
    onToggleDisableStreamOutput: () -> Unit,
    isAutoReadEnabled: Boolean,
    onToggleAutoRead: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onDisableStreamOutputInfoClick: () -> Unit,
    onAutoReadInfoClick: () -> Unit,
) {
    val valueText =
        if (disableStreamOutput) {
            stringResource(R.string.agent_menu_behavior_non_streaming)
        } else {
            stringResource(R.string.agent_menu_behavior_streaming)
        }

    AgentSettingsGroupHeader(
        title = stringResource(R.string.agent_menu_behavior),
        value = valueText,
        icon = Icons.Outlined.Speed,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onInfoClick = onInfoClick,
    )

    if (expanded) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp),
        ) {
            AgentSimpleToggleSettingItem(
                title = stringResource(R.string.disable_stream_output),
                icon = if (disableStreamOutput) Icons.Outlined.Block else Icons.Outlined.Speed,
                isChecked = disableStreamOutput,
                onToggle = onToggleDisableStreamOutput,
                onInfoClick = onDisableStreamOutputInfoClick,
            )
            AgentSimpleToggleSettingItem(
                title = stringResource(R.string.auto_read_message),
                icon =
                    if (isAutoReadEnabled) {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    } else {
                        Icons.AutoMirrored.Outlined.VolumeOff
                    },
                isChecked = isAutoReadEnabled,
                onToggle = onToggleAutoRead,
                onInfoClick = onAutoReadInfoClick,
            )
        }
    }
}

@Composable
private fun AgentPluginSettingsGroupItem(
    toggles: List<InputMenuToggleDefinition>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleInfoClick: (String, String) -> Unit,
    onInfoClick: () -> Unit,
) {
    val enabledCount = toggles.count { it.isChecked }
    val valueText = "${enabledCount}/${toggles.size}"

    AgentSettingsGroupHeader(
        title = stringResource(R.string.agent_menu_plugins),
        value = valueText,
        icon = Icons.Outlined.Hub,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onInfoClick = onInfoClick,
    )

    if (expanded) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp),
        ) {
            toggles.forEach { toggle ->
                AgentInputMenuToggleSettingItem(
                    toggle = toggle,
                    onInfoClick = onToggleInfoClick,
                )
            }
        }
    }
}

@Composable
private fun AgentSimpleToggleSettingItem(
    title: String,
    icon: ImageVector,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    onToggle: () -> Unit,
    onInfoClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .toggleable(
                    value = isChecked,
                    enabled = isEnabled,
                    onValueChange = { onToggle() },
                    role = Role.Switch,
                )
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint =
                if (!isEnabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                } else if (isChecked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
            modifier = Modifier.size(16.dp),
        )
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color =
                if (isEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = isChecked,
            onCheckedChange = null,
            enabled = isEnabled,
            modifier = Modifier.scale(0.65f),
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
    }
}

@Composable
private fun AgentInputMenuToggleSettingItem(
    toggle: InputMenuToggleDefinition,
    onInfoClick: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val toggleTitle =
        if (toggle.titleRes != 0) stringResource(toggle.titleRes)
        else toggle.title.orEmpty()
    val toggleIcon = MaterialIconNameResolver.resolveOrDefault(toggle.icon, Icons.Outlined.Hub)
    AgentSimpleToggleSettingItem(
        title = toggleTitle,
        icon = toggleIcon,
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
        },
    )
}

@Composable
private fun AgentActionSettingItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AgentInfoPopup(
    popupContainerColor: Color,
    infoPopupContent: Pair<String, String>,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Card(
                modifier =
                    Modifier
                        .padding(bottom = 52.dp, end = 12.dp)
                        .width(220.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                shape = RoundedCornerShape(8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = popupContainerColor,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = infoPopupContent.first,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = infoPopupContent.second,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

private fun Modifier.outerDiffuseShadow(
    shape: Shape,
    spread: Dp,
    color: Color = Color.Black.copy(alpha = 0.025f),
    layers: Int = 5,
): Modifier =
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val innerPath = outlineToPath(outline)
        val layerCount = layers.coerceAtLeast(1)
        val spreadPx = spread.toPx().coerceAtLeast(0f)

        onDrawWithContent {
            drawContent()
            if (spreadPx <= 0f || color.alpha <= 0f) return@onDrawWithContent

            clipPath(innerPath, clipOp = ClipOp.Difference) {
                when (outline) {
                    is Outline.Generic -> {
                        repeat(layerCount) { index ->
                            val ratio = (index + 1) / layerCount.toFloat()
                            val strokeWidth = spreadPx * ratio * 2f
                            val alpha = color.alpha * (1f - ratio).coerceAtLeast(0f)
                            if (strokeWidth > 0f && alpha > 0f) {
                                drawPath(
                                    path = outline.path,
                                    color = color.copy(alpha = alpha),
                                    style = Stroke(width = strokeWidth),
                                )
                            }
                        }
                    }

                    else -> {
                        repeat(layerCount) { index ->
                            val ratio = (index + 1) / layerCount.toFloat()
                            val expansion = spreadPx * ratio
                            val alpha = color.alpha * (1f - ratio).coerceAtLeast(0f)
                            if (alpha > 0f) {
                                drawPath(
                                    path = expandedOutlinePath(outline, expansion),
                                    color = color.copy(alpha = alpha),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

private fun Modifier.topEdgeHighlight(
    shape: Shape,
    lineColor: Color,
    glowColor: Color,
    lineWidth: Dp = 1.dp,
    glowHeight: Dp = 12.dp,
): Modifier =
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val innerPath = outlineToPath(outline)
        val lineWidthPx = lineWidth.toPx().coerceAtLeast(0f)
        val glowHeightPx = glowHeight.toPx().coerceAtLeast(0f)

        onDrawWithContent {
            drawContent()
            if ((lineColor.alpha <= 0f && glowColor.alpha <= 0f) || size.width <= 0f || size.height <= 0f) {
                return@onDrawWithContent
            }

            clipPath(innerPath) {
                if (glowColor.alpha > 0f && glowHeightPx > 0f) {
                    val glowLayers = 4
                    repeat(glowLayers) { index ->
                        val ratio = (index + 1) / glowLayers.toFloat()
                        val strokeWidth = lineWidthPx + glowHeightPx * ratio * 2f
                        val alpha = glowColor.alpha * (1f - ratio).coerceAtLeast(0f)
                        if (alpha <= 0f || strokeWidth <= 0f) return@repeat

                        when (outline) {
                            is Outline.Rounded -> {
                                drawRoundedTopEdge(
                                    roundRect = outline.roundRect,
                                    color = glowColor.copy(alpha = alpha),
                                    strokeWidth = strokeWidth,
                                )
                            }

                            else -> {
                                drawLine(
                                    color = glowColor.copy(alpha = alpha),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = strokeWidth,
                                )
                            }
                        }
                    }
                }
                if (lineColor.alpha > 0f && lineWidthPx > 0f) {
                    when (outline) {
                        is Outline.Rounded -> {
                            drawRoundedTopEdge(
                                roundRect = outline.roundRect,
                                color = lineColor,
                                strokeWidth = lineWidthPx,
                            )
                        }

                        else -> {
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = lineWidthPx,
                            )
                        }
                    }
                }
            }
        }
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedTopEdge(
    roundRect: RoundRect,
    color: Color,
    strokeWidth: Float,
) {
    val leftRadius = roundRect.topLeftCornerRadius.x.coerceAtLeast(0f)
    val rightRadius = roundRect.topRightCornerRadius.x.coerceAtLeast(0f)
    val y = roundRect.top
    val startX = roundRect.left + leftRadius
    val endX = roundRect.right - rightRadius

    if (endX > startX) {
        drawLine(
            color = color,
            start = Offset(startX, y),
            end = Offset(endX, y),
            strokeWidth = strokeWidth,
        )
    }

    if (leftRadius > 0f) {
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(roundRect.left, roundRect.top),
            size = Size(leftRadius * 2f, leftRadius * 2f),
            style = Stroke(width = strokeWidth),
        )
    }

    if (rightRadius > 0f) {
        drawArc(
            color = color,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(roundRect.right - rightRadius * 2f, roundRect.top),
            size = Size(rightRadius * 2f, rightRadius * 2f),
            style = Stroke(width = strokeWidth),
        )
    }
}

private fun outlineToPath(outline: Outline): Path =
    Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
    }

private fun expandedOutlinePath(outline: Outline, expansion: Float): Path =
    Path().apply {
        when (outline) {
            is Outline.Rectangle -> {
                addRect(
                    Rect(
                        left = outline.rect.left - expansion,
                        top = outline.rect.top - expansion,
                        right = outline.rect.right + expansion,
                        bottom = outline.rect.bottom + expansion,
                    ),
                )
            }

            is Outline.Rounded -> {
                val rounded = outline.roundRect
                addRoundRect(
                    RoundRect(
                        left = rounded.left - expansion,
                        top = rounded.top - expansion,
                        right = rounded.right + expansion,
                        bottom = rounded.bottom + expansion,
                        topLeftCornerRadius = expandCornerRadius(rounded.topLeftCornerRadius, expansion),
                        topRightCornerRadius = expandCornerRadius(rounded.topRightCornerRadius, expansion),
                        bottomRightCornerRadius = expandCornerRadius(rounded.bottomRightCornerRadius, expansion),
                        bottomLeftCornerRadius = expandCornerRadius(rounded.bottomLeftCornerRadius, expansion),
                    ),
                )
            }

            is Outline.Generic -> addPath(outline.path)
        }
    }

private fun expandCornerRadius(base: CornerRadius, expansion: Float): CornerRadius =
    CornerRadius(
        x = (base.x + expansion).coerceAtLeast(0f),
        y = (base.y + expansion).coerceAtLeast(0f),
    )
