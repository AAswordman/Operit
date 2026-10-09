package com.ai.assistance.operit.services.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 串行恢复会话统计，首轮累加和打开历史会话共用同一条读取路径。 */
internal class ChatTokenStatisticsLoader(
    private val isLoaded: (String) -> Boolean,
    private val readCounts: suspend (String) -> Triple<Long, Long, Long>?,
    private val applyCounts: (String, Long, Long, Long) -> Unit,
) {
    private val loadMutex = Mutex()

    suspend fun ensureLoaded(chatId: String, forceReload: Boolean = false): Boolean =
        loadMutex.withLock {
            if (!forceReload && isLoaded(chatId)) {
                return@withLock true
            }
            val counts = readCounts(chatId) ?: return@withLock false
            applyCounts(chatId, counts.first, counts.second, counts.third)
            true
        }
}
