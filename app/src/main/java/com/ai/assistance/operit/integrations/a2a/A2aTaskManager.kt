package com.ai.assistance.operit.integrations.a2a

import android.content.Context
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequest
import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequestExecutor
import com.ai.assistance.operit.integrations.externalchat.ExternalChatResponseSanitizer
import com.ai.assistance.operit.integrations.externalchat.ExternalChatStreamingSession
import com.ai.assistance.operit.integrations.externalchat.ExternalChatStreamingStartResult
import com.ai.assistance.operit.util.AppLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class A2aIncomingMessage(
    val text: String,
    val contextId: String?
)

data class A2aTaskSnapshot(
    val id: String,
    val contextId: String,
    val state: String,
    val output: String,
    val error: String? = null
)

sealed interface A2aTaskEvent {
    data class Status(val task: A2aTaskSnapshot, val final: Boolean) : A2aTaskEvent

    data class Artifact(
        val task: A2aTaskSnapshot,
        val text: String
    ) : A2aTaskEvent
}

class A2aTaskSubscription internal constructor(
    val snapshot: A2aTaskSnapshot,
    private val closeAction: () -> Unit
) : AutoCloseable {
    override fun close() {
        closeAction()
    }
}

/**
 * Keeps A2A tasks within the lifetime of the external HTTP service and maps them to isolated
 * Operit chats. The A2A protocol layer owns serialization; this class only manages execution.
 */
class A2aTaskManager(
    context: Context,
    private val serviceScope: CoroutineScope
) {

    private val executor = ExternalChatRequestExecutor(context.applicationContext)
    private val tasks = ConcurrentHashMap<String, TaskRecord>()
    private val contextChats = ConcurrentHashMap<String, String>()

    fun submit(message: A2aIncomingMessage): A2aTaskSnapshot {
        val contextId = resolveContextId(message)
        val chatId = contextChats[contextId]
        val taskId = UUID.randomUUID().toString()
        val record = TaskRecord(taskId, contextId)
        tasks[taskId] = record

        val job = serviceScope.launch(Dispatchers.IO) {
            executeTask(record, message.text, chatId)
        }
        record.attachJob(job)
        return record.snapshot()
    }

    fun getTask(taskId: String): A2aTaskSnapshot {
        return findTask(taskId).snapshot()
    }

    fun listTasks(contextId: String?, state: String?): List<A2aTaskSnapshot> {
        return tasks.values
            .asSequence()
            .map(TaskRecord::snapshot)
            .filter { task -> contextId == null || task.contextId == contextId }
            .filter { task -> state == null || task.state == state }
            .sortedBy(A2aTaskSnapshot::id)
            .toList()
    }

    fun cancelTask(taskId: String): A2aTaskSnapshot {
        return findTask(taskId).cancel()
    }

    fun subscribe(
        taskId: String,
        requireActive: Boolean,
        listener: (A2aTaskEvent) -> Unit
    ): A2aTaskSubscription {
        return findTask(taskId).subscribe(requireActive, listener)
    }

    suspend fun awaitTerminalTask(taskId: String): A2aTaskSnapshot {
        return findTask(taskId).awaitTerminal()
    }

    fun close() {
        tasks.values.forEach(TaskRecord::cancelForShutdown)
        tasks.clear()
        contextChats.clear()
    }

    private suspend fun executeTask(record: TaskRecord, message: String, existingChatId: String?) {
        var streamingSession: ExternalChatStreamingSession? = null
        try {
            val request = ExternalChatRequest(
                requestId = record.id,
                message = message,
                createNewChat = existingChatId == null,
                chatId = existingChatId,
                createIfNone = false,
                returnToolStatus = false
            )
            when (val startResult = executor.startStreaming(request)) {
                is ExternalChatStreamingStartResult.Failed -> {
                    record.fail(startResult.result.error ?: "A2A task could not start")
                }

                is ExternalChatStreamingStartResult.Started -> {
                    val session = startResult.session
                    streamingSession = session
                    if (!record.start(session)) {
                        session.responseStreamSession.cancel("A2A task already cancelled")
                        return
                    }
                    contextChats[record.contextId] = session.chatId
                    val sanitizedResponseStream = ExternalChatResponseSanitizer.sanitizeStream(
                        session.responseStreamSession.responseStream,
                        returnToolStatus = false
                    )
                    sanitizedResponseStream.collect { chunk ->
                        if (chunk.isNotEmpty()) {
                            record.appendOutput(chunk)
                        }
                    }

                    when (val finalState = session.responseStreamSession.currentState()) {
                        is InputProcessingState.Error -> record.fail(finalState.message)
                        else -> record.complete()
                    }
                }
            }
        } catch (error: CancellationException) {
            record.markCancelled()
            throw error
        } catch (error: Exception) {
            AppLogger.e(TAG, "A2A task execution failed: ${record.id}", error)
            record.fail(error.message ?: "A2A task execution failed")
        } finally {
            streamingSession?.cleanup()
        }
    }

    private fun resolveContextId(message: A2aIncomingMessage): String {
        return message.contextId?.trim()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
    }

    private fun findTask(taskId: String): TaskRecord {
        return tasks[taskId] ?: throw A2aTaskNotFoundException(taskId)
    }

    private class TaskRecord(
        val id: String,
        val contextId: String
    ) {
        private val lock = Any()
        private val listeners = CopyOnWriteArraySet<(A2aTaskEvent) -> Unit>()
        private val terminalTask = CompletableDeferred<A2aTaskSnapshot>()
        private var state = TASK_STATE_SUBMITTED
        private var output = ""
        private var error: String? = null
        private var job: Job? = null
        private var streamingSession: ExternalChatStreamingSession? = null

        fun snapshot(): A2aTaskSnapshot = synchronized(lock) { snapshotLocked() }

        fun attachJob(job: Job) {
            val shouldCancel = synchronized(lock) {
                this.job = job
                isTerminalState(state)
            }
            if (shouldCancel) {
                job.cancel()
            }
        }

        fun start(session: ExternalChatStreamingSession): Boolean {
            val workingTask = synchronized(lock) {
                if (isTerminalState(state)) {
                    null
                } else {
                    streamingSession = session
                    state = TASK_STATE_WORKING
                    snapshotLocked()
                }
            }
            if (workingTask == null) {
                return false
            }
            publish(A2aTaskEvent.Status(workingTask, final = false))
            return true
        }

        fun appendOutput(chunk: String) {
            val task = synchronized(lock) {
                if (isTerminalState(state)) {
                    null
                } else {
                    output += chunk
                    snapshotLocked()
                }
            }
            if (task != null) {
                publish(A2aTaskEvent.Artifact(task, chunk))
            }
        }

        fun complete() {
            transitionToTerminal(TASK_STATE_COMPLETED, null)
        }

        fun fail(message: String) {
            transitionToTerminal(TASK_STATE_FAILED, message)
        }

        fun markCancelled() {
            transitionToTerminal(TASK_STATE_CANCELED, "A2A task was cancelled")
        }

        fun cancel(): A2aTaskSnapshot {
            val cancellation = transitionToCancelled() ?: throw A2aTaskNotCancelableException(id)
            cancellation.session?.responseStreamSession?.cancel("A2A task cancelled")
            cancellation.job?.cancel()
            terminalTask.complete(cancellation.snapshot)
            publish(A2aTaskEvent.Status(cancellation.snapshot, final = true))
            return cancellation.snapshot
        }

        fun cancelForShutdown() {
            val cancellation = transitionToCancelled() ?: return
            cancellation.session?.responseStreamSession?.cancel("A2A server shutdown")
            cancellation.job?.cancel()
            terminalTask.complete(cancellation.snapshot)
            publish(A2aTaskEvent.Status(cancellation.snapshot, final = true))
        }

        fun subscribe(
            requireActive: Boolean,
            listener: (A2aTaskEvent) -> Unit
        ): A2aTaskSubscription {
            val task = synchronized(lock) {
                if (requireActive && isTerminalState(state)) {
                    throw A2aUnsupportedOperationException(
                        "A2A task cannot be subscribed after it is terminal: $id"
                    )
                }
                listeners += listener
                snapshotLocked()
            }
            return A2aTaskSubscription(task) {
                listeners -= listener
            }
        }

        suspend fun awaitTerminal(): A2aTaskSnapshot {
            val completedTask = synchronized(lock) {
                if (isTerminalState(state)) snapshotLocked() else null
            }
            return completedTask ?: terminalTask.await()
        }

        private fun transitionToTerminal(targetState: String, targetError: String?) {
            val task = synchronized(lock) {
                if (isTerminalState(state)) {
                    null
                } else {
                    state = targetState
                    error = targetError
                    snapshotLocked()
                }
            }
            if (task != null) {
                terminalTask.complete(task)
                publish(A2aTaskEvent.Status(task, final = true))
            }
        }

        private fun transitionToCancelled(): Cancellation? {
            return synchronized(lock) {
                if (isTerminalState(state)) {
                    null
                } else {
                    state = TASK_STATE_CANCELED
                    error = "A2A task was cancelled"
                    Cancellation(snapshotLocked(), streamingSession, job)
                }
            }
        }

        private fun snapshotLocked(): A2aTaskSnapshot {
            return A2aTaskSnapshot(
                id = id,
                contextId = contextId,
                state = state,
                output = output,
                error = error
            )
        }

        private fun publish(event: A2aTaskEvent) {
            listeners.forEach { listener ->
                try {
                    listener(event)
                } catch (error: Exception) {
                    AppLogger.e(TAG, "A2A task event delivery failed: $id", error)
                }
            }
        }

        private data class Cancellation(
            val snapshot: A2aTaskSnapshot,
            val session: ExternalChatStreamingSession?,
            val job: Job?
        )
    }

    companion object {
        private const val TAG = "A2aTaskManager"
        const val TASK_STATE_SUBMITTED = "TASK_STATE_SUBMITTED"
        const val TASK_STATE_WORKING = "TASK_STATE_WORKING"
        const val TASK_STATE_INPUT_REQUIRED = "TASK_STATE_INPUT_REQUIRED"
        const val TASK_STATE_AUTH_REQUIRED = "TASK_STATE_AUTH_REQUIRED"
        const val TASK_STATE_COMPLETED = "TASK_STATE_COMPLETED"
        const val TASK_STATE_CANCELED = "TASK_STATE_CANCELED"
        const val TASK_STATE_FAILED = "TASK_STATE_FAILED"
        const val TASK_STATE_REJECTED = "TASK_STATE_REJECTED"

        fun isTerminalState(state: String): Boolean {
            return state == TASK_STATE_COMPLETED ||
                state == TASK_STATE_CANCELED ||
                state == TASK_STATE_FAILED ||
                state == TASK_STATE_REJECTED
        }
    }
}

open class A2aProtocolException(
    message: String,
    val errorCode: Int = -32602
) : IllegalArgumentException(message)

class A2aTaskNotFoundException(taskId: String) :
    A2aProtocolException("Unknown A2A task: $taskId", errorCode = -32001)

class A2aTaskNotCancelableException(taskId: String) :
    A2aProtocolException("A2A task is not cancelable: $taskId", errorCode = -32002)

class A2aPushNotificationNotSupportedException :
    A2aProtocolException("Operit does not support A2A push notifications", errorCode = -32003)

class A2aUnsupportedOperationException(message: String) :
    A2aProtocolException(message, errorCode = -32004)
