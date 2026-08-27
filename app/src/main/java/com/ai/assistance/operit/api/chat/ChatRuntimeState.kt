package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.InputProcessingErrorSource
import com.ai.assistance.operit.data.model.InputProcessingState
import kotlinx.coroutines.channels.BufferOverflow
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
    val retryAttempt: Int? = null,
    val providerCode: String? = null,
    val httpStatusCode: Int? = null,
    val retryAfterMs: Long? = null
)

data class ChatRuntimeStateSnapshot(
    val chatId: String,
    val phase: ChatRuntimeStatePhase,
    val userState: ChatRuntimeStateUserState?,
    val applicationState: ChatRuntimeStateApplicationState,
    val toolName: String? = null,
    val error: ChatRuntimeStateError? = null,
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

/**
 * Process-wide store for conversation runtime state.
 *
 * MAIN and FLOATING are implementation slots only. The public snapshot is keyed by chatId and
 * receives updates from both slots so consumers do not need to know which surface is hosting a
 * conversation.
 */
object ChatRuntimeStateStore {
    private const val EVENT_STATE_SNAPSHOT = "state_snapshot"
    private const val EVENT_STATE_CHANGED = "state_changed"

    private data class ChatSourceRecord(
        var state: InputProcessingState = InputProcessingState.Idle,
        var updatedAt: Long = System.currentTimeMillis()
    )

    private data class ChatRecord(
        val sourceRecords: MutableMap<ChatRuntimeSlot, ChatSourceRecord> = linkedMapOf(),
        val draftByRuntime: MutableMap<ChatRuntimeSlot, Boolean> = linkedMapOf(),
        var phase: ChatRuntimeStatePhase = ChatRuntimeStatePhase.IDLE,
        var userState: ChatRuntimeStateUserState? = null,
        var applicationState: ChatRuntimeStateApplicationState =
            ChatRuntimeStateApplicationState.BACKGROUND,
        var toolName: String? = null,
        var confirmationToolName: String? = null,
        var error: ChatRuntimeStateError? = null,
        var updatedAt: Long = System.currentTimeMillis()
    )

    private val lock = Any()
    private val records = linkedMapOf<String, ChatRecord>()
    private var applicationState = ChatRuntimeStateApplicationState.BACKGROUND

    private val _snapshots = MutableStateFlow<Map<String, ChatRuntimeStateSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, ChatRuntimeStateSnapshot>> = _snapshots.asStateFlow()

    private val _globalSnapshot = MutableStateFlow(buildGlobalSnapshot(emptyMap()))
    val globalSnapshot: StateFlow<ChatRuntimeStateGlobalSnapshot> = _globalSnapshot.asStateFlow()

    private val _events = MutableSharedFlow<ChatRuntimeStateEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ChatRuntimeStateEvent> = _events.asSharedFlow()

    fun getSnapshot(chatId: String): ChatRuntimeStateSnapshot {
        val normalizedChatId = chatId.trim()
        synchronized(lock) {
            val record = records[normalizedChatId]
            return if (record == null) {
                ChatRuntimeStateSnapshot(
                    chatId = normalizedChatId,
                    phase = ChatRuntimeStatePhase.IDLE,
                    userState = null,
                    applicationState = applicationState
                )
            } else {
                buildSnapshot(normalizedChatId, record)
            }
        }
    }

    fun updateInputProcessingState(
        runtime: ChatRuntimeSlot,
        chatId: String,
        state: InputProcessingState
    ) {
        val normalizedChatId = chatId.trim()
        if (normalizedChatId.isBlank()) {
            return
        }

        synchronized(lock) {
            val record = records.getOrPut(normalizedChatId) {
                ChatRecord(applicationState = applicationState)
            }
            if (record.phase == ChatRuntimeStatePhase.CANCELLED &&
                (state is InputProcessingState.Idle || state is InputProcessingState.Completed)
            ) {
                return
            }
            val now = System.currentTimeMillis()
            record.sourceRecords[runtime] = ChatSourceRecord(state = state, updatedAt = now)
            val effectiveSource = selectSourceRecord(record)
            applyInputProcessingState(record, effectiveSource.state)
            record.userState = resolveUserState(record)
            record.updatedAt = now
            publishChangedLocked(normalizedChatId)
        }
    }

    fun markCancelled(runtime: ChatRuntimeSlot, chatId: String) {
        val normalizedChatId = chatId.trim()
        if (normalizedChatId.isBlank()) {
            return
        }

        synchronized(lock) {
            val record = records.getOrPut(normalizedChatId) {
                ChatRecord(applicationState = applicationState)
            }
            val now = System.currentTimeMillis()
            record.sourceRecords[runtime] =
                ChatSourceRecord(state = InputProcessingState.Idle, updatedAt = now)
            val anotherSourceIsActive = record.sourceRecords.any { (source, sourceRecord) ->
                source != runtime && phaseForInputState(sourceRecord.state).isActive
            }
            if (anotherSourceIsActive || record.phase == ChatRuntimeStatePhase.CANCELLED) {
                return
            }

            record.phase = ChatRuntimeStatePhase.CANCELLED
            record.userState = null
            record.toolName = null
            record.confirmationToolName = null
            record.error = null
            record.updatedAt = now
            publishChangedLocked(normalizedChatId)
        }
    }

    private fun selectSourceRecord(record: ChatRecord): ChatSourceRecord {
        return record.sourceRecords.values
            .filter { phaseForInputState(it.state).isActive }
            .maxByOrNull { it.updatedAt }
            ?: record.sourceRecords.values.maxByOrNull { it.updatedAt }
            ?: ChatSourceRecord()
    }

    private fun phaseForInputState(state: InputProcessingState): ChatRuntimeStatePhase {
        return when (state) {
            is InputProcessingState.Idle,
            is InputProcessingState.Completed -> ChatRuntimeStatePhase.IDLE

            is InputProcessingState.Processing,
            is InputProcessingState.ProcessingToolResult,
            is InputProcessingState.ExecutingPlan -> ChatRuntimeStatePhase.THINKING

            is InputProcessingState.Connecting -> ChatRuntimeStatePhase.REQUESTING

            is InputProcessingState.Summarizing -> ChatRuntimeStatePhase.SUMMARIZING

            is InputProcessingState.Receiving -> ChatRuntimeStatePhase.GENERATING_RESPONSE
            is InputProcessingState.ExecutingTool -> ChatRuntimeStatePhase.CALLING_TOOL
            is InputProcessingState.ToolProgress,
            is InputProcessingState.WaitingToolResult -> ChatRuntimeStatePhase.WAITING_TOOL_RESULT
            is InputProcessingState.Retrying -> ChatRuntimeStatePhase.RETRYING
            is InputProcessingState.AiError,
            is InputProcessingState.ToolError,
            is InputProcessingState.Error -> ChatRuntimeStatePhase.ERROR
        }
    }

    private fun applyInputProcessingState(record: ChatRecord, state: InputProcessingState) {
        when (state) {
            is InputProcessingState.Idle,
            is InputProcessingState.Completed -> {
                record.phase = ChatRuntimeStatePhase.IDLE
                record.toolName = null
                record.error = null
            }

            is InputProcessingState.Processing,
            is InputProcessingState.ProcessingToolResult,
            is InputProcessingState.ExecutingPlan -> {
                record.phase = ChatRuntimeStatePhase.THINKING
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

            is InputProcessingState.ToolProgress,
            is InputProcessingState.WaitingToolResult -> {
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
            }

            is InputProcessingState.AiError -> {
                record.phase = ChatRuntimeStatePhase.ERROR
                record.toolName = null
                record.error = ChatRuntimeStateError(
                    source = ChatRuntimeStateErrorSource.AI,
                    code = state.code,
                    message = state.message,
                    recoverable = state.recoverable,
                    retryAttempt = state.retryAttempt
                )
            }

            is InputProcessingState.ToolError -> {
                record.phase = ChatRuntimeStatePhase.ERROR
                record.toolName = state.toolName
                record.error = ChatRuntimeStateError(
                    source = ChatRuntimeStateErrorSource.TOOL,
                    code = state.code,
                    message = state.message,
                    recoverable = state.recoverable,
                    retryAttempt = state.retryAttempt
                )
            }

            is InputProcessingState.Error -> {
                record.phase = ChatRuntimeStatePhase.ERROR
                record.toolName = null
                record.error = ChatRuntimeStateError(
                    source = when (state.errorSource) {
                        InputProcessingErrorSource.AI ->
                            ChatRuntimeStateErrorSource.AI
                        InputProcessingErrorSource.TOOL ->
                            ChatRuntimeStateErrorSource.TOOL
                        InputProcessingErrorSource.API ->
                            ChatRuntimeStateErrorSource.API
                        InputProcessingErrorSource.SYSTEM ->
                            ChatRuntimeStateErrorSource.SYSTEM
                    },
                    code = state.code,
                    message = state.message,
                    recoverable = state.recoverable,
                    retryAttempt = state.retryAttempt,
                    providerCode = state.providerCode,
                    httpStatusCode = state.httpStatusCode,
                    retryAfterMs = state.retryAfterMs
                )
            }
        }
    }

    fun updateUserDraft(runtime: ChatRuntimeSlot, chatId: String, hasDraft: Boolean) {
        val normalizedChatId = chatId.trim()
        if (normalizedChatId.isBlank()) {
            return
        }

        synchronized(lock) {
            val record = records.getOrPut(normalizedChatId) {
                ChatRecord(applicationState = applicationState)
            }
            record.draftByRuntime[runtime] = hasDraft
            record.userState = resolveUserState(record)
            record.updatedAt = System.currentTimeMillis()
            publishChangedLocked(normalizedChatId)
        }
    }

    fun updateToolConfirmation(chatId: String, toolName: String?) {
        val normalizedChatId = chatId.trim()
        if (normalizedChatId.isBlank()) {
            return
        }

        synchronized(lock) {
            val record = records.getOrPut(normalizedChatId) {
                ChatRecord(applicationState = applicationState)
            }
            record.confirmationToolName = toolName?.trim()?.takeIf { it.isNotBlank() }
            record.updatedAt = System.currentTimeMillis()
            publishChangedLocked(normalizedChatId)
        }
    }

    fun clearToolConfirmations() {
        synchronized(lock) {
            val changedIds = records
                .filterValues { it.confirmationToolName != null }
                .keys
                .toList()
            if (changedIds.isEmpty()) {
                return
            }
            changedIds.forEach { chatId ->
                records[chatId]?.let { record ->
                    record.confirmationToolName = null
                    record.updatedAt = System.currentTimeMillis()
                }
            }
            publishAllChangedLocked()
        }
    }

    fun updateApplicationState(state: ChatRuntimeStateApplicationState) {
        synchronized(lock) {
            if (applicationState == state && records.values.all { it.applicationState == state }) {
                return
            }
            applicationState = state
            records.values.forEach { record ->
                record.applicationState = state
                record.updatedAt = System.currentTimeMillis()
            }
            publishAllChangedLocked()
        }
    }

    fun activeSnapshots(): List<ChatRuntimeStateSnapshot> {
        return snapshots.value.values
            .filter { it.phase.isActive || it.userState != null }
            .sortedBy { it.chatId }
    }

    fun replayEvents(): List<ChatRuntimeStateEvent> {
        synchronized(lock) {
            val currentSnapshots = buildSnapshotMapLocked()
            val global = buildGlobalSnapshot(currentSnapshots)
            return buildList {
                add(
                    ChatRuntimeStateEvent(
                        eventType = EVENT_STATE_SNAPSHOT,
                        session = null,
                        global = global
                    )
                )
                currentSnapshots.values
                    .filter { it.phase.isActive || it.userState != null || it.phase == ChatRuntimeStatePhase.CANCELLED }
                    .sortedBy { it.chatId }
                    .forEach { snapshot ->
                        add(
                            ChatRuntimeStateEvent(
                                eventType = EVENT_STATE_SNAPSHOT,
                                session = snapshot,
                                global = global
                            )
                        )
                    }
            }
        }
    }

    private fun resolveUserState(record: ChatRecord): ChatRuntimeStateUserState? {
        return when {
            record.draftByRuntime.values.any { it } -> ChatRuntimeStateUserState.TYPING
            record.phase.isActive -> ChatRuntimeStateUserState.WAITING_FOR_AI
            else -> null
        }
    }

    private fun buildSnapshot(chatId: String, record: ChatRecord): ChatRuntimeStateSnapshot {
        val effectivePhase = if (record.confirmationToolName != null) {
            ChatRuntimeStatePhase.WAITING_TOOL_CONFIRMATION
        } else {
            record.phase
        }
        return ChatRuntimeStateSnapshot(
            chatId = chatId,
            phase = effectivePhase,
            userState = record.userState,
            applicationState = record.applicationState,
            toolName = record.confirmationToolName ?: record.toolName,
            error = record.error,
            updatedAt = record.updatedAt
        )
    }

    private fun buildSnapshotMapLocked(): Map<String, ChatRuntimeStateSnapshot> {
        return records.mapValues { (chatId, record) -> buildSnapshot(chatId, record) }
    }

    private fun buildGlobalSnapshot(
        currentSnapshots: Map<String, ChatRuntimeStateSnapshot>
    ): ChatRuntimeStateGlobalSnapshot {
        val activeChatIds = currentSnapshots.values
            .filter { it.phase.isActive || it.userState != null }
            .map { it.chatId }
            .distinct()
            .sorted()
        return ChatRuntimeStateGlobalSnapshot(
            activity = if (activeChatIds.isEmpty()) {
                ChatRuntimeStateActivity.IDLE
            } else {
                ChatRuntimeStateActivity.ACTIVE
            },
            applicationState = applicationState,
            activeChatIds = activeChatIds
        )
    }

    private fun publishChangedLocked(chatId: String) {
        val currentSnapshots = buildSnapshotMapLocked()
        _snapshots.value = currentSnapshots
        val global = buildGlobalSnapshot(currentSnapshots)
        _globalSnapshot.value = global
        _events.tryEmit(
            ChatRuntimeStateEvent(
                eventType = EVENT_STATE_CHANGED,
                session = currentSnapshots[chatId],
                global = global
            )
        )
    }

    private fun publishAllChangedLocked() {
        val currentSnapshots = buildSnapshotMapLocked()
        _snapshots.value = currentSnapshots
        val global = buildGlobalSnapshot(currentSnapshots)
        _globalSnapshot.value = global
        if (currentSnapshots.isEmpty()) {
            _events.tryEmit(
                ChatRuntimeStateEvent(
                    eventType = EVENT_STATE_CHANGED,
                    session = null,
                    global = global
                )
            )
            return
        }
        currentSnapshots.values.forEach { snapshot ->
            _events.tryEmit(
                ChatRuntimeStateEvent(
                    eventType = EVENT_STATE_CHANGED,
                    session = snapshot,
                    global = global
                )
            )
        }
        _globalSnapshot.value = global
    }
}
