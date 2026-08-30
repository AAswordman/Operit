package com.ai.assistance.operit.services

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.ui.text.input.TextFieldValue
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatTurnOptions
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.core.ApiConfigDelegate
import com.ai.assistance.operit.services.core.AttachmentDelegate
import com.ai.assistance.operit.services.core.ChatSelectionMode
import com.ai.assistance.operit.services.core.ChatHistoryDelegate
import com.ai.assistance.operit.services.core.MessageCoordinationDelegate
import com.ai.assistance.operit.services.core.MessageProcessingDelegate
import com.ai.assistance.operit.services.core.TokenStatisticsDelegate
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.ui.features.chat.viewmodel.UiStateDelegate
import com.ai.assistance.operit.ui.features.chat.webview.workspace.process.WorkspaceChangeTracker
import com.ai.assistance.operit.util.stream.SharedStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 聊天服务核心类
 * 
 * 整合所有聊天业务逻辑，可被 FloatingChatService 或 ChatViewModel 使用
 * 生命周期独立于 ViewModel，绑定到传入的 CoroutineScope
 */
class ChatServiceCore(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val runtimeSlot: ChatRuntimeSlot,
    private val selectionMode: ChatSelectionMode = ChatSelectionMode.FOLLOW_GLOBAL
) {
    companion object {
        private const val TAG = "ChatServiceCore"
    }

    // EnhancedAIService 实例（全局单例）
    private var enhancedAiService: EnhancedAIService? = null

    // 委托实例
    private lateinit var messageProcessingDelegate: MessageProcessingDelegate
    private lateinit var chatHistoryDelegate: ChatHistoryDelegate
    private lateinit var apiConfigDelegate: ApiConfigDelegate
    private lateinit var tokenStatisticsDelegate: TokenStatisticsDelegate
    private lateinit var attachmentDelegate: AttachmentDelegate
    private lateinit var uiStateDelegate: UiStateDelegate
    private lateinit var messageCoordinationDelegate: MessageCoordinationDelegate

    // 初始化状态
    private var initialized = false

    // 回调：当 EnhancedAIService 初始化或更新时
    private var onEnhancedAiServiceReady: ((EnhancedAIService) -> Unit)? = null
    
    // 额外的 onTurnComplete 回调（用于悬浮窗通知应用等场景）
    private var additionalOnTurnComplete: ((String?, Long, Long, Long) -> Unit)? = null
    private var uiBridge: ChatServiceUiBridge = EmptyChatServiceUiBridge
    private val workspaceChangeTracker = WorkspaceChangeTracker.getInstance(context)
    private val workspaceTrackerOwnerId = "${selectionMode.name}@${System.identityHashCode(this)}"

    init {
        AppLogger.d(TAG, "ChatServiceCore 初始化")
        initializeDelegates()
    }
    
    private fun initializeDelegates() {
        // 初始化 UI 状态委托
        uiStateDelegate = UiStateDelegate()
        
        // 初始化 API 配置委托
        apiConfigDelegate = ApiConfigDelegate(
            context = context,
            coroutineScope = coroutineScope,
            onConfigChanged = { service ->
                enhancedAiService = service
                // 当服务初始化后，设置 token 统计收集器
                tokenStatisticsDelegate.setupCollectors()
                // 通知外部监听者
                onEnhancedAiServiceReady?.invoke(service)
                AppLogger.d(TAG, "EnhancedAIService 已更新")
            }
        )

        // 初始化 Token 统计委托
        tokenStatisticsDelegate = TokenStatisticsDelegate(
            coroutineScope = coroutineScope,
            getEnhancedAiService = { enhancedAiService }
        )

        // 初始化附件委托
        attachmentDelegate = AttachmentDelegate(
            context = context,
            toolHandler = AIToolHandler.getInstance(context)
        )

        // 初始化聊天历史委托
        chatHistoryDelegate = ChatHistoryDelegate(
            context = context,
            coroutineScope = coroutineScope,
            selectionMode = selectionMode,
            onTokenStatisticsLoaded = { chatId, inputTokens, outputTokens, windowSize ->
                tokenStatisticsDelegate.setActiveChatId(chatId)
                tokenStatisticsDelegate.setTokenCounts(chatId, inputTokens, outputTokens, windowSize)
            },
            getEnhancedAiService = { enhancedAiService },
            ensureAiServiceAvailable = {
                // 确保 AI 服务可用
                if (enhancedAiService == null) {
                    enhancedAiService = EnhancedAIService.getInstance(context)
                }
            },
            getChatStatistics = {
                val (inputTokens, outputTokens) = tokenStatisticsDelegate.getCumulativeTokenCounts()
                val windowSize = tokenStatisticsDelegate.getLastCurrentWindowSize()
                Triple(inputTokens, outputTokens, windowSize)
            },
            onScrollToBottom = {
                messageProcessingDelegate.scrollToBottom()
            }
        )

        coroutineScope.launch {
            chatHistoryDelegate.currentChatId.collect { chatId ->
                tokenStatisticsDelegate.setActiveChatId(chatId)
                if (::messageProcessingDelegate.isInitialized) {
                    messageProcessingDelegate.setActiveDraftChat(chatId)
                }
                if (chatId != null) {
                    tokenStatisticsDelegate.bindChatService(
                        chatId,
                        EnhancedAIService.getChatInstance(context, chatId)
                    )
                }
            }
        }

        coroutineScope.launch {
            combine(
                chatHistoryDelegate.currentChatId,
                chatHistoryDelegate.chatHistories
            ) { chatId, histories ->
                histories.firstOrNull { it.id == chatId }
            }.collect { chat ->
                workspaceChangeTracker.updateOwner(
                    ownerId = workspaceTrackerOwnerId,
                    chatId = chat?.id,
                    workspacePath = chat?.workspace,
                    workspaceEnv = chat?.workspaceEnv
                )
            }
        }

        // 初始化消息处理委托
        messageProcessingDelegate = MessageProcessingDelegate(
            context = context,
            coroutineScope = coroutineScope,
            runtimeSlot = runtimeSlot,
            getEnhancedAiService = { enhancedAiService },
            getFullChatHistory = { chatId -> chatHistoryDelegate.getChatHistory(chatId) },
            getRuntimeChatHistory = { chatId -> chatHistoryDelegate.getRuntimeChatHistory(chatId) },
            hasUserMessage = { chatId -> chatHistoryDelegate.hasUserMessage(chatId) },
            addMessageToChat = { chatId, message ->
                chatHistoryDelegate.addMessageToChat(message, chatId)
            },
            saveCurrentChat = {
                val (inputTokens, outputTokens) = tokenStatisticsDelegate.getCumulativeTokenCounts()
                val windowSize = tokenStatisticsDelegate.getLastCurrentWindowSize()
                chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, windowSize)
            },
            showErrorMessage = { error ->
                AppLogger.e(TAG, "错误: $error")
                // 错误消息可以通过回调传递给 UI
            },
            updateChatTitle = { chatId, title ->
                chatHistoryDelegate.updateChatTitle(chatId, title)
            },
            getChatTitle = { chatId ->
                chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == chatId }?.title
            },
            onTurnComplete = { chatId, service, nextWindowSize, turnOptions ->
                tokenStatisticsDelegate.updateCumulativeStatistics(chatId, service)
                val (inputTokens, outputTokens) = tokenStatisticsDelegate.getCumulativeTokenCounts(chatId)
                val windowSize = nextWindowSize ?: tokenStatisticsDelegate.getLastCurrentWindowSize(chatId)
                tokenStatisticsDelegate.setTokenCounts(chatId, inputTokens, outputTokens, windowSize)
                if (turnOptions.persistTurn) {
                    chatHistoryDelegate.saveCurrentChat(
                        inputTokens,
                        outputTokens,
                        windowSize,
                        chatIdOverride = chatId
                    )
                }
                additionalOnTurnComplete?.invoke(
                    chatId,
                    inputTokens,
                    outputTokens,
                    windowSize
                )
            },
            getIsAutoReadEnabled = {
                apiConfigDelegate.enableAutoRead.value
            },
            speakMessageHandler = { text, _ ->
                AppLogger.d(TAG, "朗读消息: $text")
            },
            onTokenLimitExceeded = { chatId, roleCardId, isGroupOrchestrationTurn, groupParticipantNamesText ->
                messageCoordinationDelegate.handleTokenLimitExceeded(
                    chatId = chatId,
                    roleCardId = roleCardId,
                    isGroupOrchestrationTurn = isGroupOrchestrationTurn,
                    groupParticipantNamesText = groupParticipantNamesText
                )
            }
        )
        messageProcessingDelegate.setActiveDraftChat(chatHistoryDelegate.currentChatId.value)

        // 初始化消息协调委托
        messageCoordinationDelegate = MessageCoordinationDelegate(
            context = context,
            coroutineScope = coroutineScope,
            chatHistoryDelegate = chatHistoryDelegate,
            messageProcessingDelegate = messageProcessingDelegate,
            tokenStatsDelegate = tokenStatisticsDelegate,
            apiConfigDelegate = apiConfigDelegate,
            attachmentDelegate = attachmentDelegate,
            uiStateDelegate = uiStateDelegate,
            getEnhancedAiService = { enhancedAiService },
            uiBridge = uiBridge
        )

        chatHistoryDelegate.setBeforeDestructiveHistoryMutation { chatId ->
            messageCoordinationDelegate.cancelSummaryForDestructiveMutation(chatId)
            messageProcessingDelegate.cancelMessageForDestructiveMutation(chatId)
        }
        chatHistoryDelegate.setAfterDestructiveHistoryMutation { chatId ->
            messageCoordinationDelegate.refreshStableContextWindow(chatId = chatId)
        }

        initialized = true
        AppLogger.d(TAG, "所有委托已初始化")
    }

    // ========== 消息处理相关 ==========

    /** 发送用户消息（使用 MessageCoordinationDelegate，包含总结逻辑） */
    fun sendUserMessage(
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        roleCardIdOverride: String? = null,
        chatIdOverride: String? = null,
        messageTextOverride: String? = null,
        proxySenderNameOverride: String? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        turnOptions: ChatTurnOptions = ChatTurnOptions(),
        preferActiveRoleCard: Boolean = false,
    ) {
        messageCoordinationDelegate.sendUserMessage(
            promptFunctionType = promptFunctionType,
            roleCardIdOverride = roleCardIdOverride,
            preferActiveRoleCard = preferActiveRoleCard,
            chatIdOverride = chatIdOverride,
            messageTextOverride = messageTextOverride,
            proxySenderNameOverride = proxySenderNameOverride,
            chatModelConfigIdOverride = chatModelConfigIdOverride,
            chatModelIndexOverride = chatModelIndexOverride,
            turnOptions = turnOptions
        )
    }

    /** 取消当前消息 */
    fun cancelCurrentMessage() {
        // 先取消总结（如果正在进行）
        messageCoordinationDelegate.cancelSummary()
        // 然后取消“当前聊天”的消息处理
        val chatId = chatHistoryDelegate.currentChatId.value
        if (chatId != null) {
            messageProcessingDelegate.cancelMessage(chatId)
        }
    }

    fun cancelMessage(chatId: String) {
        messageCoordinationDelegate.cancelSummaryForChat(chatId)
        messageProcessingDelegate.cancelMessage(chatId)
    }

    suspend fun cancelMessageForDestructiveMutation(chatId: String) {
        messageCoordinationDelegate.cancelSummaryForDestructiveMutation(chatId)
        messageProcessingDelegate.cancelMessageForDestructiveMutation(chatId)
    }

    /** 更新用户消息 */
    fun updateUserMessage(message: String) {
        messageProcessingDelegate.updateUserMessage(message)
    }

    fun getResponseStream(chatId: String): SharedStream<String>? {
        return messageProcessingDelegate.getResponseStream(chatId)
    }

    // ========== 聊天历史相关 ==========

    /** 创建新的聊天 */
    fun createNewChat(
        characterCardName: String? = null,
        group: String? = null,
        inheritGroupFromCurrent: Boolean = true,
        setAsCurrentChat: Boolean = true,
        characterCardId: String? = null
    ) {
        chatHistoryDelegate.createNewChat(
            characterCardName = characterCardName,
            group = group,
            inheritGroupFromCurrent = inheritGroupFromCurrent,
            setAsCurrentChat = setAsCurrentChat,
            characterCardId = characterCardId
        )
    }

    /** 切换聊天 */
    fun switchChat(chatId: String) {
        chatHistoryDelegate.switchChat(chatId)
    }

    /**
     * 切换聊天（仅切换本地状态，不写回全局 currentChatId）。
     * 悬浮窗可用此方法在窗口内切换会话，但不影响主界面。
     */
    fun switchChatLocal(chatId: String) {
        chatHistoryDelegate.switchChat(chatId, syncToGlobal = false)
    }

    /**
     * 将当前本地 chatId 写回全局 currentChatId，用于“返回主应用”时同步。
     */
    fun syncCurrentChatIdToGlobal() {
        val chatId = chatHistoryDelegate.currentChatId.value ?: return
        chatHistoryDelegate.switchChat(chatId, syncToGlobal = true)
    }

    /** 删除聊天历史 */
    fun deleteChatHistory(chatId: String) {
        chatHistoryDelegate.deleteChatHistory(chatId)
    }

    /** 删除消息 */
    fun deleteMessage(index: Int) {
        chatHistoryDelegate.deleteMessage(index)
    }

    /** 清空当前聊天 */
    fun clearCurrentChat() {
        chatHistoryDelegate.clearCurrentChat()
    }

    /** 更新聊天标题 */
    fun updateChatTitle(chatId: String, title: String) {
        chatHistoryDelegate.updateChatTitle(chatId, title)
    }

    // ========== Token 统计相关 ==========

    /** 重置 token 统计 */
    fun resetTokenStatistics() {
        tokenStatisticsDelegate.resetTokenStatistics()
    }

    /** 更新累计统计 */
    fun updateCumulativeStatistics() {
        tokenStatisticsDelegate.updateCumulativeStatistics()
    }

    // ========== 附件管理相关 ==========

    /** 获取 AttachmentDelegate 实例 */
    fun getAttachmentDelegate(): AttachmentDelegate = attachmentDelegate

    /** 添加附件 */
    suspend fun handleAttachment(filePath: String) {
        attachmentDelegate.handleAttachment(filePath)
    }

    /** 移除附件 */
    fun removeAttachment(filePath: String) {
        attachmentDelegate.removeAttachment(filePath)
    }

    /** 清空所有附件 */
    fun clearAttachments() {
        attachmentDelegate.clearAttachments()
    }

    // ========== StateFlow 暴露 ==========

    // 消息处理相关
    val userMessage: StateFlow<TextFieldValue>
        get() = messageProcessingDelegate.userMessage

    val isLoading: StateFlow<Boolean>
        get() = messageProcessingDelegate.isLoading

    val activeStreamingChatIds: StateFlow<Set<String>>
        get() = messageProcessingDelegate.activeStreamingChatIds

    val inputProcessingStateByChatId: StateFlow<Map<String, InputProcessingState>>
        get() = messageProcessingDelegate.inputProcessingStateByChatId

    val userDraftStateByChatId: StateFlow<Map<String, Boolean>>
        get() = messageProcessingDelegate.userDraftStateByChatId

    val currentTurnToolInvocationCountByChatId: StateFlow<Map<String, Int>>
        get() = messageProcessingDelegate.currentTurnToolInvocationCountByChatId

    val scrollToBottomEvent: SharedFlow<Unit>
        get() = messageProcessingDelegate.scrollToBottomEvent

    val nonFatalErrorEvent: SharedFlow<String>
        get() = messageProcessingDelegate.nonFatalErrorEvent

    val isSummarizing: StateFlow<Boolean>
        get() = messageCoordinationDelegate.isSummarizing

    // 聊天历史相关
    val chatHistory: StateFlow<List<ChatMessage>>
        get() = chatHistoryDelegate.chatHistory

    val currentChatId: StateFlow<String?>
        get() = chatHistoryDelegate.currentChatId

    val currentChatHasOlderDisplayHistory: StateFlow<Boolean>
        get() = chatHistoryDelegate.hasOlderDisplayHistory

    val currentChatHasNewerDisplayHistory: StateFlow<Boolean>
        get() = chatHistoryDelegate.hasNewerDisplayHistory

    val currentChatIsLoadingDisplayWindow: StateFlow<Boolean>
        get() = chatHistoryDelegate.isLoadingDisplayWindow

    val chatHistories: StateFlow<List<com.ai.assistance.operit.data.model.ChatHistory>>
        get() = chatHistoryDelegate.chatHistories

    val showChatHistorySelector: StateFlow<Boolean>
        get() = chatHistoryDelegate.showChatHistorySelector

    // API 配置相关
    val enableThinkingMode: StateFlow<Boolean>
        get() = apiConfigDelegate.enableThinkingMode

    val enableMemoryAutoUpdate: StateFlow<Boolean>
        get() = apiConfigDelegate.enableMemoryAutoUpdate

    val enableAutoRead: StateFlow<Boolean>
        get() = apiConfigDelegate.enableAutoRead

    val contextLength: StateFlow<Float>
        get() = apiConfigDelegate.effectiveContextLength

    val summaryTokenThreshold: StateFlow<Float>
        get() = apiConfigDelegate.effectiveSummaryTokenThreshold

    val enableSummary: StateFlow<Boolean>
        get() = apiConfigDelegate.effectiveEnableSummary

    val enableTools: StateFlow<Boolean>
        get() = apiConfigDelegate.enableTools

    // Token 统计相关
    val cumulativeInputTokensFlow: StateFlow<Long>
        get() = tokenStatisticsDelegate.cumulativeInputTokensFlow

    val cumulativeOutputTokensFlow: StateFlow<Long>
        get() = tokenStatisticsDelegate.cumulativeOutputTokensFlow

    val currentWindowSizeFlow: StateFlow<Long>
        get() = tokenStatisticsDelegate.currentWindowSizeFlow

    val perRequestTokenCountFlow: StateFlow<Pair<Long, Long>?>
        get() = tokenStatisticsDelegate.perRequestTokenCountFlow

    // 附件相关
    val attachments: StateFlow<List<AttachmentInfo>>
        get() = attachmentDelegate.attachments

    val attachmentToastEvent: SharedFlow<String>
        get() = attachmentDelegate.toastEvent

    // ========== 其他方法 ==========

    /** 获取 UiStateDelegate 实例 */
    fun getUiStateDelegate(): UiStateDelegate = uiStateDelegate

    fun getApiConfigDelegate(): ApiConfigDelegate = apiConfigDelegate

    fun getTokenStatisticsDelegate(): TokenStatisticsDelegate = tokenStatisticsDelegate

    fun getChatHistoryDelegate(): ChatHistoryDelegate = chatHistoryDelegate

    fun getMessageProcessingDelegate(): MessageProcessingDelegate = messageProcessingDelegate

    fun getMessageCoordinationDelegate(): MessageCoordinationDelegate = messageCoordinationDelegate

    /** 获取 EnhancedAIService 实例 */
    fun getEnhancedAiService(): EnhancedAIService? = enhancedAiService

    /** 检查是否已初始化 */
    fun isInitialized(): Boolean = initialized
    
    /** 设置 EnhancedAIService 就绪回调 */
    fun setOnEnhancedAiServiceReady(callback: (EnhancedAIService) -> Unit) {
        onEnhancedAiServiceReady = callback
        // 如果已经初始化，立即调用回调
        enhancedAiService?.let { callback(it) }
    }
    
    /** 设置额外的 onTurnComplete 回调（用于悬浮窗通知应用等场景） */
    fun setAdditionalOnTurnComplete(callback: ((chatId: String?, inputTokens: Long, outputTokens: Long, windowSize: Long) -> Unit)?) {
        additionalOnTurnComplete = callback
    }

    fun setUiBridge(uiBridge: ChatServiceUiBridge) {
        this.uiBridge = uiBridge
        if (::messageCoordinationDelegate.isInitialized) {
            messageCoordinationDelegate.setUiBridge(uiBridge)
        }
    }

    fun setSpeakMessageHandler(handler: (String, Boolean) -> Unit) {
        if (::messageProcessingDelegate.isInitialized) {
            messageProcessingDelegate.setSpeakMessageHandler(handler)
        }
    }
    
    /** 重新加载聊天消息（智能合并） */
    suspend fun reloadChatMessagesSmart(chatId: String) {
        chatHistoryDelegate.reloadChatMessagesSmart(chatId)
    }

    fun loadOlderMessagesForCurrentChat() {
        coroutineScope.launch {
            chatHistoryDelegate.loadOlderMessagesForCurrentChat()
        }
    }

    fun loadNewerMessagesForCurrentChat() {
        coroutineScope.launch {
            chatHistoryDelegate.loadNewerMessagesForCurrentChat()
        }
    }

    fun showLatestMessagesForCurrentChat() {
        coroutineScope.launch {
            chatHistoryDelegate.showLatestMessagesForCurrentChat()
        }
    }
}
