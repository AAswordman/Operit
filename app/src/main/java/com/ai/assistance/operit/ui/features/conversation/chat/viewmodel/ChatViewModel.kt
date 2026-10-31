package com.ai.assistance.operit.ui.features.conversation.chat.viewmodel

import com.ai.assistance.operit.core.chat.voice.VoiceMessageData
import com.ai.assistance.operit.core.chat.voice.VoiceInputMode
import com.ai.assistance.operit.core.chat.voice.VoiceMessageMarkup
import com.ai.assistance.operit.core.chat.voice.canStartVoiceMessage

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import com.ai.assistance.operit.api.chat.runtime.TranslationModelNotConfiguredException
import android.Manifest
import android.content.Context
import com.ai.assistance.operit.util.android.LocaleUtils
import android.content.Intent
import android.provider.Settings
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextFieldValue.Companion
import androidx.core.content.FileProvider
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.chat.runtime.ChatRuntimeHolder
import com.ai.assistance.operit.core.chat.runtime.ChatRuntimeSlot
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.chat.context.ContextWindowBreakdown
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.chat.deletion.ChatDeletionResult
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.MessageTranslationRepository
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.LocalWebServer
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.filterNotNull
import com.ai.assistance.operit.ui.features.automation.terminal.EmbeddedTerminalPage
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceHubPage
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceEntryAction
import com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceEntryRequest
import com.ai.assistance.operit.util.text.XmlTagCleaner
import com.ai.assistance.operit.api.speech.tts.TtsService
import com.ai.assistance.operit.api.speech.tts.prefetch.PrefetchingTtsService
import com.ai.assistance.operit.api.speech.tts.prefetch.TtsSubmissionOrder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.ai.assistance.operit.api.speech.tts.TtsServiceFactory
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.ui.features.conversation.workspace.WorkspaceBackupManager
import com.ai.assistance.operit.ui.features.conversation.workspace.CommandConfig
import com.ai.assistance.operit.ui.features.conversation.workspace.WorkspaceCommandExecutionState
import com.ai.assistance.operit.ui.features.conversation.workspace.WorkspaceConfigReader
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.WorkspacePreviewRefreshBus
import com.ai.assistance.operit.ui.features.conversation.workspace.preview.WorkspacePreviewRefreshEvent
import com.ai.assistance.operit.ui.features.conversation.workspace.toWorkspaceCommandOutputEntries
import com.ai.assistance.operit.core.tools.system.terminal.Terminal
import com.ai.assistance.operit.core.speech.text.TtsCleaner
import com.ai.assistance.operit.core.speech.text.TtsSegmenter
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.mentions.findMentionTokens
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.mentions.findMentionTokenEndingAtCursor
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.mentions.isMentionContinuation
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
// 使用 services/core 的 Delegate 类
import com.ai.assistance.operit.services.core.MessageProcessingDelegate
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchAction
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.history.ChatBatchController
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchItemResult
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
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sharing.MessageImageGenerator
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.CharacterSelectorTarget
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.PendingQueueMessageItem
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatHistoryDisplayMode
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.TranslationUiState
class ChatViewModel(context: Context) : ViewModel() {
    private val context: Context = LocaleUtils.getLocalizedContext(context.applicationContext)

    companion object {
        private const val TAG = "ChatViewModel"
        private const val SPEECH_PREVIEW_MAX = 48
    }

    private data class ActiveMentionTrigger(
        val triggerChar: Char,
        val triggerIndex: Int,
        val query: String,
    )

    private data class MentionDeletionNormalization(
        val value: TextFieldValue,
        val removedMentionToken: String? = null,
    )

    private fun speechPreview(text: String): String {
        return text.replace("\n", "\\n").take(SPEECH_PREVIEW_MAX)
    }

    private fun logSpeechState(event: String, extra: String = "") {
        val suffix = if (extra.isNotBlank()) " $extra" else ""
        AppLogger.d(
            TAG,
            "speech[$event] session=${_isSpeechSessionActive.value} paused=${_isSpeechPaused.value} playing=${_isPlaying.value} autoRead=${isAutoReadEnabled.value}$suffix"
        )
    }

    // 添加语音服务
    private var voiceService: TtsService? = null
    private var voiceStateCollectionJob: Job? = null
    private var speechPlaybackJob: Job? = null
    private val speechSubmissionOrder = TtsSubmissionOrder()
    private var speechControlsHideJob: Job? = null
    private val speechServiceProfiles = SpeechServiceProfilesPreferences(context)
    private val activePromptManager = ActivePromptManager.getInstance(context)
    private val characterCardManager = CharacterCardManager.getInstance(context)

    // 添加语音播放状态
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _isSpeechSessionActive = MutableStateFlow(false)
    val isSpeechSessionActive: StateFlow<Boolean> = _isSpeechSessionActive.asStateFlow()
    private val _isSpeechPaused = MutableStateFlow(false)
    val isSpeechPaused: StateFlow<Boolean> = _isSpeechPaused.asStateFlow()

    private val pendingMessageQueueStore = PendingMessageQueueStore()
    internal val pendingMessageQueueStates: StateFlow<Map<String, PendingMessageQueueState>> =
        pendingMessageQueueStore.states

    // 添加自动朗读状态 - Now managed by ApiConfigDelegate
    val isAutoReadEnabled: StateFlow<Boolean> by lazy { apiConfigDelegate.enableAutoRead }

    // 添加回复相关状态
    private val _replyToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyToMessage: StateFlow<ChatMessage?> = _replyToMessage.asStateFlow()

    private val messageTranslationRepository =
        MessageTranslationRepository.getInstance(context)
    private val _translationState = MutableStateFlow(TranslationUiState())
    val translationState: StateFlow<TranslationUiState> = _translationState.asStateFlow()
    private var translationJob: Job? = null

    fun loadCachedTranslation(
        chatId: String,
        messageTimestamp: Long,
        text: String,
        targetLanguageCode: String,
    ) {
        translationJob?.cancel()
        _translationState.value = TranslationUiState()
        translationJob =
            viewModelScope.launch {
                try {
                    val cachedTranslation =
                        messageTranslationRepository.getCachedTranslation(
                            chatId = chatId,
                            messageTimestamp = messageTimestamp,
                            sourceText = text,
                            targetLanguageCode = targetLanguageCode,
                        )
                    _translationState.value =
                        TranslationUiState(
                            translatedText = cachedTranslation?.translatedText,
                            isFromCache = cachedTranslation != null,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(TAG, "读取消息翻译缓存失败", e)
                    _translationState.value = TranslationUiState()
                }
            }
    }

    fun translateMessage(
        chatId: String,
        messageTimestamp: Long,
        text: String,
        targetLanguageCode: String,
        targetLanguagePromptName: String,
    ) {
        translationJob?.cancel()
        translationJob =
            viewModelScope.launch {
                uiStateDelegate.clearError()
                _translationState.value = TranslationUiState(isLoading = true)
                try {
                    val translatedText =
                        EnhancedAIService.getInstance(context).translateText(
                            text = text,
                            targetLanguage = targetLanguagePromptName,
                            onUpdate = { partialText ->
                                currentCoroutineContext().ensureActive()
                                _translationState.value =
                                    TranslationUiState(
                                        isLoading = true,
                                        translatedText = partialText.takeIf { it.isNotBlank() },
                                    )
                            },
                            onRetryError = { attempt, maxAttempts, error ->
                                currentCoroutineContext().ensureActive()
                                val detail =
                                    error.takeIf { it.isNotBlank() }
                                        ?: context.getString(R.string.translation_failed_unknown)
                                OKToast.warning(context,
                                    context.getString(
                                        R.string.translation_retry_error_status,
                                        attempt,
                                        maxAttempts,
                                        detail,
                                    )
                                ).show()
                            },
                        )
                    if (translatedText.isBlank()) {
                        throw IllegalStateException(
                            context.getString(R.string.translation_empty_result)
                        )
                    }
                    currentCoroutineContext().ensureActive()
                    messageTranslationRepository.saveTranslation(
                        chatId = chatId,
                        messageTimestamp = messageTimestamp,
                        sourceText = text,
                        targetLanguageCode = targetLanguageCode,
                        translatedText = translatedText,
                    )
                    _translationState.value =
                        TranslationUiState(translatedText = translatedText)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TranslationModelNotConfiguredException) {
                    AppLogger.e(TAG, "未配置翻译模型", e)
                    val errorMessage =
                        context.getString(R.string.translation_model_not_configured)
                    _translationState.value =
                        _translationState.value.copy(
                            isLoading = false,
                            errorMessage = errorMessage,
                        )
                    OKToast.error(context, errorMessage).show()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "翻译消息失败", e)
                    val reason =
                        e.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.translation_failed_unknown)
                    val errorMessage = context.getString(R.string.translation_failed, reason)
                    _translationState.value =
                        _translationState.value.copy(
                            isLoading = false,
                            errorMessage = errorMessage,
                        )
                    OKToast.error(context, errorMessage).show()
                }
            }
    }

    fun clearTranslationState() {
        translationJob?.cancel()
        translationJob = null
        _translationState.value = TranslationUiState()
    }
    // API服务
    private var enhancedAiService: EnhancedAIService? = null

    // 工具处理器
    private val toolHandler = AIToolHandler.getInstance(context)
    private val chatRuntimeHolder = ChatRuntimeHolder.getInstance(context)

    // 工具权限系统
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)
    
    // 终端管理器（用于执行工作区命令）
    private val terminal: Terminal? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Terminal.getInstance(context)
        } else {
            null
        }
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
    val activeChatModelConfig by lazy { apiConfigDelegate.activeChatModelConfig }
    val effectiveChatConfigTarget by lazy { apiConfigDelegate.effectiveChatConfigTarget }
    val isConfigured: StateFlow<Boolean> by lazy { apiConfigDelegate.isConfigured }
    val isApiConfigInitialized: StateFlow<Boolean> by lazy { apiConfigDelegate.isInitialized }


    val featureToggles: StateFlow<Map<String, Boolean>> by lazy { apiConfigDelegate.featureToggles }
    val keepScreenOn: StateFlow<Boolean> by lazy { apiConfigDelegate.keepScreenOn }

    // 思考模式状态现在由ApiConfigDelegate管理
    val enableThinkingMode: StateFlow<Boolean> by lazy { apiConfigDelegate.enableThinkingMode }
    val thinkingOptionId: StateFlow<String> by lazy { apiConfigDelegate.thinkingOptionId }
    val enableMemoryAutoUpdate: StateFlow<Boolean> by lazy { apiConfigDelegate.enableMemoryAutoUpdate }
    val enableTools: StateFlow<Boolean> by lazy { apiConfigDelegate.enableTools }
    val toolPromptVisibility: StateFlow<Map<String, Boolean>> by lazy { apiConfigDelegate.toolPromptVisibility }
    val disableStreamOutput: StateFlow<Boolean> by lazy { apiConfigDelegate.disableStreamOutput }
    val disableUserPreferenceDescription: StateFlow<Boolean> by lazy {
        apiConfigDelegate.disableUserPreferenceDescription
    }
    val summaryTokenThreshold: StateFlow<Float> by lazy { apiConfigDelegate.effectiveSummaryTokenThreshold }
    val enableSummary: StateFlow<Boolean> by lazy { apiConfigDelegate.effectiveEnableSummary }
    val enableSummaryByMessageCount: StateFlow<Boolean> by lazy {
        apiConfigDelegate.effectiveEnableSummaryByMessageCount
    }
    val summaryMessageCountThreshold: StateFlow<Int> by lazy {
        apiConfigDelegate.effectiveSummaryMessageCountThreshold
    }

    // 上下文长度
    val maxWindowSizeInK: StateFlow<Float> by lazy { apiConfigDelegate.effectiveContextLength }
    val baseContextLengthInK: StateFlow<Float> by lazy { apiConfigDelegate.effectiveBaseContextLength }
    val maxContextLengthInK: StateFlow<Float> by lazy {
        apiConfigDelegate.effectiveMaxContextLengthSetting
    }
    val enableMaxContextMode: StateFlow<Boolean> by lazy {
        apiConfigDelegate.effectiveEnableMaxContextMode
    }

    // 聊天历史相关
    val chatHistory: StateFlow<List<ChatMessage>> by lazy { chatHistoryDelegate.chatHistory }
    val chatHistories: StateFlow<List<ChatHistory>> by lazy { chatHistoryDelegate.chatHistories }
    val currentChatId: StateFlow<String?> by lazy { chatHistoryDelegate.currentChatId }
    val loadedChatId: StateFlow<String?> by lazy { chatHistoryDelegate.loadedChatId }
    val hasOlderDisplayHistory: StateFlow<Boolean> by lazy {
        chatHistoryDelegate.hasOlderDisplayHistory
    }
    val hasNewerDisplayHistory: StateFlow<Boolean> by lazy {
        chatHistoryDelegate.hasNewerDisplayHistory
    }
    val isLoadingDisplayWindow: StateFlow<Boolean> by lazy {
        chatHistoryDelegate.isLoadingDisplayWindow
    }

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
    val masterPermissionLevel: StateFlow<PermissionLevel> by lazy {
        uiStateDelegate.masterPermissionLevel
    }

    // 聊天统计相关
    val currentWindowSize: StateFlow<Long> by lazy { tokenStatsDelegate.currentWindowSizeFlow }
    val inputTokenCount: StateFlow<Long> by lazy { tokenStatsDelegate.cumulativeInputTokensFlow }
    val outputTokenCount: StateFlow<Long> by lazy { tokenStatsDelegate.cumulativeOutputTokensFlow }
    val perRequestTokenCount: StateFlow<Pair<Long, Long>?> by lazy { tokenStatsDelegate.perRequestTokenCountFlow }



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

    // 添加一个用于跟踪附件面板状态的变量
    private val _attachmentPanelState = MutableStateFlow(false)
    val attachmentPanelState: StateFlow<Boolean> = _attachmentPanelState

    // 工作区主页、文件页面和终端统一导航，绑定状态仍独立保存。
    private val _workspaceHubPage = MutableStateFlow(WorkspaceHubPage.CHAT)
    val workspaceHubPage: StateFlow<WorkspaceHubPage> = _workspaceHubPage.asStateFlow()
    private val _embeddedTerminalPage = MutableStateFlow(EmbeddedTerminalPage.CONSOLE)
    val embeddedTerminalPage: StateFlow<EmbeddedTerminalPage> = _embeddedTerminalPage.asStateFlow()
    private val _workspaceEntryRequest = MutableStateFlow(WorkspaceEntryRequest())
    val workspaceEntryRequest: StateFlow<WorkspaceEntryRequest> = _workspaceEntryRequest.asStateFlow()

    private val _isWorkspacePreparing = MutableStateFlow(false)
    val isWorkspacePreparing: StateFlow<Boolean> = _isWorkspacePreparing.asStateFlow()

    // 添加工作区状态
    val isWorkspaceOpen: StateFlow<Boolean> by lazy {
        combine(currentChatId, chatHistories) { id, histories ->
            histories.find { it.id == id }?.workspace?.isNotBlank() == true
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    }


    // 添加WebView刷新控制流 - 使用Int计数器避免重复刷新问题
    private val _webViewRefreshCounter = MutableStateFlow(0)
    val webViewRefreshCounter: StateFlow<Int> = _webViewRefreshCounter

    // 控制 @ mention 建议面板的可见性
    private val _showMentionSuggestionPanel = MutableStateFlow(false)
    val showMentionSuggestionPanel: StateFlow<Boolean> = _showMentionSuggestionPanel.asStateFlow()

    // 当前 @ mention 的搜索词
    private val _mentionSearchQuery = MutableStateFlow("")
    val mentionSearchQuery: StateFlow<String> = _mentionSearchQuery.asStateFlow()

    private val _mentionSuggestionTriggerChar = MutableStateFlow<Char?>(null)
    val mentionSuggestionTriggerChar: StateFlow<Char?> = _mentionSuggestionTriggerChar.asStateFlow()

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
                                InputProcessingState.Summarizing(
                                    LocaleUtils.getLocalizedContext(context).getString(R.string.chat_summarizing_memory)
                                )
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
            OKToast.info(context, context.getString(R.string.chat_use_default_config_continue)).show()
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

    fun updateThinkingOptionId(optionId: String) {
        apiConfigDelegate.updateThinkingOptionId(optionId)
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

    val toolPromptOrder: StateFlow<List<String>> by lazy { apiConfigDelegate.toolPromptOrder }

    fun saveToolPromptOrder(order: List<String>) {
        apiConfigDelegate.saveToolPromptOrder(order)
    }

    fun toggleDisableStreamOutput() {
        apiConfigDelegate.toggleDisableStreamOutput()
    }

    fun toggleDisableUserPreferenceDescription() {
        apiConfigDelegate.toggleDisableUserPreferenceDescription()
    }

    // 聊天历史相关方法
    val chatListMotion = com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.history.ChatListMotionState()

    fun createNewChat(
        characterCardName: String? = null, characterGroupId: String? = null,
        folderId: String? = null, inheritGroupFromCurrent: Boolean = true,
    ) {
        chatHistoryDelegate.createNewChat(
            characterCardName = characterCardName, characterGroupId = characterGroupId,
            folderId = folderId, inheritGroupFromCurrent = inheritGroupFromCurrent,
            onCreated = chatListMotion::onChatCreated,
        )
    }

    fun createNewChatWithDraft(draft: String) {
        val trimmedDraft = draft.trim()
        if (trimmedDraft.isBlank()) return

        viewModelScope.launch {
            val previousChatId = chatHistoryDelegate.currentChatId.value
            chatHistoryDelegate.createNewChat()
            chatHistoryDelegate.currentChatId
                .filter { chatId -> chatId != null && chatId != previousChatId }
                .first()
            messageProcessingDelegate.updateUserMessage(
                TextFieldValue(
                    text = trimmedDraft,
                    selection = TextRange(trimmedDraft.length)
                )
            )
        }
    }

    fun switchChat(chatId: String) {
        // 切换对话后取消旧对话的页面打开任务，避免更新错误的工作区。
        cancelWorkspaceOpen()
        chatListMotion.newChatId.value?.let(chatListMotion::consumeIntro)
        chatHistoryDelegate.switchChat(chatId)
        chatRuntimeHolder.syncMainChatSelectionToFloating(chatId)

        // 如果当前WebView正在显示，则更新工作区并触发刷新
        if (_workspaceHubPage.value == WorkspaceHubPage.WORKSPACE) {
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

    fun loadOlderMessagesForCurrentChat() {
        viewModelScope.launch {
            chatHistoryDelegate.loadOlderMessagesForCurrentChat()
        }
    }

    fun loadNewerMessagesForCurrentChat() {
        viewModelScope.launch {
            chatHistoryDelegate.loadNewerMessagesForCurrentChat()
        }
    }

    fun showLatestMessagesForCurrentChat() {
        viewModelScope.launch {
            chatHistoryDelegate.showLatestMessagesForCurrentChat()
        }
    }

    suspend fun loadChatMessageLocatorPreviews(
        chatId: String,
        query: String = "",
    ): List<ChatMessageLocatorPreview> =
        chatHistoryDelegate.loadChatMessageLocatorPreviews(chatId, query)

    suspend fun revealMessageForCurrentChat(targetTimestamp: Long): Boolean =
        chatHistoryDelegate.revealMessageForCurrentChat(targetTimestamp)

    fun deleteChatHistory(chatId: String, animate: Boolean = false) {
        chatListMotion.delete(chatId, viewModelScope, animate) { id, completed ->
            chatHistoryDelegate.deleteChatHistoryWithResult(id) { result ->
                if (result == ChatDeletionResult.DELETED) {
                    pendingMessageQueueStore.removeChat(id)
                    viewModelScope.launch {
                        // 等列表确实移除目标后释放效果，避免数据流更新前标题闪回。
                        chatHistories.first { histories -> histories.none { it.id == id } }
                        completed(true)
                    }
                } else {
                    completed(false)
                    showChatDeletionFailure(result, clearing = false)
                }
            }
        }
    }

    fun clearCurrentChat() {
        chatHistoryDelegate.clearCurrentChatWithResult { result ->
            if (result == ChatDeletionResult.DELETED) {
                OKToast.success(context, context.getString(R.string.chat_cleared)).show()
            } else showChatDeletionFailure(result, clearing = true)
        }
    }

    private fun showChatDeletionFailure(result: ChatDeletionResult, clearing: Boolean) {
        val message = when (result) {
            ChatDeletionResult.LOCKED -> R.string.chat_locked_cannot_delete
            ChatDeletionResult.NOT_FOUND -> R.string.chat_delete_not_found
            else -> if (clearing) R.string.chat_clear_failed else R.string.chat_delete_failed
        }
        OKToast.typed(context, context.getString(message), if (result == ChatDeletionResult.LOCKED || result == ChatDeletionResult.NOT_FOUND) OKToast.Kind.Warning else OKToast.Kind.Error).show()
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
                                !history.archived && history.characterGroupId?.trim() == targetGroupId
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
                if (history.archived) return@filter false
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
        OKToast.success(context, context.getString(R.string.chat_branch_created)).show()
    }

    /** 插入总结 */
    fun insertSummary(message: ChatMessage) {
        performInsertSummary(message)
    }

    private fun performInsertSummary(message: ChatMessage) {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) {
                    OKToast.warning(context, context.getString(R.string.chat_please_create_new_chat), OKToast.LENGTH_SHORT).show()
                    return@launch
                }
                if (message.sender != "user" && message.sender != "ai") {
                    OKToast.info(context, context.getString(R.string.chat_no_messages_to_summarize)).show()
                    return@launch
                }
                
                // 设置输入处理状态（按chatId隔离）
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Summarizing(
                        LocaleUtils.getLocalizedContext(context).getString(R.string.chat_summarizing_generating)
                    )
                )

                val beforeTimestamp = if (message.sender == "ai") message.timestamp else null
                val afterTimestamp = if (message.sender == "user") message.timestamp else null
                val messagesToSummarize =
                    withContext(Dispatchers.IO) {
                        chatHistoryDelegate
                            .loadMessagesForSummaryInsertion(
                                chatId = currentChatId,
                                beforeTimestampExclusive = afterTimestamp,
                                upToTimestampInclusive = beforeTimestamp,
                            ).filter { it.sender == "user" || it.sender == "ai" }
                    }

                if (messagesToSummarize.isEmpty()) {
                    OKToast.info(context, context.getString(R.string.chat_no_messages_to_summarize)).show()
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                    return@launch
                }
                
                // 显示生成中提示
                OKToast.info(context,
                    LocaleUtils.getLocalizedContext(context).getString(R.string.chat_summarizing_generating)
                ).show()
                
                // 调用AI生成总结
                if (enhancedAiService == null) {
                    OKToast.warning(context, context.getString(R.string.chat_ai_service_not_initialized)).show()
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                    return@launch
                }

                // 检查是否是群聊
                val currentChat = chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == currentChatId }
                val isGroupChat = currentChat?.characterGroupId != null
                val summaryConfig = withContext(Dispatchers.IO) {
                    messageCoordinationDelegate.readSummaryConfig()
                }

                val summaryMessage = withContext(Dispatchers.IO) {
                    AIMessageManager.summarizeMemory(
                        enhancedAiService!!,
                        messagesToSummarize,
                        autoContinue = false,
                        isGroupChat = isGroupChat,
                        summaryConfig = summaryConfig
                    )
                }

                if (summaryMessage != null) {
                    // 插入总结消息
                    withContext(Dispatchers.IO) {
                        chatHistoryDelegate.addSummaryMessage(
                            summaryMessage = summaryMessage,
                            beforeTimestamp = beforeTimestamp,
                            afterTimestamp = afterTimestamp,
                        )
                    }
                    messageCoordinationDelegate.scheduleStableContextWindowRefresh(chatId = currentChatId)

                    OKToast.success(context, context.getString(R.string.chat_summary_inserted)).show()
                } else {
                    OKToast.error(context, context.getString(R.string.chat_summary_generation_failed)).show()
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
                OKToast.error(context, context.getString(R.string.chat_insert_summary_failed, e.message ?: "")).show()
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
            val chatIdSnapshot = chatHistoryDelegate.currentChatId.value
            if (chatIdSnapshot == null) {
                OKToast.warning(context, context.getString(R.string.chat_please_create_new_chat), OKToast.LENGTH_SHORT).show()
                return@launch
            }

            val historySnapshot = chatHistoryDelegate.chatHistory.value
            val sortedIndices = indices.sortedDescending()
            val timestamps = mutableListOf<Long>()
            for (index in sortedIndices) {
                val message = historySnapshot.getOrNull(index)
                if (message == null) {
                    AppLogger.w(TAG, "批量删除消息索引无效: index=$index, historySize=${historySnapshot.size}")
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_invalid_message_index))
                    return@launch
                }
                timestamps += message.timestamp
            }

            chatHistoryDelegate.deleteMessagesByTimestamps(chatIdSnapshot, timestamps)
            AppLogger.d(TAG, "批量删除完成")
        }
    }

    fun setMessageFavorite(timestamp: Long, isFavorite: Boolean) {
        chatHistoryDelegate.setMessageFavorite(timestamp, isFavorite)
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
        userChatStyle: ChatStyle,
        aiChatStyle: ChatStyle,
        cursorUserBubbleLiquidGlass: Boolean = false,
        cursorUserBubbleWaterGlass: Boolean = false,
        bubbleUserBubbleLiquidGlass: Boolean = false,
        bubbleUserBubbleWaterGlass: Boolean = false,
        bubbleAiBubbleLiquidGlass: Boolean = false,
        bubbleAiBubbleWaterGlass: Boolean = false,
        initialThinkingExpanded: Boolean = false,
        expandThinkToolsGroups: Boolean = false,
        includeBackground: Boolean = true,
        borderWidthDp: Float = 1.5f,
        forceShowThinkingProcess: Boolean = false,
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
                        userChatStyle = userChatStyle,
                        aiChatStyle = aiChatStyle,
                        cursorUserBubbleLiquidGlass = cursorUserBubbleLiquidGlass,
                        cursorUserBubbleWaterGlass = cursorUserBubbleWaterGlass,
                        bubbleUserBubbleLiquidGlass = bubbleUserBubbleLiquidGlass,
                        bubbleUserBubbleWaterGlass = bubbleUserBubbleWaterGlass,
                        bubbleAiBubbleLiquidGlass = bubbleAiBubbleLiquidGlass,
                        bubbleAiBubbleWaterGlass = bubbleAiBubbleWaterGlass,
                        initialThinkingExpanded = initialThinkingExpanded,
                        expandThinkToolsGroups = expandThinkToolsGroups,
                        includeBackground = includeBackground,
                        borderWidthDp = borderWidthDp,
                        forceShowThinkingProcess = forceShowThinkingProcess
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
            chatHistoryDelegate.saveChatStatistics()
        }
    }

    // 添加消息编辑方法
    fun updateMessage(index: Int, editedMessage: ChatMessage) {
        viewModelScope.launch {
            try {
                val currentHistory = chatHistoryDelegate.chatHistory.value
                if (currentHistory.getOrNull(index) == null) {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_invalid_message_index))
                    return@launch
                }

                // 直接在数据库中更新该条消息
                chatHistoryDelegate.addMessageToChat(editedMessage)
                // 变更历史后异步刷新窗口，不阻塞编辑操作
                messageCoordinationDelegate.scheduleStableContextWindowRefresh(chatId = currentChatId.value)

                // 显示成功提示
                OKToast.success(context, context.getString(R.string.chat_message_updated)).show()
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
                messageCoordinationDelegate.scheduleStableContextWindowRefresh(chatId = currentChatId.value)
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
                messageCoordinationDelegate.scheduleStableContextWindowRefresh(chatId = currentChatId.value)
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
                // 获取要删除的第一条消息的时间戳
                val timestampOfFirstDeletedMessage = currentHistory[index].timestamp

                // **核心修复**：调用新的委托方法，原子性地更新数据库和内存
                chatHistoryDelegate.truncateChatHistory(timestampOfFirstDeletedMessage)

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
                val timestampOfFirstDeletedMessage = currentHistory[index].timestamp
                chatHistoryDelegate.truncateChatHistory(timestampOfFirstDeletedMessage)

                val plainText = XmlTagCleaner.stripXmlLikeTags(targetMessage.content)
                updateUserMessage(TextFieldValue(plainText))

                OKToast.info(context, context.getString(R.string.chat_rolled_back_message_in_input)).show()
            } catch (e: Exception) {
                AppLogger.e(TAG, "回滚到指定消息失败", e)
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_rollback_failed, e.message ?: ""))
            }
        }
    }

    // 消息处理相关方法
    fun updateUserMessage(value: TextFieldValue) {
        val normalization = normalizeMentionDeletion(userMessage.value, value)
        val normalizedValue = normalization.value
        val removedMentionToken = normalization.removedMentionToken

        if (
            !removedMentionToken.isNullOrBlank() &&
                !containsMentionToken(normalizedValue.text, removedMentionToken)
        ) {
            attachmentDelegate.removePackageAttachment(removedMentionToken)
            attachmentDelegate.removeWorkspaceMentionAttachment(removedMentionToken)
        }

        messageProcessingDelegate.updateUserMessage(normalizedValue)
        updateMentionSuggestionState(normalizedValue)
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

    fun replaceCurrentMentionToken(token: String) {
        val trimmedToken = token.trim()
        if (trimmedToken.isEmpty()) return

        val current = userMessage.value
        val activeMention = findActiveMentionTrigger(current) ?: return
        val text = current.text
        val cursor = current.selection.start.coerceIn(0, text.length)
        val before = text.substring(0, activeMention.triggerIndex)
        val after = text.substring(cursor)
        val insertion =
            buildString {
                append(activeMention.triggerChar)
                append(trimmedToken)
                if (after.isEmpty() || !after.first().isWhitespace()) {
                    append(' ')
                }
            }

        val newText = before + insertion + after
        val newCursor = (before.length + insertion.length).coerceAtMost(newText.length)
        updateUserMessage(TextFieldValue(newText, selection = TextRange(newCursor)))
    }

    fun selectMentionPackage(packageName: String) {
        val trimmedPackageName = packageName.trim()
        if (trimmedPackageName.isEmpty()) return

        replaceCurrentMentionToken(trimmedPackageName)
        attachMentionPackage(trimmedPackageName)
        hideMentionSuggestionPanel()
    }

    fun selectMentionWorkspaceEntry(relativePath: String) {
        val normalizedRelativePath = relativePath.trim().replace('\\', '/')
        if (normalizedRelativePath.isEmpty()) return

        replaceCurrentMentionToken(normalizedRelativePath)

        val activeChat =
            chatHistories.value.firstOrNull { it.id == currentChatId.value }
        val workspacePath = activeChat?.workspace
        if (!workspacePath.isNullOrBlank()) {
            attachMentionWorkspaceEntry(
                workspacePath = workspacePath,
                relativePath = normalizedRelativePath,
                workspaceEnv = activeChat.workspaceEnv,
            )
        }

        hideMentionSuggestionPanel()
    }

    fun submitA2ui(
        owner: com.ai.assistance.operit.ui.common.a2ui.A2uiOwner,
        text: String,
    ): Boolean = messageCoordinationDelegate.trySendA2ui(owner, text)

    fun sendUserMessage(promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT) {
        hideMentionSuggestionPanel()
        messageCoordinationDelegate.sendUserMessage(
            promptFunctionType = promptFunctionType,
            preferActiveRoleCard = true,
        )
    }

    /** 固定原会话发送，等待保存并交接录音所有权后才返回。 */
    suspend fun sendVoiceMessage(
        message: VoiceMessageData,
        originChatId: String,
        onPersisted: () -> Unit,
    ): Boolean {
        fun canSubmit(): Boolean {
            val state = currentChatInputProcessingState.value
            val processing = messageProcessingDelegate.isChatLoading(originChatId) ||
                (state !is InputProcessingState.Idle && state !is InputProcessingState.Completed && state !is InputProcessingState.Error)
            return currentChatId.value == originChatId &&
                canStartVoiceMessage(userMessage.value.text, attachments.value.size, processing, replyToMessage.value != null) &&
                (message.transcript.isNotBlank() || message.inputMode == VoiceInputMode.AUDIO) &&
                java.io.File(message.attachment.filePath).isFile
        }
        if (!canSubmit()) return false
        hideMentionSuggestionPanel()
        return messageCoordinationDelegate.sendVoiceMessage(
            chatId = originChatId,
            content = VoiceMessageMarkup.encode(message),
            canSubmit = ::canSubmit,
            onPersisted = onPersisted,
        )
    }

    fun continueGeneration() {
        messageCoordinationDelegate.continueGeneration()
    }

    fun sendTextMessage(text: String, promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT) {
        hideMentionSuggestionPanel()
        messageCoordinationDelegate.sendUserMessage(
            promptFunctionType = promptFunctionType,
            preferActiveRoleCard = true,
            messageTextOverride = text
        )
    }

    fun sendTextMessage(
        text: String,
        chatId: String,
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
    ) {
        hideMentionSuggestionPanel()
        messageCoordinationDelegate.sendUserMessage(
            promptFunctionType = promptFunctionType,
            chatIdOverride = chatId,
            messageTextOverride = text,
        )
    }

    fun enqueuePendingQueueMessage(chatId: String, text: String, isQueueBlocked: Boolean) {
        pendingMessageQueueStore.enqueue(chatId, text, isQueueBlocked)
    }

    fun removePendingQueueMessage(chatId: String, messageId: Long): PendingQueueMessageItem? =
        pendingMessageQueueStore.remove(chatId, messageId)

    fun restorePendingQueueMessage(chatId: String, message: PendingQueueMessageItem) {
        pendingMessageQueueStore.restore(chatId, message)
    }

    fun setPendingQueueExpanded(chatId: String, expanded: Boolean) {
        pendingMessageQueueStore.setExpanded(chatId, expanded)
    }

    fun consumePendingQueueAutoDequeueSignal(chatId: String, isQueueBlocked: Boolean): Boolean =
        pendingMessageQueueStore.consumeAutoDequeueSignal(chatId, isQueueBlocked)

    fun suppressNextPendingQueueAutoDequeue(chatId: String) {
        pendingMessageQueueStore.suppressNextAutoDequeue(chatId)
    }

    suspend fun removeLastVisibleUserMessageFromCurrentChat(text: String): Boolean {
        val chatId = currentChatId.value ?: return false
        val messageText = text.trim()
        if (messageText.isBlank()) return false

        val lastMessage = chatHistory.value.lastOrNull() ?: return false
        if (lastMessage.sender != "user" || lastMessage.content != messageText) {
            AppLogger.w(
                TAG,
                "Waifu merge send expected last visible user message, but found sender=${lastMessage.sender}, contentLength=${lastMessage.content.length}"
            )
            return false
        }

        chatHistoryDelegate.deleteMessagesByTimestamps(chatId, listOf(lastMessage.timestamp))
        return true
    }

    suspend fun addVisibleUserMessageToCurrentChat(text: String) {
        val messageText = text.trim()
        if (messageText.isBlank()) return

        val chatId = currentChatId.value ?: return
        val isFirstMessage = !chatHistoryDelegate.hasUserMessage(chatId)
        val fallbackTitle = if (isFirstMessage) {
            context.getString(R.string.new_conversation).also { chatHistoryDelegate.updateChatTitle(chatId, it) }
        } else {
            null
        }
        chatHistoryDelegate.addMessageToChat(
            ChatMessage(
                sender = "user",
                content = messageText,
                roleName = context.getString(R.string.message_role_user)
            ),
            chatId
        )
        fallbackTitle?.let { titleToPreserve ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val generatedTitle = EnhancedAIService.getChatInstance(context, chatId)
                        .generateConversationTitle(userText = messageText)
                        .trim()
                    val currentTitle = chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == chatId }?.title
                    if (generatedTitle.isNotBlank() && currentTitle == titleToPreserve) {
                        chatHistoryDelegate.updateChatTitle(chatId, generatedTitle)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "生成可见用户消息对话标题失败", e)
                }
            }
        }
    }

    private fun normalizeMentionDeletion(
        previous: TextFieldValue,
        proposed: TextFieldValue,
    ): MentionDeletionNormalization {
        if (previous.selection.start != previous.selection.end) return MentionDeletionNormalization(proposed)
        if (proposed.selection.start != proposed.selection.end) return MentionDeletionNormalization(proposed)
        if (previous.text.length != proposed.text.length + 1) return MentionDeletionNormalization(proposed)

        val oldCursor = previous.selection.start.coerceIn(0, previous.text.length)
        val newCursor = proposed.selection.start.coerceIn(0, proposed.text.length)
        if (newCursor != oldCursor - 1) return MentionDeletionNormalization(proposed)

        val expectedText = previous.text.removeRange(newCursor, oldCursor)
        if (expectedText != proposed.text) return MentionDeletionNormalization(proposed)

        val mentionToken =
            findMentionTokenEndingAtCursor(previous.text, oldCursor)
                ?: return MentionDeletionNormalization(proposed)
        val removedMentionToken =
            previous.text.substring(mentionToken.start + 1, mentionToken.contentEndExclusive).trim()
        val normalizedText = previous.text.removeRange(mentionToken.start, mentionToken.endExclusive)
        return MentionDeletionNormalization(
            value =
                proposed.copy(
                    text = normalizedText,
                    selection = TextRange(mentionToken.start),
                ),
            removedMentionToken = removedMentionToken.ifBlank { null },
        )
    }

    private fun attachMentionPackage(packageName: String) {
        viewModelScope.launch {
            try {
                attachmentDelegate.attachPackage(packageName)
                if (!containsMentionToken(userMessage.value.text, packageName)) {
                    attachmentDelegate.removePackageAttachment(packageName)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "添加 mention 包附件失败", e)
                OKToast.error(context, context.getString(R.string.attachment_package_failed, packageName)).show()
            }
        }
    }

    private fun attachMentionWorkspaceEntry(
        workspacePath: String,
        relativePath: String,
        workspaceEnv: String?,
    ) {
        viewModelScope.launch {
            try {
                attachmentDelegate.attachWorkspaceMention(
                    workspacePath = workspacePath,
                    relativePath = relativePath,
                    workspaceEnv = workspaceEnv,
                )
                if (!containsMentionToken(userMessage.value.text, relativePath)) {
                    attachmentDelegate.removeWorkspaceMentionAttachment(relativePath)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "添加 mention 工作区附件失败", e)
                OKToast.warning(context, context.getString(R.string.attachment_cannot_attach, relativePath)).show()
            }
        }
    }

    private fun containsMentionToken(text: String, token: String): Boolean {
        val trimmedToken = token.trim()
        if (trimmedToken.isEmpty()) return false

        return findMentionTokens(text).any { mentionToken ->
            text.substring(mentionToken.start + 1, mentionToken.contentEndExclusive) == trimmedToken
        }
    }

    fun cancelCurrentMessage() {
        val chatId = chatHistoryDelegate.currentChatId.value
        if (chatId != null) {
            cancelMessage(chatId)
        }
    }

    fun cancelMessage(chatId: String) {
        if (::messageCoordinationDelegate.isInitialized) {
            messageCoordinationDelegate.cancelSummaryForChat(chatId)
        }
        messageProcessingDelegate.cancelMessage(chatId)
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

    fun setMasterPermissionLevel(level: PermissionLevel) {
        viewModelScope.launch {
            toolPermissionSystem.saveMasterSwitch(level)
        }
    }

    // 附件相关方法
    /** Handles a file or image attachment selected by the user */
    fun handleAttachment(filePath: String) {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) {
                    // 与发消息保持一致：无活跃对话时给出明确提示，而不是静默失败
                    OKToast.warning(context, context.getString(R.string.chat_please_create_new_chat), OKToast.LENGTH_SHORT).show()
                    return@launch
                }
                
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

    suspend fun attachPastedText(text: String): Boolean {
        val currentChatId = chatHistoryDelegate.currentChatId.value ?: return false
        messageProcessingDelegate.setInputProcessingStateForChat(
            currentChatId,
            InputProcessingState.Processing(context.getString(R.string.chat_processing_attachment))
        )
        return try {
            attachmentDelegate.attachPastedText(text)
        } finally {
            messageProcessingDelegate.setInputProcessingStateForChat(
                currentChatId,
                InputProcessingState.Idle
            )
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
        OKToast.success(context, context.getString(R.string.chat_inserted_attachment_ref, attachment.fileName)).show()
    }

    /** 隐藏 @ mention 建议面板 */
    fun hideMentionSuggestionPanel() {
        clearMentionSuggestionState()
    }

    /** Captures the current screen content and attaches it to the message */
    fun captureScreenContent() {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) {
                    // 无活跃对话时给出明确提示，而不是静默失败
                    OKToast.warning(context, context.getString(R.string.chat_please_create_new_chat), OKToast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 显示屏幕内容获取进度
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_fetching_screen_content))
                )
                OKToast.info(context, context.getString(R.string.chat_fetching_screen_content)).show()

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
                if (currentChatId == null) {
                    // 无活跃对话时给出明确提示，而不是静默失败
                    OKToast.warning(context, context.getString(R.string.chat_please_create_new_chat), OKToast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 显示通知获取进度
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_fetching_notifications))
                )
                OKToast.info(context, context.getString(R.string.chat_fetching_notifications)).show()

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
                if (currentChatId == null) {
                    // 无活跃对话时给出明确提示，而不是静默失败
                    OKToast.warning(context, context.getString(R.string.chat_please_create_new_chat), OKToast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 显示位置获取进度
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_fetching_location))
                )
                OKToast.info(context, context.getString(R.string.chat_fetching_location)).show()

                // 直接委托给attachmentDelegate执行
                attachmentDelegate.captureLocation()
                
                // 隐藏进度状态
                messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error capturing location", e)
                OKToast.error(context, context.getString(R.string.chat_fetch_location_failed, e.message ?: "")).show()
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
                if (currentChatId == null) {
                    // 无活跃对话时给出明确提示，而不是静默失败
                    OKToast.warning(context, context.getString(R.string.chat_please_create_new_chat), OKToast.LENGTH_SHORT).show()
                    return@launch
                }
                // 显示记忆文件夹附着进度
                messageProcessingDelegate.setInputProcessingStateForChat(
                    currentChatId,
                    InputProcessingState.Processing(context.getString(R.string.chat_attaching_memory_folders))
                )
                OKToast.info(context, context.getString(R.string.chat_attaching_memory_folders)).show()

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
            val currentChatId = chatHistoryDelegate.currentChatId.value
            if (currentChatId == null) {
                // 与文件/图片附件入口保持一致：无活跃对话时给出明确提示
                OKToast.warning(context, context.getString(R.string.chat_please_create_new_chat), OKToast.LENGTH_SHORT).show()
                return@launch
            }
            attachmentDelegate.handleTakenPhoto(uri)
        }
    }

    /** Attaches a package's prompt content to the current message as an attachment */
    fun attachPackage(packageName: String) {
        viewModelScope.launch {
            try {
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) {
                    // 与文件/图片附件入口保持一致：无活跃对话时给出明确提示
                    OKToast.warning(context, context.getString(R.string.chat_please_create_new_chat), OKToast.LENGTH_SHORT).show()
                    return@launch
                }
                attachmentDelegate.attachPackage(packageName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "添加包附件失败", e)
                OKToast.error(context, context.getString(R.string.attachment_package_failed, packageName)).show()
            }
        }
    }

    private fun updateMentionSuggestionState(value: TextFieldValue) {
        val activeMention = findActiveMentionTrigger(value)
        if (activeMention == null) {
            clearMentionSuggestionState()
            return
        }

        _showMentionSuggestionPanel.value = true
        _mentionSuggestionTriggerChar.value = activeMention.triggerChar
        _mentionSearchQuery.value = activeMention.query
    }

    private fun clearMentionSuggestionState() {
        _showMentionSuggestionPanel.value = false
        _mentionSuggestionTriggerChar.value = null
        _mentionSearchQuery.value = ""
    }

    private fun findActiveMentionTrigger(value: TextFieldValue): ActiveMentionTrigger? {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        var index = cursor - 1
        while (index >= 0) {
            val currentChar = text[index]
            if (currentChar.isWhitespace()) {
                return null
            }
            if (currentChar != '@' && currentChar != '/') {
                index -= 1
                continue
            }
            if (currentChar == '@' && index > 0 && isMentionContinuation(text[index - 1], '@')) {
                index -= 1
                continue
            }
            if (currentChar == '/' && index > 0 && !text[index - 1].isWhitespace()) {
                index -= 1
                continue
            }

            val query = text.substring(index + 1, cursor)
            if (query.any(Char::isWhitespace)) {
                return null
            }

            return ActiveMentionTrigger(
                triggerChar = currentChar,
                triggerIndex = index,
                query = query.trim(),
            )
        }
        return null
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
    fun handleSharedText(text: String, targetChatId: String? = null) {
        val sharedText = text.trim()
        if (sharedText.isBlank()) return

        viewModelScope.launch {
            try {
                val chatId = resolveTargetChatIdForSharedContent(targetChatId)
                if (chatId == null) {
                    AppLogger.e(TAG, "Failed to prepare target chat for shared text")
                    uiStateDelegate.showErrorMessage(
                        context.getString(R.string.chat_prepare_shared_target_failed)
                    )
                    return@launch
                }

                messageProcessingDelegate.updateUserMessage(
                    TextFieldValue(sharedText)
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "处理分享文本失败", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_process_shared_files_failed, e.message ?: "")
                )
            }
        }
    }

    fun handleSharedFiles(uris: List<Uri>, targetChatId: String? = null, sharedText: String? = null) {
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
                
                if (messageProcessingDelegate.userMessage.value.text.isBlank()) {
                    AppLogger.d(TAG, "Setting pre-filled message")
                    val text = sharedText?.trim()
                    if (!text.isNullOrBlank()) {
                        messageProcessingDelegate.updateUserMessage(
                            TextFieldValue(text)
                        )
                    } else {
                        messageProcessingDelegate.updateUserMessage(
                            TextFieldValue(context.getString(R.string.chat_prefill_check_file))
                        )
                    }
                }

                // Clear processing state
                messageProcessingDelegate.setInputProcessingStateForChat(chatId, InputProcessingState.Idle)
                
                AppLogger.d(TAG, "Successfully processed shared files")
                OKToast.success(context, context.getString(R.string.chat_added_files_count, uris.size)).show()
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

    fun onWorkspaceHubButtonClick() {
        val nextPage = if (_workspaceHubPage.value == WorkspaceHubPage.CHAT) {
            WorkspaceHubPage.HOME
        } else {
            WorkspaceHubPage.CHAT
        }
        cancelWorkspaceOpen()
        _workspaceHubPage.value = nextPage
    }

    /** 从侧栏选择对话时回到消息页，同时取消尚未完成的工作区打开。 */
    fun showChatPage() {
        cancelWorkspaceOpen()
        _workspaceHubPage.value = WorkspaceHubPage.CHAT
    }

    fun showWorkspaceHubHome() {
        cancelWorkspaceOpen()
        _workspaceHubPage.value = WorkspaceHubPage.HOME
    }

    fun onWorkspaceHubBack() {
        if (_isWorkspacePreparing.value || _workspaceHubPage.value != WorkspaceHubPage.HOME) {
            showWorkspaceHubHome()
        } else {
            cancelWorkspaceOpen()
            _workspaceHubPage.value = WorkspaceHubPage.CHAT
        }
    }

    private fun cancelWorkspaceOpen() {
        workspaceOpenJob?.cancel()
        workspaceOpenJob = null
        _isWorkspacePreparing.value = false
    }

    fun openWorkspaceFromHub(action: WorkspaceEntryAction = WorkspaceEntryAction.DEFAULT) {
        if (_isWorkspacePreparing.value) return
        if (_workspaceHubPage.value == WorkspaceHubPage.WORKSPACE && action == WorkspaceEntryAction.DEFAULT) return
        cancelWorkspaceOpen()
        workspaceOpenJob = viewModelScope.launch {
            _isWorkspacePreparing.value = true
            try {
                val chatId = awaitWorkspaceChatId()
                prepareWorkspaceServerForCurrentChat(chatId)
                currentCoroutineContext().ensureActive()
                _workspaceEntryRequest.value = WorkspaceEntryRequest(
                    action = action,
                    sequence = _workspaceEntryRequest.value.sequence + 1,
                )
                _workspaceHubPage.value = WorkspaceHubPage.WORKSPACE
            } catch (e: TimeoutCancellationException) {
                AppLogger.e(TAG, "等待当前工作区对话超时", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_update_workspace_server_failed, e.message ?: "")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppLogger.e(TAG, "启动工作区服务失败", e)
                showErrorMessage(context.getString(R.string.chat_start_workspace_server_failed))
            } catch (e: Exception) {
                AppLogger.e(TAG, "打开工作区失败", e)
                uiStateDelegate.showErrorMessage(
                    context.getString(R.string.chat_update_workspace_server_failed, e.message ?: "")
                )
            } finally {
                // 取消时由导航立即清除准备状态，旧任务不再修改新任务的状态。
                if (isActive) _isWorkspacePreparing.value = false
            }
        }
    }

    private suspend fun awaitWorkspaceChatId(): String = withTimeout(5_000) {
        if (currentChatId.value == null) createNewChat()
        val chatId = currentChatId.filterNotNull().first()
        chatHistories.first { histories -> histories.any { it.id == chatId } }
        chatId
    }

    fun openTerminalFromHub(page: EmbeddedTerminalPage = EmbeddedTerminalPage.CONSOLE) {
        cancelWorkspaceOpen()
        _embeddedTerminalPage.value = page
        _workspaceHubPage.value = WorkspaceHubPage.TERMINAL
    }
    fun showEmbeddedTerminalPage(page: EmbeddedTerminalPage) {
        _embeddedTerminalPage.value = page
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

    private fun notifyWorkspacePreviewRefresh(
        workspacePath: String,
        workspaceEnv: String?,
        affectedPaths: List<String>,
        source: String
    ) {
        WorkspacePreviewRefreshBus.tryEmit(
            WorkspacePreviewRefreshEvent(
                workspacePath = workspacePath,
                workspaceEnv = workspaceEnv,
                affectedPaths = affectedPaths,
                source = source
            )
        )
    }

    private fun resolveWorkspaceEnvForPath(workspacePath: String): String? {
        val activeChatId = currentChatId.value
        val activeChat =
            chatHistories.value.firstOrNull { history ->
                history.id == activeChatId && history.workspace == workspacePath
            }
        if (activeChat != null) {
            return activeChat.workspaceEnv
        }
        return chatHistories.value.firstOrNull { it.workspace == workspacePath }?.workspaceEnv
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
        OKToast.warning(context, context.getString(R.string.chat_need_overlay_permission_start_voice_assistant)).show()
    }

    override fun onCleared() {
        speechSubmissionOrder.stop()
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

    private enum class TitleSummaryResult { UPDATED, EMPTY, SKIPPED, FAILED }
    private val activeTitleGenerations = mutableSetOf<String>()
    private val chatBatchController = ChatBatchController(
        scope = viewModelScope,
        process = ::processChatBatchItem,
        onFailure = { id, cause -> AppLogger.e(TAG, "批量操作对话失败：$id", cause) },
        onComplete = { summary ->
            val message = context.getString(R.string.chat_batch_result,
                summary.succeeded, summary.skipped, summary.failed)
            val kind = when {
                summary.failed > 0 -> OKToast.Kind.Error
                summary.skipped > 0 -> OKToast.Kind.Warning
                else -> OKToast.Kind.Success
            }
            OKToast.typed(context, message, kind).show()
        },
    )
    val chatBatchProgress = chatBatchController.progress

    fun runChatBatch(action: ChatBatchAction, ids: List<String>) {
        chatBatchController.start(action, ids)
    }

    private suspend fun processChatBatchItem(action: ChatBatchAction, chatId: String): ChatBatchItemResult {
        if (chatId in chatListMotion.deletions.value) return ChatBatchItemResult.SKIPPED
        return when (action) {
            ChatBatchAction.SUMMARIZE_TITLES -> when (generateChatTitle(chatId, notifyStarted = false)) {
                TitleSummaryResult.UPDATED -> ChatBatchItemResult.SUCCEEDED
                TitleSummaryResult.FAILED -> ChatBatchItemResult.FAILED
                TitleSummaryResult.EMPTY, TitleSummaryResult.SKIPPED -> ChatBatchItemResult.SKIPPED
            }
            ChatBatchAction.DELETE -> {
                // 复用单条删除的停止回复、切换当前聊天和最终锁定检查，成功后清理待发消息。
                when (chatHistoryDelegate.deleteChatHistoryAndAwait(chatId)) {
                    ChatDeletionResult.DELETED -> {
                        pendingMessageQueueStore.removeChat(chatId)
                        ChatBatchItemResult.SUCCEEDED
                    }
                    ChatDeletionResult.LOCKED, ChatDeletionResult.NOT_FOUND -> ChatBatchItemResult.SKIPPED
                    else -> ChatBatchItemResult.FAILED
                }
            }
            else -> withContext(Dispatchers.IO) {
                val manager = ChatHistoryManager.getInstance(context)
                if (manager.getChatTitle(chatId) == null) return@withContext ChatBatchItemResult.SKIPPED
                when (action) {
                    ChatBatchAction.ARCHIVE -> manager.updateChatArchived(chatId, true)
                    ChatBatchAction.UNARCHIVE -> manager.updateChatArchived(chatId, false)
                    ChatBatchAction.LOCK -> manager.updateChatLocked(chatId, true)
                    ChatBatchAction.UNLOCK -> manager.updateChatLocked(chatId, false)
                    else -> error("不支持的批量操作：$action")
                }
                ChatBatchItemResult.SUCCEEDED
            }
        }
    }

    fun summarizeChatTitle(chatId: String) {
        viewModelScope.launch {
            when (generateChatTitle(chatId, notifyStarted = true)) {
                TitleSummaryResult.EMPTY -> OKToast.warning(context,
                    context.getString(R.string.summarize_chat_title_empty)).show()
                TitleSummaryResult.FAILED -> OKToast.error(context,
                    context.getString(R.string.summarize_chat_title_failed)).show()
                else -> Unit
            }
        }
    }

    private suspend fun generateChatTitle(chatId: String, notifyStarted: Boolean): TitleSummaryResult {
        // 单条和批量共用占用标记，避免同一对话同时产生两个总结请求。
        if (!activeTitleGenerations.add(chatId)) return TitleSummaryResult.SKIPPED
        try {
            val manager = ChatHistoryManager.getInstance(context)
            val originalTitle = manager.getChatTitle(chatId) ?: return TitleSummaryResult.SKIPPED
            val source = manager.loadChatMessages(chatId).firstOrNull { message ->
                message.sender == "user" && message.displayContent().isNotBlank()
            } ?: return TitleSummaryResult.EMPTY
            if (notifyStarted) {
                OKToast.info(context, context.getString(R.string.summarize_chat_title_started)).show()
            }
            val generatedTitle = withContext(Dispatchers.IO) {
                EnhancedAIService.getChatInstance(context, chatId)
                    .generateConversationTitle(userText = source.displayContent().trim()).trim()
            }
            if (generatedTitle.isBlank()) return TitleSummaryResult.FAILED
            // 生成期间手动改名或删除对话时，不用旧请求覆盖用户操作。
            if (manager.getChatTitle(chatId) != originalTitle) return TitleSummaryResult.SKIPPED
            chatHistoryDelegate.updateChatTitleAndAwait(chatId, generatedTitle)
            return TitleSummaryResult.UPDATED
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            AppLogger.e(TAG, "手动总结对话标题失败", cause)
            return TitleSummaryResult.FAILED
        } finally {
            activeTitleGenerations.remove(chatId)
        }
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            uiStateDelegate.showErrorMessage(context.getString(R.string.chat_terminal_requires_android_8))
            return
        }
        val terminalInstance = terminal ?: return

        if (command.usesDedicatedSession) {
            executeBackgroundWorkspaceCommand(command, workspacePath, commandText)
            return
        }

        if (workspaceCommandExecutionJob?.isActive == true) {
            OKToast.warning(context, context.getString(R.string.workspace_command_already_running)).show()
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
                if (sharedSessionId == null || terminalInstance.terminalState.value.sessions.none { it.id == sharedSessionId }) {
                    val workspaceName = workspaceDir.name.take(4) // 只取前4位

                    sharedSessionId = terminalInstance.createSession("Workspace: $workspaceName")

                    // 保存会话 ID
                    workspaceTerminalSessions[workspacePath] = sharedSessionId

                    AppLogger.d(
                        TAG,
                        "Created new workspace terminal session $sharedSessionId for $workspacePath"
                    )
                }

                sessionId = sharedSessionId

                val activeSessionId = sessionId ?: return@launch

                terminalInstance.executeCommand(activeSessionId, "cd \"${workspaceDir.absolutePath}\"")

                _workspaceCommandExecutionState.value =
                    WorkspaceCommandExecutionState(
                        workspacePath = workspacePath,
                        commandLabel = command.label,
                        commandText = commandText,
                        sessionId = activeSessionId,
                        usesDedicatedSession = command.usesDedicatedSession
                    )

                terminalInstance.executeCommandFlow(activeSessionId, commandText).collect { event ->
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
                        notifyWorkspacePreviewRefresh(
                            workspacePath = workspacePath,
                            workspaceEnv = resolveWorkspaceEnvForPath(workspacePath),
                            affectedPaths = listOf(workspacePath),
                            source = "workspace_command:${command.id}"
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
        openTerminalFromHub()
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
        if (!currentState.isRunning || currentState.isCancelling || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        _workspaceCommandExecutionState.value = currentState.copy(isCancelling = true)
        terminal?.sendInterruptSignal(currentState.sessionId)
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
                } else {
                    val affectedPaths =
                        when (val data = result.result) {
                            is FileOperationData -> listOf(data.path)
                            else -> listOf(workspacePath)
                        }
                    notifyWorkspacePreviewRefresh(
                        workspacePath = workspacePath,
                        workspaceEnv = resolveWorkspaceEnvForPath(workspacePath),
                        affectedPaths = affectedPaths,
                        source = "workspace_tool:$toolName"
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

    /** 初始化语音服务 */
    private fun initializeVoiceService() {
        // 监听当前 TTS 档案的变化
        viewModelScope.launch {
            speechServiceProfiles.currentTtsProfileFlow.collect { profile ->
                try {
                    AppLogger.d(TAG, "TTS档案变化，重新初始化语音服务: profile=${profile.id} type=${profile.serviceType}")

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
        TtsServiceFactory.resetInstance()
        val currentVoiceService = TtsServiceFactory.getInstance(context)
        voiceService = currentVoiceService
        val initialized = currentVoiceService.initialize()
        if (initialized) {
            observeVoiceState(currentVoiceService)
        } else {
            _isPlaying.value = false
            _isSpeechSessionActive.value = false
            _isSpeechPaused.value = false
        }
        return initialized
    }

    private suspend fun ensureActiveVoiceService(): TtsService? {
        val latestVoiceService = TtsServiceFactory.getInstance(context)
        if (voiceService !== latestVoiceService) {
            AppLogger.d(TAG, "检测到TTS服务实例已切换，更新聊天页引用")
            voiceStateCollectionJob?.cancel()
            voiceService = latestVoiceService
            logSpeechState("voiceService.swap", "provider=${latestVoiceService.javaClass.simpleName}")
        }

        val currentVoiceService = voiceService ?: return null
        logSpeechState("voiceService.ensure", "provider=${currentVoiceService.javaClass.simpleName} initialized=${currentVoiceService.isInitialized}")
        if (!currentVoiceService.isInitialized) {
            val initialized = currentVoiceService.initialize()
            logSpeechState("voiceService.initializeResult", "provider=${currentVoiceService.javaClass.simpleName} initialized=$initialized")
            if (!initialized) {
                return null
            }
        }

        if (voiceStateCollectionJob?.isActive != true) {
            observeVoiceState(currentVoiceService)
        }
        return currentVoiceService
    }

    private fun cancelSpeechControlsHide(reason: String) {
        if (speechControlsHideJob?.isActive == true) {
            speechControlsHideJob?.cancel()
            logSpeechState("cancelHide", "reason=$reason")
        }
        speechControlsHideJob = null
    }

    private fun observeVoiceState(service: TtsService) {
        voiceStateCollectionJob?.cancel()
        cancelSpeechControlsHide("observeVoiceState.restart provider=${service::class.java.simpleName}")
        voiceStateCollectionJob = viewModelScope.launch {
            service.speakingStateFlow.collect { isSpeaking ->
                _isPlaying.value = isSpeaking
                logSpeechState(
                    event = "speakingStateFlow",
                    extra = "provider=${service::class.java.simpleName} emitted=$isSpeaking"
                )
                if (isSpeaking || !_isSpeechSessionActive.value || _isSpeechPaused.value || isAutoReadEnabled.value) {
                    cancelSpeechControlsHide(
                        "stateChanged provider=${service::class.java.simpleName} emitted=$isSpeaking"
                    )
                    return@collect
                }

                cancelSpeechControlsHide("reschedule provider=${service::class.java.simpleName}")
                logSpeechState("scheduleHide", "reason=idle provider=${service::class.java.simpleName} delayMs=3000")
                speechControlsHideJob = viewModelScope.launch {
                    delay(3000)
                    if (!_isPlaying.value && _isSpeechSessionActive.value && !_isSpeechPaused.value && !isAutoReadEnabled.value) {
                        _isSpeechSessionActive.value = false
                        logSpeechState("hideControls", "reason=idleTimeout provider=${service::class.java.simpleName}")
                    } else {
                        logSpeechState("skipHide", "reason=stateChangedDuringDelay provider=${service::class.java.simpleName}")
                    }
                }
            }
        }
    }

    /** 朗读消息内容 */
    fun speakMessage(message: String) {
        speakMessage(message, interrupt = true)
    }

    fun speakMessage(message: String, interrupt: Boolean) {
        val submission = speechSubmissionOrder.reserve(interrupt)
        if (interrupt) {
            speechPlaybackJob?.cancel()
            logSpeechState("cancelPlaybackJob", "reason=newInterruptingSpeak")
        }
        speechPlaybackJob = viewModelScope.launch {
            try {
                if (!submission.awaitTurn()) return@launch
                cancelSpeechControlsHide("newSpeak")
                val currentVoiceService = ensureActiveVoiceService()
                _isSpeechSessionActive.value = true
                if (interrupt) _isSpeechPaused.value = false
                logSpeechState(
                    "speakMessage.start",
                    "interrupt=$interrupt provider=${currentVoiceService?.javaClass?.simpleName} rawLen=${message.length} preview=\"${speechPreview(message)}\""
                )
                if (currentVoiceService == null) {
                    _isSpeechSessionActive.value = false
                    logSpeechState("speakMessage.abort", "reason=noVoiceService")
                    OKToast.error(context, context.getString(R.string.chat_voice_service_init_failed)).show()
                    AppLogger.e(TAG, "语音服务不可用")
                    return@launch
                }

                val cleanerRegexs = speechServiceProfiles.getCurrentTtsProfile().cleanerRegexs
                val cleanedText = TtsCleaner.clean(message, cleanerRegexs)
                val cleanMessage = WaifuMessageProcessor.cleanContentForWaifu(cleanedText)
                AppLogger.d(
                    TAG,
                    "speech[cleaned] rawLen=${message.length} cleanedLen=${cleanedText.length} finalLen=${cleanMessage.length} preview=\"${speechPreview(cleanMessage)}\""
                )

                if (cleanMessage.isBlank()) {
                    AppLogger.d(TAG, "朗读内容为空，跳过请求")
                    _isSpeechSessionActive.value = false
                    logSpeechState("speakMessage.abort", "reason=blankAfterClean")
                    return@launch
                }

                if (!submission.awaitTurn()) return@launch
                if (currentVoiceService is PrefetchingTtsService) {
                    // 整段文本一次交给云端队列；入队后立即放行后续流式分句，仍等待本段播放结果。
                    val success = coroutineScope {
                        val playback = async(start = CoroutineStart.UNDISPATCHED) {
                            currentVoiceService.speak(cleanMessage, interrupt, rate = null, pitch = null)
                        }
                        submission.markSubmitted()
                        playback.await()
                    }
                    if (!success && submission.failIfCurrent()) {
                        OKToast.error(context, context.getString(R.string.chat_speak_failed)).show()
                    }
                    return@launch
                }
                submission.markSubmitted()
                val segments = TtsSegmenter.split(cleanMessage)
                AppLogger.d(TAG, "speech[segments] count=${segments.size} lengths=${segments.joinToString(prefix = "[", postfix = "]") { it.length.toString() }}")
                var isFirstSegment = true
                for ((index, segment) in segments.withIndex()) {
                    if (_isSpeechPaused.value) {
                        logSpeechState("waitResume", "segmentIndex=$index")
                        isSpeechPaused.filter { !it }.first()
                        logSpeechState("resumeObserved", "segmentIndex=$index")
                    }

                    AppLogger.d(
                        TAG,
                        "speech[segmentSpeak] index=$index/${segments.lastIndex} interrupt=${if (isFirstSegment) interrupt else false} len=${segment.length} preview=\"${speechPreview(segment)}\""
                    )
                    val success = currentVoiceService.speak(
                        text = segment,
                        interrupt = if (isFirstSegment) interrupt else false,
                        rate = null,
                        pitch = null
                    )
                    AppLogger.d(TAG, "speech[segmentResult] index=$index success=$success provider=${currentVoiceService.javaClass.simpleName}")

                    if (!success) {
                        logSpeechState("segmentFailed", "index=$index")
                        OKToast.error(context, context.getString(R.string.chat_speak_failed)).show()
                        break
                    }
                    isFirstSegment = false
                }
            } catch (e: CancellationException) {
                logSpeechState(
                    "speakMessage.cancelled",
                    "paused=${_isSpeechPaused.value} session=${_isSpeechSessionActive.value} message=${e.message}"
                )
            } catch (e: Exception) {
                logSpeechState("speakMessage.exception", "type=${e::class.java.simpleName} message=${e.message}")
                AppLogger.e(TAG, "朗读消息失败", e)
                if (submission.failIfCurrent()) {
                    OKToast.error(context, context.getString(R.string.chat_speak_message_failed, e.message ?: "Unknown error")).show()
                }
            } finally {
                submission.markSubmitted()
            }
        }
    }

    /** 停止朗读 */
    fun stopSpeaking() {
        speechSubmissionOrder.stop()
        viewModelScope.launch {
            try {
                logSpeechState("stopSpeaking.request")
                cancelSpeechControlsHide("stopSpeaking")
                speechPlaybackJob?.cancel()
                speechPlaybackJob = null
                ensureActiveVoiceService()?.stop()
                _isSpeechPaused.value = false
                _isSpeechSessionActive.value = false
                logSpeechState("stopSpeaking.done")
            } catch (e: Exception) {
                logSpeechState("stopSpeaking.exception", "type=${e::class.java.simpleName} message=${e.message}")
                AppLogger.e(TAG, "停止朗读失败", e)
            }
        }
    }

    fun pauseSpeaking() {
        viewModelScope.launch {
            try {
                logSpeechState("pauseSpeaking.request")
                cancelSpeechControlsHide("pauseSpeaking")
                val service = ensureActiveVoiceService()
                if (service !is PrefetchingTtsService) {
                    speechPlaybackJob?.cancel()
                    speechPlaybackJob = null
                    logSpeechState("pauseSpeaking.cancelPlaybackJob")
                }
                val success = service?.pause() == true
                if (success) {
                    _isSpeechPaused.value = true
                    _isSpeechSessionActive.value = true
                    cancelSpeechControlsHide("pauseSpeaking.done")
                    logSpeechState("pauseSpeaking.done", "success=true")
                } else {
                    logSpeechState("pauseSpeaking.done", "success=false")
                }
            } catch (e: Exception) {
                logSpeechState("pauseSpeaking.exception", "type=${e::class.java.simpleName} message=${e.message}")
                AppLogger.e(TAG, "暂停朗读失败", e)
            }
        }
    }

    fun resumeSpeaking() {
        viewModelScope.launch {
            try {
                logSpeechState("resumeSpeaking.request")
                cancelSpeechControlsHide("resumeSpeaking")
                val success = ensureActiveVoiceService()?.resume() == true
                if (success) {
                    _isSpeechPaused.value = false
                    _isSpeechSessionActive.value = true
                    logSpeechState("resumeSpeaking.done", "success=true")
                } else {
                    logSpeechState("resumeSpeaking.done", "success=false")
                }
            } catch (e: Exception) {
                logSpeechState("resumeSpeaking.exception", "type=${e::class.java.simpleName} message=${e.message}")
                AppLogger.e(TAG, "继续朗读失败", e)
            }
        }
    }

    fun disableAutoRead() {
        if (isAutoReadEnabled.value) {
            AppLogger.d(TAG, "speech[disableAutoRead] current=true")
            apiConfigDelegate.toggleAutoRead() // This will set it to false
            stopSpeaking()
        }
    }

    fun enableAutoReadAndSpeak(content: String) {
        AppLogger.d(TAG, "speech[enableAutoReadAndSpeak] autoReadBefore=${isAutoReadEnabled.value} len=${content.length} preview=\"${speechPreview(content)}\"")
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

    fun enqueueSelectedMessagesForMemoryAutoSave(messages: List<ChatMessage>) {
        messageCoordinationDelegate.enqueueSelectedMessagesForMemoryAutoSave(messages)
    }

    suspend fun loadContextWindowBreakdown(chatId: String): ContextWindowBreakdown =
        withContext(Dispatchers.IO) {
            messageCoordinationDelegate.loadContextWindowBreakdown(chatId)
        }

    fun manuallySummarizeConversation() {
        messageCoordinationDelegate.manuallySummarizeConversation()
    }

}
