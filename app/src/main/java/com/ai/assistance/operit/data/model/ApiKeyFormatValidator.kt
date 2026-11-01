package com.ai.assistance.operit.data.model

object ApiKeyFormatValidator {
    fun normalize(value: String): String = value.trim()

    fun isValid(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.isNotEmpty() && normalized.all { character ->
            character.code in 0x21..0x7E
        }
    }

    fun hasUsableKey(config: ModelConfigData): Boolean {
        // 空白占位不构成密钥池，与服务创建时的实际模式保持一致。
        if (!config.useMultipleApiKeys || config.apiKeyPool.none { it.key.isNotBlank() }) {
            return isValid(config.apiKey)
        }

        val enabledKeys = config.apiKeyPool.filter { it.isEnabled }
        val hasAvailabilityMark =
            enabledKeys.any { it.availabilityStatus != ApiKeyAvailabilityStatus.UNTESTED }
        val candidateKeys =
            if (hasAvailabilityMark) {
                enabledKeys.filter {
                    it.availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE
                }
            } else {
                enabledKeys
            }

        if (candidateKeys.isNotEmpty()) {
            return candidateKeys.all { isValid(it.key) }
        }
        return !hasAvailabilityMark && isValid(config.apiKey)
    }
}
