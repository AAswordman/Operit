package com.ai.assistance.operit.ui.common.a2ui.submission

import com.ai.assistance.operit.ui.common.a2ui.A2uiOwner
import com.ai.assistance.operit.ui.common.a2ui.A2uiProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/** 等待能力仅存活于当前请求；历史卡片不能恢复或替换已经结束的等待。 */
internal class CardSubmissionStore(private val nowNanos: () -> Long = System::nanoTime) {
    enum class Status { Waiting, Submitted, TimedOut, Cancelled, Inactive }
    data class Outcome(val status: Status, val action: String? = null, val data: JsonObject? = null)
    private data class OwnerKey(val timestamp: Long, val variant: Int)
    private data class Entry(
        val run: String,
        val reference: String,
        val chat: String,
        val fingerprint: String,
        val deadline: Long,
        val state: MutableStateFlow<Status> = MutableStateFlow(Status.Waiting),
        val completion: CompletableDeferred<Outcome> = CompletableDeferred(),
        var owner: OwnerKey? = null,
    )

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun register(run: String, reference: String, chat: String, document: String, timeoutMillis: Long) {
        require(run.isNotBlank() && chat.isNotBlank() && timeoutMillis in 1L..600_000L)
        val surface = A2uiProtocol.parse(document, true).surfaceIds.single()
        require(isAwaitable(surface))
        check(surface !in entries && entries.size < 1024) { "Too many waiting cards." }
        entries[surface] = Entry(run, reference, chat, fingerprint(document), nowNanos() + timeoutMillis * 1_000_000)
    }

    @Synchronized
    fun observe(owner: A2uiOwner, document: String, surface: String): StateFlow<Status>? =
        matching(owner, document, surface)?.state

    @Synchronized
    fun submit(owner: A2uiOwner, document: String, surface: String, action: String, data: JsonObject): Boolean {
        if (action !in setOf("ai_submit", "tool_action") || data.toString().length > A2uiProtocol.MAX_STATE) return false
        val entry = matching(owner, document, surface) ?: return false
        if (entry.state.value != Status.Waiting) return false
        if (nowNanos() >= entry.deadline) {
            finish(entry, Outcome(Status.TimedOut))
            return false
        }
        return finish(entry, Outcome(Status.Submitted, action, data))
    }

    @Synchronized
    fun cancel(owner: A2uiOwner, document: String, surface: String): Boolean {
        val entry = matching(owner, document, surface) ?: return false
        return finish(entry, Outcome(Status.Cancelled))
    }

    @Synchronized
    fun cancel(run: String, reference: String): Boolean {
        val entry = entries.values.singleOrNull { it.run == run && it.reference == reference } ?: return false
        return finish(entry, Outcome(Status.Cancelled))
    }

    suspend fun await(run: String, reference: String): Outcome {
        val entry = synchronized(this) {
            entries.values.singleOrNull { it.run == run && it.reference == reference }
                ?: error("No waiting card in this active turn.")
        }
        return try {
            val remainingNanos = (entry.deadline - nowNanos()).coerceAtLeast(0)
            val remainingMillis = (remainingNanos + 999_999) / 1_000_000
            withTimeoutOrNull(remainingMillis) { entry.completion.await() } ?: kotlin.run {
                synchronized(this) { finish(entry, Outcome(Status.TimedOut)) }
                // 超时与提交竞争时，读取真正获得终态的结果，不能覆盖已接受的提交。
                entry.completion.await()
            }
        } catch (cancelled: CancellationException) {
            synchronized(this) { finish(entry, Outcome(Status.Cancelled)) }
            throw cancelled
        }
    }

    @Synchronized
    fun end(run: String) {
        val retiring = entries.filterValues { it.run == run }
        retiring.forEach { (surface, entry) ->
            finish(entry, Outcome(Status.Cancelled))
            entries.remove(surface)
        }
    }

    private fun matching(owner: A2uiOwner, document: String, surface: String): Entry? {
        val entry = entries[surface] ?: return null
        if (entry.chat != owner.chatId || entry.fingerprint != fingerprint(document)) return null
        val identity = OwnerKey(owner.timestamp, owner.variant)
        if (entry.owner == null) entry.owner = identity
        return entry.takeIf { it.owner == identity }
    }

    private fun finish(entry: Entry, outcome: Outcome): Boolean {
        if (entry.state.value != Status.Waiting) return false
        entry.state.value = outcome.status
        return entry.completion.complete(outcome)
    }

    companion object {
        val current = CardSubmissionStore()
        private val surfacePattern = Regex("await_(live_)?[0-9a-f]{32}")
        fun isAwaitable(surface: String) = surfacePattern.matches(surface)
        fun surface(reference: String, live: Boolean = false) =
            (if (live) "await_live_" else "await_") + reference.replace("-", "")
        private fun fingerprint(document: String) = A2uiProtocol.fingerprint(document.trimEnd('\r', '\n'))
    }
}
