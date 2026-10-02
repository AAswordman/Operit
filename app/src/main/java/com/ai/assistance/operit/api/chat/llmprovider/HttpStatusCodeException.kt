package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.AppLogger

interface HttpStatusCodeException {
    val statusCode: Int
    val retryAfterMs: Long?
        get() = null
}

internal suspend fun shouldSuppressKeyPoolRateLimitNotice(
    apiKeyProvider: ApiKeyProvider,
    exception: Exception,
    logTag: String
): Boolean {
    val httpException = exception as? HttpStatusCodeException ?: return false
    if (httpException.statusCode != 429) return false

    val candidateKeyCount = apiKeyProvider.getCandidateKeyCount()
    val shouldSuppress = candidateKeyCount > 1
    if (shouldSuppress) {
        AppLogger.w(
            logTag,
            "多密钥轮询遇到 429，跳过本次中间提示，继续尝试其他密钥。candidateKeyCount=$candidateKeyCount",
            exception
        )
    }

    return shouldSuppress
}
