package com.ai.assistance.operit.ui.features.conversation.chat.screens

import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.chatMessageAppearance
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import com.ai.assistance.operit.ui.features.conversation.chat.modules.voice.VoiceMessageSheet
import com.ai.assistance.operit.core.chat.voice.canStartVoiceMessage

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import com.ai.assistance.operit.data.preferences.transparency.ThemeTransparencyPreferences
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.list.animateScrollToEnd
import android.content.ClipboardManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.util.logging.AppLogger
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CodeOff
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.ui.components.ErrorDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.agent.AgentChatInputSection
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.classic.ClassicChatInputSection
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common.ChatInputEvents
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common.ChatInputHookContext
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common.ChatInputHookRegistry
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common.ChatInputSubmitActions
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.classic.ClassicChatSettingsBar
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.PendingQueueMessageItem
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.AndroidExportDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.ExportCompleteDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.ExportPlatformDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.ExportProgressDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.WindowsExportDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.mentions.MentionSuggestionPanelStyle
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceHubScreen
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceHubOverlay
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceHubPage
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.mentions.MentionSuggestionPanel
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.*
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.main.PendingChatDraftHandler
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import com.ai.assistance.operit.ui.main.SharedFileHandler
import java.io.File
import kotlinx.coroutines.flow.first
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.main.components.LocalSetScreenSoftInputMode
import com.ai.assistance.operit.ui.main.components.LocalSetUseScreenImePadding
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.PendingMessageQueueState
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.plugins.chatview.ChatViewEvent
import com.ai.assistance.operit.plugins.chatview.ChatViewHookParams
import com.ai.assistance.operit.plugins.chatview.ChatViewHookPluginRegistry
import java.util.UUID
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatStyle
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.memory.MemoryFolderSelectionDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.imports.SharedIncomingContentHandler
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.exportAndroidApp
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.exportWindowsApp
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.extractClipboardPastedText
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.extractInsertedText
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalMainChatSidebarState
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalOpenMainChatSidebar
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.rememberChatSidebarState

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
        onNavigateToSettings: () -> Unit = {},
        onNavigateToMemoryBase: () -> Unit = {},
        onNavigateToModelConfig: () -> Unit = {},
        onNavigateToModelPrompts: () -> Unit = {},
        onNavigateToPackageManager: () -> Unit = {},
        onNavigateToHtmlPackager: () -> Unit = {},
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
                    OKToast.warning(
                                    context,
                                    context.getString(R.string.microphone_permission_denied),
                                    OKToast.LENGTH_SHORT
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
    val chatInputTransparent = themeSnapshot.chatInputTransparent
    val chatInputOpacity = themeSnapshot.chatInputOpacity
    val chatInputFloating = themeSnapshot.chatInputFloating
    val chatInputLiquidGlassRaw = themeSnapshot.chatInputLiquidGlass
    val chatInputWaterGlass = themeSnapshot.chatInputWaterGlass
    val chatInputLiquidGlass = chatInputLiquidGlassRaw && !chatInputWaterGlass
    val showInputProcessingStatus = themeSnapshot.showInputProcessingStatus
    val enableEnterToSend by displayPreferencesManager.enableEnterToSend.collectAsState(initial = false)
    val showChatFloatingDotsAnimation = themeSnapshot.showChatFloatingDotsAnimation
    val hasBackgroundImageFromPrefs = useBackgroundImage && backgroundImageUri != null
    val effectiveHasBackgroundImage = hasBackgroundImage || hasBackgroundImageFromPrefs

    // 用户和 AI 分别选择渲染风格，输入框风格仍保持独立。
    val userChatStyle = if (themeSnapshot.userChatStyle == UserPreferencesManager.CHAT_STYLE_BUBBLE) ChatStyle.BUBBLE else ChatStyle.CURSOR
    val aiChatStyle = if (themeSnapshot.aiChatStyle == UserPreferencesManager.CHAT_STYLE_BUBBLE) ChatStyle.BUBBLE else ChatStyle.CURSOR
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
    val modelName by actualViewModel.modelName.collectAsState()
    val chatHistory by actualViewModel.chatHistory.collectAsState()
    val newChatIntroId by actualViewModel.chatListMotion.newChatId.collectAsState()
    val loadedChatId by actualViewModel.loadedChatId.collectAsState()
    val introDraft by actualViewModel.userMessage.collectAsState()
    // 仅对当前会话显示处理中状态（影响“停止/发送”按钮）
    val isLoading by actualViewModel.currentChatIsLoading.collectAsState()
    val errorMessage by actualViewModel.errorMessage.collectAsState()
    // 按会话隔离的输入处理状态（用于进度条文案）
    val inputProcessingState by actualViewModel.currentChatInputProcessingState.collectAsState()

    val featureStates by actualViewModel.featureToggles.collectAsState()
    val enableThinkingMode by actualViewModel.enableThinkingMode.collectAsState() // 收集思考模式状态
    val thinkingOptionId by actualViewModel.thinkingOptionId.collectAsState()
    val enableMemoryAutoUpdate by actualViewModel.enableMemoryAutoUpdate.collectAsState()
    val enableTools by actualViewModel.enableTools.collectAsState()
    val toolPromptVisibility by actualViewModel.toolPromptVisibility.collectAsState()
    val toolPromptOrder by actualViewModel.toolPromptOrder.collectAsState()
    val disableStreamOutput by actualViewModel.disableStreamOutput.collectAsState()
    val disableUserPreferenceDescription by
            actualViewModel.disableUserPreferenceDescription.collectAsState()
    val summaryTokenThreshold by actualViewModel.summaryTokenThreshold.collectAsState()
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
        onClearSharedText = SharedFileHandler::clearSharedText,
        onShowMessage = { message, kind -> OKToast.typed(context, message, kind).show() }
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
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val historySidebarState = if (embedded) {
        rememberChatSidebarState(actualViewModel)
    } else {
        LocalMainChatSidebarState.current
    }
    val activePrompt = historySidebarState.activePrompt
    val activeCharacterCard = historySidebarState.activeCharacterCard
    val activeCharacterGroup = historySidebarState.activeCharacterGroup
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


    val messageAppearance = chatMessageAppearance()
    val userMessageColor = messageAppearance.userMessageColor
    val aiMessageColor = messageAppearance.aiMessageColor
    val userTextColor = messageAppearance.userTextColor
    val aiTextColor = messageAppearance.aiTextColor
    val systemMessageColor = messageAppearance.systemMessageColor
    val systemTextColor = messageAppearance.systemTextColor
    val thinkingBackgroundColor = messageAppearance.thinkingBackgroundColor
    val thinkingTextColor = messageAppearance.thinkingTextColor
    val bubbleUserImageStyle = messageAppearance.bubbleUserImageStyle
    val bubbleAiImageStyle = messageAppearance.bubbleAiImageStyle

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
                        scrollState.animateScrollToEnd()
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

    // Save chat on app exit
    DisposableEffect(Unit) {
        onDispose {
            // This is handled by the ViewModel
        }
    }

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

    val workspaceHubPage by actualViewModel.workspaceHubPage.collectAsState()
    val isWorkspaceHubOpen = workspaceHubPage != WorkspaceHubPage.CHAT
    // 聊天输入框和工作台终端各自维护 IME 位移；终端不能再叠加外层 imePadding。
    val shouldUseChatLocalImeHandling =
        (embedded &&
            inputStyle == UserPreferencesManager.INPUT_STYLE_AGENT) ||
            (isWorkspaceHubOpen && workspaceHubPage == WorkspaceHubPage.TERMINAL)
    var hasEverShownWorkspaceHub by remember { mutableStateOf(false) }
    LaunchedEffect(isWorkspaceHubOpen) {
        if (isWorkspaceHubOpen) hasEverShownWorkspaceHub = true
    }
    // 当手势状态改变时，通知父组件
    LaunchedEffect(chatScreenGestureConsumed, isWorkspaceHubOpen) {
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

    val setScreenSoftInputMode = LocalSetScreenSoftInputMode.current
    val setUseScreenImePadding = LocalSetUseScreenImePadding.current
    val requestedSoftInputMode =
        if (shouldUseChatLocalImeHandling) {
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        } else {
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    val shouldUseGlobalImePadding = !shouldUseChatLocalImeHandling

    SideEffect {
        if (isCurrentScreen && !embedded) {
            setScreenSoftInputMode(requestedSoftInputMode)
            setUseScreenImePadding(shouldUseGlobalImePadding)
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
    ChatScreenShell(
        embedded = embedded, viewModel = actualViewModel, sidebarState = historySidebarState,
        onOpenCharacterSettings = onNavigateToModelPrompts,
    ) {
        CustomScaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
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
                            userChatStyle = userChatStyle,
                            aiChatStyle = aiChatStyle,
                            cursorUserBubbleLiquidGlass = cursorUserBubbleLiquidGlass,
                            cursorUserBubbleWaterGlass = cursorUserBubbleWaterGlass,
                            bubbleUserBubbleLiquidGlass = bubbleUserBubbleLiquidGlass,
                            bubbleUserBubbleWaterGlass = bubbleUserBubbleWaterGlass,
                            bubbleAiBubbleLiquidGlass = bubbleAiBubbleLiquidGlass,
                            bubbleAiBubbleWaterGlass = bubbleAiBubbleWaterGlass,
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
                                actualViewModel = actualViewModel,
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
                                thinkingOptionId = thinkingOptionId,
                                onThinkingOptionIdChange = {
                                    actualViewModel.updateThinkingOptionId(it)
                                },
                                maxWindowSizeInK =
                                        actualViewModel.maxWindowSizeInK.collectAsState().value,
                                onContextLengthChange = {
                                    actualViewModel.updateContextLength(it)
                                },
                                enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                                onToggleMemoryAutoUpdate = {
                                    actualViewModel.toggleMemoryAutoUpdate()
                                },
                                summaryTokenThreshold = summaryTokenThreshold,
                                onSummaryTokenThresholdChange = {
                                    actualViewModel.updateSummaryTokenThreshold(it)
                                },
                                onNavigateToMemoryBase = onNavigateToMemoryBase,
                                onNavigateToModelConfig = onNavigateToModelConfig,
                                onNavigateToModelPrompts = onNavigateToModelPrompts,
                                onNavigateToPackageManager = onNavigateToPackageManager,
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
                            chatInputOpacity = chatInputOpacity,
                            chatInputFloating = chatInputFloating,
                            chatInputLiquidGlass = chatInputLiquidGlass,
                            chatInputWaterGlass = chatInputWaterGlass,
                            showInputProcessingStatus = showInputProcessingStatus,
                            enableTools = enableTools,
                            isWorkspaceOpen = isWorkspaceOpen,
                            enableThinkingMode = enableThinkingMode,
                            thinkingOptionId = thinkingOptionId,
                            featureStates = featureStates,
                            enableMemoryAutoUpdate = enableMemoryAutoUpdate,
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

        val isWorkspaceHubVisible = isCurrentScreen && !embedded && isWorkspaceHubOpen
        WorkspaceHubOverlay(visible = isWorkspaceHubVisible) { overlayVisible ->
            if (!embedded && (hasEverShownWorkspaceHub || isWorkspaceHubVisible)) {
                WorkspaceHubScreen(
                    actualViewModel = actualViewModel,
                    currentChat = currentChatView,
                    chatHistories = chatHistories,
                    isVisible = overlayVisible,
                    onOpenHtmlPackager = onNavigateToHtmlPackager,
                    reserveChatControlsSpace = true,
                    onExportClick = { workDir ->
                        webContentDir = workDir
                        showExportPlatformDialog = true
                    },
                )
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

        com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.motion.NewChatIntro(
            requestId = newChatIntroId, currentChatId = currentChatId,
            ready = loadedChatId == currentChatId && currentChatId != null && !isLoading && !isLoadingDisplayWindow, empty = chatHistory.isEmpty(),
            hasDraft = introDraft.text.isNotEmpty(), onConsumed = actualViewModel.chatListMotion::consumeIntro,
            modifier = Modifier.fillMaxSize().padding(bottom = bottomBarHeightDp),
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
            OKToast.warning(
                            context,
                            context.getString(R.string.floating_window_permission_denied),
                            OKToast.LENGTH_SHORT
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
    chatInputOpacity: Float?,
    chatInputFloating: Boolean,
    chatInputLiquidGlass: Boolean,
    chatInputWaterGlass: Boolean,
    showInputProcessingStatus: Boolean,
    enableTools: Boolean,
    isWorkspaceOpen: Boolean,
    enableThinkingMode: Boolean,
    thinkingOptionId: String,
    featureStates: Map<String, Boolean>,
    enableMemoryAutoUpdate: Boolean,
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
    val chatHistory by actualViewModel.chatHistory.collectAsState()
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
            inputState is InputProcessingState.Receiving ||
            inputState is InputProcessingState.ExecutingPlan
    val isQueueBlocked = isMessageProcessing || isSummarizing || isSendTriggeredSummarizing

    val pendingQueueStates by actualViewModel.pendingMessageQueueStates.collectAsState()
    val pendingQueueState =
        currentChatId?.let { chatId -> pendingQueueStates[chatId] } ?: PendingMessageQueueState()
    val pendingQueueMessages = pendingQueueState.messages
    val isPendingQueueExpanded = pendingQueueState.isExpanded
    val waifuMergeBuffer = remember(currentChatId) { mutableStateListOf<String>() }
    val latestQueueBlocked = rememberUpdatedState(isQueueBlocked)
    val latestCurrentChatId = rememberUpdatedState(currentChatId)

    fun isContinueGenerationAvailable(): Boolean =
        !currentChatId.isNullOrBlank() &&
            chatHistory.any { message ->
                (message.sender == "user" || message.sender == "ai") &&
                    message.content.isNotBlank()
            } &&
            !isQueueBlocked &&
            pendingQueueMessages.isEmpty() &&
            waifuMergeBuffer.isEmpty()

    val continueGenerationEnabled = isContinueGenerationAvailable()
    val continueGeneration: () -> Unit = {
        if (isContinueGenerationAvailable()) {
            actualViewModel.continueGeneration()
            onRequestAutoScrollToBottom()
        }
    }

    fun buildChatInputHookContext(
        eventName: String,
        text: String = userMessage.text,
        selectionStart: Int = userMessage.selection.start,
        selectionEnd: Int = userMessage.selection.end,
        source: String = inputStyle,
        submitSource: String = "",
        chatId: String? = currentChatId,
        attachmentCount: Int = attachments.size,
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
            hasAttachments = attachmentCount > 0,
            attachmentCount = attachmentCount,
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
        // Gate clipboard access behind a long insertion. Reading primaryClip on every
        // keystroke makes Android show the "app read clipboard" toast and is unused
        // unless the insertion is already long enough to convert into an attachment.
        val insertedText =
            if (convertLongPastedTextToFile && !currentChatId.isNullOrBlank()) {
                extractInsertedText(userMessage, value)
            } else {
                null
            }
        val pastedText =
            if (insertedText != null && insertedText.length > longPastedTextFileThreshold) {
                val clipboardText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
                if (clipboardText != null) {
                    extractClipboardPastedText(userMessage, value, clipboardText)
                } else {
                    null
                }
            } else {
                null
            }

        if (pastedText != null) {
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
            OKToast.info(context, normalizedMessage).show()
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
            OKToast.warning(
                context,
                context.getString(R.string.chat_please_create_new_chat),
                OKToast.LENGTH_SHORT,
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
        OKToast.info(context, context.getString(R.string.chat_queue_added)).show()
    }

    val sendMessage: () -> Unit = {
        coroutineScope.launch {
            if (currentChatId.isNullOrBlank()) {
                OKToast.warning(
                    context,
                    context.getString(R.string.chat_please_create_new_chat),
                    OKToast.LENGTH_SHORT,
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

    var voiceRecordingChatId by remember { mutableStateOf<String?>(null) }
    var prepareVoiceChat by remember { mutableStateOf(false) }
    val voiceKeyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(currentChatId) {
        if (prepareVoiceChat && !currentChatId.isNullOrBlank()) {
            prepareVoiceChat = false
            if (canStartVoiceMessage(userMessage.text, attachments.size, isLoading, replyToMessage != null)) {
                voiceRecordingChatId = currentChatId
            }
        } else if (voiceRecordingChatId != currentChatId) {
            voiceRecordingChatId = null
        }
    }
    val recordVoiceMessage: () -> Unit = {
        if (canStartVoiceMessage(userMessage.text, attachments.size,
                isLoading || (inputState !is InputProcessingState.Idle && inputState !is InputProcessingState.Completed && inputState !is InputProcessingState.Error),
                replyToMessage != null)) {
            actualViewModel.resetAttachmentPanelState()
            focusManager.clearFocus()
            voiceKeyboardController?.hide()
            val chatId = currentChatId
            if (chatId.isNullOrBlank()) {
                if (!prepareVoiceChat) {
                    prepareVoiceChat = true
                    actualViewModel.createNewChat()
                }
            } else {
                voiceRecordingChatId = chatId
            }
        }
    }
    voiceRecordingChatId?.let { originChatId ->
        key(originChatId) {
            VoiceMessageSheet(onDismiss = { voiceRecordingChatId = null }, onSend = { message, onPersisted ->
                val decision = ChatInputHookRegistry.dispatchSubmitRequested(
                    buildChatInputHookContext(eventName = ChatInputEvents.SUBMIT_REQUESTED,
                        text = message.transcript, selectionStart = message.transcript.length,
                        selectionEnd = message.transcript.length, submitSource = "voice",
                        chatId = originChatId, attachmentCount = 1)
                )
                if (decision.action == ChatInputSubmitActions.BLOCK || decision.action == ChatInputSubmitActions.CONSUME) {
                    showChatInputHookMessage(decision.message)
                    false
                } else {
                    showChatInputHookMessage(decision.noticeMessage)
                    val finalTranscript = decision.text ?: message.transcript
                    val accepted = actualViewModel.sendVoiceMessage(message.copy(transcript = finalTranscript), originChatId, onPersisted)
                    if (accepted) {
                        onRequestAutoScrollToBottom()
                        ChatInputHookRegistry.dispatchNotification(
                            buildChatInputHookContext(eventName = ChatInputEvents.SUBMITTED,
                                text = finalTranscript, selectionStart = finalTranscript.length,
                                selectionEnd = finalTranscript.length, submitSource = "voice",
                                chatId = originChatId, attachmentCount = 1)
                        )
                    } else OKToast.warning(context, context.getString(R.string.voice_message_standalone)).show()
                    accepted
                }
            })
        }
    }

    if (inputStyle == UserPreferencesManager.INPUT_STYLE_AGENT) {
        AgentChatInputSection(
                actualViewModel = actualViewModel,
                userMessage = userMessage,
                onUserMessageChange = { value -> handleUserMessageChange(value) },
                enableEnterToSend = enableEnterToSend,
                onSendMessage = sendMessage,
                onRecordVoiceMessage = recordVoiceMessage,
                onContinueGeneration = continueGeneration,
                continueGenerationEnabled = continueGenerationEnabled,
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
                chatInputOpacity = chatInputOpacity,
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
                thinkingOptionId = thinkingOptionId,
                onThinkingOptionIdChange = actualViewModel::updateThinkingOptionId,
                currentChatId = currentChatId,
                featureStates = featureStates,
                onToggleFeature = actualViewModel::toggleFeature,
                inputMenuRuntime = inputMenuRuntime,
                permissionLevel = permissionLevel,
                onSetPermissionLevel = actualViewModel::setMasterPermissionLevel,
                enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                onToggleMemoryAutoUpdate = actualViewModel::toggleMemoryAutoUpdate,
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
                onRecordVoiceMessage = recordVoiceMessage,
                onContinueGeneration = continueGeneration,
                continueGenerationEnabled = continueGenerationEnabled,
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
                chatInputOpacity = chatInputOpacity,
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
