package com.ai.assistance.operit.api.chat.keypool

import java.util.concurrent.ConcurrentHashMap

internal object ApiKeyStickyStore {
    const val TTL_MS = 45L * 60L * 1000L

    data class Pin(
        val keyId: String,
        val lastSuccessAtMs: Long,
    )

    private val pins = ConcurrentHashMap<String, Pin>()

    fun get(configId: String, model: String, nowMs: Long): String? {
        val pin = pins[slot(configId, model)] ?: return null
        if (nowMs - pin.lastSuccessAtMs > TTL_MS) {
            pins.remove(slot(configId, model), pin)
            return null
        }
        return pin.keyId
    }

    fun pin(configId: String, model: String, keyId: String, nowMs: Long) {
        if (configId.isBlank() || keyId.isBlank()) return
        pins[slot(configId, model)] = Pin(keyId = keyId, lastSuccessAtMs = nowMs)
    }

    fun unpin(configId: String, model: String, keyId: String? = null) {
        val key = slot(configId, model)
        if (keyId == null) {
            pins.remove(key)
            return
        }
        pins.computeIfPresent(key) { _, pin ->
            if (pin.keyId == keyId) null else pin
        }
    }

    fun resetForTests() {
        pins.clear()
    }

    private fun slot(configId: String, model: String): String = "$configId|$model"
}