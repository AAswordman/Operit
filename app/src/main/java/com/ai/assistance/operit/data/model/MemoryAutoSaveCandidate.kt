package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.Date

@Entity
data class MemoryAutoSaveCandidate(
    @Id var id: Long = 0,
    @Index var chatId: String = "",
    var triggerMessageTimestamp: Long = 0L,
    var createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    var status: String = STATUS_PENDING,
    var attemptCount: Int = 0,
    var lastError: String = "",
    var sourceType: String = SOURCE_TYPE_REPLY_FINALIZED_AUTO
) {
    fun beginProcessing(now: Date = Date()) {
        status = STATUS_PROCESSING
        updatedAt = now
        lastError = ""
    }

    fun markFailed(now: Date = Date(), errorMessage: String) {
        attemptCount += 1
        status = statusAfterFailureAttempt(attemptCount)
        lastError = errorMessage.take(MAX_ERROR_LENGTH)
        updatedAt = now
    }

    /**
     * A cancelled attempt did not reach the model result, so it must stay retryable. Keeping the
     * original attempt count avoids turning a user cancellation into a consumable retry.
     */
    fun restorePending(now: Date = Date()) {
        status = STATUS_PENDING
        lastError = ""
        updatedAt = now
    }

    /**
     * A process can be killed while a candidate is marked processing. Recovery counts that attempt,
     * otherwise a crash loop would repeat model requests without ever reaching the retry limit.
     */
    fun recoverInterrupted(now: Date = Date()) {
        attemptCount += 1
        status = statusAfterInterruptionAttempt(attemptCount)
        lastError = ERROR_INTERRUPTED
        updatedAt = now
    }

    fun isRetryable(): Boolean =
        (status == STATUS_PENDING || status == STATUS_FAILED) && attemptCount < MAX_ATTEMPTS

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_FAILED = "failed"
        const val STATUS_DEAD_LETTER = "dead_letter"

        const val MAX_ATTEMPTS = 3
        private const val MAX_ERROR_LENGTH = 500
        const val ERROR_INTERRUPTED = "Processing interrupted before completion"

        const val SOURCE_TYPE_REPLY_FINALIZED_AUTO = "reply_finalized_auto"
        const val SOURCE_TYPE_SELECTED_USER_MESSAGE = "selected_user_message"

        fun isSelectedUserMessageSource(sourceType: String): Boolean {
            return sourceType == SOURCE_TYPE_SELECTED_USER_MESSAGE
        }

        fun statusAfterFailureAttempt(attemptCount: Int): String =
            if (attemptCount >= MAX_ATTEMPTS) STATUS_DEAD_LETTER else STATUS_FAILED

        fun statusAfterInterruptionAttempt(attemptCount: Int): String =
            if (attemptCount >= MAX_ATTEMPTS) STATUS_DEAD_LETTER else STATUS_PENDING
    }
}
