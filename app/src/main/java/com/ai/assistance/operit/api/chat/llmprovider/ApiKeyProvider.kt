package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.api.chat.keypool.*
import com.ai.assistance.operit.data.model.ApiKeyAttemptRecordEntity
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.stats.ApiKeyAttemptRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * API密钥提供程序接口
 * 抽象了API密钥的获取逻辑，以支持单个密钥和密钥池轮询。
 */
interface ApiKeyProvider {
    /** 获取当前可用的API Key */
    suspend fun getApiKey(): String

    /** 获取当前会参与轮询的API Key数量 */
    suspend fun getCandidateKeyCount(): Int

    fun createRequestSession(model: String, stream: Boolean): ApiKeyRequestSession {
        return PassthroughApiKeyRequestSession { getApiKey() }
    }
}

suspend fun <T> ApiKeyProvider.withRequestSession(
    model: String,
    stream: Boolean,
    block: suspend () -> T,
): T {
    val session = createRequestSession(model, stream)
    return withContext(ApiKeyRequestElement(session)) { block() }
}

/**
 * 单个API Key的简单提供程序，用于兼容旧配置。
 */
class SingleApiKeyProvider(private val apiKey: String) : ApiKeyProvider {
    override suspend fun getApiKey(): String {
        val session = currentApiKeySession()
        if (session != null) {
            return session.nextKey().key
        }
        AppLogger.d("ApiKeyProvider", "Using single API key: ${apiKey.take(4)}...${apiKey.takeLast(4)}")
        return apiKey
    }

    override suspend fun getCandidateKeyCount(): Int = if (apiKey.isNotBlank()) 1 else 0

    override fun createRequestSession(model: String, stream: Boolean): ApiKeyRequestSession {
        return SingleApiKeyRequestSession(apiKey = apiKey, model = model, stream = stream)
    }
}

private class SingleApiKeyRequestSession(
    private val apiKey: String,
    private val model: String,
    private val stream: Boolean,
) : ApiKeyRequestSession {
    private var summary = ApiKeyTriedSummary()
    private var httpAttempts = 0
    private var sameKeyAttempts = 0
    private val maxAttempts = ApiKeyRetryPlanner.maxHttpAttempts(candidateCount = 1, poolMode = false)

    override suspend fun nextKey(): ApiKeySelection {
        AppLogger.d("ApiKeyProvider", "Using single API key: ${apiKey.take(4)}...${apiKey.takeLast(4)}")
        return ApiKeySelection(
            key = apiKey,
            keyId = SINGLE_KEY_ID,
            name = "",
            candidateCount = if (apiKey.isNotBlank()) 1 else 0,
        )
    }

    override suspend fun reportOutcome(report: ApiKeyAttemptReport) {
        httpAttempts = maxOf(httpAttempts, report.attemptIndex)
        if (report.success) {
            sameKeyAttempts = 0
            return
        }
        sameKeyAttempts += 1
        summary = summary.plus(report.errorClass)
    }

    override suspend fun maxAttempts(): Int = maxAttempts

    override fun triedSummary(): ApiKeyTriedSummary = summary

    override suspend fun decideRetry(errorClass: ApiKeyErrorClass, retryAfterMs: Long?): ApiKeyRetryDecision {
        return ApiKeyRetryPlanner.decide(
            errorClass = errorClass,
            sameKeyAttempts = sameKeyAttempts,
            remainingCandidatesAfterExclude = 0,
            httpAttemptsIncludingThis = httpAttempts,
            maxHttpAttempts = maxAttempts,
            retryAfterMs = retryAfterMs,
        )
    }

    companion object {
        const val SINGLE_KEY_ID = "single"
    }
}

/**
 * 多API Key提供程序，实现密钥的轮询和状态管理。
 * @param configId 配置ID
 * @param modelConfigManager 用于读取和更新模型配置的管理器
 */
class MultiApiKeyProvider(
    private val configId: String,
    private val modelConfigManager: ModelConfigManager
) : ApiKeyProvider {
    private val mutex = Mutex()
    private val attemptRepository by lazy {
        ApiKeyAttemptRepository.getInstance(modelConfigManager.appContext)
    }

    override suspend fun getApiKey(): String {
        val session = currentApiKeySession()
        if (session != null) {
            return session.nextKey().key
        }
        return mutex.withLock { pickWithoutSession().key }
    }

    override suspend fun getCandidateKeyCount(): Int {
        val session = currentApiKeySession()
        if (session != null) {
            return session.maxAttempts().coerceAtLeast(0)
        }
        return mutex.withLock {
            val config = modelConfigManager.getModelConfig(configId)
                ?: throw IllegalStateException("Config with ID $configId not found")
            ApiKeyPoolSelector.candidateCount(
                pool = config.apiKeyPool,
                excludedIds = emptySet(),
                nowMs = System.currentTimeMillis(),
                model = "",
                cooldownUntilMs = { keyId, model ->
                    ApiKeyCooldownStore.cooldownUntilMs(configId, keyId, model)
                },
            )
        }
    }

    override fun createRequestSession(model: String, stream: Boolean): ApiKeyRequestSession {
        return MultiApiKeyRequestSession(
            configId = configId,
            model = model,
            stream = stream,
            modelConfigManager = modelConfigManager,
            mutex = mutex,
            attemptRepository = attemptRepository,
        )
    }

    private suspend fun pickWithoutSession(): ApiKeySelection {
        val config = modelConfigManager.getModelConfig(configId)
            ?: throw IllegalStateException("Config with ID $configId not found")
        return when (
            val pick =
                ApiKeyPoolSelector.select(
                    pool = config.apiKeyPool,
                    fallbackSingleKey = config.apiKey,
                    currentIndex = config.currentKeyIndex,
                    excludedIds = emptySet(),
                    nowMs = System.currentTimeMillis(),
                    model = "",
                    cooldownUntilMs = { keyId, model ->
                        ApiKeyCooldownStore.cooldownUntilMs(configId, keyId, model)
                    },
                )
        ) {
            is ApiKeyPick.Selected -> {
                modelConfigManager.updateConfigKeyIndex(configId, pick.nextIndex)
                AppLogger.d(
                    "ApiKeyProvider",
                    "Config ${config.name}: Using key '${pick.name}' (sk-...${pick.key.takeLast(4)})"
                )
                ApiKeySelection(pick.key, pick.id, pick.name, pick.candidateCount)
            }
            is ApiKeyPick.FallbackSingle -> {
                AppLogger.d("ApiKeyProvider", "Config ${config.name}: Falling back to single API key")
                ApiKeySelection(pick.key, SingleApiKeyRequestSession.SINGLE_KEY_ID, "", 1)
            }
            is ApiKeyPick.Exhausted -> throw exhausted(pick.reason, config.name)
        }
    }

    companion object {
        internal fun exhausted(reason: String, configName: String): ApiKeyPoolExhaustedException {
            val message =
                when (reason) {
                    "no_available_marks" ->
                        "No AVAILABLE API keys in pool for $configName. Please test keys or clear availability marks."
                    "all_cooling_or_excluded" ->
                        "All API keys in pool for $configName are cooling down or already tried."
                    else ->
                        "API key pool for $configName is empty or all keys are disabled, and no fallback API key is available."
                }
            return ApiKeyPoolExhaustedException(message, ApiKeyTriedSummary())
        }
    }
}

private class MultiApiKeyRequestSession(
    private val configId: String,
    private val model: String,
    private val stream: Boolean,
    private val modelConfigManager: ModelConfigManager,
    private val mutex: Mutex,
    private val attemptRepository: ApiKeyAttemptRepository,
) : ApiKeyRequestSession {
    private val excludedIds = linkedSetOf<String>()
    private var current: ApiKeySelection? = null
    private var sameKeyAttempts = 0
    private var httpAttempts = 0
    private var summary = ApiKeyTriedSummary()
    private var cachedMaxAttempts: Int? = null
    private var initialCandidateCount: Int = 1

    override suspend fun nextKey(): ApiKeySelection {
        return mutex.withLock {
            val keepCurrent = current?.takeIf { selection ->
                selection.keyId.isNotBlank() &&
                    selection.keyId !in excludedIds &&
                    sameKeyAttempts in 1 until 2
            }
            if (keepCurrent != null) {
                return@withLock keepCurrent
            }

            val config = modelConfigManager.getModelConfig(configId)
                ?: throw IllegalStateException("Config with ID $configId not found")
            hydrateWindowIfNeeded()
            val nowMs = System.currentTimeMillis()
            val failover = excludedIds.isNotEmpty()
            val scores = scoreMap(config, nowMs)
            val pick =
                ApiKeyPoolSelector.select(
                    pool = config.apiKeyPool,
                    fallbackSingleKey = config.apiKey,
                    currentIndex = config.currentKeyIndex,
                    excludedIds = excludedIds,
                    nowMs = nowMs,
                    model = model,
                    cooldownUntilMs = { keyId, modelName ->
                        ApiKeyCooldownStore.cooldownUntilMs(configId, keyId, modelName)
                    },
                    stickyKeyId = ApiKeyStickyStore.get(configId, model, nowMs),
                    scores = scores,
                    failover = failover,
                )
            val selection =
                when (pick) {
                    is ApiKeyPick.Selected -> {
                        modelConfigManager.updateConfigKeyIndex(configId, pick.nextIndex)
                        AppLogger.d(
                            "ApiKeyProvider",
                            "Config ${config.name}: Using key ${pick.name.ifBlank { pick.id }} (sk-...${pick.key.takeLast(4)}), candidates=${pick.candidateCount}, sticky=${pick.stickyHit}, failover=$failover"
                        )
                        ApiKeySelection(
                            key = pick.key,
                            keyId = pick.id,
                            name = pick.name,
                            candidateCount = pick.candidateCount,
                            stickyHit = pick.stickyHit,
                        )
                    }
                    is ApiKeyPick.FallbackSingle -> {
                        AppLogger.d("ApiKeyProvider", "Config ${config.name}: Falling back to single API key")
                        ApiKeySelection(pick.key, SingleApiKeyRequestSession.SINGLE_KEY_ID, "", 1)
                    }
                    is ApiKeyPick.Exhausted -> {
                        val message = MultiApiKeyProvider.exhausted(pick.reason, config.name).message
                            ?: pick.reason
                        throw ApiKeyPoolExhaustedException(message, summary)
                    }
                }
            resolveAttemptBudgetLocked(config)
            current = selection
            sameKeyAttempts = 0
            selection
        }
    }

    override suspend fun reportOutcome(report: ApiKeyAttemptReport) {
        val selection = current
        httpAttempts = maxOf(httpAttempts, report.attemptIndex)
        val nowMs = System.currentTimeMillis()
        val modelName = report.model.ifBlank { model }
        if (report.success) {
            sameKeyAttempts = 0
            if (selection != null && selection.keyId.isNotBlank()) {
                ApiKeyStickyStore.pin(configId, modelName, selection.keyId, nowMs)
                ApiKeyWindowStore.record(
                    configId = configId,
                    keyId = selection.keyId,
                    model = modelName,
                    success = true,
                    ttftMs = report.ttftMs,
                    tokensPerSec = report.tokensPerSec,
                    occurredAtMs = nowMs,
                )
            }
            persist(report, selection)
            return
        }
        sameKeyAttempts += 1
        summary = summary.plus(report.errorClass)
        if (selection != null && selection.keyId.isNotBlank()) {
            ApiKeyCooldownStore.apply(
                configId = configId,
                keyId = selection.keyId,
                model = modelName,
                errorClass = report.errorClass,
                retryAfterMs = report.retryAfterMs,
                nowMs = nowMs,
            )
            ApiKeyWindowStore.record(
                configId = configId,
                keyId = selection.keyId,
                model = modelName,
                success = false,
                ttftMs = null,
                tokensPerSec = null,
                occurredAtMs = nowMs,
            )
            val remainingAfterThisKey =
                (initialCandidateCount - excludedIds.size - 1).coerceAtLeast(0)
            if (
                ApiKeyRetryPlanner.switchesKeyImmediately(
                    report.errorClass,
                    remainingAfterThisKey,
                ) || sameKeyAttempts >= 2
            ) {
                ApiKeyStickyStore.unpin(configId, modelName, selection.keyId)
                excludedIds += selection.keyId
                current = null
                sameKeyAttempts = 0
            }
        }
        persist(report, selection)
    }

    override suspend fun maxAttempts(): Int {
        return mutex.withLock {
            val config = modelConfigManager.getModelConfig(configId)
                ?: throw IllegalStateException("Config with ID $configId not found")
            resolveAttemptBudgetLocked(config)
        }
    }

    override fun triedSummary(): ApiKeyTriedSummary = summary

    override suspend fun decideRetry(errorClass: ApiKeyErrorClass, retryAfterMs: Long?): ApiKeyRetryDecision {
        val maxHttpAttempts = maxAttempts()
        val currentStillUsable =
            current != null && current!!.keyId.isNotBlank() && current!!.keyId !in excludedIds
        val remainingAfterExclude =
            (initialCandidateCount - excludedIds.size).coerceAtLeast(0)
        return ApiKeyRetryPlanner.decide(
            errorClass = errorClass,
            sameKeyAttempts = if (currentStillUsable) sameKeyAttempts else 2,
            remainingCandidatesAfterExclude = remainingAfterExclude,
            httpAttemptsIncludingThis = httpAttempts,
            maxHttpAttempts = maxHttpAttempts,
            retryAfterMs = retryAfterMs,
        )
    }

    private fun resolveAttemptBudgetLocked(
        config: com.ai.assistance.operit.data.model.ModelConfigData,
    ): Int {
        cachedMaxAttempts?.let { return it }
        val nowMs = System.currentTimeMillis()
        val count =
            ApiKeyPoolSelector.candidateCount(
                pool = config.apiKeyPool,
                excludedIds = excludedIds,
                nowMs = nowMs,
                model = model,
                cooldownUntilMs = { keyId, modelName ->
                    ApiKeyCooldownStore.cooldownUntilMs(configId, keyId, modelName)
                },
            ).coerceAtLeast(1)
        initialCandidateCount = count
        val attempts = ApiKeyRetryPlanner.maxHttpAttempts(count, poolMode = true)
        cachedMaxAttempts = attempts
        AppLogger.d(
            "ApiKeyProvider",
            "Config $configId: key pool attempt budget=$attempts for $count candidate(s)"
        )
        return attempts
    }

    private var windowHydrated = false

    private suspend fun hydrateWindowIfNeeded() {
        if (windowHydrated) return
        windowHydrated = true
        try {
            val sinceMs = System.currentTimeMillis() - ApiKeyWindowStore.WINDOW_MS
            val records = attemptRepository.recentForConfig(configId, sinceMs)
            ApiKeyWindowStore.hydrate(configId, records)
        } catch (e: Exception) {
            AppLogger.w("ApiKeyProvider", "hydrate key window failed", e)
        }
    }

    private fun scoreMap(
        config: com.ai.assistance.operit.data.model.ModelConfigData,
        nowMs: Long,
    ): Map<String, ApiKeyScore> {
        val keyIds = config.apiKeyPool.map { it.id }.filter { it.isNotBlank() }
        if (keyIds.isEmpty()) return emptyMap()
        return ApiKeyScoreCalculator.scores(
            ApiKeyWindowStore.stats(configId, model, keyIds, nowMs)
        ).associateBy { it.keyId }
    }

    private suspend fun persist(report: ApiKeyAttemptReport, selection: ApiKeySelection?) {
        val keyId = selection?.keyId.orEmpty()
        if (keyId.isBlank()) return
        attemptRepository.recordQuietly(
            ApiKeyAttemptRecordEntity(
                occurredAtMs = System.currentTimeMillis(),
                configId = configId,
                keyId = keyId,
                model = report.model.ifBlank { model },
                success = report.success,
                errorClass = report.errorClass.name,
                httpStatus = report.httpStatus,
                ttftMs = report.ttftMs,
                tokensPerSec = report.tokensPerSec,
                inputTokens = report.inputTokens,
                outputTokens = report.outputTokens,
                cachedInputTokens = report.cachedInputTokens,
                attemptIndex = report.attemptIndex,
                stickyHit = selection?.stickyHit == true,
                stream = report.stream,
            )
        )
    }
}

internal class PassthroughApiKeyRequestSession(
    private val fetchKey: suspend () -> String,
) : ApiKeyRequestSession {
    private var summary = ApiKeyTriedSummary()
    private var httpAttempts = 0
    private var sameKeyAttempts = 0
    private val maxAttempts = ApiKeyRetryPlanner.maxHttpAttempts(1, poolMode = false)

    override suspend fun nextKey(): ApiKeySelection {
        val key = fetchKey()
        return ApiKeySelection(
            key = key,
            keyId = "passthrough",
            name = "",
            candidateCount = if (key.isNotBlank()) 1 else 0,
        )
    }

    override suspend fun reportOutcome(report: ApiKeyAttemptReport) {
        httpAttempts = maxOf(httpAttempts, report.attemptIndex)
        if (report.success) {
            sameKeyAttempts = 0
            return
        }
        sameKeyAttempts += 1
        summary = summary.plus(report.errorClass)
    }

    override suspend fun maxAttempts(): Int = maxAttempts

    override fun triedSummary(): ApiKeyTriedSummary = summary

    override suspend fun decideRetry(errorClass: ApiKeyErrorClass, retryAfterMs: Long?): ApiKeyRetryDecision {
        return ApiKeyRetryPlanner.decide(
            errorClass = errorClass,
            sameKeyAttempts = sameKeyAttempts,
            remainingCandidatesAfterExclude = 0,
            httpAttemptsIncludingThis = httpAttempts,
            maxHttpAttempts = maxAttempts,
            retryAfterMs = retryAfterMs,
        )
    }
}
