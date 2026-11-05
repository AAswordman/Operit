package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate
import com.ai.assistance.operit.data.model.MemoryAutoSaveOutcome
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.MemoryAutoSaveCandidateRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the auto-save tick loop. The loop keeps running after a failed tick so a transient database
 * or service error cannot stop automatic memory saving for the rest of the process lifetime.
 * [CancellationException] is rethrown so structured cancellation still ends the loop.
 */
internal class MemoryAutoSavePollingLoop(
    private val tickIntervalMs: Long,
    private val onStart: suspend () -> Unit = {},
    private val onTick: suspend () -> Unit,
    private val onError: (Throwable) -> Unit
) {
    suspend fun run() {
        try {
            onStart()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
        while (currentCoroutineContext().isActive) {
            try {
                delay(tickIntervalMs)
                onTick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}

internal class MemoryAutoSaveRunSummary {
    private var savedGroups = 0
    private var noMemoryGroups = 0
    private var failedGroups = 0

    fun recordSaved() {
        savedGroups += 1
    }

    fun recordNoMemory() {
        noMemoryGroups += 1
    }

    fun recordFailed() {
        failedGroups += 1
    }

    fun outcome(): MemoryAutoSaveOutcome =
        when {
            failedGroups > 0 -> MemoryAutoSaveOutcome.FAILED
            savedGroups > 0 -> MemoryAutoSaveOutcome.SAVED
            noMemoryGroups > 0 -> MemoryAutoSaveOutcome.NO_MEMORY_EXTRACTED
            else -> MemoryAutoSaveOutcome.IDLE
        }
}

class MemoryAutoSaveScheduler(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MemoryAutoSaveScheduler"
        private const val LOOP_TICK_MS = 60 * 1000L
        const val DEFAULT_POLL_INTERVAL_MS =
            MemorySearchSettingsPreferences.DEFAULT_AUTO_SAVE_INTERVAL_MINUTES * 60 * 1000L
        private const val MAX_MESSAGES_PER_BATCH = 48
        private const val MAX_CANDIDATES_PER_RUN_PER_CHAT = 20
        private const val MIN_TOTAL_CANDIDATES_TO_EXTRACT = 5

        @Volatile
        private var instance: MemoryAutoSaveScheduler? = null

        fun getInstance(): MemoryAutoSaveScheduler? = instance
    }

    private val isRunning = AtomicBoolean(false)
    @Volatile
    private var loopJob: Job? = null
    private val nextRunAtMsByProfileId = ConcurrentHashMap<String, Long>()

    fun start() {
        if (loopJob?.isActive == true) return
        instance = this
        loopJob =
            scope.launch(Dispatchers.IO) {
                AppLogger.d(TAG, "长期记忆自动保存轮询器已启动")
                MemoryAutoSavePollingLoop(
                        tickIntervalMs = LOOP_TICK_MS,
                        onStart = { recoverInterruptedCandidatesAtStartup() },
                        onTick = { runOnce() },
                        onError = { error ->
                            AppLogger.e(TAG, "长期记忆自动保存轮询异常，等待下一轮", error)
                        }
                    )
                    .run()
            }
    }

    fun getMinutesUntilNextRun(
        profileId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val target = getOrInitNextRunAtMs(profileId, nowMs)
        val remainingMs = (target - nowMs).coerceAtLeast(0L)
        return ((remainingMs + 60_000L - 1L) / 60_000L).coerceAtLeast(0L)
    }

    suspend fun runOnce() {
        if (!isRunning.compareAndSet(false, true)) {
            AppLogger.d(TAG, "上一轮长期记忆自动保存仍在运行，跳过本轮")
            return
        }
        try {
            scanAndProcessCandidates()
        } finally {
            isRunning.set(false)
        }
    }

    /**
     * Recovers candidates left in PROCESSING by a killed process before the first slow tick. The
     * per-scan recovery keeps retrying if this early pass fails, so a transient store error cannot
     * leave candidates stuck for the lifetime of the process.
     */
    private suspend fun recoverInterruptedCandidatesAtStartup() {
        val profileIds = preferencesManager.memorySpaceListFlow.first()
        var recoveredCount = 0
        var deadLetterCount = 0
        for (profileId in profileIds) {
            val repository = MemoryAutoSaveCandidateRepository(context, profileId)
            recoveredCount += repository.recoverInterruptedCandidates()
            deadLetterCount += repository.expireExhaustedCandidates()
        }
        if (recoveredCount > 0 || deadLetterCount > 0) {
            AppLogger.w(
                TAG,
                "长期记忆自动保存启动恢复完成: recovered=$recoveredCount, deadLetter=$deadLetterCount"
            )
        }
    }

    private suspend fun scanAndProcessCandidates() {
        val profileIds = preferencesManager.memorySpaceListFlow.first()
        if (profileIds.isEmpty()) return

        val toolHandler = AIToolHandler.getInstance(context)
        val memoryService =
            EnhancedAIService.getAIServiceForFunction(context, FunctionType.MEMORY)
        val chatContentDao = AppDatabase.getDatabase(context).chatContentDao()
        val nowMs = System.currentTimeMillis()

        for (profileId in profileIds) {
            try {
                processProfileCandidates(
                    profileId = profileId,
                    nowMs = nowMs,
                    toolHandler = toolHandler,
                    memoryService = memoryService,
                    chatContentDao = chatContentDao
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "长期记忆自动保存处理记忆空间失败: profileId=$profileId", e)
                MemorySearchSettingsPreferences(context, profileId)
                    .saveLastAutoSaveOutcome(MemoryAutoSaveOutcome.FAILED)
            }
        }
    }

    private suspend fun processProfileCandidates(
        profileId: String,
        nowMs: Long,
        toolHandler: AIToolHandler,
        memoryService: com.ai.assistance.operit.api.chat.llmprovider.AIService,
        chatContentDao: com.ai.assistance.operit.data.dao.ChatContentDao
    ) {
        val repository = MemoryAutoSaveCandidateRepository(context, profileId)
        // This scan is serialized by isRunning and starts before any candidate is marked processing,
        // so any PROCESSING row here was left behind by a killed process. Recovering on every scan
        // also retries a recovery that failed earlier instead of waiting for another app start.
        val recoveredInterrupted = repository.recoverInterruptedCandidates()
        val expiredExhausted = repository.expireExhaustedCandidates()
        if (recoveredInterrupted > 0 || expiredExhausted > 0) {
            AppLogger.w(
                TAG,
                "恢复中断的长期记忆候选: profileId=$profileId, recovered=$recoveredInterrupted, deadLetter=$expiredExhausted"
            )
        }

        val intervalMs = intervalMsForProfile(profileId)
        val nextRunAtMs = getOrInitNextRunAtMs(profileId, nowMs)
        if (nowMs < nextRunAtMs) {
            return
        }

        val allCandidates = repository.getPendingAndFailedCandidates()
        // The accumulation threshold only applies to fresh candidates. A retry must run even when
        // the queue is small, otherwise a single failed candidate would wait forever for new
        // messages and the bounded retry policy would never finish.
        val hasRetryCandidate = allCandidates.any { it.attemptCount > 0 }
        if (!hasRetryCandidate && allCandidates.size < MIN_TOTAL_CANDIDATES_TO_EXTRACT) {
            val nextRunAt = System.currentTimeMillis() + intervalMs
            AppLogger.d(
                TAG,
                "候选总条数不足，继续累计并重置下次执行时间: profileId=$profileId, totalCandidates=${allCandidates.size}, nextRunAt=$nextRunAt"
            )
            scheduleNextRun(profileId, nextRunAt)
            return
        }
        val groupedCandidates =
            allCandidates
                .groupBy { it.chatId }
                .filterKeys { it.isNotBlank() }

        if (groupedCandidates.isEmpty()) {
            scheduleNextRun(profileId, System.currentTimeMillis() + intervalMs)
            return
        }

        AppLogger.d(
            TAG,
            "开始处理长期记忆候选: profileId=$profileId, chats=${groupedCandidates.size}"
        )

        val summary = MemoryAutoSaveRunSummary()
        for ((chatId, candidates) in groupedCandidates) {
            val orderedCandidates =
                candidates.sortedWith(
                    compareBy<MemoryAutoSaveCandidate> { it.triggerMessageTimestamp }
                        .thenBy { it.createdAt.time }
                )
            val batchCandidates = orderedCandidates.take(MAX_CANDIDATES_PER_RUN_PER_CHAT)
            val selectedUserCandidates =
                batchCandidates.filter {
                    MemoryAutoSaveCandidate.isSelectedUserMessageSource(it.sourceType)
                }
            val automaticCandidates =
                batchCandidates.filterNot {
                    MemoryAutoSaveCandidate.isSelectedUserMessageSource(it.sourceType)
                }

            if (selectedUserCandidates.isNotEmpty()) {
                processChatCandidateGroup(
                    profileId = profileId,
                    chatId = chatId,
                    candidates = selectedUserCandidates,
                    repository = repository,
                    chatContentDao = chatContentDao,
                    toolHandler = toolHandler,
                    memoryService = memoryService,
                    summary = summary
                )
            }
            if (automaticCandidates.isNotEmpty()) {
                processChatCandidateGroup(
                    profileId = profileId,
                    chatId = chatId,
                    candidates = automaticCandidates,
                    repository = repository,
                    chatContentDao = chatContentDao,
                    toolHandler = toolHandler,
                    memoryService = memoryService,
                    summary = summary
                )
            }
        }
        MemorySearchSettingsPreferences(context, profileId).saveLastAutoSaveOutcome(summary.outcome())
        scheduleNextRun(profileId, System.currentTimeMillis() + intervalMs)
    }

    private fun intervalMsForProfile(profileId: String): Long {
        val minutes = MemorySearchSettingsPreferences(context, profileId).loadAutoSaveIntervalMinutes()
        return minutes * 60_000L
    }

    private fun getOrInitNextRunAtMs(
        profileId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        nextRunAtMsByProfileId[profileId]?.takeIf { it > 0L }?.let { return it }
        val preferences = MemorySearchSettingsPreferences(context, profileId)
        val persisted = preferences.loadNextAutoSaveRunAtMs().takeIf { it > 0L }
        val target = persisted ?: (nowMs + intervalMsForProfile(profileId))
        scheduleNextRun(profileId, target)
        return target
    }

    private fun scheduleNextRun(profileId: String, nextRunAtMs: Long) {
        val normalized = nextRunAtMs.coerceAtLeast(0L)
        nextRunAtMsByProfileId[profileId] = normalized
        MemorySearchSettingsPreferences(context, profileId).saveNextAutoSaveRunAtMs(normalized)
    }

    private suspend fun processChatCandidateGroup(
        profileId: String,
        chatId: String,
        candidates: List<MemoryAutoSaveCandidate>,
        repository: MemoryAutoSaveCandidateRepository,
        chatContentDao: com.ai.assistance.operit.data.dao.ChatContentDao,
        toolHandler: AIToolHandler,
        memoryService: com.ai.assistance.operit.api.chat.llmprovider.AIService,
        summary: MemoryAutoSaveRunSummary
    ) {
        if (candidates.isEmpty()) return

        val isSelectedUserBatch =
            candidates.all {
                MemoryAutoSaveCandidate.isSelectedUserMessageSource(it.sourceType)
            }
        val candidateIds = candidates.map { it.id }
        repository.markProcessing(candidateIds)

        try {
            val messages =
                if (isSelectedUserBatch) {
                    val selectedMessages =
                        withContext(Dispatchers.IO) {
                            candidates
                                .mapNotNull { candidate ->
                                    chatContentDao.getMessageByTimestamp(
                                        chatId = chatId,
                                        timestamp = candidate.triggerMessageTimestamp
                                    )?.toChatMessage()
                                }
                        }
                    selectedMessages
                        .filter { it.sender == "user" && it.content.isNotBlank() }
                        .sortedBy { it.timestamp }
                } else {
                    val latestTriggerTimestamp = candidates.maxOf { it.triggerMessageTimestamp }
                    withContext(Dispatchers.IO) {
                        chatContentDao.getMessagesForChatBeforeTimestampDesc(
                            chatId = chatId,
                            maxTimestamp = latestTriggerTimestamp,
                            limit = MAX_MESSAGES_PER_BATCH
                        )
                    }.asReversed().map { it.toChatMessage() }
                }

            if (messages.isEmpty()) {
                AppLogger.w(TAG, "未找到候选对应消息，直接清理候选: profileId=$profileId, chatId=$chatId")
                repository.deleteCandidates(candidateIds)
                summary.recordNoMemory()
                return
            }

            val conversationHistory =
                messages
                    .filter { message -> message.sender == "user" || message.sender == "ai" }
                    .map { message ->
                        val role =
                            if (message.sender == "user") {
                                "user"
                            } else {
                                "assistant"
                            }
                        role to message.content
                    }

            if (conversationHistory.isEmpty() || conversationHistory.none { it.first == "user" }) {
                AppLogger.w(TAG, "候选消息缺少有效用户上下文，直接清理候选: profileId=$profileId, chatId=$chatId")
                repository.deleteCandidates(candidateIds)
                summary.recordNoMemory()
                return
            }

            val memoryContent =
                if (isSelectedUserBatch) {
                    conversationHistory
                        .filter { it.first == "user" }
                        .joinToString("\n\n") { it.second }
                        .trim()
                } else {
                    conversationHistory.lastOrNull { (role, content) ->
                        role == "assistant" && content.isNotBlank()
                    }?.second.orEmpty()
                }

            if (memoryContent.isBlank()) {
                AppLogger.w(TAG, "候选消息缺少可写入的记忆内容，直接清理候选: profileId=$profileId, chatId=$chatId")
                repository.deleteCandidates(candidateIds)
                summary.recordNoMemory()
                return
            }

            val saveResult =
                MemoryLibrary.saveMemoryNow(
                    context = context,
                    toolHandler = toolHandler,
                    conversationHistory = conversationHistory,
                    content = memoryContent,
                    aiService = memoryService,
                    profileIdOverride = profileId
                )
            repository.deleteCandidates(candidateIds)
            when (saveResult) {
                MemoryLibrary.MemorySaveResult.SAVED -> {
                    summary.recordSaved()
                    AppLogger.d(
                        TAG,
                        "长期记忆候选已写入记忆库: profileId=$profileId, chatId=$chatId, candidates=${candidateIds.size}"
                    )
                }
                MemoryLibrary.MemorySaveResult.NO_MEMORY_EXTRACTED -> {
                    summary.recordNoMemory()
                    AppLogger.d(
                        TAG,
                        "长期记忆候选无需保存: profileId=$profileId, chatId=$chatId, candidates=${candidateIds.size}，模型未提取到需要保存的记忆"
                    )
                }
            }
        } catch (e: CancellationException) {
            // A cancelled run did not finish a model request, so its attempts stay unconsumed and
            // candidates return to the pending queue for the next process or scheduler run.
            repository.markPending(candidateIds)
            throw e
        } catch (e: Exception) {
            summary.recordFailed()
            AppLogger.e(TAG, "长期记忆候选处理失败: profileId=$profileId, chatId=$chatId", e)
            val deadLetterCount =
                repository.markFailed(candidateIds, e.message ?: e.javaClass.simpleName)
            if (deadLetterCount > 0) {
                AppLogger.w(
                    TAG,
                    "长期记忆候选达到重试上限，停止自动重试: profileId=$profileId, chatId=$chatId, deadLetter=$deadLetterCount"
                )
            }
        }
    }
}
