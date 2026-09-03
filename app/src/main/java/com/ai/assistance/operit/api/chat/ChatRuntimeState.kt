package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.InputProcessingErrorSource
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable runtime-state phases exposed to conversation-facing integrations. */
enum class ChatRuntimeStateActivity(val wireName: String) {
    IDLE("idle"),
    ACTIVE("active")
}

enum class ChatRuntimeStatePhase(val wireName: String) {
    IDLE("idle"),
    REQUESTING("requesting"),
    THINKING("thinking"),
    PROCESSING_TOOL_RESULT("processing_tool_result"),
    EXECUTING_PLAN("executing_plan"),
    CALLING_TOOL("calling_tool"),
    WAITING_TOOL_RESULT("waiting_tool_result"),
    WAITING_TOOL_CONFIRMATION("waiting_tool_confirmation"),
    GENERATING_RESPONSE("generating_response"),
    SUMMARIZING("summarizing"),
    RETRYING("retrying"),
    CANCELLED("cancelled"),
    ERROR("error");

    val isActive: Boolean
        get() = when (this) {
            IDLE,
            CANCELLED -> false
            else -> true
        }
}

enum class ChatRuntimeStateUserState(val wireName: String) {
    TYPING("typing"),
    WAITING_FOR_AI("waiting_for_ai")
}

enum class ChatRuntimeStateApplicationState(val wireName: String) {
    FOREGROUND("foreground"),
    BACKGROUND("background")
}

enum class ChatRuntimeStateErrorSource(val wireName: String) {
    AI("ai"),
    TOOL("tool"),
    API("api"),
    SYSTEM("system")
}

data class ChatRuntimeStateError(
    val source: ChatRuntimeStateErrorSource,
    val code: String,
    val message: String? = null,
    val recoverable: Boolean = false,
    val appCode: Int? = null,
    val providerCode: String? = null,
    val httpStatusCode: Int? = null
)

data class ChatRuntimeStateRetry(
    val attempt: Int? = null,
    val maxAttempts: Int? = null,
    val retryAfterMs: Long? = null
)

data class ChatRuntimeStateSnapshot(
    val chatId: String,
    val phase: ChatRuntimeStatePhase,
    val userState: ChatRuntimeStateUserState?,
    val applicationState: ChatRuntimeStateApplicationState,
    val toolName: String? = null,
    val error: ChatRuntimeStateError? = null,
    val retry: ChatRuntimeStateRetry? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatRuntimeStateGlobalSnapshot(
    val activity: ChatRuntimeStateActivity,
    val applicationState: ChatRuntimeStateApplicationState,
    val activeChatIds: List<String>,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatRuntimeStateEvent(
    val eventType: String,
    val session: ChatRuntimeStateSnapshot?,
    val global: ChatRuntimeStateGlobalSnapshot
)

/** Process-wide store for conversation runtime state. */
object ChatRuntimeStateStore {
    private const val TAG = "ChatRuntimeStateStore"
    private const val EVENT_STATE_SNAPSHOT = "state_snapshot"
    private const val EVENT_STATE_CHANGED = "state_changed"
    private const val EVENT_BUFFER_CAPACITY = 256
    private const val MAX_RECORD_COUNT = 512
    private const val INACTIVE_RECORD_TTL_MS = 24L * 60L * 60L * 1000L

    private data class ChatSourceRecord(
        var state: InputProcessingState = InputProcessingState.Idle,
        var updatedAt: Long = System.currentTimeMillis()
    )

    private data class ChatRecord(
        val sourceRecords: MutableMap<ChatRuntimeSlot, ChatSourceRecord> = linkedMapOf(),
        val draftByRuntime: MutableMap<ChatRuntimeSlot, Boolean> = linkedMapOf(),
        var phase: ChatRuntimeStatePhase = ChatRuntimeStatePhase.IDLE,
        var phaseBeforeConfirmation: ChatRuntimeStatePhase? = null,
        var userState: ChatRuntimeStateUserState? = null,
        var applicationState: ChatRuntimeStateApplicationState = ChatRuntimeStateApplicationState.BACKGROUND,
        var toolName: String? = null,
        var toolNameBeforeConfirmation: String? = null,
        var confirmationToolName: String? = null,
        var error: ChatRuntimeStateError? = null,
        var retry: ChatRuntimeStateRetry? = null,
        var updatedAt: Long = System.currentTimeMillis(),
        var lastActivityAt: Long = updatedAt
    )

    private data class Publication(
        val snapshots: Map<String, ChatRuntimeStateSnapshot>,
        val global: ChatRuntimeStateGlobalSnapshot,
        val events: List<ChatRuntimeStateEvent>
    )

    private val lock = Any()
    private val records = linkedMapOf<String, ChatRecord>()
    private val droppedEventCount = AtomicLong(0L)
    private var applicationState = ChatRuntimeStateApplicationState.BACKGROUND

    private val _snapshots = MutableStateFlow<Map<String, ChatRuntimeStateSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, ChatRuntimeStateSnapshot>> = _snapshots.asStateFlow()

    private val _globalSnapshot = MutableStateFlow(buildGlobalSnapshot(emptyMap()))
    val globalSnapshot: StateFlow<ChatRuntimeStateGlobalSnapshot> = _globalSnapshot.asStateFlow()

    private val _events = MutableSharedFlow<ChatRuntimeStateEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY
    )
    val events: SharedFlow<ChatRuntimeStateEvent> = _events.asSharedFlow()

    fun getSnapshot(chatId: String): ChatRuntimeStateSnapshot {
        val normalizedChatId = chatId.trim()
        synchronized(lock) {
            val record = records[normalizedChatId]
            return record?.let { buildSnapshot(normalizedChatId, it) }
                ?: idleSnapshot(normalizedChatId)
        }
    }

    fun updateInputProcessingState(runtime: ChatRuntimeSlot, chatId: String, state: InputProcessingState) {
        val normalizedChatId = chatId.trim().takeIf { it.isNotBlank() } ?: return
        val publication = synchronized(lock) {
            val now = System.currentTimeMillis()
            pruneRecordsLocked(now)
            val record = records.getOrPut(normalizedChatId) {
                ChatRecord(applicationState = applicationState)
            }
            record.sourceRecords[runtime] = ChatSourceRecord(state = state, updatedAt = now)
            val effectiveSource = selectSourceRecord(record)
            applyInputProcessingState(record, effectiveSource.state)
            record.userState = resolveUserState(record)
            record.updatedAt = now
            record.lastActivityAt = now
            pruneRecordsLocked(now)
            publicationForChatLocked(normalizedChatId)
        }
        publish(publication)
    }

    fun markCancelled(runtime: ChatRuntimeSlot, chatId: String) {
        val normalizedChatId = chatId.trim().takeIf { it.isNotBlank() } ?: return
        val publication = synchronized(lock) {
            val now = System.currentTimeMillis()
            pruneRecordsLocked(now)
            val record = records.getOrPut(normalizedChatId) {
                ChatRecord(applicationState = applicationState)
            }
            record.sourceRecords[runtime] = ChatSourceRecord(state = InputProcessingState.Idle, updatedAt = now)
            val anotherSourceIsActive = record.sourceRecords.any { (source, sourceRecord) ->
                source != runtime && phaseForInputState(sourceRecord.state).isActive
            }
            if (anotherSourceIsActive || record.phase == ChatRuntimeStatePhase.CANCELLED) {
                null
            } else {
                record.phase = ChatRuntimeStatePhase.CANCELLED
                record.phaseBeforeConfirmation = null
                record.userState = null
                record.toolName = null
                record.toolNameBeforeConfirmation = null
                record.confirmationToolName = null
                record.error = null
                record.retry = null
                record.updatedAt = now
                record.lastActivityAt = now
                pruneRecordsLocked(now)
                publicationForChatLocked(normalizedChatId)
            }
        }
        publication?.let(::publish)
    }

    fun removeChat(chatId: String) {
        val normalizedChatId = chatId.trim().takeIf { it.isNotBlank() } ?: return
        val publication = synchronized(lock) {
            if (records.remove(normalizedChatId) == null) null else publicationForChatLocked(normalizedChatId)
        }
        publication?.let(::publish)
    }

    fun updateUserDraft(runtime: ChatRuntimeSlot, chatId: String, hasDraft: Boolean) {
        val normalizedChatId = chatId.trim().takeIf { it.isNotBlank() } ?: return
        val publication = synchronized(lock) {
            val now = System.currentTimeMillis()
            pruneRecordsLocked(now)
            val record = records.getOrPut(normalizedChatId) { ChatRecord(applicationState = applicationState) }
            record.draftByRuntime[runtime] = hasDraft
            record.userState = resolveUserState(record)
            record.updatedAt = now
            record.lastActivityAt = now
            pruneRecordsLocked(now)
            publicationForChatLocked(normalizedChatId)
        }
        publish(publication)
    }

    fun updateToolConfirmation(chatId: String, toolName: String?) {
        val normalizedChatId = chatId.trim().takeIf { it.isNotBlank() } ?: return
        val normalizedToolName = toolName?.trim()?.takeIf { it.isNotBlank() }
        val publication = synchronized(lock) {
            val now = System.currentTimeMillis()
            pruneRecordsLocked(now)
            val record = records.getOrPut(normalizedChatId) { ChatRecord(applicationState = applicationState) }
            if (normalizedToolName != null) {
                if (record.phase != ChatRuntimeStatePhase.WAITING_TOOL_CONFIRMATION) {
                    record.phaseBeforeConfirmation = record.phase
                    record.toolNameBeforeConfirmation = record.toolName
                }
                record.phase = ChatRuntimeStatePhase.WAITING_TOOL_CONFIRMATION
                record.confirmationToolName = normalizedToolName
                record.toolName = normalizedToolName
            } else {
                restoreAfterConfirmation(record)
            }
            record.userState = resolveUserState(record)
            record.updatedAt = now
            record.lastActivityAt = now
            pruneRecordsLocked(now)
            publicationForChatLocked(normalizedChatId)
        }
        publish(publication)
    }

    fun clearToolConfirmations() {
        val publication = synchronized(lock) {
            val changedIds = records.filterValues { it.confirmationToolName != null }.keys.toList()
            if (changedIds.isEmpty()) {
                null
            } else {
                val now = System.currentTimeMillis()
                changedIds.forEach { chatId ->
                    records[chatId]?.let { record ->
                        restoreAfterConfirmation(record)
                        record.userState = resolveUserState(record)
                        record.updatedAt = now
                        record.lastActivityAt = now
                    }
                }
                pruneRecordsLocked(now)
                publicationForAllLocked(changedIds)
            }
        }
        publication?.let(::publish)
    }

    fun updateApplicationState(state: ChatRuntimeStateApplicationState) {
        val publication = synchronized(lock) {
            if (applicationState == state && records.values.all { it.applicationState == state }) {
                null
            } else {
                applicationState = state
                val now = System.currentTimeMillis()
                pruneRecordsLocked(now)
                records.values.forEach { record ->
                    record.applicationState = state
                    record.updatedAt = now
                }
                publicationForAllLocked(records.keys.toList())
            }
        }
        publication?.let(::publish)
    }

    fun activeSnapshots(): List<ChatRuntimeStateSnapshot> = snapshots.value.values
        .filter { it.phase.isActive || it.userState != null }
        .sortedBy { it.chatId }

    fun replayEvents(): List<ChatRuntimeStateEvent> {
        synchronized(lock) {
            val currentSnapshots = buildSnapshotMapLocked()
            val global = buildGlobalSnapshot(currentSnapshots)
            return buildList {
                add(ChatRuntimeStateEvent(EVENT_STATE_SNAPSHOT, session = null, global = global))
                currentSnapshots.values
                    .filter { it.phase.isActive || it.userState != null || it.phase == ChatRuntimeStatePhase.CANCELLED }
                    .sortedBy { it.chatId }
                    .forEach { snapshot ->
                        add(ChatRuntimeStateEvent(EVENT_STATE_SNAPSHOT, session = snapshot, global = global))
                    }
            }
        }
    }

    internal fun resetForTest() {
        val publication = synchronized(lock) {
            records.clear()
            droppedEventCount.set(0L)
            applicationState = ChatRuntimeStateApplicationState.BACKGROUND
            publicationForAllLocked(emptyList())
        }
        publish(publication)
    }

    internal fun recordCountForTest(): Int = synchronized(lock) { records.size }

    internal fun maxRecordCountForTest(): Int = MAX_RECORD_COUNT

    internal fun droppedEventCountForTest(): Long = droppedEventCount.get()

    internal fun eventBufferCapacityForTest(): Int = EVENT_BUFFER_CAPACITY

    internal fun pruneInactiveRecordsForTest(now: Long) {
        val publication = synchronized(lock) {
            val previousIds = records.keys.toSet()
            pruneRecordsLocked(now)
            val removedIds = previousIds - records.keys
            if (removedIds.isEmpty()) null else publicationForAllLocked(removedIds.toList())
        }
        publication?.let(::publish)
    }

    private fun idleSnapshot(chatId: String) = ChatRuntimeStateSnapshot(
        chatId = chatId,
        phase = ChatRuntimeStatePhase.IDLE,
        userState = null,
        applicationState = applicationState
    )

    private fun selectSourceRecord(record: ChatRecord): ChatSourceRecord = record.sourceRecords.values
        .filter { phaseForInputState(it.state).isActive }
        .maxByOrNull { it.updatedAt }
        ?: record.sourceRecords.values.maxByOrNull { it.updatedAt }
        ?: ChatSourceRecord()

    private fun phaseForInputState(state: InputProcessingState): ChatRuntimeStatePhase = when (state) {
        is InputProcessingState.Idle, is InputProcessingState.Completed -> ChatRuntimeStatePhase.IDLE
        is InputProcessingState.Processing -> ChatRuntimeStatePhase.THINKING
        is InputProcessingState.ProcessingToolResult -> ChatRuntimeStatePhase.PROCESSING_TOOL_RESULT
        is InputProcessingState.ExecutingPlan -> ChatRuntimeStatePhase.EXECUTING_PLAN
        is InputProcessingState.Connecting -> ChatRuntimeStatePhase.REQUESTING
        is InputProcessingState.Summarizing -> ChatRuntimeStatePhase.SUMMARIZING
        is InputProcessingState.Receiving -> ChatRuntimeStatePhase.GENERATING_RESPONSE
        is InputProcessingState.ExecutingTool -> ChatRuntimeStatePhase.CALLING_TOOL
        is InputProcessingState.ToolProgress, is InputProcessingState.WaitingToolResult -> ChatRuntimeStatePhase.WAITING_TOOL_RESULT
        is InputProcessingState.Retrying -> ChatRuntimeStatePhase.RETRYING
        is InputProcessingState.AiError, is InputProcessingState.ToolError, is InputProcessingState.Error -> ChatRuntimeStatePhase.ERROR
    }

    private fun applyInputProcessingState(record: ChatRecord, state: InputProcessingState) {
        record.phaseBeforeConfirmation = null
        record.toolNameBeforeConfirmation = null
        record.confirmationToolName = null
        if (
            state !is InputProcessingState.Retrying &&
            state !is InputProcessingState.AiError &&
            state !is InputProcessingState.ToolError &&
            state !is InputProcessingState.Error
        ) {
            record.retry = null
        }
        when (state) {
            is InputProcessingState.Idle, is InputProcessingState.Completed -> {
                record.phase = ChatRuntimeStatePhase.IDLE
                record.toolName = null
                record.error = null
            }
            is InputProcessingState.Processing -> {
                record.phase = ChatRuntimeStatePhase.THINKING
                record.toolName = null
                record.error = null
            }
            is InputProcessingState.ProcessingToolResult -> {
                record.phase = ChatRuntimeStatePhase.PROCESSING_TOOL_RESULT
                record.toolName = state.toolName
                record.error = null
            }
            is InputProcessingState.ExecutingPlan -> {
                record.phase = ChatRuntimeStatePhase.EXECUTING_PLAN
                record.toolName = null
                record.error = null
            }
            is InputProcessingState.Connecting -> {
                record.phase = ChatRuntimeStatePhase.REQUESTING
                record.toolName = null
                record.error = null
            }
            is InputProcessingState.Summarizing -> {
                record.phase = ChatRuntimeStatePhase.SUMMARIZING
                record.toolName = null
                record.error = null
            }
            is InputProcessingState.Receiving -> {
                record.phase = ChatRuntimeStatePhase.GENERATING_RESPONSE
                record.toolName = null
                record.error = null
            }
            is InputProcessingState.ExecutingTool -> {
                record.phase = ChatRuntimeStatePhase.CALLING_TOOL
                record.toolName = state.toolName
                record.error = null
            }
            is InputProcessingState.ToolProgress, is InputProcessingState.WaitingToolResult -> {
                record.phase = ChatRuntimeStatePhase.WAITING_TOOL_RESULT
                record.toolName = when (state) {
                    is InputProcessingState.ToolProgress -> state.toolName
                    is InputProcessingState.WaitingToolResult -> state.toolName
                    else -> null
                }
                record.error = null
            }
            is InputProcessingState.Retrying -> {
                record.phase = ChatRuntimeStatePhase.RETRYING
                record.toolName = null
                val existingError = record.error
                val existingRetry = record.retry
                record.retry = ChatRuntimeStateRetry(
                    attempt = state.retryAttempt ?: existingRetry?.attempt,
                    maxAttempts = state.maxRetryAttempts ?: existingRetry?.maxAttempts,
                    retryAfterMs = state.retryAfterMs ?: existingRetry?.retryAfterMs
                )
                if (state.errorCode != null || existingError == null) {
                    record.error = ChatRuntimeStateError(
                        source = when (state.errorSource) {
                            InputProcessingErrorSource.AI -> ChatRuntimeStateErrorSource.AI
                            InputProcessingErrorSource.TOOL -> ChatRuntimeStateErrorSource.TOOL
                            InputProcessingErrorSource.API -> ChatRuntimeStateErrorSource.API
                            InputProcessingErrorSource.SYSTEM -> ChatRuntimeStateErrorSource.SYSTEM
                        },
                        code = state.errorCode ?: existingError?.code ?: "retrying",
                        message = state.message.takeIf { it.isNotBlank() } ?: existingError?.message,
                        recoverable = state.recoverable,
                        appCode = existingError?.appCode,
                        providerCode = state.providerCode ?: existingError?.providerCode,
                        httpStatusCode = state.httpStatusCode ?: existingError?.httpStatusCode
                    )
                }
            }
            is InputProcessingState.AiError -> setError(record, ChatRuntimeStateErrorSource.AI, state.code, state.message, state.recoverable, state.retryAttempt)
            is InputProcessingState.ToolError -> {
                record.toolName = state.toolName
                setError(record, ChatRuntimeStateErrorSource.TOOL, state.code, state.message, state.recoverable, state.retryAttempt)
            }
            is InputProcessingState.Error -> {
                record.phase = ChatRuntimeStatePhase.ERROR
                record.toolName = null
                record.error = ChatRuntimeStateError(
                    source = when (state.errorSource) {
                        InputProcessingErrorSource.AI -> ChatRuntimeStateErrorSource.AI
                        InputProcessingErrorSource.TOOL -> ChatRuntimeStateErrorSource.TOOL
                        InputProcessingErrorSource.API -> ChatRuntimeStateErrorSource.API
                        InputProcessingErrorSource.SYSTEM -> ChatRuntimeStateErrorSource.SYSTEM
                    },
                    code = state.code,
                    message = state.message,
                    recoverable = state.recoverable,
                    appCode = state.appCode,
                    providerCode = state.providerCode,
                    httpStatusCode = state.httpStatusCode
                )
                if (
                    state.retryAttempt != null ||
                    state.maxRetryAttempts != null ||
                    state.retryAfterMs != null
                ) {
                    record.retry = ChatRuntimeStateRetry(
                        attempt = state.retryAttempt ?: record.retry?.attempt,
                        maxAttempts = state.maxRetryAttempts ?: record.retry?.maxAttempts,
                        retryAfterMs = state.retryAfterMs ?: record.retry?.retryAfterMs
                    )
                }
            }
        }
    }

    private fun setError(
        record: ChatRecord,
        source: ChatRuntimeStateErrorSource,
        code: String,
        message: String?,
        recoverable: Boolean,
        retryAttempt: Int?
    ) {
        record.phase = ChatRuntimeStatePhase.ERROR
        record.error = ChatRuntimeStateError(
            source = source,
            code = code,
            message = message,
            recoverable = recoverable
        )
        if (retryAttempt != null) {
            record.retry = ChatRuntimeStateRetry(
                attempt = retryAttempt,
                maxAttempts = record.retry?.maxAttempts,
                retryAfterMs = record.retry?.retryAfterMs
            )
        }
    }

    private fun restoreAfterConfirmation(record: ChatRecord) {
        record.confirmationToolName = null
        if (record.phase == ChatRuntimeStatePhase.WAITING_TOOL_CONFIRMATION) {
            record.phase = record.phaseBeforeConfirmation ?: ChatRuntimeStatePhase.IDLE
            record.toolName = record.toolNameBeforeConfirmation
        }
        record.phaseBeforeConfirmation = null
        record.toolNameBeforeConfirmation = null
    }

    private fun resolveUserState(record: ChatRecord): ChatRuntimeStateUserState? = when {
        record.draftByRuntime.values.any { it } -> ChatRuntimeStateUserState.TYPING
        record.phase.isActive -> ChatRuntimeStateUserState.WAITING_FOR_AI
        else -> null
    }

    private fun buildSnapshot(chatId: String, record: ChatRecord) = ChatRuntimeStateSnapshot(
        chatId = chatId,
        phase = record.phase,
        userState = record.userState,
        applicationState = record.applicationState,
        toolName = record.toolName,
        error = record.error?.let { error ->
            error.copy(message = ChatRuntimeStateErrorSanitizer.sanitize(error.message))
        },
        retry = record.retry,
        updatedAt = record.updatedAt
    )

    private fun buildSnapshotMapLocked(): Map<String, ChatRuntimeStateSnapshot> =
        records.mapValues { (chatId, record) -> buildSnapshot(chatId, record) }

    private fun buildGlobalSnapshot(currentSnapshots: Map<String, ChatRuntimeStateSnapshot>): ChatRuntimeStateGlobalSnapshot {
        val activeChatIds = currentSnapshots.values
            .filter { it.phase.isActive || it.userState != null }
            .map { it.chatId }
            .distinct()
            .sorted()
        return ChatRuntimeStateGlobalSnapshot(
            activity = if (activeChatIds.isEmpty()) ChatRuntimeStateActivity.IDLE else ChatRuntimeStateActivity.ACTIVE,
            applicationState = applicationState,
            activeChatIds = activeChatIds
        )
    }

    private fun publicationForChatLocked(chatId: String): Publication {
        val currentSnapshots = buildSnapshotMapLocked()
        val global = buildGlobalSnapshot(currentSnapshots)
        return Publication(
            snapshots = currentSnapshots,
            global = global,
            events = listOf(ChatRuntimeStateEvent(EVENT_STATE_CHANGED, currentSnapshots[chatId], global))
        )
    }

    private fun publicationForAllLocked(changedIds: List<String>): Publication {
        val currentSnapshots = buildSnapshotMapLocked()
        val global = buildGlobalSnapshot(currentSnapshots)
        val events = if (changedIds.isEmpty()) {
            listOf(ChatRuntimeStateEvent(EVENT_STATE_CHANGED, session = null, global = global))
        } else {
            changedIds.map { chatId -> ChatRuntimeStateEvent(EVENT_STATE_CHANGED, currentSnapshots[chatId], global) }
        }
        return Publication(currentSnapshots, global, events)
    }

    private fun publish(publication: Publication) {
        _snapshots.value = publication.snapshots
        _globalSnapshot.value = publication.global
        publication.events.forEach { event ->
            if (!_events.tryEmit(event)) {
                val dropped = droppedEventCount.incrementAndGet()
                AppLogger.w(
                    TAG,
                    "Runtime-state event buffer is full; consumers can resync from snapshots. " +
                        "dropped=$dropped, chatId=${event.session?.chatId}"
                )
            }
        }
    }

    private fun pruneRecordsLocked(now: Long) {
        val staleIds = records.filter { (_, record) ->
            !isRecordActive(record) && now - record.lastActivityAt >= INACTIVE_RECORD_TTL_MS
        }.keys.toList()
        staleIds.forEach(records::remove)

        if (records.size <= MAX_RECORD_COUNT) return
        records.entries
            .filterNot { isRecordActive(it.value) }
            .sortedBy { it.value.lastActivityAt }
            .take(records.size - MAX_RECORD_COUNT)
            .forEach { records.remove(it.key) }
    }

    private fun isRecordActive(record: ChatRecord): Boolean =
        record.phase.isActive || record.userState != null || record.confirmationToolName != null
}
