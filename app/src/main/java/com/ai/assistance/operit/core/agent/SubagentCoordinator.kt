package com.ai.assistance.operit.core.agent

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatTurnOptions
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.data.model.SubagentRunStatus
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.repository.CreateSubagentRunRequest
import com.ai.assistance.operit.data.repository.SubagentRunRepository
import com.ai.assistance.operit.services.core.ChatTurnDispatchRequest
import com.ai.assistance.operit.services.core.ChatTurnDispatchResult
import com.ai.assistance.operit.services.core.ChatTurnDispatcher
import com.ai.assistance.operit.services.core.ChatTurnOutcome
import com.ai.assistance.operit.services.core.ChatTurnSession
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SubagentTaskRequest(
    val parentChatId: String,
    val parentToolCallId: String?,
    val parentAgentName: String?,
    val title: String,
    val prompt: String,
    val subagentType: String,
    val taskId: String? = null,
    /** Actual model used by the parent turn; inherited when the profile has no fixed model. */
    val parentModelConfigId: String? = null,
    val parentModelIndex: Int? = null,
    /** Invoked right after this coordinator creates the executing run/child (suspend-safe). */
    val onRunCreated: (suspend (SubagentRunEntity) -> Unit)? = null,
    /** v2 owns admission and lifetime; this runner retains transcript/model/terminal handling. */
    val collaborationSystemPrompt: String? = null,
    val onTurnStarted: (() -> Unit)? = null,
    val collaborationHistory: List<com.ai.assistance.operit.core.chat.hooks.PromptTurn> = emptyList(),
    val profileOverride: AgentProfile? = null,
    val collaborationHistoryCutoff: Long? = null,
    val collaborationRoleCardId: String? = null,
    val externalOwnerType: String? = null,
    val externalOwnerId: String? = null,
)

internal fun SubagentTaskRequest.toChatTurnOptions(
    systemPrompt: String,
    assistantRoleName: String,
): ChatTurnOptions =
    ChatTurnOptions(
        persistTurn = true,
        notifyReply = false,
        isSubTask = true,
        isCollaborationAgent = collaborationSystemPrompt != null,
        collaborationHistory = collaborationHistory,
        collaborationHistoryCutoff = collaborationHistoryCutoff,
        systemPromptOverride = systemPrompt,
        userRoleNameOverride = parentAgentName,
        assistantRoleNameOverride = assistantRoleName,
    )

sealed interface SubagentTaskResult {
    val run: SubagentRunEntity

    data class Completed(
        override val run: SubagentRunEntity,
        val outcome: ChatTurnOutcome,
    ) : SubagentTaskResult

    data class AlreadyRunning(
        override val run: SubagentRunEntity,
    ) : SubagentTaskResult
}

class SubagentExecutionException(
    val taskId: String,
    cause: Throwable,
) : Exception(cause.message ?: cause.javaClass.simpleName, cause)

private class SubagentChatDeletionInProgressException(message: String) :
    IllegalStateException(message)

/**
 * Foreground Subagent lifecycle and per-parent scheduler.
 *
 * Each stable parent chat owns a fair 10-permit semaphore. There is intentionally no global
 * semaphore, so unrelated parent chats do not consume one another's Subagent capacity.
 */
class SubagentCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val runRepository = SubagentRunRepository.getInstance(appContext)
    private val profileRepository = AgentProfileRepository.instance
    private val modelConfigManager = ModelConfigManager(appContext)
    private val chatTurnDispatcher = ChatTurnDispatcher()
    private val chatCore =
        ChatRuntimeHolder.getInstance(appContext).getCore(ChatRuntimeSlot.MAIN)
    private val parentSemaphores = ConcurrentHashMap<String, Semaphore>()
    private val modelSemaphores = ConcurrentHashMap<String, AdjustableConcurrencyGate>()
    private val modelSemaphoreUpdateMutexes = ConcurrentHashMap<String, Mutex>()
    private val taskMutexes = ConcurrentHashMap<String, Mutex>()
    private val taskJobs = ConcurrentHashMap<String, Job>()
    private val taskParents = ConcurrentHashMap<String, String>()
    private val activeSessions = ConcurrentHashMap<String, ChatTurnSession>()
    private val deletingChatIds = ConcurrentHashMap.newKeySet<String>()
    private val lifecycleLock = Any()
    private val lifecycleGate = Mutex()
    private val profileJson = Json { ignoreUnknownKeys = true }

    suspend fun runTask(request: SubagentTaskRequest): SubagentTaskResult {
        require(request.title.isNotBlank()) { "Subagent title must not be blank" }
        require(request.prompt.isNotBlank()) { "Subagent prompt must not be blank" }

        val requestedTaskId = request.taskId
        if (requestedTaskId != null) {
            val taskMutex = taskMutexes.getOrPut(requestedTaskId) { Mutex() }
            if (!taskMutex.tryLock()) {
                return SubagentTaskResult.AlreadyRunning(
                    requireNotNull(runRepository.getById(requestedTaskId))
                )
            }
            try {
                return supervisorScope {
                    val registered =
                        lifecycleGate.withLock {
                            val existing = resolveRun(request)
                            if (existing.run.status in ACTIVE_STATUS_NAMES) {
                                return@withLock null
                            }
                            registerTask(this@supervisorScope, request, existing)
                        }
                    if (registered == null) {
                        return@supervisorScope SubagentTaskResult.AlreadyRunning(
                            requireNotNull(runRepository.getById(requestedTaskId))
                        )
                    }
                    awaitRegisteredTask(registered)
                }
            } finally {
                taskMutex.unlock()
            }
        }

        return supervisorScope {
            val registered =
                lifecycleGate.withLock {
                    check(!deletingChatIds.contains(request.parentChatId)) {
                        "Parent chat ${request.parentChatId} is being deleted"
                    }
                    registerTask(this@supervisorScope, request, resolveRun(request))
                }
            taskMutexes.getOrPut(registered.resolved.run.id) { Mutex() }.withLock {
                awaitRegisteredTask(registered)
            }
        }
    }

    private fun registerTask(
        scope: CoroutineScope,
        request: SubagentTaskRequest,
        resolved: ResolvedRun,
    ): RegisteredTask {
        val task =
            scope.async(start = CoroutineStart.LAZY) {
                runResolvedTask(request, resolved)
            }
        registerResolvedTask(resolved.run, task)
        task.start()
        return RegisteredTask(resolved, task)
    }

    private suspend fun awaitRegisteredTask(registered: RegisteredTask): SubagentTaskResult =
        try {
            registered.task.await()
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                runRepository.updateStatus(
                    registered.resolved.run.id, SubagentRunStatus.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                )
            }
            if (!currentCoroutineContext().isActive) throw error
            throw SubagentExecutionException(
                registered.resolved.run.id,
                IllegalStateException("Subagent task ${registered.resolved.run.id} was cancelled"),
            )
        }

    suspend fun getRun(taskId: String): SubagentRunEntity? = runRepository.getById(taskId)

    suspend fun cancelTask(taskId: String): Boolean {
        val job = taskJobs[taskId] ?: return false
        job.cancel(CancellationException("Subagent task $taskId was cancelled"))
        return true
    }

    fun cancelTasksForParent(parentChatId: String): Int {
        val taskIds =
            taskParents.entries
                .asSequence()
                .filter { it.value == parentChatId }
                .map { it.key }
                .toList()
        taskIds.forEach { taskId ->
            taskJobs[taskId]?.cancel(
                CancellationException("Parent chat $parentChatId was cancelled")
            )
        }
        return taskIds.size
    }

    /**
     * Stops every active run affected by a parent/child chat deletion before the caller mutates
     * Room. Marking the chat first closes the small window where a newly resolved task could start
     * after the cancellation snapshot was taken.
     */
    suspend fun <T> withChatDeletionPrepared(
        chatId: String,
        delete: suspend () -> T,
    ): T = withChatDeletionsPrepared(listOf(chatId), delete)

    suspend fun <T> withChatDeletionsPrepared(
        chatIds: Collection<String>,
        delete: suspend () -> T,
    ): T =
        com.ai.assistance.operit.core.agent.collaboration.CollaborationCoordinator
            .getInstance(appContext).withChatDeletionsPrepared(chatIds) {
                withLegacyChatDeletionsPrepared(chatIds, delete)
            }

    private suspend fun <T> withLegacyChatDeletionsPrepared(
        chatIds: Collection<String>,
        delete: suspend () -> T,
    ): T {
        val normalizedChatIds =
            chatIds.mapNotNullTo(linkedSetOf()) { it.trim().takeIf(String::isNotEmpty) }
        if (normalizedChatIds.isEmpty()) return delete()

        val runsAndJobs =
            try {
                lifecycleGate.withLock {
                    val alreadyDeleting = normalizedChatIds.firstOrNull(deletingChatIds::contains)
                    check(alreadyDeleting == null) {
                        "Chat deletion is already in progress: $alreadyDeleting"
                    }
                    deletingChatIds.addAll(normalizedChatIds)
                    val runs =
                        normalizedChatIds
                            .flatMap { chatId ->
                                listOfNotNull(runRepository.getByChildChatId(chatId)) +
                                    runRepository.getByParentChatId(chatId)
                            }
                            .distinctBy(SubagentRunEntity::id)
                    val jobs =
                        synchronized(lifecycleLock) {
                            runs.mapNotNull { run ->
                                taskJobs[run.id]?.also { job ->
                                    job.cancel(
                                        CancellationException(
                                            "Chat ${run.childChatId} is being deleted"
                                        )
                                    )
                                }
                            }
                        }
                    runs to jobs
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    lifecycleGate.withLock {
                        deletingChatIds.removeAll(normalizedChatIds)
                    }
                }
                throw error
            }
        try {
            val (runs, jobs) = runsAndJobs
            withTimeout(DELETION_CANCELLATION_TIMEOUT_MS) {
                jobs.joinAll()
            }
            runs.forEach { run -> EnhancedAIService.releaseChatInstance(run.childChatId) }
            return delete()
        } finally {
            withContext(NonCancellable) {
                lifecycleGate.withLock {
                    deletingChatIds.removeAll(normalizedChatIds)
                }
            }
        }
    }

    private fun registerResolvedTask(
        run: SubagentRunEntity,
        taskJob: Job,
    ) {
        synchronized(lifecycleLock) {
            if (
                deletingChatIds.contains(run.parentChatId) ||
                    deletingChatIds.contains(run.childChatId)
            ) {
                throw SubagentChatDeletionInProgressException(
                    "Subagent task ${run.id} cannot start while its chat is being deleted"
                )
            }
            taskJobs[run.id] = taskJob
            taskParents[run.id] = run.parentChatId
        }
    }

    private suspend fun resolveRun(request: SubagentTaskRequest): ResolvedRun {
        val existingTaskId = request.taskId
        if (existingTaskId != null) {
            val run =
                requireNotNull(runRepository.getById(existingTaskId)) {
                    "Subagent task does not exist: $existingTaskId"
                }
            require(run.parentChatId == request.parentChatId) {
                "Subagent task $existingTaskId belongs to a different parent chat"
            }
            require(run.agentProfileId == request.subagentType) {
                "Subagent task $existingTaskId uses profile ${run.agentProfileId}"
            }
            val profile =
                run.agentConfigSnapshot
                    ?.let { snapshot ->
                        runCatching { profileJson.decodeFromString<AgentProfile>(snapshot) }
                            .getOrNull()
                    }
                    ?: error(
                        "Subagent task $existingTaskId has no usable Agent profile snapshot"
                    )
            return ResolvedRun(run = run, profile = profile)
        }

        val profile = request.profileOverride ?: profileRepository.requireSubagent(request.subagentType)
        val effectiveModelConfigId = profile.modelConfigId ?: request.parentModelConfigId
        val effectiveModelIndex =
            if (profile.modelConfigId != null) {
                profile.modelIndex ?: 0
            } else {
                request.parentModelIndex
            }
        val created =
            runRepository.createSubagentChatAndRun(
                CreateSubagentRunRequest(
                    parentChatId = request.parentChatId,
                    parentToolCallId = request.parentToolCallId,
                    agentProfileId = profile.id,
                    title = request.title,
                    agentConfigSnapshot = profileJson.encodeToString(profile),
                    modelConfigIdSnapshot = effectiveModelConfigId,
                    externalOwnerType = request.externalOwnerType,
                    externalOwnerId = request.externalOwnerId,
                    modelIndexSnapshot = effectiveModelIndex,
                )
            )
        // Register the same persisted child that will execute the turn.
        try {
            request.onRunCreated?.invoke(created.run)
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                runRepository.updateStatus(
                    created.run.id,
                    if (error is CancellationException) SubagentRunStatus.CANCELLED else SubagentRunStatus.FAILED,
                    completedAt = System.currentTimeMillis(),
                    error = error.message,
                )
            }
            throw error
        }
        return ResolvedRun(run = created.run, profile = profile)
    }

    private suspend fun runResolvedTask(
        request: SubagentTaskRequest,
        resolved: ResolvedRun,
    ): SubagentTaskResult {
        val run = resolved.run
        val taskId = run.id
        val childChatId = run.childChatId
        val semaphore =
            parentSemaphores.getOrPut(run.parentChatId) {
                Semaphore(MAX_CONCURRENT_SUBAGENTS_PER_PARENT)
            }
        var modelSemaphore = resolveModelSemaphore(run.modelConfigIdSnapshot)
        val usesParentQuota = request.collaborationSystemPrompt == null
        var parentAcquired = !usesParentQuota || semaphore.tryAcquire()
        var modelAcquired =
            if (parentAcquired) {
                modelSemaphore?.tryAcquire() ?: true
            } else {
                false
            }
        var activeSession: ChatTurnSession? = null
        val taskJob = requireNotNull(currentCoroutineContext()[Job])
        try {
            check(request.collaborationSystemPrompt == null || (parentAcquired && modelAcquired)) {
                "Model or parent concurrency limit reached; retry after an active agent finishes"
            }
            if (!parentAcquired || !modelAcquired) {
                check(runRepository.updateStatus(taskId, SubagentRunStatus.QUEUED)) {
                    "Subagent task $taskId could not enter the queue"
                }
                if (!parentAcquired) {
                    semaphore.acquire()
                    parentAcquired = true
                }
                if (!modelAcquired) {
                    modelSemaphore?.acquire()
                    modelAcquired = true
                }
            }

            withTimeout(CHILD_VISIBILITY_TIMEOUT_MS) {
                chatCore.chatHistories.first { histories ->
                    histories.any { it.id == childChatId }
                }
            }
            check(
                runRepository.updateStatus(
                taskId = taskId,
                status = SubagentRunStatus.RUNNING,
                startedAt = System.currentTimeMillis(),
                )
            ) {
                "Subagent task $taskId could not start"
            }
            suspend fun executeTurn(
                message: String,
                modelConfigId: String?,
                modelIndex: Int?,
            ): ChatTurnOutcome {
                val dispatch =
                    chatTurnDispatcher.dispatch(
                        core = chatCore,
                        request =
                            ChatTurnDispatchRequest(
                                chatId = childChatId,
                                message = message,
                                roleCardId = request.collaborationRoleCardId,
                                proxySenderName = null,
                                turnOptions =
                                    request.toChatTurnOptions(
                                        systemPrompt =
                                            request.collaborationSystemPrompt ?: SubagentPromptBuilder.buildSystemPrompt(
                                                resolved.profile
                                            ),
                                        assistantRoleName = resolved.profile.name,
                                    ),
                                chatModelConfigIdOverride = modelConfigId,
                                chatModelIndexOverride = modelIndex,
                                responseStreamAcquireTimeoutMs =
                                    RESPONSE_STREAM_ACQUIRE_TIMEOUT_MS,
                                responseTimeoutMs = null,
                            ),
                    )
                activeSession =
                    when (dispatch) {
                        is ChatTurnDispatchResult.Started -> dispatch.session
                        is ChatTurnDispatchResult.Failed -> error(dispatch.error)
                    }
                activeSessions[taskId] = requireNotNull(activeSession)
                request.onTurnStarted?.invoke()
                return requireNotNull(activeSession).awaitOutcome()
            }

            val initialTaskPrompt = SubagentPromptBuilder.buildTaskPrompt(request.prompt)
            val userMessageCountBeforeAttempt =
                chatCore.getChatHistoryDelegate().getUserMessageCount(childChatId)
            val outcome =
                try {
                    executeTurn(
                        message = initialTaskPrompt,
                        modelConfigId = run.modelConfigIdSnapshot,
                        modelIndex = run.modelIndexSnapshot,
                    )
                } catch (error: Exception) {
                    val parentModelConfigId = request.parentModelConfigId
                    val canFallback =
                        resolved.profile.modelConfigId != null &&
                            !parentModelConfigId.isNullOrBlank() &&
                            (
                                parentModelConfigId != run.modelConfigIdSnapshot ||
                                    request.parentModelIndex != run.modelIndexSnapshot
                            ) &&
                            SubagentModelFallbackPolicy.isExplicitModelAvailabilityFailure(
                                error.message.orEmpty()
                            )
                    if (!canFallback) {
                        throw error
                    }
                    if (parentModelConfigId != run.modelConfigIdSnapshot) {
                        if (modelAcquired) {
                            modelSemaphore?.release()
                            modelAcquired = false
                        }
                        modelSemaphore = resolveModelSemaphore(parentModelConfigId)
                        if (request.collaborationSystemPrompt != null) {
                            check(modelSemaphore?.tryAcquire() != false) {
                                "Fallback model concurrency limit reached; retry after an active agent finishes"
                            }
                        } else {
                            modelSemaphore?.acquire()
                        }
                        modelAcquired = true
                    }
                    executeTurn(
                        message =
                            SubagentModelFallbackPolicy.buildFallbackMessage(
                                originalTaskPrompt = initialTaskPrompt,
                                fallbackNote =
                                    appContext.getString(
                                        R.string.subagent_model_fallback_transcript_note
                                    ),
                                originalPromptPersisted =
                                    chatCore.getChatHistoryDelegate()
                                        .getUserMessageCount(childChatId) >
                                        userMessageCountBeforeAttempt,
                            ),
                        modelConfigId = parentModelConfigId,
                        modelIndex = request.parentModelIndex,
                    )
                }
            check(
                runRepository.updateStatus(
                taskId = taskId,
                status = SubagentRunStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
                )
            ) {
                "Subagent task $taskId did not accept its completed result"
            }
            return SubagentTaskResult.Completed(
                run = requireNotNull(runRepository.getById(taskId)),
                outcome = outcome,
            )
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                activeSession?.cancelAndAwaitTermination()
                runRepository.updateStatus(
                    taskId = taskId,
                    status = SubagentRunStatus.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                )
            }
            throw error
        } catch (error: SubagentChatDeletionInProgressException) {
            throw error
        } catch (error: Exception) {
            withContext(NonCancellable) {
                runRepository.updateStatus(
                    taskId = taskId,
                    status = SubagentRunStatus.FAILED,
                    completedAt = System.currentTimeMillis(),
                    error = error.message ?: error.javaClass.simpleName,
                )
            }
            throw SubagentExecutionException(taskId, error)
        } finally {
            activeSessions.remove(taskId)
            taskJobs.remove(taskId, taskJob)
            taskParents.remove(taskId, run.parentChatId)
            if (modelAcquired) {
                modelSemaphore?.release()
            }
            if (parentAcquired && usesParentQuota) {
                semaphore.release()
            }
            EnhancedAIService.releaseChatInstance(childChatId)
        }
    }

    private data class ResolvedRun(
        val run: SubagentRunEntity,
        val profile: AgentProfile,
    )

    private data class RegisteredTask(
        val resolved: ResolvedRun,
        val task: Deferred<SubagentTaskResult>,
    )

    private suspend fun resolveModelSemaphore(modelConfigId: String?): AdjustableConcurrencyGate? {
        if (modelConfigId.isNullOrBlank()) return null
        return modelSemaphoreUpdateMutexes
            .getOrPut(modelConfigId) { Mutex() }
            .withLock {
                val config = modelConfigManager.getModelConfigFlow(modelConfigId).first()
                val limit =
                    SubagentConcurrencyPolicy.modelLimit(
                        providerType = config.apiProviderType,
                        configuredMaxConcurrentRequests = config.maxConcurrentRequests,
                    )
                if (limit <= 0) return@withLock null
                val effectiveLimit = limit
                modelSemaphores
                    .computeIfAbsent(modelConfigId) {
                        AdjustableConcurrencyGate(effectiveLimit)
                    }
                    .also { it.updateLimit(effectiveLimit) }
            }
    }

    companion object {
        private const val MAX_CONCURRENT_SUBAGENTS_PER_PARENT = 10
        private const val RESPONSE_STREAM_ACQUIRE_TIMEOUT_MS = 15_000L
        private const val DELETION_CANCELLATION_TIMEOUT_MS = 20_000L
        private const val CHILD_VISIBILITY_TIMEOUT_MS = 5_000L
        private val ACTIVE_STATUS_NAMES =
            setOf(
                SubagentRunStatus.CREATED.name,
                SubagentRunStatus.QUEUED.name,
                SubagentRunStatus.RUNNING.name,
            )

        @Volatile
        private var INSTANCE: SubagentCoordinator? = null

        fun getInstance(context: Context): SubagentCoordinator =
            INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: SubagentCoordinator(context.applicationContext).also { INSTANCE = it }
                }
    }
}

internal object SubagentConcurrencyPolicy {
    fun modelLimit(
        providerType: ApiProviderType,
        configuredMaxConcurrentRequests: Int,
    ): Int =
        when (providerType) {
            ApiProviderType.MNN,
            ApiProviderType.LLAMA_CPP -> 1
            else -> configuredMaxConcurrentRequests.coerceAtLeast(0)
        }
}

internal class AdjustableConcurrencyGate(initialLimit: Int) {
    private val lock = Any()
    private val waiters = ArrayDeque<CompletableDeferred<Unit>>()
    private var limit = initialLimit
    private var inFlight = 0

    init {
        require(initialLimit > 0)
    }

    fun updateLimit(newLimit: Int) {
        require(newLimit > 0)
        synchronized(lock) {
            limit = newLimit
            promoteWaitersLocked()
        }
    }

    fun tryAcquire(): Boolean =
        synchronized(lock) {
            if (waiters.isNotEmpty() || inFlight >= limit) {
                false
            } else {
                inFlight += 1
                true
            }
        }

    suspend fun acquire() {
        val waiter =
            synchronized(lock) {
                if (waiters.isEmpty() && inFlight < limit) {
                    inFlight += 1
                    null
                } else {
                    CompletableDeferred<Unit>().also(waiters::addLast)
                }
            }
        if (waiter == null) return
        try {
            waiter.await()
        } catch (error: CancellationException) {
            synchronized(lock) {
                if (waiters.remove(waiter)) {
                    promoteWaitersLocked()
                } else if (waiter.isCompleted) {
                    inFlight -= 1
                    promoteWaitersLocked()
                }
            }
            throw error
        }
    }

    fun release() {
        synchronized(lock) {
            check(inFlight > 0) { "Concurrency gate released without an acquired slot" }
            inFlight -= 1
            promoteWaitersLocked()
        }
    }

    private fun promoteWaitersLocked() {
        while (inFlight < limit && waiters.isNotEmpty()) {
            val waiter = waiters.removeFirst()
            if (!waiter.isCancelled) {
                inFlight += 1
                waiter.complete(Unit)
            }
        }
    }
}

internal object SubagentModelFallbackPolicy {
    private val explicitAvailabilityMarkers =
        listOf(
            "insufficient_quota",
            "insufficient quota",
            "quota exhausted",
            "quota has been exhausted",
            "billing hard limit",
            "billing limit",
            "payment required",
            "credit balance",
            "credits exhausted",
            "account_deactivated",
            "account disabled",
            "account is disabled",
            "account is not active",
            "model_not_found",
            "model not found",
            "model is unavailable",
            "model unavailable",
            "does not exist or you do not have access to it",
            "余额不足",
            "额度已用尽",
            "额度耗尽",
            "账户不可用",
            "账号不可用",
            "模型不可用",
            "模型不存在",
        )

    fun isExplicitModelAvailabilityFailure(message: String): Boolean {
        val normalized = message.lowercase()
        return explicitAvailabilityMarkers.any(normalized::contains)
    }

    fun buildFallbackMessage(
        originalTaskPrompt: String,
        fallbackNote: String,
        originalPromptPersisted: Boolean,
    ): String =
        if (originalPromptPersisted) {
            fallbackNote
        } else {
            "$originalTaskPrompt\n\n$fallbackNote"
        }
}
