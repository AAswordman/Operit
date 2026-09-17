package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.data.model.ApiKeyAttemptRecordEntity
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

internal data class ApiKeyWindowSample(
    val success: Boolean,
    val ttftMs: Long?,
    val tokensPerSec: Double?,
    val occurredAtMs: Long,
)

internal data class ApiKeyWindowStats(
    val keyId: String,
    val successCount: Int,
    val failCount: Int,
    val meanTtftMs: Double?,
    val meanTps: Double?,
) {
    val sampleCount: Int
        get() = successCount + failCount
}

internal object ApiKeyWindowStore {
    const val MIN_WINDOW_SIZE = 30
    const val MAX_WINDOW_SIZE = 200
    const val WINDOW_MS = 24L * 60L * 60L * 1000L

    private val samplesBySlot = ConcurrentHashMap<String, ArrayDeque<ApiKeyWindowSample>>()

    fun record(
        configId: String,
        keyId: String,
        model: String,
        success: Boolean,
        ttftMs: Long?,
        tokensPerSec: Double?,
        occurredAtMs: Long,
    ) {
        if (configId.isBlank() || keyId.isBlank()) return
        val deque = samplesBySlot.getOrPut(slot(configId, keyId, model)) { ArrayDeque() }
        synchronized(deque) {
            deque.addLast(
                ApiKeyWindowSample(
                    success = success,
                    ttftMs = if (success) ttftMs else null,
                    tokensPerSec = if (success) tokensPerSec else null,
                    occurredAtMs = occurredAtMs,
                )
            )
            while (deque.size > MAX_WINDOW_SIZE) {
                deque.removeFirst()
            }
        }
    }

    fun hydrate(configId: String, records: List<ApiKeyAttemptRecordEntity>) {
        val grouped = records.groupBy { slot(configId, it.keyId, it.model) }
        grouped.forEach { (key, group) ->
            val existing = samplesBySlot[key]
            if (existing != null && existing.isNotEmpty()) {
                return@forEach
            }
            group.asReversed().forEach { record ->
                record(
                    configId = configId,
                    keyId = record.keyId,
                    model = record.model,
                    success = record.success,
                    ttftMs = record.ttftMs,
                    tokensPerSec = record.tokensPerSec,
                    occurredAtMs = record.occurredAtMs,
                )
            }
        }
    }

    fun stats(
        configId: String,
        model: String,
        keyIds: Collection<String>,
        nowMs: Long,
    ): List<ApiKeyWindowStats> {
        return keyIds.map { keyId -> statsFor(configId, keyId, model, nowMs) }
    }

    fun statsFor(
        configId: String,
        keyId: String,
        model: String,
        nowMs: Long,
    ): ApiKeyWindowStats {
        val deque = samplesBySlot[slot(configId, keyId, model)]
        val window =
            if (deque == null) {
                emptyList()
            } else {
                synchronized(deque) { selectWindow(deque.toList(), nowMs) }
            }
        var successCount = 0
        var failCount = 0
        var ttftSum = 0.0
        var ttftCount = 0
        var tpsSum = 0.0
        var tpsCount = 0
        window.forEach { sample ->
            if (sample.success) {
                successCount += 1
                sample.ttftMs?.let {
                    ttftSum += it.toDouble()
                    ttftCount += 1
                }
                sample.tokensPerSec?.let {
                    tpsSum += it
                    tpsCount += 1
                }
            } else {
                failCount += 1
            }
        }
        return ApiKeyWindowStats(
            keyId = keyId,
            successCount = successCount,
            failCount = failCount,
            meanTtftMs = if (ttftCount > 0) ttftSum / ttftCount else null,
            meanTps = if (tpsCount > 0) tpsSum / tpsCount else null,
        )
    }

    fun resetForTests() {
        samplesBySlot.clear()
    }

    internal fun selectWindow(
        samples: List<ApiKeyWindowSample>,
        nowMs: Long,
    ): List<ApiKeyWindowSample> {
        if (samples.isEmpty()) return emptyList()
        val newestFirst = samples.sortedByDescending { it.occurredAtMs }
        val lastN = newestFirst.take(MIN_WINDOW_SIZE)
        val lastDay = newestFirst.filter { nowMs - it.occurredAtMs <= WINDOW_MS }
        val wider = if (lastDay.size >= lastN.size) lastDay else lastN
        return wider.take(MAX_WINDOW_SIZE)
    }

    private fun slot(configId: String, keyId: String, model: String): String =
        "$configId|$keyId|$model"
}