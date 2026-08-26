package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.InputProcessingState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable action phases exposed to conversation-facing integrations. */
enum class ChatCurrentActionActivity(val wireName: String) {
    IDLE("idle"),
    ACTIVE("active")
}

enum class ChatCurrentActionPhase(val wireName: String) {
    IDLE("idle"),
    THINKING("thinking"),
    CALLING_TOOL("calling_tool"),
    WAITING_TOOL_RESULT("waiting_tool_result"),
    WAITING_TOOL_CONFIRMATION("waiting_tool_confirmation"),
    GENERATING_RESPONSE("generating_response"),
    RETRYING("retrying"),
    ERROR("error");

    val isActive: Boolean
        get() = this != IDLE
}

enum class ChatCurrentActionUserState(val wireName: String) {
    TYPING("typing"),
    WAITING_FOR_AI("waiting_for_ai")
}

enum class ChatCurrentActionApplicationState(val wireName: String) {
    FOREGROUND("foreground"),
    BACKGROUND("background")
}

enum class ChatCurrentActionErrorSource(val wireName: String) {
    AI("ai"),
    TOOL("tool")
}

data class ChatCurrentActionError(
    val source: ChatCurrentActionErrorSource,
    val code: String,
    val message: String? = null,
    val recoverable: Boolean = false,
    val retryAttempt: Int? = null
)

data class ChatCurrentActionSnapshot(
    val chatId: String,
    val phase: ChatCurrentActionPhase,
    val userState: ChatCurrentActionUserState?,
    val applicationState: ChatCurrentActionApplicationState,
    val toolName: String? = null,
    val error: ChatCurrentActionError? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatCurrentActionGlobalSnapshot(
    val activity: ChatCurrentActionActivity,
    val applicationState: ChatCurrentActionApplicationState,
    val activeChatIds: List<String>,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatCurrentActionEvent(
    val eventType: String,
    val session: ChatCurrentActionSnapshot?,
    val global: ChatCurrentActionGlobalSnapshot
)

/**
 * Process-wide store for conversation action state.
 *
 * MAIN and FLOATING are implementation slots only. The public snapshot is keyed by chatId and
 * receives updates from both slots so consumers do not need to know which surface is hosting a
 * conversation.
 */
object ChatCurrentActionStore {
    private const val EVENT_STATE_SNAPSHOT = "state_snapshot"
    private const val EVENT_STATE_CHANGED = "state_changed"

    private data class ChatSourceRecord(
        var state: InputProcessingState = InputProcessingState.Idle,
        var updatedAt: Long = System.currentTimeMillis()
    )

    private data class ChatRecord(
        val sourceRecords: MutableMap<ChatRuntimeSlot, ChatSourceRecord> = linkedMapOf(),
        val draftByRuntime: MutableMap<ChatRuntimeSlot, Boolean> = linkedMapOf(),
        var phase: ChatCurrentActionPhase = ChatCurrentActionPhase.IDLE,
        var userState: ChatCurrentActionUserState? = null,
        var applicationState: ChatCurrentActionApplicationState =
            ChatCurrentActionApplicationState.BACKGROUND,
        var toolName: String? = null,
        var confirmationToolName: String? = null,
        var error: ChatCurrentActionError? = null,
        var updatedAt: Long = System.currentTimeMillis()
    )

    private val lock = Any()
    private val records = linkedMapOf<String, ChatRecord>()
    private var applicationState = ChatCurrentActionApplicationState.BACKGROUND

    private val _snapshots = MutableStateFlow<Map<String, ChatCurrentActionSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, ChatCurrentActionSnapshot>> = _snapshots.asStateFlow()

    private val _globalSnapshot = MutableStateFlow(buildGlobalSnapshot(emptyMap()))
    val globalSnapshot: StateFlow<ChatCurrentActionGlobalSnapshot> = _globalSnapshot.asStateFlow()

    private val _events = MutableSharedFlow<ChatCurrentActionEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ChatCurrentActionEvent> = _events.asSharedFlow()

    fun getSnapshot(chatId: String): ChatCurrentActionSnapshot {
        val normalizedChatId = chatId.trim()
        synchronized(lock) {
            val record = records[normalizedChatId]
            return if (record == null) {
                ChatCurrentActionSnapshot(
                    chatId = normalizedChatId,
                    phase = ChatCurrentActionPhase.IDLE,
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
            val now = System.currentTimeMillis()
            record.sourceRecords[runtime] = ChatSourceRecord(state = state, updatedAt = now)
            val effectiveSource = selectSourceRecord(record)
            applyInputProcessingState(record, effectiveSource.state)
            record.userState = resolveUserState(record)
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

    private fun phaseForInputState(state: InputProcessingState): ChatCurrentActionPhase {
        return when (state) {
            is InputProcessingState.Idle,
            is InputProcessingState.Completed -> ChatCurrentActionPhase.IDLE

            is InputProcessingState.Processing,
            is InputProcessingState.Connecting,
            is InputProcessingState.ProcessingToolResult,
            is InputProcessingState.Summarizing,
            is InputProcessingState.ExecutingPlan -> ChatCurrentActionPhase.THINKING

            is InputProcessingState.Receiving -> ChatCurrentActionPhase.GENERATING_RESPONSE
            is InputProcessingState.ExecutingTool -> ChatCurrentActionPhase.CALLING_TOOL
            is InputProcessingState.ToolProgress,
            is InputProcessingState.WaitingToolResult -> ChatCurrentActionPhase.WAITING_TOOL_RESULT
            is InputProcessingState.Retrying -> ChatCurrentActionPhase.RETRYING
            is InputProcessingState.AiError,
            is InputProcessingState.ToolError,
            is InputProcessingState.Error -> ChatCurrentActionPhase.ERROR
        }
    }

    private fun applyInputProcessingState(record: ChatRecord, state: InputProcessingState) {
        when (state) {
            is InputProcessingState.Idle,
            is InputProcessingState.Completed -> {
                record.phase = ChatCurrentActionPhase.IDLE
                record.toolName = null
                record.error = null
            }

            is InputProcessingState.Processing,
            is InputProcessingState.Connecting,
            is InputProcessingState.ProcessingToolResult,
            is InputProcessingState.Summarizing,
            is InputProcessingState.ExecutingPlan -> {
                record.phase = ChatCurrentActionPhase.THINKING
                record.toolName = null
                record.error = null
            }

            is InputProcessingState.Receiving -> {
                record.phase = ChatCurrentActionPhase.GENERATING_RESPONSE
                record.toolName = null
                record.error = null
            }

            is InputProcessingState.ExecutingTool -> {
                record.phase = ChatCurrentActionPhase.CALLING_TOOL
                record.toolName = state.toolName
                record.error = null
            }

            is InputProcessingState.ToolProgress,
            is InputProcessingState.WaitingToolResult -> {
                record.phase = ChatCurrentActionPhase.WAITING_TOOL_RESULT
                record.toolName = when (state) {
                    is InputProcessingState.ToolProgress -> state.toolName
                    is InputProcessingState.WaitingToolResult -> state.toolName
                    else -> null
                }
                record.error = null
            }

            is InputProcessingState.Retrying -> {
                record.phase = ChatCurrentActionPhase.RETRYING
                record.toolName = null
            }

            is InputProcessingState.AiError -> {
                record.phase = ChatCurrentActionPhase.ERROR
                record.toolName = null
                record.error = ChatCurrentActionError(
                    source = ChatCurrentActionErrorSource.AI,
                    code = state.code,
                    message = state.message,
                    recoverable = state.recoverable,
                    retryAttempt = state.retryAttempt
                )
            }

            is InputProcessingState.ToolError -> {
                record.phase = ChatCurrentActionPhase.ERROR
                record.toolName = state.toolName
                record.error = ChatCurrentActionError(
                    source = ChatCurrentActionErrorSource.TOOL,
                    code = state.code,
                    message = state.message,
                    recoverable = state.recoverable,
                    retryAttempt = state.retryAttempt
                )
            }

            is InputProcessingState.Error -> {
                record.phase = ChatCurrentActionPhase.ERROR
                record.toolName = null
                record.error = ChatCurrentActionError(
                    source = ChatCurrentActionErrorSource.AI,
                    code = "ai_error",
                    message = state.message
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

    fun updateApplicationState(state: ChatCurrentActionApplicationState) {
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

    fun activeSnapshots(): List<ChatCurrentActionSnapshot> {
        return snapshots.value.values
            .filter { it.phase.isActive || it.userState != null }
            .sortedBy { it.chatId }
    }

    fun replayEvents(): List<ChatCurrentActionEvent> {
        synchronized(lock) {
            val currentSnapshots = buildSnapshotMapLocked()
            val global = buildGlobalSnapshot(currentSnapshots)
            return buildList {
                add(
                    ChatCurrentActionEvent(
                        eventType = EVENT_STATE_SNAPSHOT,
                        session = null,
                        global = global
                    )
                )
                currentSnapshots.values
                    .filter { it.phase.isActive || it.userState != null }
                    .sortedBy { it.chatId }
                    .forEach { snapshot ->
                        add(
                            ChatCurrentActionEvent(
                                eventType = EVENT_STATE_SNAPSHOT,
                                session = snapshot,
                                global = global
                            )
                        )
                    }
            }
        }
    }

    private fun resolveUserState(record: ChatRecord): ChatCurrentActionUserState? {
        return when {
            record.draftByRuntime.values.any { it } -> ChatCurrentActionUserState.TYPING
            record.phase.isActive -> ChatCurrentActionUserState.WAITING_FOR_AI
            else -> null
        }
    }

    private fun buildSnapshot(chatId: String, record: ChatRecord): ChatCurrentActionSnapshot {
        val effectivePhase = if (record.confirmationToolName != null) {
            ChatCurrentActionPhase.WAITING_TOOL_CONFIRMATION
        } else {
            record.phase
        }
        return ChatCurrentActionSnapshot(
            chatId = chatId,
            phase = effectivePhase,
            userState = record.userState,
            applicationState = record.applicationState,
            toolName = record.confirmationToolName ?: record.toolName,
            error = record.error,
            updatedAt = record.updatedAt
        )
    }

    private fun buildSnapshotMapLocked(): Map<String, ChatCurrentActionSnapshot> {
        return records.mapValues { (chatId, record) -> buildSnapshot(chatId, record) }
    }

    private fun buildGlobalSnapshot(
        currentSnapshots: Map<String, ChatCurrentActionSnapshot>
    ): ChatCurrentActionGlobalSnapshot {
        val activeChatIds = currentSnapshots.values
            .filter { it.phase.isActive || it.userState != null }
            .map { it.chatId }
            .distinct()
            .sorted()
        return ChatCurrentActionGlobalSnapshot(
            activity = if (activeChatIds.isEmpty()) {
                ChatCurrentActionActivity.IDLE
            } else {
                ChatCurrentActionActivity.ACTIVE
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
            ChatCurrentActionEvent(
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
                ChatCurrentActionEvent(
                    eventType = EVENT_STATE_CHANGED,
                    session = null,
                    global = global
                )
            )
            return
        }
        currentSnapshots.values.forEach { snapshot ->
            _events.tryEmit(
                ChatCurrentActionEvent(
                    eventType = EVENT_STATE_CHANGED,
                    session = snapshot,
                    global = global
                )
            )
        }
        _globalSnapshot.value = global
    }
}
