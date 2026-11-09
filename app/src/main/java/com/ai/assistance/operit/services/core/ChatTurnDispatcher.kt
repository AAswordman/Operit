package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatTurnOptions
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.ChatServiceCore
import com.ai.assistance.operit.util.stream.SharedStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TURN_CANCELLATION_TIMEOUT_MS = 15_000L

internal suspend fun awaitChatTurnTerminalSignal(
    terminalSignal: CompletableDeferred<ChatTurnTerminalSignal>,
    responseTimeoutMs: Long?,
): ChatTurnTerminalSignal =
    if (responseTimeoutMs == null) {
        terminalSignal.await()
    } else {
        withTimeout(responseTimeoutMs) { terminalSignal.await() }
    }

data class ChatTurnDispatchRequest(
    val chatId: String?,
    val message: String,
    val roleCardId: String?,
    val proxySenderName: String?,
    val turnOptions: ChatTurnOptions,
    val chatModelConfigIdOverride: String? = null,
    val chatModelIndexOverride: Int? = null,
    val responseStreamAcquireTimeoutMs: Long,
    val responseTimeoutMs: Long?,
    val turnId: String = UUID.randomUUID().toString(),
)

sealed interface ChatTurnTerminalSignal {
    val turnId: String
    val chatId: String

    data class Completed(
        override val turnId: String,
        override val chatId: String,
        val assistantMessageTimestamp: Long,
    ) : ChatTurnTerminalSignal

    data class Failed(
        override val turnId: String,
        override val chatId: String,
        val error: String,
    ) : ChatTurnTerminalSignal

    data class Cancelled(
        override val turnId: String,
        override val chatId: String,
    ) : ChatTurnTerminalSignal
}

/** A registered turn retains its replayable stream even after the runtime has finished. */
class RegisteredChatTurn {
    val terminal = CompletableDeferred<ChatTurnTerminalSignal>()
    val started = CompletableDeferred<Pair<String, SharedStream<String>>>()

    fun complete(signal: ChatTurnTerminalSignal) {
        terminal.complete(signal)
        if (!started.isCompleted) {
            started.completeExceptionally(when (signal) {
                is ChatTurnTerminalSignal.Cancelled -> CancellationException("Turn ${signal.turnId} was cancelled")
                is ChatTurnTerminalSignal.Failed -> IllegalStateException(signal.error)
                is ChatTurnTerminalSignal.Completed -> IllegalStateException("Turn completed without a response stream")
            })
        }
    }
}

data class ChatTurnOutcome(
    val turnId: String,
    val chatId: String,
    val finalAssistantText: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val completedAt: Long,
)

data class ChatTurnSession(
    val turnId: String,
    val chatId: String,
    val message: String,
    val responseStream: SharedStream<String>,
    val responseTimeoutMs: Long,
    private val enforceResponseTimeout: Boolean,
    private val terminalSignal: CompletableDeferred<ChatTurnTerminalSignal>,
    private val loadPersistedMessage: suspend (Long) -> ChatMessage?,
    private val currentStateProvider: () -> InputProcessingState,
    private val cancelAction: () -> Unit,
    private val cancelAndAwaitAction: suspend () -> Unit,
    private val timeoutAction: (String) -> Unit,
) {
    fun currentState(): InputProcessingState = currentStateProvider()

    suspend fun awaitOutcome(): ChatTurnOutcome {
        val signal =
            try {
                awaitChatTurnTerminalSignal(
                    terminalSignal = terminalSignal,
                    responseTimeoutMs = responseTimeoutMs.takeIf { enforceResponseTimeout },
                )
            } catch (error: TimeoutCancellationException) {
                if (!currentCoroutineContext().isActive) throw error
                val message = "Turn $turnId timed out waiting for a terminal result"
                withContext(NonCancellable) {
                    runCatching {
                        withTimeout(TURN_CANCELLATION_TIMEOUT_MS) {
                            cancelAndAwaitAction()
                        }
                    }
                    timeoutAction(message)
                }
                throw IllegalStateException(message)
            }
        return when (signal) {
            is ChatTurnTerminalSignal.Completed -> {
                val message =
                    loadPersistedMessage(signal.assistantMessageTimestamp)
                        ?: throw IllegalStateException(
                            "Turn $turnId completed but its persisted assistant message is missing"
                        )
                ChatTurnOutcome(
                    turnId = turnId,
                    chatId = chatId,
                    finalAssistantText = message.content,
                    inputTokens = message.inputTokens,
                    outputTokens = message.outputTokens,
                    completedAt = message.completedAt,
                )
            }
            is ChatTurnTerminalSignal.Failed ->
                throw IllegalStateException("Turn $turnId failed: ${signal.error}")
            is ChatTurnTerminalSignal.Cancelled ->
                throw CancellationException("Turn $turnId was cancelled")
        }
    }

    fun cancel() {
        cancelAction()
    }

    suspend fun cancelAndAwaitTermination() {
        withContext(NonCancellable) {
            try {
                withTimeout(TURN_CANCELLATION_TIMEOUT_MS) {
                    cancelAndAwaitAction()
                    terminalSignal.await()
                }
            } catch (_: TimeoutCancellationException) {
                timeoutAction("Turn $turnId timed out while cancelling")
            }
        }
    }

}

sealed interface ChatTurnDispatchResult {
    data class Started(val session: ChatTurnSession) : ChatTurnDispatchResult

    data class Failed(
        val chatId: String,
        val message: String,
        val error: String,
    ) : ChatTurnDispatchResult
}

/**
 * Starts a chat turn without changing the visible chat selection.
 *
 * This is the shared transport used by chat tools and, later, Subagent execution. It deliberately
 * exposes the raw response stream only; the stable terminal outcome is added on top of this path.
 */
class ChatTurnDispatcher {
    private suspend fun cancelRegisteredTurnWithinTimeout(
        core: ChatServiceCore,
        turnId: String,
        chatId: String,
    ) {
        withContext(NonCancellable) {
            try {
                withTimeout(TURN_CANCELLATION_TIMEOUT_MS) {
                    core.cancelRegisteredChatTurnAndAwait(turnId, chatId)
                }
            } catch (_: TimeoutCancellationException) {
                core.failRegisteredChatTurn(
                    turnId = turnId,
                    chatId = chatId,
                    error = "Turn $turnId timed out while cancelling",
                )
            }
        }
    }

    suspend fun dispatch(
        core: ChatServiceCore,
        request: ChatTurnDispatchRequest,
    ): ChatTurnDispatchResult {
        val timeoutDeadlineMs =
            request.responseTimeoutMs?.let { timeoutMs ->
                System.currentTimeMillis() + timeoutMs
            }

        fun remainingTimeoutMs(defaultTimeoutMs: Long): Long {
            val deadline = timeoutDeadlineMs ?: return defaultTimeoutMs
            return (deadline - System.currentTimeMillis()).coerceAtMost(defaultTimeoutMs)
                .coerceAtLeast(1L)
        }

        val hasTargetChat = !request.chatId.isNullOrBlank()
        if (hasTargetChat) {
            val targetChatId = requireNotNull(request.chatId)
            val chatExists = core.chatExists(targetChatId)
            if (!chatExists) {
                return ChatTurnDispatchResult.Failed(
                    chatId = targetChatId,
                    message = request.message,
                    error = "Specified chat does not exist: $targetChatId",
                )
            }
        }

        val preflightChatId = request.chatId ?: core.currentChatId.value
        if (preflightChatId != null && !request.turnOptions.isSubTask &&
            core.getChatHistoryDelegate().isSubagentChat(preflightChatId)
        ) {
            return ChatTurnDispatchResult.Failed(
                chatId = preflightChatId,
                message = request.message,
                error = "Subagent transcripts are read-only. Use collaboration tools to continue the task.",
            )
        }

        try {
            preflightChatId?.let { chatId ->
                withTimeout(
                    remainingTimeoutMs(
                        request.responseTimeoutMs ?: request.responseStreamAcquireTimeoutMs
                    )
                ) {
                    core.activeStreamingChatIds.first { activeChatIds ->
                        chatId !in activeChatIds
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive) throw error
            return ChatTurnDispatchResult.Failed(
                chatId = preflightChatId.orEmpty(),
                message = request.message,
                error = "Previous message is still being processed",
            )
        }

        val registration = core.registerChatTurn(request.turnId)
        val terminalSignal = registration.terminal
        val effectiveTurnOptions = request.turnOptions.copy(turnId = request.turnId)

        try {
            if (hasTargetChat) {
                core.sendUserMessage(
                    promptFunctionType = PromptFunctionType.CHAT,
                    roleCardIdOverride = request.roleCardId,
                    chatIdOverride = preflightChatId,
                    messageTextOverride = request.message,
                    proxySenderNameOverride = request.proxySenderName,
                    chatModelConfigIdOverride = request.chatModelConfigIdOverride,
                    chatModelIndexOverride = request.chatModelIndexOverride,
                    turnOptions = effectiveTurnOptions,
                )
            } else {
                core.sendUserMessage(
                    promptFunctionType = PromptFunctionType.CHAT,
                    roleCardIdOverride = request.roleCardId,
                    messageTextOverride = request.message,
                    proxySenderNameOverride = request.proxySenderName,
                    chatModelConfigIdOverride = request.chatModelConfigIdOverride,
                    chatModelIndexOverride = request.chatModelIndexOverride,
                    turnOptions = effectiveTurnOptions,
                )
            }
        } catch (error: Throwable) {
            core.failRegisteredChatTurn(
                turnId = request.turnId,
                chatId = preflightChatId.orEmpty(),
                error = error.message ?: error.javaClass.simpleName,
            )
            throw error
        }

        val started = try {
            withTimeout(remainingTimeoutMs(request.responseStreamAcquireTimeoutMs)) { registration.started.await() }
        } catch (error: TimeoutCancellationException) {
            cancelRegisteredTurnWithinTimeout(core, request.turnId, preflightChatId.orEmpty())
            if (!currentCoroutineContext().isActive) throw error
            core.failRegisteredChatTurn(request.turnId, preflightChatId.orEmpty(), "Timeout waiting for AI response")
            return ChatTurnDispatchResult.Failed(preflightChatId.orEmpty(), request.message, "Timeout waiting for AI response")
        } catch (error: CancellationException) {
            cancelRegisteredTurnWithinTimeout(core, request.turnId, preflightChatId.orEmpty())
            throw error
        } catch (error: Exception) {
            return ChatTurnDispatchResult.Failed(preflightChatId.orEmpty(), request.message, error.message ?: "Unable to start turn")
        }
        val (resolvedChatId, responseStream) = started

        return ChatTurnDispatchResult.Started(
            ChatTurnSession(
                turnId = request.turnId,
                chatId = resolvedChatId,
                message = request.message,
                responseStream = responseStream,
                responseTimeoutMs =
                    request.responseTimeoutMs?.let(::remainingTimeoutMs) ?: Long.MAX_VALUE,
                enforceResponseTimeout = request.responseTimeoutMs != null,
                terminalSignal = terminalSignal,
                loadPersistedMessage = { timestamp ->
                    core.getChatHistoryDelegate()
                        .getPersistedMessage(resolvedChatId, timestamp)
                },
                currentStateProvider = {
                    core.inputProcessingStateByChatId.value[resolvedChatId]
                        ?: InputProcessingState.Idle
                },
                cancelAction = {
                    runCatching {
                        core.cancelRegisteredChatTurn(request.turnId, resolvedChatId)
                    }
                },
                cancelAndAwaitAction = {
                    core.cancelRegisteredChatTurnAndAwait(request.turnId, resolvedChatId)
                },
                timeoutAction = { error ->
                    core.failRegisteredChatTurn(request.turnId, resolvedChatId, error)
                },
            )
        )
    }
}
