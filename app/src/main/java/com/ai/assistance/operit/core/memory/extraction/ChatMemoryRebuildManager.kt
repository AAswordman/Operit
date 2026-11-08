package com.ai.assistance.operit.core.memory.extraction

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.memory.resolveChatMemorySpaceId
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

class ChatMemoryRebuildManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ChatMemoryRebuild"

        @Volatile
        private var instance: ChatMemoryRebuildManager? = null

        fun getInstance(context: Context): ChatMemoryRebuildManager =
            instance ?: synchronized(this) {
                instance ?: ChatMemoryRebuildManager(context.applicationContext).also { instance = it }
            }
    }

    data class Progress(
        val status: Status = Status.IDLE,
        val memorySpaceId: String? = null,
        val totalChats: Int = 0,
        val completedChats: Int = 0,
        val currentChatTitle: String = "",
        val totalWindows: Int = 0,
        val completedWindows: Int = 0,
        val totalSourceMessages: Int = 0,
        val processedSourceMessages: Int = 0,
        val failedWindows: Int = 0,
        val errorMessage: String = ""
    ) {
        val fraction: Float
            get() =
                if (totalSourceMessages <= 0) 0f
                else processedSourceMessages.toFloat() / totalSourceMessages.toFloat()
    }

    enum class Status {
        IDLE,
        PREPARING,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    @Volatile
    private var runningJob: Job? = null

    fun start(
        chatIds: List<String>,
        windowMessageCount: Int,
        timeScope: ChatMemoryRebuildTimeScope
    ) {
        startInternal(chatIds, windowMessageCount, timeScope, null)
    }

    /** 在图谱内重建时固定写入浏览中的空间，避免异步任务跟随全局空间变化。 */
    fun startForMemorySpace(
        memorySpaceId: String,
        chatIds: List<String>,
        windowMessageCount: Int,
        timeScope: ChatMemoryRebuildTimeScope,
    ) {
        require(memorySpaceId.isNotBlank())
        startInternal(chatIds, windowMessageCount, timeScope, memorySpaceId)
    }

    @Synchronized
    private fun startInternal(
        chatIds: List<String>,
        windowMessageCount: Int,
        timeScope: ChatMemoryRebuildTimeScope,
        memorySpaceId: String?,
    ) {
        if (runningJob?.isCompleted == false) return
        val distinctChatIds = chatIds.filter(String::isNotBlank).distinct()
        if (distinctChatIds.isEmpty()) return

        // 先发布任务与进度，再启动协程，避免重复点击产生多个重建请求。
        val job = scope.launch(start = CoroutineStart.LAZY) {
            operationMutex.withLock {
                rebuild(distinctChatIds, windowMessageCount, timeScope, memorySpaceId)
            }
        }
        runningJob = job
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                synchronized(this) {
                    if (runningJob === job && _progress.value.status in setOf(Status.PREPARING, Status.RUNNING)) {
                        _progress.value = _progress.value.copy(status = Status.CANCELLED)
                    }
                }
            }
        }
        _progress.value = Progress(status = Status.PREPARING, memorySpaceId = memorySpaceId, totalChats = distinctChatIds.size)
        job.start()
    }

    @Synchronized
    fun cancel() {
        runningJob?.cancel()
    }

    private suspend fun rebuild(
        chatIds: List<String>,
        windowMessageCount: Int,
        timeScope: ChatMemoryRebuildTimeScope,
        memorySpaceId: String?,
    ) {
        try {
            _progress.value = Progress(status = Status.PREPARING, memorySpaceId = memorySpaceId, totalChats = chatIds.size)
            val chatHistoryManager = ChatHistoryManager.getInstance(context)
            val selectedHistories =
                chatHistoryManager.chatHistoriesFlow.first().filter { it.id in chatIds }
            require(selectedHistories.isNotEmpty()) { "No selected chat history found" }
            val activeProfileId = preferencesManager.activeMemorySpaceIdFlow.first()
            val characterCards = CharacterCardManager.getInstance(context).getAllCharacterCards()

            val plannedChats = selectedHistories.map { history ->
                val messages = chatHistoryManager.loadChatMessages(history.id)
                PlannedChat(
                    history = history,
                    windows = ChatMemoryWindowPlanner.plan(messages, windowMessageCount, timeScope),
                    memoryProfileId = if (memorySpaceId != null) memorySpaceId
                        else resolveChatMemorySpaceId(history, characterCards, activeProfileId)
                )
            }
            val totalWindows = plannedChats.sumOf { it.windows.size }
            val totalSourceMessages = plannedChats.sumOf { plannedChat ->
                plannedChat.windows.sumOf { window -> window.sourceMessageCount }
            }
            require(totalWindows > 0) {
                when (timeScope) {
                    ChatMemoryRebuildTimeScope.EntireChat ->
                        "Selected chats do not contain any user messages to rebuild"
                    is ChatMemoryRebuildTimeScope.InclusiveLocalRange ->
                        context.getString(R.string.chat_memory_rebuild_empty_range)
                }
            }

            val toolHandler = AIToolHandler.getInstance(context)
            val memoryService = EnhancedAIService.getAIServiceForFunction(context, FunctionType.MEMORY)
            var completedChats = 0
            var completedWindows = 0
            var processedSourceMessages = 0
            var failedWindows = 0
            _progress.value = Progress(
                status = Status.RUNNING, memorySpaceId = memorySpaceId,
                totalChats = plannedChats.size,
                totalWindows = totalWindows,
                totalSourceMessages = totalSourceMessages
            )

            plannedChats.forEach { plannedChat ->
                coroutineContext.ensureActive()
                plannedChat.windows.forEach { window ->
                    coroutineContext.ensureActive()
                    _progress.value = Progress(
                        status = Status.RUNNING, memorySpaceId = memorySpaceId,
                        totalChats = plannedChats.size,
                        completedChats = completedChats,
                        currentChatTitle = plannedChat.history.title,
                        totalWindows = totalWindows,
                        completedWindows = completedWindows,
                        totalSourceMessages = totalSourceMessages,
                        processedSourceMessages = processedSourceMessages,
                        failedWindows = failedWindows
                    )
                    try {
                        val history = window.messages.map { message ->
                            when (message.sender) {
                                "user" -> "user" to message.content
                                "ai", "assistant" -> "assistant" to message.content
                                else -> error("Unsupported memory rebuild message sender: ${message.sender}")
                            }
                        }
                        val content =
                            window.messages.lastOrNull {
                                it.sender == "ai" || it.sender == "assistant"
                            }?.content
                                ?: window.messages.lastOrNull { it.sender == "user" }?.content.orEmpty()
                        ConversationMemoryExtractor.saveMemoryWindowNow(
                            context = context,
                            toolHandler = toolHandler,
                            conversationHistory = history,
                            content = content,
                            aiService = memoryService,
                            profileIdOverride = plannedChat.memoryProfileId,
                            analysisHistoryLimit = window.messages.size
                        )
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        failedWindows++
                        AppLogger.e(
                            TAG,
                            "窗口记忆重建失败: chatId=${plannedChat.history.id}, title=${plannedChat.history.title}",
                            error
                        )
                    }
                    completedWindows++
                    processedSourceMessages += window.sourceMessageCount
                }
                completedChats++
            }
            _progress.value = Progress(
                status = Status.COMPLETED, memorySpaceId = memorySpaceId,
                totalChats = plannedChats.size,
                completedChats = completedChats,
                totalWindows = totalWindows,
                completedWindows = completedWindows,
                totalSourceMessages = totalSourceMessages,
                processedSourceMessages = totalSourceMessages,
                failedWindows = failedWindows
            )
        } catch (error: CancellationException) {
            _progress.value = _progress.value.copy(status = Status.CANCELLED)
            AppLogger.i(TAG, "聊天记忆重建已取消")
        } catch (error: Exception) {
            AppLogger.e(TAG, "聊天记忆重建失败", error)
            _progress.value = _progress.value.copy(
                status = Status.FAILED,
                errorMessage = error.localizedMessage ?: error.javaClass.simpleName
            )
        }
    }

    private data class PlannedChat(
        val history: ChatHistory,
        val windows: List<ChatMemoryWindowPlanner.Window>,
        val memoryProfileId: String
    )
}
