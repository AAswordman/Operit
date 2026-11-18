package com.ai.assistance.operit.core.agent

import android.content.Context
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatTurnOptions
import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.data.model.SubagentRunStatus
import com.ai.assistance.operit.data.repository.CreateSubagentRunRequest
import com.ai.assistance.operit.data.repository.SubagentRunRepository
import com.ai.assistance.operit.services.core.ChatTurnDispatchRequest
import com.ai.assistance.operit.services.core.ChatTurnDispatchResult
import com.ai.assistance.operit.services.core.ChatTurnDispatcher
import com.ai.assistance.operit.services.core.ChatTurnOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class SubagentSliceStatus {
    CREATED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

data class SubagentSliceRun(
    val taskId: String,
    val parentChatId: String,
    val childChatId: String,
    val title: String,
    val status: SubagentSliceStatus,
    val error: String? = null,
)

/**
 * Minimal vertical slice used to prove an independent child-chat model/tool loop.
 *
 * The production scheduler, persistent run entity, general AgentProfile model and UI are
 * deliberately deferred until this path has been exercised successfully.
 */
class SubagentSliceCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val runRepository = SubagentRunRepository.getInstance(appContext)
    private val profileRepository = AgentProfileRepository.instance
    private val chatTurnDispatcher = ChatTurnDispatcher()
    private val chatCore =
        ChatRuntimeHolder.getInstance(appContext).getCore(ChatRuntimeSlot.MAIN)

    suspend fun getRun(taskId: String): SubagentSliceRun? =
        runRepository.getById(taskId)?.toSliceRun()

    suspend fun runExplore(
        parentChatId: String,
        title: String,
        prompt: String,
    ): Pair<SubagentSliceRun, ChatTurnOutcome> {
        require(title.isNotBlank()) { "Subagent title must not be blank" }
        require(prompt.isNotBlank()) { "Subagent prompt must not be blank" }
        val profile = profileRepository.requireSubagent(EXPLORE_PROFILE_ID)

        val created =
            runRepository.createSubagentChatAndRun(
                CreateSubagentRunRequest(
                    parentChatId = parentChatId,
                    parentToolCallId = null,
                    agentProfileId = profile.id,
                    title = title,
                    agentConfigSnapshot = Json.encodeToString(profile),
                    modelConfigIdSnapshot = profile.modelConfigId,
                    modelIndexSnapshot = profile.modelIndex,
                )
            )
        val taskId = created.run.id
        val child = created.childChat

        var activeSession: com.ai.assistance.operit.services.core.ChatTurnSession? = null
        try {
            // MessageCoordinationDelegate currently resolves workspace metadata from this runtime
            // flow. Wait for the just-inserted child before dispatching its first turn.
            withTimeout(CHILD_VISIBILITY_TIMEOUT_MS) {
                chatCore.chatHistories.first { histories ->
                    histories.any { it.id == child.id }
                }
            }
            updateStatus(taskId, SubagentRunStatus.RUNNING, startedAt = System.currentTimeMillis())
            val dispatch =
                chatTurnDispatcher.dispatch(
                    core = chatCore,
                    request =
                        ChatTurnDispatchRequest(
                            chatId = child.id,
                            message = SubagentPromptBuilder.buildTaskPrompt(prompt),
                            roleCardId = null,
                            proxySenderName = null,
                            turnOptions =
                                ChatTurnOptions(
                                    persistTurn = true,
                                    notifyReply = false,
                                    isSubTask = true,
                                    systemPromptOverride =
                                        SubagentPromptBuilder.buildSystemPrompt(profile),
                                ),
                            responseStreamAcquireTimeoutMs = RESPONSE_STREAM_ACQUIRE_TIMEOUT_MS,
                            responseTimeoutMs = null,
                            turnId = taskId,
                        ),
                )
            val session =
                when (dispatch) {
                    is ChatTurnDispatchResult.Started -> dispatch.session
                    is ChatTurnDispatchResult.Failed ->
                        error(dispatch.error)
                }
            activeSession = session
            val outcome = session.awaitOutcome()
            updateStatus(
                taskId,
                SubagentRunStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
            )
            return requireNotNull(getRun(taskId)) to outcome
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (activeSession != null) {
                    activeSession.cancelAndAwaitTermination()
                }
                updateStatus(
                    taskId,
                    SubagentRunStatus.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                )
            }
            throw e
        } catch (e: Exception) {
            updateStatus(
                taskId = taskId,
                status = SubagentRunStatus.FAILED,
                completedAt = System.currentTimeMillis(),
                error = e.message ?: e.javaClass.simpleName,
            )
            throw e
        } finally {
            EnhancedAIService.releaseChatInstance(child.id)
        }
    }

    private suspend fun updateStatus(
        taskId: String,
        status: SubagentRunStatus,
        startedAt: Long? = null,
        completedAt: Long? = null,
        error: String? = null,
    ) {
        runRepository.updateStatus(
            taskId = taskId,
            status = status,
            startedAt = startedAt,
            completedAt = completedAt,
            error = error,
        )
    }

    private fun SubagentRunEntity.toSliceRun(): SubagentSliceRun =
        SubagentSliceRun(
            taskId = id,
            parentChatId = parentChatId,
            childChatId = childChatId,
            title = title,
            status = SubagentSliceStatus.valueOf(status),
            error = error,
        )

    companion object {
        private const val EXPLORE_PROFILE_ID = "explore"
        private const val RESPONSE_STREAM_ACQUIRE_TIMEOUT_MS = 15_000L
        private const val CHILD_VISIBILITY_TIMEOUT_MS = 5_000L
    }
}
