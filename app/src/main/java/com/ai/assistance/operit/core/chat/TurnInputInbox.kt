package com.ai.assistance.operit.core.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Serializes input acceptance with the model loop's decision to finish the turn. */
class TurnInputInbox {
    val turnId: String = java.util.UUID.randomUUID().toString()
    private val lock = Any()
    private var open = true
    /** True once the turn ended to compact its context; the follow-up turn should still get the input. */
    @Volatile
    var sealedForSummary: Boolean = false
        private set
    private val revision = MutableStateFlow(0L)
    internal val changes: kotlinx.coroutines.flow.StateFlow<Long> = revision
    data class Input(
        val text: String,
        val consumed: () -> Unit = {},
        val returned: () -> Unit = {},
        /** Used instead of [returned] when the turn stopped only to compact its context. */
        val returnedForSummary: (() -> Unit)? = null,
        val agentPath: String? = null,
        val startsAgentTurn: Boolean = false,
        /** Told the time of the chat row this input becomes, just before that row is added. */
        val saved: (Long) -> Unit = {},
        /** When the user sent it; 0 for input not typed by the user. Its row keeps this time. */
        val sentAt: Long = 0L,
    )
    private val pending = ArrayDeque<Input>()
    private var delivering: List<Input> = emptyList()
    enum class PendingInputKind { USER, AGENT }
    fun pendingInputKind(): PendingInputKind? = synchronized(lock) {
        when {
            pending.any { it.agentPath == null } -> PendingInputKind.USER
            pending.isNotEmpty() -> PendingInputKind.AGENT
            else -> null
        }
    }
    fun hasPending(): Boolean = synchronized(lock) { pending.isNotEmpty() }
    fun hasPendingUserInput(): Boolean = synchronized(lock) { pending.any { it.agentPath == null } }

    /** StateFlow replays the current version, including input offered before subscription. */
    suspend fun awaitInput(userOnly: Boolean = false) {
        revision.first { if (userOnly) hasPendingUserInput() else hasPending() }
    }

    fun isOpen(): Boolean = synchronized(lock) { open }

    fun offer(input: Input): Boolean = synchronized(lock) {
        if (!open || input.text.isBlank()) return@synchronized false
        pending.addLast(input)
        revision.value += 1
        true
    }

    fun drain(): List<Input> = synchronized(lock) {
        check(delivering.isEmpty())
        pending.toList().also { pending.clear(); delivering = it }
    }

    fun acknowledge() {
        val inputs = synchronized(lock) { delivering.also { delivering = emptyList() } }
        inputs.forEach { it.consumed() }
    }

    fun seal() = synchronized(lock) {
        open = false
    }

    fun sealForSummary() = synchronized(lock) {
        open = false
        sealedForSummary = true
    }

    fun finishIfEmpty(): Boolean = synchronized(lock) {
        if (pending.isNotEmpty()) return@synchronized false
        open = false
        true
    }

    fun close(): List<Input> = synchronized(lock) {
        open = false
        (delivering + pending).also { pending.clear(); delivering = emptyList() }
    }
}
