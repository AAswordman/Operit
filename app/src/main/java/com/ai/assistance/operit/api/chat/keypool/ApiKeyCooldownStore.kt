package com.ai.assistance.operit.api.chat.keypool

import java.util.concurrent.ConcurrentHashMap

internal object ApiKeyCooldownStore {
    private val untilMsBySlot = ConcurrentHashMap<String, Long>()

    fun cooldownUntilMs(configId: String, keyId: String, model: String): Long {
        val global = untilMsBySlot[globalSlot(configId, keyId)] ?: 0L
        val perModel = untilMsBySlot[modelSlot(configId, keyId, model)] ?: 0L
        return maxOf(global, perModel)
    }

    fun apply(
        configId: String,
        keyId: String,
        model: String,
        errorClass: ApiKeyErrorClass,
        retryAfterMs: Long?,
        nowMs: Long,
    ) {
        if (keyId.isBlank() || errorClass == ApiKeyErrorClass.NONE) return
        val until = ApiKeyRetryPlanner.cooldownMs(errorClass, retryAfterMs, nowMs)
        if (until <= nowMs) return
        val slot =
            if (errorClass == ApiKeyErrorClass.MODEL) {
                modelSlot(configId, keyId, model)
            } else {
                globalSlot(configId, keyId)
            }
        untilMsBySlot.merge(slot, until) { current, incoming -> maxOf(current, incoming) }
    }

    fun clear(configId: String, keyId: String) {
        val prefix = "$configId|$keyId|"
        untilMsBySlot.keys.removeAll { it.startsWith(prefix) }
    }

    fun resetForTests() {
        untilMsBySlot.clear()
    }

    private fun globalSlot(configId: String, keyId: String): String = "$configId|$keyId|"

    private fun modelSlot(configId: String, keyId: String, model: String): String =
        "$configId|$keyId|$model"
}