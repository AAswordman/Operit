package com.ai.assistance.operit.core.memory.extraction

import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate

/** 读取真实候选队列的状态，不把失败记录计入待处理记录。 */
data class MemoryAutoSaveQueueStatus(
    val pendingCandidates: Int = 0,
    val pendingChats: Int = 0,
    val processingCandidates: Int = 0,
    val failedCandidates: Int = 0,
    val lastError: String = "",
)

internal fun summarizeMemoryAutoSaveQueue(candidates: List<MemoryAutoSaveCandidate>): MemoryAutoSaveQueueStatus {
    val pending = candidates.filter { it.status == MemoryAutoSaveCandidate.STATUS_PENDING }
    val failed = candidates.filter { it.status == MemoryAutoSaveCandidate.STATUS_FAILED }
    return MemoryAutoSaveQueueStatus(
        pendingCandidates = pending.size,
        pendingChats = pending.map { it.chatId }.filter { it.isNotBlank() }.distinct().size,
        processingCandidates = candidates.count { it.status == MemoryAutoSaveCandidate.STATUS_PROCESSING },
        failedCandidates = failed.size,
        lastError = failed.maxByOrNull { it.updatedAt.time }?.lastError.orEmpty(),
    )
}
