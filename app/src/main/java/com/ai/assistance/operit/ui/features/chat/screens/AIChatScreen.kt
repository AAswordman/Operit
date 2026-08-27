package com.ai.assistance.operit.ui.features.chat.screens

import android.content.ClipboardManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CodeOff
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.ui.components.ErrorDialog
import com.ai.assistance.operit.ui.features.chat.components.*
import com.ai.assistance.operit.ui.features.chat.components.style.input.agent.AgentChatInputSection
import com.ai.assistance.operit.ui.features.chat.components.style.input.classic.ClassicChatInputSection
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputEvents
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputHookContext
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputHookRegistry
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputSubmitActions
import com.ai.assistance.operit.ui.features.chat.components.style.input.classic.ClassicChatSettingsBar
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.PendingQueueMessageItem
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.features.chat.components.AndroidExportDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportCompleteDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportPlatformDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportProgressDialog
import com.ai.assistance.operit.ui.features.chat.components.WindowsExportDialog
import com.ai.assistance.operit.ui.features.chat.webview.MentionSuggestionPanelStyle
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceScreen
import com.ai.assistance.operit.ui.features.chat.webview.MentionSuggestionPanel
import com.ai.assistance.operit.ui.features.chat.webview.computer.ComputerScreen
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.main.LocalTopBarActions
import com.ai.assistance.operit.ui.main.PendingChatDraftHandler
import com.ai.assistance.operit.ui.main.components.LocalAppBarContentColor
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import com.ai.assistance.operit.ui.main.SharedFileHandler
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.flowOf
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.main.components.LocalSetScreenSoftInputMode
import com.ai.assistance.operit.ui.main.components.LocalSetUseScreenImePadding
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatHistoryDisplayMode
import com.ai.assistance.operit.ui.features.chat.viewmodel.PendingMessageQueueState
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.plugins.chatview.ChatViewEvent
import com.ai.assistance.operit.plugins.chatview.ChatViewHookParams
import com.ai.assistance.operit.plugins.chatview.ChatViewHookPluginRegistry
import java.util.UUID


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun AIChatScreen(
        padding: PaddingValues = PaddingValues(),
        viewModel: ChatViewModel? = null,
        isFloatingMode: Boolean = false,
        embedded: Boolean = false,
        onLoading: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
        hasBackgroundImage: Boolean = false,
        onNavigateToTokenConfig: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        onNavigateToMemoryBase: () -> Unit = {},
        onNavigateToModelConfig: () -> Unit = {},
        onNavigateToOnboardingModelConfig: () -> Unit = {},
        onNavigateToModelPrompts: () -> Unit = {},
        onNavigateToPackageManager: () -> Unit = {},
        onGestureConsumed: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val isCurrentScreen = LocalIsCurrentScreen.current
// Correctly initialize ViewModel using the viewModel() composable function
val actualViewModel: ChatViewModel = viewModel ?: viewModel { ChatViewModel(context.applicationContext) }

    // 设置权限系统的颜色方案
    LaunchedEffect(colorScheme) { actualViewModel.setPermissionSystemColorScheme(colorScheme) }

    // Monitor shared files from external apps
    val sharedFiles by SharedFileHandler.sharedFiles.collectAsState()
    val sharedFileText by SharedFileHandler.sharedFileText.collectAsState()
    val sharedText by SharedFileHandler.sharedText.collectAsState()

    // 添加麦克风权限请求启动器
    val requestMicrophonePermissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    // This launcher is now used inside the ViewModel's permission check flow
                    // It's kept here because it's tied to the composable lifecycle.
                    // The actual logic is now triggered from within the ViewModel after the check.
                } else {
                    // 权限被拒绝
                    android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.microphone_permission_denied),
                                    android.widget.Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }

    // Get background image state
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val themeSnapshot = LocalThemePreferenceSnapshot.current
    val useBackgroundImage = themeSnapshot.useBackgroundImage
    val backgroundImageUri = themeSnapshot.backgroundImageUri
    val chatHeaderTransparent = themeSnapshot.chatHeaderTransparent
    val chatInputTransparent = themeSnapshot.chatInputTransparent
    val chatInputFloating = themeSnapshot.chatInputFloating
    val chatInputLiquidGlassRaw = themeSnapshot.chatInputLiquidGlass
    val chatInputWaterGlass = themeSnapshot.chatInputWaterGlass
    val chatInputLiquidGlass = chatInputLiquidGlassRaw && !chatInputWaterGlass
    val chatHeaderHistoryIconColor = themeSnapshot.chatHeaderHistoryIconColor
    val chatHeaderPipIconColor = themeSnapshot.chatHeaderPipIconColor
    val chatHeaderOverlayMode = themeSnapshot.chatHeaderOverlayMode
    val showInputProcessingStatus = themeSnapshot.showInputProcessingStatus
    val enableEnterToSend by displayPreferencesManager.enableEnterToSend.collectAsState(initial = false)
    val showChatFloatingDotsAnimation = themeSnapshot.showChatFloatingDotsAnimation
    val hasBackgroundImageFromPrefs = useBackgroundImage && backgroundImageUri != null
    val effectiveHasBackgroundImage = hasBackgroundImage || hasBackgroundImageFromPrefs

    // Collect chat style from preferences
    val chatStyleSetting = themeSnapshot.chatStyle
    val chatStyle = remember(chatStyleSetting) {
        when (chatStyleSetting) {
            UserPreferencesManager.CHAT_STYLE_BUBBLE -> ChatStyle.BUBBLE
            else -> ChatStyle.CURSOR
        }
    }
    val inputStyle = themeSnapshot.inputStyle
    val cursorUserBubbleFollowTheme = themeSnapshot.cursorUserBubbleFollowTheme
    val cursorUserBubbleLiquidGlassRaw = themeSnapshot.cursorUserBubbleLiquidGlass
    val cursorUserBubbleWaterGlass = themeSnapshot.cursorUserBubbleWaterGlass
    val cursorUserBubbleLiquidGlass = cursorUserBubbleLiquidGlassRaw && !cursorUserBubbleWaterGlass
    val bubbleUserBubbleLiquidGlassRaw = themeSnapshot.bubbleUserBubbleLiquidGlass
    val bubbleUserBubbleWaterGlass = themeSnapshot.bubbleUserBubbleWaterGlass
    val bubbleUserBubbleLiquidGlass =
        bubbleUserBubbleLiquidGlassRaw && !bubbleUserBubbleWaterGlass
    val bubbleAiBubbleLiquidGlassRaw = themeSnapshot.bubbleAiBubbleLiquidGlass
    val bubbleAiBubbleWaterGlass = themeSnapshot.bubbleAiBubbleWaterGlass
    val bubbleAiBubbleLiquidGlass =
        bubbleAiBubbleLiquidGlassRaw && !bubbleAiBubbleWaterGlass
    val cursorUserBubbleColorValue = themeSnapshot.cursorUserBubbleColor
    val bubbleUserBubbleColorValue = themeSnapshot.bubbleUserBubbleColor
    val bubbleAiBubbleColorValue = themeSnapshot.bubbleAiBubbleColor
    val bubbleUserTextColorValue = themeSnapshot.bubbleUserTextColor
    val bubbleAiTextColorValue = themeSnapshot.bubbleAiTextColor
    val bubbleUserUseImage = themeSnapshot.bubbleUserUseImage
    val bubbleAiUseImage = themeSnapshot.bubbleAiUseImage
    val bubbleUserImageUri = themeSnapshot.bubbleUserImageUri
    val bubbleAiImageUri = themeSnapshot.bubbleAiImageUri
    val bubbleUserImageCropLeft = themeSnapshot.bubbleUserImageCropLeft
    val bubbleUserImageCropTop = themeSnapshot.bubbleUserImageCropTop
    val bubbleUserImageCropRight = themeSnapshot.bubbleUserImageCropRight
    val bubbleUserImageCropBottom = themeSnapshot.bubbleUserImageCropBottom
    val bubbleUserImageRepeatStart = themeSnapshot.bubbleUserImageRepeatStart
    val bubbleUserImageRepeatEnd = themeSnapshot.bubbleUserImageRepeatEnd
    val bubbleUserImageRepeatYStart = themeSnapshot.bubbleUserImageRepeatYStart
    val bubbleUserImageRepeatYEnd = themeSnapshot.bubbleUserImageRepeatYEnd
    val bubbleUserImageScale = themeSnapshot.bubbleUserImageScale
    val bubbleAiImageCropLeft = themeSnapshot.bubbleAiImageCropLeft
    val bubbleAiImageCropTop = themeSnapshot.bubbleAiImageCropTop
    val bubbleAiImageCropRight = themeSnapshot.bubbleAiImageCropRight
    val bubbleAiImageCropBottom = themeSnapshot.bubbleAiImageCropBottom
    val bubbleAiImageRepeatStart = themeSnapshot.bubbleAiImageRepeatStart
    val bubbleAiImageRepeatEnd = themeSnapshot.bubbleAiImageRepeatEnd
    val bubbleAiImageRepeatYStart = themeSnapshot.bubbleAiImageRepeatYStart
    val bubbleAiImageRepeatYEnd = themeSnapshot.bubbleAiImageRepeatYEnd
    val bubbleAiImageScale = themeSnapshot.bubbleAiImageScale
    val bubbleImageRenderMode = themeSnapshot.bubbleImageRenderMode
    val bubbleUserRoundedCornersEnabled = themeSnapshot.bubbleUserRoundedCornersEnabled
    val bubbleAiRoundedCornersEnabled = themeSnapshot.bubbleAiRoundedCornersEnabled
    val bubbleUserContentPaddingLeft = themeSnapshot.bubbleUserContentPaddingLeft
    val bubbleUserContentPaddingRight = themeSnapshot.bubbleUserContentPaddingRight
    val bubbleAiContentPaddingLeft = themeSnapshot.bubbleAiContentPaddingLeft
    val bubbleAiContentPaddingRight = themeSnapshot.bubbleAiContentPaddingRight
    // Collect chat area horizontal padding from preferences
    val chatAreaHorizontalPadding by preferencesManager.chatAreaHorizontalPadding.collectAsState(initial = 16f)

    // 添加编辑按钮和编辑状态
    val editingMessageIndex = remember { mutableStateOf<Int?>(null) }
    val editingMessageContent = remember { mutableStateOf("") }

    // Collect state from ViewModel
    val apiKey by actualViewModel.apiKey.collectAsState()
    val activeChatConfigId by actualViewModel.activeChatConfigId.collectAsState()
    val modelName by actualViewModel.modelName.collectAsState()
    val chatHistory by actualViewModel.chatHistory.collectAsState()
    // 仅对当前会话显示处理中状态（影响“停止/发送”按钮）
    val isLoading by actualViewModel.currentChatIsLoading.collectAsState()
    val errorMessage by actualViewModel.errorMessage.collectAsState()
    // 按会话隔离的输入处理状态（用于进度条文案）
    val inputProcessingState by actualViewModel.currentChatInputProcessingState.collectAsState()

    val featureStates by actualViewModel.featureToggles.collectAsState()
    val enableThinkingMode by actualViewModel.enableThinkingMode.collectAsState() // 收集思考模式状态
    val thinkingQualityLevel by actualViewModel.thinkingQualityLevel.collectAsState()
    val enableMemoryAutoUpdate by actualViewModel.enableMemoryAutoUpdate.collectAsState()
    val enableMaxContextMode by actualViewModel.enableMaxContextMode.collectAsState()
    val enableTools by actualViewModel.enableTools.collectAsState()
    val toolPromptVisibility by actualViewModel.toolPromptVisibility.collectAsState()
    val toolPromptOrder by actualViewModel.toolPromptOrder.collectAsState()
    val disableStreamOutput by actualViewModel.disableStreamOutput.collectAsState()
    val disableUserPreferenceDescription by
            actualViewModel.disableUserPreferenceDescription.collectAsState()
    val summaryTokenThreshold by actualViewModel.summaryTokenThreshold.collectAsState()
    val isAutoReadEnabled by actualViewModel.isAutoReadEnabled.collectAsState()
    val showChatHistorySelector by actualViewModel.showChatHistorySelector.collectAsState()
    val chatHistories by actualViewModel.chatHistories.collectAsState()
    val currentChatId by actualViewModel.currentChatId.collectAsState()
    val hasNewerDisplayHistory by actualViewModel.hasNewerDisplayHistory.collectAsState()
    val isLoadingDisplayWindow by actualViewModel.isLoadingDisplayWindow.collectAsState()
    val popupMessage by actualViewModel.popupMessage.collectAsState()
    val chatViewRuntime = if (isFloatingMode) "floating" else "main"
    val chatViewId = rememberSaveable { UUID.randomUUID().toString() }
    val currentChatView = remember(chatHistories, currentChatId) {
        chatHistories.find { it.id == currentChatId }
    }
    val latestChatViewParams by rememberUpdatedState(
        ChatViewHookParams(
            context = context,
            viewId = chatViewId,
            chatId = currentChatId,
            workspacePath = currentChatView?.workspace,
            workspaceEnv = currentChatView?.workspaceEnv,
            runtime = chatViewRuntime,
            title = currentChatView?.title
        )
    )
    var hasDispatchedChatViewOpen by remember(chatViewId) { mutableStateOf(false) }
    LaunchedEffect(
        chatViewId,
        currentChatId,
        currentChatView?.workspace,
        currentChatView?.workspaceEnv,
        currentChatView?.title,
        chatViewRuntime
    ) {
        val event =
            if (hasDispatchedChatViewOpen) {
                ChatViewEvent.VIEW_UPDATED
            } else {
                hasDispatchedChatViewOpen = true
                ChatViewEvent.VIEW_OPENED
            }
        ChatViewHookPluginRegistry.dispatchAsync(event, latestChatViewParams)
    }
    DisposableEffect(chatViewId) {
        onDispose {
            ChatViewHookPluginRegistry.dispatchAsync(
                ChatViewEvent.VIEW_CLOSED,
                latestChatViewParams
            )
        }
    }
    // 收集滚动事件
    val scrollToBottomEvent = actualViewModel.scrollToBottomEvent
    // 从ViewModel收集新的状态
    val shouldShowConfigDialog by actualViewModel.shouldShowConfigDialog.collectAsState()
    val isWorkspaceOpen by actualViewModel.isWorkspaceOpen.collectAsState()
    val isWorkspacePreparing by actualViewModel.isWorkspacePreparing.collectAsState()

    // 添加模型建议对话框状态
    var showModelSuggestionDialog by remember { mutableStateOf(false) }
    
    // 添加记忆文件夹选择对话框状态
    var showMemoryFolderDialog by remember { mutableStateOf(false) }

    // 当模型名称加载后，检查是否为建议更换的模型
    LaunchedEffect(modelName) {
        if (modelName.isNotBlank() && modelName.contains("deepseek-r1-0528-qwen3-8b:free", ignoreCase = true)) {
            showModelSuggestionDialog = true
        }
    }

    // 模型建议对话框
    if (showModelSuggestionDialog) {
        AlertDialog(
            onDismissRequest = { showModelSuggestionDialog = false },
            title = { Text(stringResource(R.string.model_suggestion_title)) },
            text = { Text(stringResource(R.string.model_suggestion_message)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showModelSuggestionDialog = false
                        // 如果用户已输入token，直接保存配置
                        actualViewModel.updateApiKey("")
                        actualViewModel.updateApiEndpoint(ApiPreferences.DEFAULT_API_ENDPOINT)
                        actualViewModel.updateModelName(ApiPreferences.DEFAULT_MODEL_NAME)
                        actualViewModel.updateApiProviderType(ApiProviderType.DEEPSEEK)
                        actualViewModel.saveApiSettings()

                    }) {
                        Text(stringResource(R.string.change_model))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showModelSuggestionDialog = false }) {
                    Text(stringResource(R.string.ignore))
                }
            }
        )
    }

    SharedIncomingContentHandler(
        sharedFiles = sharedFiles,
        sharedFileText = sharedFileText,
        sharedText = sharedText,
        chatHistories = chatHistories,
        currentChatId = currentChatId,
        onHandleSharedFiles = actualViewModel::handleSharedFiles,
        onHandleSharedText = actualViewModel::handleSharedText,
        onClearSharedFiles = SharedFileHandler::clearSharedFiles,
        onClearSharedText = SharedFileHandler::clearSharedText
    )

    val pendingChatDraft by PendingChatDraftHandler.pendingDraft.collectAsState()
    LaunchedEffect(pendingChatDraft, isCurrentScreen) {
        if (!isCurrentScreen) return@LaunchedEffect
        val draft = pendingChatDraft?.trim().orEmpty()
        if (draft.isBlank()) return@LaunchedEffect

        actualViewModel.createNewChatWithDraft(draft)
        PendingChatDraftHandler.clearPendingDraft()
    }


    // 添加WebView刷新相关状态
    val webViewRefreshCounter by actualViewModel.webViewRefreshCounter.collectAsState()

    // Floating window mode state
    val isFloatingMode by actualViewModel.isFloatingMode.collectAsState()
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // UI state
    val scrollState = rememberScrollState()
    val historyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
    )
    val activeCharacterCard by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCardFlow(prompt.id)
            is ActivePrompt.CharacterGroup -> flowOf(null)
        }
    }.collectAsState(initial = null)
    val activeCharacterGroup by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterGroup -> characterGroupCardManager.getCharacterGroupCardFlow(prompt.id)
            is ActivePrompt.CharacterCard -> flowOf(null)
        }
    }.collectAsState(initial = null)
    var historyDisplayMode by rememberLocal(
        "chat_history_display_mode",
        ChatHistoryDisplayMode.BY_FOLDER
    )
    var autoSwitchCharacterCard by rememberLocal(
        "chat_history_auto_switch_character_card",
        false
    )
    var autoSwitchChatOnCharacterSelect by rememberLocal(
        "chat_history_auto_switch_chat_on_character_select",
        false
    )
    val displayedChatHistories = remember(
        chatHistories,
        activePrompt,
        activeCharacterCard,
        activeCharacterGroup,
        historyDisplayMode
    ) {
        when (historyDisplayMode) {
            ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY -> {
                when (activePrompt) {
                    is ActivePrompt.CharacterGroup -> {
                        val group = activeCharacterGroup ?: return@remember emptyList()
                        chatHistories.filter { history ->
                            history.characterGroupId == group.id
                        }
                    }
                    is ActivePrompt.CharacterCard -> {
                        val activeCard = activeCharacterCard ?: return@remember emptyList()
                        chatHistories.filter { history ->
                            val historyCard = history.characterCardName
                            if (activeCard.isDefault) {
                                historyCard == null || historyCard == activeCard.name
                            } else {
                                historyCard == activeCard.name
                            }
                        }
                    }
                }
            }
            else -> chatHistories
        }
    }
    LaunchedEffect(autoSwitchCharacterCard) {
        actualViewModel.setAutoSwitchCharacterCard(autoSwitchCharacterCard)
    }
    LaunchedEffect(autoSwitchChatOnCharacterSelect) {
        actualViewModel.setAutoSwitchChatOnCharacterSelect(autoSwitchChatOnCharacterSelect)
    }
    LaunchedEffect(
        activePrompt,
        activeCharacterCard,
        activeCharacterGroup,
        displayedChatHistories,
        currentChatId,
        chatHistories,
        historyDisplayMode
    ) {
        if (historyDisplayMode != ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY) {
            return@LaunchedEffect
        }
        val hasActiveTarget = when (activePrompt) {
            is ActivePrompt.CharacterGroup -> activeCharacterGroup != null
            is ActivePrompt.CharacterCard -> activeCharacterCard != null
        }
        if (!hasActiveTarget || displayedChatHistories.isEmpty()) {
            return@LaunchedEffect
        }
        val currentId = currentChatId ?: ""
        if (currentId.isBlank()) {
            actualViewModel.switchChat(displayedChatHistories.first().id)
        }
    }
    val characterCardBoundChatModelConfigId =
        activeCharacterCard
            ?.takeIf {
                activePrompt is ActivePrompt.CharacterCard &&
                    CharacterCardChatModelBindingMode.normalize(it.chatModelBindingMode) ==
                    CharacterCardChatModelBindingMode.FIXED_CONFIG &&
                    !it.chatModelConfigId.isNullOrBlank()
            }
            ?.chatModelConfigId
    val characterCardBoundChatModelIndex =
        activeCharacterCard?.chatModelIndex?.coerceAtLeast(0) ?: 0
    val characterCardBoundMemoryProfileId =
        activeCharacterCard
            ?.takeIf {
                activePrompt is ActivePrompt.CharacterCard &&
                    CharacterCardMemoryProfileBindingMode.normalize(it.memoryProfileBindingMode) ==
                    CharacterCardMemoryProfileBindingMode.FIXED_PROFILE &&
                    !it.memoryProfileId.isNullOrBlank()
            }
            ?.memoryProfileId
    


    val defaultUserMessageColor = MaterialTheme.colorScheme.primaryContainer
    val defaultAiMessageColor = MaterialTheme.colorScheme.surface
    val cursorCustomUserMessageColor = cursorUserBubbleColorValue?.let(::Color)
    val bubbleCustomUserMessageColor = bubbleUserBubbleColorValue?.let(::Color)
    val bubbleCustomAiMessageColor = bubbleAiBubbleColorValue?.let(::Color)
    val bubbleCustomUserTextColor = bubbleUserTextColorValue?.let(::Color)
    val bubbleCustomAiTextColor = bubbleAiTextColorValue?.let(::Color)

    val userMessageColor =
        when (chatStyle) {
            ChatStyle.CURSOR -> {
                if (cursorUserBubbleFollowTheme) {
                    defaultUserMessageColor
                } else {
                    cursorCustomUserMessageColor ?: defaultUserMessageColor
                }
            }

            ChatStyle.BUBBLE -> bubbleCustomUserMessageColor ?: defaultUserMessageColor
        }
    val aiMessageColor =
        when (chatStyle) {
            ChatStyle.BUBBLE -> bubbleCustomAiMessageColor ?: defaultAiMessageColor
            ChatStyle.CURSOR -> defaultAiMessageColor
        }
    val userTextColor =
        when {
            chatStyle == ChatStyle.CURSOR && cursorUserBubbleFollowTheme ->
                MaterialTheme.colorScheme.onPrimaryContainer
            chatStyle == ChatStyle.BUBBLE && bubbleCustomUserTextColor != null ->
                bubbleCustomUserTextColor
            else -> getTextColorForBackground(userMessageColor.copy(alpha = 1f))
        }
    val aiTextColor =
        when {
            chatStyle == ChatStyle.BUBBLE && bubbleCustomAiTextColor != null ->
                bubbleCustomAiTextColor
            chatStyle == ChatStyle.BUBBLE ->
                getTextColorForBackground(aiMessageColor.copy(alpha = 1f))
            else -> MaterialTheme.colorScheme.onSurface
        }
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val bubbleUserImageStyle =
        remember(
            chatStyle,
            bubbleUserBubbleLiquidGlass,
            bubbleUserBubbleWaterGlass,
            bubbleUserUseImage,
            bubbleUserImageUri,
            bubbleUserImageCropLeft,
            bubbleUserImageCropTop,
            bubbleUserImageCropRight,
            bubbleUserImageCropBottom,
            bubbleUserImageRepeatStart,
            bubbleUserImageRepeatEnd,
            bubbleUserImageRepeatYStart,
            bubbleUserImageRepeatYEnd,
            bubbleUserImageScale,
            bubbleImageRenderMode,
        ) {
            val imageUri = bubbleUserImageUri
            if (
                chatStyle == ChatStyle.BUBBLE &&
                    !bubbleUserBubbleLiquidGlass &&
                    !bubbleUserBubbleWaterGlass &&
                    bubbleUserUseImage &&
                    !imageUri.isNullOrBlank()
            ) {
                BubbleImageStyleConfig(
                    imageUri = imageUri,
                    cropLeftRatio = bubbleUserImageCropLeft,
                    cropTopRatio = bubbleUserImageCropTop,
                    cropRightRatio = bubbleUserImageCropRight,
                    cropBottomRatio = bubbleUserImageCropBottom,
                    repeatXStartRatio = bubbleUserImageRepeatStart,
                    repeatXEndRatio = bubbleUserImageRepeatEnd,
                    repeatYStartRatio = bubbleUserImageRepeatYStart,
                    repeatYEndRatio = bubbleUserImageRepeatYEnd,
                    imageScale = bubbleUserImageScale,
                    renderMode = bubbleImageRenderMode,
                )
            } else {
                null
            }
        }

    val bubbleAiImageStyle =
        remember(
            chatStyle,
            bubbleAiUseImage,
            bubbleAiBubbleLiquidGlass,
            bubbleAiBubbleWaterGlass,
            bubbleAiImageUri,
            bubbleAiImageCropLeft,
            bubbleAiImageCropTop,
            bubbleAiImageCropRight,
            bubbleAiImageCropBottom,
            bubbleAiImageRepeatStart,
            bubbleAiImageRepeatEnd,
            bubbleAiImageRepeatYStart,
            bubbleAiImageRepeatYEnd,
            bubbleAiImageScale,
            bubbleImageRenderMode,
        ) {
            val imageUri = bubbleAiImageUri
            if (
                chatStyle == ChatStyle.BUBBLE &&
                    !bubbleAiBubbleLiquidGlass &&
                    !bubbleAiBubbleWaterGlass &&
                    bubbleAiUseImage &&
                    !imageUri.isNullOrBlank()
            ) {
                BubbleImageStyleConfig(
                    imageUri = imageUri,
                    cropLeftRatio = bubbleAiImageCropLeft,
                    cropTopRatio = bubbleAiImageCropTop,
                    cropRightRatio = bubbleAiImageCropRight,
                    cropBottomRatio = bubbleAiImageCropBottom,
                    repeatXStartRatio = bubbleAiImageRepeatStart,
                    repeatXEndRatio = bubbleAiImageRepeatEnd,
                    repeatYStartRatio = bubbleAiImageRepeatYStart,
                    repeatYEndRatio = bubbleAiImageRepeatYEnd,
                    imageScale = bubbleAiImageScale,
                    renderMode = bubbleImageRenderMode,
                )
            } else {
                null
            }
        }

    // 滚动状态
    var autoScrollToBottom by remember { mutableStateOf(true) }
    val onAutoScrollToBottomChange = remember { { it: Boolean -> autoScrollToBottom = it } }
    val requestAutoScrollToBottom = remember { { autoScrollToBottom = true } }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val latestChatHistory by rememberUpdatedState(chatHistory)
    val latestAutoScrollToBottom by rememberUpdatedState(autoScrollToBottom)
    val latestHasNewerDisplayHistory by rememberUpdatedState(hasNewerDisplayHistory)
    val latestIsLoadingDisplayWindow by rememberUpdatedState(isLoadingDisplayWindow)

    // 处理来自ViewModel的滚动事件（流式输出时）
    LaunchedEffect(Unit) {
        scrollToBottomEvent.collect {
            if (
                latestAutoScrollToBottom &&
                    !latestHasNewerDisplayHistory &&
                    !latestIsLoadingDisplayWindow
            ) {
                try {
                    if (latestChatHistory.isNotEmpty()) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                } catch (e: Exception) {
                    // AppLogger.e("AIChatScreen", "自动滚动失败", e)
                }
            }
        }
    }

    // 移除原有的 snackbar 错误处理
    val snackbarHostState = remember { SnackbarHostState() }

    // 用新的错误弹窗替换原有的错误显示逻辑
    errorMessage?.let { message ->
        ErrorDialog(errorMessage = message, onDismiss = { actualViewModel.dismissErrorDialog() })
    }

    val toastEvent by actualViewModel.toastEvent.collectAsState()

    // Save chat on app exit
    DisposableEffect(Unit) {
        onDispose {
            // This is handled by the ViewModel
        }
    }

    var isSavingInitialConfiguration by remember { mutableStateOf(false) }
    var initialConfigurationSaveFailed by
        rememberSaveable(activeChatConfigId) { mutableStateOf(false) }
    val showConfig =
        shouldShowConfigDialog ||
            isSavingInitialConfiguration ||
            initialConfigurationSaveFailed

    // 添加手势状态
    var chatScreenGestureConsumed by remember { mutableStateOf(false) }
    val onChatScreenGestureConsumedChange = remember {
        { it: Boolean -> chatScreenGestureConsumed = it }
    }

    // 添加累计滑动距离变量
    var currentDrag by remember { mutableStateOf(0f) }
    val onCurrentDragChange = remember { { it: Float -> currentDrag = it } }
    var verticalDrag by remember { mutableStateOf(0f) }
    val onVerticalDragChange = remember { { it: Float -> verticalDrag = it } }
    val dragThreshold = 40f // 与PhoneLayout保持一致
    val onSwitchCharacter = remember(actualViewModel) {
        { target: CharacterSelectorTarget ->
            actualViewModel.switchActiveCharacterTarget(target)
        }
    }

    // 收集WebView显示状态
    val showWebView by actualViewModel.showWebView.collectAsState()
    // 收集AI电脑显示状态
    val showAiComputer by actualViewModel.showAiComputer.collectAsState()
    // AppContent owns the primary chat IME layout; embedded chat retains its local translation.
    val shouldUseChatLocalImeHandling =
        embedded &&
            inputStyle == UserPreferencesManager.INPUT_STYLE_AGENT &&
            !showWebView &&
            !showAiComputer
    var hasEverShownWebView by remember { mutableStateOf(false) }
    LaunchedEffect(showWebView, isWorkspacePreparing) {
        if (showWebView || isWorkspacePreparing) {
            hasEverShownWebView = true
        }
    }
    // 当手势状态改变时，通知父组件
    LaunchedEffect(chatScreenGestureConsumed, showWebView) {
        val finalGestureState = chatScreenGestureConsumed
        // 同时更新全局状态持有者，确保PhoneLayout能够访问到状态
        GestureStateHolder.isChatScreenGestureConsumed = finalGestureState
        onGestureConsumed(finalGestureState)
    }

    // 处理文件选择器请求
    val fileChooserRequest by actualViewModel.uiStateDelegate.fileChooserRequest.collectAsState()
    val fileChooserLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                // 处理文件选择结果
                actualViewModel.handleFileChooserResult(result.resultCode, result.data)
                // 清除请求
                actualViewModel.uiStateDelegate.clearFileChooserRequest()
            }

    // 启动文件选择器
    LaunchedEffect(fileChooserRequest) {
        fileChooserRequest?.let { fileChooserLauncher.launch(it) }
    }

    // 从CompositionLocal获取设置TopBar Actions的函数
    val setTopBarActions = LocalTopBarActions.current
    val appBarContentColor = LocalAppBarContentColor.current
    val setScreenSoftInputMode = LocalSetScreenSoftInputMode.current
    val setUseScreenImePadding = LocalSetUseScreenImePadding.current
    val requestedSoftInputMode =
        if (shouldUseChatLocalImeHandling) {
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        } else {
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    val shouldUseGlobalImePadding = !shouldUseChatLocalImeHandling
    val hasBoundWorkspace = !currentChatView?.workspace.isNullOrBlank()

    SideEffect {
        if (isCurrentScreen && !embedded) {
            setScreenSoftInputMode(requestedSoftInputMode)
            setUseScreenImePadding(shouldUseGlobalImePadding)
        }
    }


    // 当showWebView或showAiComputer状态改变时，更新TopAppBar的actions
    // 使用DisposableEffect确保当AIChatScreen离开组合时，actions被清空
    LaunchedEffect(isCurrentScreen, embedded, showWebView, showAiComputer, isWorkspacePreparing, appBarContentColor, hasBoundWorkspace) {
        if (isCurrentScreen && !embedded) {
            setTopBarActions {
                // AI电脑模式切换按钮
                IconButton(
                        enabled = !isWorkspacePreparing,
                        onClick = {
                            actualViewModel.onAiComputerButtonClick()
                        }
                ) {
                    Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = stringResource(R.string.ai_computer),
                            tint =
                            if (showAiComputer) MaterialTheme.colorScheme.primaryContainer
                            else appBarContentColor
                    )
                }

                // Web开发模式切换按钮
                IconButton(
                        enabled = !isWorkspacePreparing,
                        onClick = {
                            actualViewModel.onWorkspaceButtonClick()
                        }
                ) {
                    if (isWorkspacePreparing) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = appBarContentColor
                        )
                    } else {
                        Icon(
                                imageVector =
                                if (hasBoundWorkspace) Icons.Default.Code
                                else Icons.Default.CodeOff,
                                contentDescription =
                                if (hasBoundWorkspace) stringResource(R.string.workspace)
                                else stringResource(R.string.setup_workspace),
                                tint =
                                if (showWebView) MaterialTheme.colorScheme.primaryContainer
                                else appBarContentColor
                        )
                    }
                }
            }
        }
    }

    // 导出相关状态
    var showExportPlatformDialog by remember { mutableStateOf(false) }
    var showAndroidExportDialog by remember { mutableStateOf(false) }
    var showWindowsExportDialog by remember { mutableStateOf(false) }
    var showExportProgressDialog by remember { mutableStateOf(false) }
    var showExportCompleteDialog by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportStatus by remember { mutableStateOf("") }
    var exportSuccess by remember { mutableStateOf(false) }
    var exportFilePath by remember { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }
    var webContentDir by remember { mutableStateOf<File?>(null) }
    var showCharacterSelector by remember { mutableStateOf(false) }

    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp() }
    val classicSettingsBarBottomPadding =
        if (bottomBarHeightDp > 36.dp) {
            bottomBarHeightDp - 6.dp
        } else {
            18.dp
        }
    val inputBarTranslationYPx =
        if (shouldUseChatLocalImeHandling && imeBottomPx > 0) {
            imeBottomPx.toFloat()
        } else {
            0f
        }
    val chatViewportTranslationYPx = inputBarTranslationYPx
    Box(modifier = Modifier.fillMaxSize()) {
        CustomScaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            // 根据前面的逻辑条件决定是否显示配置界面
            if (showConfig) {
                ConfigurationScreen(
                        apiKey = apiKey,
                        isSaving = isSavingInitialConfiguration,
                        onSaveApiKey = { normalizedApiKey ->
                            if (!isSavingInitialConfiguration) {
                                coroutineScope.launch {
                                    isSavingInitialConfiguration = true
                                    initialConfigurationSaveFailed = false
                                    try {
                                        actualViewModel.saveDeepSeekConfiguration(
                                            activeChatConfigId,
                                            normalizedApiKey
                                        )
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        initialConfigurationSaveFailed = true
                                        actualViewModel.showErrorMessage(
                                            e.message ?: context.getString(R.string.save_failed)
                                        )
                                    } finally {
                                        isSavingInitialConfiguration = false
                                    }
                                }
                            }
                        },
                        onNavigateToTokenConfig = onNavigateToTokenConfig,
                        onNavigateToModelConfig = {
                            initialConfigurationSaveFailed = false
                            onNavigateToOnboardingModelConfig()
                        }
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .clipToBounds()
                ) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { translationY = -chatViewportTranslationYPx }
                    ) {
                        ChatScreenContent(
                                modifier = Modifier.fillMaxSize(),
                                paddingValues =
                                        PaddingValues(), // Padding is already handled by the parent Box
                                bottomInset = bottomBarHeightDp,
                                actualViewModel = actualViewModel,
                                enableMessageDialogs = !isFloatingMode,
                                showChatHistorySelector = showChatHistorySelector,
                                chatHistory = chatHistory,
                                isLoading = isLoading,
                                userMessageColor = userMessageColor,
                                aiMessageColor = aiMessageColor,
                                userTextColor = userTextColor,
                                aiTextColor = aiTextColor,
                                systemMessageColor = systemMessageColor,
                                systemTextColor = systemTextColor,
                                thinkingBackgroundColor = thinkingBackgroundColor,
                                thinkingTextColor = thinkingTextColor,
                                hasBackgroundImage = effectiveHasBackgroundImage,
                                editingMessageIndex = editingMessageIndex,
                                editingMessageContent = editingMessageContent,
                                chatScreenGestureConsumed = chatScreenGestureConsumed,
                                onChatScreenGestureConsumed = onChatScreenGestureConsumedChange,
                                currentDrag = currentDrag,
                                onCurrentDragChange = onCurrentDragChange,
                                verticalDrag = verticalDrag,
                                onVerticalDragChange = onVerticalDragChange,
                                dragThreshold = dragThreshold,
                                scrollState = scrollState,
                                autoScrollToBottom = autoScrollToBottom,
                                onAutoScrollToBottomChange = onAutoScrollToBottomChange,
                                coroutineScope = coroutineScope,
                                chatHistories = chatHistories,
                                currentChatId = currentChatId ?: "",
                                chatHeaderTransparent = chatHeaderTransparent,
                                chatHeaderHistoryIconColor = chatHeaderHistoryIconColor,
                                chatHeaderPipIconColor = chatHeaderPipIconColor,
                                chatHeaderOverlayMode = chatHeaderOverlayMode,
                                chatStyle = chatStyle, // Pass chat style
                                cursorUserBubbleLiquidGlass = cursorUserBubbleLiquidGlass,
                                cursorUserBubbleWaterGlass = cursorUserBubbleWaterGlass,
                                bubbleUserBubbleLiquidGlass = bubbleUserBubbleLiquidGlass,
                                bubbleUserBubbleWaterGlass = bubbleUserBubbleWaterGlass,
                                bubbleAiBubbleLiquidGlass = bubbleAiBubbleLiquidGlass,
                                bubbleAiBubbleWaterGlass = bubbleAiBubbleWaterGlass,
                                historyListState = historyListState,
                                showCharacterSelector = showCharacterSelector,
                                onShowCharacterSelectorChange = { showCharacterSelector = it },
                                onSwitchCharacter = onSwitchCharacter,
                                onOpenCharacterSettings = onNavigateToModelPrompts,
                                chatAreaHorizontalPadding = chatAreaHorizontalPadding,
                                bubbleUserImageStyle = bubbleUserImageStyle,
                                bubbleAiImageStyle = bubbleAiImageStyle,
                                bubbleUserRoundedCornersEnabled = bubbleUserRoundedCornersEnabled,
                                bubbleAiRoundedCornersEnabled = bubbleAiRoundedCornersEnabled,
                                bubbleUserContentPaddingLeft = bubbleUserContentPaddingLeft,
                                bubbleUserContentPaddingRight = bubbleUserContentPaddingRight,
                                bubbleAiContentPaddingLeft = bubbleAiContentPaddingLeft,
                                bubbleAiContentPaddingRight = bubbleAiContentPaddingRight,
                                showChatFloatingDotsAnimation = showChatFloatingDotsAnimation,
                        )

                        if (inputStyle == UserPreferencesManager.INPUT_STYLE_CLASSIC) {
                            ClassicChatSettingsBar(
                                    modifier =
                                            Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(
                                                            end = 4.dp,
                                                            bottom = classicSettingsBarBottomPadding,
                                                    )
                                                    .graphicsLayer {
                                                        translationY = -inputBarTranslationYPx
                                                    },
                                    currentChatId = currentChatId,
                                    featureStates = featureStates,
                                    onToggleFeature = { featureKey ->
                                        actualViewModel.toggleFeature(featureKey)
                                    },
                                    inputMenuRuntime = chatViewRuntime,
                                    permissionLevel =
                                            actualViewModel.masterPermissionLevel
                                                    .collectAsState()
                                                    .value,
                                    onSetPermissionLevel = actualViewModel::setMasterPermissionLevel,
                                    enableThinkingMode = enableThinkingMode,
                                    onToggleThinkingMode = { actualViewModel.toggleThinkingMode() },
                                    thinkingQualityLevel = thinkingQualityLevel,
                                    onThinkingQualityLevelChange = {
                                        actualViewModel.updateThinkingQualityLevel(it)
                                    },
                                    maxWindowSizeInK =
                                            actualViewModel.maxWindowSizeInK.collectAsState().value,
                                    baseContextLengthInK =
                                            actualViewModel.baseContextLengthInK.collectAsState().value,
                                    maxContextLengthInK =
                                            actualViewModel.maxContextLengthInK.collectAsState().value,
                                    onContextLengthChange = {
                                        actualViewModel.updateContextLength(it)
                                    },
                                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                                    onToggleMemoryAutoUpdate = {
                                        actualViewModel.toggleMemoryAutoUpdate()
                                    },
                                    enableMaxContextMode = enableMaxContextMode,
                                    onToggleEnableMaxContextMode = {
                                        actualViewModel.toggleEnableMaxContextMode()
                                    },
                                    summaryTokenThreshold = summaryTokenThreshold,
                                    onSummaryTokenThresholdChange = {
                                        actualViewModel.updateSummaryTokenThreshold(it)
                                    },
                                    onNavigateToMemoryBase = onNavigateToMemoryBase,
                                    onNavigateToModelConfig = onNavigateToModelConfig,
                                    onNavigateToModelPrompts = onNavigateToModelPrompts,
                                    onNavigateToPackageManager = onNavigateToPackageManager,
                                    isAutoReadEnabled = isAutoReadEnabled,
                                    onToggleAutoRead = { actualViewModel.toggleAutoRead() },
                                    enableTools = enableTools,
                                    onToggleTools = { actualViewModel.toggleTools() },
                                    toolPromptVisibility = toolPromptVisibility,
                                    onSaveToolPromptVisibilityMap = { visibilityMap ->
                                        actualViewModel.saveToolPromptVisibilityMap(visibilityMap)
                                    },
                                    toolOrder = toolPromptOrder,
                                    onSaveToolOrder = { order ->
                                        actualViewModel.saveToolPromptOrder(order)
                                    },
                                    disableStreamOutput = disableStreamOutput,
                                    onToggleDisableStreamOutput = {
                                        actualViewModel.toggleDisableStreamOutput()
                                    },
                                    disableUserPreferenceDescription =
                                            disableUserPreferenceDescription,
                                    onToggleDisableUserPreferenceDescription = {
                                        actualViewModel.toggleDisableUserPreferenceDescription()
                                    },
                                    onManualMemoryUpdate = {
                                        actualViewModel.manuallyUpdateMemory()
                                    },
                                    characterCardBoundChatModelConfigId = characterCardBoundChatModelConfigId,
                                    characterCardBoundChatModelIndex = characterCardBoundChatModelIndex,
                                    characterCardBoundMemoryProfileId = characterCardBoundMemoryProfileId
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .onGloballyPositioned {
                                    bottomBarHeightPx = it.size.height
                                }
                                .graphicsLayer { translationY = -inputBarTranslationYPx }
                    ) {
                        ChatInputBottomBar(
                                actualViewModel = actualViewModel,
                                inputStyle = inputStyle,
                                currentChatId = currentChatId,
                                inputMenuRuntime = chatViewRuntime,
                                enableEnterToSend = enableEnterToSend,
                                isLoading = isLoading,
                                inputState = inputProcessingState,
                                hasBackgroundImage = effectiveHasBackgroundImage,
                                chatInputTransparent = chatInputTransparent,
                                chatInputFloating = chatInputFloating,
                                chatInputLiquidGlass = chatInputLiquidGlass,
                                chatInputWaterGlass = chatInputWaterGlass,
                                showInputProcessingStatus = showInputProcessingStatus,
                                enableTools = enableTools,
                                isWorkspaceOpen = isWorkspaceOpen,
                                enableThinkingMode = enableThinkingMode,
                                thinkingQualityLevel = thinkingQualityLevel,
                                enableMaxContextMode = enableMaxContextMode,
                                featureStates = featureStates,
                                enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                                isAutoReadEnabled = isAutoReadEnabled,
                                disableStreamOutput = disableStreamOutput,
                                disableUserPreferenceDescription =
                                        disableUserPreferenceDescription,
                                onNavigateToMemoryBase = onNavigateToMemoryBase,
                                onNavigateToPackageManager = onNavigateToPackageManager,
                                toolPromptVisibility = toolPromptVisibility,
                                toolPromptOrder = toolPromptOrder,
                                onSaveToolOrder = { order ->
                                    actualViewModel.saveToolPromptOrder(order)
                                },
                                onNavigateToModelConfig = onNavigateToModelConfig,
                                characterCardBoundChatModelConfigId =
                                        characterCardBoundChatModelConfigId,
                                characterCardBoundChatModelIndex =
                                        characterCardBoundChatModelIndex,
                                characterCardBoundMemoryProfileId =
                                        characterCardBoundMemoryProfileId,
                                onShowMemoryFolderDialog = {
                                    showMemoryFolderDialog = true
                                },
                                onRequestAutoScrollToBottom = requestAutoScrollToBottom,
                        )
                    }

                    CharacterSelectorPanel(
                        isVisible = showCharacterSelector,
                        onDismiss = { showCharacterSelector = false },
                        onSelectCharacter = onSwitchCharacter,
                        onOpenCharacterSettings = onNavigateToModelPrompts
                    )

                    AnimatedVisibility(
                        visible = showChatHistorySelector,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300)),
                        modifier = Modifier.matchParentSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    actualViewModel.toggleChatHistorySelector()
                                }
                        )
                    }

                    AnimatedVisibility(
                        visible = showChatHistorySelector,
                        enter = androidx.compose.animation.slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(300)
                        ),
                        exit = androidx.compose.animation.slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(300)
                        ),
                        modifier = Modifier.matchParentSize()
                    ) {
                        val chatHistorySearchQuery by actualViewModel.chatHistorySearchQuery.collectAsState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.align(Alignment.TopStart)) {
                                ChatHistorySelectorPanel(
                                    actualViewModel = actualViewModel,
                                    chatHistories = displayedChatHistories,
                                    currentChatId = currentChatId ?: "",
                                    showChatHistorySelector = showChatHistorySelector,
                                    historyListState = historyListState,
                                    onChatScreenGestureConsumed = onChatScreenGestureConsumedChange,
                                    searchQuery = chatHistorySearchQuery,
                                    onSearchQueryChange = actualViewModel::onChatHistorySearchQueryChange,
                                    activePrompt = activePrompt,
                                    historyDisplayMode = historyDisplayMode,
                                    onDisplayModeChange = { historyDisplayMode = it },
                                    autoSwitchCharacterCard = autoSwitchCharacterCard,
                                    onAutoSwitchCharacterCardChange = { autoSwitchCharacterCard = it },
                                    autoSwitchChatOnCharacterSelect = autoSwitchChatOnCharacterSelect,
                                    onAutoSwitchChatOnCharacterSelectChange = {
                                        autoSwitchChatOnCharacterSelect = it
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        MentionSuggestionOverlay(
            actualViewModel = actualViewModel,
            bottomBarHeightPx = bottomBarHeightPx,
            inputBarTranslationYPx = inputBarTranslationYPx,
            panelStyle =
                MentionSuggestionPanelStyle(
                    hasBackgroundImage = effectiveHasBackgroundImage,
                    chatInputTransparent = chatInputTransparent,
                    chatInputFloating = chatInputFloating,
                    chatInputLiquidGlass = chatInputLiquidGlass,
                    chatInputWaterGlass = chatInputWaterGlass,
                ),
        )

        val isWorkspaceVisible = !embedded && showWebView
        val workspaceOverlayModifier =
            if (isWorkspaceVisible) {
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
            } else {
                Modifier
                    .size(0.dp)
                    .clearAndSetSemantics {}
            }

        // Web开发模式作为浮层，现在位于Scaffold外部，可以覆盖整个屏幕
        Layout(
            modifier = workspaceOverlayModifier,
            content = {
                // The content is composed unconditionally, keeping it "alive"
                val currentChat = chatHistories.find { it.id == currentChatId }
                if (hasEverShownWebView && currentChat != null) {
                    WorkspaceScreen(
                        actualViewModel = actualViewModel,
                        currentChat = currentChat,
                        isVisible = isWorkspaceVisible, // Pass visibility state
                        onExportClick = { workDir ->
                            webContentDir = workDir
                            AppLogger.d(
                                "AIChatScreen",
                                "正在导出工作区: ${workDir.absolutePath}, 聊天ID: $currentChatId"
                            )
                            showExportPlatformDialog = true
                        }
                    )
                }
            }
        ) { measurables, constraints ->
            if (measurables.isEmpty()) {
                layout(0, 0) {}
            } else {
                if (isWorkspaceVisible) {
                    val placeable = measurables.first().measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                } else {
                    // 当不可见时，我们让布局大小为0，并且完全不测量或放置子项。
                    // 这可以跳过子项昂贵的测量/布局过程，从而解决性能问题，
                    // 同时由于它仍在组合中，因此可以保持其状态。
                    layout(0, 0) {}
                }
            }
        }

        // AI电脑模式作为浮层：关闭时完全移出组合，确保 SurfaceView 被释放，避免机型相关残影
        if (!embedded && showAiComputer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                ComputerScreen()
            }
        }

        AnimatedVisibility(
            visible = !embedded && isWorkspacePreparing,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.workspace_opening),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.workspace_opening_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 导出平台选择对话框
        if (showExportPlatformDialog) {
            ExportPlatformDialog(
                    onDismiss = { showExportPlatformDialog = false },
                    onSelectAndroid = {
                        showExportPlatformDialog = false
                        showAndroidExportDialog = true
                    },
                    onSelectWindows = {
                        showExportPlatformDialog = false
                        showWindowsExportDialog = true
                    }
            )
        }

        // Android导出设置对话框
        if (showAndroidExportDialog && webContentDir != null) {
            AndroidExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showAndroidExportDialog = false },
                    onExport = { packageName, appName, iconUri, versionName, versionCode ->
                        showAndroidExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = context.getString(R.string.export_starting)

                        // 启动导出过程
                        coroutineScope.launch {
                            exportAndroidApp(
                                    context = context,
                                    packageName = packageName,
                                    appName = appName,
                                    versionName = versionName,
                                    versionCode = versionCode,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // Windows导出设置对话框
        if (showWindowsExportDialog && webContentDir != null) {
            WindowsExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showWindowsExportDialog = false },
                    onExport = { appName, iconUri ->
                        showWindowsExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = context.getString(R.string.export_starting)

                        // 启动导出过程
                        coroutineScope.launch {
                            exportWindowsApp(
                                    context = context,
                                    appName = appName,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // 导出进度对话框
        if (showExportProgressDialog) {
            ExportProgressDialog(
                    progress = exportProgress,
                    status = exportStatus,
                    onCancel = {
                        // TODO: 实现取消导出的逻辑
                        showExportProgressDialog = false
                    }
            )
        }

        // 导出完成对话框
        if (showExportCompleteDialog) {
            ExportCompleteDialog(
                    success = exportSuccess,
                    filePath = exportFilePath,
                    errorMessage = exportErrorMessage,
                    onDismiss = { showExportCompleteDialog = false },
                    onOpenFile = { path ->
                        val tool = AITool(
                            name = if (path.endsWith(".apk", ignoreCase = true)) "install_app" else "open_file",
                            parameters = listOf(ToolParameter("path", path))
                        )
                        AIToolHandler.getInstance(context).executeTool(tool)
                    }
            )
        }

        ChatToastHost(
            event = toastEvent,
            onDismiss = actualViewModel::clearToastEvent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 50.dp,
                    end = 16.dp
                ),
            maxHeight = 280.dp
        )
    }

    // Show popup message dialog when needed
    popupMessage?.let { message ->
        AlertDialog(
                onDismissRequest = { actualViewModel.clearPopupMessage() },
                title = { Text(stringResource(R.string.dialog_title_prompt)) },
                text = { Text(message ?: "") },
                confirmButton = {
                    TextButton(onClick = { actualViewModel.clearPopupMessage() }) { Text(stringResource(R.string.ok)) }
                }
        )
    }

    // Check for overlay permission on resume
    LaunchedEffect(Unit) {
        canDrawOverlays.value = Settings.canDrawOverlays(context)

        // If floating mode is on but no permission, turn it off
        if (isFloatingMode && !canDrawOverlays.value) {
            actualViewModel.toggleFloatingMode()
            Toast.makeText(
                            context,
                            context.getString(R.string.floating_window_permission_denied),
                            Toast.LENGTH_SHORT
                    )
                    .show()
        }
    }
    
    // 记忆文件夹选择对话框
    MemoryFolderSelectionDialog(
        visible = showMemoryFolderDialog,
        onDismiss = { showMemoryFolderDialog = false },
        onConfirm = { selectedFolders ->
            actualViewModel.captureMemoryFolders(selectedFolders)
        }
    )
}

@Composable
private fun ChatInputBottomBar(
    actualViewModel: ChatViewModel,
    inputStyle: String,
    currentChatId: String?,
    inputMenuRuntime: String,
    enableEnterToSend: Boolean,
    isLoading: Boolean,
    inputState: InputProcessingState,
    hasBackgroundImage: Boolean,
    chatInputTransparent: Boolean,
    chatInputFloating: Boolean,
    chatInputLiquidGlass: Boolean,
    chatInputWaterGlass: Boolean,
    showInputProcessingStatus: Boolean,
    enableTools: Boolean,
    isWorkspaceOpen: Boolean,
    enableThinkingMode: Boolean,
    thinkingQualityLevel: Int,
    enableMaxContextMode: Boolean,
    featureStates: Map<String, Boolean>,
    enableMemoryAutoUpdate: Boolean,
    isAutoReadEnabled: Boolean,
    disableStreamOutput: Boolean,
    disableUserPreferenceDescription: Boolean,
    onNavigateToMemoryBase: () -> Unit,
    onNavigateToPackageManager: () -> Unit,
    toolPromptVisibility: Map<String, Boolean>,
    toolPromptOrder: List<String> = emptyList(),
    onSaveToolOrder: (List<String>) -> Unit = {},
    onNavigateToModelConfig: () -> Unit,
    characterCardBoundChatModelConfigId: String?,
    characterCardBoundChatModelIndex: Int,
    characterCardBoundMemoryProfileId: String?,
    onShowMemoryFolderDialog: () -> Unit,
    onRequestAutoScrollToBottom: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val waifuPreferences = remember(context) { WaifuPreferences.getInstance(context) }
    val userPreferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }

    val userMessage by actualViewModel.userMessage.collectAsState()
    val attachments by actualViewModel.attachments.collectAsState()
    val attachmentPanelState by actualViewModel.attachmentPanelState.collectAsState()
    val replyToMessage by actualViewModel.replyToMessage.collectAsState()
    val permissionLevel by actualViewModel.masterPermissionLevel.collectAsState()
    val isSummarizing by actualViewModel.isSummarizing.collectAsState()
    val isSendTriggeredSummarizing by actualViewModel.isSendTriggeredSummarizing.collectAsState()
    val isWaifuModeEnabled by waifuPreferences.enableWaifuModeFlow.collectAsState(initial = false)
    val isWaifuMergeSendEnabled by
        waifuPreferences.waifuEnableMergeSendFlow.collectAsState(initial = false)
    val waifuMergeSendDelayMs by
        waifuPreferences.waifuMergeSendDelayMsFlow.collectAsState(
            initial = WaifuPreferences.DEFAULT_WAIFU_MERGE_SEND_DELAY_MS
        )
    val convertLongPastedTextToFile by
        userPreferences.convertLongPastedTextToFile.collectAsState(initial = true)
    val longPastedTextFileThreshold by
        userPreferences.longPastedTextFileThreshold.collectAsState(
            initial = UserPreferencesManager.DEFAULT_LONG_PASTED_TEXT_FILE_THRESHOLD
        )

    val isMessageProcessing =
        isLoading ||
            inputState is InputProcessingState.Connecting ||
            inputState is InputProcessingState.ExecutingTool ||
            inputState is InputProcessingState.ToolProgress ||
            inputState is InputProcessingState.Processing ||
            inputState is InputProcessingState.ProcessingToolResult ||
            inputState is InputProcessingState.Summarizing ||
            inputState is InputProcessingState.Receiving
    val isQueueBlocked = isMessageProcessing || isSummarizing || isSendTriggeredSummarizing

    val pendingQueueStates by actualViewModel.pendingMessageQueueStates.collectAsState()
    val pendingQueueState =
        currentChatId?.let { chatId -> pendingQueueStates[chatId] } ?: PendingMessageQueueState()
    val pendingQueueMessages = pendingQueueState.messages
    val isPendingQueueExpanded = pendingQueueState.isExpanded
    val waifuMergeBuffer = remember(currentChatId) { mutableStateListOf<String>() }
    val latestQueueBlocked = rememberUpdatedState(isQueueBlocked)
    val latestCurrentChatId = rememberUpdatedState(currentChatId)

    fun buildChatInputHookContext(
        eventName: String,
        text: String = userMessage.text,
        selectionStart: Int = userMessage.selection.start,
        selectionEnd: Int = userMessage.selection.end,
        source: String = inputStyle,
        submitSource: String = "",
        chatId: String? = currentChatId,
    ): ChatInputHookContext {
        val normalizedSelectionStart = selectionStart.coerceIn(0, text.length)
        val normalizedSelectionEnd = selectionEnd.coerceIn(0, text.length)
        return ChatInputHookContext(
            context = context,
            eventName = eventName,
            chatId = chatId,
            text = text,
            selectionStart = normalizedSelectionStart,
            selectionEnd = normalizedSelectionEnd,
            hasAttachments = attachments.isNotEmpty(),
            attachmentCount = attachments.size,
            isProcessing = isMessageProcessing,
            inputStyle = inputStyle,
            source = source,
            submitSource = submitSource
        )
    }

    fun commitUserMessageChange(value: TextFieldValue) {
        actualViewModel.updateUserMessage(value)
        ChatInputHookRegistry.dispatchNotification(
            buildChatInputHookContext(
                eventName = ChatInputEvents.INPUT_CHANGED,
                text = value.text,
                selectionStart = value.selection.start,
                selectionEnd = value.selection.end
            )
        )
    }

    fun handleUserMessageChange(value: TextFieldValue) {
        val clipboardText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
        val pastedText =
            if (convertLongPastedTextToFile && clipboardText != null) {
                extractClipboardPastedText(userMessage, value, clipboardText)
            } else {
                null
            }

        if (
            pastedText != null &&
                pastedText.length > longPastedTextFileThreshold &&
                !currentChatId.isNullOrBlank()
        ) {
            coroutineScope.launch {
                // Keep the draft unchanged until the attachment has been created, so I/O errors do not lose text.
                if (!actualViewModel.attachPastedText(pastedText)) {
                    commitUserMessageChange(value)
                }
            }
            return
        }

        commitUserMessageChange(value)
    }

    fun showChatInputHookMessage(message: String?) {
        val normalizedMessage = message?.trim().orEmpty()
        if (normalizedMessage.isNotBlank()) {
            actualViewModel.showToast(normalizedMessage)
        }
    }

    LaunchedEffect(
        currentChatId,
        waifuMergeBuffer.size,
        isWaifuModeEnabled,
        isWaifuMergeSendEnabled,
        waifuMergeSendDelayMs
    ) {
        if (!isWaifuModeEnabled || !isWaifuMergeSendEnabled) {
            waifuMergeBuffer.clear()
            return@LaunchedEffect
        }
        if (waifuMergeBuffer.isEmpty()) {
            return@LaunchedEffect
        }

        delay(waifuMergeSendDelayMs.toLong())
        snapshotFlow { latestQueueBlocked.value }.first { blocked -> !blocked }

        val messages = waifuMergeBuffer.toList()
        if (messages.isEmpty()) {
            return@LaunchedEffect
        }

        val chatId = latestCurrentChatId.value
        if (chatId.isNullOrBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.chat_please_create_new_chat),
                Toast.LENGTH_SHORT,
            ).show()
            return@LaunchedEffect
        }

        val triggerText = messages.last()
        val removedVisibleMessage =
            actualViewModel.removeLastVisibleUserMessageFromCurrentChat(triggerText)
        if (!removedVisibleMessage) {
            return@LaunchedEffect
        }
        waifuMergeBuffer.clear()

        focusManager.clearFocus()
        actualViewModel.sendTextMessage(triggerText)
        onRequestAutoScrollToBottom()
        ChatInputHookRegistry.dispatchNotification(
            buildChatInputHookContext(
                eventName = ChatInputEvents.SUBMITTED,
                text = triggerText,
                selectionStart = triggerText.length,
                selectionEnd = triggerText.length,
                source = "waifu_merge",
                submitSource = "waifu_merge"
            )
        )
    }

    fun restorePendingQueueItem(chatId: String, item: PendingQueueMessageItem) {
        actualViewModel.restorePendingQueueMessage(chatId, item)
    }

    fun removePendingQueueMessageById(id: Long): PendingQueueMessageItem? {
        val chatId = currentChatId ?: return null
        return actualViewModel.removePendingQueueMessage(chatId, id)
    }

    val sendQueuedItemNow: (String, PendingQueueMessageItem, Boolean) -> Unit =
        { queueChatId, item, cancelCurrentConversation ->
            coroutineScope.launch {
                val submitDecision =
                    ChatInputHookRegistry.dispatchSubmitRequested(
                        buildChatInputHookContext(
                            eventName = ChatInputEvents.SUBMIT_REQUESTED,
                            text = item.text,
                            selectionStart = item.text.length,
                            selectionEnd = item.text.length,
                            source = "queue",
                            submitSource = "queue",
                            chatId = queueChatId,
                        )
                    )
                when (submitDecision.action) {
                    ChatInputSubmitActions.BLOCK -> {
                        restorePendingQueueItem(queueChatId, item)
                        showChatInputHookMessage(submitDecision.message)
                        return@launch
                    }
                    ChatInputSubmitActions.CONSUME -> {
                        showChatInputHookMessage(submitDecision.message)
                        return@launch
                    }
                }
                showChatInputHookMessage(submitDecision.noticeMessage)
                val finalText = submitDecision.text ?: item.text
                val shouldWaitForCancel = cancelCurrentConversation && latestQueueBlocked.value
                if (shouldWaitForCancel) {
                    actualViewModel.suppressNextPendingQueueAutoDequeue(queueChatId)
                }
                if (cancelCurrentConversation) {
                    actualViewModel.cancelMessage(queueChatId)
                }
                if (shouldWaitForCancel) {
                    snapshotFlow { latestQueueBlocked.value }.first { !it }
                }

                focusManager.clearFocus()
                actualViewModel.sendTextMessage(finalText, chatId = queueChatId)
                if (latestCurrentChatId.value == queueChatId) {
                    onRequestAutoScrollToBottom()
                }
                ChatInputHookRegistry.dispatchNotification(
                    buildChatInputHookContext(
                        eventName = ChatInputEvents.SUBMITTED,
                        text = finalText,
                        selectionStart = finalText.length,
                        selectionEnd = finalText.length,
                        source = "queue",
                        submitSource = "queue",
                        chatId = queueChatId,
                    )
                )
            }
        }

    LaunchedEffect(isQueueBlocked, pendingQueueMessages.size, currentChatId) {
        val queueChatId = currentChatId ?: return@LaunchedEffect
        if (actualViewModel.consumePendingQueueAutoDequeueSignal(queueChatId, isQueueBlocked)) {
            delay(250)
            val nextMessage = pendingQueueMessages.firstOrNull()
            if (!latestQueueBlocked.value && nextMessage != null) {
                actualViewModel.removePendingQueueMessage(queueChatId, nextMessage.id)?.let { item ->
                    sendQueuedItemNow(queueChatId, item, false)
                }
            }
        }
    }

    fun enqueueDraftToPendingQueue() {
        val draftText = userMessage.text.trim()
        if (draftText.isBlank()) return
        val chatId = currentChatId ?: return

        actualViewModel.enqueuePendingQueueMessage(
            chatId = chatId,
            text = draftText,
            isQueueBlocked = isQueueBlocked,
        )
        actualViewModel.updateUserMessage(TextFieldValue(""))
        actualViewModel.showToast(context.getString(R.string.chat_queue_added))
    }

    val sendMessage: () -> Unit = {
        coroutineScope.launch {
            if (currentChatId.isNullOrBlank()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.chat_please_create_new_chat),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }

            val submitDecision =
                ChatInputHookRegistry.dispatchSubmitRequested(
                    buildChatInputHookContext(
                        eventName = ChatInputEvents.SUBMIT_REQUESTED,
                        submitSource = "send"
                    )
                )
            when (submitDecision.action) {
                ChatInputSubmitActions.BLOCK -> {
                    showChatInputHookMessage(submitDecision.message)
                    return@launch
                }
                ChatInputSubmitActions.CONSUME -> {
                    if (submitDecision.clearInput) {
                        actualViewModel.updateUserMessage(TextFieldValue(""))
                        actualViewModel.resetAttachmentPanelState()
                    }
                    showChatInputHookMessage(submitDecision.message)
                    return@launch
                }
            }
            showChatInputHookMessage(submitDecision.noticeMessage)

            val finalText = submitDecision.text ?: userMessage.text
            if (finalText != userMessage.text) {
                actualViewModel.updateUserMessage(
                    TextFieldValue(
                        text = finalText,
                        selection = TextRange(finalText.length)
                    )
                )
            }
            val shouldUseWaifuMergeSend =
                isWaifuModeEnabled &&
                    isWaifuMergeSendEnabled &&
                    attachments.isEmpty() &&
                    replyToMessage == null &&
                    finalText.isNotBlank()
            if (shouldUseWaifuMergeSend) {
                val visibleText = finalText.trim()
                waifuMergeBuffer.add(visibleText)
                actualViewModel.addVisibleUserMessageToCurrentChat(visibleText)
                actualViewModel.updateUserMessage(TextFieldValue(""))
                actualViewModel.resetAttachmentPanelState()
                focusManager.clearFocus()
                onRequestAutoScrollToBottom()
                return@launch
            }
            focusManager.clearFocus()
            actualViewModel.sendUserMessage()
            actualViewModel.resetAttachmentPanelState()
            onRequestAutoScrollToBottom()
            ChatInputHookRegistry.dispatchNotification(
                buildChatInputHookContext(
                    eventName = ChatInputEvents.SUBMITTED,
                    text = finalText,
                    selectionStart = finalText.length,
                    selectionEnd = finalText.length,
                    submitSource = "send"
                )
            )
        }
    }

    if (inputStyle == UserPreferencesManager.INPUT_STYLE_AGENT) {
        AgentChatInputSection(
                actualViewModel = actualViewModel,
                userMessage = userMessage,
                onUserMessageChange = { value -> handleUserMessageChange(value) },
                enableEnterToSend = enableEnterToSend,
                onSendMessage = sendMessage,
                onQueueMessage = { enqueueDraftToPendingQueue() },
                onCancelMessage = actualViewModel::cancelCurrentMessage,
                isLoading = isLoading,
                inputState = inputState,
                allowTextInputWhileProcessing = true,
                onAttachmentRequest = actualViewModel::handleAttachment,
                attachments = attachments,
                onRemoveAttachment = actualViewModel::removeAttachment,
                onInsertAttachment = actualViewModel::insertAttachmentReference,
                onAttachScreenContent = actualViewModel::captureScreenContent,
                onAttachNotifications = actualViewModel::captureNotifications,
                onAttachLocation = actualViewModel::captureLocation,
                onAttachMemory = onShowMemoryFolderDialog,
                onAttachPackage = actualViewModel::attachPackage,
                onTakePhoto = actualViewModel::handleTakenPhoto,
                hasBackgroundImage = hasBackgroundImage,
                chatInputTransparent = chatInputTransparent,
                chatInputFloating = chatInputFloating,
                chatInputLiquidGlass = chatInputLiquidGlass,
                chatInputWaterGlass = chatInputWaterGlass,
                externalAttachmentPanelState = attachmentPanelState,
                onAttachmentPanelStateChange = actualViewModel::updateAttachmentPanelState,
                showInputProcessingStatus = showInputProcessingStatus,
                enableTools = enableTools,
                replyToMessage = replyToMessage,
                onClearReply = actualViewModel::clearReplyToMessage,
                isWorkspaceOpen = isWorkspaceOpen,
                enableThinkingMode = enableThinkingMode,
                onToggleThinkingMode = actualViewModel::toggleThinkingMode,
                thinkingQualityLevel = thinkingQualityLevel,
                onThinkingQualityLevelChange = actualViewModel::updateThinkingQualityLevel,
                enableMaxContextMode = enableMaxContextMode,
                onToggleEnableMaxContextMode = actualViewModel::toggleEnableMaxContextMode,
                currentChatId = currentChatId,
                featureStates = featureStates,
                onToggleFeature = actualViewModel::toggleFeature,
                inputMenuRuntime = inputMenuRuntime,
                permissionLevel = permissionLevel,
                onSetPermissionLevel = actualViewModel::setMasterPermissionLevel,
                enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                onToggleMemoryAutoUpdate = actualViewModel::toggleMemoryAutoUpdate,
                isAutoReadEnabled = isAutoReadEnabled,
                onToggleAutoRead = actualViewModel::toggleAutoRead,
                onToggleTools = actualViewModel::toggleTools,
                disableStreamOutput = disableStreamOutput,
                onToggleDisableStreamOutput = actualViewModel::toggleDisableStreamOutput,
                disableUserPreferenceDescription = disableUserPreferenceDescription,
                onToggleDisableUserPreferenceDescription =
                    actualViewModel::toggleDisableUserPreferenceDescription,
                onNavigateToMemoryBase = onNavigateToMemoryBase,
                onNavigateToPackageManager = onNavigateToPackageManager,
                toolPromptVisibility = toolPromptVisibility,
                onSaveToolPromptVisibilityMap = actualViewModel::saveToolPromptVisibilityMap,
                toolOrder = toolPromptOrder,
                onSaveToolOrder = actualViewModel::saveToolPromptOrder,
                onManualMemoryUpdate = actualViewModel::manuallyUpdateMemory,
                onNavigateToModelConfig = onNavigateToModelConfig,
                characterCardBoundChatModelConfigId = characterCardBoundChatModelConfigId,
                characterCardBoundChatModelIndex = characterCardBoundChatModelIndex,
                characterCardBoundMemoryProfileId = characterCardBoundMemoryProfileId,
                pendingQueueMessages = pendingQueueMessages,
                isPendingQueueExpanded = isPendingQueueExpanded,
                onPendingQueueExpandedChange = { expanded ->
                    currentChatId?.let { chatId ->
                        actualViewModel.setPendingQueueExpanded(chatId, expanded)
                    }
                },
                onDeletePendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)
                },
                onEditPendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)?.let { queueItem ->
                        val text = queueItem.text
                        actualViewModel.updateUserMessage(
                            TextFieldValue(
                                text = text,
                                selection = TextRange(text.length),
                            ),
                        )
                    }
                },
                onSendPendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)?.let { queueItem ->
                        currentChatId?.let { chatId ->
                            sendQueuedItemNow(chatId, queueItem, true)
                        }
                    }
                },
        )
    } else {
        ClassicChatInputSection(
                actualViewModel = actualViewModel,
                userMessage = userMessage,
                onUserMessageChange = { value -> handleUserMessageChange(value) },
                enableEnterToSend = enableEnterToSend,
                onSendMessage = sendMessage,
                onQueueMessage = { enqueueDraftToPendingQueue() },
                onCancelMessage = actualViewModel::cancelCurrentMessage,
                isLoading = isLoading,
                inputState = inputState,
                allowTextInputWhileProcessing = true,
                onAttachmentRequest = actualViewModel::handleAttachment,
                attachments = attachments,
                onRemoveAttachment = actualViewModel::removeAttachment,
                onInsertAttachment = actualViewModel::insertAttachmentReference,
                onAttachScreenContent = actualViewModel::captureScreenContent,
                onAttachNotifications = actualViewModel::captureNotifications,
                onAttachLocation = actualViewModel::captureLocation,
                onAttachMemory = onShowMemoryFolderDialog,
                onAttachPackage = actualViewModel::attachPackage,
                onTakePhoto = actualViewModel::handleTakenPhoto,
                hasBackgroundImage = hasBackgroundImage,
                chatInputTransparent = chatInputTransparent,
                chatInputFloating = chatInputFloating,
                chatInputLiquidGlass = chatInputLiquidGlass,
                chatInputWaterGlass = chatInputWaterGlass,
                externalAttachmentPanelState = attachmentPanelState,
                onAttachmentPanelStateChange = actualViewModel::updateAttachmentPanelState,
                showInputProcessingStatus = showInputProcessingStatus,
                enableTools = enableTools,
                replyToMessage = replyToMessage,
                onClearReply = actualViewModel::clearReplyToMessage,
                isWorkspaceOpen = isWorkspaceOpen,
                pendingQueueMessages = pendingQueueMessages,
                isPendingQueueExpanded = isPendingQueueExpanded,
                onPendingQueueExpandedChange = { expanded ->
                    currentChatId?.let { chatId ->
                        actualViewModel.setPendingQueueExpanded(chatId, expanded)
                    }
                },
                onDeletePendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)
                },
                onEditPendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)?.let { queueItem ->
                        val text = queueItem.text
                        actualViewModel.updateUserMessage(
                            TextFieldValue(
                                text = text,
                                selection = TextRange(text.length),
                            ),
                        )
                    }
                },
                onSendPendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)?.let { queueItem ->
                        currentChatId?.let { chatId ->
                            sendQueuedItemNow(chatId, queueItem, true)
                        }
                    }
                },
        )
    }
}

@Composable
private fun MentionSuggestionOverlay(
    actualViewModel: ChatViewModel,
    bottomBarHeightPx: Int,
    inputBarTranslationYPx: Float,
    panelStyle: MentionSuggestionPanelStyle,
) {
    val density = LocalDensity.current
    val showMentionSuggestionPanel by actualViewModel.showMentionSuggestionPanel.collectAsState()
    val bottomPaddingForSelector = with(density) { bottomBarHeightPx.toDp() }

    AnimatedVisibility(
        visible = showMentionSuggestionPanel,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = actualViewModel::hideMentionSuggestionPanel,
                        ),
            )
            MentionSuggestionPanel(
                modifier =
                    Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = bottomPaddingForSelector + 8.dp)
                        .graphicsLayer { translationY = -inputBarTranslationYPx }
                        .animateEnterExit(
                            enter = slideInVertically(
                                animationSpec = tween(durationMillis = 240),
                            ) { it / 4 },
                            exit = slideOutVertically(
                                animationSpec = tween(durationMillis = 200),
                            ) { it / 4 },
                        ),
                viewModel = actualViewModel,
                panelStyle = panelStyle,
                onFileSelected = { relativePath ->
                    actualViewModel.selectMentionWorkspaceEntry(relativePath)
                },
                onPackageSelected = actualViewModel::selectMentionPackage,
            )
        }
    }
}
