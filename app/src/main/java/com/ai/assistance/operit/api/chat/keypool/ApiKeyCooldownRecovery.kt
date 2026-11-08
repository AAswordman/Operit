package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.keypool.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.config.model.ModelConfigData

/** 配置修正与主动复测后解除相关旧冷却，避免已恢复的密钥继续等待。 */
internal object ApiKeyCooldownRecovery {
    fun onConfigSaved(previous: ModelConfigData, current: ModelConfigData) {
        if (previous.apiEndpoint != current.apiEndpoint ||
            previous.apiProviderTypeId != current.apiProviderTypeId ||
            previous.modelName != current.modelName ||
            previous.useMultipleApiKeys != current.useMultipleApiKeys
        ) {
            ApiKeyCooldownStore.clearConfig(current.id)
            return
        }
        if (previous.apiKey != current.apiKey) {
            ApiKeyCooldownStore.clear(current.id, "single")
        }
        val currentKeys = current.apiKeyPool.associateBy { it.id }
        previous.apiKeyPool.forEach { oldKey ->
            val newKey = currentKeys[oldKey.id]
            val availabilityReset = newKey != null &&
                oldKey.availabilityStatus != newKey.availabilityStatus &&
                (newKey.availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE ||
                    newKey.availabilityStatus == ApiKeyAvailabilityStatus.UNTESTED)
            if (newKey == null || oldKey.key != newKey.key || availabilityReset) {
                ApiKeyCooldownStore.clear(current.id, oldKey.id)
            }
        }
    }
}
