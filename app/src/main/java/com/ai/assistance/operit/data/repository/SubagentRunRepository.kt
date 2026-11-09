package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatKind
import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.data.model.SubagentRunStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class CreateSubagentRunRequest(
    val parentChatId: String,
    val parentToolCallId: String?,
    val agentProfileId: String,
    val title: String,
    val agentConfigSnapshot: String?,
    val modelConfigIdSnapshot: String?,
    val modelIndexSnapshot: Int?,
    val externalOwnerType: String? = null,
    val externalOwnerId: String? = null,
)

data class SubagentRunWithChat(
    val run: SubagentRunEntity,
    val childChat: ChatEntity,
)

/**
 * Owns the durable parent/run/child relationship.
 *
 * The child chat and its run are created in one Room transaction so a task id can never point at
 * a missing transcript. Prompt and result content continue to live only in the child transcript.
 */
class SubagentRunRepository private constructor(
    private val database: AppDatabase,
) {
    private val chatDao = database.chatDao()
    private val runDao = database.subagentRunDao()

    suspend fun createSubagentChatAndRun(
        request: CreateSubagentRunRequest,
    ): SubagentRunWithChat =
        database.withTransaction {
            require(request.title.isNotBlank()) { "Subagent title must not be blank" }
            val parent =
                requireNotNull(chatDao.getChatById(request.parentChatId)) {
                    "Parent chat does not exist: ${request.parentChatId}"
                }
            val now = System.currentTimeMillis()
            val child =
                ChatEntity(
                    id = UUID.randomUUID().toString(),
                    title = request.title,
                    createdAt = now,
                    updatedAt = now,
                    group = parent.group,
                    displayOrder = -now,
                    workspace = parent.workspace,
                    workspaceEnv = parent.workspaceEnv,
                    parentChatId = parent.id,
                    chatKind = ChatKind.SUBAGENT.name,
                    characterCardName = parent.characterCardName,
                    characterGroupId = parent.characterGroupId,
                )
            val run =
                SubagentRunEntity(
                    id = UUID.randomUUID().toString(),
                    parentChatId = parent.id,
                    childChatId = child.id,
                    parentToolCallId = request.parentToolCallId,
                    agentProfileId = request.agentProfileId,
                    title = request.title,
                    status = SubagentRunStatus.CREATED.name,
                    createdAt = now,
                    agentConfigSnapshot = request.agentConfigSnapshot,
                    modelConfigIdSnapshot = request.modelConfigIdSnapshot,
                    externalOwnerType = request.externalOwnerType,
                    externalOwnerId = request.externalOwnerId,
                    modelIndexSnapshot = request.modelIndexSnapshot,
                )
            chatDao.insertChat(child)
            runDao.insert(run)
            SubagentRunWithChat(run = run, childChat = child)
        }

    suspend fun incrementModelRoundCountByChildChatId(childChatId: String) = runDao.incrementModelRoundCountByChildChatId(childChatId)

    suspend fun getById(taskId: String): SubagentRunEntity? = runDao.getById(taskId)

    fun observeById(taskId: String): Flow<SubagentRunEntity?> = runDao.observeById(taskId)

    suspend fun getByChildChatId(childChatId: String): SubagentRunEntity? =
        runDao.getByChildChatId(childChatId)

    fun observeByChildChatId(childChatId: String): Flow<SubagentRunEntity?> =
        runDao.observeByChildChatId(childChatId)

    suspend fun getByParentChatId(parentChatId: String): List<SubagentRunEntity> =
        runDao.getByParentChatId(parentChatId)

    fun observeByParentChatId(parentChatId: String): Flow<List<SubagentRunEntity>> =
        runDao.observeByParentChatId(parentChatId)

    fun observeByParentToolCallId(
        parentChatId: String,
        callId: String,
    ): Flow<SubagentRunEntity?> =
        runDao.observeByParentToolCallId(parentChatId, callId)

    suspend fun updateStatus(
        taskId: String,
        status: SubagentRunStatus,
        startedAt: Long? = null,
        completedAt: Long? = null,
        error: String? = null,
    ): Boolean {
        val allowedFrom = SubagentRunStateMachine.allowedOrigins(status)
        val updated =
            runDao.updateStatus(
                taskId = taskId,
                allowedFromStatuses = allowedFrom.map { it.name },
                status = status.name,
                startedAt = startedAt,
                completedAt = completedAt,
                error = error,
            )
        if (updated == 1) return true
        check(runDao.getById(taskId) != null) {
            "Subagent task does not exist: $taskId"
        }
        return false
    }

    suspend fun reconcileIncompleteRuns(createdBeforeOrAt: Long): Int =
        runDao.markIncompleteAsInterrupted(
            incompleteStatuses =
                listOf(
                    SubagentRunStatus.CREATED.name,
                    SubagentRunStatus.QUEUED.name,
                    SubagentRunStatus.RUNNING.name,
                ),
            createdBeforeOrAt = createdBeforeOrAt,
            interruptedStatus = SubagentRunStatus.INTERRUPTED.name,
            completedAt = System.currentTimeMillis(),
            error = SubagentInterruption.APP_RESTART,
        )

    suspend fun countChildren(parentChatId: String): Int =
        runDao.countByParentChatId(parentChatId)

    suspend fun incrementToolInvocationCountByChildChatId(childChatId: String): Boolean =
        runDao.incrementToolInvocationCountByChildChatId(childChatId) == 1

    suspend fun setArchived(taskId: String, archived: Boolean): Boolean =
        runDao.updateArchivedAt(
            taskId = taskId,
            archivedAt = if (archived) System.currentTimeMillis() else null,
        ) == 1

    suspend fun deleteChildChat(childChatId: String): Boolean =
        database.withTransaction {
            val run = runDao.getByChildChatId(childChatId) ?: return@withTransaction false
            deleteParentChatAndChildren(run.childChatId)
        }

    /**
     * Deletes child chats first so the child foreign key cascades each run before the parent row is
     * removed. The parent foreign key intentionally uses NO_ACTION to make bypassing this order fail.
     */
    suspend fun deleteParentChatAndChildren(parentChatId: String): Boolean =
        database.withTransaction {
            val tree = chatDao.getChatAndSubagentDescendants(parentChatId)
            if (tree.isEmpty() || tree.any { it.locked }) {
                return@withTransaction false
            }
            ChatDeletionGraphPolicy.orderChildFirst(tree).forEach { chat ->
                chatDao.deleteChat(chat.id)
            }
            true
        }

    companion object {
        @Volatile
        private var INSTANCE: SubagentRunRepository? = null

        fun getInstance(context: Context): SubagentRunRepository =
            INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: SubagentRunRepository(
                                AppDatabase.getDatabase(context.applicationContext)
                            )
                            .also { INSTANCE = it }
                }
    }
}

internal object SubagentRunStateMachine {
    private val active =
        setOf(
            SubagentRunStatus.CREATED,
            SubagentRunStatus.QUEUED,
            SubagentRunStatus.RUNNING,
        )
    private val terminal = SubagentRunStatus.entries.toSet() - active

    fun allowedOrigins(target: SubagentRunStatus): Set<SubagentRunStatus> =
        when (target) {
            SubagentRunStatus.CREATED -> emptySet()
            SubagentRunStatus.QUEUED ->
                setOf(SubagentRunStatus.CREATED) + terminal
            SubagentRunStatus.RUNNING ->
                setOf(SubagentRunStatus.CREATED, SubagentRunStatus.QUEUED) + terminal
            SubagentRunStatus.COMPLETED ->
                setOf(SubagentRunStatus.RUNNING)
            SubagentRunStatus.FAILED,
            SubagentRunStatus.CANCELLED,
            SubagentRunStatus.INTERRUPTED -> active
        }
}
