package com.ai.assistance.operit.data.repository

import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatKind
import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.data.model.SubagentRunStatus
import java.util.UUID

internal data class ChatBranchCopyResult(
    val branch: ChatEntity,
    val copiedMessageCount: Int,
    val copiedSubagentCount: Int,
)

/**
 * Copies one persisted conversation branch together with the Subagent chats referenced by the
 * copied transcript.
 *
 * Tool call IDs are scoped by parent chat, so they remain unchanged. Chat and run IDs are always
 * regenerated, and persisted task references are rewritten to keep the branch graph independent.
 */
internal class ChatBranchRepository(
    private val database: AppDatabase,
) {
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()
    private val chatContentDao = database.chatContentDao()
    private val messageVariantDao = database.messageVariantDao()
    private val subagentRunDao = database.subagentRunDao()

    suspend fun copyBranch(
        sourceChatId: String,
        branch: ChatEntity,
        upToTimestampInclusive: Long?,
    ): ChatBranchCopyResult =
        database.withTransaction {
            requireNotNull(chatDao.getChatById(sourceChatId)) {
                "Source chat does not exist: $sourceChatId"
            }
            chatDao.insertChat(branch)

            val copiedMessageCount =
                messageDao.countMessagesForChatUpToTimestamp(
                    sourceChatId,
                    upToTimestampInclusive,
                )
            if (copiedMessageCount > 0) {
                copyMessagesAndVariants(
                    sourceChatId = sourceChatId,
                    targetChatId = branch.id,
                    upToTimestampInclusive = upToTimestampInclusive,
                )
            }
            val copiedSubagentCount =
                cloneReferencedSubagentTree(
                    sourceParentChatId = sourceChatId,
                    targetParentChatId = branch.id,
                    upToTimestampInclusive = upToTimestampInclusive,
                    copiedSourceChatIds = mutableSetOf(sourceChatId),
                )

            ChatBranchCopyResult(
                branch = requireNotNull(chatDao.getChatById(branch.id)),
                copiedMessageCount = copiedMessageCount,
                copiedSubagentCount = copiedSubagentCount,
            )
        }

    private suspend fun cloneReferencedSubagentTree(
        sourceParentChatId: String,
        targetParentChatId: String,
        upToTimestampInclusive: Long?,
        copiedSourceChatIds: MutableSet<String>,
    ): Int {
        val parentRuns = subagentRunDao.getByParentChatId(sourceParentChatId)
        if (parentRuns.isEmpty()) return 0
        val referencedCallIds =
            extractPersistedToolCallIds(
                buildList {
                    addAll(
                        chatContentDao
                            .getMessagesForChatInRangeAsc(
                                chatId = sourceParentChatId,
                                afterTimestampExclusive = null,
                                beforeTimestampExclusive = null,
                                upToTimestampInclusive = upToTimestampInclusive,
                            )
                            .map { it.content }
                    )
                    addAll(
                        chatContentDao
                            .getVariantsForChat(sourceParentChatId)
                            .asSequence()
                            .filter {
                                upToTimestampInclusive == null ||
                                    it.messageTimestamp <= upToTimestampInclusive
                            }
                            .map { it.content }
                    )
                }
            )
        if (referencedCallIds.isEmpty()) return 0

        var copiedCount = 0
        val taskIdRemap = mutableMapOf<String, String>()
        parentRuns
            .filter { run -> run.parentToolCallId in referencedCallIds }
            .forEach { sourceRun ->
                val sourceChild = requireNotNull(chatDao.getChatById(sourceRun.childChatId)) {
                    "Subagent child chat does not exist: ${sourceRun.childChatId}"
                }
                if (!copiedSourceChatIds.add(sourceChild.id)) {
                    return@forEach
                }

                val childCopy =
                    sourceChild.copy(
                        id = UUID.randomUUID().toString(),
                        parentChatId = targetParentChatId,
                        chatKind = ChatKind.SUBAGENT.name,
                    )
                chatDao.insertChat(childCopy)
                copyMessagesAndVariants(
                    sourceChatId = sourceChild.id,
                    targetChatId = childCopy.id,
                    upToTimestampInclusive = null,
                )
                val copiedRun =
                    sourceRun.copyForBranch(
                        branchTaskId = UUID.randomUUID().toString(),
                        branchParentChatId = targetParentChatId,
                        branchChildChatId = childCopy.id,
                        snapshotAt = System.currentTimeMillis(),
                    )
                subagentRunDao.insert(copiedRun)
                taskIdRemap[sourceRun.id] = copiedRun.id
                copiedCount++
                copiedCount +=
                    cloneReferencedSubagentTree(
                        sourceParentChatId = sourceChild.id,
                        targetParentChatId = childCopy.id,
                        upToTimestampInclusive = null,
                        copiedSourceChatIds = copiedSourceChatIds,
                    )
            }
        remapCopiedTaskIds(targetParentChatId, taskIdRemap)
        return copiedCount
    }

    private suspend fun remapCopiedTaskIds(
        targetChatId: String,
        taskIdRemap: Map<String, String>,
    ) {
        if (taskIdRemap.isEmpty()) return
        chatContentDao.getMessagesForChat(targetChatId).forEach { message ->
            val content = remapPersistedTaskIds(message.content, taskIdRemap)
            if (content != message.content) {
                messageDao.updateMessage(message.copy(content = content))
            }
        }
        chatContentDao.getVariantsForChat(targetChatId).forEach { variant ->
            val content = remapPersistedTaskIds(variant.content, taskIdRemap)
            if (content != variant.content) {
                messageVariantDao.updateVariant(variant.copy(content = content))
            }
        }
    }

    private suspend fun copyMessagesAndVariants(
        sourceChatId: String,
        targetChatId: String,
        upToTimestampInclusive: Long?,
    ) {
        messageDao.copyMessagesToChat(
            sourceChatId = sourceChatId,
            targetChatId = targetChatId,
            upToTimestampInclusive = upToTimestampInclusive,
        )
        messageVariantDao.copyVariantsToChat(
            sourceChatId = sourceChatId,
            targetChatId = targetChatId,
            upToTimestampInclusive = upToTimestampInclusive,
        )
    }
}

private val ACTIVE_SUBAGENT_RUN_STATUS_NAMES =
    setOf(
        SubagentRunStatus.CREATED.name,
        SubagentRunStatus.QUEUED.name,
        SubagentRunStatus.RUNNING.name,
    )

private const val BRANCH_SNAPSHOT_INTERRUPTED_ERROR =
    "This Subagent run was interrupted because its parent chat was branched."

internal fun SubagentRunEntity.copyForBranch(
    branchTaskId: String,
    branchParentChatId: String,
    branchChildChatId: String,
    snapshotAt: Long,
): SubagentRunEntity {
    val activeSnapshot = status in ACTIVE_SUBAGENT_RUN_STATUS_NAMES
    return copy(
        id = branchTaskId,
        parentChatId = branchParentChatId,
        childChatId = branchChildChatId,
        status = if (activeSnapshot) SubagentRunStatus.INTERRUPTED.name else status,
        completedAt = if (activeSnapshot) snapshotAt else completedAt,
        error = if (activeSnapshot) BRANCH_SNAPSHOT_INTERRUPTED_ERROR else error,
    )
}

private val persistedToolCallIdAttribute =
    Regex(
        """\bcall_id\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

internal fun extractPersistedToolCallIds(contents: Iterable<String>): Set<String> =
    contents
        .asSequence()
        .flatMap { content ->
            persistedToolCallIdAttribute
                .findAll(content)
                .mapNotNull { match -> match.groupValues.getOrNull(1)?.takeIf(String::isNotBlank) }
        }
        .toSet()

internal fun remapPersistedTaskIds(
    content: String,
    taskIdRemap: Map<String, String>,
): String =
    taskIdRemap.entries.fold(content) { current, (oldTaskId, newTaskId) ->
        val escapedTaskId = Regex.escape(oldTaskId)
        val taskResultRemapped =
            Regex(
                """(<task\b[^>]*\bid\s*=\s*["'])$escapedTaskId(["'])""",
                RegexOption.IGNORE_CASE,
            ).replace(current) { match ->
                match.groupValues[1] + newTaskId + match.groupValues[2]
            }
        Regex(
            """(<param\s+name\s*=\s*["']task_id["']\s*>\s*)$escapedTaskId(\s*</param\s*>)""",
            RegexOption.IGNORE_CASE,
        ).replace(taskResultRemapped) { match ->
            match.groupValues[1] + newTaskId + match.groupValues[2]
        }
    }
