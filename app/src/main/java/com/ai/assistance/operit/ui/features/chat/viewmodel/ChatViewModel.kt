package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextFieldValue.Companion
import androidx.core.content.FileProvider
import com.ai.assistance.operit.ui.features.chat.components.ChatStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.floating.ui.pet.AvatarEmotionManager
import com.ai.assistance.operit.api.voice.VoiceService
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceBackupManager
import com.ai.assistance.operit.ui.features.chat.webview.workspace.CommandConfig
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceCommandExecutionState
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceConfigReader
import com.ai.assistance.operit.ui.features.chat.webview.workspace.toWorkspaceCommandOutputEntries
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.util.TtsCleaner
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
// 使用 services/core 的 Delegate 类
import com.ai.assistance.operit.services.core.MessageProcessingDelegate
import com.ai.assistance.operit.services.core.ChatHistoryDelegate
import com.ai.assistance.operit.services.core.ApiConfigDelegate
import com.ai.assistance.operit.services.core.TokenStatisticsDelegate
import com.ai.assistance.operit.services.core.AttachmentDelegate
import com.ai.assistance.operit.services.core.MessageCoordinationDelegate
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.WorkspaceRenameResult
import com.ai.assistance.operit.services.ChatServiceCore
import com.ai.assistance.operit.services.ChatServiceUiBridge
import com.ai.assistance.operit.services.EmptyChatServiceUiBridge
import com.ai.assistance.operit.ui.features.chat.util.MessageImageGenerator
import com.ai.assistance.operit.ui.features.chat.components.CharacterSelectorTarget
enum class ChatHistoryDisplayMode {
    BY_CHARACTER_CARD,
    BY_FOLDER,
    CURRENT_CHARACTER_ONLY
}

class ChatViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MIN_WORKSPACE_FILE_QUERY_LENGTH = 2
    }

    // 添加语音服务
    private var voiceService: VoiceService? = null
    private var voiceStateCollectionJob: Job? = null
    private val speechServicesPreferences = SpeechServicesPreferences(context)
    private val activePromptManager = ActivePromptManager.getInstance(context)
    private val characterCardManager = CharacterCardManager.getInstance(context)

    // 添加语音播放状态
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // 添加自动朗读状态 - Now managed by ApiConfigDelegate
    val isAutoReadEnabled: StateFlow<Boolean> by lazy { apiConfigDelegate.enableAutoRead }

    // 添加回复相关状态
    private val _replyToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyToMessage: StateFlow<ChatMessage?> = _replyToMessage.asStateFlow()

    // API服务
    private var enhancedAiService: EnhancedAIService? = null

    // 工具处理器
    private val toolHandler = AIToolHandler.getInstance(context)
    private val chatRuntimeHolder = ChatRuntimeHolder.getInstance(context)

    // 工具权限系统
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)
    
    // 终端管理器（用于执行工作区命令）
    @RequiresApi(Build.VERSION_CODES.O)
    private val terminal: Terminal? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Terminal.getInstance(context)
    } else {
        null
    }
    
    // 工作区终端会话映射表：workspacePath -> sessionId
    private val workspaceTerminalSessions = mutableMapOf<String, String>()
    private var workspaceCommandExecutionJob: Job? = null
    private var workspaceOpenJob: Job? = null
    private var inputProcessingStateListenerJob: Job? = null

    private lateinit var mainChatCore: ChatServiceCore
    private lateinit var attachmentDelegate: AttachmentDelegate
    lateinit var uiStateDelegate: UiStateDelegate
        private set
    private lateinit var tokenStatsDelegate: TokenStatisticsDelegate
    private lateinit var apiConfigDelegate: ApiConfigDelegate
    private lateinit var chatHistoryDelegate: ChatHistoryDelegate
    private lateinit var messageProcessingDelegate: MessageProcessingDelegate
    private lateinit var floatingWindowDelegate: FloatingWindowDelegate
    private lateinit var messageCoordinationDelegate: MessageCoordinationDelegate

    // Use lazy initialization for exposed properties to avoid circular reference issues
    // API配置相关
    val apiKey: StateFlow<String> by lazy { apiConfigDelegate.apiKey }
    val apiEndpoint: StateFlow<String> by lazy { apiConfigDelegate.apiEndpoint }
    val modelName: StateFlow<String> by lazy { apiConfigDelegate.modelName }
    val apiProviderType: StateFlow<ApiProviderType> by lazy { apiConfigDelegate.apiProviderType }
    val isConfigured: StateFlow<Boolean> by lazy { apiConfigDelegate.isConfigured }
    val isApiConfigInitialized: StateFlow<Boolean> by lazy { apiConfigDelegate.isInitialized }

    private val _shouldShowConfigDialog = MutableStateFlow(false)
    val shouldShowConfigDialog: StateFlow<Boolean> = _shouldShowConfigDialog.asStateFlow()

    fun onConfigDialogConfirmed() {
        _shouldShowConfigDialog.value = false
    }

    fun showConfigurationScreen() {
        _shouldShowConfigDialog.value = true
    }

    val featureToggles: StateFlow<Map<String, Boolean>> by lazy { apiConfigDelegate.featureToggles }
    val keepScreenOn: StateFlow<Boolean> by lazy { apiConfigDelegate.keepScreenOn }

    // 思考模式和思考引导状态现在由ApiConfigDelegate管理
    val enableThinkingMode: StateFlow<Boolean> by lazy { apiConfigDelegate.enableThinkingMode }
    val enableThinkingGuidance: StateFlow<Boolean> by lazy { apiConfigDelegate.enableThinkingGuidance }
    val thinkingQualityLevel: StateFlow<Int> by lazy { apiConfigDelegate.thinkingQualityLevel }
    val enableMemoryAutoUpdate: StateFlow<Boolean> by lazy { apiConfigDelegate.enableMemoryAutoUpdate }
    val enableTools: StateFlow<Boolean> by lazy { apiConfigDelegate.enableTools }
    val toolPromptVisibility: StateFlow<Map<String, Boolean>> by lazy { apiConfigDelegate.toolPromptVisibility }
    val disableStreamOutput: StateFlow<Boolean> by lazy { apiConfigDelegate.disableStreamOutput }
    val disableUserPreferenceDescription: StateFlow<Boolean> by lazy {
        apiConfigDelegate.disableUserPreferenceDescription
    }
    val disableStatusTags: StateFlow<Boolean> by lazy { apiConfigDelegate.disableStatusTags }

    val summaryTokenThreshold: StateFlow<Float> by lazy { apiConfigDelegate.summaryTokenThreshold }
    val enableSummary: StateFlow<Boolean> by lazy { apiConfigDelegate.enableSummary }
    val enableSummaryByMessageCount: StateFlow<Boolean> by lazy { apiConfigDelegate.enableSummaryByMessageCount }
    val summaryMessageCountThreshold: StateFlow<Int> by lazy { apiConfigDelegate.summaryMessageCountThreshold }

    // 上下文长度
    val maxWindowSizeInK: StateFlow<Float> by lazy { apiConfigDelegate.contextLength }
    val baseContextLengthInK: StateFlow<Float> by lazy { apiConfigDelegate.baseContextLength }
    val maxContextLengthInK: StateFlow<Float> by lazy { apiConfigDelegate.maxContextLengthSetting }
    val enableMaxContextMode: StateFlow<Boolean> by lazy { apiConfigDelegate.enableMaxContextMode }

    // 聊天历史相关
    val chatHistory: StateFlow<List<ChatMessage>> by lazy { chatHistoryDelegate.chatHistory }
    val showChatHistorySelector: StateFlow<Boolean> by lazy {
        chatHistoryDelegate.showChatHistorySelector
    }
    val chatHistories: StateFlow<List<ChatHistory>> by lazy { chatHistoryDelegate.chatHistories }
    val currentChatId: StateFlow<String?> by lazy { chatHistoryDelegate.currentChatId }

    // 消息处理相关
    val userMessage: StateFlow<TextFieldValue> by lazy { messageProcessingDelegate.userMessage }
    val isLoading: StateFlow<Boolean> by lazy { messageProcessingDelegate.isLoading }

    // 会话隔离：仅当“当前聊天ID == 正在流式的聊天ID”时，才显示处理中/停止按钮
    val activeStreamingChatIds: StateFlow<Set<String>> by lazy { messageProcessingDelegate.activeStreamingChatIds }
    val currentChatIsLoading: StateFlow<Boolean> by lazy {
        kotlinx.coroutines.flow.combine(
            chatHistoryDelegate.currentChatId,
            messageProcessingDelegate.activeStreamingChatIds
        ) { currentId, activeIds ->
            currentId != null && activeIds.contains(currentId)
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = false
        )
    }
    val currentChatInputProcessingState: StateFlow<InputProcessingState> by lazy {
        kotlinx.coroutines.flow.combine(
            chatHistoryDelegate.currentChatId,
            messageProcessingDelegate.inputProcessingStateByChatId
        ) { currentId, stateMap ->
            if (currentId == null) return@combine InputProcessingState.Idle
            stateMap[currentId] ?: InputProcessingState.Idle
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = InputProcessingState.Idle
        )
    }

    val scrollToBottomEvent: SharedFlow<Unit> by lazy {
        messageProcessingDelegate.scrollToBottomEvent
    }

    // UI状态相关
    val errorMessage: StateFlow<String?> by lazy { uiStateDelegate.errorMessage }
    val popupMessage: StateFlow<String?> by lazy { uiStateDelegate.popupMessage }
    val toastEvent: StateFlow<String?> by lazy { uiStateDelegate.toastEvent }
    val masterPermissionLevel: StateFlow<PermissionLevel> by lazy {
        uiStateDelegate.masterPermissionLevel
    }

    // 聊天统计相关
    val currentWindowSize: StateFlow<Int> by lazy { tokenStatsDelegate.currentWindowSizeFlow }
    val inputTokenCount: StateFlow<Int> by lazy { tokenStatsDelegate.cumulativeInputTokensFlow }
    val outputTokenCount: StateFlow<Int> by lazy { tokenStatsDelegate.cumulativeOutputTokensFlow }
    val perRequestTokenCount: StateFlow<Pair<Int, Int>?> by lazy { tokenStatsDelegate.perRequestTokenCountFlow }



    // 悬浮窗相关
    val isFloatingMode: StateFlow<Boolean> by lazy { floatingWindowDelegate.isFloatingMode }
    val moveTaskToBackEvents: SharedFlow<Unit> by lazy { floatingWindowDelegate.moveTaskToBackEvents }

    // 附件相关
    val attachments: StateFlow<List<AttachmentInfo>> by lazy { attachmentDelegate.attachments }

    // 聊天历史搜索状态
    private val _chatHistorySearchQuery = MutableStateFlow("")
    val chatHistorySearchQuery: StateFlow<String> = _chatHistorySearchQuery.asStateFlow()

    fun onChatHistorySearchQueryChange(query: String) {
        _chatHistorySearchQuery.value = query
    }

    fun setHistoryDisplayMode(mode: ChatHistoryDisplayMode) {
        _historyDisplayMode.value = mode
    }

    fun setAutoSwitchCharacterCard(enabled: Boolean) {
        _autoSwitchCharacterCard.value = enabled
    }

    fun toggleAutoSwitchCharacterCard() {
        setAutoSwitchCharacterCard(!_autoSwitchCharacterCard.value)
    }

    fun setAutoSwitchChatOnCharacterSelect(enabled: Boolean) {
        _autoSwitchChatOnCharacterSelect.value = enabled
    }

    private val _historyDisplayMode =
            MutableStateFlow(ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY)
    val historyDisplayMode: StateFlow<ChatHistoryDisplayMode> = _historyDisplayMode.asStateFlow()

    private val _autoSwitchCharacterCard = MutableStateFlow(false)
    val autoSwitchCharacterCard: StateFlow<Boolean> = _autoSwitchCharacterCard.asStateFlow()

    private val _autoSwitchChatOnCharacterSelect = MutableStateFlow(false)
    val autoSwitchChatOnCharacterSelect: StateFlow<Boolean> =
        _autoSwitchChatOnCharacterSelect.asStateFlow()
    
    // 总结状态
    val isSummarizing: StateFlow<Boolean> by lazy {
        if (::messageCoordinationDelegate.isInitialized) {
            messageCoordinationDelegate.isSummarizing
        } else {
            MutableStateFlow(false)
        }
    }
    val isSendTriggeredSummarizing: StateFlow<Boolean> by lazy {
        if (::messageCoordinationDelegate.isInitialized) {
            messageCoordinationDelegate.isSendTriggeredSummarizing
        } else {
            MutableStateFlow(false)
        }
    }

    fun handleSharedLinks(urls: List<String>, targetChatId: String? = null) {
        AppLogger.d(TAG, "handleSharedLinks called with ${urls.size} link(s)")
        urls.forEachIndexed { index, url ->
            AppLogger.d(TAG, "  [$index] URL: $url")
        }

        viewModelScope.launch {
            try {
                val chatId = resolveTargetChatIdForSharedContent(targetChatId)
                if (chatId == null) {
                    AppLogger.e(TAG, "Failed to prepare target chat for shared links")
                    uiStateDelegate.showErrorMessage(
                        context.getString(R.string.chat_prepare_shared_target_failed)
                    )
                    return@launch
                }

                messageProcessingDelegate.setInputProcessingStateForChat(
                    chatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_processing_shared_files))
                )

                val now = System.currentTimeMillis()
                val attachmentsToAdd = urls
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .mapIndexed { index, url ->
                        val host = runCatching { Uri.parse(url).host }.getOrNull()
                        val fileName = if (!host.isNullOrBlank()) "${host}.url" else "link.url"
                        AttachmentInfo(
                            filePath = "link_${now}_$index",
                            fileName = fileName,
                            mimeType = "text/plain",
                            fileSize = url.length.toLong(),
                            content = url
                        )
                    }

                attachmentDelegate.addAttachments(attachmentsToAdd)

                messageProcessingDelegate.updateUserMessage(
                    TextFieldValue(context.getString(R.string.chat_prefill_check_file))
                )

                messageProcessingDelegate.setInputProcessingStateForChat(
                    chatId,
                    InputProcessingState.Idle
                )

                uiStateDelegate.showToast(context.getString(R.string.chat_added_files_count, attachmentsToAdd.size))
            } catch (e: Exception) {
                AppLogger.e(TAG, "处理分享链接失败", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_process_shared_files_failed, e.message ?: ""))
                val chatId = currentChatId.value
                if (chatId != null) {
                    messageProcessingDelegate.setInputProcessingStateForChat(chatId, InputProcessingState.Idle)
                }
            }
        }
    }

    // 添加一个用于跟踪附件面板状态的变量
    private val _attachmentPanelState = MutableStateFlow(false)
    val attachmentPanelState: StateFlow<Boolean> = _attachmentPanelState

    // 添加WebView显示状态的状态流
    private val _showWebView = MutableStateFlow(false)
    val showWebView: StateFlow<Boolean> = _showWebView.asStateFlow()

    private val _isWorkspacePreparing = MutableStateFlow(false)
    val isWorkspacePreparing: StateFlow<Boolean> = _isWorkspacePreparing.asStateFlow()

    // 添加工作区状态
    val isWorkspaceOpen: StateFlow<Boolean> by lazy {
        combine(currentChatId, chatHistories) { id, histories ->
            histories.find { it.id == id }?.workspace?.isNotBlank() == true
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    }

    // 添加AI电脑显示状态的状态流
    private val _showAiComputer = MutableStateFlow(false)
    val showAiComputer: StateFlow<Boolean> = _showAiComputer

    // 添加WebView刷新控制流 - 使用Int计数器避免重复刷新问题
    private val _webViewRefreshCounter = MutableStateFlow(0)
    val webViewRefreshCounter: StateFlow<Int> = _webViewRefreshCounter

    // 控制工作区文件选择器的可见性
    private val _showWorkspaceFileSelector = MutableStateFlow(false)
    val showWorkspaceFileSelector: StateFlow<Boolean> = _showWorkspaceFileSelector.asStateFlow()

    // 工作区文件搜索词
    private val _workspaceFileSearchQuery = MutableStateFlow("")
    val workspaceFileSearchQuery: StateFlow<String> = _workspaceFileSearchQuery.asStateFlow()

    private val _workspaceCommandExecutionState =
        MutableStateFlow<WorkspaceCommandExecutionState?>(null)
    val workspaceCommandExecutionState: StateFlow<WorkspaceCommandExecutionState?> =
        _workspaceCommandExecutionState.asStateFlow()

    // 文件选择相关回调
    private var fileChooserCallback: ((Int, Intent?) -> Unit)? = null

    init {
        // Initialize delegates in correct order to avoid circular references
        initializeDelegates()

        // Setup additional components
        setupPermissionSystemCollection()
        setupAttachmentDelegateToastCollection()

        // 初始化语音服务
        initializeVoiceService()

        // 配置提示只跟随当前活跃聊天配置，避免被其他未使用配置误伤。
        viewModelScope.launch {
            combine(
                isApiConfigInitialized,
                apiKey,
                apiProviderType,
                apiEndpoint
            ) { initialized, currentApiKey, currentProviderType, currentApiEndpoint ->
                initialized &&
                    ApiProviderConfigs.requiresApiKey(
                        currentProviderType,
                        currentApiEndpoint
                    ) &&
                    currentApiKey == ApiPreferences.DEFAULT_API_KEY
            }.collect { shouldShow ->
                _shouldShowConfigDialog.value = shouldShow
            }
        }
    }

    private fun initializeDelegates() {
        mainChatCore = chatRuntimeHolder.getCore(ChatRuntimeSlot.MAIN)
        uiStateDelegate = mainChatCore.getUiStateDelegate()
        tokenStatsDelegate = mainChatCore.getTokenStatisticsDelegate()
        apiConfigDelegate = mainChatCore.getApiConfigDelegate()
        attachmentDelegate = mainChatCore.getAttachmentDelegate()
        chatHistoryDelegate = mainChatCore.getChatHistoryDelegate()
        messageProcessingDelegate = mainChatCore.getMessageProcessingDelegate()
        messageCoordinationDelegate = mainChatCore.getMessageCoordinationDelegate()

        enhancedAiService = mainChatCore.getEnhancedAiService()
        mainChatCore.setUiBridge(
                object : ChatServiceUiBridge {
                    override fun updateWebServerForCurrentChat(chatId: String) {
                        this@ChatViewModel.updateWebServerForCurrentChat(chatId)
                    }

                    override fun resetAttachmentPanelState() {
                        this@ChatViewModel.resetAttachmentPanelState()
                    }

                    override fun clearReplyToMessage() {
                        this@ChatViewModel.clearReplyToMessage()
                    }

                    override fun getReplyToMessage(): ChatMessage? = replyToMessage.value
                }
        )
        mainChatCore.setSpeakMessageHandler(::speakMessage)
        mainChatCore.setOnEnhancedAiServiceReady { service ->
            enhancedAiService = service
            setupInputProcessingStateListener(service)
        }

        floatingWindowDelegate =
                FloatingWindowDelegate(
                        context = context,
                        coroutineScope = viewModelScope,
                        inputProcessingState = this.currentChatInputProcessingState
                )
    }

    private fun setupPermissionSystemCollection() {
        viewModelScope.launch {
            toolPermissionSystem.masterSwitchFlow.collect { level ->
                uiStateDelegate.updateMasterPermissionLevel(level)
            }
        }
    }

    private fun setupAttachmentDelegateToastCollection() {
        viewModelScope.launch {
            attachmentDelegate.toastEvent.collect { message -> uiStateDelegate.showToast(message) }
        }
    }

    private fun checkIfShouldCreateNewChat() {
        viewModelScope.launch {
            // 检查历史记录加载后是否需要创建新聊天
            if (chatHistoryDelegate.checkIfShouldCreateNewChat() && isConfigured.value) {
                chatHistoryDelegate.createNewChat()
            }
        }
    }

    /** 设置服务相关的流收集逻辑 */
    /**
     * 设置输入处理状态监听
     * 当 EnhancedAIService 初始化或更新时调用
     */
    private fun setupInputProcessingStateListener(service: EnhancedAIService) {
        AppLogger.d(TAG, "EnhancedAIService 已就绪，开始监听输入处理状态")
        inputProcessingStateListenerJob?.cancel()
        inputProcessingStateListenerJob = viewModelScope.launch {
            try {
                service.inputProcessingState.collect { state ->
                    if (::messageProcessingDelegate.isInitialized && messageProcessingDelegate.isLoading.value) {
                        return@collect
                    }
                    if (state is InputProcessingState.Completed && 
                        ::messageCoordinationDelegate.isInitialized &&
                        (messageCoordinationDelegate.isSummarizing.value ||
                         messageCoordinationDelegate.isSendTriggeredSummarizing.value)
                    ) {
                        val targetChatId =
                            if (messageCoordinationDelegate.isSummarizing.value) {
                                messageCoordinationDelegate.summarizingChatId.value
                            } else {
                                messageCoordinationDelegate.sendTriggeredSummarizingChatId.value
                            }

                        if (targetChatId != null) {
                            messageProcessingDelegate.setInputProcessingStateForChat(
                                targetChatId,
                                InputProcessingState.Summarizing(context.getString(R.string.chat_summarizing_memory))
                            )
                        }
                    } else if (::messageProcessingDelegate.isInitialized) {
                        val currentChatId = chatHistoryDelegate.currentChatId.value
                        if (currentChatId != null) {
                            messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, state)
                        }
                    }
                }
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "输入处理状态监听已取消")
            } catch (e: Exception) {
                AppLogger.e(TAG, "输入处理状态收集出错: ${e.message}", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_input_processing_collect_failed, e.message ?: ""))
            }
        }
    }

    // API配置相关方法
    fun updateApiKey(key: String) = apiConfigDelegate.updateApiKey(key)

    fun updateApiEndpoint(endpoint: String) = apiConfigDelegate.updateApiEndpoint(endpoint)

    fun updateModelName(modelName: String) = apiConfigDelegate.updateModelName(modelName)

    fun updateApiProviderType(providerType: ApiProviderType) = apiConfigDelegate.updateApiProviderType(providerType)
    fun saveApiSettings() = apiConfigDelegate.saveApiSettings()
    fun useDefaultConfig() {
        if (apiConfigDelegate.useDefaultConfig()) {
            uiStateDelegate.showToast(context.getString(R.string.chat_use_default_config_continue))
        } else {
            // 修改：使用错误弹窗而不是Toast显示配置错误
            uiStateDelegate.showErrorMessage(context.getString(R.string.chat_default_config_incomplete))
        }
    }
    fun toggleFeature(featureKey: String) {
        apiConfigDelegate.toggleFeature(featureKey)
    }

    // 切换思考模式的方法现在委托给ApiConfigDelegate
    fun toggleThinkingMode() {
        apiConfigDelegate.toggleThinkingMode()
    }

    // 切换思考引导的方法现在委托给ApiConfigDelegate
    fun toggleThinkingGuidance() {
        apiConfigDelegate.toggleThinkingGuidance()
    }

    fun updateThinkingQualityLevel(level: Int) {
        apiConfigDelegate.updateThinkingQualityLevel(level)
    }

    // 切换记忆自动更新的方法现在委托给ApiConfigDelegate
    fun toggleMemoryAutoUpdate() {
        apiConfigDelegate.toggleMemoryAutoUpdate()
    }

    // 更新上下文长度
    fun updateContextLength(length: Float) {
        apiConfigDelegate.updateContextLength(length)
    }

    fun updateMaxContextLength(length: Float) {
        apiConfigDelegate.updateMaxContextLength(length)
    }

    fun toggleEnableMaxContextMode() {
        apiConfigDelegate.toggleEnableMaxContextMode()
    }

    fun updateSummaryTokenThreshold(threshold: Float) {
        apiConfigDelegate.updateSummaryTokenThreshold(threshold)
    }

    fun toggleEnableSummary() {
        apiConfigDelegate.toggleEnableSummary()
    }

    fun toggleEnableSummaryByMessageCount() {
        apiConfigDelegate.toggleEnableSummaryByMessageCount()
    }

    fun updateSummaryMessageCountThreshold(threshold: Int) {
        apiConfigDelegate.updateSummaryMessageCountThreshold(threshold)
    }

    fun toggleTools() {
        apiConfigDelegate.toggleTools()
    }

    fun saveToolPromptVisibility(toolName: String, isVisible: Boolean) {
        apiConfigDelegate.saveToolPromptVisibility(toolName, isVisible)
    }

    fun saveToolPromptVisibilityMap(visibilityMap: Map<String, Boolean>) {
        apiConfigDelegate.saveToolPromptVisibilityMap(visibilityMap)
    }

    fun toggleDisableStreamOutput() {
        apiConfigDelegate.toggleDisableStreamOutput()
    }

    fun toggleDisableUserPreferenceDescription() {
        apiConfigDelegate.toggleDisableUserPreferenceDescription()
    }

    fun toggleDisableStatusTags() {
        apiConfigDelegate.toggleDisableStatusTags()
    }

    // 聊天历史相关方法
    fun createNewChat(characterCardName: String? = null, characterGroupId: String? = null) {
        chatHistoryDelegate.createNewChat(characterCardName = characterCardName, characterGroupId = characterGroupId)
    }

    fun switchChat(chatId: String) {
        chatHistoryDelegate.switchChat(chatId)
        chatRuntimeHolder.syncMainChatSelectionToFloating(chatId)

        // 如果当前WebView正在显示，则更新工作区并触发刷新
        if (_showWebView.value) {
            viewModelScope.launch {
                try {
                    prepareWorkspaceServerForCurrentChat(chatId)
                    refreshWebView()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "切换聊天后更新工作区服务失败", e)
                    uiStateDelegate.showErrorMessage(
                        context.getString(R.string.chat_update_workspace_server_failed, e.message ?: "")
                    )
                }
            }
        }

        if (_autoSwitchCharacterCard.value) {
            viewModelScope.launch {
                autoSwitchCharacterTargetForChat(chatId)
            }
        }
    }

    fun deleteChatHistory(chatId: String) {
        chatHistoryDelegate.deleteChatHistory(chatId) { deleted ->
            if (!deleted) {
                uiStateDelegate.showToast(context.getString(R.string.chat_locked_cannot_delete))
            }
        }
    }

    fun clearCurrentChat() {
        chatHistoryDelegate.clearCurrentChat { deleted ->
            if (deleted) {
                uiStateDelegate.showToast(context.getString(R.string.chat_cleared))
            } else {
                uiStateDelegate.showToast(context.getString(R.string.chat_locked_cannot_delete))
            }
        }
    }
    fun toggleChatHistorySelector() = chatHistoryDelegate.toggleChatHistorySelector()
    fun showChatHistorySelector(show: Boolean) {
        chatHistoryDelegate.showChatHistorySelector(show)
    }

    fun switchActiveCharacterTarget(target: CharacterSelectorTarget) {
        viewModelScope.launch {
            when (target) {
                is CharacterSelectorTarget.CharacterCardTarget -> {
                    activePromptManager.setActivePrompt(ActivePrompt.CharacterCard(target.id))
                }
                is CharacterSelectorTarget.CharacterGroupTarget -> {
                    activePromptManager.setActivePrompt(ActivePrompt.CharacterGroup(target.id))
                }
            }

            if (_autoSwitchChatOnCharacterSelect.value) {
                autoSwitchChatForCharacterTarget(target)
            }
        }
    }

    private suspend fun autoSwitchCharacterTargetForChat(chatId: String) {
        val targetHistory = chatHistories.value.firstOrNull { it.id == chatId } ?: return
        runCatching {
            activePromptManager.activateForChatBinding(
                characterCardName = targetHistory.characterCardName,
                characterGroupId = targetHistory.characterGroupId
            )
        }.onFailure { throwable ->
            AppLogger.w(TAG, "Auto switch character target failed: ${throwable.message}")
        }
    }

    private suspend fun autoSwitchChatForCharacterTarget(target: CharacterSelectorTarget) {
        runCatching {
            when (target) {
                is CharacterSelectorTarget.CharacterCardTarget -> {
                    val targetCard = characterCardManager.getCharacterCard(target.id)
                    val latestChat = findLatestChatForCharacterCard(targetCard)
                    if (latestChat != null) {
                        switchChat(latestChat.id)
                    } else {
                        chatHistoryDelegate.createNewChat(characterCardId = target.id)
                    }
                }
                is CharacterSelectorTarget.CharacterGroupTarget -> {
                    val targetGroupId = target.id.trim()
                    val latestChat =
                        chatHistories.value
                            .asSequence()
                            .filter { history ->
                                history.characterGroupId?.trim() == targetGroupId
                            }
                            .maxByOrNull { it.updatedAt }
                    if (latestChat != null) {
                        switchChat(latestChat.id)
                    } else {
                        chatHistoryDelegate.createNewChat(characterGroupId = targetGroupId)
                    }
                }
            }
        }.onFailure { throwable ->
            AppLogger.w(TAG, "Auto switch chat for character target failed: ${throwable.message}")
        }
    }

    private fun findLatestChatForCharacterCard(targetCard: com.ai.assistance.operit.data.model.CharacterCard): ChatHistory? {
        val targetCardName = targetCard.name.trim()
        return chatHistories.value
            .asSequence()
            .filter { history ->
                val historyGroupId = history.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
                if (!historyGroupId.isNullOrBlank()) {
                    return@filter false
                }
                val historyCardName = history.characterCardName?.trim()?.takeIf { it.isNotBlank() }
                if (targetCard.isDefault) {
                    historyCardName == null || historyCardName == targetCardName
                } else {
                    historyCardName == targetCardName
                }
            }
            .maxByOrNull { it.updatedAt }
    }

    /** 创建对话分支 */
    fun createBranch(upToMessageTimestamp: Long? = null) {
        chatHistoryDelegate.createBranch(upToMessageTimestamp)
        uiStateDelegate.showToast(context.getString(R.string.chat_branch_created))
    }

    /** 插入总结 */
    fun insertSummary(index: Int, message: ChatMessage) {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) {
                    uiStateDelegate.showToast(context.getString(R.string.chat_no_active_conversation))
                    return@launch
                }
                
                // 设置输入处理状态（按chatId隔离）
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Summarizing(context.getString(R.string.chat_summarizing_generating))
                )
                
                // 获取当前聊天历史
                val currentHistory = chatHistoryDelegate.chatHistory.value
                
                // 确定插入位置：用户消息插入在上面（index），AI消息插入在下面（index+1）
                val insertPosition = if (message.sender == "user") {
                    index
                } else {
                    index + 1
                }
                
                // 获取要总结的消息：从开始到插入位置的消息
                val historyForSummary = currentHistory.subList(0, insertPosition)

                val lastSummaryIndex = historyForSummary.indexOfLast { it.sender == "summary" }
                val messagesToSummarize = when {
                    lastSummaryIndex == -1 -> historyForSummary
                    else -> historyForSummary.subList(lastSummaryIndex + 1, historyForSummary.size)
                }.filter { it.sender == "user" || it.sender == "ai" }

                if (messagesToSummarize.isEmpty()) {
                    uiStateDelegate.showToast(context.getString(R.string.chat_no_messages_to_summarize))
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                    return@launch
                }
                
                // 显示生成中提示
                uiStateDelegate.showToast(context.getString(R.string.chat_summarizing_generating))
                
                // 调用AI生成总结
                if (enhancedAiService == null) {
                    uiStateDelegate.showToast(context.getString(R.string.chat_ai_service_not_initialized))
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                    return@launch
                }

                // 检查是否是群聊
                val currentChat = chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == currentChatId }
                val isGroupChat = currentChat?.characterGroupId != null

                val summaryMessage = AIMessageManager.summarizeMemory(
                    enhancedAiService!!,
                    historyForSummary,
                    autoContinue = false,
                    isGroupChat = isGroupChat
                )

                if (summaryMessage != null) {
                    // 插入总结消息
                    chatHistoryDelegate.addSummaryMessage(summaryMessage, insertPosition)

                    messageCoordinationDelegate.refreshStableContextWindow(chatId = currentChatId)

                    uiStateDelegate.showToast(context.getString(R.string.chat_summary_inserted))
                } else {
                    uiStateDelegate.showToast(context.getString(R.string.chat_summary_generation_failed))
                }
                
                // 清除输入处理状态
                messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "插入总结已取消")
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setInputProcessingStateForChat(
                        currentChatId,
                        InputProcessingState.Idle
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "插入总结时发生错误", e)
                uiStateDelegate.showToast(context.getString(R.string.chat_insert_summary_failed, e.message ?: ""))
                // 发生错误时也需要清除状态
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                }
            }
        }
    }

    /** 删除单条消息 */
    fun deleteMessage(index: Int) {
        AppLogger.d(TAG, "准备删除消息，索引: $index")
        val chatIdSnapshot = chatHistoryDelegate.currentChatId.value
        val historySnapshot = chatHistoryDelegate.chatHistory.value
        val timestampSnapshot = historySnapshot.getOrNull(index)?.timestamp
        if (chatIdSnapshot != null && timestampSnapshot != null) {
            chatHistoryDelegate.deleteMessageByTimestamp(chatIdSnapshot, timestampSnapshot)
        } else {
            chatHistoryDelegate.deleteMessage(index)
        }
    }

    /** 从指定索引删除后续所有消息 */
    fun deleteMessagesFrom(index: Int) {
        viewModelScope.launch {
            AppLogger.d(TAG, "准备从索引 $index 开始删除后续消息")
            chatHistoryDelegate.deleteMessagesFrom(index)
        }
    }

    /** 批量删除消息 */
    fun deleteMessages(indices: Set<Int>) {
        viewModelScope.launch {
            AppLogger.d(TAG, "准备批量删除消息，索引: $indices")
            // 按降序排列索引后依次删除，避免索引偏移问题
            val sortedIndices = indices.sortedDescending()
            sortedIndices.forEach { index ->
                chatHistoryDelegate.deleteMessage(index)
            }
            AppLogger.d(TAG, "批量删除完成")
        }
    }

    /** 分享消息为图片 */
    fun shareMessages(
        context: Context,
        messageIndices: Set<Int>,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        chatStyle: ChatStyle,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "开始生成分享图片，消息索引: $messageIndices")
                
                // 获取当前聊天历史
                val currentHistory = chatHistoryDelegate.chatHistory.value
                
                // 验证索引有效性
                if (messageIndices.any { it < 0 || it >= currentHistory.size }) {
                    onError(context.getString(R.string.chat_invalid_message_index))
                    return@launch
                }
                
                // 获取选中的消息
                val selectedMessages = messageIndices.sorted().map { currentHistory[it] }
                
                AppLogger.d(TAG, "准备生成图片，选中消息数量: ${selectedMessages.size}")
                
                // 生成图片（内部会自动处理线程切换）
                val imageFile = MessageImageGenerator
                    .generateMessageImage(
                        context = context,
                        messages = selectedMessages,
                        userMessageColor = userMessageColor,
                        aiMessageColor = aiMessageColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = systemMessageColor,
                        systemTextColor = systemTextColor,
                        thinkingBackgroundColor = thinkingBackgroundColor,
                        thinkingTextColor = thinkingTextColor,
                        chatStyle = chatStyle
                    )
                
                AppLogger.d(TAG, "图片文件生成成功: ${imageFile.absolutePath}, 大小: ${imageFile.length()} bytes")
                
                // 使用 FileProvider 获取 Uri
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )
                
                AppLogger.d(TAG, "Uri 获取成功: $uri")
                
                onSuccess(uri)
                
            } catch (e: Exception) {
                AppLogger.e(TAG, "生成分享图片失败", e)
                onError(context.getString(R.string.chat_generate_share_image_failed, e.message ?: ""))
            }
        }
    }

    fun saveCurrentChat() {
        viewModelScope.launch {
            val (inputTokens, outputTokens) = tokenStatsDelegate.getCumulativeTokenCounts()
            val currentWindowSize = tokenStatsDelegate.getLastCurrentWindowSize()
            chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, currentWindowSize)
        }
    }

    // 添加消息编辑方法
    fun updateMessage(index: Int, editedMessage: ChatMessage) {
        viewModelScope.launch {
            try {
                // 获取当前聊天历史
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                // 确保索引有效
                if (index < 0 || index >= currentHistory.size) {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_invalid_message_index))
                    return@launch
                }

                // 更新消息
                currentHistory[index] = editedMessage

                // 将更新后的历史记录保存到ChatHistoryDelegate
                // 注意：这里仅更新内存，因为此方法只用于单个消息内容的修改，不涉及历史截断
                chatHistoryDelegate.updateChatHistory(currentHistory)

                // 直接在数据库中更新该条消息
                chatHistoryDelegate.addMessageToChat(editedMessage)

                messageCoordinationDelegate.refreshStableContextWindow(chatId = currentChatId.value)

                // 显示成功提示
                uiStateDelegate.showToast(context.getString(R.string.chat_message_updated))
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新消息失败", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_update_message_failed, e.message ?: ""))
            }
        }
    }

    fun regenerateSingleAiMessage(index: Int) {
        viewModelScope.launch {
            try {
                messageCoordinationDelegate.regenerateSingleAiMessage(index)
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "单条重新生成已取消")
            } catch (e: Exception) {
                AppLogger.e(TAG, "单条重新生成失败", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_regenerate_single_failed, e.message ?: ""),
                )
            }
        }
    }

    fun switchMessageVariant(index: Int, targetVariantIndex: Int) {
        viewModelScope.launch {
            try {
                val currentHistory = chatHistoryDelegate.chatHistory.value
                val targetMessage = currentHistory.getOrNull(index)
                if (targetMessage == null) {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_invalid_message_index))
                    return@launch
                }
                if (targetMessage.sender != "ai") {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_only_ai_message_allowed))
                    return@launch
                }
                chatHistoryDelegate.selectMessageVariant(targetMessage.timestamp, targetVariantIndex)
                messageCoordinationDelegate.refreshStableContextWindow(chatId = currentChatId.value)
            } catch (e: Exception) {
                AppLogger.e(TAG, "切换回答版本失败", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_switch_variant_failed, e.message ?: ""),
                )
            }
        }
    }

    fun deleteCurrentMessageVariant(index: Int) {
        viewModelScope.launch {
            try {
                val currentHistory = chatHistoryDelegate.chatHistory.value
                val targetMessage = currentHistory.getOrNull(index)
                if (targetMessage == null) {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_invalid_message_index))
                    return@launch
                }
                if (targetMessage.sender != "ai") {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_only_ai_message_allowed))
                    return@launch
                }
                if (targetMessage.variantCount <= 1) {
                    uiStateDelegate.showErrorMessage(
                        context.getString(R.string.chat_delete_single_variant_unavailable),
                    )
                    return@launch
                }
                chatHistoryDelegate.deleteMessageVariant(
                    timestamp = targetMessage.timestamp,
                    variantIndex = targetMessage.selectedVariantIndex,
                )
                messageCoordinationDelegate.refreshStableContextWindow(chatId = currentChatId.value)
            } catch (e: Exception) {
                AppLogger.e(TAG, "删除当前消息候选失败", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_delete_single_variant_failed, e.message ?: ""),
                )
            }
        }
    }

    /**
     * 回档到指定消息并重新发送
     * @param index 要回档到的消息索引
     * @param editedContent 编辑后的消息内容（如果有）
     */
    fun rewindAndResendMessage(index: Int, editedContent: String) {
        viewModelScope.launch {
            try {
                // 获取当前聊天历史
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                // 确保索引有效
                if (index < 0 || index >= currentHistory.size) {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_invalid_message_index))
                    return@launch
                }

                // 获取目标消息
                val targetMessage = currentHistory[index]

                // 检查目标消息是否是用户消息
                if (targetMessage.sender != "user") {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_only_user_message_allowed))
                    return@launch
                }

                // **核心修复**: 确定回滚的时间戳。
                // 我们需要恢复到目标消息 *之前* 的状态,
                // 所以我们使用前一条消息的时间戳。
                // 如果目标是第一条消息，则回滚到初始状态 (时间戳 0)。
                val rewindTimestamp = if (index > 0) {
                    currentHistory[index - 1].timestamp
                } else {
                    0L
                }

                // 获取当前工作区路径
                val chatId = currentChatId.value
                val currentChat = chatHistories.value.find { it.id == chatId }
                val workspacePath = currentChat?.workspace
                val workspaceEnv = currentChat?.workspaceEnv

                AppLogger.d(TAG, "[Rewind] Target message timestamp: ${targetMessage.timestamp}")
                if (index > 0) {
                    AppLogger.d(TAG, "[Rewind] Previous message timestamp: ${currentHistory[index - 1].timestamp}")
                } else {
                    AppLogger.d(TAG, "[Rewind] No previous message, target is the first message.")
                }
                AppLogger.d(TAG, "[Rewind] Timestamp passed to syncState: $rewindTimestamp")

                // 如果绑定了工作区，则执行回滚
                if (!workspacePath.isNullOrBlank()) {
                    AppLogger.d(TAG, "Rewinding workspace to timestamp: $rewindTimestamp")
                    withContext(Dispatchers.IO) {
                        WorkspaceBackupManager.getInstance(context)
                            .syncState(workspacePath, rewindTimestamp, workspaceEnv, chatId)
                    }
                    AppLogger.d(TAG, "Workspace rewind complete.")
                }

                // 截取到指定消息的历史记录（不包含该消息本身）
                val rewindHistory = currentHistory.subList(0, index)
                
                // 获取要删除的第一条消息的时间戳
                val timestampOfFirstDeletedMessage = currentHistory[index].timestamp

                // **核心修复**：调用新的委托方法，原子性地更新数据库和内存
                chatHistoryDelegate.truncateChatHistory(
                        rewindHistory,
                        timestampOfFirstDeletedMessage
                )

                // 使用修改后的消息内容来发送
                messageProcessingDelegate.updateUserMessage(editedContent)
                sendUserMessage()
            } catch (e: Exception) {
                AppLogger.e(TAG, "回档并重新发送消息失败", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_rewind_failed, e.message ?: ""))
            }
        }
    }

    suspend fun previewWorkspaceChangesForMessage(index: Int): List<WorkspaceBackupManager.WorkspaceFileChange> {
        return withContext(Dispatchers.IO) {
            try {
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                if (index < 0 || index >= currentHistory.size) {
                    emptyList()
                } else {
                    val rewindTimestamp = if (index > 0) {
                        currentHistory[index - 1].timestamp
                    } else {
                        0L
                    }

                    val chatId = currentChatId.value
                    val currentChat = chatHistories.value.find { it.id == chatId }
                    val workspacePath = currentChat?.workspace
                    val workspaceEnv = currentChat?.workspaceEnv

                    if (workspacePath.isNullOrBlank()) {
                        emptyList()
                    } else {
                        WorkspaceBackupManager.getInstance(context)
                            .previewChangesForRewind(workspacePath, workspaceEnv, rewindTimestamp, chatId)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "预览工作区变更失败", e)
                emptyList()
            }
        }
    }

    fun rollbackToMessage(index: Int) {
        viewModelScope.launch {
            try {
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                if (index < 0 || index >= currentHistory.size) {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_invalid_message_index))
                    return@launch
                }

                val targetMessage = currentHistory[index]

                // 目前UI只允许对用户消息执行回滚，这里再做一次保护
                if (targetMessage.sender != "user") {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_only_user_message_allowed))
                    return@launch
                }

                val rewindTimestamp = if (index > 0) {
                    currentHistory[index - 1].timestamp
                } else {
                    0L
                }

                val chatId = currentChatId.value
                val currentChat = chatHistories.value.find { it.id == chatId }
                val workspacePath = currentChat?.workspace
                val workspaceEnv = currentChat?.workspaceEnv

                if (!workspacePath.isNullOrBlank()) {
                    AppLogger.d(TAG, "[Rollback] Rewinding workspace to timestamp: $rewindTimestamp")
                    withContext(Dispatchers.IO) {
                        WorkspaceBackupManager.getInstance(context)
                            .syncState(workspacePath, rewindTimestamp, workspaceEnv, chatId)
                    }
                    AppLogger.d(TAG, "[Rollback] Workspace rewind complete.")
                }

                // 删除目标消息及其之后的所有消息
                val newHistory = currentHistory.subList(0, index)

                val timestampOfFirstDeletedMessage = currentHistory[index].timestamp
                chatHistoryDelegate.truncateChatHistory(
                    newHistory,
                    timestampOfFirstDeletedMessage
                )

                val plainText = AvatarEmotionManager.stripXmlLikeTags(targetMessage.content)
                updateUserMessage(TextFieldValue(plainText))

                uiStateDelegate.showToast(context.getString(R.string.chat_rolled_back_message_in_input))
            } catch (e: Exception) {
                AppLogger.e(TAG, "回滚到指定消息失败", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_rollback_failed, e.message ?: ""))
            }
        }
    }

    // 消息处理相关方法
    fun updateUserMessage(value: TextFieldValue) {
        messageProcessingDelegate.updateUserMessage(value)
        val message = value.text
        // 当用户输入@且工作-区打开时，显示文件选择器
        // 新逻辑：提取@后的内容作为搜索词，并根据条件决定是否显示选择器
        val lastAt = message.lastIndexOf('@')
        if (isWorkspaceOpen.value && lastAt != -1) {
            val substringAfterAt = message.substring(lastAt + 1)
            if (substringAfterAt.any(Char::isWhitespace)) {
                // 如果@后面出现空白字符，则认为提及结束，隐藏选择器并清空搜索词
                _showWorkspaceFileSelector.value = false
                _workspaceFileSearchQuery.value = ""
            } else {
                val normalizedQuery = substringAfterAt.trim()
                if (normalizedQuery.length >= MIN_WORKSPACE_FILE_QUERY_LENGTH) {
                    _showWorkspaceFileSelector.value = true
                    _workspaceFileSearchQuery.value = normalizedQuery
                } else {
                    _showWorkspaceFileSelector.value = false
                    _workspaceFileSearchQuery.value = ""
                }
            }
        } else {
            // 如果没有@或者工作区未打开，则隐藏并清空
            _showWorkspaceFileSelector.value = false
            _workspaceFileSearchQuery.value = ""
        }
    }

    fun insertRoleMention(roleName: String) {
        val trimmedRoleName = roleName.trim()
        if (trimmedRoleName.isEmpty()) return

        val mentionText = "@$trimmedRoleName"
        val current = userMessage.value
        val text = current.text
        val selectionStart = current.selection.start.coerceIn(0, text.length)
        val selectionEnd = current.selection.end.coerceIn(0, text.length)

        val before = text.substring(0, selectionStart)
        val after = text.substring(selectionEnd)

        val needLeadingSpace = before.isNotEmpty() && !before.last().isWhitespace()
        val insertion = buildString {
            if (needLeadingSpace) append(' ')
            append(mentionText)
            if (after.isEmpty() || !after.first().isWhitespace()) {
                append(' ')
            }
        }

        val newText = before + insertion + after
        val newCursor = (before.length + insertion.length).coerceAtMost(newText.length)
        updateUserMessage(TextFieldValue(newText, selection = TextRange(newCursor)))
    }

    fun sendUserMessage(promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT) {
        messageCoordinationDelegate.sendUserMessage(promptFunctionType)
    }

    fun sendTextMessage(text: String, promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT) {
        messageCoordinationDelegate.sendUserMessage(
            promptFunctionType = promptFunctionType,
            messageTextOverride = text
        )
    }

    fun insertAiMessage(text: String, roleName: String = "") {
        val content = text.trim()
        if (content.isBlank()) return
        viewModelScope.launch {
            chatHistoryDelegate.addMessageToChat(
                ChatMessage(
                    sender = "ai",
                    content = content,
                    roleName = roleName,
                    provider = "",
                    modelName = ""
                )
            )
        }
    }

    fun cancelCurrentMessage() {
        // 先取消总结（如果正在进行）
        if (::messageCoordinationDelegate.isInitialized) {
            messageCoordinationDelegate.cancelSummary()
        }
        val chatId = chatHistoryDelegate.currentChatId.value
        if (chatId != null) {
            messageProcessingDelegate.cancelMessage(chatId)
        }
    }

    // UI状态相关方法
    fun showErrorMessage(message: String) = uiStateDelegate.showErrorMessage(message)
    fun clearError() = uiStateDelegate.clearError()
    fun dismissErrorDialog() {
        uiStateDelegate.clearError()
        val chatId = chatHistoryDelegate.currentChatId.value
        if (chatId != null) {
            messageProcessingDelegate.setInputProcessingStateForChat(chatId, InputProcessingState.Idle)
            EnhancedAIService.getChatInstance(context, chatId)?.setInputProcessingState(InputProcessingState.Idle)
        }
    }
    fun popupMessage(message: String) = uiStateDelegate.showPopupMessage(message)
    fun clearPopupMessage() = uiStateDelegate.clearPopupMessage()
    fun showToast(message: String) = uiStateDelegate.showToast(message)
    fun clearToastEvent() = uiStateDelegate.clearToastEvent()

    // 悬浮窗相关方法
    fun onFloatingButtonClick(
        mode: FloatingMode,
        permissionLauncher: ActivityResultLauncher<String>,
        colorScheme: ColorScheme,
        typography: Typography,
        moveTaskToBackOnReady: Boolean = false
    ) {
        viewModelScope.launch {
            // 如果悬浮窗已经开启，则关闭它
            if (isFloatingMode.value) {
                toggleFloatingMode()
                return@launch
            }

            when(mode) {
                FloatingMode.WINDOW -> launchFloatingWindowWithPermissionCheck(permissionLauncher) {
                    launchFloatingModeIn(
                        mode = FloatingMode.WINDOW,
                        colorScheme = colorScheme,
                        typography = typography,
                        moveTaskToBackOnReady = moveTaskToBackOnReady
                    )
                }
                FloatingMode.FULLSCREEN -> launchFullscreenVoiceModeWithPermissionCheck(permissionLauncher, colorScheme, typography)
                FloatingMode.SCREEN_OCR -> launchFloatingWindowWithPermissionCheck(permissionLauncher) {
                    launchFloatingModeIn(
                        mode = FloatingMode.WINDOW,
                        colorScheme = colorScheme,
                        typography = typography,
                        moveTaskToBackOnReady = moveTaskToBackOnReady
                    )
                }
                FloatingMode.BALL,
                FloatingMode.VOICE_BALL,
                FloatingMode.RESULT_DISPLAY -> {
                    // 这些模式暂时不处理，或者可以添加默认行为
                    AppLogger.d(TAG, "未实现的悬浮窗模式: $mode")
                }
            }
        }
    }


    fun toggleFloatingMode(colorScheme: ColorScheme? = null, typography: Typography? = null) {
        floatingWindowDelegate.toggleFloatingMode(colorScheme, typography)
    }

    // 权限相关方法
    fun toggleMasterPermission() {
        viewModelScope.launch {
            val newLevel =
                    if (masterPermissionLevel.value == PermissionLevel.ASK) {
                        PermissionLevel.ALLOW
                    } else {
                        PermissionLevel.ASK
                    }
            toolPermissionSystem.saveMasterSwitch(newLevel)

            // 移除Toast提示
        }
    }

    // 附件相关方法
    /** Handles a file or image attachment selected by the user */
    fun handleAttachment(filePath: String) {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) return@launch
                
                // 显示附件处理进度
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_processing_attachment))
                )

                attachmentDelegate.handleAttachment(filePath)

                // 清除附件处理进度显示
                messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
            } catch (e: Exception) {
                AppLogger.e(TAG, "处理附件失败", e)
                // 修改: 使用错误弹窗而不是 Toast 显示附件处理错误
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_attachment_processing_failed, e.message ?: ""))
                // 发生错误时也需要清除进度显示
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                }
            }
        }
    }

    /** Removes an attachment by its file path */
    fun removeAttachment(filePath: String) {
        attachmentDelegate.removeAttachment(filePath)
    }

    /** Inserts a reference to an attachment at the current cursor position in the user's message */
    fun insertAttachmentReference(attachment: AttachmentInfo) {
        val currentMessage = userMessage.value
        val attachmentRef = attachmentDelegate.createAttachmentReference(attachment)

        // Insert at the end of the current message
        val currentText = currentMessage.text
        val newText = "$currentText $attachmentRef "
        updateUserMessage(TextFieldValue(newText, selection = TextRange(newText.length)))

        // Show a toast to confirm insertion
        uiStateDelegate.showToast(context.getString(R.string.chat_inserted_attachment_ref, attachment.fileName))
    }

    /** 隐藏工作区文件选择器 */
    fun hideWorkspaceFileSelector() {
        _showWorkspaceFileSelector.value = false
    }

    /** Captures the current screen content and attaches it to the message */
    fun captureScreenContent() {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) return@launch
                
                // 显示屏幕内容获取进度
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_fetching_screen_content))
                )
                uiStateDelegate.showToast(context.getString(R.string.chat_fetching_screen_content))

                // 直接委托给attachmentDelegate执行
                attachmentDelegate.captureScreenContent()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
            } catch (e: Exception) {
                AppLogger.e(TAG, "截取屏幕内容失败", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_capture_screen_failed, e.message ?: ""))
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                }
            }
        }
    }

    /** 获取设备当前通知数据并添加为附件 */
    fun captureNotifications() {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) return@launch
                
                // 显示通知获取进度
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_fetching_notifications))
                )
                uiStateDelegate.showToast(context.getString(R.string.chat_fetching_notifications))

                // 直接委托给attachmentDelegate执行
                attachmentDelegate.captureNotifications()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取通知数据失败", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_fetch_notifications_failed, e.message ?: ""))
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                }
            }
        }
    }

    /** 获取设备当前位置数据并添加为附件 */
    fun captureLocation() {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) return@launch
                
                // 显示位置获取进度
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_fetching_location))
                )
                uiStateDelegate.showToast(context.getString(R.string.chat_fetching_location))

                // 直接委托给attachmentDelegate执行
                attachmentDelegate.captureLocation()
                
                // 隐藏进度状态
                messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error capturing location", e)
                uiStateDelegate.showToast(context.getString(R.string.chat_fetch_location_failed, e.message ?: ""))
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                }
            }
        }
    }

    /**
     * 捕获记忆文件夹作为附件
     */
    fun captureMemoryFolders(folderPaths: List<String>) {
        viewModelScope.launch {
            try {
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) return@launch
                // 显示记忆文件夹附着进度
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_attaching_memory_folders))
                )
                uiStateDelegate.showToast(context.getString(R.string.chat_attaching_memory_folders))

                // 直接委托给attachmentDelegate执行
                attachmentDelegate.captureMemoryFolders(folderPaths)

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
            } catch (e: Exception) {
                AppLogger.e(TAG, "附着记忆文件夹失败", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_attach_memory_folders_failed, e.message ?: ""))
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                }
            }
        }
    }

    /** Handles a photo taken by the camera */
    fun handleTakenPhoto(uri: Uri) {
        viewModelScope.launch {
            attachmentDelegate.handleTakenPhoto(uri)
        }
    }

    private suspend fun awaitCurrentChat(chatId: String, maxWaitCount: Int = 40): Boolean {
        var waitCount = 0
        while (currentChatId.value != chatId && waitCount < maxWaitCount) {
            delay(50)
            waitCount++
        }
        return currentChatId.value == chatId
    }

    private suspend fun resolveTargetChatIdForSharedContent(targetChatId: String?): String? {
        if (!targetChatId.isNullOrBlank()) {
            if (currentChatId.value != targetChatId) {
                switchChat(targetChatId)
                if (!awaitCurrentChat(targetChatId)) {
                    AppLogger.e(TAG, "Failed to switch to target chat: $targetChatId")
                    return null
                }
            }
            return targetChatId
        }

        val existingChatIds = chatHistories.value.mapTo(mutableSetOf()) { it.id }
        val previousCurrentChatId = currentChatId.value
        createNewChat()

        var waitCount = 0
        var createdChatId: String? = null
        while (createdChatId.isNullOrBlank() && waitCount < 40) {
            createdChatId = chatHistories.value.firstOrNull { it.id !in existingChatIds }?.id
                ?: currentChatId.value
                    ?.takeIf { it != previousCurrentChatId && it !in existingChatIds }
            if (createdChatId.isNullOrBlank()) {
                delay(50)
                waitCount++
            }
        }

        if (createdChatId.isNullOrBlank()) {
            AppLogger.e(TAG, "Failed to detect newly created chat")
            return null
        }

        if (currentChatId.value != createdChatId) {
            switchChat(createdChatId)
            if (!awaitCurrentChat(createdChatId)) {
                AppLogger.e(TAG, "Failed to activate newly created chat: $createdChatId")
                return null
            }
        }

        return createdChatId
    }

    /**
     * Handles shared files from external apps
     * Attaches files to the selected chat or a newly created chat, then pre-fills the message
     */
    fun handleSharedFiles(uris: List<Uri>, targetChatId: String? = null) {
        AppLogger.d(TAG, "handleSharedFiles called with ${uris.size} file(s)")
        uris.forEachIndexed { index, uri ->
            AppLogger.d(TAG, "  [$index] URI: $uri")
        }
        
        viewModelScope.launch {
            try {
                val chatId = resolveTargetChatIdForSharedContent(targetChatId)
                if (chatId == null) {
                    AppLogger.e(TAG, "Failed to prepare target chat for shared files")
                    uiStateDelegate.showErrorMessage(
                        context.getString(R.string.chat_prepare_shared_target_failed)
                    )
                    return@launch
                }

                AppLogger.d(TAG, "Target chat prepared successfully: $chatId")

                // Show processing state
                messageProcessingDelegate.setInputProcessingStateForChat(
                    chatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_processing_shared_files))
                )

                // Attach each file
                AppLogger.d(TAG, "Starting to attach ${uris.size} file(s)...")
                uris.forEachIndexed { index, uri ->
                    val filePath = uri.toString()
                    AppLogger.d(TAG, "Attaching file [$index]: $filePath")
                    attachmentDelegate.handleAttachment(filePath)
                    delay(100) // Small delay between files
                }
                AppLogger.d(TAG, "All files attached successfully")
                
                // Set the pre-filled message
                AppLogger.d(TAG, "Setting pre-filled message")
                messageProcessingDelegate.updateUserMessage(TextFieldValue(context.getString(R.string.chat_prefill_check_file)))

                // Clear processing state
                messageProcessingDelegate.setInputProcessingStateForChat(chatId, InputProcessingState.Idle)
                
                AppLogger.d(TAG, "Successfully processed shared files")
                uiStateDelegate.showToast(context.getString(R.string.chat_added_files_count, uris.size))
            } catch (e: Exception) {
                AppLogger.e(TAG, "处理分享文件失败", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_process_shared_files_failed, e.message ?: ""))
                val chatId = currentChatId.value
                if (chatId != null) {
                    messageProcessingDelegate.setInputProcessingStateForChat(chatId, InputProcessingState.Idle)
                }
            }
        }
    }

    /** 确保AI服务可用，如果当前实例为空则创建一个默认实例 */
    fun ensureAiServiceAvailable() {
        if (enhancedAiService == null) {
            viewModelScope.launch {
                try {
                    // 使用默认配置或保存的配置创建一个新实例
                    AppLogger.d(TAG, "创建默认EnhancedAIService实例")
                    apiConfigDelegate.useDefaultConfig()

                    // 等待服务实例创建完成
                    var retryCount = 0
                    while (enhancedAiService == null && retryCount < 3) {
                        kotlinx.coroutines.delay(500)
                        retryCount++
                    }

                    if (enhancedAiService == null) {
                        AppLogger.e(TAG, "无法创建EnhancedAIService实例")
                        // 修改: 使用错误弹窗而不是 Toast 显示服务初始化错误
                        uiStateDelegate.showErrorMessage(context.getString(R.string.chat_ai_service_init_network_hint))
                    } else {
                        AppLogger.d(TAG, "成功创建EnhancedAIService实例")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "创建EnhancedAIService实例时出错", e)
                    // 修改: 使用错误弹窗而不是 Toast 显示服务初始化错误
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_ai_service_init_failed, e.message ?: ""))
                }
            }
        }
    }

    /** 重置附件面板状态 - 在发送消息后关闭附件面板 */
    fun resetAttachmentPanelState() {
        _attachmentPanelState.value = false
    }

    /** 更新附件面板状态 */
    fun updateAttachmentPanelState(isExpanded: Boolean) {
        _attachmentPanelState.value = isExpanded
    }

    // WebView控制方法
    fun toggleWebView() {
        if (_showWebView.value) {
            workspaceOpenJob?.cancel()
            _isWorkspacePreparing.value = false
            _showWebView.value = false
            return
        }

        if (_isWorkspacePreparing.value) {
            AppLogger.d(TAG, "工作区正在准备中，忽略重复打开请求")
            return
        }

        workspaceOpenJob?.cancel()
        workspaceOpenJob = viewModelScope.launch {
            _isWorkspacePreparing.value = true
            try {
                if (_showAiComputer.value) {
                    _showAiComputer.value = false
                    AppLogger.d(TAG, "AI电脑已关闭（由于打开工作区）")
                }

                val chatId = awaitWorkspaceChatId()
                if (chatId != null) {
                    prepareWorkspaceServerForCurrentChat(chatId)
                } else {
                    AppLogger.w(TAG, "打开工作区时未获取到聊天ID，将直接显示工作区页面")
                }

                _showWebView.value = true
                _webViewRefreshCounter.value += 1
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "工作区打开流程已取消")
                throw e
            } catch (e: IOException) {
                AppLogger.e(TAG, "Failed to start workspace web server", e)
                showErrorMessage(context.getString(R.string.chat_start_workspace_server_failed))
            } catch (e: Exception) {
                AppLogger.e(TAG, "打开工作区失败", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_update_workspace_server_failed, e.message ?: "")
                )
            } finally {
                _isWorkspacePreparing.value = false
            }
        }
    }

    private suspend fun awaitWorkspaceChatId(): String? {
        currentChatId.value?.let { return it }

        createNewChat()

        var waitCount = 0
        while (currentChatId.value == null && waitCount < 10) {
            delay(100)
            waitCount++
        }

        return currentChatId.value
    }

    private fun isWorkspaceServerEnabled(workspacePath: String, workspaceEnv: String?): Boolean {
        if (workspaceEnv?.startsWith("repo:", ignoreCase = true) == true) {
            return false
        }
        return WorkspaceConfigReader.readConfig(workspacePath).server.enabled
    }

    private suspend fun prepareWorkspaceServer(workspacePath: String, workspaceEnv: String?): Boolean {
        return withContext(Dispatchers.IO) {
            val webServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
            val serverEnabled = isWorkspaceServerEnabled(workspacePath, workspaceEnv)

            if (!serverEnabled) {
                if (webServer.isRunning()) {
                    webServer.stop()
                }
                false
            } else {
                if (!webServer.isRunning()) {
                    webServer.start()
                }
                webServer.updateChatWorkspace(workspacePath, workspaceEnv)
                true
            }
        }
    }

    private suspend fun prepareWorkspaceServerForCurrentChat(chatId: String) {
        val chat = chatHistories.value.find { it.id == chatId }
        val workspacePath = chat?.workspace
        val workspaceEnv = chat?.workspaceEnv

        if (workspacePath == null) {
            AppLogger.w(TAG, "Chat $chatId has no workspace bound. Web server not updated.")
            return
        }

        if (prepareWorkspaceServer(workspacePath, workspaceEnv)) {
            AppLogger.d(TAG, "Web服务器工作空间已更新为: $workspacePath env=$workspaceEnv for chat $chatId")
        } else {
            AppLogger.d(TAG, "工作区服务器已按配置禁用: $workspacePath env=$workspaceEnv for chat $chatId")
        }
    }


    // 更新当前聊天ID的Web服务器工作空间
    fun updateWebServerForCurrentChat(chatId: String) {
        viewModelScope.launch {
            try {
                prepareWorkspaceServerForCurrentChat(chatId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新Web服务器工作空间失败", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_update_workspace_server_failed, e.message ?: "")
                )
            }
        }
    }

    // 强制WebView刷新
    fun refreshWebView() {
        _webViewRefreshCounter.value += 1
    }

    // 用于启动文件选择器并处理结果
    fun startFileChooserForResult(intent: Intent, callback: (Int, Intent?) -> Unit) {
        fileChooserCallback = callback
        // 通过UIStateDelegate广播一个请求，让Activity处理文件选择
        uiStateDelegate.requestFileChooser(intent)
    }

    // 供Activity调用，处理文件选择结果
    fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        fileChooserCallback?.invoke(resultCode, data)
        fileChooserCallback = null
    }

    /** 设置权限系统的颜色方案 */
    fun setPermissionSystemColorScheme(colorScheme: ColorScheme?) {
        toolPermissionSystem.setColorScheme(colorScheme)
    }

    fun launchFloatingModeIn(
            mode: FloatingMode,
            colorScheme: ColorScheme? = null,
            typography: Typography? = null,
            moveTaskToBackOnReady: Boolean = false
    ) {
        floatingWindowDelegate.launchInMode(mode, colorScheme, typography, moveTaskToBackOnReady)
    }
    
    /**
     * 从Widget启动悬浮窗到指定模式（使用默认主题）
     */
    fun launchFloatingWindowInMode(mode: FloatingMode) {
        launchFloatingModeIn(mode, null, null)
    }

    fun launchWindowFloatingModeAfterMicPermissionGranted(
            colorScheme: ColorScheme? = null,
            typography: Typography? = null,
            moveTaskToBackOnReady: Boolean = false
    ) {
        if (!Settings.canDrawOverlays(context)) {
            openOverlayPermissionSettings()
            return
        }

        launchFloatingModeIn(
            mode = FloatingMode.WINDOW,
            colorScheme = colorScheme,
            typography = typography,
            moveTaskToBackOnReady = moveTaskToBackOnReady
        )
    }

    fun launchFloatingWindowWithPermissionCheck(
            launcher: ActivityResultLauncher<String>,
            onPermissionGranted: () -> Unit
    ) {
        val hasMicPermission =
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val canDrawOverlays = Settings.canDrawOverlays(context)

        if (!hasMicPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (!canDrawOverlays) {
            openOverlayPermissionSettings()
        } else {
            onPermissionGranted()
        }
    }

    fun launchFullscreenVoiceModeWithPermissionCheck(
            launcher: ActivityResultLauncher<String>,
            colorScheme: ColorScheme? = null,
            typography: Typography? = null
    ) {
        val hasMicPermission =
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val canDrawOverlays = Settings.canDrawOverlays(context)

        if (!hasMicPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (!canDrawOverlays) {
            openOverlayPermissionSettings()
        } else {
            // Directly launch fullscreen voice mode
            launchFloatingModeIn(FloatingMode.FULLSCREEN, colorScheme, typography)
        }
    }

    private fun openOverlayPermissionSettings() {
        val intent =
                Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        showToast(context.getString(R.string.chat_need_overlay_permission_start_voice_assistant))
    }

    override fun onCleared() {
        super.onCleared()
        inputProcessingStateListenerJob?.cancel()
        voiceStateCollectionJob?.cancel()
        // 清理悬浮窗资源
        floatingWindowDelegate.cleanup()

        if (::mainChatCore.isInitialized) {
            mainChatCore.setUiBridge(EmptyChatServiceUiBridge)
            mainChatCore.setSpeakMessageHandler { _, _ -> }
        }
        
        // 清理语音服务资源
        voiceService?.shutdown()

        // 不再在这里停止Web服务器，因为使用的是单例模式
        // 服务器应在应用退出时由Application类或专门的服务管理类关闭
        // 这样可以在界面切换时保持服务器的连续运行
    }

    /** 更新指定聊天的标题 */
    fun updateChatTitle(chatId: String, newTitle: String) {
        chatHistoryDelegate.updateChatTitle(chatId, newTitle)
    }

    /** 更新指定聊天绑定的角色卡 */
    fun updateChatCharacterCardBinding(chatId: String, characterCardName: String?) {
        chatHistoryDelegate.updateChatCharacterCard(chatId, characterCardName)
    }

    /** 更新指定聊天绑定的群组角色卡 */
    fun updateChatCharacterGroupBinding(chatId: String, characterGroupId: String?) {
        chatHistoryDelegate.updateChatCharacterGroup(chatId, characterGroupId)
    }

    /** 同时更新指定聊天绑定的角色卡与群组 */
    fun updateChatCharacterBinding(
        chatId: String,
        characterCardName: String?,
        characterGroupId: String?
    ) {
        chatHistoryDelegate.updateChatCharacterBinding(chatId, characterCardName, characterGroupId)
    }

    /** 更新指定聊天的标题 */
    fun bindChatToWorkspace(chatId: String, workspace: String, workspaceEnv: String? = null) {
        // 1. Persist the change
        chatHistoryDelegate.bindChatToWorkspace(chatId, workspace, workspaceEnv)

        // 2. Update the web server with the new path and refresh
        viewModelScope.launch {
            try {
                if (prepareWorkspaceServer(workspace, workspaceEnv)) {
                    AppLogger.d(TAG, "Web server workspace updated to: $workspace env=$workspaceEnv for chat $chatId")
                } else {
                    AppLogger.d(TAG, "Workspace server disabled by config for: $workspace env=$workspaceEnv")
                }

                // 3. Trigger a refresh of the WebView
                refreshWebView()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update web server workspace after binding", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_update_workspace_server_failed, e.message ?: "")
                )
            }
        }
    }

    /** 解绑聊天的工作区 */
    fun unbindChatFromWorkspace(chatId: String) {
        // 1. Persist the change
        chatHistoryDelegate.unbindChatFromWorkspace(chatId)

        // 2. Stop the web server or clear workspace
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val webServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
                    if (webServer.isRunning()) {
                        webServer.stop()
                    }
                }
                AppLogger.d(TAG, "Web server stopped after unbinding workspace for chat $chatId")

                // 3. Trigger a refresh of the WebView
                refreshWebView()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to stop web server after unbinding", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_stop_workspace_server_failed, e.message ?: "")
                )
            }
        }
    }

    suspend fun renameWorkspace(
        chatId: String,
        newWorkspaceName: String
    ): WorkspaceRenameResult {
        val result = chatHistoryDelegate.renameWorkspaceAndChat(chatId, newWorkspaceName)
        runCatching {
            if (prepareWorkspaceServer(result.workspacePath, result.workspaceEnv)) {
                AppLogger.d(
                    TAG,
                    "Web server workspace renamed to: ${result.workspacePath} env=${result.workspaceEnv} for chat $chatId"
                )
            } else {
                AppLogger.d(
                    TAG,
                    "Workspace server disabled by config after rename: ${result.workspacePath} env=${result.workspaceEnv}"
                )
            }
            refreshWebView()
        }.onFailure { e ->
            AppLogger.e(TAG, "Failed to refresh workspace after rename", e)
            uiStateDelegate.showErrorMessage(
                context.getString(R.string.chat_update_workspace_server_failed, e.message ?: "")
            )
        }
        return result
    }

    /** 在工作区中执行命令（来自 config.json 按钮） */
    @RequiresApi(Build.VERSION_CODES.O)
    fun executeCommandInWorkspace(command: CommandConfig, workspacePath: String) {
        val toolName = command.tool?.trim().orEmpty()
        if (toolName.isNotEmpty()) {
            executeWorkspaceTool(command, workspacePath, toolName)
            return
        }

        val commandText = command.command?.trim().orEmpty()
        if (commandText.isBlank()) {
            uiStateDelegate.showErrorMessage(
                context.getString(R.string.chat_execute_command_failed, "No command/tool configured")
            )
            return
        }

        if (terminal == null) {
            uiStateDelegate.showErrorMessage(context.getString(R.string.chat_terminal_requires_android_8))
            return
        }

        if (command.usesDedicatedSession) {
            executeBackgroundWorkspaceCommand(command, workspacePath, commandText)
            return
        }

        if (workspaceCommandExecutionJob?.isActive == true) {
            uiStateDelegate.showToast(context.getString(R.string.workspace_command_already_running))
            return
        }

        workspaceCommandExecutionJob = viewModelScope.launch {
            var sessionId: String? = null
            try {
                AppLogger.d(TAG, "Executing workspace command: $commandText in $workspacePath")
                
                val workspaceDir = File(workspacePath)

                // 使用工作区的共享会话
                var sharedSessionId = workspaceTerminalSessions[workspacePath]

                // 如果会话不存在或已关闭，创建新会话
                if (sharedSessionId == null || terminal.terminalState.value.sessions.none { it.id == sharedSessionId }) {
                    val workspaceName = workspaceDir.name.take(4) // 只取前4位

                    sharedSessionId = terminal.createSession("Workspace: $workspaceName")

                    // 保存会话 ID
                    workspaceTerminalSessions[workspacePath] = sharedSessionId

                    AppLogger.d(
                        TAG,
                        "Created new workspace terminal session $sharedSessionId for $workspacePath"
                    )
                }

                sessionId = sharedSessionId

                val activeSessionId = sessionId ?: return@launch

                terminal.executeCommand(activeSessionId, "cd \"${workspaceDir.absolutePath}\"")

                _workspaceCommandExecutionState.value =
                    WorkspaceCommandExecutionState(
                        workspacePath = workspacePath,
                        commandLabel = command.label,
                        commandText = commandText,
                        sessionId = activeSessionId,
                        usesDedicatedSession = command.usesDedicatedSession
                    )

                terminal.executeCommandFlow(activeSessionId, commandText).collect { event ->
                    val currentState = _workspaceCommandExecutionState.value
                    if (currentState?.sessionId != activeSessionId) {
                        return@collect
                    }

                    if (event.isCompleted) {
                        val finalEntries = event.outputChunk.toWorkspaceCommandOutputEntries()
                        _workspaceCommandExecutionState.value =
                            currentState.copy(
                                outputEntries =
                                    finalEntries.takeIf { it.isNotEmpty() }
                                        ?: currentState.outputEntries,
                                isRunning = false,
                                isCancelling = false
                            )
                    } else {
                        val appendedEntries = event.outputChunk.toWorkspaceCommandOutputEntries()
                        if (appendedEntries.isEmpty()) {
                            return@collect
                        }
                        _workspaceCommandExecutionState.value =
                            currentState.copy(
                                outputEntries = currentState.outputEntries + appendedEntries
                            )
                    }
                }

                val currentState = _workspaceCommandExecutionState.value
                if (currentState?.sessionId == activeSessionId && !currentState.isVisible) {
                    _workspaceCommandExecutionState.value = null
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to execute workspace command", e)
                if (_workspaceCommandExecutionState.value?.sessionId == sessionId) {
                    _workspaceCommandExecutionState.value = null
                }
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_execute_command_failed, e.message ?: "")
                )
            } finally {
                workspaceCommandExecutionJob = null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun executeBackgroundWorkspaceCommand(
        command: CommandConfig,
        workspacePath: String,
        commandText: String
    ) {
        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "Executing background workspace command: $commandText in $workspacePath")

                val terminalInstance = terminal ?: run {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_terminal_requires_android_8))
                    return@launch
                }
                val workspaceDir = File(workspacePath)
                val sessionTitle = command.sessionTitle ?: command.label
                val dedicatedSessionId = terminalInstance.createSession(sessionTitle)

                terminalInstance.executeCommand(dedicatedSessionId, "cd \"${workspaceDir.absolutePath}\"")
                terminalInstance.sendInput(dedicatedSessionId, commandText + "\r")
                openAiComputerForTerminalSession()

                AppLogger.d(
                    TAG,
                    "Background workspace command started in dedicated terminal session $dedicatedSessionId"
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to execute background workspace command", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_execute_command_failed, e.message ?: "")
                )
            }
        }
    }

    private fun openAiComputerForTerminalSession() {
        if (_showWebView.value) {
            _showWebView.value = false
            AppLogger.d(TAG, "工作区已关闭（由于打开后台命令终端）")
        }
        _showAiComputer.value = true
    }

    fun dismissWorkspaceCommandExecutionDialog(workspacePath: String) {
        val currentState = _workspaceCommandExecutionState.value ?: return
        if (currentState.workspacePath != workspacePath) {
            return
        }
        _workspaceCommandExecutionState.value =
            if (currentState.isRunning) {
                currentState.copy(isVisible = false)
            } else {
                null
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun cancelWorkspaceCommandExecution() {
        val currentState = _workspaceCommandExecutionState.value ?: return
        if (!currentState.isRunning || currentState.isCancelling || terminal == null) {
            return
        }

        _workspaceCommandExecutionState.value = currentState.copy(isCancelling = true)
        terminal.sendInterruptSignal(currentState.sessionId)
    }

    private fun executeWorkspaceTool(command: CommandConfig, workspacePath: String, toolName: String) {
        viewModelScope.launch {
            try {
                val workspaceDir = File(workspacePath)
                val toolParameters = command.toolParameters.map { (name, value) ->
                    ToolParameter(
                        name = name,
                        value = resolveWorkspaceToolParameterValue(name, value, workspaceDir)
                    )
                }
                val tool = AITool(
                    name = toolName,
                    parameters = toolParameters,
                    description = "Workspace action: ${command.label}"
                )

                AppLogger.d(TAG, "Executing workspace tool: $toolName with ${toolParameters.size} params")
                val result = withContext(Dispatchers.IO) { toolHandler.executeTool(tool) }

                if (!result.success) {
                    uiStateDelegate.showErrorMessage(
                        context.getString(
                            R.string.chat_execute_command_failed,
                            result.error ?: "Tool execution failed"
                        )
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to execute workspace tool", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_execute_command_failed, e.message ?: "")
                )
            }
        }
    }

    private fun resolveWorkspaceToolParameterValue(name: String, rawValue: String, workspaceDir: File): String {
        val workspacePath = workspaceDir.absolutePath
        val expanded = rawValue
            .replace("${'$'}WORKSPACE", workspacePath)
            .replace("${'$'}{WORKSPACE}", workspacePath)

        if (!isPathLikeToolParameter(name)) {
            return expanded
        }

        val trimmed = expanded.trim()
        if (trimmed.isEmpty() || trimmed.contains("://")) {
            return expanded
        }

        val file = File(trimmed)
        if (file.isAbsolute) {
            return trimmed
        }

        return File(workspaceDir, trimmed).absolutePath
    }

    private fun isPathLikeToolParameter(name: String): Boolean {
        val lowered = name.lowercase()
        return lowered.contains("path") || lowered.contains("file") || lowered.contains("dir")
    }

    /** 更新聊天顺序和分组 */
    fun updateChatOrderAndGroup(
        reorderedHistories: List<ChatHistory>,
        movedItem: ChatHistory,
        targetGroup: String?
    ) {
        chatHistoryDelegate.updateChatOrderAndGroup(reorderedHistories, movedItem, targetGroup)
    }

    /** 创建新分组（通过创建新聊天实现） */
    fun createGroup(groupName: String, characterCardName: String?, characterGroupId: String? = null) {
        chatHistoryDelegate.createGroup(groupName, characterCardName, characterGroupId)
    }

    /** 重命名分组 */
    fun updateGroupName(oldName: String, newName: String, characterCardName: String?) {
        chatHistoryDelegate.updateGroupName(oldName, newName, characterCardName)
    }

    /** 删除分组 */
    fun deleteGroup(groupName: String, deleteChats: Boolean, characterCardName: String?) {
        chatHistoryDelegate.deleteGroup(groupName, deleteChats, characterCardName)
    }

    fun onWorkspaceButtonClick() {
        toggleWebView()
    }

    fun onAiComputerButtonClick() {
        toggleAiComputer()
    }

    // AI电脑控制方法
    fun toggleAiComputer() {
        viewModelScope.launch {
            // 如果要显示AI电脑，先关闭工作区
            if (!_showAiComputer.value && _showWebView.value) {
                _showWebView.value = false
                AppLogger.d(TAG, "工作区已关闭（由于打开AI电脑）")
            }
            
            val newShowState = !_showAiComputer.value
            _showAiComputer.value = newShowState
            
            if (newShowState) {
                // 初始化AI电脑管理器
                try {
                    AppLogger.d(TAG, "AI电脑已启动")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "启动AI电脑失败", e)
                    _showAiComputer.value = false
                    uiStateDelegate.showErrorMessage(
                        context.getString(R.string.chat_start_ai_computer_failed, e.message ?: "")
                    )
                }
            } else {
                AppLogger.d(TAG, "AI电脑已关闭")
            }
        }
    }



    /** 初始化语音服务 */
    private fun initializeVoiceService() {
        // 监听TTS服务类型和配置的变化
        viewModelScope.launch {
            combine(
                speechServicesPreferences.ttsServiceTypeFlow,
                speechServicesPreferences.ttsHttpConfigFlow
            ) { type, config ->
                type to config
            }.collect { (type, _) ->
                try {
                    AppLogger.d(TAG, "TTS配置变化，重新初始化语音服务: type=$type")

                    val initialized = recreateVoiceService()
                    if (!initialized) {
                        AppLogger.w(TAG, "语音服务初始化失败")
                    } else {
                        AppLogger.i(TAG, "语音服务初始化成功")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "初始化语音服务时出错", e)
                }
            }
        }
    }

    private suspend fun recreateVoiceService(): Boolean {
        voiceService?.stop()
        voiceStateCollectionJob?.cancel()
        VoiceServiceFactory.resetInstance()
        val currentVoiceService = VoiceServiceFactory.getInstance(context)
        voiceService = currentVoiceService
        val initialized = currentVoiceService.initialize()
        if (initialized) {
            observeVoiceState(currentVoiceService)
        } else {
            _isPlaying.value = false
        }
        return initialized
    }

    private suspend fun ensureActiveVoiceService(): VoiceService? {
        val latestVoiceService = VoiceServiceFactory.getInstance(context)
        if (voiceService !== latestVoiceService) {
            AppLogger.d(TAG, "检测到TTS服务实例已切换，更新聊天页引用")
            voiceStateCollectionJob?.cancel()
            voiceService = latestVoiceService
        }

        val currentVoiceService = voiceService ?: return null
        if (!currentVoiceService.isInitialized) {
            val initialized = currentVoiceService.initialize()
            if (!initialized) {
                return null
            }
        }

        if (voiceStateCollectionJob?.isActive != true) {
            observeVoiceState(currentVoiceService)
        }
        return currentVoiceService
    }

    private fun observeVoiceState(service: VoiceService) {
        voiceStateCollectionJob?.cancel()
        voiceStateCollectionJob = viewModelScope.launch {
            service.speakingStateFlow.collect { isSpeaking ->
                _isPlaying.value = isSpeaking
            }
        }
    }

    /** 朗读消息内容 */
    fun speakMessage(message: String) {
        speakMessage(message, interrupt = true)
    }

    fun speakMessage(message: String, interrupt: Boolean) {
        viewModelScope.launch {
            try {
                val currentVoiceService = ensureActiveVoiceService()
                if (currentVoiceService == null) {
                    uiStateDelegate.showToast(context.getString(R.string.chat_voice_service_init_failed))
                    AppLogger.e(TAG, "语音服务不可用")
                    return@launch
                }

                val cleanerRegexs = speechServicesPreferences.ttsCleanerRegexsFlow.first()
                val cleanedText = TtsCleaner.clean(message, cleanerRegexs)
                val cleanMessage = WaifuMessageProcessor.cleanContentForWaifu(cleanedText)

                if (cleanMessage.isBlank()) {
                    AppLogger.d(TAG, "朗读内容为空，跳过请求")
                    return@launch
                }

                val success = currentVoiceService.speak(
                    text = cleanMessage,
                    interrupt = interrupt,
                    rate = null,
                    pitch = null
                )

                if (!success) {
                    uiStateDelegate.showToast(context.getString(R.string.chat_speak_failed))
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "朗读消息失败", e)
                uiStateDelegate.showToast(context.getString(R.string.chat_speak_message_failed, e.message ?: "Unknown error"))
            }
        }
    }

    /** 停止朗读 */
    fun stopSpeaking() {
        viewModelScope.launch {
            try {
                ensureActiveVoiceService()?.stop()
            } catch (e: Exception) {
                AppLogger.e(TAG, "停止朗读失败", e)
            }
        }
    }

    fun toggleAutoRead() {
        apiConfigDelegate.toggleAutoRead()
        // Stop speaking if auto-read is being turned off.
        // We check the new value directly from the delegate's state flow.
        viewModelScope.launch {
            // A small delay to allow the state flow to update, although it's often fast.
            delay(50)
            if (!isAutoReadEnabled.value) {
                stopSpeaking()
            }
        }
    }

    fun disableAutoRead() {
        if (isAutoReadEnabled.value) {
            apiConfigDelegate.toggleAutoRead() // This will set it to false
            stopSpeaking()
        }
    }

    fun enableAutoReadAndSpeak(content: String) {
        if (!isAutoReadEnabled.value) {
            apiConfigDelegate.toggleAutoRead() // This will set it to true
        }
        speakMessage(content)
    }

    /** 设置回复目标消息 */
    fun setReplyToMessage(message: ChatMessage) {
        _replyToMessage.value = message
    }

    /** 清除回复状态 */
    fun clearReplyToMessage() {
        _replyToMessage.value = null
    }

    fun manuallyUpdateMemory() {
        messageCoordinationDelegate.manuallyUpdateMemory()
    }

    fun manuallySummarizeConversation() {
        messageCoordinationDelegate.manuallySummarizeConversation()
    }

}
