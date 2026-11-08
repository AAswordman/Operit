package com.ai.assistance.operit.api.chat.keypool

import java.util.concurrent.ConcurrentHashMap

internal data class ApiKeyCooldown(
    val keyId: String,
    val model: String,
    val untilMs: Long,
)

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

    fun activeCooldowns(configId: String, nowMs: Long): List<ApiKeyCooldown> {
        val prefix = "$configId|"
        return untilMsBySlot.mapNotNull { (slot, untilMs) ->
            if (!slot.startsWith(prefix) || untilMs <= nowMs) return@mapNotNull null
            val keyAndModel = slot.removePrefix(prefix)
            ApiKeyCooldown(
                keyId = keyAndModel.substringBefore('|'),
                model = keyAndModel.substringAfter('|'),
                untilMs = untilMs,
            )
        }
    }

    fun clearConfig(configId: String) {
        val prefix = "$configId|"
        untilMsBySlot.keys.removeAll { it.startsWith(prefix) }
    }

    fun clearForModel(configId: String, keyId: String, model: String) {
        // 本次成功证明全局鉴权与当前模型已恢复，保留其他模型的独立冷却。
        untilMsBySlot.remove(globalSlot(configId, keyId))
        untilMsBySlot.remove(modelSlot(configId, keyId, model))
    }

    fun resetForTests() {
        untilMsBySlot.clear()
    }

    private fun globalSlot(configId: String, keyId: String): String = "$configId|$keyId|"

    private fun modelSlot(configId: String, keyId: String, model: String): String =
        "$configId|$keyId|$model"
}