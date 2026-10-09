package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.EnhancedAIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/** 委托类，负责管理token统计相关功能 */
class TokenStatisticsDelegate(
    private val coroutineScope: CoroutineScope,
    private val getEnhancedAiService: () -> EnhancedAIService?
) {
    companion object {
        private const val TAG = "TokenStatisticsDelegate"
    }

    // --- UI State Flows ---
    private val _cumulativeInputTokens = MutableStateFlow(0L)
    val cumulativeInputTokensFlow: StateFlow<Long> = _cumulativeInputTokens.asStateFlow()

    private val _cumulativeOutputTokens = MutableStateFlow(0L)
    val cumulativeOutputTokensFlow: StateFlow<Long> = _cumulativeOutputTokens.asStateFlow()

    private val _currentWindowSize = MutableStateFlow(0L)
    val currentWindowSizeFlow: StateFlow<Long> = _currentWindowSize.asStateFlow()

    private val _perRequestTokenCount = MutableStateFlow<Pair<Long, Long>?>(null)
    val perRequestTokenCountFlow: StateFlow<Pair<Long, Long>?> = _perRequestTokenCount.asStateFlow()

    // --- Internal State ---
    private var lastCurrentWindowSize = 0L
    private var tokenCollectorJob: Job? = null

    private val tokenCollectorJobsByChatKey = ConcurrentHashMap<String, Job>()
    private val boundServicesByChatKey = ConcurrentHashMap<String, EnhancedAIService>()

    // 输入和输出作为同一份快照更新，保留“尚未初始化”和“已初始化为零”的区别。
    private val cumulativeTokensByChatKey = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val lastWindowSizeByChatKey = ConcurrentHashMap<String, Long>()
    private val perRequestTokenCountByChatKey =
        ConcurrentHashMap<String, Pair<Long, Long>?>()

    @Volatile private var activeChatId: String? = null

    private fun chatKey(chatId: String?): String = chatId ?: "__DEFAULT_CHAT__"

    private fun isActiveKey(key: String): Boolean = key == chatKey(activeChatId)

    private fun refreshActiveFromCache() {
        val key = chatKey(activeChatId)
        val (input, output) = cumulativeTokensByChatKey[key] ?: Pair(0L, 0L)
        val window = lastWindowSizeByChatKey[key] ?: 0L
        val perRequest = perRequestTokenCountByChatKey[key]

        _cumulativeInputTokens.value = input
        _cumulativeOutputTokens.value = output
        _currentWindowSize.value = window
        _perRequestTokenCount.value = perRequest
        lastCurrentWindowSize = window
    }

    private fun handlePerRequestCounts(
        key: String,
        counts: Pair<Long, Long>?
    ) {
        if (counts == null) {
            perRequestTokenCountByChatKey.remove(key)
        } else {
            perRequestTokenCountByChatKey[key] = counts
        }

        if (isActiveKey(key)) {
            _perRequestTokenCount.value = counts
        }
    }

    private fun handleRequestWindowEstimate(
        key: String,
        windowSize: Long?
    ) {
        if (windowSize == null) {
            return
        }
        val safeWindowSize = windowSize.coerceAtLeast(0L)

        lastWindowSizeByChatKey[key] = safeWindowSize

        if (isActiveKey(key)) {
            _currentWindowSize.value = safeWindowSize
            lastCurrentWindowSize = safeWindowSize
        }
    }

    fun setupCollectors() {
        tokenCollectorJob?.cancel() // Cancel previous collector if any
        val service = getEnhancedAiService() ?: return // Service not ready
        tokenCollectorJob = coroutineScope.launch(Dispatchers.IO) {
            launch {
                service.perRequestTokenCounts.collect { counts ->
                    handlePerRequestCounts(
                        key = chatKey(null),
                        counts = counts
                    )
                }
            }
            launch {
                service.requestWindowEstimateFlow.collect { windowSize ->
                    handleRequestWindowEstimate(
                        key = chatKey(null),
                        windowSize = windowSize
                    )
                }
            }
        }
    }

    fun setActiveChatId(chatId: String?) {
        activeChatId = chatId
        refreshActiveFromCache()
    }

    fun bindChatService(chatId: String?, service: EnhancedAIService) {
        val key = chatKey(chatId)
        boundServicesByChatKey[key] = service

        tokenCollectorJobsByChatKey[key]?.cancel()
        tokenCollectorJobsByChatKey[key] =
            coroutineScope.launch(Dispatchers.IO) {
                launch {
                    service.perRequestTokenCounts.collect { counts ->
                        handlePerRequestCounts(
                            key = key,
                            counts = counts
                        )
                    }
                }
                launch {
                    service.requestWindowEstimateFlow.collect { windowSize ->
                        handleRequestWindowEstimate(
                            key = key,
                            windowSize = windowSize
                        )
                    }
                }
            }

        if (isActiveKey(key)) {
            refreshActiveFromCache()
        }
    }

    /** 重置token统计 */
    fun resetTokenStatistics() {
        _cumulativeInputTokens.value = 0L
        _cumulativeOutputTokens.value = 0L
        _currentWindowSize.value = 0L
        _perRequestTokenCount.value = null
        lastCurrentWindowSize = 0L

        cumulativeTokensByChatKey.clear()
        lastWindowSizeByChatKey.clear()
        perRequestTokenCountByChatKey.clear()

        // 同时重置服务中的token计数
        val services = buildSet {
            getEnhancedAiService()?.let { add(it) }
            addAll(boundServicesByChatKey.values)
        }
        services.forEach { it.resetTokenCounters() }
        AppLogger.d(TAG, "token统计已重置")
    }

    /** 更新累计的token统计信息 */
    fun updateCumulativeStatistics(chatId: String? = activeChatId, serviceOverride: EnhancedAIService? = null) {
        val key = chatKey(chatId)
        if (chatId != null && !hasTokenStatistics(chatId)) {
            AppLogger.w(TAG, "会话统计尚未恢复，跳过累计: chatId=$chatId")
            return
        }
        val service = serviceOverride ?: boundServicesByChatKey[key] ?: getEnhancedAiService()
        service?.let {
            try {
                // 从AI服务获取最新的token统计
                val currentInputTokens = it.getCurrentInputTokenCount().coerceAtLeast(0L)
                val currentOutputTokens = it.getCurrentOutputTokenCount().coerceAtLeast(0L)

                // 更新累计token数
                val (newInput, newOutput) = cumulativeTokensByChatKey.compute(key) { _, previous ->
                    Pair(
                        (previous?.first ?: 0L) + currentInputTokens,
                        (previous?.second ?: 0L) + currentOutputTokens,
                    )
                }!!

                if (isActiveKey(key)) {
                    refreshActiveFromCache()
                }

                AppLogger.d(
                        TAG,
                    "Cumulative token stats updated - " +
                            "Input: $newInput, Output: $newOutput"
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取累计token计数时出错: ${e.message}", e)
            }
        }
    }

    /** 已加载的零值与尚未加载的会话分别处理。 */
    fun hasTokenStatistics(chatId: String?): Boolean =
        cumulativeTokensByChatKey.containsKey(chatKey(chatId))

    /** 显式清空消息后只清零对应会话，保留其他会话的累计。 */
    fun clearChatTokenStatistics(chatId: String?) {
        val key = chatKey(chatId)
        cumulativeTokensByChatKey[key] = Pair(0L, 0L)
        lastWindowSizeByChatKey[key] = 0L
        perRequestTokenCountByChatKey.remove(key)
        if (isActiveKey(key)) {
            refreshActiveFromCache()
        }
    }

    /** 设置上下文窗口，不初始化或修改累计用量。 */
    fun setCurrentWindowSize(chatId: String?, windowSize: Long) {
        handleRequestWindowEstimate(chatKey(chatId), windowSize)
    }

    /** 设置累计token计数；迟到快照不覆盖较新的累计值，清零由显式重置处理。 */
    fun setTokenCounts(chatId: String?, inputTokens: Long, outputTokens: Long, windowSize: Long) {
        val key = chatKey(chatId)
        val safeInputTokens = inputTokens.coerceAtLeast(0L)
        val safeOutputTokens = outputTokens.coerceAtLeast(0L)
        val safeWindowSize = windowSize.coerceAtLeast(0L)
        cumulativeTokensByChatKey.compute(key) { _, previous ->
            Pair(
                maxOf(previous?.first ?: 0L, safeInputTokens),
                maxOf(previous?.second ?: 0L, safeOutputTokens),
            )
        }
        lastWindowSizeByChatKey[key] = safeWindowSize

        if (isActiveKey(key)) {
            refreshActiveFromCache()
        }
    }

    fun setTokenCounts(inputTokens: Long, outputTokens: Long, windowSize: Long) {
        setTokenCounts(activeChatId, inputTokens, outputTokens, windowSize)
    }

    /** 获取当前累计token计数 */
    fun getCumulativeTokenCounts(chatId: String? = activeChatId): Pair<Long, Long> {
        val key = chatKey(chatId)
        return cumulativeTokensByChatKey[key] ?: Pair(0L, 0L)
    }

    /** 获取最近一次的实际上下文窗口大小 */
    fun getLastCurrentWindowSize(chatId: String? = activeChatId): Long {
        val key = chatKey(chatId)
        return lastWindowSizeByChatKey[key] ?: 0L
    }
}
