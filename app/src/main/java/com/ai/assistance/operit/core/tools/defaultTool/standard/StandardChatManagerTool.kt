package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.core.tools.AgentStatusResultData
import com.ai.assistance.operit.core.tools.ChatCreationResultData
import com.ai.assistance.operit.core.tools.ChatFindResultData
import com.ai.assistance.operit.core.tools.ChatListResultData
import com.ai.assistance.operit.core.tools.ChatMessagesResultData
import com.ai.assistance.operit.core.tools.CharacterCardListResultData
import com.ai.assistance.operit.core.tools.ChatServiceStartResultData
import com.ai.assistance.operit.core.tools.ChatSwitchResultData
import com.ai.assistance.operit.core.tools.ChatTitleUpdateResultData
import com.ai.assistance.operit.core.tools.ChatDeleteResultData
import com.ai.assistance.operit.core.tools.MessageSendResultData
import com.ai.assistance.operit.core.tools.MessageSendStreamEventData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ChatTurnOptions
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.services.ChatServiceCore
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.floating.FloatingMode
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class MessageSendStreamSession(
    val chatId: String,
    val message: String,
    val responseStream: SharedStream<String>,
    val responseTimeoutMs: Long,
    private val currentStateProvider: () -> InputProcessingState,
    private val cancelAction: () -> Unit
) {
    fun currentState(): InputProcessingState = currentStateProvider()

    fun cancel() {
        cancelAction()
    }
}

sealed class MessageSendStreamStartResult {
    data class Started(val session: MessageSendStreamSession) : MessageSendStreamStartResult()

    data class Failed(val result: ToolResult) : MessageSendStreamStartResult()
}

/**
 * 对话管理工具
 * 负责管理对话、浮窗服务，以及按指定 runtime 发送消息
 */
class StandardChatManagerTool(private val context: Context) {

    companion object {
        private const val TAG = "StandardChatManagerTool"
        private const val SERVICE_CONNECTION_TIMEOUT = 15000L // 15秒超时
        private const val RESPONSE_STREAM_ACQUIRE_TIMEOUT = 15000L
        private const val AI_RESPONSE_TIMEOUT = 180000L
    }

    private fun simplifyXmlBlocksForHistory(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(ChatMarkupRegex.toolTag, "")
            .replace(ChatMarkupRegex.toolSelfClosingTag, "")
            .replace(ChatMarkupRegex.toolResultTag, "")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
            .trim()
    }

    private fun isMatchTitle(title: String, query: String, matchMode: String): Boolean {
        if (query.isBlank()) return true
        return when (matchMode) {
            "exact" -> title == query
            "regex" -> {
                try {
                    Regex(query).containsMatchIn(title)
                } catch (_: Exception) {
                    false
                }
            }
            else -> title.contains(query)
        }
    }

    private fun toSortableNumber(value: String): Long {
        return runCatching { value.toLong() }
            .getOrElse { 0L }
    }

    private fun toEpochMillis(dateTime: java.time.LocalDateTime): Long {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun parseBooleanOrNull(value: String?): Boolean? {
        return when (value?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun parseMessageRuntimeSlot(value: String?): ChatRuntimeSlot? {
        return when (value?.trim()?.lowercase()) {
            null, "" -> ChatRuntimeSlot.FLOATING
            "main" -> ChatRuntimeSlot.MAIN
            "floating" -> ChatRuntimeSlot.FLOATING
            else -> null
        }
    }

    /** 角色卡名到角色卡ID的映射，同名时取第一张，与 findCharacterCardByName 的选取一致 */
    private suspend fun buildCharacterCardIdsByName(chats: List<ChatHistory>): Map<String, String> {
        if (chats.none { !it.characterCardName.isNullOrBlank() }) {
            return emptyMap()
        }
        return CharacterCardManager.getInstance(appContext)
            .getAllCharacterCards()
            .groupBy { it.name }
            .mapValues { (_, cards) -> cards.first().id }
    }

    private fun buildChatInfo(
        chat: ChatHistory,
        messageCounts: Map<String, Int>,
        currentChatId: String?,
        characterCardIdsByName: Map<String, String>
    ): ChatListResultData.ChatInfo {
        return ChatListResultData.ChatInfo(
            id = chat.id,
            title = chat.title,
            messageCount = messageCounts[chat.id] ?: 0,
            createdAt = chat.createdAt.toString(),
            updatedAt = chat.updatedAt.toString(),
            isCurrent = currentChatId != null && chat.id == currentChatId,
            inputTokens = chat.inputTokens,
            outputTokens = chat.outputTokens,
            characterCardName = chat.characterCardName,
            characterCardId = chat.characterCardName
                ?.takeIf { it.isNotBlank() }
                ?.let { characterCardIdsByName[it] },
            characterGroupId = chat.characterGroupId
        )
    }

    private fun buildChatMessagesResult(
        chatId: String,
        order: String,
        limit: Int,
        messages: List<com.ai.assistance.operit.data.model.ChatMessage>,
        start: Int? = null,
        end: Int? = null,
    ): ChatMessagesResultData {
        val filteredMessages = messages.filterNot { msg -> msg.sender == "summary" }
        return ChatMessagesResultData(
            chatId = chatId,
            order = order,
            limit = limit,
            messages = filteredMessages.map { msg ->
                ChatMessagesResultData.ChatMessageInfo(
                    sender = msg.sender,
                    content = simplifyXmlBlocksForHistory(msg.content),
                    timestamp = msg.timestamp,
                    roleName = msg.roleName,
                    provider = msg.provider,
                    modelName = msg.modelName
                )
            },
            start = start,
            end = end,
        )
    }

    suspend fun getChatMessages(tool: AITool): ToolResult {
        return try {
            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            val rawOrder = tool.parameters.find { it.name == "order" }?.value?.trim()
            val order = rawOrder?.lowercase()?.takeIf { it == "asc" || it == "desc" }
            if (rawOrder != null && rawOrder.isNotBlank() && order == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: order must be asc/desc"
                )
            }

            val rawLimit = tool.parameters.find { it.name == "limit" }?.value?.trim()
            val parsedLimit = rawLimit?.takeIf { it.isNotBlank() }?.toIntOrNull()
            if (rawLimit != null && rawLimit.isNotBlank() && parsedLimit == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: limit must be an integer"
                )
            }

            val effectiveOrder = order ?: "desc"
            val effectiveLimit = (parsedLimit ?: 20).coerceIn(1, 200)

            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val title = chatHistoryManager.getChatTitle(chatId)
            if (title == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Chat does not exist: $chatId"
                )
            }

            val messages = chatHistoryManager.loadChatMessages(
                chatId = chatId,
                order = effectiveOrder,
                limit = effectiveLimit
            )

            ToolResult(
                toolName = tool.name,
                success = true,
                result = buildChatMessagesResult(chatId, effectiveOrder, effectiveLimit, messages)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get chat messages", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting chat messages: ${e.message}"
            )
        }
    }

    suspend fun getChatMessagesRange(tool: AITool): ToolResult {
        return try {
            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            val rawOrder = tool.parameters.find { it.name == "order" }?.value?.trim()
            val order = rawOrder?.lowercase()?.takeIf { it == "asc" || it == "desc" }
            if (rawOrder != null && rawOrder.isNotBlank() && order == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: order must be asc/desc"
                )
            }

            val rawStart = tool.parameters.find { it.name == "start" }?.value?.trim()
            val parsedStart = rawStart?.takeIf { it.isNotBlank() }?.toIntOrNull()
            if (rawStart != null && rawStart.isNotBlank() && parsedStart == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: start must be an integer"
                )
            }

            val rawEnd = tool.parameters.find { it.name == "end" }?.value?.trim()
            val parsedEnd = rawEnd?.takeIf { it.isNotBlank() }?.toIntOrNull()
            if (rawEnd != null && rawEnd.isNotBlank() && parsedEnd == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: end must be an integer"
                )
            }

            if (parsedStart == null || parsedEnd == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: start and end are required"
                )
            }

            if (parsedStart < 0 || parsedEnd < parsedStart) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: range requires 0 <= start <= end"
                )
            }

            val effectiveOrder = order ?: "asc"
            val effectiveLimit = parsedEnd - parsedStart + 1

            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val title = chatHistoryManager.getChatTitle(chatId)
            if (title == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Chat does not exist: $chatId"
                )
            }

            val messages = chatHistoryManager.loadChatMessagesRange(
                chatId = chatId,
                order = effectiveOrder,
                start = parsedStart,
                end = parsedEnd,
            )

            ToolResult(
                toolName = tool.name,
                success = true,
                result = buildChatMessagesResult(
                    chatId = chatId,
                    order = effectiveOrder,
                    limit = effectiveLimit,
                    messages = messages,
                    start = parsedStart,
                    end = parsedEnd,
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get chat messages range", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting chat messages range: ${e.message}"
            )
        }
    }

    /**
     * 查询对话输入状态
     */
    suspend fun agentStatus(tool: AITool): ToolResult {
        return try {
            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = AgentStatusResultData(chatId = "", state = "unknown"),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val title = chatHistoryManager.getChatTitle(chatId)
            if (title == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = AgentStatusResultData(chatId = chatId, state = "unknown"),
                    error = "Chat does not exist: $chatId"
                )
            }

            val connected = ensureServiceConnected()
            val chatService = chatCore
            if (!connected || chatService == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = AgentStatusResultData(chatId = chatId, state = "unknown"),
                    error = "Chat service not connected"
                )
            }

            val state = chatService.inputProcessingStateByChatId.value[chatId] ?: InputProcessingState.Idle
            var stateKey = "idle"
            var message: String? = null
            var isIdle = false
            var isProcessing = false
            when (state) {
                is InputProcessingState.Idle -> {
                    stateKey = "idle"
                    isIdle = true
                }
                is InputProcessingState.Completed -> {
                    stateKey = "completed"
                    isIdle = true
                }
                is InputProcessingState.Processing -> {
                    stateKey = "processing"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.Connecting -> {
                    stateKey = "connecting"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.Receiving -> {
                    stateKey = "receiving"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.ExecutingTool -> {
                    stateKey = "executing_tool"
                    message = state.toolName
                    isProcessing = true
                }
                is InputProcessingState.ToolProgress -> {
                    stateKey = "tool_progress"
                    message = if (state.message.isNotBlank()) {
                        "${state.toolName}: ${state.message}"
                    } else {
                        "${state.toolName}: ${(state.progress * 100).toInt()}%"
                    }
                    isProcessing = true
                }
                is InputProcessingState.ProcessingToolResult -> {
                    stateKey = "processing_tool_result"
                    message = state.toolName
                    isProcessing = true
                }
                is InputProcessingState.Summarizing -> {
                    stateKey = "summarizing"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.ExecutingPlan -> {
                    stateKey = "executing_plan"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.Error -> {
                    stateKey = "error"
                    message = state.message
                }
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = AgentStatusResultData(
                    chatId = chatId,
                    state = stateKey,
                    message = message,
                    isIdle = isIdle,
                    isProcessing = isProcessing
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get agent status", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = AgentStatusResultData(chatId = "", state = "unknown"),
                error = "Error getting agent status: ${e.message}"
            )
        }
    }

    /**
     * 查找对话
     */
    suspend fun findChat(tool: AITool): ToolResult {
        return try {
            val query = tool.parameters.find { it.name == "query" }?.value?.trim().orEmpty()
            if (query.isBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatFindResultData(matchedCount = 0, chat = null),
                    error = "Invalid parameter: missing query"
                )
            }

            val matchRaw = tool.parameters.find { it.name == "match" }?.value?.trim()?.lowercase()
            val matchMode = when (matchRaw) {
                null, "", "contains" -> "contains"
                "exact", "regex" -> matchRaw
                else ->
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatFindResultData(matchedCount = 0, chat = null),
                        error = "Invalid parameter: match must be contains/exact/regex"
                    )
            }

            val rawIndex = tool.parameters.find { it.name == "index" }?.value?.trim()
            val index = rawIndex?.takeIf { it.isNotBlank() }?.toIntOrNull()
            if (rawIndex != null && rawIndex.isNotBlank() && index == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatFindResultData(matchedCount = 0, chat = null),
                    error = "Invalid parameter: index must be an integer"
                )
            }

            val targetIndex = index ?: 0
            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val chatHistories = chatHistoryManager.chatHistoriesFlow.first()
            val currentChatId = chatHistoryManager.currentChatIdFlow.first()
            val messageCounts = chatHistoryManager.getMessageCountsByChatId()

            val idMatches = chatHistories.filter { chat -> chat.id == query }
            val matched = if (idMatches.isNotEmpty()) {
                idMatches
            } else {
                chatHistories.filter { chat -> isMatchTitle(chat.title, query, matchMode) }
            }
            if (matched.isEmpty()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatFindResultData(matchedCount = 0, chat = null),
                    error = "Chat not found by query: $query"
                )
            }

            if (targetIndex !in matched.indices) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatFindResultData(matchedCount = matched.size, chat = null),
                    error = "Chat index out of range: index=$targetIndex, matched=${matched.size}"
                )
            }

            val selectedChat = matched[targetIndex]
            val chatInfo = buildChatInfo(
                selectedChat,
                messageCounts,
                currentChatId,
                buildCharacterCardIdsByName(listOf(selectedChat))
            )
            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatFindResultData(matchedCount = matched.size, chat = chatInfo)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to find chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatFindResultData(matchedCount = 0, chat = null),
                error = "Error finding chat: ${e.message}"
            )
        }
    }

    /**
     * 更新对话标题
     */
    suspend fun updateChatTitle(tool: AITool): ToolResult {
        return try {
            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatTitleUpdateResultData(chatId = "", title = ""),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            val titleRaw = tool.parameters.find { it.name == "title" }?.value
            val title = titleRaw?.trim().orEmpty()
            if (title.isBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatTitleUpdateResultData(chatId = chatId, title = ""),
                    error = "Invalid parameter: missing title"
                )
            }

            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val existingTitle = chatHistoryManager.getChatTitle(chatId)
            if (existingTitle == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatTitleUpdateResultData(chatId = chatId, title = title),
                    error = "Chat does not exist: $chatId"
                )
            }

            chatHistoryManager.updateChatTitle(chatId, title)
            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatTitleUpdateResultData(chatId = chatId, title = title)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update chat title", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatTitleUpdateResultData(chatId = "", title = ""),
                error = "Error updating chat title: ${e.message}"
            )
        }
    }

    /**
     * 删除对话
     */
    suspend fun deleteChat(tool: AITool): ToolResult {
        return try {
            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatDeleteResultData(chatId = ""),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val chat = chatHistoryManager.chatHistoriesFlow.first().find { it.id == chatId }
            if (chat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatDeleteResultData(chatId = chatId),
                    error = "Chat does not exist: $chatId"
                )
            }

            if (chat.locked) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatDeleteResultData(chatId = chatId),
                    error = "Chat is locked and cannot be deleted: $chatId"
                )
            }

            val deleted = chatHistoryManager.deleteChatHistory(chatId)
            if (!deleted) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatDeleteResultData(chatId = chatId),
                    error = "Failed to delete chat: $chatId"
                )
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatDeleteResultData(chatId = chatId)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatDeleteResultData(chatId = ""),
                error = "Error deleting chat: ${e.message}"
            )
        }
    }

    private val appContext = context.applicationContext
    private val chatRuntimeHolder by lazy { ChatRuntimeHolder.getInstance(appContext) }

    // Service 连接状态
    private var chatCore: ChatServiceCore? = null
    private var floatingService: FloatingChatService? = null
    private var isBound = false
    private var connectionDeferred = CompletableDeferred<Boolean>().apply { complete(false) }

    // Service 连接回调
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            AppLogger.d(TAG, "Service connected")
            val binder = service as? FloatingChatService.LocalBinder
            if (binder != null) {
                floatingService = binder.getService()
                chatCore = binder.getChatCore()
                isBound = true
                binder.setCloseCallback {
                    AppLogger.d(TAG, "Received close callback from FloatingChatService")
                    unbindService()
                }
                if (!connectionDeferred.isCompleted) {
                    connectionDeferred.complete(true)
                }
                AppLogger.d(TAG, "ChatServiceCore obtained successfully")
            } else {
                AppLogger.e(TAG, "Failed to cast binder")
                if (!connectionDeferred.isCompleted) {
                    connectionDeferred.complete(false)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppLogger.d(TAG, "Service disconnected")
            chatCore = null
            floatingService = null
            isBound = false
            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(false)
            }
        }
    }

    /**
     * 确保服务已连接
     * @return 是否成功连接
     */
    private suspend fun ensureServiceConnected(startIntent: Intent? = null): Boolean {
        // 如果已经连接，直接返回
        if (isBound && chatCore != null) {
            if (startIntent != null) {
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(startIntent)
                    } else {
                        appContext.startService(startIntent)
                    }
                }
            }
            return true
        }

        if (startIntent == null && FloatingChatService.getInstance() == null) {
            AppLogger.w(TAG, "FloatingChatService not running; skip auto-start in ensureServiceConnected")
            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(false)
            }
            return false
        }

        val prefs = appContext.getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("service_disabled_due_to_crashes", false)) {
            AppLogger.w(TAG, "FloatingChatService is disabled due to frequent crashes")
            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(false)
            }
            return false
        }

        // 如果正在连接中，等待连接完成
        if (!connectionDeferred.isCompleted) {
            return try {
                withTimeout(SERVICE_CONNECTION_TIMEOUT) {
                    connectionDeferred.await()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Service connection timeout", e)
                if (!connectionDeferred.isCompleted) {
                    connectionDeferred.complete(false)
                }
                false
            }
        }

        // 重新启动和绑定服务
        return try {
            // 重置 deferred
            connectionDeferred = CompletableDeferred()
            
            val intent = startIntent ?: Intent(appContext, FloatingChatService::class.java)

            val bound =
                withContext(Dispatchers.Main) {
                    if (startIntent != null) {
                        // 启动服务
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            appContext.startForegroundService(intent)
                        } else {
                            appContext.startService(intent)
                        }
                    }

                    // 绑定服务
                    appContext.bindService(
                        intent,
                        serviceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                }
            
            if (!bound) {
                AppLogger.e(TAG, "Failed to bind service")
                connectionDeferred.complete(false)
                return false
            }

            // 等待连接完成
            withTimeout(SERVICE_CONNECTION_TIMEOUT) {
                connectionDeferred.await()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to ensure service connected", e)
            connectionDeferred.completeExceptionally(e)
            false
        }
    }

    /**
     * 解绑服务
     */
    fun unbindService() {
        if (isBound) {
            try {
                appContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error unbinding service", e)
            }
        }

        isBound = false
        chatCore = null
        floatingService = null
        connectionDeferred = CompletableDeferred<Boolean>().apply { complete(false) }
        AppLogger.d(TAG, "Service unbound")
    }

    /**
     * 启动对话服务
     */
    suspend fun startChatService(tool: AITool): ToolResult {
        return try {
            val initialModeParam = tool.parameters.find { it.name == "initial_mode" }?.value?.trim()
            val autoEnterVoiceChatParam =
                tool.parameters.find { it.name == "auto_enter_voice_chat" }?.value?.trim()
            val wakeLaunchedParam = tool.parameters.find { it.name == "wake_launched" }?.value?.trim()
            val timeoutMsParam = tool.parameters.find { it.name == "timeout_ms" }?.value?.trim()
            val keepIfExistsParam = tool.parameters.find { it.name == "keep_if_exists" }?.value?.trim()

            val initialMode =
                initialModeParam
                    ?.takeIf { it.isNotBlank() }
                    ?.let { raw ->
                        runCatching { FloatingMode.valueOf(raw.uppercase()) }.getOrNull()
                    }
            if (initialModeParam != null && initialModeParam.isNotBlank() && initialMode == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: initial_mode is invalid: $initialModeParam"
                )
            }

            fun parseBooleanOrNull(value: String?): Boolean? {
                return when (value?.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }

            val autoEnterVoiceChat = parseBooleanOrNull(autoEnterVoiceChatParam)
            if (autoEnterVoiceChatParam != null && autoEnterVoiceChat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: auto_enter_voice_chat must be true/false"
                )
            }

            val wakeLaunched = parseBooleanOrNull(wakeLaunchedParam)
            if (wakeLaunchedParam != null && wakeLaunched == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: wake_launched must be true/false"
                )
            }

            val timeoutMs = timeoutMsParam?.takeIf { it.isNotBlank() }?.toLongOrNull()
            if (timeoutMsParam != null && timeoutMsParam.isNotBlank() && timeoutMs == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: timeout_ms must be an integer (milliseconds)"
                )
            }

            val keepIfExists = parseBooleanOrNull(keepIfExistsParam)
            if (keepIfExistsParam != null && keepIfExists == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: keep_if_exists must be true/false"
                )
            }

            val intent = Intent(appContext, FloatingChatService::class.java)
            if (initialMode != null) {
                intent.putExtra("INITIAL_MODE", initialMode.name)
            }
            if (autoEnterVoiceChat == true) {
                intent.putExtra(FloatingChatService.EXTRA_AUTO_ENTER_VOICE_CHAT, true)
            }
            if (wakeLaunched != null) {
                intent.putExtra(FloatingChatService.EXTRA_WAKE_LAUNCHED, wakeLaunched)
            }
            if (timeoutMs != null) {
                intent.putExtra(FloatingChatService.EXTRA_AUTO_EXIT_AFTER_MS, timeoutMs)
            }
            if (keepIfExists == true) {
                intent.putExtra(FloatingChatService.EXTRA_KEEP_IF_EXISTS, true)
            }

            val connected = ensureServiceConnected(intent)
            
            if (connected) {
                try {
                    floatingService?.setFloatingWindowVisible(true)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to set floating window visible", e)
                }
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatServiceStartResultData(isConnected = true)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Chat service failed to start or connection timed out"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start chat service", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatServiceStartResultData(isConnected = false),
                error = "Error starting chat service: ${e.message}"
            )
        }
    }

    suspend fun stopChatService(tool: AITool): ToolResult {
        return try {
            try {
                floatingService?.setFloatingWindowVisible(false)
            } catch (_: Exception) {
            }

            unbindService()

            val intent = Intent(appContext, FloatingChatService::class.java)
            val stopped = runCatching { appContext.stopService(intent) }.getOrDefault(false)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(if (stopped) "Chat service stopped" else "Requested to stop chat service")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop chat service", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error stopping chat service: ${e.message}"
            )
        }
    }

    /**
     * 创建新的对话
     */
    suspend fun createNewChat(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "Service not connected"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatCreationResultData(chatId = ""),
                error = "ChatServiceCore not initialized"
            )

            // 获取创建前的 chat list
            val previousChatIds = core.chatHistories.value.map { it.id }.toSet()

            val group = tool.parameters.find { it.name == "group" }?.value?.trim()
            val effectiveGroup = group?.takeIf { it.isNotBlank() }

            val rawSetAsCurrent = tool.parameters.find { it.name == "set_as_current_chat" }?.value?.trim()
            val setAsCurrentChat =
                when (rawSetAsCurrent?.lowercase()) {
                    null, "" -> true
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            if (setAsCurrentChat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "Invalid parameter: set_as_current_chat must be true/false"
                )
            }

            val characterCardId =
                tool.parameters.find { it.name == "character_card_id" }?.value?.trim()
            if (!characterCardId.isNullOrBlank()) {
                val roleCardManager = CharacterCardManager.getInstance(appContext)
                val targetCard = roleCardManager.getCharacterCard(characterCardId)
                if (targetCard == null) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatCreationResultData(chatId = ""),
                        error = "Invalid parameter: character_card_id not found"
                    )
                }
            }
            
            // 创建新对话（不切换当前对话）
            core.createNewChat(
                group = effectiveGroup,
                setAsCurrentChat = setAsCurrentChat,
                characterCardId = characterCardId
            )

            val newChatId = try {
                withTimeout(5000L) {
                    var newId: String? = null
                    while (newId == null) {
                        val newChat = core.chatHistories.value.firstOrNull { it.id !in previousChatIds }
                        newId = newChat?.id
                        if (newId == null) {
                            delay(50)
                        }
                    }
                    newId
                }
            } catch (_: TimeoutCancellationException) {
                null
            }

            if (newChatId != null) {
                if (setAsCurrentChat) {
                    val switched = try {
                        withTimeout(5000L) {
                            while (core.currentChatId.value != newChatId) {
                                delay(50)
                            }
                            true
                        }
                    } catch (_: TimeoutCancellationException) {
                        false
                    }
                    if (!switched) {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = ChatCreationResultData(chatId = newChatId),
                            error = "Chat created but current chat switch did not complete in time"
                        )
                    }
                }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatCreationResultData(chatId = newChatId)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "Failed to create chat, unable to get new chat ID"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create new chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatCreationResultData(chatId = ""),
                error = "Error creating chat: ${e.message}"
            )
        }
    }

    /**
     * 列出所有对话
     */
    suspend fun listChats(tool: AITool): ToolResult {
        return try {
            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val chatHistories = chatHistoryManager.chatHistoriesFlow.first()
            val currentChatId = chatHistoryManager.currentChatIdFlow.first()
            val messageCounts = chatHistoryManager.getMessageCountsByChatId()

            val query = tool.parameters.find { it.name == "query" }?.value?.trim().orEmpty()
            val matchRaw = tool.parameters.find { it.name == "match" }?.value?.trim()?.lowercase()
            val matchMode = when (matchRaw) {
                null, "", "contains" -> "contains"
                "exact", "regex" -> matchRaw
                else ->
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatListResultData(totalCount = 0, currentChatId = currentChatId, chats = emptyList()),
                        error = "Invalid parameter: match must be contains/exact/regex"
                    )
            }

            val rawLimit = tool.parameters.find { it.name == "limit" }?.value?.trim()
            val parsedLimit = rawLimit?.takeIf { it.isNotBlank() }?.toIntOrNull()
            if (rawLimit != null && rawLimit.isNotBlank() && parsedLimit == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatListResultData(totalCount = 0, currentChatId = currentChatId, chats = emptyList()),
                    error = "Invalid parameter: limit must be an integer"
                )
            }
            val limit = (parsedLimit ?: 50).coerceIn(1, 200)

            val sortByRaw = tool.parameters.find { it.name == "sort_by" }?.value?.trim()
            val sortBy = when (sortByRaw) {
                null, "", "updatedAt" -> "updatedAt"
                "createdAt", "messageCount" -> sortByRaw
                else ->
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatListResultData(totalCount = 0, currentChatId = currentChatId, chats = emptyList()),
                        error = "Invalid parameter: sort_by must be updatedAt/createdAt/messageCount"
                    )
            }

            val sortOrderRaw = tool.parameters.find { it.name == "sort_order" }?.value?.trim()?.lowercase()
            val sortOrder = when (sortOrderRaw) {
                null, "", "desc" -> "desc"
                "asc" -> "asc"
                else ->
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatListResultData(totalCount = 0, currentChatId = currentChatId, chats = emptyList()),
                        error = "Invalid parameter: sort_order must be asc/desc"
                    )
            }

            val matched = chatHistories
                .filter { chat -> isMatchTitle(chat.title, query, matchMode) }
                .sortedWith { a, b ->
                    val av = when (sortBy) {
                        "messageCount" -> toSortableNumber((messageCounts[a.id] ?: 0).toString())
                        "createdAt" -> toEpochMillis(a.createdAt)
                        else -> toEpochMillis(a.updatedAt)
                    }
                    val bv = when (sortBy) {
                        "messageCount" -> toSortableNumber((messageCounts[b.id] ?: 0).toString())
                        "createdAt" -> toEpochMillis(b.createdAt)
                        else -> toEpochMillis(b.updatedAt)
                    }
                    if (sortOrder == "asc") av.compareTo(bv) else bv.compareTo(av)
                }

            val visibleChats = matched.take(limit)
            val characterCardIdsByName = buildCharacterCardIdsByName(visibleChats)
            val chatInfoList = visibleChats.map { chat ->
                buildChatInfo(chat, messageCounts, currentChatId, characterCardIdsByName)
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatListResultData(
                    totalCount = matched.size,
                    currentChatId = currentChatId,
                    chats = chatInfoList
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list chats", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatListResultData(
                    totalCount = 0,
                    currentChatId = null,
                    chats = emptyList()
                ),
                error = "Error listing chats: ${e.message}"
            )
        }
    }

    /**
     * 切换对话
     */
    suspend fun switchChat(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                    error = "Service not connected"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                error = "ChatServiceCore not initialized"
            )

            val chatId = tool.parameters.find { it.name == "chat_id" }?.value
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            // 检查对话是否存在并获取标题
            val targetChat = core.chatHistories.value.find { it.id == chatId }
            if (targetChat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = chatId, chatTitle = ""),
                    error = "Chat does not exist: $chatId"
                )
            }

            // 切换对话
            core.switchChatLocal(chatId)
            
            // 等待切换完成（最多等待1秒）
            var attempts = 0
            while (attempts < 10 && core.currentChatId.value != chatId) {
                delay(100)
                attempts++
            }
            
            if (core.currentChatId.value == chatId) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatSwitchResultData(
                        chatId = chatId,
                        chatTitle = targetChat.title
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = chatId, chatTitle = targetChat.title),
                    error = "Failed to switch chat, current chat ID not updated"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to switch chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                error = "Error switching chat: ${e.message}"
            )
        }
    }

    /**
     * 向AI发送消息
     */
    suspend fun startMessageToAIStream(tool: AITool): MessageSendStreamStartResult {
        return try {
            val runtimeParam = tool.parameters.find { it.name == "runtime" }?.value?.trim()
            val runtimeSlot = parseMessageRuntimeSlot(runtimeParam)
            if (runtimeParam != null && runtimeSlot == null) {
                return MessageSendStreamStartResult.Failed(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = "", message = ""),
                        error = "Invalid parameter: runtime must be main/floating"
                    )
                )
            }

            val core = chatRuntimeHolder.getCore(runtimeSlot ?: ChatRuntimeSlot.FLOATING)

            val message = tool.parameters.find { it.name == "message" }?.value
            if (message.isNullOrBlank()) {
                return MessageSendStreamStartResult.Failed(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = "", message = ""),
                        error = "Invalid parameter: missing message"
                    )
                )
            }

            val roleCardManager = CharacterCardManager.getInstance(appContext)
            val roleCardId = tool.parameters.find { it.name == "role_card_id" }?.value?.trim()
            if (!roleCardId.isNullOrBlank()) {
                val cardExists = runCatching { roleCardManager.getCharacterCard(roleCardId) }.isSuccess
                if (!cardExists) {
                    return MessageSendStreamStartResult.Failed(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = "", message = message),
                            error = "Invalid parameter: role_card_id not found"
                        )
                    )
                }
            }

            val senderNameParam = tool.parameters.find { it.name == "sender_name" }?.value?.trim()
            val proxySenderName = senderNameParam?.takeIf { it.isNotBlank() }
            val persistTurnParam = tool.parameters.find { it.name == "persist_turn" }?.value?.trim()
            val notifyReplyParam = tool.parameters.find { it.name == "notify_reply" }?.value?.trim()
            val hideUserMessageParam =
                tool.parameters.find { it.name == "hide_user_message" }?.value?.trim()
            val disableWarningParam =
                tool.parameters.find { it.name == "disable_warning" }?.value?.trim()
            val timeoutMsParam = tool.parameters.find { it.name == "timeout_ms" }?.value?.trim()

            val persistTurn = parseBooleanOrNull(persistTurnParam)
            if (persistTurnParam != null && persistTurn == null) {
                return MessageSendStreamStartResult.Failed(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = "", message = message),
                        error = "Invalid parameter: persist_turn must be true/false"
                    )
                )
            }

            val notifyReply = parseBooleanOrNull(notifyReplyParam)
            if (notifyReplyParam != null && notifyReply == null) {
                return MessageSendStreamStartResult.Failed(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = "", message = message),
                        error = "Invalid parameter: notify_reply must be true/false"
                    )
                )
            }

            val hideUserMessage = parseBooleanOrNull(hideUserMessageParam)
            if (hideUserMessageParam != null && hideUserMessage == null) {
                return MessageSendStreamStartResult.Failed(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = "", message = message),
                        error = "Invalid parameter: hide_user_message must be true/false"
                    )
                )
            }

            val disableWarning = parseBooleanOrNull(disableWarningParam)
            if (disableWarningParam != null && disableWarning == null) {
                return MessageSendStreamStartResult.Failed(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = "", message = message),
                        error = "Invalid parameter: disable_warning must be true/false"
                    )
                )
            }

            val timeoutMs = timeoutMsParam?.takeIf { it.isNotBlank() }?.toLongOrNull()
            if (timeoutMsParam != null && timeoutMsParam.isNotBlank() && (timeoutMs == null || timeoutMs <= 0L)) {
                return MessageSendStreamStartResult.Failed(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = "", message = message),
                        error = "Invalid parameter: timeout_ms must be a positive integer (milliseconds)"
                    )
                )
            }

            val timeoutDeadlineMs = timeoutMs?.let { System.currentTimeMillis() + it }

            fun remainingTimeoutMs(defaultTimeoutMs: Long): Long {
                val deadline = timeoutDeadlineMs ?: return defaultTimeoutMs
                return (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
            }

            val turnOptions =
                ChatTurnOptions(
                    persistTurn = persistTurn ?: true,
                    notifyReply = notifyReply,
                    hideUserMessage = hideUserMessage ?: false,
                    disableWarning = disableWarning ?: false
                )

            try {
                // 可选的 chat_id 参数
                val targetChatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
                val hasTargetChat = !targetChatId.isNullOrBlank()

                if (hasTargetChat) {
                    val chatExists = core.chatHistories.value.any { it.id == targetChatId }
                    if (!chatExists) {
                        return MessageSendStreamStartResult.Failed(
                            ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = MessageSendResultData(chatId = targetChatId!!, message = message),
                                error = "Specified chat does not exist: $targetChatId"
                            )
                        )
                    }
                }

                val preflightChatId = targetChatId ?: core.currentChatId.value
                val preflightResponseStream = preflightChatId?.let { chatId ->
                    core.getResponseStream(chatId)
                }

                try {
                    preflightChatId?.let { chatId ->
                        withTimeout(remainingTimeoutMs(AI_RESPONSE_TIMEOUT)) {
                            core.activeStreamingChatIds.first { activeChatIds ->
                                !activeChatIds.contains(chatId)
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    return MessageSendStreamStartResult.Failed(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = preflightChatId ?: "", message = message),
                            error = "Previous message is still being processed"
                        )
                    )
                }

                if (hasTargetChat) {
                    // 后台发送到指定对话，不切换 UI
                    core.sendUserMessage(
                        promptFunctionType = PromptFunctionType.CHAT,
                        roleCardIdOverride = roleCardId,
                        chatIdOverride = preflightChatId,
                        messageTextOverride = message,
                        proxySenderNameOverride = proxySenderName,
                        turnOptions = turnOptions
                    )
                } else {
                    // 发送消息（包含总结逻辑），由 Coordination 处理 chatId 默认
                    core.sendUserMessage(
                        promptFunctionType = PromptFunctionType.CHAT,
                        roleCardIdOverride = roleCardId,
                        messageTextOverride = message,
                        proxySenderNameOverride = proxySenderName,
                        turnOptions = turnOptions
                    )
                }

                val resolvedChatId = if (hasTargetChat) {
                    preflightChatId
                } else {
                    withTimeout(remainingTimeoutMs(RESPONSE_STREAM_ACQUIRE_TIMEOUT)) {
                        var id = core.currentChatId.value
                        while (id == null) {
                            delay(50)
                            id = core.currentChatId.value
                        }
                        id
                    }
                }

                if (resolvedChatId == null) {
                    return MessageSendStreamStartResult.Failed(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = preflightChatId ?: "", message = message),
                            error = "Unable to get current chat ID"
                        )
                    )
                }

                val responseStream: SharedStream<String> = try {
                    var stream: SharedStream<String>? = core.getResponseStream(resolvedChatId)
                    withTimeout(remainingTimeoutMs(RESPONSE_STREAM_ACQUIRE_TIMEOUT)) {
                        while (stream == null || stream === preflightResponseStream) {
                            val state = core.inputProcessingStateByChatId.value[resolvedChatId]
                                ?: InputProcessingState.Idle
                            if (state is InputProcessingState.Error) {
                                throw IllegalStateException(state.message)
                            }
                            delay(50)
                            stream = core.getResponseStream(resolvedChatId)
                        }
                    }
                    requireNotNull(stream)
                } catch (e: TimeoutCancellationException) {
                    runCatching { core.cancelMessage(resolvedChatId) }
                    return MessageSendStreamStartResult.Failed(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = resolvedChatId, message = message),
                            error = "Timeout waiting for AI response"
                        )
                    )
                }

                MessageSendStreamStartResult.Started(
                    MessageSendStreamSession(
                        chatId = resolvedChatId,
                        message = message,
                        responseStream = responseStream,
                        responseTimeoutMs = remainingTimeoutMs(AI_RESPONSE_TIMEOUT),
                        currentStateProvider = {
                            core.inputProcessingStateByChatId.value[resolvedChatId]
                                ?: InputProcessingState.Idle
                        },
                        cancelAction = {
                            runCatching { core.cancelMessage(resolvedChatId) }
                        }
                    )
                )
            } finally {}
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send message", e)
            MessageSendStreamStartResult.Failed(
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = ""),
                    error = "Error sending message: ${e.message}"
                )
            )
        }
    }

    suspend fun sendMessageToAI(tool: AITool): ToolResult {
        return try {
            when (val startResult = startMessageToAIStream(tool)) {
                is MessageSendStreamStartResult.Failed -> startResult.result
                is MessageSendStreamStartResult.Started -> {
                    val session = startResult.session
                    val aiResponse =
                        try {
                            withTimeout(session.responseTimeoutMs) {
                                val sb = StringBuilder()
                                session.responseStream.collect { chunk: String ->
                                    sb.append(chunk)
                                }
                                sb.toString()
                            }
                        } catch (e: TimeoutCancellationException) {
                            runCatching { session.cancel() }
                            return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = MessageSendResultData(chatId = session.chatId, message = session.message),
                                error = "Timeout waiting for AI reply"
                            )
                        }

                    val finalState = session.currentState()
                    if (finalState is InputProcessingState.Error) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(
                                chatId = session.chatId,
                                message = session.message,
                                aiResponse = aiResponse,
                                receivedAt = System.currentTimeMillis()
                            ),
                            error = finalState.message
                        )
                    } else {
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = MessageSendResultData(
                                chatId = session.chatId,
                                message = session.message,
                                aiResponse = aiResponse,
                                receivedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send message", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = MessageSendResultData(chatId = "", message = ""),
                error = "Error sending message: ${e.message}"
            )
        }
    }

    fun sendMessageToAIStream(tool: AITool): Flow<ToolResult> = channelFlow {
        val message = tool.parameters.find { it.name == "message" }?.value ?: ""
        val waifuParam = tool.parameters.find { it.name == "waifu" }?.value?.trim()
        val waifuMode = parseBooleanOrNull(waifuParam)
        if (waifuParam != null && waifuMode == null) {
            send(
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = message),
                    error = "Invalid parameter: waifu must be true/false"
                )
            )
            return@channelFlow
        }

        try {
            when (val startResult = startMessageToAIStream(tool)) {
                is MessageSendStreamStartResult.Failed -> send(startResult.result)
                is MessageSendStreamStartResult.Started -> {
                    val session = startResult.session
                    val effectiveWaifuMode = waifuMode == true
                    val waifuPreferences = WaifuPreferences.getInstance(context)
                    val waifuCharDelay = waifuPreferences.waifuCharDelayFlow.first()
                    val waifuRemovePunctuation =
                        if (effectiveWaifuMode) {
                            waifuPreferences.waifuRemovePunctuationFlow.first()
                        } else {
                            false
                        }

                    send(
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = MessageSendStreamEventData(
                                type = "start",
                                chatId = session.chatId,
                                message = session.message,
                                waifu = effectiveWaifuMode,
                                chunkIndex = 0,
                                receivedChars = 0
                            ),
                            error = ""
                        )
                    )

                    val fullResponse = StringBuilder()
                    var chunkIndex = 0
                    var receivedChars = 0

                    suspend fun sendChunk(chunk: String) {
                        if (chunk.isEmpty()) return
                        send(
                            ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = MessageSendStreamEventData(
                                    type = "chunk",
                                    chatId = session.chatId,
                                    message = session.message,
                                    waifu = effectiveWaifuMode,
                                    chunk = chunk,
                                    chunkIndex = chunkIndex,
                                    receivedChars = receivedChars
                                ),
                                error = ""
                            )
                        )
                        chunkIndex += 1
                    }

                    val aiResponse =
                        try {
                            coroutineScope {
                                val rawStreamJob = async {
                                    session.responseStream.collect { chunk: String ->
                                        if (chunk.isEmpty()) {
                                            return@collect
                                        }
                                        fullResponse.append(chunk)
                                        receivedChars += chunk.length
                                        if (!effectiveWaifuMode) {
                                            sendChunk(chunk)
                                        }
                                    }
                                    fullResponse.toString()
                                }

                                val waifuStreamJob =
                                    if (effectiveWaifuMode) {
                                        launch {
                                            WaifuMessageProcessor.streamSegmentsWithTypingQueue(
                                                sourceStream = session.responseStream,
                                                removePunctuation = waifuRemovePunctuation,
                                                charDelayMs = waifuCharDelay
                                            ).collect { segment ->
                                                sendChunk(segment)
                                            }
                                        }
                                    } else {
                                        null
                                    }

                                val result = withTimeout(session.responseTimeoutMs) {
                                    rawStreamJob.await()
                                }
                                waifuStreamJob?.join()
                                result
                            }
                        } catch (e: TimeoutCancellationException) {
                            runCatching { session.cancel() }
                            send(
                                ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = MessageSendResultData(
                                        chatId = session.chatId,
                                        message = session.message
                                    ),
                                    error = "Timeout waiting for AI reply"
                                )
                            )
                            return@channelFlow
                        }

                    val finalState = session.currentState()
                    val finalError =
                        if (finalState is InputProcessingState.Error) {
                            finalState.message
                        } else {
                            null
                        }

                    send(
                        ToolResult(
                            toolName = tool.name,
                            success = finalError == null,
                            result = MessageSendResultData(
                                chatId = session.chatId,
                                message = session.message,
                                aiResponse = aiResponse,
                                receivedAt = System.currentTimeMillis()
                            ),
                            error = finalError
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stream message", e)
            send(
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = message),
                    error = "Error sending message: ${e.message}"
                )
            )
        }
    }

    /**
     * 列出所有角色卡
     */
    suspend fun listCharacterCards(tool: AITool): ToolResult {
        return try {
            val characterCardManager = CharacterCardManager.getInstance(appContext)
            val cards = characterCardManager.getAllCharacterCards()
            val result = CharacterCardListResultData(
                totalCount = cards.size,
                cards = cards.map { card ->
                    CharacterCardListResultData.CharacterCardInfo(
                        id = card.id,
                        name = card.name,
                        description = card.description,
                        isDefault = card.isDefault,
                        createdAt = card.createdAt,
                        updatedAt = card.updatedAt
                    )
                }
            )
            ToolResult(
                toolName = tool.name,
                success = true,
                result = result
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list character cards", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = CharacterCardListResultData(totalCount = 0, cards = emptyList()),
                error = "Error listing character cards: ${e.message}"
            )
        }
    }
}
