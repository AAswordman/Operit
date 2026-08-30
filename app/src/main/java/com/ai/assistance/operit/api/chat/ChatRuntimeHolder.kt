package com.ai.assistance.operit.api.chat

import android.content.Context
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.services.ChatServiceCore
import com.ai.assistance.operit.services.core.ChatSelectionMode
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ChatRuntimeHolder private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cores = ConcurrentHashMap<ChatRuntimeSlot, ChatServiceCore>()
    private val _activeConversationCount = MutableStateFlow(0)
    val activeConversationCount: StateFlow<Int> = _activeConversationCount.asStateFlow()
    private val _currentSessionToolCount = MutableStateFlow(0)
    val currentSessionToolCount: StateFlow<Int> = _currentSessionToolCount.asStateFlow()

    init {
        ChatRuntimeSlot.values().forEach { slot ->
            getCore(slot)
        }
        setupCrossSessionSync()
        observeStats()
        observeCurrentRuntimeStates()
    }

    fun getCore(slot: ChatRuntimeSlot): ChatServiceCore {
        return cores.getOrPut(slot) {
            ChatServiceCore(
                context = appContext,
                coroutineScope = runtimeScope,
                runtimeSlot = slot,
                selectionMode = when (slot) {
                    ChatRuntimeSlot.MAIN -> ChatSelectionMode.FOLLOW_GLOBAL
                    ChatRuntimeSlot.FLOATING -> ChatSelectionMode.LOCAL_ONLY
                }
            )
        }
    }

    private fun observeCurrentRuntimeStates() {
        ChatRuntimeSlot.values().forEach { slot ->
            val core = getCore(slot)
            runtimeScope.launch {
                core.inputProcessingStateByChatId.collect { states ->
                    states.forEach { (chatId, state) ->
                        if (chatId != DEFAULT_CHAT_KEY) {
                            ChatRuntimeStateStore.updateInputProcessingState(
                                runtime = slot,
                                chatId = chatId,
                                state = state
                            )
                        }
                    }
                }
            }
            runtimeScope.launch {
                core.userDraftStateByChatId.collect { draftStates ->
                    draftStates.forEach { (chatId, hasDraft) ->
                        if (chatId != DEFAULT_CHAT_KEY) {
                            ChatRuntimeStateStore.updateUserDraft(
                                runtime = slot,
                                chatId = chatId,
                                hasDraft = hasDraft
                            )
                        }
                    }
                }
            }
        }

        runtimeScope.launch {
            ToolPermissionSystem.getInstance(appContext).permissionRequestState.collect { request ->
                ChatRuntimeStateStore.clearToolConfirmations()
                val chatId = request?.chatId?.trim()?.takeIf { it.isNotBlank() }
                if (chatId != null) {
                    ChatRuntimeStateStore.updateToolConfirmation(
                        chatId = chatId,
                        toolName = request.tool.name
                    )
                }
            }
        }

        runtimeScope.launch {
            ActivityLifecycleManager.applicationVisibilityState.collect { state ->
                ChatRuntimeStateStore.updateApplicationState(state)
            }
        }
    }

    private fun observeStats() {
        val mainCore = getCore(ChatRuntimeSlot.MAIN)
        val floatingCore = getCore(ChatRuntimeSlot.FLOATING)

        runtimeScope.launch {
            combine(
                mainCore.activeStreamingChatIds,
                floatingCore.activeStreamingChatIds
            ) { mainActiveChatIds, floatingActiveChatIds ->
                (mainActiveChatIds + floatingActiveChatIds).size
            }.collect { count ->
                _activeConversationCount.value = count
            }
        }

        runtimeScope.launch {
            combine(
                mainCore.activeStreamingChatIds,
                mainCore.currentTurnToolInvocationCountByChatId,
                floatingCore.activeStreamingChatIds,
                floatingCore.currentTurnToolInvocationCountByChatId
            ) { mainActiveChatIds, mainCounts, floatingActiveChatIds, floatingCounts ->
                countCurrentTurnToolsForActiveChats(mainActiveChatIds, mainCounts) +
                    countCurrentTurnToolsForActiveChats(floatingActiveChatIds, floatingCounts)
            }.collect { count ->
                _currentSessionToolCount.value = count
            }
        }
    }

    private fun countCurrentTurnToolsForActiveChats(
        activeChatIds: Set<String>,
        countMap: Map<String, Int>
    ): Int {
        return activeChatIds.sumOf { chatId -> countMap[chatId] ?: 0 }
    }

    private fun setupCrossSessionSync() {
        registerChatSelectionSync(
            sourceSlot = ChatRuntimeSlot.MAIN,
            targetSlot = ChatRuntimeSlot.FLOATING
        )
        registerTurnSync(
            sourceSlot = ChatRuntimeSlot.MAIN,
            targetSlot = ChatRuntimeSlot.FLOATING
        )
        registerTurnSync(
            sourceSlot = ChatRuntimeSlot.FLOATING,
            targetSlot = ChatRuntimeSlot.MAIN
        )
    }

    private fun registerTurnSync(
        sourceSlot: ChatRuntimeSlot,
        targetSlot: ChatRuntimeSlot
    ) {
        val sourceCore = getCore(sourceSlot)
        val targetCore = getCore(targetSlot)

        sourceCore.setAdditionalOnTurnComplete { chatId, inputTokens, outputTokens, windowSize ->
            if (chatId.isNullOrBlank()) {
                return@setAdditionalOnTurnComplete
            }
            if (targetCore.currentChatId.value != chatId) {
                return@setAdditionalOnTurnComplete
            }

            runtimeScope.launch {
                try {
                    targetCore.reloadChatMessagesSmart(chatId)
                    targetCore.getTokenStatisticsDelegate()
                        .setTokenCounts(chatId, inputTokens, outputTokens, windowSize)
                    AppLogger.d(
                        TAG,
                        "跨 Session smart 同步完成: $sourceSlot -> $targetSlot, chatId=$chatId, input=$inputTokens, output=$outputTokens, window=$windowSize"
                    )
                } catch (e: Exception) {
                    AppLogger.e(
                        TAG,
                        "跨 Session smart 同步失败: $sourceSlot -> $targetSlot, chatId=$chatId",
                        e
                    )
                }
            }
        }
    }

    fun syncMainChatSelectionToFloating(chatId: String) {
        if (chatId.isBlank()) return
        syncChatSelection(
            sourceSlot = ChatRuntimeSlot.MAIN,
            targetSlot = ChatRuntimeSlot.FLOATING,
            chatId = chatId
        )
    }

    private fun registerChatSelectionSync(
        sourceSlot: ChatRuntimeSlot,
        targetSlot: ChatRuntimeSlot
    ) {
        val sourceCore = getCore(sourceSlot)

        runtimeScope.launch {
            sourceCore.currentChatId
                .collect { chatId ->
                    if (chatId.isNullOrBlank()) {
                        return@collect
                    }
                    syncChatSelection(sourceSlot, targetSlot, chatId)
                }
        }
    }

    private fun syncChatSelection(
        sourceSlot: ChatRuntimeSlot,
        targetSlot: ChatRuntimeSlot,
        chatId: String
    ) {
        val targetCore = getCore(targetSlot)
        if (targetCore.currentChatId.value == chatId) {
            return
        }

        try {
            targetCore.switchChatLocal(chatId)
            AppLogger.d(
                TAG,
                "跨 Session 当前聊天同步: $sourceSlot -> $targetSlot, chatId=$chatId"
            )
        } catch (e: Exception) {
            AppLogger.e(
                TAG,
                "跨 Session 当前聊天同步失败: $sourceSlot -> $targetSlot, chatId=$chatId",
                e
            )
        }
    }

    companion object {
        private const val TAG = "ChatRuntimeHolder"
        private const val DEFAULT_CHAT_KEY = "__DEFAULT_CHAT__"

        @Volatile
        private var instance: ChatRuntimeHolder? = null

        fun getInstance(context: Context): ChatRuntimeHolder {
            return instance ?: synchronized(this) {
                instance ?: ChatRuntimeHolder(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
