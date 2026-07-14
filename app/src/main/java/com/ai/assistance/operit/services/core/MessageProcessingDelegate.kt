package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.agent.PhoneAgentJobRegistry
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.model.InputProcessingState as EnhancedInputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import com.ai.assistance.operit.util.TtsSegmenter
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceBackupManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.assistance.operit.core.tools.ToolProgressBus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** 委托类，负责处理消息处理相关功能 */
class MessageProcessingDelegate(
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val getFullChatHistory: suspend (String) -> List<ChatMessage>,
        private val getRuntimeChatHistory: suspend (String) -> List<ChatMessage>,
        private val hasUserMessage: suspend (String) -> Boolean,
        private val addMessageToChat: suspend (String, ChatMessage) -> Unit,
        private val saveCurrentChat: suspend () -> Unit,
        private val showErrorMessage: (String) -> Unit,
        private val updateChatTitle: (chatId: String, title: String) -> Unit,
        private val getChatTitle: (chatId: String) -> String?,
        private val onTurnComplete:
            suspend (chatId: String?, service: EnhancedAIService, nextWindowSize: Int?, turnOptions: ChatTurnOptions) -> Unit,
        private val onTokenLimitExceeded: suspend (
            chatId: String?,
            roleCardId: String?,
            isGroupOrchestrationTurn: Boolean,
            groupParticipantNamesText: String?
        ) -> Unit,
        // 添加自动朗读相关的回调
        private val getIsAutoReadEnabled: () -> Boolean,
        private var speakMessageHandler: (String, Boolean) -> Unit
) {
    companion object {
        private const val TAG = "MessageProcessingDelegate"
        private const val STREAM_SCROLL_THROTTLE_MS = 200L
        private const val STREAM_PERSIST_INTERVAL_MS = 1000L
        private const val AUTO_READ_PREVIEW_MAX = 48
    }



    private fun fallbackConversationTitle(userText: String, attachments: List<AttachmentInfo>): String {
        return attachments.firstOrNull()?.fileName?.trim()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.new_conversation)
    }

    private fun launchConversationTitleGeneration(
        chatId: String,
        userText: String,
        attachments: List<AttachmentInfo>,
        fallbackTitle: String
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val generatedTitle = EnhancedAIService.getChatInstance(context, chatId)
                    .generateConversationTitle(
                        userText = userText,
                        attachmentFileNames = attachments.map { it.fileName }
                    )
                    .trim()
                if (generatedTitle.isNotBlank() && getChatTitle(chatId) == fallbackTitle) {
                    updateChatTitle(chatId, generatedTitle)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "生成对话标题失败", e)
            }
        }
    }

    private fun speechPreview(text: String): String {
        return text.replace("\n", "\\n").take(AUTO_READ_PREVIEW_MAX)
    }

    // 角色卡管理器
    private val characterCardManager = CharacterCardManager.getInstance(context)
    
    // 模型配置管理器
    private val modelConfigManager = ModelConfigManager(context)
    
    // 功能配置管理器，用于获取正确的模型配置ID
    private val functionalConfigManager = FunctionalConfigManager(context)

    private val _userMessage = MutableStateFlow(TextFieldValue(""))
    val userMessage: StateFlow<TextFieldValue> = _userMessage.asStateFlow()
    private val userMessageDraftsByChatId = ConcurrentHashMap<String, TextFieldValue>()
    @Volatile
    private var activeDraftChatId: String? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activeStreamingChatIds = MutableStateFlow<Set<String>>(emptySet())
    val activeStreamingChatIds: StateFlow<Set<String>> = _activeStreamingChatIds.asStateFlow()

    private val _inputProcessingStateByChatId =
        MutableStateFlow<Map<String, EnhancedInputProcessingState>>(emptyMap())
    val inputProcessingStateByChatId: StateFlow<Map<String, EnhancedInputProcessingState>> =
        _inputProcessingStateByChatId.asStateFlow()

    private val _scrollToBottomEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottomEvent = _scrollToBottomEvent.asSharedFlow()

    private val _nonFatalErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val nonFatalErrorEvent = _nonFatalErrorEvent.asSharedFlow()

    private val _turnCompleteCounterByChatId = MutableStateFlow<Map<String, Long>>(emptyMap())
    val turnCompleteCounterByChatId: StateFlow<Map<String, Long>> =
        _turnCompleteCounterByChatId.asStateFlow()
    private val _currentTurnToolInvocationCountByChatId =
        MutableStateFlow<Map<String, Int>>(emptyMap())
    val currentTurnToolInvocationCountByChatId: StateFlow<Map<String, Int>> =
        _currentTurnToolInvocationCountByChatId.asStateFlow()

    // 当前活跃的AI响应流
    private data class ChatRuntime(
        var sendJob: Job? = null,
        var responseStream: SharedStream<String>? = null,
        var streamCollectionJob: Job? = null,
        var stateCollectionJob: Job? = null,
        var currentTurnOptions: ChatTurnOptions = ChatTurnOptions(),
        var requestSentAt: Long = 0L,
        var requestStartElapsed: Long = 0L,
        var firstResponseElapsed: Long? = null,
        val isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    )

    private val chatRuntimes = ConcurrentHashMap<String, ChatRuntime>()
    private val lastScrollEmitMsByChatKey = ConcurrentHashMap<String, AtomicLong>()
    private val suppressIdleCompletedStateByChatId = ConcurrentHashMap<String, Boolean>()
    private val pendingAsyncSummaryUiByChatId = ConcurrentHashMap<String, Boolean>()

    private fun chatKey(chatId: String?): String = chatId ?: "__DEFAULT_CHAT__"

    private fun tryEmitScrollToBottomThrottled(chatId: String?) {
        val key = chatKey(chatId)
        val now = System.currentTimeMillis()
        val last = lastScrollEmitMsByChatKey.getOrPut(key) { AtomicLong(0L) }
        val prev = last.get()
        if (now - prev >= STREAM_SCROLL_THROTTLE_MS && last.compareAndSet(prev, now)) {
            _scrollToBottomEvent.tryEmit(Unit)
        }
    }

    private fun forceEmitScrollToBottom(chatId: String?) {
        val key = chatKey(chatId)
        lastScrollEmitMsByChatKey.getOrPut(key) { AtomicLong(0L) }.set(System.currentTimeMillis())
        _scrollToBottomEvent.tryEmit(Unit)
    }

    private fun runtimeFor(chatId: String?): ChatRuntime {
        val key = chatKey(chatId)
        return chatRuntimes[key] ?: ChatRuntime().also { chatRuntimes[key] = it }
    }

    private fun updateGlobalLoadingState() {
        val anyLoading = chatRuntimes.values.any { it.isLoading.value }
        val activeChatIds = chatRuntimes
            .filter { (_, runtime) -> runtime.isLoading.value }
            .keys
            .filter { it != "__DEFAULT_CHAT__" }
            .toSet()

        _activeStreamingChatIds.value = activeChatIds
        _isLoading.value = anyLoading
    }

    private fun isTerminalInputState(state: EnhancedInputProcessingState): Boolean {
        return state is EnhancedInputProcessingState.Idle ||
            state is EnhancedInputProcessingState.Completed
    }

    private fun setChatInputProcessingState(chatId: String?, state: EnhancedInputProcessingState) {
        if (chatId != null &&
            runtimeFor(chatId).isLoading.value &&
            isTerminalInputState(state)
        ) {
            return
        }
        if (chatId != null && suppressIdleCompletedStateByChatId.containsKey(chatId)) {
            if (isTerminalInputState(state)) {
                return
            }
        }
        if (state !is EnhancedInputProcessingState.ExecutingTool &&
            state !is EnhancedInputProcessingState.Summarizing
        ) {
            ToolProgressBus.clear()
        }
        val key = chatKey(chatId)
        val map = _inputProcessingStateByChatId.value.toMutableMap()
        map[key] = state
        _inputProcessingStateByChatId.value = map
    }

    fun setSuppressIdleCompletedStateForChat(chatId: String, suppress: Boolean) {
        if (suppress) {
            suppressIdleCompletedStateByChatId[chatId] = true
        } else {
            suppressIdleCompletedStateByChatId.remove(chatId)
        }
    }

    fun setPendingAsyncSummaryUiForChat(chatId: String, pending: Boolean) {
        if (pending) {
            pendingAsyncSummaryUiByChatId[chatId] = true
        } else {
            pendingAsyncSummaryUiByChatId.remove(chatId)
        }
    }

    fun setInputProcessingStateForChat(chatId: String, state: EnhancedInputProcessingState) {
        setChatInputProcessingState(chatId, state)
    }

    suspend fun buildUserMessageContentForGroupOrchestration(
        messageText: String,
        attachments: List<AttachmentInfo>,
        workspacePath: String?,
        workspaceEnv: String?,
        replyToMessage: ChatMessage?,
        chatId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val totalStartTime = messageTimingNow()
        val configId = functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
        val currentModelConfig = modelConfigManager.getModelConfigFlow(configId).first()
        val enableDirectImageProcessing = currentModelConfig.enableDirectImageProcessing
        val enableDirectAudioProcessing = currentModelConfig.enableDirectAudioProcessing
        val enableDirectVideoProcessing = currentModelConfig.enableDirectVideoProcessing

        val finalMessageContent = AIMessageManager.buildUserMessageContent(
            context = context,
            messageText = messageText,
            attachments = attachments,
            workspacePath = workspacePath,
            workspaceEnv = workspaceEnv,
            replyToMessage = replyToMessage,
            enableDirectImageProcessing = enableDirectImageProcessing,
            enableDirectAudioProcessing = enableDirectAudioProcessing,
            enableDirectVideoProcessing = enableDirectVideoProcessing,
            chatId = chatId
        )
        logMessageTiming(
            stage = "delegate.groupOrchestration.buildUserMessageContent",
            startTimeMs = totalStartTime,
            details = "attachments=${attachments.size}, configId=$configId, finalLength=${finalMessageContent.length}"
        )
        finalMessageContent
    }

    fun getResponseStream(chatId: String): SharedStream<String>? {
        return chatRuntimes[chatKey(chatId)]?.responseStream
    }

    private fun resolveFinalContent(aiMessage: ChatMessage): String {
        val sharedStream = aiMessage.contentStream as? SharedStream<String>
        val replayChunks = sharedStream?.replayCache
        val eventCarrier = aiMessage.contentStream as? TextStreamEventCarrier

        return if (eventCarrier?.eventChannel?.replayCache?.isNotEmpty() == true) {
            aiMessage.content
        } else if (!replayChunks.isNullOrEmpty()) {
            replayChunks.joinToString(separator = "")
        } else {
            aiMessage.content
        }
    }

    private fun ChatMessage.withTurnMetrics(
        inputTokens: Int,
        outputTokens: Int,
        cachedInputTokens: Int,
        sentAt: Long,
        outputDurationMs: Long,
        waitDurationMs: Long
    ): ChatMessage {
        return copy(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            sentAt = sentAt,
            outputDurationMs = outputDurationMs,
            waitDurationMs = waitDurationMs
        )
    }

    private data class TurnCancellationSnapshot(
        val inputTokens: Int,
        val outputTokens: Int,
        val cachedInputTokens: Int,
        val sentAt: Long,
        val outputDurationMs: Long,
        val waitDurationMs: Long,
    )

    private fun readCurrentTurnCancellationSnapshot(chatId: String): TurnCancellationSnapshot? {
        val service =
            EnhancedAIService.getChatInstance(context, chatId)
                ?: getEnhancedAiService()
                ?: return null
        val runtime = runtimeFor(chatId)
        return runCatching {
            val snapshot = service.captureCurrentTurnTokenSnapshot()
            val sentAt = runtime.requestSentAt
            val firstResponseElapsed = runtime.firstResponseElapsed
            val waitDurationMs =
                if (runtime.requestStartElapsed > 0L && firstResponseElapsed != null) {
                    (firstResponseElapsed - runtime.requestStartElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }
            val outputDurationMs =
                if (firstResponseElapsed != null) {
                    (messageTimingNow() - firstResponseElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }
            TurnCancellationSnapshot(
                inputTokens = snapshot.inputTokens,
                outputTokens = snapshot.outputTokens,
                cachedInputTokens = snapshot.cachedInputTokens,
                sentAt = sentAt,
                outputDurationMs = outputDurationMs,
                waitDurationMs = waitDurationMs,
            )
        }.onFailure {
            AppLogger.w(TAG, "读取取消请求的统计快照失败", it)
        }.getOrNull()
    }

    private suspend fun detachStreamingAiMessage(
        chatId: String,
        snapshot: TurnCancellationSnapshot? = null,
    ) {
        val messages = getRuntimeChatHistory(chatId)
        val streamingMessage =
            messages.lastOrNull { it.sender == "ai" && it.contentStream != null }
                ?: return
        val finalContent = resolveFinalContent(streamingMessage)
        streamingMessage.content = finalContent
        val completedAt = System.currentTimeMillis()
        val finalMessage =
            snapshot?.let { stats ->
                streamingMessage.withTurnMetrics(
                    inputTokens = stats.inputTokens,
                    outputTokens = stats.outputTokens,
                    cachedInputTokens = stats.cachedInputTokens,
                    sentAt = stats.sentAt.takeIf { it > 0L } ?: streamingMessage.sentAt,
                    outputDurationMs = stats.outputDurationMs,
                    waitDurationMs = stats.waitDurationMs,
                )
            }?.copy(content = finalContent, contentStream = null, completedAt = completedAt)
                ?: streamingMessage.copy(
                    content = finalContent,
                    contentStream = null,
                    completedAt = completedAt,
                )
        withContext(Dispatchers.Main) {
            snapshot?.let { stats ->
                val matchingUserMessage =
                    messages.lastOrNull { message ->
                        message.sender == "user" &&
                            message.sentAt == (stats.sentAt.takeIf { it > 0L } ?: streamingMessage.sentAt)
                    }
                if (matchingUserMessage != null) {
                    addMessageToChat(
                        chatId,
                        matchingUserMessage.withTurnMetrics(
                            inputTokens = stats.inputTokens,
                            outputTokens = stats.outputTokens,
                            cachedInputTokens = stats.cachedInputTokens,
                            sentAt = stats.sentAt.takeIf { it > 0L } ?: matchingUserMessage.sentAt,
                            outputDurationMs = stats.outputDurationMs,
                            waitDurationMs = stats.waitDurationMs,
                        ),
                    )
                }
            }
            addMessageToChat(chatId, finalMessage)
        }
    }

    private suspend fun cancelMessageInternal(chatId: String, keepPartialResponse: Boolean) {
        val chatRuntime = runtimeFor(chatId)
        val currentTurnOptions = chatRuntime.currentTurnOptions
        val cancellationSnapshot =
            if (keepPartialResponse) readCurrentTurnCancellationSnapshot(chatId) else null
        val jobsToCancel =
            linkedSetOf<Job>().apply {
                chatRuntime.sendJob?.let { add(it) }
                chatRuntime.stateCollectionJob?.let { add(it) }
                chatRuntime.streamCollectionJob?.let { add(it) }
            }

        clearCurrentTurnToolInvocationCount(chatId)
        AIMessageManager.cancelOperation(chatId)

        jobsToCancel.forEach { job -> job.cancel() }
        jobsToCancel.forEach { job ->
            try {
                job.join()
            } catch (_: kotlinx.coroutines.CancellationException) {
            }
        }

        chatRuntime.sendJob = null
        chatRuntime.stateCollectionJob = null
        chatRuntime.streamCollectionJob = null

        if (keepPartialResponse) {
            detachStreamingAiMessage(chatId, snapshot = cancellationSnapshot)
        }

        chatRuntime.responseStream = null
        chatRuntime.isLoading.value = false
        chatRuntime.currentTurnOptions = ChatTurnOptions()
        chatRuntime.requestSentAt = 0L
        chatRuntime.requestStartElapsed = 0L
        chatRuntime.firstResponseElapsed = null
        updateGlobalLoadingState()
        setChatInputProcessingState(chatId, EnhancedInputProcessingState.Idle)

        if (currentTurnOptions.persistTurn) {
            withContext(Dispatchers.IO) { saveCurrentChat() }
        }
    }

    fun cancelMessage(chatId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            cancelMessageInternal(chatId, keepPartialResponse = true)
        }
    }

    suspend fun cancelMessageForDestructiveMutation(chatId: String) {
        cancelMessageInternal(chatId, keepPartialResponse = false)
    }

    init {
        AppLogger.d(TAG, "MessageProcessingDelegate初始化: 创建滚动事件流")
    }

    fun setActiveDraftChat(chatId: String?) {
        val previousChatId = activeDraftChatId
        if (previousChatId == chatId) {
            return
        }

        val currentValue = _userMessage.value
        if (previousChatId != null) {
            saveUserMessageDraft(previousChatId, currentValue)
        }

        activeDraftChatId = chatId
        if (chatId == null) {
            _userMessage.value = TextFieldValue("")
            return
        }

        val savedDraft = userMessageDraftsByChatId[chatId]
        if (savedDraft != null) {
            _userMessage.value = savedDraft
            return
        }

        if (previousChatId == null && currentValue.text.isNotEmpty()) {
            saveUserMessageDraft(chatId, currentValue)
            _userMessage.value = currentValue
            return
        }

        _userMessage.value = TextFieldValue("")
    }

    fun updateUserMessage(message: String) {
        setUserMessageDraft(TextFieldValue(message))
    }

    fun updateUserMessage(value: TextFieldValue) {
        setUserMessageDraft(value)
    }

    private fun setUserMessageDraft(value: TextFieldValue) {
        _userMessage.value = value
        val chatId = activeDraftChatId
        if (chatId != null) {
            saveUserMessageDraft(chatId, value)
        }
    }

    private fun saveUserMessageDraft(chatId: String, value: TextFieldValue) {
        if (value.text.isEmpty()) {
            userMessageDraftsByChatId.remove(chatId)
            return
        }

        userMessageDraftsByChatId[chatId] = value
    }

    private fun clearUserMessageDraft(chatId: String) {
        userMessageDraftsByChatId.remove(chatId)
        if (activeDraftChatId == chatId) {
            _userMessage.value = TextFieldValue("")
        }
    }

    fun scrollToBottom() {
        _scrollToBottomEvent.tryEmit(Unit)
    }

    fun getTurnCompleteCounter(chatId: String): Long {
        return _turnCompleteCounterByChatId.value[chatId] ?: 0L
    }

    fun isChatLoading(chatId: String): Boolean {
        return runtimeFor(chatId).isLoading.value
    }

    fun setSpeakMessageHandler(handler: (String, Boolean) -> Unit) {
        speakMessageHandler = handler
    }

    private fun resetCurrentTurnToolInvocationCount(chatId: String) {
        val updated = _currentTurnToolInvocationCountByChatId.value.toMutableMap()
        updated[chatId] = 0
        _currentTurnToolInvocationCountByChatId.value = updated
    }

    private fun incrementCurrentTurnToolInvocationCount(chatId: String) {
        val updated = _currentTurnToolInvocationCountByChatId.value.toMutableMap()
        updated[chatId] = (updated[chatId] ?: 0) + 1
        _currentTurnToolInvocationCountByChatId.value = updated
    }

    private fun clearCurrentTurnToolInvocationCount(chatId: String) {
        val updated = _currentTurnToolInvocationCountByChatId.value.toMutableMap()
        updated.remove(chatId)
        _currentTurnToolInvocationCountByChatId.value = updated
    }

    fun sendUserMessage(
            attachments: List<AttachmentInfo> = emptyList(),
            chatId: String,
            messageTextOverride: String? = null,
            proxySenderNameOverride: String? = null,
            workspacePath: String? = null,
            workspaceEnv: String? = null,
            promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
            roleCardId: String,
            enableThinking: Boolean = false,
            enableMemoryAutoUpdate: Boolean = true,
            maxTokens: Int,
            tokenUsageThreshold: Double,
            replyToMessage: ChatMessage? = null, // 新增回复消息参数
            isAutoContinuation: Boolean = false, // 标识是否为自动续写
            enableSummary: Boolean = true,
            chatModelConfigIdOverride: String? = null,
            chatModelIndexOverride: Int? = null,
            memorySpaceIdOverride: String? = null,
            suppressUserMessageInHistory: Boolean = false,
            isGroupOrchestrationTurn: Boolean = false,
            groupParticipantNamesText: String? = null,
            turnOptions: ChatTurnOptions = ChatTurnOptions()
    ) {
        val rawMessageText = messageTextOverride ?: _userMessage.value.text
        // 群组编排模式下，允许空消息（后续成员不需要用户消息）
        if (rawMessageText.isBlank() && attachments.isEmpty() && !isAutoContinuation && !isGroupOrchestrationTurn) {
            AppLogger.d(
                TAG,
                "sendUserMessage忽略: 空消息且无附件, chatId=$chatId, autoContinuation=$isAutoContinuation"
            )
            return
        }
        val chatRuntime = runtimeFor(chatId)
        if (chatRuntime.isLoading.value) {
            AppLogger.w(
                TAG,
                "sendUserMessage忽略: chat正在处理中, chatId=$chatId, roleCardId=$roleCardId, override=${!messageTextOverride.isNullOrBlank()}, suppressUserMessageInHistory=$suppressUserMessageInHistory"
            )
            return
        }

        val originalMessageText = rawMessageText.trim()
        var messageText = originalMessageText
        
        if (messageTextOverride == null) {
            clearUserMessageDraft(chatId)
        }
        resetCurrentTurnToolInvocationCount(chatId)
        chatRuntime.responseStream = null
        chatRuntime.isLoading.value = true
        chatRuntime.currentTurnOptions = turnOptions
        updateGlobalLoadingState()
        setChatInputProcessingState(chatId, EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing)))

        val sendJob =
            coroutineScope.launch(Dispatchers.IO) {
            val sendUserMessageStartTime = messageTimingNow()
            val effectivePersistTurn = turnOptions.persistTurn
            val effectiveHideUserMessage = effectivePersistTurn && turnOptions.hideUserMessage
            // 检查这是否是聊天中的第一条用户消息（忽略AI的开场白）
            val isFirstMessage = !hasUserMessage(chatId)
            val titleFallback = if (effectivePersistTurn && isFirstMessage && chatId != null) {
                fallbackConversationTitle(originalMessageText, attachments).also { fallbackTitle ->
                    updateChatTitle(chatId, fallbackTitle)
                }
            } else {
                null
            }

            AppLogger.d(TAG, "开始处理用户消息：附件数量=${attachments.size}")

            // 获取当前模型配置以检查是否启用直接图片处理
            val configId = chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
                ?: functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
            val loadModelConfigStartTime = messageTimingNow()
            val currentModelConfig = modelConfigManager.getModelConfigFlow(configId).first()
            val enableDirectImageProcessing = currentModelConfig.enableDirectImageProcessing
            val enableDirectAudioProcessing = currentModelConfig.enableDirectAudioProcessing
            val enableDirectVideoProcessing = currentModelConfig.enableDirectVideoProcessing
            AppLogger.d(TAG, "直接图片处理状态: $enableDirectImageProcessing (配置ID: $configId)")
            logMessageTiming(
                stage = "delegate.loadModelConfig",
                startTimeMs = loadModelConfigStartTime,
                details = "chatId=$chatId, configId=$configId"
            )

            // 1. 使用 AIMessageManager 构建最终消息
            val buildUserMessageStartTime = messageTimingNow()
            val finalMessageContent = AIMessageManager.buildUserMessageContent(
                context = context,
                messageText = messageText,
                proxySenderName = proxySenderNameOverride,
                attachments = attachments,
                workspacePath = workspacePath,
                workspaceEnv = workspaceEnv,
                replyToMessage = replyToMessage,
                enableDirectImageProcessing = enableDirectImageProcessing,
                enableDirectAudioProcessing = enableDirectAudioProcessing,
                enableDirectVideoProcessing = enableDirectVideoProcessing,
                chatId = chatId,
                roleCardId = roleCardId
            )
            logMessageTiming(
                stage = "delegate.buildUserMessageContent",
                startTimeMs = buildUserMessageStartTime,
                details = "chatId=$chatId, attachments=${attachments.size}, finalLength=${finalMessageContent.length}"
            )

            // 自动继续且原本消息为空时，不添加到聊天历史（虽然会发送"继续"给AI）
            // 群组编排模式下，空消息也不添加到聊天历史
            val shouldAddUserMessageToChat =
                effectivePersistTurn &&
                !suppressUserMessageInHistory &&
                !(isAutoContinuation &&
                        originalMessageText.isBlank() &&
                        attachments.isEmpty()) &&
                !(isGroupOrchestrationTurn &&
                        originalMessageText.isBlank() &&
                        attachments.isEmpty())
            var userMessageAdded = false
            var userMessage = ChatMessage(
                sender = "user",
                content = finalMessageContent,
                roleName = context.getString(R.string.message_role_user), // 用户消息的角色名固定为"用户"
                displayMode =
                    if (effectiveHideUserMessage) {
                        ChatMessageDisplayMode.HIDDEN_PLACEHOLDER
                    } else {
                        ChatMessageDisplayMode.NORMAL
                    }
            )

            val toolHandler = AIToolHandler.getInstance(context)
            var workspaceToolHookSession: WorkspaceBackupManager.WorkspaceToolHookSession? = null

            // 在消息发送期间临时挂载 workspace hook，结束后卸载
            if (!workspacePath.isNullOrBlank()) {
                val attachWorkspaceHookStartTime = messageTimingNow()
                try {
                    val session =
                        WorkspaceBackupManager.getInstance(context)
                            .createWorkspaceToolHookSession(
                                workspacePath = workspacePath,
                                workspaceEnv = workspaceEnv,
                                messageTimestamp = userMessage.timestamp,
                                chatId = chatId
                            )
                    workspaceToolHookSession = session
                    toolHandler.addToolHook(session)
                    AppLogger.d(
                        TAG,
                        "Workspace hook attached for timestamp=${userMessage.timestamp}, path=$workspacePath"
                    )
                    logMessageTiming(
                        stage = "delegate.attachWorkspaceHook",
                        startTimeMs = attachWorkspaceHookStartTime,
                        details = "chatId=$chatId, workspacePath=$workspacePath"
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to attach workspace hook", e)
                    _nonFatalErrorEvent.emit(context.getString(R.string.message_workspace_sync_failed, e.message))
                }
            }

            if (shouldAddUserMessageToChat && chatId != null) {
                // 等待消息添加到聊天历史完成，确保getChatHistory()包含新消息
                val addUserMessageStartTime = messageTimingNow()
                addMessageToChat(chatId, userMessage)
                userMessageAdded = true
                logMessageTiming(
                    stage = "delegate.addUserMessageToChat",
                    startTimeMs = addUserMessageStartTime,
                    details = "chatId=$chatId, contentLength=${userMessage.content.length}"
                )
                titleFallback?.let { fallbackTitle ->
                    launchConversationTitleGeneration(
                        chatId = chatId,
                        userText = originalMessageText,
                        attachments = attachments,
                        fallbackTitle = fallbackTitle
                    )
                }
            }

            lateinit var aiMessage: ChatMessage
            val activeChatId = chatId
            var serviceForTurnComplete: EnhancedAIService? = null
            var shouldNotifyTurnComplete = false
            var finalInputStateAfterSend: EnhancedInputProcessingState? = null
            var isWaifuModeEnabled = false
            var didStreamAutoRead = false
            val effectiveRoleCardId = roleCardId
            val waifuEmittedMessages = mutableListOf<ChatMessage>()
            var syncWaifuMessageMetricsHandler: (suspend (ChatMessage) -> Unit)? = null
            var requestSentAt = 0L
            var requestStartElapsed = 0L
            var firstResponseElapsed: Long? = null
            var turnInputTokens = 0
            var turnOutputTokens = 0
            var turnCachedInputTokens = 0
            var calculateNextWindowSize: (suspend () -> Int?)? = null
            var cancellationToPropagate: kotlinx.coroutines.CancellationException? = null
            try {
                // if (!NetworkUtils.isNetworkAvailable(context)) {
                //     withContext(Dispatchers.Main) { showErrorMessage("网络连接不可用") }
                //     _isLoading.value = false
                //     setChatInputProcessingState(activeChatId, EnhancedInputProcessingState.Idle)
                //     return@launch
                // }

                val acquireServiceStartTime = messageTimingNow()
                val chatScopedService = EnhancedAIService.getChatInstance(context, activeChatId)
                val service =
                    (chatScopedService
                        ?: getEnhancedAiService())
                        ?: run {
                            withContext(Dispatchers.Main) { showErrorMessage(context.getString(R.string.message_ai_service_not_initialized)) }
                            chatRuntime.isLoading.value = false
                            updateGlobalLoadingState()
                            setChatInputProcessingState(activeChatId, EnhancedInputProcessingState.Idle)
                            return@launch
                        }
                logMessageTiming(
                    stage = "delegate.acquireService",
                    startTimeMs = acquireServiceStartTime,
                    details = "chatId=$activeChatId, reusedChatInstance=${chatScopedService != null}"
                )
                serviceForTurnComplete = service

                // 清除上一次可能残留的 Error 状态，避免 StateFlow 重放导致新一轮发送立即再次触发弹窗
                service.setInputProcessingState(EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing)))

                // 监听此 chat 对应的 EnhancedAIService 状态，映射到 per-chat state
                chatRuntime.stateCollectionJob?.cancel()
                chatRuntime.stateCollectionJob =
                    coroutineScope.launch {
                        var lastErrorMessage: String? = null
                        service.inputProcessingState.collect { state ->
                            setChatInputProcessingState(activeChatId, state)

                            if (state is EnhancedInputProcessingState.Error) {
                                val msg = state.message
                                if (msg != lastErrorMessage) {
                                    lastErrorMessage = msg
                                    withContext(Dispatchers.Main) {
                                        showErrorMessage(msg)
                                    }
                                }
                            } else {
                                lastErrorMessage = null
                            }
                        }
                    }

                val responseStartTime = messageTimingNow()

                val userPreferencesManager = UserPreferencesManager.getInstance(context)

                // 获取角色信息用于通知
                val loadRoleInfoStartTime = messageTimingNow()
                val (characterName, avatarUri) = try {
                    val roleCard = characterCardManager.getCharacterCardFlow(effectiveRoleCardId).first()
                    val avatar =
                        userPreferencesManager.getAiAvatarForCharacterCardFlow(roleCard.id).first()
                    Pair(roleCard.name, avatar)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "获取角色信息失败: ${e.message}", e)
                    Pair(null, null)
                }
                val currentRoleName = characterName ?: "Operit"
                logMessageTiming(
                    stage = "delegate.loadRoleInfo",
                    startTimeMs = loadRoleInfoStartTime,
                    details = "chatId=$activeChatId, roleCardId=$effectiveRoleCardId, roleName=$currentRoleName"
                )
                calculateNextWindowSize = {
                    runCatching {
                        AIMessageManager.calculateStableContextWindow(
                            enhancedAiService = service,
                            chatId = activeChatId,
                            messageContent = "",
                            chatHistory = getRuntimeChatHistory(activeChatId),
                            workspacePath = workspacePath,
                            workspaceEnv = workspaceEnv,
                            promptFunctionType = promptFunctionType,
                            roleCardId = effectiveRoleCardId,
                            currentRoleName = currentRoleName,
                            splitHistoryByRole = true,
                            groupOrchestrationMode = isGroupOrchestrationTurn,
                            groupParticipantNamesText = groupParticipantNamesText,
                            chatModelConfigIdOverride = chatModelConfigIdOverride,
                            chatModelIndexOverride = chatModelIndexOverride,
                            memorySpaceIdOverride = memorySpaceIdOverride,
                            publishEstimate = false
                        )
                    }.onFailure {
                        AppLogger.w(TAG, "回合结束后重算上下文窗口失败", it)
                    }.getOrNull()
                }

                val loadChatHistoryStartTime = messageTimingNow()
                val chatHistory = getRuntimeChatHistory(activeChatId)
                logMessageTiming(
                    stage = "delegate.loadChatHistory",
                    startTimeMs = loadChatHistoryStartTime,
                    details = "chatId=$activeChatId, size=${chatHistory.size}"
                )

                // 关闭总结时仍保留真实 limits，避免下游插件收到 0/Infinity 这类无效 JSON 值。
                val effectiveMaxTokens = maxTokens
                val effectiveEnableSummary = enableSummary && effectivePersistTurn
                val effectiveTokenUsageThreshold =
                    if (effectiveEnableSummary) tokenUsageThreshold else Double.MAX_VALUE
                val effectiveOnTokenLimitExceeded = if (effectiveEnableSummary) {
                    suspend {
                        onTokenLimitExceeded(
                            activeChatId,
                            effectiveRoleCardId,
                            isGroupOrchestrationTurn,
                            groupParticipantNamesText
                        )
                    }
                } else {
                    null
                }

                // 2. 使用 AIMessageManager 发送消息
                // 群组编排模式下，只有当消息内容不为空时才添加 [From user] 前缀
                val requestMessageContent =
                    if (isGroupOrchestrationTurn &&
                        finalMessageContent.trimStart().isNotEmpty() &&
                        !finalMessageContent.trimStart().startsWith("[From user]")
                    ) {
                        "[From user]\n$finalMessageContent"
                    } else {
                        finalMessageContent
                    }

                requestSentAt = System.currentTimeMillis()
                requestStartElapsed = messageTimingNow()
                chatRuntime.requestSentAt = requestSentAt
                chatRuntime.requestStartElapsed = requestStartElapsed
                chatRuntime.firstResponseElapsed = null
                if (userMessageAdded && chatId != null) {
                    userMessage = userMessage.copy(sentAt = requestSentAt)
                    addMessageToChat(chatId, userMessage)
                }

                val prepareResponseStreamStartTime = messageTimingNow()
                val responseStream = AIMessageManager.sendMessage(
                    enhancedAiService = service,
                    chatId = activeChatId,
                    messageContent = requestMessageContent,
                    // 仅在群组编排中去掉当前用户消息，避免重复拼接。
                    chatHistory = if (isGroupOrchestrationTurn && userMessageAdded && chatHistory.isNotEmpty()) {
                        chatHistory.subList(0, chatHistory.size - 1)
                    } else {
                        chatHistory
                    },
                    workspacePath = workspacePath,
                    promptFunctionType = promptFunctionType,
                    enableThinking = enableThinking,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    maxTokens = effectiveMaxTokens,
                    tokenUsageThreshold = effectiveTokenUsageThreshold,
                    onNonFatalError = { error ->
                        _nonFatalErrorEvent.emit(error)
                    },
                    onTokenLimitExceeded = effectiveOnTokenLimitExceeded,
                    characterName = characterName,
                    avatarUri = avatarUri,
                    roleCardId = effectiveRoleCardId,
                    currentRoleName = currentRoleName,
                    splitHistoryByRole = true,
                    groupOrchestrationMode = isGroupOrchestrationTurn,
                    groupParticipantNamesText = groupParticipantNamesText,
                    proxySenderName = proxySenderNameOverride,
                    onToolInvocation = {
                        incrementCurrentTurnToolInvocationCount(chatId)
                    },
                    notifyReplyOverride = turnOptions.notifyReply,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    memorySpaceIdOverride = memorySpaceIdOverride,
                    disableWarning = turnOptions.disableWarning
                )
                logMessageTiming(
                    stage = "delegate.prepareResponseStream",
                    startTimeMs = prepareResponseStreamStartTime,
                    details = "chatId=$activeChatId, requestLength=${requestMessageContent.length}, history=${chatHistory.size}"
                )

                // AIMessageManager 已返回可重放的共享流，这里直接复用，避免在 viewModelScope 上再包一层。
                val sharedCharStream = responseStream

                // 更新当前响应流，使其可以被其他组件（如悬浮窗）访问
                chatRuntime.responseStream = sharedCharStream

                // 获取当前使用的provider和model信息
                val loadProviderModelStartTime = messageTimingNow()
                val (provider, modelName) = try {
                    service.getDisplayProviderAndModelForFunction(
                        functionType = com.ai.assistance.operit.data.model.FunctionType.CHAT,
                        chatModelConfigIdOverride = chatModelConfigIdOverride,
                        chatModelIndexOverride = chatModelIndexOverride
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "获取provider和model信息失败: ${e.message}", e)
                    Pair("", "")
                }
                logMessageTiming(
                    stage = "delegate.loadProviderModel",
                    startTimeMs = loadProviderModelStartTime,
                    details = "chatId=$activeChatId, provider=$provider, model=$modelName"
                )

                aiMessage = ChatMessage(
                    sender = "ai", 
                    contentStream = sharedCharStream,
                    timestamp = ChatMessageTimestampAllocator.next(),
                    roleName = currentRoleName,
                    provider = provider,
                    modelName = modelName,
                    sentAt = requestSentAt
                )
                AppLogger.d(
                    TAG,
                    "创建带流的AI消息, stream is null: ${aiMessage.contentStream == null}, timestamp: ${aiMessage.timestamp}"
                )

                // 检查是否启用waifu模式来决定是否显示流式过程
                val waifuPreferences = WaifuPreferences.getInstance(context)
                isWaifuModeEnabled = waifuPreferences.enableWaifuModeFlow.first()
                val waifuCharDelay = waifuPreferences.waifuCharDelayFlow.first()
                val waifuRemovePunctuation =
                    if (isWaifuModeEnabled) {
                        waifuPreferences.waifuRemovePunctuationFlow.first()
                    } else {
                        false
                    }

                suspend fun emitWaifuSegment(segment: String) {
                    if (segment.isBlank()) return

                    val interrupt = waifuEmittedMessages.isEmpty()
                    val segmentMessage =
                        ChatMessage(
                            sender = "ai",
                            content = segment,
                            contentStream = null,
                            timestamp = ChatMessageTimestampAllocator.next(),
                            roleName = currentRoleName,
                            provider = provider,
                            modelName = modelName,
                            sentAt = requestSentAt
                        )

                    withContext(Dispatchers.Main) {
                        waifuEmittedMessages += segmentMessage
                        if (effectivePersistTurn && chatId != null) {
                            addMessageToChat(chatId, segmentMessage)
                        }
                        if (getIsAutoReadEnabled()) {
                            didStreamAutoRead = true
                            AppLogger.d(
                                TAG,
                                "autoRead[waifuStream] interrupt=$interrupt len=${segment.length} preview=\"${speechPreview(segment)}\""
                            )
                            speakMessageHandler(segment, interrupt)
                        }
                        tryEmitScrollToBottomThrottled(chatId)
                    }
                }

                suspend fun syncWaifuMessageMetrics(sourceMessage: ChatMessage) {
                    if (!effectivePersistTurn || chatId == null || waifuEmittedMessages.isEmpty()) return

                    withContext(Dispatchers.Main) {
                        waifuEmittedMessages.indices.forEach { index ->
                            val updatedMessage =
                                waifuEmittedMessages[index].copy(
                                    inputTokens = sourceMessage.inputTokens,
                                    outputTokens = sourceMessage.outputTokens,
                                    cachedInputTokens = sourceMessage.cachedInputTokens,
                                    sentAt = sourceMessage.sentAt,
                                    outputDurationMs = sourceMessage.outputDurationMs,
                                    waitDurationMs = sourceMessage.waitDurationMs,
                                    completedAt = sourceMessage.completedAt,
                                )
                            waifuEmittedMessages[index] = updatedMessage
                            addMessageToChat(chatId, updatedMessage)
                        }
                    }
                }
                syncWaifuMessageMetricsHandler = { sourceMessage ->
                    syncWaifuMessageMetrics(sourceMessage)
                }
                
                // 只有在非waifu模式下才添加初始的AI消息
                if (!isWaifuModeEnabled) {
                    withContext(Dispatchers.Main) {
                        if (effectivePersistTurn && chatId != null) {
                            addMessageToChat(chatId, aiMessage)
                        }
                    }
                }
                
                // 启动一个独立的协程来收集流内容并持续更新数据库
                val streamCollectionResult = CompletableDeferred<Throwable?>()
                chatRuntime.streamCollectionJob =
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            var hasLoggedFirstChunk = false
                            var lastStreamingPersistAt = 0L
                            val revisionTracker = TextStreamRevisionTracker()
                            val revisionMutex = Mutex()
                            val autoReadBuffer = StringBuilder()
                            var isFirstAutoReadSegment = true
                            val autoReadStream =
                                if (!isWaifuModeEnabled) {
                                    WaifuMessageProcessor.streamTtsText(sharedCharStream)
                                } else {
                                    null
                                }
                            val revisableStream = sharedCharStream as? TextStreamEventCarrier

                            fun flushAutoReadSegment(segment: String, interrupt: Boolean) {
                                val trimmed = segment.trim()
                                if (trimmed.isNotEmpty()) {
                                    didStreamAutoRead = true
                                    AppLogger.d(
                                        TAG,
                                        "autoRead[flush] interrupt=$interrupt didStreamAutoRead=$didStreamAutoRead len=${trimmed.length} preview=\"${speechPreview(trimmed)}\""
                                    )
                                    speakMessageHandler(trimmed, interrupt)
                                } else if (segment.isNotEmpty()) {
                                    AppLogger.d(
                                        TAG,
                                        "autoRead[flush.skipBlank] rawLen=${segment.length}"
                                    )
                                }
                            }

                            fun tryFlushAutoRead() {
                                if (!getIsAutoReadEnabled()) return
                                if (isWaifuModeEnabled) return
                                while (true) {
                                    val bufferBefore = autoReadBuffer.length
                                    val cutIdx = TtsSegmenter.nextSegmentEnd(autoReadBuffer)
                                    if (cutIdx < 0) return

                                    val seg = autoReadBuffer.substring(0, cutIdx)
                                    autoReadBuffer.delete(0, cutIdx)
                                    AppLogger.d(
                                        TAG,
                                        "autoRead[cut] cutIdx=$cutIdx bufferBefore=$bufferBefore bufferAfter=${autoReadBuffer.length} firstSegment=$isFirstAutoReadSegment rawLen=${seg.length} preview=\"${speechPreview(seg)}\""
                                    )

                                    flushAutoReadSegment(seg, interrupt = isFirstAutoReadSegment)
                                    isFirstAutoReadSegment = false
                                }
                            }

                            suspend fun persistStreamingSnapshot(
                                contentSnapshot: String,
                                force: Boolean = false
                            ) {
                                if (!effectivePersistTurn || isWaifuModeEnabled || chatId == null) return
                                val now = messageTimingNow()
                                if (!force && now - lastStreamingPersistAt < STREAM_PERSIST_INTERVAL_MS) {
                                    return
                                }

                                addMessageToChat(chatId, aiMessage.copy(content = contentSnapshot))
                                lastStreamingPersistAt = now
                            }

                            val autoReadJob =
                                autoReadStream?.let { stream ->
                                    launch {
                                        stream.collect { char ->
                                            autoReadBuffer.append(char)
                                            tryFlushAutoRead()
                                        }
                                    }
                                }
                            val waifuSegmentsJob =
                                if (isWaifuModeEnabled) {
                                    launch {
                                        WaifuMessageProcessor.streamSegmentsWithTypingQueue(
                                            sourceStream = sharedCharStream,
                                            removePunctuation = waifuRemovePunctuation,
                                            charDelayMs = waifuCharDelay
                                        ).collect { segment ->
                                            emitWaifuSegment(segment)
                                        }
                                    }
                                } else {
                                    null
                                }

                            val revisionJob =
                                revisableStream?.let { carrier ->
                                    launch {
                                        carrier.eventChannel.collect { event ->
                                            when (event.eventType) {
                                                TextStreamEventType.SAVEPOINT -> {
                                                    revisionMutex.withLock {
                                                        revisionTracker.savepoint(event.id)
                                                    }
                                                }

                                                TextStreamEventType.ROLLBACK -> {
                                                    val snapshot =
                                                        revisionMutex.withLock {
                                                            revisionTracker.rollback(event.id)
                                                        } ?: return@collect

                                                    aiMessage.content = snapshot

                                                    if (!isWaifuModeEnabled) {
                                                        persistStreamingSnapshot(snapshot)
                                                        tryEmitScrollToBottomThrottled(chatId)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                            sharedCharStream.collect { chunk ->
                                if (!hasLoggedFirstChunk) {
                                    hasLoggedFirstChunk = true
                                    if (firstResponseElapsed == null) {
                                        firstResponseElapsed = messageTimingNow()
                                        chatRuntime.firstResponseElapsed = firstResponseElapsed
                                    }
                                    logMessageTiming(
                                        stage = "delegate.firstResponseChunk",
                                        startTimeMs = responseStartTime,
                                        details = "chatId=$activeChatId, firstChunkLength=${chunk.length}"
                                    )
                                }
                                val content =
                                    revisionMutex.withLock {
                                        revisionTracker.append(chunk)
                                    }
                                // 防止后续读取不到
                                aiMessage.content = content
                                
                                // 流式内容由 contentStream 实时渲染，这里仅按固定间隔同步快照，避免碎片 chunk 导致高频持久化。
                                persistStreamingSnapshot(content)
                                if (!isWaifuModeEnabled) {
                                    tryEmitScrollToBottomThrottled(chatId)
                                }
                            }

                            revisionJob?.cancelAndJoin()
                            autoReadJob?.join()
                            waifuSegmentsJob?.join()

                            if (getIsAutoReadEnabled() && !isWaifuModeEnabled) {
                                val remaining = autoReadBuffer.toString()
                                autoReadBuffer.clear()
                                AppLogger.d(
                                    TAG,
                                    "autoRead[remaining] firstSegment=$isFirstAutoReadSegment rawLen=${remaining.length} trimmedLen=${remaining.trim().length} preview=\"${speechPreview(remaining)}\""
                                )
                                flushAutoReadSegment(remaining, interrupt = isFirstAutoReadSegment)
                            }
                        } catch (t: Throwable) {
                            if (!streamCollectionResult.isCompleted) {
                                streamCollectionResult.complete(t)
                            }
                            throw t
                        } finally {
                            if (!streamCollectionResult.isCompleted) {
                                streamCollectionResult.complete(null)
                            }
                        }
                    }

                val streamCollectionError = streamCollectionResult.await()
                if (streamCollectionError != null) {
                    throw streamCollectionError
                }
                logMessageTiming(
                    stage = "delegate.sharedStreamComplete",
                    startTimeMs = responseStartTime,
                    details = "chatId=$activeChatId"
                )

                runCatching {
                    turnInputTokens = service.getCurrentInputTokenCount()
                    turnOutputTokens = service.getCurrentOutputTokenCount()
                    turnCachedInputTokens = service.getCurrentCachedInputTokenCount()
                }.onFailure {
                    AppLogger.w(TAG, "读取本轮 token 统计失败", it)
                }

                val waitDurationMs =
                    if (requestStartElapsed > 0L && firstResponseElapsed != null) {
                        (firstResponseElapsed!! - requestStartElapsed).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                val outputDurationMs =
                    if (firstResponseElapsed != null) {
                        (messageTimingNow() - firstResponseElapsed!!).coerceAtLeast(0L)
                    } else {
                        0L
                    }

                if (requestSentAt > 0L) {
                    if (userMessageAdded && chatId != null) {
                        userMessage =
                            userMessage.withTurnMetrics(
                                inputTokens = turnInputTokens,
                                outputTokens = turnOutputTokens,
                                cachedInputTokens = turnCachedInputTokens,
                                sentAt = requestSentAt,
                                outputDurationMs = outputDurationMs,
                                waitDurationMs = waitDurationMs
                            )
                        addMessageToChat(chatId, userMessage)
                    }

                    aiMessage =
                        aiMessage.withTurnMetrics(
                            inputTokens = turnInputTokens,
                            outputTokens = turnOutputTokens,
                            cachedInputTokens = turnCachedInputTokens,
                            sentAt = requestSentAt,
                            outputDurationMs = outputDurationMs,
                            waitDurationMs = waitDurationMs
                        )
                }
                aiMessage = aiMessage.copy(completedAt = System.currentTimeMillis())

                if (isWaifuModeEnabled) {
                    syncWaifuMessageMetricsHandler?.invoke(aiMessage)
                }

                val stateAfterStream =
                    _inputProcessingStateByChatId.value[chatKey(chatId)]
                if (stateAfterStream !is EnhancedInputProcessingState.Error) {
                    shouldNotifyTurnComplete = true
                    finalInputStateAfterSend = EnhancedInputProcessingState.Completed
                }

                if (pendingAsyncSummaryUiByChatId.containsKey(chatId)) {
                    setSuppressIdleCompletedStateForChat(chatId, true)
                    finalInputStateAfterSend =
                        EnhancedInputProcessingState.Summarizing(
                            context.getString(R.string.message_summarizing)
                        )
                }

                logMessageTiming(
                    stage = "delegate.responseProcessingComplete",
                    startTimeMs = responseStartTime,
                    details = "chatId=$activeChatId, waifu=$isWaifuModeEnabled, autoRead=$didStreamAutoRead"
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    AppLogger.d(TAG, "消息发送被取消")
                    finalInputStateAfterSend = EnhancedInputProcessingState.Idle
                    shouldNotifyTurnComplete = false
                    cancellationToPropagate = e
                } else {
                    AppLogger.e(TAG, "发送消息时出错", e)
                    setChatInputProcessingState(
                        chatId,
                        EnhancedInputProcessingState.Error(context.getString(R.string.message_send_failed, e.message))
                    )
                    withContext(Dispatchers.Main) { showErrorMessage(context.getString(R.string.message_send_failed, e.message)) }
                }
            } finally {
                val finalizeMessageStartTime = messageTimingNow()
                val deferTurnCompleteToAsyncJob =
                    if (cancellationToPropagate == null) {
                        finalizeMessageAndNotify(
                            chatId = chatId,
                            activeChatId = activeChatId,
                            aiMessageProvider = { aiMessage },
                            isWaifuModeEnabled = isWaifuModeEnabled,
                            skipFinalAutoRead = didStreamAutoRead && !isWaifuModeEnabled,
                            syncWaifuMessageMetrics = { sourceMessage ->
                                syncWaifuMessageMetricsHandler?.invoke(sourceMessage)
                            },
                            calculateNextWindowSize = calculateNextWindowSize,
                            turnOptions = turnOptions
                        )
                    } else {
                        AppLogger.d(TAG, "取消回合不执行消息收尾: chatId=$activeChatId")
                        false
                    }
                logMessageTiming(
                    stage = "delegate.finalizeMessage",
                    startTimeMs = finalizeMessageStartTime,
                    details = "chatId=$activeChatId, notifyTurnComplete=$shouldNotifyTurnComplete"
                )

                workspaceToolHookSession?.let { session ->
                    val cleanupWorkspaceHookStartTime = messageTimingNow()
                    runCatching { toolHandler.removeToolHook(session) }
                        .onFailure { AppLogger.w(TAG, "Failed to remove workspace hook", it) }
                    runCatching { session.close() }
                        .onFailure { AppLogger.w(TAG, "Failed to close workspace hook session", it) }
                    logMessageTiming(
                        stage = "delegate.cleanupWorkspaceHook",
                        startTimeMs = cleanupWorkspaceHookStartTime,
                        details = "chatId=$activeChatId"
                    )
                }

                val cleanupRuntimeStartTime = messageTimingNow()
                cleanupRuntimeAfterSend(chatId, chatRuntime)
                logMessageTiming(
                    stage = "delegate.cleanupRuntime",
                    startTimeMs = cleanupRuntimeStartTime,
                    details = "chatId=$activeChatId"
                )

                if (!deferTurnCompleteToAsyncJob) {
                    finalInputStateAfterSend?.let { terminalState ->
                        setChatInputProcessingState(chatId, terminalState)
                    }
                }

                if (shouldNotifyTurnComplete && !deferTurnCompleteToAsyncJob) {
                    val service = serviceForTurnComplete
                    if (service != null) {
                        notifyTurnComplete(
                            chatId,
                            activeChatId,
                            service,
                            calculateNextWindowSize,
                            turnOptions
                        )
                    }
                }

                logMessageTiming(
                    stage = "delegate.sendUserMessage.total",
                    startTimeMs = sendUserMessageStartTime,
                    details = "chatId=$activeChatId, addedUserMessage=$userMessageAdded, enableSummary=$enableSummary, persistTurn=${turnOptions.persistTurn}"
                )
                val currentJob = coroutineContext[Job]
                if (currentJob != null && chatRuntime.sendJob === currentJob) {
                    chatRuntime.sendJob = null
                }
            }
            cancellationToPropagate?.let { throw it }
        }
        chatRuntime.sendJob = sendJob
    }

    suspend fun regenerateAiMessageVariant(
        chatId: String,
        targetMessageTimestamp: Long,
        requestMessageContent: String,
        requestHistory: List<ChatMessage>,
        workspacePath: String?,
        promptFunctionType: PromptFunctionType,
        roleCardId: String,
        currentRoleName: String,
        enableThinking: Boolean,
        enableMemoryAutoUpdate: Boolean,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?,
        memorySpaceIdOverride: String?,
        groupOrchestrationMode: Boolean,
        groupParticipantNamesText: String?,
        onVariantPreviewStarted: suspend (ChatMessage) -> Unit,
        onVariantReady: suspend (ChatMessage) -> Unit,
    ) {
        val chatRuntime = runtimeFor(chatId)
        if (chatRuntime.isLoading.value) {
            throw IllegalStateException(context.getString(R.string.chat_regenerate_busy))
        }

        val currentJob = coroutineContext[Job] ?: throw IllegalStateException("Missing coroutine job")
        var serviceForTerminalCleanup: EnhancedAIService? = null
        var shouldResetInputStateToIdle = false
        chatRuntime.sendJob = currentJob
        resetCurrentTurnToolInvocationCount(chatId)
        chatRuntime.isLoading.value = true
        updateGlobalLoadingState()
        setChatInputProcessingState(
            chatId,
            EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing)),
        )
        var terminalState: EnhancedInputProcessingState? = null
        var exceptionToPropagate: Exception? = null

        try {
            val service =
                EnhancedAIService.getChatInstance(context, chatId)
                    ?: getEnhancedAiService()
                    ?: throw IllegalStateException(context.getString(R.string.message_ai_service_not_initialized))
            serviceForTerminalCleanup = service
            service.setInputProcessingState(
                EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing))
            )

            chatRuntime.stateCollectionJob?.cancel()
            chatRuntime.stateCollectionJob =
                coroutineScope.launch {
                    var lastErrorMessage: String? = null
                    service.inputProcessingState.collect { state ->
                        setChatInputProcessingState(chatId, state)

                        if (state is EnhancedInputProcessingState.Error) {
                            val msg = state.message
                            if (msg != lastErrorMessage) {
                                lastErrorMessage = msg
                                withContext(Dispatchers.Main) {
                                    showErrorMessage(msg)
                                }
                            }
                        } else {
                            lastErrorMessage = null
                        }
                    }
                }

            val (provider, modelName) =
                service.getDisplayProviderAndModelForFunction(
                    functionType = FunctionType.CHAT,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                )

            var firstResponseElapsed: Long? = null
            val requestSentAt = System.currentTimeMillis()
            val requestStartElapsed = messageTimingNow()
            val effectiveRequestMessageContent =
                if (groupOrchestrationMode &&
                    requestMessageContent.trimStart().isNotEmpty() &&
                    !requestMessageContent.trimStart().startsWith("[From user]")
                ) {
                    "[From user]\n$requestMessageContent"
                } else {
                    requestMessageContent
                }

            val responseStream =
                AIMessageManager.sendMessage(
                    enhancedAiService = service,
                    chatId = chatId,
                    messageContent = effectiveRequestMessageContent,
                    chatHistory = requestHistory,
                    workspacePath = workspacePath,
                    promptFunctionType = promptFunctionType,
                    enableThinking = enableThinking,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    onNonFatalError = { error -> _nonFatalErrorEvent.emit(error) },
                    characterName = currentRoleName,
                    roleCardId = roleCardId,
                    currentRoleName = currentRoleName,
                    splitHistoryByRole = true,
                    groupOrchestrationMode = groupOrchestrationMode,
                    groupParticipantNamesText = groupParticipantNamesText,
                    onToolInvocation = { incrementCurrentTurnToolInvocationCount(chatId) },
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    memorySpaceIdOverride = memorySpaceIdOverride,
                )

            val sharedResponseStream = responseStream
            chatRuntime.responseStream = sharedResponseStream

            val aiMessage =
                ChatMessage(
                    sender = "ai",
                    contentStream = sharedResponseStream,
                    timestamp = targetMessageTimestamp,
                    roleName = currentRoleName,
                    provider = provider,
                    modelName = modelName,
                    sentAt = requestSentAt,
                )
            onVariantPreviewStarted(aiMessage)

            coroutineScope {
                val revisableStream = sharedResponseStream as? TextStreamEventCarrier
                val revisionTracker = TextStreamRevisionTracker()
                val revisionMutex = Mutex()

                val revisionJob =
                    revisableStream?.let { carrier ->
                        launch {
                            carrier.eventChannel.collect { event ->
                                when (event.eventType) {
                                    TextStreamEventType.SAVEPOINT -> {
                                        revisionMutex.withLock {
                                            revisionTracker.savepoint(event.id)
                                        }
                                    }

                                    TextStreamEventType.ROLLBACK -> {
                                        val snapshot =
                                            revisionMutex.withLock {
                                                revisionTracker.rollback(event.id)
                                            } ?: return@collect
                                        aiMessage.content = snapshot
                                    }
                                }
                            }
                        }
                    }

                sharedResponseStream.collect { chunk ->
                    if (firstResponseElapsed == null) {
                        firstResponseElapsed = messageTimingNow()
                    }
                    aiMessage.content =
                        revisionMutex.withLock {
                            revisionTracker.append(chunk)
                        }
                }

                revisionJob?.cancelAndJoin()
            }

            val finalContent = resolveFinalContent(aiMessage)
            var turnInputTokens = 0
            var turnOutputTokens = 0
            var turnCachedInputTokens = 0
            runCatching {
                turnInputTokens = service.getCurrentInputTokenCount()
                turnOutputTokens = service.getCurrentOutputTokenCount()
                turnCachedInputTokens = service.getCurrentCachedInputTokenCount()
            }.onFailure {
                AppLogger.w(TAG, "读取重新生成 token 统计失败", it)
            }

            val waitDurationMs =
                if (firstResponseElapsed != null) {
                    (firstResponseElapsed!! - requestStartElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }
            val outputDurationMs =
                if (firstResponseElapsed != null) {
                    (messageTimingNow() - firstResponseElapsed!!).coerceAtLeast(0L)
                } else {
                    0L
                }

            val completedAt = System.currentTimeMillis()
            onVariantReady(
                aiMessage.withTurnMetrics(
                    inputTokens = turnInputTokens,
                    outputTokens = turnOutputTokens,
                    cachedInputTokens = turnCachedInputTokens,
                    sentAt = requestSentAt,
                    outputDurationMs = outputDurationMs,
                    waitDurationMs = waitDurationMs,
                ).copy(
                    content = finalContent,
                    contentStream = null,
                    completedAt = completedAt,
                )
            )
            terminalState = EnhancedInputProcessingState.Completed
            shouldResetInputStateToIdle = true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                terminalState = EnhancedInputProcessingState.Idle
            } else {
                AppLogger.e(TAG, "单条重新生成失败", e)
                setChatInputProcessingState(
                    chatId,
                    EnhancedInputProcessingState.Error(
                        context.getString(R.string.chat_regenerate_single_failed, e.message ?: "")
                    ),
                )
            }
            exceptionToPropagate = e
        } finally {
            clearCurrentTurnToolInvocationCount(chatId)
            if (chatRuntime.sendJob === currentJob) {
                chatRuntime.sendJob = null
            }
            chatRuntime.stateCollectionJob?.cancel()
            chatRuntime.stateCollectionJob = null
            chatRuntime.responseStream = null
            chatRuntime.isLoading.value = false
            updateGlobalLoadingState()
            terminalState?.let { state ->
                setChatInputProcessingState(chatId, state)
            }
            if (shouldResetInputStateToIdle) {
                serviceForTerminalCleanup?.setInputProcessingState(EnhancedInputProcessingState.Idle)
                setChatInputProcessingState(chatId, EnhancedInputProcessingState.Idle)
            }
        }
        exceptionToPropagate?.let { throw it }
    }

    private suspend fun notifyTurnComplete(
        chatId: String?,
        activeChatId: String?,
        service: EnhancedAIService,
        calculateNextWindowSize: (suspend () -> Int?)? = null,
        turnOptions: ChatTurnOptions = ChatTurnOptions()
    ) {
        if (!chatId.isNullOrBlank()) {
            val updated = _turnCompleteCounterByChatId.value.toMutableMap()
            updated[chatId] = (updated[chatId] ?: 0L) + 1L
            _turnCompleteCounterByChatId.value = updated
        }
        val nextWindowSize = calculateNextWindowSize?.invoke()
        AppLogger.d(
            TAG,
            "回合完成: chatId=$activeChatId, nextWindow=$nextWindowSize, service=${service.javaClass.simpleName}"
        )
        onTurnComplete(activeChatId, service, nextWindowSize, turnOptions)
    }

    private suspend fun finalizeMessageAndNotify(
        chatId: String?,
        activeChatId: String?,
        aiMessageProvider: () -> ChatMessage,
        isWaifuModeEnabled: Boolean,
        skipFinalAutoRead: Boolean,
        syncWaifuMessageMetrics: suspend (ChatMessage) -> Unit,
        calculateNextWindowSize: (suspend () -> Int?)? = null,
        turnOptions: ChatTurnOptions = ChatTurnOptions()
    ): Boolean {
        try {
            val aiMessage = aiMessageProvider()
            // 优先使用共享流的全量重放缓存重建最终文本，避免完成信号早于收集协程处理尾部字符时丢字。
            val finalContent = resolveFinalContent(aiMessage)
            aiMessage.content = finalContent
            val completedAt = System.currentTimeMillis()

            withContext(Dispatchers.IO) {
                if (isWaifuModeEnabled) {
                    syncWaifuMessageMetrics(aiMessage.copy(completedAt = completedAt))
                    forceEmitScrollToBottom(chatId)
                } else {
                    // 普通模式，直接清理流
                    val finalMessage =
                        aiMessage.copy(
                            content = finalContent,
                            contentStream = null,
                            completedAt = completedAt,
                        )
                    withContext(Dispatchers.Main) {
                        if (turnOptions.persistTurn && chatId != null) {
                            addMessageToChat(chatId, finalMessage)
                        }
                        AppLogger.d(
                            TAG,
                            "autoRead[final] enabled=${getIsAutoReadEnabled()} skipFinalAutoRead=$skipFinalAutoRead len=${finalContent.length} preview=\"${speechPreview(finalContent)}\""
                        )
                        // 如果启用了自动朗读，则朗读完整消息
                        if (getIsAutoReadEnabled() && !skipFinalAutoRead) {
                            speakMessageHandler(finalContent, true)
                        }
                        forceEmitScrollToBottom(chatId)
                    }
                }
            }
        } catch (e: UninitializedPropertyAccessException) {
            AppLogger.d(TAG, "AI消息未初始化，跳过流清理步骤")
        } catch (e: kotlinx.coroutines.CancellationException) {
            AppLogger.d(TAG, "消息收尾阶段被取消，跳过waifu收尾处理")
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "处理waifu模式时出错", e)
            try {
                val aiMessage = aiMessageProvider()
                val finalContent = aiMessage.content
                val finalMessage =
                    aiMessage.copy(
                        content = finalContent,
                        contentStream = null,
                        completedAt = System.currentTimeMillis(),
                    )
                withContext(Dispatchers.Main) {
                    if (turnOptions.persistTurn && chatId != null) {
                        addMessageToChat(chatId, finalMessage)
                    }
                }
            } catch (ex: Exception) {
                AppLogger.e(TAG, "回退到普通模式也失败", ex)
            }
        }
        return false
    }

    private fun cleanupRuntimeAfterSend(chatId: String, chatRuntime: ChatRuntime) {
        chatRuntime.streamCollectionJob = null
        chatRuntime.stateCollectionJob?.cancel()
        chatRuntime.stateCollectionJob = null
        chatRuntime.responseStream = null
        chatRuntime.currentTurnOptions = ChatTurnOptions()
        chatRuntime.requestSentAt = 0L
        chatRuntime.requestStartElapsed = 0L
        chatRuntime.firstResponseElapsed = null
        chatRuntime.isLoading.value = false

        updateGlobalLoadingState()
        clearCurrentTurnToolInvocationCount(chatId)
    }

    /**
     * 刷新聚合后的加载状态。
     * 仅重新计算全局/按会话的加载派生值，不会直接改写具体 chat 的 isLoading。
     */
    fun refreshGlobalLoadingState() {
        updateGlobalLoadingState()
    }
}
