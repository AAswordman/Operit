package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
        if (runningJob?.isActive == true) return
        val distinctChatIds = chatIds.filter(String::isNotBlank).distinct()
        if (distinctChatIds.isEmpty()) return

        runningJob = scope.launch {
            operationMutex.withLock {
                rebuild(distinctChatIds, windowMessageCount, timeScope)
            }
        }
    }

    fun cancel() {
        runningJob?.cancel()
    }

    private suspend fun rebuild(
        chatIds: List<String>,
        windowMessageCount: Int,
        timeScope: ChatMemoryRebuildTimeScope
    ) {
        var serviceLease: MultiServiceManager.ServiceLease? = null
        try {
            _progress.value = Progress(status = Status.PREPARING, totalChats = chatIds.size)
            val chatHistoryManager = ChatHistoryManager.getInstance(context)
            val selectedHistories =
                chatHistoryManager.chatHistoriesFlow.first().filter { it.id in chatIds }
            require(selectedHistories.isNotEmpty()) { "No selected chat history found" }
            val activeProfileId = preferencesManager.activeMemorySpaceIdFlow.first()

            val plannedChats = selectedHistories.map { history ->
                val messages = chatHistoryManager.loadChatMessages(history.id)
                PlannedChat(
                    history = history,
                    windows = ChatMemoryWindowPlanner.plan(messages, windowMessageCount, timeScope),
                    memoryProfileId = resolveMemoryProfileId(history, activeProfileId)
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
            val lease = EnhancedAIService.acquireAIServiceForFunction(context, FunctionType.MEMORY)
            serviceLease = lease
            val memoryService = lease.service
            var completedChats = 0
            var completedWindows = 0
            var processedSourceMessages = 0
            var failedWindows = 0
            _progress.value = Progress(
                status = Status.RUNNING,
                totalChats = plannedChats.size,
                totalWindows = totalWindows,
                totalSourceMessages = totalSourceMessages
            )

            plannedChats.forEach { plannedChat ->
                coroutineContext.ensureActive()
                plannedChat.windows.forEach { window ->
                    coroutineContext.ensureActive()
                    _progress.value = Progress(
                        status = Status.RUNNING,
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
                        MemoryLibrary.saveMemoryWindowNow(
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
                status = Status.COMPLETED,
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
        } finally {
            serviceLease?.close()
        }
    }

    private suspend fun resolveMemoryProfileId(
        history: ChatHistory,
        activeProfileId: String
    ): String {
        if (!history.characterGroupId.isNullOrBlank()) {
            return activeProfileId
        }
        val cardName = history.characterCardName ?: return activeProfileId
        val card = CharacterCardManager.getInstance(context).getAllCharacterCards()
            .firstOrNull { it.name == cardName }
        return if (
            card != null &&
                CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode) ==
                    CharacterCardMemoryProfileBindingMode.FIXED_PROFILE &&
                !card.memoryProfileId.isNullOrBlank()
        ) {
            requireNotNull(card.memoryProfileId)
        } else {
            activeProfileId
        }
    }

    private data class PlannedChat(
        val history: ChatHistory,
        val windows: List<ChatMemoryWindowPlanner.Window>,
        val memoryProfileId: String
    )
}
