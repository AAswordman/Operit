package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "api_key_attempt_records",
    indices = [
        Index(value = ["occurredAtMs"]),
        Index(value = ["configId", "keyId", "model", "occurredAtMs"]),
    ],
)
data class ApiKeyAttemptRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val occurredAtMs: Long,
    val configId: String,
    val keyId: String,
    val model: String,
    val success: Boolean,
    val errorClass: String,
    val httpStatus: Int? = null,
    val ttftMs: Long? = null,
    val tokensPerSec: Double? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val attemptIndex: Int,
    val stickyHit: Boolean = false,
    val stream: Boolean = true,
)