package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate
import com.ai.assistance.operit.core.memory.extraction.MemoryAutoSaveQueueStatus
import com.ai.assistance.operit.core.memory.extraction.summarizeMemoryAutoSaveQueue
import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import java.util.Date

class MemoryAutoSaveCandidateRepository(
    context: Context,
    profileId: String
) {
    private val store = ObjectBoxManager.get(context, profileId)
    private val candidateBox: Box<MemoryAutoSaveCandidate> = store.boxFor()

    fun enqueue(
        chatId: String,
        triggerMessageTimestamp: Long,
        sourceType: String = MemoryAutoSaveCandidate.SOURCE_TYPE_REPLY_FINALIZED_AUTO
    ): Long {
        val now = Date()
        val candidate =
            MemoryAutoSaveCandidate(
                chatId = chatId,
                triggerMessageTimestamp = triggerMessageTimestamp,
                createdAt = now,
                updatedAt = now,
                status = MemoryAutoSaveCandidate.STATUS_PENDING,
                sourceType = sourceType
            )
        return candidateBox.put(candidate)
    }

    fun enqueueSelectedUserMessages(
        chatId: String,
        triggerMessageTimestamps: List<Long>
    ) {
        val normalizedTimestamps =
            triggerMessageTimestamps
                .filter { it > 0L }
                .distinct()
                .sorted()
        if (chatId.isBlank() || normalizedTimestamps.isEmpty()) return
        normalizedTimestamps.forEach { timestamp ->
            enqueue(
                chatId = chatId,
                triggerMessageTimestamp = timestamp,
                sourceType = MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE
            )
        }
    }

    fun getPendingAndFailedCandidates(): List<MemoryAutoSaveCandidate> {
        return candidateBox
            .query(
                MemoryAutoSaveCandidate_.status
                    .equal(MemoryAutoSaveCandidate.STATUS_PENDING)
                    .or(
                        MemoryAutoSaveCandidate_.status.equal(
                            MemoryAutoSaveCandidate.STATUS_FAILED
                        )
                    )
            )
            .build()
            .find()
            .sortedBy { it.createdAt.time }
    }

    fun countPendingAndFailedChats(): Int {
        return getPendingAndFailedCandidates()
            .map { it.chatId }
            .filter { it.isNotBlank() }
            .distinct()
            .size
    }

    fun readQueueStatus(): MemoryAutoSaveQueueStatus = summarizeMemoryAutoSaveQueue(candidateBox.all)

    fun countPendingAndFailedCandidates(): Int {
        return getPendingAndFailedCandidates().size
    }

    fun markProcessing(candidateIds: List<Long>) {
        if (candidateIds.isEmpty()) return
        val now = Date()
        val candidates = candidateIds.mapNotNull { candidateBox.get(it) }
        candidates.forEach { candidate ->
            candidate.status = MemoryAutoSaveCandidate.STATUS_PROCESSING
            candidate.updatedAt = now
            candidate.lastError = ""
        }
        candidateBox.put(candidates)
    }

    fun markPending(candidateIds: List<Long>) {
        if (candidateIds.isEmpty()) return
        val now = Date()
        val candidates = candidateIds.mapNotNull { candidateBox.get(it) }
        candidates.forEach { candidate ->
            candidate.status = MemoryAutoSaveCandidate.STATUS_PENDING
            candidate.updatedAt = now
            candidate.lastError = ""
        }
        candidateBox.put(candidates)
    }

    fun deleteCandidates(candidateIds: List<Long>) {
        if (candidateIds.isEmpty()) return
        candidateIds.forEach { candidateBox.remove(it) }
    }

    fun markFailed(candidateIds: List<Long>, errorMessage: String) {
        if (candidateIds.isEmpty()) return
        val now = Date()
        val normalizedError = errorMessage.take(500)
        val candidates = candidateIds.mapNotNull { candidateBox.get(it) }
        candidates.forEach { candidate ->
            candidate.status = MemoryAutoSaveCandidate.STATUS_FAILED
            candidate.attemptCount += 1
            candidate.lastError = normalizedError
            candidate.updatedAt = now
        }
        candidateBox.put(candidates)
    }
}
