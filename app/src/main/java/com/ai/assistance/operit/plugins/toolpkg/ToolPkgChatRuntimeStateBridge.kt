package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.api.chat.ChatRuntimeStateEvent
import com.ai.assistance.operit.api.chat.ChatRuntimeStateSnapshot
import com.ai.assistance.operit.api.chat.ChatRuntimeStateStore
import com.ai.assistance.operit.core.tools.javascript.extractJsExecutionErrorMessage
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_CHAT_RUNTIME_STATE
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ToolPkgChatRuntimeStateBridge"
private const val DISPATCH_BUFFER_CAPACITY = 256
private const val HOOK_TIMEOUT_MS = 5_000L

private data class ChatRuntimeStateDispatch(
    val event: ChatRuntimeStateEvent,
    val scope: String,
    val targetHookKeys: Set<String>? = null,
    val recoveryAttempt: Boolean = false
)

internal class BoundedRuntimeStateQueue<T>(
    capacity: Int,
    private val onDrop: (Long) -> Unit = {}
) {
    private val channel = Channel<T>(capacity)
    private val droppedCount = AtomicLong(0L)
    private val resyncRequested = AtomicBoolean(false)

    init {
        require(capacity > 0) { "Runtime-state queue capacity must be positive" }
    }

    fun enqueue(value: T): Boolean {
        if (channel.trySend(value).isSuccess) return true
        val dropped = droppedCount.incrementAndGet()
        resyncRequested.set(true)
        onDrop(dropped)
        return false
    }

    suspend fun consume(block: suspend (T) -> Unit) {
        for (value in channel) block(value)
    }

    fun takeResyncRequest(): Boolean = resyncRequested.compareAndSet(true, false)

    fun droppedCount(): Long = droppedCount.get()
}

internal data class RuntimeStateHookDeliveryResult(
    val hook: ToolPkgChatRuntimeStateHookRegistration,
    val failure: Throwable?
)

internal suspend fun deliverRuntimeStateHooksConcurrently(
    hooks: List<ToolPkgChatRuntimeStateHookRegistration>,
    timeoutMillis: Long,
    runner: suspend (ToolPkgChatRuntimeStateHookRegistration) -> Result<Any?>
): List<RuntimeStateHookDeliveryResult> = supervisorScope {
    require(timeoutMillis > 0L) { "Hook timeout must be positive" }
    hooks.map { hook ->
        async {
            val result = withTimeoutOrNull(timeoutMillis) { runner(hook) }
            RuntimeStateHookDeliveryResult(
                hook = hook,
                failure = result?.exceptionOrNull()
                    ?: if (result == null) {
                        TimeoutException("Runtime-state hook timed out after ${timeoutMillis}ms")
                    } else {
                        null
                    }
            )
        }
    }.awaitAll()
}

internal object ToolPkgChatRuntimeStateBridge {
    private val installed = AtomicBoolean(false)
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deliveryMutex = Mutex()
    private val dispatchQueue = BoundedRuntimeStateQueue<ChatRuntimeStateDispatch>(
        capacity = DISPATCH_BUFFER_CAPACITY,
        onDrop = { dropped ->
            AppLogger.w(
                TAG,
                "Chat runtime state dispatch queue is full; scheduling snapshot resync. dropped=$dropped"
            )
        }
    )
    private val failedHookCounts = ConcurrentHashMap<String, AtomicLong>()
    private val hooksNeedingSnapshot = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var hooks: List<ToolPkgChatRuntimeStateHookRegistration> = emptyList()

    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            replaceHooks(
                activeContainers.flatMap { runtime ->
                    runtime.chatRuntimeStateHooks.map { hook ->
                        ToolPkgChatRuntimeStateHookRegistration(
                            containerPackageName = runtime.packageName,
                            hookId = hook.id,
                            functionName = hook.function,
                            functionSource = hook.functionSource
                        )
                    }
                }.sortedWith(
                    compareBy(
                        ToolPkgChatRuntimeStateHookRegistration::containerPackageName,
                        ToolPkgChatRuntimeStateHookRegistration::hookId
                    )
                )
            )
        }

    init {
        dispatchScope.launch {
            ChatRuntimeStateStore.events.collect { event ->
                enqueue(event, scope = "session")
                enqueue(event, scope = "global")
            }
        }
        dispatchScope.launch {
            dispatchQueue.consume { dispatch ->
                deliveryMutex.withLock {
                    deliver(dispatch)
                    if (dispatchQueue.takeResyncRequest()) {
                        deliverReplayEvents(targetHookKeys = null)
                    }
                }
            }
        }
    }

    fun register() {
        if (!installed.compareAndSet(false, true)) return
        // Listener registration performs an initial runtime sync; replaceHooks replays only to new hooks.
        toolPkgPackageManager().addToolPkgRuntimeChangeListener(runtimeChangeListener)
    }

    fun replaceHooks(updatedHooks: List<ToolPkgChatRuntimeStateHookRegistration>) {
        val previousHooks = hooks
        val previousByKey = previousHooks.associateBy { hook -> hookKey(hook) }
        val updatedKeys = updatedHooks.mapTo(mutableSetOf()) { hook -> hookKey(hook) }
        val replayKeys = updatedHooks
            .filter { hook -> previousByKey[hookKey(hook)] != hook }
            .mapTo(mutableSetOf()) { hook -> hookKey(hook) }
        val removedHooks = previousHooks.filter { hookKey(it) !in updatedKeys }
        val changedHooks = previousHooks.filter { hookKey(it) in replayKeys }
        hooks = updatedHooks
        hooksNeedingSnapshot
            .filter { it !in updatedKeys }
            .forEach(hooksNeedingSnapshot::remove)
        hooksNeedingSnapshot.addAll(replayKeys)
        failedHookCounts.keys
            .filter { it !in updatedKeys }
            .forEach(failedHookCounts::remove)

        if (!installed.get()) return
        dispatchScope.launch {
            deliveryMutex.withLock {
                val manager = toolPkgPackageManager()
                (removedHooks + changedHooks)
                    .distinctBy { hook -> hookExecutionContextKey(hook) }
                    .forEach { hook ->
                    manager.releaseToolPkgExecutionEngine(hookExecutionContextKey(hook))
                }
                if (replayKeys.isNotEmpty()) {
                    enqueueReplayEvents(targetHookKeys = replayKeys)
                }
            }
        }
    }

    private fun enqueueReplayEvents(targetHookKeys: Set<String>?) {
        ChatRuntimeStateStore.replayEvents().forEach { event ->
            enqueue(
                event = event,
                scope = if (event.session == null) "global" else "session",
                targetHookKeys = targetHookKeys
            )
        }
    }

    private suspend fun deliverReplayEvents(targetHookKeys: Set<String>?) {
        ChatRuntimeStateStore.replayEvents().forEach { event ->
            deliver(
                ChatRuntimeStateDispatch(
                    event = event,
                    scope = if (event.session == null) "global" else "session",
                    targetHookKeys = targetHookKeys
                )
            )
        }
    }

    private fun enqueue(
        event: ChatRuntimeStateEvent,
        scope: String,
        targetHookKeys: Set<String>? = null
    ) {
        if (scope == "session" && event.session == null) return
        dispatchQueue.enqueue(
            ChatRuntimeStateDispatch(
                event = event,
                scope = scope,
                targetHookKeys = targetHookKeys
            )
        )
    }

    private suspend fun deliver(dispatch: ChatRuntimeStateDispatch) {
        val selectedHooks = hooks.filter { hook ->
            dispatch.targetHookKeys?.contains(hookKey(hook)) != false
        }
        if (selectedHooks.isEmpty()) return

        val pendingSnapshotKeys = hooksNeedingSnapshot.toSet()
        val snapshotDispatch = if (selectedHooks.any { hookKey(it) in pendingSnapshotKeys }) {
            latestSnapshotDispatch(dispatch)
        } else {
            null
        }
        val manager = toolPkgPackageManager()
        val results = deliverRuntimeStateHooksConcurrently(
            hooks = selectedHooks,
            timeoutMillis = HOOK_TIMEOUT_MS + 1_000L
        ) { hook ->
            val key = hookKey(hook)
            val effectiveDispatch = if (key in pendingSnapshotKeys) {
                snapshotDispatch ?: dispatch.copy(
                    event = dispatch.event.copy(eventType = "state_snapshot")
                )
            } else {
                dispatch
            }
            withContext(Dispatchers.IO) {
                manager.runToolPkgMainHook(
                    containerPackageName = hook.containerPackageName,
                    functionName = hook.functionName,
                    event = TOOLPKG_EVENT_CHAT_RUNTIME_STATE,
                    eventName = effectiveDispatch.event.eventType,
                    pluginId = hook.hookId,
                    inlineFunctionSource = hook.functionSource,
                    eventPayload = buildEventPayload(effectiveDispatch),
                    executionContextKey = hookExecutionContextKey(hook),
                    runtimeKind = "hook",
                    dispatchIntermediateOnMain = false,
                    timeoutMillis = HOOK_TIMEOUT_MS
                ).mapCatching { value ->
                    extractJsExecutionErrorMessage(value)?.let { message ->
                        throw IllegalStateException(message)
                    }
                    value
                }
            }
        }

        val failedKeys = mutableSetOf<String>()
        results.forEach { result ->
            val key = hookKey(result.hook)
            val error = result.failure
            if (error == null) {
                hooksNeedingSnapshot.remove(key)
            } else {
                hooksNeedingSnapshot.add(key)
                failedKeys += key
                val failureCount = failedHookCounts
                    .getOrPut(key) { AtomicLong(0L) }
                    .incrementAndGet()
                AppLogger.e(
                    TAG,
                    "ToolPkg chat runtime state hook failed: $key, failures=$failureCount; " +
                        "the next delivery will be a state_snapshot",
                    error
                )
            }
        }
        if (failedKeys.isNotEmpty() && !dispatch.recoveryAttempt) {
            val recoveryDispatch = latestSnapshotDispatch(dispatch)
                ?: dispatch.copy(event = dispatch.event.copy(eventType = "state_snapshot"))
            dispatchQueue.enqueue(
                recoveryDispatch.copy(
                    targetHookKeys = failedKeys,
                    recoveryAttempt = true
                )
            )
        }
    }

    private fun latestSnapshotDispatch(dispatch: ChatRuntimeStateDispatch): ChatRuntimeStateDispatch? {
        val replayEvents = ChatRuntimeStateStore.replayEvents()
        val snapshotEvent = if (dispatch.scope == "global") {
            replayEvents.firstOrNull { it.session == null }
        } else {
            val chatId = dispatch.event.session?.chatId
            replayEvents.firstOrNull { it.session?.chatId == chatId }
        } ?: return null
        return dispatch.copy(event = snapshotEvent)
    }

    private fun buildEventPayload(dispatch: ChatRuntimeStateDispatch): Map<String, Any?> {
        val event = dispatch.event
        return buildMap {
            put("scope", dispatch.scope)
            put("event", event.eventType)
            put("applicationState", event.global.applicationState.wireName)
            put("activeChatIds", event.global.activeChatIds)
            put("globalActivity", event.global.activity.wireName)
            put(
                "updatedAt",
                if (dispatch.scope == "global") {
                    event.global.updatedAt
                } else {
                    requireNotNull(event.session).updatedAt
                }
            )
            if (dispatch.scope == "session") {
                event.session?.let { session -> putAll(buildSessionPayload(session)) }
            }
        }
    }

    internal fun buildSessionPayload(session: ChatRuntimeStateSnapshot): Map<String, Any?> {
        return buildMap {
            put("chatId", session.chatId)
            put("aiBehavior", session.phase.wireName)
            put("userState", session.userState?.wireName)
            put("applicationState", session.applicationState.wireName)
            put("toolName", session.toolName)
            put("updatedAt", session.updatedAt)
            session.error?.let { error ->
                put(
                    "error",
                    buildMap<String, Any?> {
                        put("source", error.source.wireName)
                        put("code", error.code)
                        put("message", error.message)
                        put("recoverable", error.recoverable)
                        put("appCode", error.appCode)
                        put("providerCode", error.providerCode)
                        put("httpStatusCode", error.httpStatusCode)
                    }
                )
            }
            session.retry?.let { retry ->
                put(
                    "retry",
                    buildMap<String, Any?> {
                        put("attempt", retry.attempt)
                        put("maxAttempts", retry.maxAttempts)
                        put("retryAfterMs", retry.retryAfterMs)
                    }
                )
            }
        }
    }

    private fun hookKey(hook: ToolPkgChatRuntimeStateHookRegistration): String =
        "${hook.containerPackageName}:${hook.hookId}"

    private fun hookExecutionContextKey(hook: ToolPkgChatRuntimeStateHookRegistration): String =
        "chat_runtime_state:${hook.containerPackageName}:${hook.hookId}"

    internal fun droppedDispatchCountForTest(): Long = dispatchQueue.droppedCount()

    internal fun hookFailureCountForTest(hook: ToolPkgChatRuntimeStateHookRegistration): Long =
        failedHookCounts[hookKey(hook)]?.get() ?: 0L
}