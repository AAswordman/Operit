package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.ai.assistance.operit.data.model.SubagentRunEntity
import kotlinx.coroutines.flow.Flow

data class SubagentRunOwner(val runId: String, val childChatId: String, val path: String)

@Dao
interface SubagentRunDao {
    @Query("SELECT EXISTS(SELECT 1 FROM pending_subagent_owner_restore WHERE id = 1)")
    suspend fun hasPendingOwnerRestore(): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun markPendingOwnerRestore(state: com.ai.assistance.operit.data.model.PendingSubagentOwnerRestore)

    @Query("DELETE FROM pending_subagent_owner_restore WHERE id = 1")
    suspend fun completeOwnerRestore()

    @Query("SELECT EXISTS(SELECT 1 FROM subagent_runs WHERE externalOwnerType IS NULL AND externalOwnerId IS NULL)")
    suspend fun hasUnownedRuns(): Boolean

    @Query("""
        UPDATE subagent_runs SET externalOwnerType = 'subagent_v2', externalOwnerId = :path
        WHERE id = :runId AND childChatId = :childChatId
            AND externalOwnerType IS NULL AND externalOwnerId IS NULL
    """)
    suspend fun restoreV2Owner(runId: String, childChatId: String, path: String): Int

    @Transaction
    suspend fun restoreV2Owners(owners: List<SubagentRunOwner>) {
        owners.forEach { restoreV2Owner(it.runId, it.childChatId, it.path) }
        completeOwnerRestore()
    }

    @Query("UPDATE subagent_runs SET modelRoundCount = modelRoundCount + 1 WHERE childChatId = :childChatId")
    suspend fun incrementModelRoundCountByChildChatId(childChatId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(run: SubagentRunEntity)

    @Update
    suspend fun update(run: SubagentRunEntity)

    @Query("SELECT * FROM subagent_runs WHERE id = :taskId")
    suspend fun getById(taskId: String): SubagentRunEntity?

    @Query("SELECT * FROM subagent_runs WHERE id = :taskId")
    fun observeById(taskId: String): Flow<SubagentRunEntity?>

    @Query("SELECT * FROM subagent_runs ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<SubagentRunEntity>

    @Query("SELECT * FROM subagent_runs WHERE childChatId = :childChatId")
    suspend fun getByChildChatId(childChatId: String): SubagentRunEntity?

    @Query("SELECT * FROM subagent_runs WHERE childChatId = :childChatId")
    fun observeByChildChatId(childChatId: String): Flow<SubagentRunEntity?>

    @Query(
        "SELECT * FROM subagent_runs " +
            "WHERE parentChatId = :parentChatId AND parentToolCallId = :callId LIMIT 1"
    )
    fun observeByParentToolCallId(
        parentChatId: String,
        callId: String,
    ): Flow<SubagentRunEntity?>

    @Query(
        "SELECT * FROM subagent_runs WHERE parentChatId = :parentChatId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    fun observeByParentChatId(parentChatId: String): Flow<List<SubagentRunEntity>>

    @Query(
        "SELECT * FROM subagent_runs WHERE parentChatId = :parentChatId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getByParentChatId(parentChatId: String): List<SubagentRunEntity>

    @Query("SELECT * FROM subagent_runs WHERE status IN (:statuses) ORDER BY createdAt ASC, id ASC")
    suspend fun getByStatuses(statuses: List<String>): List<SubagentRunEntity>

    @Query("SELECT childChatId FROM subagent_runs WHERE parentChatId = :parentChatId")
    suspend fun getChildChatIds(parentChatId: String): List<String>

    @Query("SELECT COUNT(*) FROM subagent_runs WHERE parentChatId = :parentChatId")
    suspend fun countByParentChatId(parentChatId: String): Int

    @Query(
        """
        UPDATE subagent_runs
        SET toolInvocationCount = toolInvocationCount + 1
        WHERE childChatId = :childChatId
        """
    )
    suspend fun incrementToolInvocationCountByChildChatId(childChatId: String): Int

    @Query("UPDATE subagent_runs SET archivedAt = :archivedAt WHERE id = :taskId")
    suspend fun updateArchivedAt(taskId: String, archivedAt: Long?): Int

    @Query(
        """
        UPDATE subagent_runs
        SET status = :status,
            startedAt = COALESCE(:startedAt, startedAt),
            completedAt = :completedAt,
            error = :error,
            archivedAt = CASE WHEN :status IN ('QUEUED', 'RUNNING') THEN NULL ELSE archivedAt END
        WHERE id = :taskId AND status IN (:allowedFromStatuses)
        """
    )
    suspend fun updateStatus(
        taskId: String,
        allowedFromStatuses: List<String>,
        status: String,
        startedAt: Long? = null,
        completedAt: Long? = null,
        error: String? = null,
    ): Int

    @Query(
        """
        UPDATE subagent_runs
        SET status = :interruptedStatus,
            completedAt = :completedAt,
            error = :error
        WHERE status IN (:incompleteStatuses) AND createdAt <= :createdBeforeOrAt
        """
    )
    suspend fun markIncompleteAsInterrupted(
        incompleteStatuses: List<String>,
        createdBeforeOrAt: Long,
        interruptedStatus: String,
        completedAt: Long,
        error: String,
    ): Int

    @Query("DELETE FROM subagent_runs WHERE id = :taskId")
    suspend fun deleteById(taskId: String): Int
}
