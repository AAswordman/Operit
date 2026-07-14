package com.ai.assistance.operit.api.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.api.chat.enhance.ConversationMarkupManager
import com.ai.assistance.operit.api.chat.enhance.ConversationRoundManager
import com.ai.assistance.operit.api.chat.enhance.ConversationService
import com.ai.assistance.operit.api.chat.enhance.FileBindingService
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.core.chat.hooks.PromptHookContext
import com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.appendUserTurnIfMissing
import com.ai.assistance.operit.core.chat.hooks.buildActivePromptHookMetadata
import com.ai.assistance.operit.core.chat.hooks.mergeAdjacentTurns
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.core.chat.hooks.toRoleContentPairs
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.core.tools.climode.CliToolModeSupport
import com.ai.assistance.operit.core.tools.climode.ToolExposureMode
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import com.ai.assistance.operit.util.stream.withEventChannel
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterCardToolAccessResolver
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.MemoryAutoSaveCandidateRepository
import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.LocaleUtils

/**
 * Enhanced AI service that provides advanced conversational capabilities by integrating various
 * components like tool execution, conversation management, user preferences, and problem library.
 */
class EnhancedAIService private constructor(private val context: Context) {
    data class TurnTokenSnapshot(
        val inputTokens: Int,
        val outputTokens: Int,
        val cachedInputTokens: Int,
    )

    companion object {
        private const val TAG = "EnhancedAIService"

        @Volatile private var INSTANCE: EnhancedAIService? = null

        private val CHAT_INSTANCES = ConcurrentHashMap<String, EnhancedAIService>()

        private val FOREGROUND_REF_COUNT = AtomicInteger(0)

        /**
         * 获取EnhancedAIService实例
         * @param context 应用上下文
         * @return EnhancedAIService实.
         */
        fun getInstance(context: Context): EnhancedAIService {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: EnhancedAIService(context.applicationContext).also {
                                    INSTANCE = it
                                }
                    }
        }

        fun getChatInstance(context: Context, chatId: String): EnhancedAIService {
            val appContext = context.applicationContext
            return CHAT_INSTANCES[chatId]
                ?: synchronized(CHAT_INSTANCES) {
                    CHAT_INSTANCES[chatId]
                        ?: EnhancedAIService(appContext).also { CHAT_INSTANCES[chatId] = it }
                }
        }

        fun releaseChatInstance(chatId: String) {
            val instance = CHAT_INSTANCES.remove(chatId) ?: return
            runCatching {
                instance.cancelConversation()
            }.onFailure { e ->
                AppLogger.e(TAG, "释放chat实例资源失败: chatId=$chatId", e)
            }
        }

        /**
         * 获取指定功能类型的 AIService 实例（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         * @return AIService 实例
         */
        suspend fun getAIServiceForFunction(
                context: Context,
                functionType: FunctionType
        ): AIService {
            return getInstance(context).multiServiceManager.getServiceForFunction(functionType)
        }

        suspend fun getModelConfigForFunction(
            context: Context,
            functionType: FunctionType
        ): ModelConfigData {
            return getInstance(context).multiServiceManager.getModelConfigForFunction(functionType)
        }

        /**
         * 刷新指定功能类型的 AIService 实例（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         */
        suspend fun refreshServiceForFunction(context: Context, functionType: FunctionType) {
            val allInstances = buildList {
                add(getInstance(context))
                addAll(CHAT_INSTANCES.values)
            }.distinct()
            allInstances.forEach { it.multiServiceManager.refreshServiceForFunction(functionType) }
        }

        /**
         * 刷新所有 AIService 实例（非实例化方式）
         * @param context 应用上下文
         */
        suspend fun refreshAllServices(context: Context) {
            val allInstances = buildList {
                add(getInstance(context))
                addAll(CHAT_INSTANCES.values)
            }.distinct()
            allInstances.forEach { it.multiServiceManager.refreshAllServices() }
        }

        /**
         * 获取指定功能类型的当前输入token计数（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         * @return 输入token计数
         */
        suspend fun getCurrentInputTokenCountForFunction(
                context: Context,
                functionType: FunctionType
        ): Int {
            return getInstance(context)
                    .multiServiceManager
                    .getServiceForFunction(functionType)
                    .inputTokenCount
        }

        /**
         * 获取指定功能类型的当前输出token计数（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         * @return 输出token计数
         */
        suspend fun getCurrentOutputTokenCountForFunction(
                context: Context,
                functionType: FunctionType
        ): Int {
            return getInstance(context)
                    .multiServiceManager
                    .getServiceForFunction(functionType)
                    .outputTokenCount
        }

        /**
         * 重置指定功能类型或所有功能类型的token计数器（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型，如果为null则重置所有功能类型
         */
        suspend fun resetTokenCountersForFunction(
                context: Context,
                functionType: FunctionType? = null
        ) {
            val allInstances = buildList {
                add(getInstance(context))
                addAll(CHAT_INSTANCES.values)
            }.distinct()
            allInstances.forEach {
                if (functionType == null) {
                    it.multiServiceManager.resetAllTokenCounters()
                } else {
                    it.multiServiceManager.resetTokenCountersForFunction(functionType)
                }
            }
        }

        fun resetTokenCounters(context: Context) {
            val appContext = context.applicationContext
            val allInstances = buildList {
                add(getInstance(appContext))
                addAll(CHAT_INSTANCES.values)
            }.distinct()

            allInstances.forEach { instance ->
                instance.initScope.launch {
                    runCatching {
                        instance.multiServiceManager.resetAllTokenCounters()
                    }.onFailure { e ->
                        AppLogger.e(TAG, "重置token计数器失败", e)
                    }
                }
            }
        }

        /**
         * 处理文件绑定操作（非实例化方式）
         * @param context 应用上下文
         * @param originalContent 原始文件内容
         * @param aiGeneratedCode AI生成的代码（包含"//existing code"标记）
         * @return 混合后的文件内容
         */
        suspend fun applyFileBinding(
                context: Context,
                originalContent: String,
                aiGeneratedCode: String,
                onProgress: ((Float, String) -> Unit)? = null
        ): Pair<String, String> {
            // 获取EnhancedAIService实例
            val instance = getInstance(context)

            // 委托给FileBindingService处理
            return instance.fileBindingService.processFileBinding(
                    originalContent,
                    aiGeneratedCode,
                    onProgress
            )
        }

        suspend fun applyFileBindingOperations(
            context: Context,
            originalContent: String,
            operations: List<FileBindingService.StructuredEditOperation>,
            onProgress: ((Float, String) -> Unit)? = null
        ): Pair<String, String> {
            val instance = getInstance(context)
            return instance.fileBindingService.processFileBindingOperations(
                originalContent = originalContent,
                operations = operations,
                onProgress = onProgress
            )
        }

        /**
         * 自动生成工具包描述（非实例化方式）
         * @param context 应用上下文
         * @param pluginName 工具包名称
         * @param toolDescriptions 工具描述列表
         * @return 生成的工具包描述
         */
        suspend fun generatePackageDescription(
            context: Context,
            pluginName: String,
            toolDescriptions: List<String>
        ): String {
            return getInstance(context).generatePackageDescription(pluginName, toolDescriptions)
        }
    }

    interface SendMessageCallbacks {
        fun onNonFatalError(error: String) {}

        fun onTokenLimitExceeded() {}

        fun onToolInvocation(toolName: String) {}
    }

    data class SendMessageOptions(
        var message: String = "",
        var maxTokens: Int = 0,
        var tokenUsageThreshold: Double = 0.0,
        var chatId: String? = null,
        var chatHistory: List<PromptTurn> = emptyList(),
        var workspacePath: String? = null,
        var workspaceEnv: String? = null,
        var functionType: FunctionType = FunctionType.CHAT,
        var promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        var enableThinking: Boolean = false,
        var enableMemoryAutoUpdate: Boolean = true,
        var onNonFatalError: suspend (error: String) -> Unit = {},
        var onTokenLimitExceeded: (suspend () -> Unit)? = null,
        var customSystemPromptTemplate: String? = null,
        var isSubTask: Boolean = false,
        var characterName: String? = null,
        var avatarUri: String? = null,
        var roleCardId: String? = null,
        var enableGroupOrchestrationHint: Boolean = false,
        var groupParticipantNamesText: String? = null,
        var proxySenderName: String? = null,
        var callbacks: SendMessageCallbacks? = null,
        var onToolInvocation: (suspend (String) -> Unit)? = null,
        var notifyReplyOverride: Boolean? = null,
        var chatModelConfigIdOverride: String? = null,
        var chatModelIndexOverride: Int? = null,
        var memorySpaceIdOverride: String? = null,
        var stream: Boolean = true,
        var disableWarning: Boolean = false
    )

    // MultiServiceManager 管理不同功能的 AIService 实例
    private val multiServiceManager = MultiServiceManager(context)

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initMutex = Mutex()
    @Volatile private var isServiceManagerInitialized = false

    // 添加ConversationService实例
    private val conversationService = ConversationService(context, CustomEmojiRepository.getInstance(context))

    // 添加FileBindingService实例
    private val fileBindingService = FileBindingService(context)

    // Tool handler for executing tools
    private val toolHandler = AIToolHandler.getInstance(context)

    private suspend fun ensureInitialized() {
        if (isServiceManagerInitialized) return
        initMutex.withLock {
            if (isServiceManagerInitialized) return
            withContext(Dispatchers.IO) {
                multiServiceManager.initialize()
            }
            isServiceManagerInitialized = true
        }
    }

    // State flows for UI updates
    private val _inputProcessingState =
            MutableStateFlow<InputProcessingState>(InputProcessingState.Idle)
    val inputProcessingState = _inputProcessingState.asStateFlow()

    /**
     * 设置当前的输入处理状态
     * @param newState 新的状态
     */
    fun setInputProcessingState(newState: com.ai.assistance.operit.data.model.InputProcessingState) {
        _inputProcessingState.value = newState
    }

    // Per-request token counts
    private val _perRequestTokenCounts = MutableStateFlow<Pair<Int, Int>?>(null)
    val perRequestTokenCounts: StateFlow<Pair<Int, Int>?> = _perRequestTokenCounts.asStateFlow()

    // Stable request window estimate for the next model hop.
    private val _requestWindowEstimate = MutableStateFlow<Int?>(null)
    val requestWindowEstimateFlow: StateFlow<Int?> = _requestWindowEstimate.asStateFlow()

    // Conversation management
    // private val streamBuffer = StringBuilder() // Moved to MessageExecutionContext
    // private val roundManager = ConversationRoundManager() // Moved to MessageExecutionContext
    // private val isConversationActive = AtomicBoolean(false) // Moved to MessageExecutionContext

    // Api Preferences for settings
    private val apiPreferences = ApiPreferences.getInstance(context)
    private val characterCardToolAccessResolver = CharacterCardToolAccessResolver.getInstance(context)

    // Execution context for a single sendMessage call to achieve concurrency
    private data class ModelExecutionSnapshot(
        val lease: MultiServiceManager.ServiceLease
    ) {
        val service: AIService
            get() = lease.service
        val config: ModelConfigData
            get() = lease.modelConfig
        val modelParameters: List<ModelParameter<*>>
            get() = lease.modelParameters
    }

    private data class MessageExecutionContext(
        val executionId: Int,
        val streamBuffer: StringBuilder = StringBuilder(),
        val roundManager: ConversationRoundManager = ConversationRoundManager(),
        val isConversationActive: AtomicBoolean = AtomicBoolean(true),
        val conversationHistory: MutableList<PromptTurn>,
        val eventChannel: MutableSharedStream<TextStreamEvent>,
        var modelExecutionSnapshot: ModelExecutionSnapshot? = null
    )

    private val activeExecutionContexts = ConcurrentHashMap<Int, MessageExecutionContext>()
    private val nextExecutionContextId = AtomicInteger(0)

    private fun registerExecutionContext(context: MessageExecutionContext) {
        activeExecutionContexts[context.executionId] = context
    }

    private fun unregisterExecutionContext(context: MessageExecutionContext) {
        activeExecutionContexts.remove(context.executionId, context)
    }

    private fun invalidateExecutionContext(context: MessageExecutionContext, reason: String) {
        if (context.isConversationActive.compareAndSet(true, false)) {
            AppLogger.d(TAG, "执行上下文已失效: id=${context.executionId}, reason=$reason")
        }
    }

    private fun invalidateAllExecutionContexts(reason: String) {
        activeExecutionContexts.values.forEach { context ->
            invalidateExecutionContext(context, reason)
        }
    }

    private fun isExecutionContextActive(context: MessageExecutionContext): Boolean {
        return context.isConversationActive.get() &&
            activeExecutionContexts[context.executionId] === context
    }

    private suspend fun getModelExecutionSnapshot(
        context: MessageExecutionContext,
        functionType: FunctionType,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?
    ): ModelExecutionSnapshot {
        context.modelExecutionSnapshot?.let { return it }
        ensureInitialized()
        val overrideConfigId = chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
        val lease =
            if (functionType == FunctionType.CHAT && overrideConfigId != null) {
                multiServiceManager.acquireServiceForConfig(
                    configId = overrideConfigId,
                    modelIndex = (chatModelIndexOverride ?: 0).coerceAtLeast(0)
                )
            } else {
                multiServiceManager.acquireServiceForFunction(functionType)
            }
        val snapshot = ModelExecutionSnapshot(lease)
        context.modelExecutionSnapshot = snapshot
        return snapshot
    }

    private suspend fun releaseModelExecutionSnapshot(context: MessageExecutionContext) {
        val snapshot = context.modelExecutionSnapshot ?: return
        context.modelExecutionSnapshot = null
        snapshot.lease.close()
    }

    private suspend fun startAssistantResponseRound(context: MessageExecutionContext) {
        context.roundManager.startNewRound()
        context.streamBuffer.clear()
    }

    // Coroutine management
    private val toolProcessingScope = CoroutineScope(Dispatchers.IO)
    private val toolExecutionJobs = ConcurrentHashMap<String, Job>()
    // private val conversationHistory = mutableListOf<Pair<String, String>>() // Moved to MessageExecutionContext
    // private val conversationMutex = Mutex() // Moved to MessageExecutionContext

    private var accumulatedInputTokenCount = 0
    private var accumulatedOutputTokenCount = 0
    private var accumulatedCachedInputTokenCount = 0
    private var currentRequestInputTokenCount = 0
    private var currentRequestOutputTokenCount = 0
    private var currentRequestCachedInputTokenCount = 0

    private fun saturatedTokenSum(vararg values: Int): Int {
        val total = values.fold(0L) { acc, value -> acc + value.toLong().coerceAtLeast(0L) }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    // Callbacks
    private var currentResponseCallback: ((content: String, thinking: String?) -> Unit)? = null
    private var currentCompleteCallback: (() -> Unit)? = null

    // Package manager for handling tool packages
    private val packageManager = PackageManager.getInstance(context, toolHandler)

    // 存储最后的回复内容，用于通知
    private var lastReplyContent: String? = null

    init {
        com.ai.assistance.operit.api.chat.library.MemoryLibrary.initialize(context)
        initScope.launch {
            runCatching {
                ensureInitialized()
            }.onFailure { e ->
                AppLogger.e(TAG, "MultiServiceManager初始化失败", e)
            }
        }
        initScope.launch {
            runCatching {
                toolHandler.registerDefaultTools()
            }.onFailure { e ->
                AppLogger.e(TAG, "注册默认工具失败", e)
            }
        }
    }

    /**
     * 获取指定功能类型的 AIService 实例
     * @param functionType 功能类型
     * @return AIService 实例
     */
    suspend fun getAIServiceForFunction(functionType: FunctionType): AIService {
        ensureInitialized()
        return getAIServiceForFunction(
            functionType = functionType,
            chatModelConfigIdOverride = null,
            chatModelIndexOverride = null
        )
    }

    suspend fun getAIServiceForFunction(
        functionType: FunctionType,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?
    ): AIService {
        ensureInitialized()
        val overrideConfigId = chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
        return if (functionType == FunctionType.CHAT && overrideConfigId != null) {
            multiServiceManager.getServiceForConfig(
                configId = overrideConfigId,
                modelIndex = (chatModelIndexOverride ?: 0).coerceAtLeast(0)
            )
        } else {
            multiServiceManager.getServiceForFunction(functionType)
        }
    }

    /**
     * 获取指定功能类型的provider和model信息
     * @param functionType 功能类型
     * @return Pair<provider, modelName>，例如 Pair("DEEPSEEK", "deepseek-chat")
     */
    suspend fun getProviderAndModelForFunction(functionType: FunctionType): Pair<String, String> {
        return getProviderAndModelForFunction(
            functionType = functionType,
            chatModelConfigIdOverride = null,
            chatModelIndexOverride = null
        )
    }

    suspend fun getProviderAndModelForFunction(
        functionType: FunctionType,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?
    ): Pair<String, String> {
        val service = getAIServiceForFunction(
            functionType = functionType,
            chatModelConfigIdOverride = chatModelConfigIdOverride,
            chatModelIndexOverride = chatModelIndexOverride
        )
        val providerModel = service.providerModel
        // providerModel格式为"PROVIDER:modelName"，使用第一个冒号分割
        val colonIndex = providerModel.indexOf(":")
        return if (colonIndex > 0) {
            val provider = providerModel.substring(0, colonIndex)
            val modelName = providerModel.substring(colonIndex + 1)
            Pair(provider, modelName)
        } else {
            // 如果没有冒号，整个字符串作为provider，modelName为空
            Pair(providerModel, "")
        }
    }

    suspend fun getDisplayProviderAndModelForFunction(
        functionType: FunctionType,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?
    ): Pair<String, String> {
        val (provider, modelName) = getProviderAndModelForFunction(
            functionType = functionType,
            chatModelConfigIdOverride = chatModelConfigIdOverride,
            chatModelIndexOverride = chatModelIndexOverride
        )
        val config = getModelConfigForFunction(
            functionType = functionType,
            chatModelConfigIdOverride = chatModelConfigIdOverride,
            chatModelIndexOverride = chatModelIndexOverride
        )
        return Pair("$provider/${config.name}", modelName)
    }

    suspend fun getModelConfigForFunction(
        functionType: FunctionType,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null
    ): ModelConfigData {
        ensureInitialized()
        val overrideConfigId = chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
        return if (functionType == FunctionType.CHAT && overrideConfigId != null) {
            multiServiceManager.getModelConfigForConfig(overrideConfigId)
        } else {
            multiServiceManager.getModelConfigForFunction(functionType)
        }
    }

    /**
     * 刷新指定功能类型的 AIService 实例 当配置发生更改时调用
     * @param functionType 功能类型
     */
    suspend fun refreshServiceForFunction(functionType: FunctionType) {
        ensureInitialized()
        multiServiceManager.refreshServiceForFunction(functionType)
    }

    /** 刷新所有 AIService 实例 当全局配置发生更改时调用 */
    suspend fun refreshAllServices() {
        ensureInitialized()
        multiServiceManager.refreshAllServices()
    }

    private suspend fun getModelParametersForFunction(
        functionType: FunctionType,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null
    ): List<com.ai.assistance.operit.data.model.ModelParameter<*>> {
        ensureInitialized()
        val overrideConfigId = chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
        return if (functionType == FunctionType.CHAT && overrideConfigId != null) {
            multiServiceManager.getModelParametersForConfig(overrideConfigId)
        } else {
            multiServiceManager.getModelParametersForFunction(functionType)
        }
    }

    private fun publishRequestWindowEstimate(windowSize: Int) {
        _requestWindowEstimate.value = windowSize
    }

    private suspend fun estimatePreparedRequestWindow(
        serviceForFunction: AIService,
        preparedHistory: List<PromptTurn>,
        availableTools: List<ToolPrompt>?,
        publishEstimate: Boolean
    ): Int {
        val windowSize =
            serviceForFunction.calculateInputTokens(
                chatHistory = preparedHistory,
                availableTools = availableTools
            )
        if (publishEstimate) {
            publishRequestWindowEstimate(windowSize)
        }
        return windowSize
    }

    private fun applyPromptFinalizeHooks(
        initialContext: PromptHookContext,
        dispatchHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchPromptFinalizeHooks
    ): PromptHookContext {
        return dispatchHooks(initialContext)
    }

    private fun bypassPromptHooks(context: PromptHookContext): PromptHookContext = context

    private suspend fun buildPromptFinalizeMetadata(
        chatId: String?,
        roleCardId: String?,
        workspacePath: String?,
        workspaceEnv: String?,
        enableThinking: Boolean,
        stream: Boolean,
        isSubTask: Boolean
    ): Map<String, Any?> {
        return mapOf(
            "workspacePath" to workspacePath,
            "workspaceEnv" to workspaceEnv,
            "enableThinking" to enableThinking,
            "stream" to stream,
            "isSubTask" to isSubTask
        ) + buildActivePromptHookMetadata(context, chatId, roleCardId)
    }

    private fun applyFinalizedCurrentUserTurn(
        preparedHistory: List<PromptTurn>,
        originalCurrentMessage: String,
        finalizedCurrentMessage: String
    ): List<PromptTurn> {
        if (finalizedCurrentMessage.isBlank()) {
            return preparedHistory
        }

        val lastTurn = preparedHistory.lastOrNull()
        return when {
            lastTurn?.kind == PromptTurnKind.USER &&
                lastTurn.content == finalizedCurrentMessage -> {
                preparedHistory
            }
            lastTurn?.kind == PromptTurnKind.USER &&
                lastTurn.content == originalCurrentMessage -> {
                preparedHistory.dropLast(1) + lastTurn.copy(content = finalizedCurrentMessage)
            }
            else -> {
                preparedHistory.appendUserTurnIfMissing(finalizedCurrentMessage)
            }
        }
    }

    suspend fun estimateRequestWindowFromMemory(
        message: String,
        chatHistory: List<PromptTurn>,
        chatId: String? = null,
        workspacePath: String? = null,
        workspaceEnv: String? = null,
        functionType: FunctionType = FunctionType.CHAT,
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        enableThinking: Boolean = false,
        customSystemPromptTemplate: String? = null,
        roleCardId: String? = null,
        enableGroupOrchestrationHint: Boolean = false,
        groupParticipantNamesText: String? = null,
        proxySenderName: String? = null,
        isSubTask: Boolean = false,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        memorySpaceIdOverride: String? = null,
        stream: Boolean = true,
        publishEstimate: Boolean = true
    ): Int {
        val modelConfig =
            getModelConfigForFunction(
                functionType = functionType,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride
            )
        val preparedHistory =
            prepareConversationHistory(
                chatHistory = chatHistory,
                processedInput = message,
                chatId = chatId,
                workspacePath = workspacePath,
                workspaceEnv = workspaceEnv,
                promptFunctionType = promptFunctionType,
                customSystemPromptTemplate = customSystemPromptTemplate,
                roleCardId = roleCardId,
                enableGroupOrchestrationHint = enableGroupOrchestrationHint,
                groupParticipantNamesText = groupParticipantNamesText,
                proxySenderName = proxySenderName,
                isSubTask = isSubTask,
                functionType = functionType,
                modelConfig = modelConfig,
                memorySpaceIdOverride = memorySpaceIdOverride,
                dispatchHistoryHooks = PromptHookRegistry::dispatchPromptEstimateHistoryHooks,
                dispatchSystemPromptComposeHooks = ::bypassPromptHooks,
                dispatchToolPromptComposeHooks = ::bypassPromptHooks
            )

        val modelParameters =
            getModelParametersForFunction(
                functionType = functionType,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride
            )
        val serviceForFunction =
            getAIServiceForFunction(
                functionType = functionType,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride
            )
        val availableTools =
            getAvailableToolsForFunction(
                functionType = functionType,
                chatId = chatId,
                promptFunctionType = promptFunctionType,
                roleCardId = roleCardId,
                modelConfig = modelConfig
            )

        var finalProcessedInput = message
        var finalPreparedHistory = preparedHistory
        val beforeFinalizeContext =
            applyPromptFinalizeHooks(
                PromptHookContext(
                    stage = "before_finalize_prompt",
                    chatId = chatId,
                    functionType = functionType.name,
                    promptFunctionType = promptFunctionType.name,
                    rawInput = message,
                    processedInput = finalProcessedInput,
                    preparedHistory = finalPreparedHistory,
                    modelParameters = serializePromptHookModelParameters(modelParameters),
                    availableTools = serializePromptHookToolPrompts(availableTools),
                    metadata = buildPromptFinalizeMetadata(
                        chatId = chatId,
                        roleCardId = roleCardId,
                        workspacePath = workspacePath,
                        workspaceEnv = workspaceEnv,
                        enableThinking = enableThinking,
                        stream = stream,
                        isSubTask = isSubTask
                    )
                ),
                dispatchHooks = PromptHookRegistry::dispatchPromptEstimateFinalizeHooks
            )
        finalProcessedInput = beforeFinalizeContext.processedInput ?: finalProcessedInput
        finalPreparedHistory = beforeFinalizeContext.preparedHistory
        val beforeSendContext =
            applyPromptFinalizeHooks(
                beforeFinalizeContext.copy(
                    stage = "before_send_to_model",
                    processedInput = finalProcessedInput,
                    preparedHistory = finalPreparedHistory
                ),
                dispatchHooks = PromptHookRegistry::dispatchPromptEstimateFinalizeHooks
            )
        finalProcessedInput = beforeSendContext.processedInput ?: finalProcessedInput
        finalPreparedHistory = beforeSendContext.preparedHistory
        if (!ChatUtils.isGeminiProviderModel(serviceForFunction.providerModel)) {
            finalProcessedInput = ChatUtils.stripGeminiThoughtSignatureMeta(finalProcessedInput)
            finalPreparedHistory = ChatUtils.stripGeminiThoughtSignatureMetaTurns(finalPreparedHistory)
        }
        if (!ChatUtils.isOpenAIResponsesProviderModel(serviceForFunction.providerModel)) {
            finalProcessedInput = ChatUtils.stripOpenAiResponsesReasoningMeta(finalProcessedInput)
            finalPreparedHistory = ChatUtils.stripOpenAiResponsesReasoningMetaTurns(finalPreparedHistory)
        }

        val requestHistory =
            applyFinalizedCurrentUserTurn(
                preparedHistory = finalPreparedHistory,
                originalCurrentMessage = message,
                finalizedCurrentMessage = finalProcessedInput
            ).mergeAdjacentTurns { previous, current ->
                previous.kind == PromptTurnKind.USER &&
                    current.kind == PromptTurnKind.USER &&
                    previous.toolName == current.toolName
            }

        return estimatePreparedRequestWindow(
            serviceForFunction = serviceForFunction,
            preparedHistory = requestHistory,
            availableTools = availableTools,
            publishEstimate = publishEstimate
        )
    }

    /** Send a message to the AI service */
    suspend fun sendMessage(
        options: SendMessageOptions
    ): Stream<String> {
        val message = options.message
        val chatId = options.chatId
        val chatHistory = options.chatHistory
        val workspacePath = options.workspacePath
        val workspaceEnv = options.workspaceEnv
        val functionType = options.functionType
        val promptFunctionType = options.promptFunctionType
        val enableThinking = options.enableThinking
        val enableMemoryAutoUpdate = options.enableMemoryAutoUpdate
        val maxTokens = options.maxTokens
        val tokenUsageThreshold = options.tokenUsageThreshold
        val customSystemPromptTemplate = options.customSystemPromptTemplate
        val isSubTask = options.isSubTask
        val characterName = options.characterName
        val avatarUri = options.avatarUri
        val roleCardId = options.roleCardId
        val enableGroupOrchestrationHint = options.enableGroupOrchestrationHint
        val groupParticipantNamesText = options.groupParticipantNamesText
        val proxySenderName = options.proxySenderName
        val callbacks = options.callbacks
        val notifyReplyOverride = options.notifyReplyOverride
        val chatModelConfigIdOverride = options.chatModelConfigIdOverride
        val chatModelIndexOverride = options.chatModelIndexOverride
        val memorySpaceIdOverride = options.memorySpaceIdOverride
        val stream = options.stream
        val disableWarning = options.disableWarning
        val onNonFatalError: suspend (error: String) -> Unit = { error ->
            options.onNonFatalError(error)
            callbacks?.onNonFatalError(error)
        }
        val onTokenLimitExceeded: (suspend () -> Unit)? =
            if (options.onTokenLimitExceeded != null || callbacks != null) {
                suspend {
                    options.onTokenLimitExceeded?.invoke()
                    callbacks?.onTokenLimitExceeded()
                }
            } else {
                null
            }
        val onToolInvocation: (suspend (String) -> Unit)? =
            if (options.onToolInvocation != null || callbacks != null) {
                { toolName ->
                    options.onToolInvocation?.invoke(toolName)
                    callbacks?.onToolInvocation(toolName)
                }
            } else {
                null
            }

        AppLogger.d(TAG, "sendMessage调用开始: 功能类型=$functionType, 提示词类型=$promptFunctionType")
        accumulatedInputTokenCount = 0
        accumulatedOutputTokenCount = 0
        accumulatedCachedInputTokenCount = 0
        currentRequestInputTokenCount = 0
        currentRequestOutputTokenCount = 0
        currentRequestCachedInputTokenCount = 0

        val eventChannel = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val wrappedStream = stream {
            val execContext =
                MessageExecutionContext(
                    executionId = nextExecutionContextId.incrementAndGet(),
                    conversationHistory = chatHistory.toMutableList(),
                    eventChannel = eventChannel
                )
            registerExecutionContext(execContext)
            var hadFatalError = false
            try {
                // 确保所有操作都在IO线程上执行
                withContext(Dispatchers.IO) {
                    // 仅当会话首次启动时开启服务，并更新前台通知为“运行中”
                    if (!isSubTask) {
                        startAiService(characterName, avatarUri)
                    }

                    // Update state to show we're processing
                    if (!isSubTask) {
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value = InputProcessingState.Processing(context.getString(R.string.enhanced_processing_message))
                        }
                    }

                    val startTime = messageTimingNow()
                    val modelSnapshot =
                        getModelExecutionSnapshot(
                            execContext,
                            functionType,
                            chatModelConfigIdOverride,
                            chatModelIndexOverride
                        )

                    // Prepare conversation history with system prompt
                    val preparedHistory =
                            prepareConversationHistory(
                                    execContext.conversationHistory, // 始终使用内部历史记录
                                    message,
                                    chatId,
                                    workspacePath,
                                    workspaceEnv,
                                    promptFunctionType,
                                    customSystemPromptTemplate,
                                    roleCardId,
                                    enableGroupOrchestrationHint,
                                    groupParticipantNamesText,
                                    proxySenderName,
                                    isSubTask,
                                    functionType,
                                    modelSnapshot.config,
                                    memorySpaceIdOverride
                            )
                    val tAfterPrepareHistory = messageTimingNow()
                    AppLogger.d(TAG, "sendMessage本地耗时: prepareConversationHistory=${tAfterPrepareHistory - startTime}ms")
                    
                    // 关键修复：用准备好的历史记录（包含了系统提示）去同步更新内部的 conversationHistory 状态
                    execContext.conversationHistory.clear()
                    execContext.conversationHistory.addAll(preparedHistory)

                    // Update UI state to connecting
                    if (!isSubTask) {
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value = InputProcessingState.Connecting(context.getString(R.string.enhanced_connecting_service))
                        }
                    }

                    // Get all model parameters from preferences (with enabled state)
                    val modelParameters = modelSnapshot.modelParameters
                    val tAfterModelParams = messageTimingNow()
                    AppLogger.d(TAG, "sendMessage本地耗时: getModelParametersForFunction=${tAfterModelParams - tAfterPrepareHistory}ms")

                    // 获取对应功能类型的AIService实例
                    val serviceForFunction = modelSnapshot.service
                    val tAfterGetService = messageTimingNow()
                    AppLogger.d(TAG, "sendMessage本地耗时: getAIServiceForFunction=${tAfterGetService - tAfterModelParams}ms")

                    // 清空之前的单次请求token计数
                    _perRequestTokenCounts.value = null
                    currentRequestInputTokenCount = 0
                    currentRequestOutputTokenCount = 0
                    currentRequestCachedInputTokenCount = 0

                    // 获取工具列表（如果启用Tool Call）
                    val availableTools = getAvailableToolsForFunction(
                        functionType = functionType,
                        chatId = chatId,
                        promptFunctionType = promptFunctionType,
                        roleCardId = roleCardId,
                        modelConfig = modelSnapshot.config
                    )
                    val tAfterGetTools = messageTimingNow()
                    AppLogger.d(TAG, "sendMessage本地耗时: getAvailableToolsForFunction=${tAfterGetTools - tAfterGetService}ms")

                    var finalProcessedInput = message
                    var finalPreparedHistory = preparedHistory
                    val beforeFinalizeContext =
                        applyPromptFinalizeHooks(
                            PromptHookContext(
                                stage = "before_finalize_prompt",
                                chatId = chatId,
                                functionType = functionType.name,
                                promptFunctionType = promptFunctionType.name,
                                rawInput = message,
                                processedInput = finalProcessedInput,
                                preparedHistory = finalPreparedHistory,
                                modelParameters = serializePromptHookModelParameters(modelParameters),
                                availableTools = serializePromptHookToolPrompts(availableTools),
                                metadata = buildPromptFinalizeMetadata(
                                    chatId = chatId,
                                    roleCardId = roleCardId,
                                    workspacePath = workspacePath,
                                    workspaceEnv = workspaceEnv,
                                    enableThinking = enableThinking,
                                    stream = stream,
                                    isSubTask = isSubTask
                                )
                            )
                        )
                    finalProcessedInput = beforeFinalizeContext.processedInput ?: finalProcessedInput
                    finalPreparedHistory = beforeFinalizeContext.preparedHistory
                    val beforeSendContext =
                        applyPromptFinalizeHooks(
                            beforeFinalizeContext.copy(
                                stage = "before_send_to_model",
                                processedInput = finalProcessedInput,
                                preparedHistory = finalPreparedHistory
                            )
                        )
                    finalProcessedInput = beforeSendContext.processedInput ?: finalProcessedInput
                    finalPreparedHistory = beforeSendContext.preparedHistory
                    if (!ChatUtils.isGeminiProviderModel(serviceForFunction.providerModel)) {
                        finalProcessedInput = ChatUtils.stripGeminiThoughtSignatureMeta(finalProcessedInput)
                        finalPreparedHistory = ChatUtils.stripGeminiThoughtSignatureMetaTurns(finalPreparedHistory)
                    }
                    if (!ChatUtils.isOpenAIResponsesProviderModel(serviceForFunction.providerModel)) {
                        finalProcessedInput = ChatUtils.stripOpenAiResponsesReasoningMeta(finalProcessedInput)
                        finalPreparedHistory = ChatUtils.stripOpenAiResponsesReasoningMetaTurns(finalPreparedHistory)
                    }
                    val requestHistory =
                        applyFinalizedCurrentUserTurn(
                            preparedHistory = finalPreparedHistory,
                            originalCurrentMessage = message,
                            finalizedCurrentMessage = finalProcessedInput
                        ).mergeAdjacentTurns { previous, current ->
                            previous.kind == PromptTurnKind.USER &&
                                current.kind == PromptTurnKind.USER &&
                                previous.toolName == current.toolName
                        }
                    execContext.conversationHistory.clear()
                    execContext.conversationHistory.addAll(requestHistory)
                    estimatePreparedRequestWindow(
                        serviceForFunction = serviceForFunction,
                        preparedHistory = requestHistory,
                        availableTools = availableTools,
                        publishEstimate = true
                    )
                    
                    // 使用新的Stream API
                    AppLogger.d(TAG, "sendMessage请求前准备耗时: ${tAfterGetTools - startTime}ms, 流式输出: $stream")
                    val requestStartTime = messageTimingNow()
                    val responseStream =
                            serviceForFunction.sendMessage(
                                    context = this@EnhancedAIService.context,
                                    chatHistory = requestHistory,
                                    modelParameters = modelParameters,
                                    enableThinking = enableThinking,
                                    stream = stream,
                                    availableTools = availableTools,
                                    onTokensUpdated = { input, cachedInput, output ->
                                        currentRequestInputTokenCount = input.coerceAtLeast(0)
                                        currentRequestOutputTokenCount = output.coerceAtLeast(0)
                                        currentRequestCachedInputTokenCount = cachedInput.coerceAtLeast(0)
                                        _perRequestTokenCounts.value = Pair(input, output)
                                    },
                                    onNonFatalError = onNonFatalError
                            )
                    val revisableStream = responseStream as? TextStreamEventCarrier

                    // 收到第一个响应，更新状态
                    var isFirstChunk = true

                    // 创建一个新的轮次来管理内容
                    startAssistantResponseRound(execContext)
                    val revisionTracker = TextStreamRevisionTracker()
                    val revisionMutex = Mutex()

                    // 从原始stream收集内容并处理
                    var chunkCount = 0
                    var totalChars = 0
                    var lastLogTime = messageTimingNow()

                    coroutineScope {
                        val revisionJob =
                            revisableStream?.let { carrier ->
                                launch {
                                    carrier.eventChannel.collect { event ->
                                        execContext.eventChannel.emit(event)
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
                                                execContext.streamBuffer.clear()
                                                execContext.streamBuffer.append(snapshot)
                                                execContext.roundManager.updateContent(snapshot)
                                            }
                                        }
                                    }
                                }
                            }
 
                        try {
                            responseStream.collect { content ->
                                // 第一次收到响应，更新状态
                                if (isFirstChunk) {
                                    if (!isSubTask) {
                                    withContext(Dispatchers.Main) {
                                        _inputProcessingState.value =
                                                InputProcessingState.Receiving(context.getString(R.string.enhanced_receiving_response))
                                        }
                                    }
                                    isFirstChunk = false
                                    logMessageTiming(
                                        stage = "enhanced.sendMessage.firstResponseChunk",
                                        startTimeMs = requestStartTime,
                                        details = "functionType=$functionType, stream=$stream"
                                    )
                                }

                                // 累计统计
                                chunkCount++
                                totalChars += content.length

                                // 周期性日志
                                val currentTime = messageTimingNow()
                                if (currentTime - lastLogTime > 5000) { // 每5秒记录一次
                                    AppLogger.d(TAG, "已接收 $chunkCount 个内容块，总计 $totalChars 个字符")
                                    lastLogTime = currentTime
                                }

                                revisionMutex.withLock {
                                    revisionTracker.append(content)
                                }

                                // 更新streamBuffer，保持与原有逻辑一致
                                execContext.streamBuffer.append(content)

                                // 更新内容到轮次管理器
                                execContext.roundManager.updateContent(execContext.streamBuffer.toString())

                                // 发射当前内容片段
                                emit(content)
                            }
                        } finally {
                            revisionJob?.cancelAndJoin()
                        }
                    }

                    // Update accumulated token counts and persist them
                    val inputTokens = serviceForFunction.inputTokenCount
                    val cachedInputTokens = serviceForFunction.cachedInputTokenCount
                    val outputTokens = serviceForFunction.outputTokenCount
                    accumulatedInputTokenCount = saturatedTokenSum(accumulatedInputTokenCount, inputTokens)
                    accumulatedOutputTokenCount = saturatedTokenSum(accumulatedOutputTokenCount, outputTokens)
                    accumulatedCachedInputTokenCount =
                        saturatedTokenSum(accumulatedCachedInputTokenCount, cachedInputTokens)
                    currentRequestInputTokenCount = 0
                    currentRequestOutputTokenCount = 0
                    currentRequestCachedInputTokenCount = 0
                    apiPreferences.updateTokensForProviderModel(serviceForFunction.providerModel, inputTokens, outputTokens, cachedInputTokens)
                    
                    // Update request count
                    apiPreferences.incrementRequestCountForProviderModel(serviceForFunction.providerModel)

                    AppLogger.d(
                            TAG,
                            "Token count updated for $functionType. Input: $inputTokens, Output: $outputTokens, CachedInput: $cachedInputTokens. Turn Accumulated: $accumulatedInputTokenCount, $accumulatedOutputTokenCount, $accumulatedCachedInputTokenCount"
                    )
                    logMessageTiming(
                        stage = "enhanced.sendMessage.streamComplete",
                        startTimeMs = requestStartTime,
                        details = "functionType=$functionType, totalChars=$totalChars, stream=$stream"
                    )
                }
            } catch (e: CancellationException) {
                invalidateExecutionContext(execContext, "sendMessage.collect.cancelled")
                AppLogger.d(TAG, "sendMessage流被取消")
                throw e
            } catch (e: Exception) {
                // 用户取消导致的 Socket closed 是预期行为，不应作为错误处理
                if (e.message?.contains("Socket closed", ignoreCase = true) == true) {
                    if (isExecutionContextActive(execContext)) {
                        AppLogger.d(TAG, "Stream was cancelled by the user (Socket closed).")
                    } else {
                        AppLogger.d(TAG, "Stream closed after execution context was invalidated.")
                    }
                } else {
                    hadFatalError = true
                    // Handle any exceptions
                    AppLogger.e(TAG, "发送消息时发生错误: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value =
                                InputProcessingState.Error(message = context.getString(R.string.enhanced_error_with_message, e.message ?: ""))
                    }
                }

                // 发生无法处理的错误时，也应停止服务，但用户取消除外
                if (e.message?.contains("Socket closed", ignoreCase = true) != true) {
                    if (!isSubTask) stopAiService()
                }
            } finally {
                try {
                    // 确保流处理完成后调用；如果本轮已被取消，则不能再继续跑完成逻辑。
                    if (!hadFatalError && isExecutionContextActive(execContext)) {
                        val collector = this
                        withContext(Dispatchers.IO) {
                            processStreamCompletion(
                                execContext,
                                functionType,
                                promptFunctionType,
                                collector,
                                enableThinking,
                                enableMemoryAutoUpdate,
                                onNonFatalError,
                                onTokenLimitExceeded,
                                maxTokens,
                                tokenUsageThreshold,
                                isSubTask,
                                characterName,
                                avatarUri,
                                roleCardId,
                                chatId,
                                onToolInvocation,
                                notifyReplyOverride,
                                chatModelConfigIdOverride,
                                chatModelIndexOverride,
                                memorySpaceIdOverride,
                                stream,
                                enableGroupOrchestrationHint,
                                disableWarning
                            )
                        }
                    } else if (!hadFatalError) {
                        AppLogger.d(
                            TAG,
                            "跳过流完成处理：执行上下文已失效, id=${execContext.executionId}"
                        )
                    }
                } finally {
                    unregisterExecutionContext(execContext)
                    withContext(NonCancellable) {
                        releaseModelExecutionSnapshot(execContext)
                    }
                }
            }
        }
        return wrappedStream.withEventChannel(eventChannel)
    }

    /**
     * 使用流处理技术增强工具调用检测能力 这个方法通过流式XML解析来辅助识别工具调用，比单纯依赖正则表达式更可靠
     * @param content 需要检测工具调用的内容
     * @return 经过增强检测的内容，可能会修复格式问题
     */
    private suspend fun enhanceToolDetection(content: String): String {
        try {
            // 检查内容是否包含可能的工具调用标记
            if (!ChatMarkupRegex.containsToolTag(content)) {
                return content
            }

            // 创建字符流以应用流处理，使用 stream() 替代 asCharStream()
            val charStream = content.stream()

            // 使用XML插件来拆分流
            val plugins = listOf(StreamXmlPlugin())

            // 保存增强后的内容
            val enhancedContent = StringBuilder()

            // 追踪是否发现了工具标签
            var foundToolTag = false

            // 处理拆分的结果
            charStream.splitBy(plugins).collect { group ->
                when (val tag = group.tag) {
                    // 匹配到XML标签
                    is StreamXmlPlugin -> {
                        val xmlContent = StringBuilder()
                        group.stream.collect { char -> xmlContent.append(char) }

                        val xml = xmlContent.toString()
                        // 检查是否是工具标签
                        val tagName = ChatMarkupRegex.extractOpeningTagName(xml)
                        if (ChatMarkupRegex.isToolTagName(tagName) && tagName != null && isToolXmlBlock(xml, tagName)) {
                            foundToolTag = true
                            // 格式标准化，使其符合工具调用的正则表达式预期格式
                            val normalizedXml = normalizeToolXml(xml)
                            enhancedContent.append(normalizedXml)
                            AppLogger.d(TAG, "工具调用XML被增强流处理检测到并标准化")
                        } else {
                            // 保留其他XML标签
                            enhancedContent.append(xml)
                        }
                    }
                    // 纯文本内容
                    null -> {
                        val textContent = StringBuilder()
                        group.stream.collect { char -> textContent.append(char) }
                        enhancedContent.append(textContent.toString())
                    }
                    // 添加必要的else分支
                    else -> {
                        val textContent = StringBuilder()
                        group.stream.collect { char -> textContent.append(char) }
                        enhancedContent.append(textContent.toString())
                        AppLogger.w(TAG, "未知标签类型: ${tag::class.java.simpleName}")
                    }
                }
            }

            // 如果找到了工具标签，返回增强的内容；否则返回原始内容
            return if (foundToolTag) {
                AppLogger.d(TAG, "增强的XML工具检测完成")
                enhancedContent.toString()
            } else {
                content
            }
        } catch (e: Exception) {
            // 如果流处理失败，返回原始内容并记录错误
            AppLogger.e(TAG, "增强工具检测失败: ${e.message}", e)
            return content
        }
    }

    /**
     * 规范化工具XML以符合正则表达式预期
     * @param xml 原始XML文本
     * @return 标准化后的XML
     */
    private fun normalizeToolXml(xml: String): String {
        var result = xml.trim()
        val toolTagName = ChatMarkupRegex.extractOpeningTagName(result)

        // 确保工具名称格式正确
        if (ChatMarkupRegex.isToolTagName(toolTagName) && toolTagName != null) {
            result = result.replace(
                Regex("<${Regex.escape(toolTagName)}\\s+name\\s*=", RegexOption.IGNORE_CASE),
                "<$toolTagName name="
            )
        }

        // 确保参数格式正确
        result = result.replace(Regex("<param\\s+name\\s*="), "<param name=")

        return result
    }

    private fun isToolXmlBlock(xml: String, tagName: String): Boolean {
        val trimmed = xml.trim()
        if (trimmed.endsWith("/>")) {
            return true
        }
        return trimmed.contains("</$tagName>")
    }

    private data class TruncatedToolRoundRecovery(
        val repairedContent: String,
        val appendedSuffix: String,
        val invalidatedToolNames: List<String>
    )

    private fun detectAndRepairTruncatedToolRound(content: String): TruncatedToolRoundRecovery? {
        if (!content.contains("<tool", ignoreCase = true)) {
            return null
        }

        val completeToolBlocks = ChatMarkupRegex.toolCallPattern.findAll(content).toList()
        val openToolPattern =
            Regex(
                "<(${ChatMarkupRegex.TOOL_TAG_NAME_REGEX_SOURCE})\\b[^>]*",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )

        val candidate = openToolPattern.findAll(content).lastOrNull { match ->
            completeToolBlocks.none { block ->
                match.range.first >= block.range.first && match.range.last <= block.range.last
            }
        } ?: return null

        val fragment = content.substring(candidate.range.first)
        if (!Regex("\\bname\\s*=\\s*\"", RegexOption.IGNORE_CASE).containsMatchIn(fragment)) {
            return null
        }

        val tagName =
            (ChatMarkupRegex.extractOpeningTagName(fragment) ?: candidate.groupValues.getOrNull(1))
                ?.takeIf { ChatMarkupRegex.isToolTagName(it) }
                ?: ChatMarkupRegex.generateRandomToolTagName()

        if (Regex("</${Regex.escape(tagName)}\\s*>", RegexOption.IGNORE_CASE).containsMatchIn(fragment)) {
            return null
        }

        val appendedSuffix = buildTruncatedToolRepairSuffix(fragment, tagName)
        if (appendedSuffix.isEmpty()) {
            return null
        }
        val repairedContent = content + appendedSuffix
        val invalidatedToolNames =
            buildList {
                ChatMarkupRegex.toolCallPattern.findAll(content)
                    .mapNotNull { match ->
                        match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
                    }
                    .forEach { add(it) }
                extractXmlAttributeValue(fragment, "name")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { add(it) }
            }
                .distinct()
                .toList()

        return TruncatedToolRoundRecovery(
            repairedContent = repairedContent,
            appendedSuffix = appendedSuffix,
            invalidatedToolNames = invalidatedToolNames
        )
    }

    private fun buildTruncatedToolRepairSuffix(fragment: String, fallbackTagName: String): String {
        val toolTagName =
            fallbackTagName.takeIf { ChatMarkupRegex.isToolTagName(it) }
                ?: ChatMarkupRegex.generateRandomToolTagName()

        val openingTagEnd = fragment.indexOf('>')
        if (openingTagEnd < 0) {
            return completePartialOpenTag(fragment, toolTagName, defaultNameValue = "truncated_tool_call") +
                "</$toolTagName>"
        }

        val body = fragment.substring(openingTagEnd + 1)
        val completeTagPattern =
            Regex("</?([A-Za-z][A-Za-z0-9_]*)\\b[^>]*>", RegexOption.IGNORE_CASE)
        var openParamCount = 0

        completeTagPattern.findAll(body).forEach { match ->
            val tagText = match.value
            val tagName = match.groupValues[1]
            val isClosing = tagText.startsWith("</")

            when {
                tagName.equals("param", ignoreCase = true) -> {
                    if (isClosing) {
                        if (openParamCount > 0) {
                            openParamCount--
                        }
                    } else if (!tagText.endsWith("/>")) {
                        openParamCount++
                    }
                }

                tagName.equals(toolTagName, ignoreCase = true) && isClosing -> {
                    return ""
                }
            }
        }

        val suffix = StringBuilder()
        val trailingPartialTag = extractTrailingPartialTag(fragment)
        var toolClosedBySuffix = false

        if (trailingPartialTag != null) {
            when {
                isPartialClosingTagFor(trailingPartialTag, "param") -> {
                    suffix.append(completePartialClosingTag(trailingPartialTag, "param"))
                    if (openParamCount > 0) {
                        openParamCount--
                    }
                }

                isPartialOpeningTagFor(trailingPartialTag, "param") -> {
                    val completedTagNameSuffix = completePartialTagName(trailingPartialTag, "param")
                    suffix.append(
                        completePartialOpenTag(
                            trailingPartialTag + completedTagNameSuffix,
                            "param",
                            defaultNameValue = "_truncated_fragment"
                        )
                    )
                    suffix.insert(0, completedTagNameSuffix)
                    openParamCount++
                }

                isPartialClosingTagFor(trailingPartialTag, toolTagName) -> {
                    suffix.append(completePartialClosingTag(trailingPartialTag, toolTagName))
                    toolClosedBySuffix = true
                }

                isPartialOpeningTagFor(trailingPartialTag, toolTagName) -> {
                    val completedTagNameSuffix =
                        completePartialTagName(trailingPartialTag, toolTagName)
                    suffix.append(
                        completePartialOpenTag(
                            trailingPartialTag + completedTagNameSuffix,
                            toolTagName,
                            defaultNameValue = "truncated_tool_call"
                        )
                    )
                    suffix.insert(0, completedTagNameSuffix)
                }

                trailingPartialTag == "<" -> {
                    suffix.append("!-- truncated -->")
                }
            }
        }

        repeat(openParamCount) {
            suffix.append("</param>")
        }
        if (!toolClosedBySuffix) {
            suffix.append("</$toolTagName>")
        }
        return suffix.toString()
    }

    private fun extractXmlAttributeValue(source: String, attributeName: String): String? {
        val pattern =
            Regex(
                "\\b${Regex.escape(attributeName)}\\s*=\\s*\"([^\"]*)\"",
                RegexOption.IGNORE_CASE
            )
        return pattern.find(source)?.groupValues?.getOrNull(1)
    }

    private fun extractTrailingPartialTag(fragment: String): String? {
        val lastOpen = fragment.lastIndexOf('<')
        val lastClose = fragment.lastIndexOf('>')
        if (lastOpen <= lastClose) {
            return null
        }
        return fragment.substring(lastOpen)
    }

    private fun completePartialOpenTag(
        partialTag: String,
        tagName: String,
        defaultNameValue: String
    ): String {
        val suffix = StringBuilder()
        val normalizedPartial = partialTag.lowercase()
        val normalizedTagName = tagName.lowercase()
        val tagPrefix = "<$normalizedTagName"
        val attrValueOpenPattern = Regex("\\bname\\s*=\\s*\"[^\"]*$", RegexOption.IGNORE_CASE)
        val attrEqPattern = Regex("\\bname\\s*=\\s*$", RegexOption.IGNORE_CASE)
        val defaultAttrPattern =
            Regex("^<${Regex.escape(tagName)}\\s*$", RegexOption.IGNORE_CASE)
        val partialNamePatterns =
            listOf(
                Regex("\\bn$", RegexOption.IGNORE_CASE) to "ame=\"\"",
                Regex("\\bna$", RegexOption.IGNORE_CASE) to "me=\"\"",
                Regex("\\bnam$", RegexOption.IGNORE_CASE) to "e=\"\"",
                Regex("\\bname$", RegexOption.IGNORE_CASE) to "=\"\""
            )
        val partialNameCompletion =
            partialNamePatterns.firstOrNull { it.first.containsMatchIn(partialTag) }?.second

        when {
            attrValueOpenPattern.containsMatchIn(partialTag) -> suffix.append("\"")
            attrEqPattern.containsMatchIn(partialTag) -> suffix.append("\"\"")
            partialNameCompletion != null -> suffix.append(partialNameCompletion)
            normalizedPartial == tagPrefix || defaultAttrPattern.matches(partialTag) -> {
                suffix.append(" name=\"")
                suffix.append(defaultNameValue)
                suffix.append("\"")
            }
        }

        if (((partialTag.length + suffix.length) > 0) &&
            ((partialTag + suffix.toString()).count { it == '"' } % 2 != 0)
        ) {
            suffix.append("\"")
        }
        if (!(partialTag + suffix.toString()).endsWith(">")) {
            suffix.append(">")
        }
        return suffix.toString()
    }

    private fun isPartialOpeningTagFor(partialTag: String, tagName: String): Boolean {
        if (!partialTag.startsWith("<") || partialTag.startsWith("</")) {
            return false
        }
        val currentName = Regex("^<([A-Za-z_]*)", RegexOption.IGNORE_CASE)
            .find(partialTag)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            ?: return false
        if (currentName.isEmpty()) {
            return false
        }
        return tagName.lowercase().startsWith(currentName)
    }

    private fun isPartialClosingTagFor(partialTag: String, tagName: String): Boolean {
        if (!partialTag.startsWith("</")) {
            return false
        }
        val currentName = Regex("^</([A-Za-z_]*)", RegexOption.IGNORE_CASE)
            .find(partialTag)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            ?: return false
        if (currentName.isEmpty()) {
            return false
        }
        return tagName.lowercase().startsWith(currentName)
    }

    private fun completePartialTagName(partialTag: String, tagName: String): String {
        val currentName = Regex("^</?([A-Za-z_]*)", RegexOption.IGNORE_CASE)
            .find(partialTag)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            .orEmpty()
        return tagName.substring(currentName.length.coerceAtMost(tagName.length))
    }

    private fun completePartialClosingTag(partialTag: String, tagName: String): String {
        return buildString {
            append(completePartialTagName(partialTag, tagName))
            append(">")
        }
    }

    /** 在处理完流后调用，使用增强的工具检测功能 */
    private suspend fun processStreamCompletion(
            context: MessageExecutionContext,
            functionType: FunctionType = FunctionType.CHAT,
            promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
            collector: StreamCollector<String>,
            enableThinking: Boolean = false,
            enableMemoryAutoUpdate: Boolean = true,
            onNonFatalError: suspend (error: String) -> Unit,
            onTokenLimitExceeded: (suspend () -> Unit)? = null,
            maxTokens: Int,
            tokenUsageThreshold: Double,
            isSubTask: Boolean,
            characterName: String? = null,
            avatarUri: String? = null,
            roleCardId: String? = null,
            chatId: String? = null,
            onToolInvocation: (suspend (String) -> Unit)? = null,
            notifyReplyOverride: Boolean? = null,
            chatModelConfigIdOverride: String? = null,
            chatModelIndexOverride: Int? = null,
            memorySpaceIdOverride: String? = null,
            stream: Boolean = true,
            enableGroupOrchestrationHint: Boolean = false,
            disableWarning: Boolean = false
    ) {
        try {
            val startTime = messageTimingNow()
            // If conversation is no longer active, return immediately
            if (!context.isConversationActive.get()) {
                return
            }

            // Get response content
            val content = context.streamBuffer.toString().trim()

            // If content is empty, it means an error likely occurred or the model returned nothing.
            // We must still finalize the conversation to reset the state correctly.
            if (content.isEmpty()) {
                AppLogger.d(TAG, "Stream content is empty. Finalizing conversation state.")
                finalizeAssistantResponse(
                    context = context,
                    content = content,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    onNonFatalError = onNonFatalError,
                    isSubTask = isSubTask,
                    chatId = chatId,
                    notifyReplyOverride = notifyReplyOverride,
                    memorySpaceIdOverride = memorySpaceIdOverride
                )
                return
            }

            // If content is empty, finish immediately
            if (content.isEmpty()) {
                return
            }

            // 禁止“纯思考输出”：移除 thinking 后正文为空时，发出专用告警并回传给 AI 继续生成
            val contentWithoutThinking = ChatUtils.removeThinkingContent(content)
            if (contentWithoutThinking.isEmpty()) {
                if (disableWarning) {
                    AppLogger.w(TAG, "检测到纯思考输出，disableWarning=true，直接结束本轮而不注入警告")
                    finalizeAssistantResponse(
                        context = context,
                        content = context.roundManager.getDisplayContent(),
                        enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                        onNonFatalError = onNonFatalError,
                        isSubTask = isSubTask,
                        chatId = chatId,
                        characterName = characterName,
                        avatarUri = avatarUri,
                        notifyReplyOverride = notifyReplyOverride,
                        memorySpaceIdOverride = memorySpaceIdOverride
                    )
                    return
                }
                val pureThinkingWarning =
                        ConversationMarkupManager.createWarningStatus(
                                this@EnhancedAIService.context.getString(
                                        R.string.enhanced_pure_thinking_only_warning
                                )
                        )
                context.roundManager.appendContent("\n$pureThinkingWarning")
                collector.emit(pureThinkingWarning)
                try {
                    context.conversationHistory.add(
                        PromptTurn(kind = PromptTurnKind.TOOL_RESULT, content = pureThinkingWarning)
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "添加纯思考告警到历史记录失败", e)
                    return
                }
                AppLogger.w(TAG, "检测到纯思考输出（removeThinking后正文为空），已回传告警给AI继续生成")
                handleToolInvocation(
                        toolInvocations = emptyList(),
                        context = context,
                        functionType = functionType,
                        promptFunctionType = promptFunctionType,
                        collector = collector,
                        enableThinking = enableThinking,
                        enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                        onNonFatalError = onNonFatalError,
                        onTokenLimitExceeded = onTokenLimitExceeded,
                        maxTokens = maxTokens,
                        tokenUsageThreshold = tokenUsageThreshold,
                        isSubTask = isSubTask,
                        characterName = characterName,
                        avatarUri = avatarUri,
                        roleCardId = roleCardId,
                        chatId = chatId,
                        onToolInvocation = onToolInvocation,
                        notifyReplyOverride = notifyReplyOverride,
                        chatModelConfigIdOverride = chatModelConfigIdOverride,
                        chatModelIndexOverride = chatModelIndexOverride,
                        memorySpaceIdOverride = memorySpaceIdOverride,
                        stream = stream,
                        enableGroupOrchestrationHint = enableGroupOrchestrationHint,
                        toolResultOverrideMessage = pureThinkingWarning,
                        disableWarning = disableWarning
                )
                return
            }

            // 使用增强的工具检测功能处理内容
            val enhancedContent = enhanceToolDetection(content)
            val truncatedToolRecovery = detectAndRepairTruncatedToolRound(content)
            val finalContent = truncatedToolRecovery?.repairedContent ?: enhancedContent

            // 截断修复仅追加缺失后缀，不撤回已经发出的内容
            if (truncatedToolRecovery != null) {
                val appendedSuffix = truncatedToolRecovery.appendedSuffix
                if (appendedSuffix.isNotEmpty()) {
                    context.streamBuffer.append(appendedSuffix)
                    context.roundManager.updateContent(context.streamBuffer.toString())
                    collector.emit(appendedSuffix)
                }
            } else if (finalContent != content) {
                context.streamBuffer.setLength(0)
                context.streamBuffer.append(finalContent)
                context.roundManager.updateContent(finalContent)
            }

            // 预先提取工具调用信息，避免重复解析
            val extractedToolInvocations =
                    if (truncatedToolRecovery == null) {
                        ToolExecutionManager.extractToolInvocations(finalContent)
                    } else {
                        emptyList()
                    }

            // Check again if conversation is active
            if (!context.isConversationActive.get()) {
                return
            }

            // Add current assistant message to conversation history
            try {
                context.conversationHistory.add(
                    PromptTurn(
                        kind = PromptTurnKind.ASSISTANT,
                        content = context.roundManager.getCurrentRoundContent()
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "添加助手消息到历史记录失败", e)
                return
            }

            // Check again if conversation is active
            if (!context.isConversationActive.get()) {
                return
            }

            if (truncatedToolRecovery != null) {
                if (disableWarning) {
                    AppLogger.w(
                        TAG,
                        "检测到未闭合工具调用，disableWarning=true，直接结束本轮而不注入警告。invalidated=${truncatedToolRecovery.invalidatedToolNames}"
                    )
                    finalizeAssistantResponse(
                        context = context,
                        content = context.roundManager.getDisplayContent(),
                        enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                        onNonFatalError = onNonFatalError,
                        isSubTask = isSubTask,
                        chatId = chatId,
                        characterName = characterName,
                        avatarUri = avatarUri,
                        notifyReplyOverride = notifyReplyOverride,
                        memorySpaceIdOverride = memorySpaceIdOverride
                    )
                    return
                }
                val warningStatus =
                        ConversationMarkupManager.createWarningStatus(
                                this@EnhancedAIService.context.getString(
                                        R.string.enhanced_truncated_tool_call_warning
                                )
                        )
                val warningDisplayContent = "\n$warningStatus"
                context.roundManager.appendContent(warningDisplayContent)
                collector.emit(warningDisplayContent)
                AppLogger.w(
                        TAG,
                        "检测到未闭合工具调用，本轮工具全部作废。invalidated=${truncatedToolRecovery.invalidatedToolNames}"
                )
                handleToolInvocation(
                        toolInvocations = emptyList(),
                        context = context,
                        functionType = functionType,
                        promptFunctionType = promptFunctionType,
                        collector = collector,
                        enableThinking = enableThinking,
                        enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                        onNonFatalError = onNonFatalError,
                        onTokenLimitExceeded = onTokenLimitExceeded,
                        maxTokens = maxTokens,
                        tokenUsageThreshold = tokenUsageThreshold,
                        isSubTask = isSubTask,
                        characterName = characterName,
                        avatarUri = avatarUri,
                        roleCardId = roleCardId,
                        chatId = chatId,
                        onToolInvocation = onToolInvocation,
                        notifyReplyOverride = notifyReplyOverride,
                        chatModelConfigIdOverride = chatModelConfigIdOverride,
                        chatModelIndexOverride = chatModelIndexOverride,
                        memorySpaceIdOverride = memorySpaceIdOverride,
                        stream = stream,
                        enableGroupOrchestrationHint = enableGroupOrchestrationHint,
                        toolResultOverrideMessage = warningStatus,
                        disableWarning = disableWarning
                )
                return
            }

            // Main flow: Detect and process tool invocations
            if (extractedToolInvocations.isNotEmpty()) {
                logMessageTiming(
                    stage = "enhanced.processStreamCompletion.detectToolInvocations",
                    startTimeMs = startTime,
                    details = "count=${extractedToolInvocations.size}"
                )
                handleToolInvocation(
                        extractedToolInvocations,
                        context,
                        functionType,
                        promptFunctionType,
                        collector,
                        enableThinking,
                        enableMemoryAutoUpdate,
                        onNonFatalError,
                        onTokenLimitExceeded,
                        maxTokens,
                        tokenUsageThreshold,
                        isSubTask,
                        characterName,
                        avatarUri,
                        roleCardId,
                        chatId,
                        onToolInvocation,
                        notifyReplyOverride,
                        chatModelConfigIdOverride,
                        chatModelIndexOverride,
                        memorySpaceIdOverride,
                        stream = stream,
                        enableGroupOrchestrationHint = enableGroupOrchestrationHint,
                        disableWarning = disableWarning
                )
                return
            }

            finalizeAssistantResponse(
                context = context,
                content = context.roundManager.getDisplayContent(),
                enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                onNonFatalError = onNonFatalError,
                isSubTask = isSubTask,
                chatId = chatId,
                characterName = characterName,
                avatarUri = avatarUri,
                notifyReplyOverride = notifyReplyOverride,
                memorySpaceIdOverride = memorySpaceIdOverride
            )
            logMessageTiming(
                stage = "enhanced.processStreamCompletion.complete",
                startTimeMs = startTime
            )
        } catch (e: Exception) {
            // Catch any exceptions in the processing flow
            AppLogger.e(TAG, "处理流完成时发生错误", e)
            withContext(Dispatchers.Main) {
                _inputProcessingState.value = InputProcessingState.Idle
            }
        }
    }

    /** Finalize an assistant response without relying on status-tag control flow. */
    private suspend fun finalizeAssistantResponse(
        context: MessageExecutionContext,
        content: String,
        enableMemoryAutoUpdate: Boolean,
        onNonFatalError: suspend (error: String) -> Unit,
        isSubTask: Boolean,
        chatId: String? = null,
        characterName: String? = null,
        avatarUri: String? = null,
        notifyReplyOverride: Boolean? = null,
        memorySpaceIdOverride: String? = null
    ) {
        // Mark conversation as complete
        context.isConversationActive.set(false)

        // 清除内容池
        // roundManager.clearContent()
        
        // 保存最后的回复内容用于通知
        lastReplyContent = context.roundManager.getDisplayContent()

        // Ensure input processing state is updated to completed
        if (!isSubTask) {
            withContext(Dispatchers.Main) {
                _inputProcessingState.value = InputProcessingState.Completed
            }
        }

        if (enableMemoryAutoUpdate && !isSubTask && content.isNotBlank()) {
            runCatching {
                val currentChatId = chatId?.takeIf { it.isNotBlank() }
                val profileId =
                    memorySpaceIdOverride?.takeIf { it.isNotBlank() }
                        ?: preferencesManager.activeMemorySpaceIdFlow.first()
                if (currentChatId.isNullOrBlank()) {
                    AppLogger.w(TAG, "自动保存长期记忆入队跳过：chatId为空")
                } else {
                    MemoryAutoSaveCandidateRepository(this@EnhancedAIService.context, profileId)
                        .enqueue(
                            chatId = currentChatId,
                            triggerMessageTimestamp = System.currentTimeMillis()
                        )
                }
            }.onFailure { e ->
                AppLogger.e(TAG, "自动保存长期记忆候选入队失败", e)
                onNonFatalError(
                    this@EnhancedAIService.context.getString(
                        R.string.chat_auto_update_memory_failed,
                        e.message ?: ""
                    )
                )
            }
        }

        if (!isSubTask) {
            notifyReplyCompleted(chatId, characterName, avatarUri, notifyReplyOverride)
            stopAiService(characterName, avatarUri)
        }
    }

    /** Handle tool invocation processing - simplified version without callbacks */
    private suspend fun handleToolInvocation(
        toolInvocations: List<ToolInvocation>,
        context: MessageExecutionContext,
        functionType: FunctionType = FunctionType.CHAT,
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        collector: StreamCollector<String>,
        enableThinking: Boolean = false,
        enableMemoryAutoUpdate: Boolean = true,
        onNonFatalError: suspend (error: String) -> Unit,
        onTokenLimitExceeded: (suspend () -> Unit)? = null,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        isSubTask: Boolean,
        characterName: String? = null,
        avatarUri: String? = null,
        roleCardId: String? = null,
        chatId: String? = null,
        onToolInvocation: (suspend (String) -> Unit)? = null,
        notifyReplyOverride: Boolean? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        memorySpaceIdOverride: String? = null,
        stream: Boolean = true,
        enableGroupOrchestrationHint: Boolean = false,
        toolResultOverrideMessage: String? = null,
        disableWarning: Boolean = false
    ) {
        val startTime = messageTimingNow()

        toolInvocations.forEach { invocation ->
            onToolInvocation?.invoke(invocation.tool.name)
        }

        if (!isSubTask && toolInvocations.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                val toolNames = toolInvocations.joinToString(", ") { resolveToolDisplayName(it.tool) }
                _inputProcessingState.value = InputProcessingState.ExecutingTool(toolNames)
            }
        }

        val processToolJob = toolProcessingScope.launch {
            val modelSnapshot = getModelExecutionSnapshot(
                context,
                functionType,
                chatModelConfigIdOverride,
                chatModelIndexOverride
            )
            val config = modelSnapshot.config
            val allToolResults = ToolExecutionManager.executeInvocations(
                invocations = toolInvocations,
                context = this@EnhancedAIService.context,
                toolHandler = toolHandler,
                packageManager = packageManager,
                collector = collector,
                toolExposureMode = ToolExposureMode.resolve(config.apiProviderType),
                callerName = characterName,
                callerChatId = chatId,
                callerCardId = roleCardId
            )

            if (allToolResults.isNotEmpty()) {
                AppLogger.d(TAG, "所有工具结果收集完毕，准备最终处理。")
                processToolResults(
                    allToolResults, context, functionType, promptFunctionType, collector, enableThinking,
                    enableMemoryAutoUpdate, onNonFatalError, onTokenLimitExceeded, maxTokens, tokenUsageThreshold, isSubTask,
                    characterName, avatarUri, roleCardId, chatId, onToolInvocation, notifyReplyOverride,
                    chatModelConfigIdOverride, chatModelIndexOverride, memorySpaceIdOverride, stream, enableGroupOrchestrationHint,
                    disableWarning = disableWarning
                )
            } else if (!toolResultOverrideMessage.isNullOrEmpty()) {
                AppLogger.d(TAG, "0工具路由命中，使用覆盖消息继续请求AI。")
                processToolResults(
                    results = emptyList(),
                    context = context,
                    functionType = functionType,
                    promptFunctionType = promptFunctionType,
                    collector = collector,
                    enableThinking = enableThinking,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    onNonFatalError = onNonFatalError,
                    onTokenLimitExceeded = onTokenLimitExceeded,
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    isSubTask = isSubTask,
                    characterName = characterName,
                    avatarUri = avatarUri,
                    roleCardId = roleCardId,
                    chatId = chatId,
                    onToolInvocation = onToolInvocation,
                    notifyReplyOverride = notifyReplyOverride,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    memorySpaceIdOverride = memorySpaceIdOverride,
                    stream = stream,
                    enableGroupOrchestrationHint = enableGroupOrchestrationHint,
                    toolResultMessageOverride = toolResultOverrideMessage,
                    disableWarning = disableWarning
                )
            }

            logMessageTiming(
                stage = "enhanced.handleToolInvocation.complete",
                startTimeMs = startTime,
                details = "toolCount=${toolInvocations.size}"
            )
        }

        val invocationId = java.util.UUID.randomUUID().toString()
        toolExecutionJobs[invocationId] = processToolJob

        try {
            processToolJob.join()
        } finally {
            toolExecutionJobs.remove(invocationId)
        }
    }


    /** Process tool execution result - simplified version without callbacks */
    private suspend fun processToolResults(
            results: List<ToolResult>,
            context: MessageExecutionContext,
            functionType: FunctionType = FunctionType.CHAT,
            promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
            collector: StreamCollector<String>,
            enableThinking: Boolean = false,
            enableMemoryAutoUpdate: Boolean = true,
            onNonFatalError: suspend (error: String) -> Unit,
            onTokenLimitExceeded: (suspend () -> Unit)? = null,
            maxTokens: Int,
            tokenUsageThreshold: Double,
            isSubTask: Boolean,
            characterName: String? = null,
            avatarUri: String? = null,
            roleCardId: String? = null,
            chatId: String? = null,
            onToolInvocation: (suspend (String) -> Unit)? = null,
            notifyReplyOverride: Boolean? = null,
            chatModelConfigIdOverride: String? = null,
            chatModelIndexOverride: Int? = null,
            memorySpaceIdOverride: String? = null,
            stream: Boolean = true,
            enableGroupOrchestrationHint: Boolean = false,
            toolResultMessageOverride: String? = null,
            disableWarning: Boolean = false
    ) {
        val startTime = messageTimingNow()
        val toolNames = results.joinToString(", ") { it.toolName }
        val rawToolResultMessage =
            toolResultMessageOverride ?: ConversationMarkupManager.buildBoundedToolResultMessage(results)
        val toolResultMessage =
            if (rawToolResultMessage.length <= ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS) {
                rawToolResultMessage
            } else {
                AppLogger.w(
                    TAG,
                    "工具结果消息超过最终兜底上限，已静默截断。原长度: ${rawToolResultMessage.length}"
                )
                rawToolResultMessage.take(ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS)
            }

        if (toolResultMessage.isBlank()) {
            AppLogger.w(TAG, "工具结果消息为空，跳过后续AI请求")
            return
        }

        val displayToolNames = if (toolNames.isNotBlank()) toolNames else "warning"
        if (results.isNotEmpty()) {
            AppLogger.d(TAG, "开始处理工具结果: $toolNames, 成功: ${results.all { it.success }}")
        } else {
            AppLogger.d(TAG, "开始处理0工具覆盖消息，长度: ${toolResultMessage.length}")
        }

        // Add transition state
        if (!isSubTask) {
        withContext(Dispatchers.Main) {
            _inputProcessingState.value = InputProcessingState.ProcessingToolResult(displayToolNames)
            }
        }

        // Check if conversation is still active
        if (!context.isConversationActive.get()) {
            return
        }

        // Add tool result to conversation history
        context.conversationHistory.add(
            PromptTurn(
                kind = PromptTurnKind.TOOL_RESULT,
                content = toolResultMessage,
                toolName = toolNames.ifBlank { null }
            )
        )

        val normalizedChatHistory =
            conversationService.normalizeConversationHistoryForModel(context.conversationHistory)
        context.conversationHistory.clear()
        context.conversationHistory.addAll(normalizedChatHistory)

        // Get current conversation history is now just the normalized context history
        val currentChatHistory = context.conversationHistory

        // 不再需要，因为结果在调用时已实时输出
        // context.roundManager.appendContent(toolResultMessage)
        // try { collector.emit(toolResultMessage) } catch (_: Exception) {}

        // Start new round - ensure tool execution response will be shown in a new message
        startAssistantResponseRound(context)

        // Clearly show we're preparing to send tool result to AI
        if (!isSubTask) {
        withContext(Dispatchers.Main) {
            _inputProcessingState.value = InputProcessingState.ProcessingToolResult(displayToolNames)
            }
        }

        // Add short delay to make state change more visible
        delay(300)

        // Get all model parameters from preferences (with enabled state)
        val modelSnapshot = getModelExecutionSnapshot(
            context,
            functionType,
            chatModelConfigIdOverride,
            chatModelIndexOverride
        )
        val modelParameters = modelSnapshot.modelParameters

        // 获取对应功能类型的AIService实例
        val serviceForFunction = modelSnapshot.service
        
        // 获取工具列表（如果启用Tool Call）- 提前获取，以便在token计算中使用
        val availableTools = getAvailableToolsForFunction(
            functionType = functionType,
            chatId = chatId,
            promptFunctionType = promptFunctionType,
            roleCardId = roleCardId,
            modelConfig = modelSnapshot.config
        )
 
        val currentTokens = estimatePreparedRequestWindow(
            serviceForFunction = serviceForFunction,
            preparedHistory = currentChatHistory,
            availableTools = availableTools,
            publishEstimate = true
        )

        // After a tool call, check if token usage exceeds the threshold
        if (maxTokens > 0) {
            val usageRatio = currentTokens.toDouble() / maxTokens.toDouble()

            if (usageRatio >= tokenUsageThreshold) {
                AppLogger.w(TAG, "Token usage ($usageRatio) exceeds threshold ($tokenUsageThreshold) after tool call. Triggering summary.")
                onTokenLimitExceeded?.invoke()
                context.isConversationActive.set(false)
                if (!isSubTask) {
                    stopAiService(characterName, avatarUri)
                }
                // 关键修复：在触发总结后，直接返回，因为后续流程将由回调处理
                return
            }
        }

        // 清空之前的单次请求token计数
        _perRequestTokenCounts.value = null
        currentRequestInputTokenCount = 0
        currentRequestOutputTokenCount = 0
        currentRequestCachedInputTokenCount = 0
        
        // 使用新的Stream API处理工具执行结果
        withContext(Dispatchers.IO) {
            try {
                // 发送消息并获取响应流
                val aiStartTime = messageTimingNow()
                val responseStream =
                        serviceForFunction.sendMessage(
                                context = this@EnhancedAIService.context,
                                chatHistory = currentChatHistory,
                                modelParameters = modelParameters,
                                enableThinking = enableThinking,
                                stream = stream,
                                availableTools = availableTools,
                                onTokensUpdated = { input, cachedInput, output ->
                                    currentRequestInputTokenCount = input.coerceAtLeast(0)
                                    currentRequestOutputTokenCount = output.coerceAtLeast(0)
                                    currentRequestCachedInputTokenCount = cachedInput.coerceAtLeast(0)
                                    _perRequestTokenCounts.value = Pair(input, output)
                                },
                                onNonFatalError = onNonFatalError
                        )

                // 更新状态为接收中
                if (!isSubTask) {
                withContext(Dispatchers.Main) {
                    _inputProcessingState.value =
                            InputProcessingState.Receiving(this@EnhancedAIService.context.getString(R.string.enhanced_receiving_tool_result))
                    }
                }

                // 处理流
                var chunkCount = 0
                var totalChars = 0
                var lastLogTime = messageTimingNow()
                var isFirstChunk = true
                val revisableStream = responseStream as? TextStreamEventCarrier
                val revisionTracker = TextStreamRevisionTracker()
                val revisionMutex = Mutex()

                coroutineScope {
                    val revisionJob =
                        revisableStream?.let { carrier ->
                            launch {
                                carrier.eventChannel.collect { event ->
                                    context.eventChannel.emit(event)
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
                                            context.streamBuffer.clear()
                                            context.streamBuffer.append(snapshot)
                                            context.roundManager.updateContent(snapshot)
                                        }
                                    }
                                }
                            }
                        }

                    try {
                        responseStream.collect { content ->
                            if (isFirstChunk) {
                                isFirstChunk = false
                                logMessageTiming(
                                    stage = "enhanced.processToolResults.firstResponseChunk",
                                    startTimeMs = aiStartTime,
                                    details = "toolNames=$displayToolNames, stream=$stream"
                                )
                            }

                            revisionMutex.withLock {
                                revisionTracker.append(content)
                            }

                            // 更新streamBuffer
                            context.streamBuffer.append(content)

                            // 更新内容到轮次管理器
                            context.roundManager.updateContent(context.streamBuffer.toString())

                            // 累计统计
                            chunkCount++
                            totalChars += content.length

                            // 定期记录日志
                            val currentTime = messageTimingNow()
                            if (currentTime - lastLogTime > 5000) { // 每5秒记录一次
                                lastLogTime = currentTime
                            }

                            // 通过收集器将内容发射出去，让UI可以接收到
                            collector.emit(content)
                        }
                    } finally {
                        revisionJob?.cancelAndJoin()
                    }
                }

                // Update accumulated token counts and persist them
                val inputTokens = serviceForFunction.inputTokenCount
                val cachedInputTokens = serviceForFunction.cachedInputTokenCount
                val outputTokens = serviceForFunction.outputTokenCount
                accumulatedInputTokenCount = saturatedTokenSum(accumulatedInputTokenCount, inputTokens)
                accumulatedOutputTokenCount = saturatedTokenSum(accumulatedOutputTokenCount, outputTokens)
                accumulatedCachedInputTokenCount =
                    saturatedTokenSum(accumulatedCachedInputTokenCount, cachedInputTokens)
                currentRequestInputTokenCount = 0
                currentRequestOutputTokenCount = 0
                currentRequestCachedInputTokenCount = 0
                apiPreferences.updateTokensForProviderModel(serviceForFunction.providerModel, inputTokens, outputTokens, cachedInputTokens)
                
                // Update request count
                apiPreferences.incrementRequestCountForProviderModel(serviceForFunction.providerModel)

                AppLogger.d(
                        TAG,
                        "Token count updated after tool result for $functionType. Input: $inputTokens, Output: $outputTokens, CachedInput: $cachedInputTokens. Turn Accumulated: $accumulatedInputTokenCount, $accumulatedOutputTokenCount, $accumulatedCachedInputTokenCount"
                )

                logMessageTiming(
                    stage = "enhanced.processToolResults.aiResponseComplete",
                    startTimeMs = aiStartTime,
                    details = "toolNames=$displayToolNames, totalChars=$totalChars"
                )

                // 流处理完成，处理完成逻辑
                processStreamCompletion(
                    context,
                    functionType,
                    promptFunctionType,
                    collector,
                    enableThinking,
                    enableMemoryAutoUpdate,
                    onNonFatalError,
                    onTokenLimitExceeded,
                    maxTokens,
                    tokenUsageThreshold,
                    isSubTask,
                    characterName,
                    avatarUri,
                    roleCardId,
                    chatId,
                    onToolInvocation,
                    notifyReplyOverride,
                    chatModelConfigIdOverride,
                    chatModelIndexOverride,
                    memorySpaceIdOverride,
                    stream,
                    enableGroupOrchestrationHint,
                    disableWarning
                )
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "处理工具执行结果被取消")
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "处理工具执行结果时出错", e)
                withContext(Dispatchers.Main) {
                    _inputProcessingState.value =
                            InputProcessingState.Error(this@EnhancedAIService.context.getString(R.string.enhanced_process_tool_result_failed, e.message ?: ""))
                }
            } finally {
                logMessageTiming(
                    stage = "enhanced.processToolResults.complete",
                    startTimeMs = startTime,
                    details = "toolNames=$displayToolNames, resultCount=${results.size}"
                )
            }
        }
    }
    /**
     * Get the current input token count from the last API call
     * @return The number of input tokens used in the most recent request
     */
    fun getCurrentInputTokenCount(): Int {
        return accumulatedInputTokenCount
    }

    /**
     * Get the current output token count from the last API call
     * @return The number of output tokens generated in the most recent response
     */
    fun getCurrentOutputTokenCount(): Int {
        return accumulatedOutputTokenCount
    }

    /**
     * Get the current cached input token count accumulated across the current turn
     * @return The number of cached input tokens used in the current turn
     */
    fun getCurrentCachedInputTokenCount(): Int {
        return accumulatedCachedInputTokenCount
    }

    fun captureCurrentTurnTokenSnapshot(): TurnTokenSnapshot {
        return TurnTokenSnapshot(
            inputTokens = saturatedTokenSum(accumulatedInputTokenCount, currentRequestInputTokenCount),
            outputTokens = saturatedTokenSum(accumulatedOutputTokenCount, currentRequestOutputTokenCount),
            cachedInputTokens =
                saturatedTokenSum(accumulatedCachedInputTokenCount, currentRequestCachedInputTokenCount)
        )
    }

    fun setCurrentTurnTokenCounts(
        inputTokens: Int,
        outputTokens: Int,
        cachedInputTokens: Int = 0
    ) {
        accumulatedInputTokenCount = inputTokens.coerceAtLeast(0)
        accumulatedOutputTokenCount = outputTokens.coerceAtLeast(0)
        accumulatedCachedInputTokenCount = cachedInputTokens.coerceAtLeast(0)
        currentRequestInputTokenCount = 0
        currentRequestOutputTokenCount = 0
        currentRequestCachedInputTokenCount = 0
        _perRequestTokenCounts.value =
            Pair(accumulatedInputTokenCount, accumulatedOutputTokenCount)
        AppLogger.d(
            TAG,
            "Current turn token counts overridden. Input: $accumulatedInputTokenCount, Output: $accumulatedOutputTokenCount, CachedInput: $accumulatedCachedInputTokenCount"
        )
    }

    /** Reset token counters to zero Use this when starting a new conversation */
    fun resetTokenCounters() {
        Companion.resetTokenCounters(context)
    }

    /**
     * 重置指定功能类型或所有功能类型的token计数器
     * @param functionType 功能类型，如果为null则重置所有功能类型
     */
    suspend fun resetTokenCountersForFunction(functionType: FunctionType? = null) {
        Companion.resetTokenCountersForFunction(context, functionType)
    }

    /**
     * 生成对话总结
     * @param messages 要总结的消息列表
     * @return 生成的总结文本
     */
    suspend fun generateSummary(messages: List<Pair<String, String>>): String {
        return generateSummary(messages, null)
    }

    /**
     * 生成对话总结，并且包含上一次的总结内容
     * @param messages 要总结的消息列表
     * @param previousSummary 上一次的总结内容，可以为null
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            previousSummary: String?,
            customRules: String? = null
    ): String {
        return generateSummaryFromPromptTurns(messages.toPromptTurns(), previousSummary, customRules)
    }

    suspend fun generateSummaryFromPromptTurns(
            messages: List<PromptTurn>,
            previousSummary: String?,
            customRules: String? = null
    ): String {
        // 调用ConversationService中的方法
        return conversationService.generateSummaryFromPromptTurns(messages, previousSummary, multiServiceManager, customRules)
    }



    suspend fun generateConversationTitle(
        userText: String,
        attachmentFileNames: List<String> = emptyList()
    ): String {
        return conversationService.generateConversationTitle(
            userText = userText,
            attachmentFileNames = attachmentFileNames,
            multiServiceManager = multiServiceManager
        )
    }

    /**
     * 获取指定功能类型的当前输入token计数
     * @param functionType 功能类型
     * @return 输入token计数
     */
    suspend fun getCurrentInputTokenCountForFunction(functionType: FunctionType): Int {
        return Companion.getCurrentInputTokenCountForFunction(context, functionType)
    }

    /**
     * 获取指定功能类型的当前输出token计数
     * @param functionType 功能类型
     * @return 输出token计数
     */
    suspend fun getCurrentOutputTokenCountForFunction(functionType: FunctionType): Int {
        return Companion.getCurrentOutputTokenCountForFunction(context, functionType)
    }

    private fun resolveToolDisplayName(tool: AITool): String {
        if (tool.name != "package_proxy" && tool.name != "proxy") {
            return tool.name
        }
        val targetToolName = tool.parameters
            .firstOrNull { it.name == "tool_name" }
            ?.value
            ?.trim()
            .orEmpty()
        return if (targetToolName.isNotBlank()) targetToolName else tool.name
    }

    /** Prepare the conversation history with system prompt */
    private suspend fun prepareConversationHistory(
            chatHistory: List<PromptTurn>,
            processedInput: String,
            chatId: String?,
            workspacePath: String?,
            workspaceEnv: String?,
            promptFunctionType: PromptFunctionType,
            customSystemPromptTemplate: String? = null,
            roleCardId: String?,
            enableGroupOrchestrationHint: Boolean,
            groupParticipantNamesText: String? = null,
            proxySenderName: String? = null,
            isSubTask: Boolean = false,
            functionType: FunctionType = FunctionType.CHAT,
            modelConfig: ModelConfigData,
            memorySpaceIdOverride: String? = null,
            dispatchHistoryHooks: (PromptHookContext) -> PromptHookContext =
                PromptHookRegistry::dispatchPromptHistoryHooks,
            dispatchSystemPromptComposeHooks: (PromptHookContext) -> PromptHookContext =
                PromptHookRegistry::dispatchSystemPromptComposeHooks,
            dispatchToolPromptComposeHooks: (PromptHookContext) -> PromptHookContext =
                PromptHookRegistry::dispatchToolPromptComposeHooks
    ): List<PromptTurn> {
        // Check if backend image recognition service is configured (for intent-based vision)
        // For subtasks, always disable backend image recognition (only support OCR)
        val hasImageRecognition = if (isSubTask) false else multiServiceManager.hasImageRecognitionConfigured()
        val hasAudioRecognition = if (isSubTask) false else multiServiceManager.hasAudioRecognitionConfigured()
        val hasVideoRecognition = if (isSubTask) false else multiServiceManager.hasVideoRecognitionConfigured()

        // 获取当前功能类型（通常是聊天模型）的模型配置，用于判断聊天模型是否自带识图能力
        val config = modelConfig
        val useToolCallApi = config.enableToolCall
        val chatModelHasDirectImage = config.enableDirectImageProcessing
        val chatModelHasDirectAudio = config.enableDirectAudioProcessing
        val chatModelHasDirectVideo = config.enableDirectVideoProcessing
        val toolExposureMode = ToolExposureMode.resolve(config.apiProviderType)

        return conversationService.prepareConversationHistory(
                chatHistory,
                processedInput,
                chatId,
                workspacePath,
                workspaceEnv,
                packageManager,
                promptFunctionType,
                customSystemPromptTemplate,
                roleCardId,
                enableGroupOrchestrationHint,
                groupParticipantNamesText,
                proxySenderName,
                hasImageRecognition,
                hasAudioRecognition,
                hasVideoRecognition,
                chatModelHasDirectAudio,
                chatModelHasDirectVideo,
                useToolCallApi,
                chatModelHasDirectImage,
                toolExposureMode,
                memorySpaceIdOverride,
                dispatchHistoryHooks,
                dispatchSystemPromptComposeHooks,
                dispatchToolPromptComposeHooks
        )
    }

    private fun serializePromptHookModelParameters(
        modelParameters: List<com.ai.assistance.operit.data.model.ModelParameter<*>>
    ): List<Map<String, Any?>> {
        return modelParameters.map { parameter ->
            mapOf(
                "id" to parameter.id,
                "name" to parameter.name,
                "apiName" to parameter.apiName,
                "description" to parameter.description,
                "defaultValue" to parameter.defaultValue,
                "currentValue" to parameter.currentValue,
                "isEnabled" to parameter.isEnabled,
                "valueType" to parameter.valueType.name,
                "minValue" to parameter.minValue,
                "maxValue" to parameter.maxValue,
                "category" to parameter.category.name,
                "isCustom" to parameter.isCustom
            )
        }
    }

    private fun serializePromptHookToolPrompts(
        toolPrompts: List<ToolPrompt>?
    ): List<Map<String, Any?>> {
        return toolPrompts.orEmpty().map { tool ->
            mapOf(
                "categoryName" to "",
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.parameters,
                "details" to tool.details,
                "notes" to tool.notes,
                "parametersStructured" to
                    tool.parametersStructured.orEmpty().map { parameter ->
                        mapOf(
                            "name" to parameter.name,
                            "type" to parameter.type,
                            "description" to parameter.description,
                            "required" to parameter.required,
                            "default" to parameter.default
                        )
                    }
            )
        }
    }

    private fun deserializePromptHookToolPrompts(
        toolItems: List<Map<String, Any?>>
    ): List<ToolPrompt> {
        return toolItems.mapNotNull { item ->
            val name = item["name"] as? String ?: return@mapNotNull null
            val description = item["description"] as? String ?: return@mapNotNull null
            val parametersStructured = deserializePromptHookToolParameters(item["parametersStructured"])
            ToolPrompt(
                name = name,
                description = description,
                parameters = item["parameters"] as? String ?: "",
                parametersStructured = parametersStructured.ifEmpty { null },
                details = item["details"] as? String ?: "",
                notes = item["notes"] as? String ?: ""
            )
        }
    }

    private fun deserializePromptHookToolParameters(
        value: Any?
    ): List<ToolParameterSchema> {
        val items = value as? List<*> ?: return emptyList()
        return items.mapNotNull { item ->
            val parameter = item as? Map<*, *> ?: return@mapNotNull null
            val name = parameter["name"] as? String ?: return@mapNotNull null
            val description = parameter["description"] as? String ?: return@mapNotNull null
            ToolParameterSchema(
                name = name,
                type = parameter["type"] as? String ?: "string",
                description = description,
                required = parameter["required"] as? Boolean ?: true,
                default = (parameter["default"] as? String) ?: parameter["default"]?.toString()
            )
        }
    }

    private fun applyToolPromptComposeHooksToAvailableTools(
        availableTools: List<ToolPrompt>,
        chatId: String?,
        functionType: FunctionType,
        promptFunctionType: PromptFunctionType?,
        useEnglish: Boolean
    ): List<ToolPrompt> {
        val hookContext =
            PromptHookRegistry.dispatchToolPromptComposeHooks(
                PromptHookContext(
                    stage = "filter_tool_call_tools",
                    chatId = chatId,
                    functionType = functionType.name,
                    promptFunctionType = promptFunctionType?.name,
                    useEnglish = useEnglish,
                    availableTools = serializePromptHookToolPrompts(availableTools)
                )
            )
        return deserializePromptHookToolPrompts(hookContext.availableTools)
    }

    /** Cancel the current conversation */
    fun cancelConversation() {
        invalidateAllExecutionContexts("cancelConversation")

        // Set conversation inactive
        // isConversationActive.set(false) // This is now per-context, can't set a global one

        // Cancel all underlying AIService streaming instances
        initScope.launch {
            runCatching {
                multiServiceManager.cancelAllStreaming()
            }.onFailure { e ->
                AppLogger.e(TAG, "取消AIService流式输出失败", e)
            }
        }

        // Cancel all tool executions
        cancelAllToolExecutions()

        // Clean up current conversation content
        // roundManager.clearContent() // This is now per-context, can't clear a global one
        AppLogger.d(TAG, "Conversation canceled")

        // Reset input processing state
        _inputProcessingState.value = InputProcessingState.Idle

        // Reset per-request token counts
        _perRequestTokenCounts.value = null
        accumulatedInputTokenCount = 0
        accumulatedOutputTokenCount = 0
        accumulatedCachedInputTokenCount = 0
        currentRequestInputTokenCount = 0
        currentRequestOutputTokenCount = 0
        currentRequestCachedInputTokenCount = 0

        // Clear callback references
        currentResponseCallback = null
        currentCompleteCallback = null

        // 停止AI服务并关闭屏幕常亮
        stopAiService()

        AppLogger.d(TAG, "Conversation cancellation complete")
    }

    /** Cancel all tool executions */
    private fun cancelAllToolExecutions() {
        toolProcessingScope.coroutineContext.cancelChildren()
    }

    /**
     * 获取可用工具列表（用于Tool Call API）
     * 如果模型配置启用了Tool Call，返回工具列表；否则返回null
     */
    private suspend fun getAvailableToolsForFunction(
        functionType: FunctionType,
        chatId: String? = null,
        promptFunctionType: PromptFunctionType? = null,
        roleCardId: String? = null,
        modelConfig: ModelConfigData
    ): List<ToolPrompt>? {
        return try {
            AppLogger.d(
                TAG,
                "准备构建Tool Call工具列表: functionType=${functionType.name}, promptFunctionType=${promptFunctionType?.name}, chatId=${chatId ?: "null"}"
            )
            // 先读取全局工具开关
            val enableTools = apiPreferences.enableToolsFlow.first()
            val toolPromptVisibility = runCatching {
                apiPreferences.toolPromptVisibilityFlow.first()
            }.getOrElse { emptyMap() }
            val roleCardToolAccess = characterCardToolAccessResolver.resolve(
                roleCardId = roleCardId,
                packageManager = packageManager,
                globalToolVisibility = toolPromptVisibility
            )

            if (!enableTools) {
                AppLogger.d(TAG, "全局设置已禁用工具，本次调用不提供任何Tool Call工具")
                return null
            }

            // 获取对应功能类型的模型配置
            val config = modelConfig
            
            // 检查是否启用Tool Call
            if (!config.enableToolCall) {
                return null
            }

            val toolExposureMode = ToolExposureMode.resolve(config.apiProviderType)

            // 获取所有工具分类
            val isEnglish = LocaleUtils.getCurrentLanguage(context) == "en"

            // 后端识图服务是否可用（IMAGE_RECOGNITION 功能），用于 intent-based 视觉模型
            val hasBackendImageRecognition = multiServiceManager.hasImageRecognitionConfigured()

            val hasBackendAudioRecognition = multiServiceManager.hasAudioRecognitionConfigured()
            val hasBackendVideoRecognition = multiServiceManager.hasVideoRecognitionConfigured()

            val safBookmarkNames = runCatching {
                apiPreferences.safBookmarksFlow.first().map { it.name }
            }.getOrElse { emptyList() }

            // 当前功能模型（通常是聊天模型）是否支持直接看图
            val chatModelHasDirectImage = config.enableDirectImageProcessing

            val chatModelHasDirectAudio = config.enableDirectAudioProcessing
            val chatModelHasDirectVideo = config.enableDirectVideoProcessing

            val selectedTools = if (toolExposureMode == ToolExposureMode.CLI) {
                CliToolModeSupport.buildCliPublicToolPrompts(isEnglish).toMutableList()
            } else {
                val categories = if (isEnglish) {
                    SystemToolPrompts.getAIAllCategoriesEn(
                        hasBackendImageRecognition = hasBackendImageRecognition,
                        chatModelHasDirectImage = chatModelHasDirectImage,
                        hasBackendAudioRecognition = hasBackendAudioRecognition,
                        hasBackendVideoRecognition = hasBackendVideoRecognition,
                        chatModelHasDirectAudio = chatModelHasDirectAudio,
                        chatModelHasDirectVideo = chatModelHasDirectVideo,
                        safBookmarkNames = safBookmarkNames
                    )
                } else {
                    SystemToolPrompts.getAIAllCategoriesCn(
                        hasBackendImageRecognition = hasBackendImageRecognition,
                        chatModelHasDirectImage = chatModelHasDirectImage,
                        hasBackendAudioRecognition = hasBackendAudioRecognition,
                        hasBackendVideoRecognition = hasBackendVideoRecognition,
                        chatModelHasDirectAudio = chatModelHasDirectAudio,
                        chatModelHasDirectVideo = chatModelHasDirectVideo,
                        safBookmarkNames = safBookmarkNames
                    )
                }

                categories.flatMap { it.tools }.toMutableList().apply {
                    retainAll { tool ->
                        roleCardToolAccess.isBuiltinToolAllowed(tool.name)
                    }
                }
            }

            if (toolExposureMode == ToolExposureMode.CLI) {
                AppLogger.d(
                    TAG,
                    "CLI Tool Mode已启用，提供 ${selectedTools.size} 个工具 (provider=${config.apiProviderType})"
                )
            } else if (config.enableToolCall) {
                selectedTools.add(
                    ToolPrompt(
                        name = "package_proxy",
                        description = "Proxy tool for package tools activated by use_package.",
                        parametersStructured = listOf(
                            ToolParameterSchema(
                                name = "tool_name",
                                type = "string",
                                description = "Target tool name from an activated package (for example: packageName:toolName)",
                                required = true
                            ),
                            ToolParameterSchema(
                                name = "params",
                                type = "object",
                                description = "JSON object of parameters to forward to the target tool",
                                required = true
                            )
                        )
                    )
                )
            }

            val hookedTools = applyToolPromptComposeHooksToAvailableTools(
                availableTools = selectedTools,
                chatId = chatId,
                functionType = functionType,
                promptFunctionType = promptFunctionType,
                useEnglish = isEnglish
            )

            if (hookedTools.isEmpty()) {
                AppLogger.d(TAG, "根据当前工具开关，未选择任何Tool Call工具")
                return null
            }

            AppLogger.d(
                TAG,
                "Tool Call已启用，提供 ${hookedTools.size} 个工具 (base=${selectedTools.size}, enableTools=$enableTools, visibleToolOverrides=${toolPromptVisibility.size}, roleCardCustomTools=${roleCardToolAccess.customEnabled})"
            )
            hookedTools
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取工具列表失败", e)
            null
        }
    }

    // --- Service Lifecycle Management ---

    /** 启动或更新前台服务为“AI 正在运行”状态，以保持应用活跃 */
    private fun startAiService(characterName: String? = null, avatarUri: String? = null) {
        val refCount = FOREGROUND_REF_COUNT.incrementAndGet()
        val appInForeground = ActivityLifecycleManager.getCurrentActivity() != null
        val alwaysListeningEnabled = runCatching {
            runBlocking { WakeWordPreferences(context).alwaysListeningEnabledFlow.first() }
        }.getOrDefault(false)
        val externalHttpEnabled = runCatching {
            runBlocking { ExternalHttpApiPreferences.getInstance(context).enabledFlow.first() }
        }.getOrDefault(false)
        if (!appInForeground &&
            !AIForegroundService.isRunning.get() &&
            !alwaysListeningEnabled &&
            !externalHttpEnabled
        ) {
            AppLogger.d(TAG, "应用不在前台，跳过启动 AIForegroundService")
            return
        }
        try {
            val updateIntent = Intent(context, AIForegroundService::class.java).apply {
                putExtra(AIForegroundService.EXTRA_STATE, AIForegroundService.STATE_RUNNING)
                if (characterName != null) {
                    putExtra(AIForegroundService.EXTRA_CHARACTER_NAME, characterName)
                }
                if (avatarUri != null) {
                    putExtra(AIForegroundService.EXTRA_AVATAR_URI, avatarUri)
                }
            }
            context.startService(updateIntent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "更新AI前台服务为运行中状态失败: ${e.message}", e)
        }

        if (refCount == 1) {
            ActivityLifecycleManager.checkAndApplyKeepScreenOn(true)
        }
    }

    private fun notifyReplyCompleted(
        chatId: String?,
        characterName: String? = null,
        avatarUri: String? = null,
        notifyReplyOverride: Boolean? = null
    ) {
        AIForegroundService.notifyReplyCompleted(
            context = context,
            chatId = chatId,
            characterName = characterName,
            rawReplyContent = lastReplyContent,
            avatarUri = avatarUri,
            notifyReplyOverride = notifyReplyOverride
        )
    }

    /** 将前台服务更新为“空闲/已完成”状态，但不真正停止服务 */
    private fun stopAiService(characterName: String? = null, avatarUri: String? = null) {
        val remaining = run {
            var remainingValue = -1
            while (true) {
                val current = FOREGROUND_REF_COUNT.get()
                if (current <= 0) {
                    remainingValue = -1
                    break
                }
                val next = current - 1
                if (FOREGROUND_REF_COUNT.compareAndSet(current, next)) {
                    remainingValue = next
                    break
                }
            }
            remainingValue
        }
        if (remaining < 0) return
        if (remaining > 0) return
         if (AIForegroundService.isRunning.get()) {
             AppLogger.d(TAG, "更新AI前台服务为闲置状态...")

            try {
                val stopIntent = Intent(context, AIForegroundService::class.java).apply {
                    putExtra(AIForegroundService.EXTRA_CHARACTER_NAME, characterName)
                    putExtra(AIForegroundService.EXTRA_AVATAR_URI, avatarUri)
                    putExtra(AIForegroundService.EXTRA_STATE, AIForegroundService.STATE_IDLE)
                }

                AppLogger.d(TAG, "传递闲置状态 - 角色: $characterName, 头像: $avatarUri")

                // 仅发送更新，不再真正停止前台服务
                context.startService(stopIntent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新AI前台服务为闲置状态失败: ${e.message}", e)
            }
        } else {
            AppLogger.d(TAG, "AI前台服务未在运行，无需更新闲置状态。")
        }

        // 使用管理器来恢复屏幕常亮设置
        ActivityLifecycleManager.checkAndApplyKeepScreenOn(false)
    }

    /**
     * 处理文件绑定操作（实例方法）
     * @param originalContent 原始文件内容
     * @param aiGeneratedCode AI生成的代码（包含"//existing code"标记）
     * @return 混合后的文件内容
     */
    suspend fun applyFileBinding(
            originalContent: String,
            aiGeneratedCode: String
    ): Pair<String, String> {
        return fileBindingService.processFileBinding(
                originalContent,
                aiGeneratedCode
        )
    }

    /**
     * 翻译文本功能
     * @param text 要翻译的文本
     * @return 翻译后的文本
     */
    suspend fun translateText(text: String): String {
        return conversationService.translateText(text, multiServiceManager)
    }

    /**
     * 自动生成工具包描述
     * @param pluginName 工具包名称
     * @param toolDescriptions 工具描述列表
     * @return 生成的工具包描述
     */
    suspend fun generatePackageDescription(
        pluginName: String,
        toolDescriptions: List<String>
    ): String {
        return conversationService.generatePackageDescription(pluginName, toolDescriptions, multiServiceManager)
    }


    /**
     * Manually saves the current conversation to the problem library.
     * @param conversationHistory The history of the conversation to save.
     * @param lastContent The content of the last message in the conversation.
     */
    fun saveConversationToMemoryAsync(
        conversationHistory: List<Pair<String, String>>,
        lastContent: String,
        memorySpaceIdOverride: String? = null,
        onSuccess: (suspend () -> Unit)? = null,
        onError: (suspend (Exception) -> Unit)? = null
    ) {
        AppLogger.d(TAG, "手动触发记忆更新...")
        toolProcessingScope.launch {
            try {
                val memoryService = multiServiceManager.getServiceForFunction(FunctionType.MEMORY)
                com.ai.assistance.operit.api.chat.library.MemoryLibrary.saveMemoryAsync(
                    context = context,
                    toolHandler = toolHandler,
                    conversationHistory = conversationHistory,
                    content = lastContent,
                    aiService = memoryService,
                    profileIdOverride = memorySpaceIdOverride,
                    onSuccess = {
                        AppLogger.d(TAG, "手动记忆更新成功")
                        onSuccess?.invoke()
                    },
                    onError = { e ->
                        AppLogger.e(TAG, "手动记忆更新失败", e)
                        onError?.invoke(e)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "手动记忆更新初始化失败", e)
                onError?.invoke(e)
            }
        }
    }

    /**
     * 使用识图模型分析图片
     * @param imagePath 图片路径
     * @param userIntent 用户意图，例如"这个图片里面有什么"、"图片的题目公式是什么"等
     * @return AI分析结果
     */
    suspend fun analyzeImageWithIntent(imagePath: String, userIntent: String?): String {
        return conversationService.analyzeImageWithIntent(imagePath, userIntent, multiServiceManager)
    }

    suspend fun analyzeAudioWithIntent(audioPath: String, userIntent: String?): String {
        return conversationService.analyzeAudioWithIntent(audioPath, userIntent, multiServiceManager)
    }

    suspend fun analyzeVideoWithIntent(videoPath: String, userIntent: String?): String {
        return conversationService.analyzeVideoWithIntent(videoPath, userIntent, multiServiceManager)
    }
}
