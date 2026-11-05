package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate
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
            .filter { it.isRetryable() }
            .sortedBy { it.createdAt.time }
    }

    /**
     * Recovers candidates left in [MemoryAutoSaveCandidate.STATUS_PROCESSING] by a process that
     * died mid-run. The scheduler calls this before it marks any candidate processing, so rows found
     * here cannot belong to an in-flight batch of the current process.
     */
    fun recoverInterruptedCandidates(): Int {
        val now = Date()
        val candidates =
            candidateBox
                .query(MemoryAutoSaveCandidate_.status.equal(MemoryAutoSaveCandidate.STATUS_PROCESSING))
                .build()
                .find()
        if (candidates.isEmpty()) return 0
        candidates.forEach { candidate -> candidate.recoverInterrupted(now) }
        candidateBox.put(candidates)
        return candidates.size
    }

    /**
     * Moves legacy failed rows that already exhausted the retry budget into the terminal state.
     * Older builds only incremented [MemoryAutoSaveCandidate.attemptCount], so those rows would
     * otherwise stay invisible to the retry query without ever becoming observable dead letters.
     */
    fun expireExhaustedCandidates(): Int {
        val now = Date()
        val candidates =
            candidateBox
                .query(
                    MemoryAutoSaveCandidate_.status
                        .equal(MemoryAutoSaveCandidate.STATUS_FAILED)
                        .and(
                            MemoryAutoSaveCandidate_.attemptCount.greaterOrEqual(
                                MemoryAutoSaveCandidate.MAX_ATTEMPTS
                            )
                        )
                )
                .build()
                .find()
        if (candidates.isEmpty()) return 0
        candidates.forEach { candidate ->
            candidate.status = MemoryAutoSaveCandidate.STATUS_DEAD_LETTER
            candidate.updatedAt = now
        }
        candidateBox.put(candidates)
        return candidates.size
    }

    fun countPendingAndFailedChats(): Int {
        return getPendingAndFailedCandidates()
            .map { it.chatId }
            .filter { it.isNotBlank() }
            .distinct()
            .size
    }

    fun countPendingAndFailedCandidates(): Int {
        return getPendingAndFailedCandidates().size
    }

    fun countDeadLetterCandidates(): Int {
        return candidateBox
            .query(MemoryAutoSaveCandidate_.status.equal(MemoryAutoSaveCandidate.STATUS_DEAD_LETTER))
            .build()
            .count()
            .toInt()
    }

    fun markProcessing(candidateIds: List<Long>) {
        if (candidateIds.isEmpty()) return
        val now = Date()
        val candidates = candidateIds.mapNotNull { candidateBox.get(it) }
        candidates.forEach { candidate -> candidate.beginProcessing(now) }
        candidateBox.put(candidates)
    }

    fun markPending(candidateIds: List<Long>) {
        if (candidateIds.isEmpty()) return
        val now = Date()
        val candidates = candidateIds.mapNotNull { candidateBox.get(it) }
        candidates.forEach { candidate -> candidate.restorePending(now) }
        candidateBox.put(candidates)
    }

    fun deleteCandidates(candidateIds: List<Long>) {
        if (candidateIds.isEmpty()) return
        candidateIds.forEach { candidateBox.remove(it) }
    }

    fun markFailed(candidateIds: List<Long>, errorMessage: String): Int {
        if (candidateIds.isEmpty()) return 0
        val now = Date()
        val candidates = candidateIds.mapNotNull { candidateBox.get(it) }
        var deadLetterCount = 0
        candidates.forEach { candidate ->
            candidate.markFailed(now = now, errorMessage = errorMessage)
            if (candidate.status == MemoryAutoSaveCandidate.STATUS_DEAD_LETTER) {
                deadLetterCount += 1
            }
        }
        candidateBox.put(candidates)
        return deadLetterCount
    }
}
