package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.ui.features.chat.components.*
import com.ai.assistance.operit.core.agent.collaboration.CollaborationCoordinator
import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.ui.theme.stoppedAttention
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.core.agent.SubagentCoordinator
import com.ai.assistance.operit.core.tools.ToolExecutionTimingKey
import com.ai.assistance.operit.core.tools.ToolExecutionTimingRepository
import com.ai.assistance.operit.core.tools.ToolExecutionTimingSnapshot
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.SubagentRunStatus
import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.data.repository.SubagentRunRepository
import com.ai.assistance.operit.util.ChatMarkupRegex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PersistedToolExecution(
    val callId: String?,
    val toolName: String,
    val state: ToolExecutionState,
    val durationMs: Long?,
    val success: Boolean,
    val resultText: String,
)

private val FILE_EDIT_TOOL_NAMES = setOf("apply_file", "create_file", "edit_file")

internal data class SubagentTaskRowContent(
    val title: String,
    val summary: String,
)

internal sealed interface SubagentRunLookup {
    data class TaskId(val taskId: String, val parentChatId: String) : SubagentRunLookup

    data class ParentCall(val parentChatId: String, val callId: String) : SubagentRunLookup
}

internal fun resolveSubagentRunLookup(
    requestedTaskId: String?,
    parentChatId: String?,
    callId: String?,
): SubagentRunLookup? {
    val normalizedParentChatId = parentChatId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val normalizedTaskId = requestedTaskId?.trim()?.takeIf(String::isNotEmpty)
    if (normalizedTaskId != null) {
        return SubagentRunLookup.TaskId(normalizedTaskId, normalizedParentChatId)
    }
    val normalizedCallId = callId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return SubagentRunLookup.ParentCall(normalizedParentChatId, normalizedCallId)
}

internal fun buildSubagentTaskRowContent(
    agentName: String?,
    durationText: String?,
    statusText: String,
): SubagentTaskRowContent =
    SubagentTaskRowContent(
        title = agentName?.trim()?.takeIf { it.isNotEmpty() } ?: "subagent",
        summary =
            listOfNotNull(
                    durationText?.trim()?.takeIf { it.isNotEmpty() },
                    statusText.trim().takeIf { it.isNotEmpty() },
                )
                .joinToString(" · "),
    )

@Composable
internal fun rememberPersistedToolExecutions(
    messageKey: Long,
    content: String,
): Map<Int, PersistedToolExecution> {
    com.ai.assistance.operit.ui.common.markdown.LocalTranscriptMarkdownSlice.current?.let {
        return it.document.toolExecutions
    }
    val state = remember(messageKey) {
        androidx.compose.runtime.mutableStateOf<Map<Int, PersistedToolExecution>>(emptyMap())
    }
    androidx.compose.runtime.LaunchedEffect(messageKey, content) {
        state.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            parsePersistedToolExecutions(content)
        }
    }
    return state.value
}

internal fun parsePersistedToolExecutions(content: String): Map<Int, PersistedToolExecution> {
    if (content.isBlank()) return emptyMap()

    val executions = buildMap {
        ChatMarkupRegex.toolResultTagWithAttrs.findAll(content).forEach { match ->
            val attrs = match.groupValues[2]
            val invocationIndex = readFinalInvocationIndex(attrs) ?: return@forEach
            val state =
                readXmlAttribute(attrs, "execution_state")
                    ?.uppercase()
                    ?.let { runCatching { ToolExecutionState.valueOf(it) }.getOrNull() }
                    ?: ToolExecutionState.COMPLETED
            val durationMs = readXmlAttribute(attrs, "duration_ms")?.toLongOrNull()
            val success = readXmlAttribute(attrs, "status").equals("success", ignoreCase = true)
            val toolName = readXmlAttribute(attrs, "name").orEmpty()
            val callId = readXmlAttribute(attrs, "call_id")
            val body = match.groupValues[3]
            val resultText =
                ChatMarkupRegex.contentTag.find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            put(
                invocationIndex,
                PersistedToolExecution(
                    callId = callId,
                    toolName = toolName,
                    state = state,
                    durationMs = durationMs,
                    success = success,
                    resultText = resultText,
                ),
            )
        }
    }
    // Earlier steering builds stored a turn-global index in a message-local segment.
    // Recover only a complete contiguous result set; do not guess for partial batches.
    val offset = executions.keys.minOrNull() ?: return executions
    val toolCount = ChatMarkupRegex.toolCallPattern.findAll(content).count()
    if (offset > 0 && toolCount == executions.size &&
        executions.keys.sorted() == (offset until offset + toolCount).toList()
    ) {
        return executions.mapKeys { (index, _) -> index - offset }
    }
    return executions
}

private fun readXmlAttribute(attrs: String, name: String): String? =
    Regex("""\b${Regex.escape(name)}="([^"]*)"""", RegexOption.IGNORE_CASE)
        .find(attrs)
        ?.groupValues
        ?.getOrNull(1)

private fun readFinalInvocationIndex(attrs: String): Int? {
    if (!readXmlAttribute(attrs, "final").equals("true", ignoreCase = true)) return null
    return readXmlAttribute(attrs, "invocation_index")
        ?.toIntOrNull()
        ?.takeIf { it >= 0 }
}

internal fun shouldRenderStandaloneToolResult(content: String): Boolean {
    val match = ChatMarkupRegex.toolResultTagWithAttrs.matchEntire(content.trim()) ?: return true
    return readFinalInvocationIndex(match.groupValues[2]) == null
}

internal fun resolveLiveToolExecution(
    liveExecution: ToolExecutionTimingSnapshot?,
    persistedExecution: PersistedToolExecution?,
    allowUnmatchedLiveExecution: Boolean,
): ToolExecutionTimingSnapshot? {
    val live = liveExecution ?: return null
    val persistedCallId = persistedExecution?.callId?.takeIf { it.isNotBlank() }
    if (persistedCallId == null) {
        return live.takeIf { allowUnmatchedLiveExecution || it.state.isStillInFlight() }
    }
    return live.takeIf { it.callId == persistedCallId }
}

private fun ToolExecutionState.isStillInFlight(): Boolean =
    this == ToolExecutionState.WAITING_AUTHORIZATION ||
        this == ToolExecutionState.WAITING_EXECUTION ||
        this == ToolExecutionState.RUNNING

internal fun shouldAllowUnmatchedTaskExecution(
    requestedToolName: String?,
    liveExecution: ToolExecutionTimingSnapshot?,
): Boolean =
    requestedToolName == "task" && liveExecution?.toolName in setOf("task", "proxy")

@Composable
internal fun ToolExecutionStatusDisplay(
    timingScopeId: String?,
    invocationIndex: Int,
    persistedExecution: PersistedToolExecution?,
    allowUnmatchedLiveExecution: Boolean,
    enableDialogs: Boolean,
    modifier: Modifier = Modifier,
    requestedToolName: String? = null,
    requestedSubagentName: String? = null,
    requestedSubagentTaskId: String? = null,
    requestedSubagentTask: String? = null,
) {
    val context = LocalContext.current
    val timings by ToolExecutionTimingRepository.timings.collectAsState()
    val liveCandidate = timingScopeId?.let { timings[ToolExecutionTimingKey(it, invocationIndex)] }
    val liveExecution =
        resolveLiveToolExecution(
            liveExecution = liveCandidate,
            persistedExecution = persistedExecution,
            allowUnmatchedLiveExecution = allowUnmatchedLiveExecution || shouldAllowUnmatchedTaskExecution(requestedToolName, liveCandidate),
        )
    val state = liveExecution?.state ?: persistedExecution?.state ?: return
    val toolName =
        requestedToolName
            ?: liveExecution?.toolName
            ?: persistedExecution?.toolName
            ?: return

    if (toolName == "task") {
        val success = liveExecution?.success ?: persistedExecution?.success ?: false
        SubagentTaskStatusDisplay(
            callId = liveExecution?.callId ?: persistedExecution?.callId,
            fallbackState = state,
            fallbackStartedAtElapsedMs = liveExecution?.startedAtElapsedMs,
            fallbackDurationMs = liveExecution?.durationMs ?: persistedExecution?.durationMs,
            executionSuccess = success,
            executionResultText =
                resolveResultText(liveExecution, persistedExecution, success),
            requestedSubagentName = requestedSubagentName,
            requestedSubagentTaskId = requestedSubagentTaskId,
            modifier = modifier,
        )
        return
    }

    val callKind = subagentCallKind(toolName)
    if (callKind != null && !state.isStillInFlight()) {
        val success = liveExecution?.success ?: persistedExecution?.success ?: false
        SubagentCallStatusDisplay(
            callKind = callKind,
            callId = liveExecution?.callId ?: persistedExecution?.callId,
            // The assistant message's time: the agent the call addressed already existed then.
            rowTime = timingScopeId?.toLongOrNull(),
            fallbackState = state,
            executionSuccess = success,
            executionResultText =
                resolveResultText( liveExecution, persistedExecution, success),
            requestedAgentName = requestedSubagentName,
            bodyText = requestedSubagentTask?.takeIf { it.isNotBlank() },
            enableDialogs = enableDialogs,
            modifier = modifier,
        )
        return
    }

    var currentElapsedMs by remember(liveExecution?.startedAtElapsedMs) {
        mutableLongStateOf(resolveElapsedMs(liveExecution))
    }
    LaunchedEffect(liveExecution?.state, liveExecution?.startedAtElapsedMs) {
        while (liveExecution?.state == ToolExecutionState.RUNNING) {
            currentElapsedMs = resolveElapsedMs(liveExecution)
            delay(100L)
        }
    }

    when (state) {
        ToolExecutionState.WAITING_AUTHORIZATION -> {
            ToolPendingStatusRow(
                text = stringResource(R.string.tool_waiting_authorization),
                modifier = modifier,
            )
        }
        ToolExecutionState.WAITING_EXECUTION -> {
            ToolPendingStatusRow(
                text = stringResource(R.string.tool_waiting_execution),
                modifier = modifier,
            )
        }
        ToolExecutionState.RUNNING -> {
            ToolPendingStatusRow(
                text =
                    stringResource(
                        R.string.tool_executing_duration,
                        formatToolExecutionDuration(context, currentElapsedMs),
                    ),
                modifier = modifier,
            )
        }
        ToolExecutionState.COMPLETED -> {
            val durationMs = liveExecution?.durationMs ?: persistedExecution?.durationMs ?: 0L
            val success = liveExecution?.success ?: persistedExecution?.success ?: false
            val resultText =
                resolveResultText(liveExecution, persistedExecution, success)
            ToolExecutionResultDisplay(
                toolName = toolName,
                result = resultText,
                isSuccess = success,
                summaryPrefix = formatToolExecutionDuration(context, durationMs),
                enableDialog = enableDialogs,
                modifier = modifier,
            )
        }
        ToolExecutionState.NOT_EXECUTED -> {
            val success = false
            val resultText = resolveResultText(liveExecution, persistedExecution, success)
            ToolExecutionResultDisplay(
                toolName = liveExecution?.toolName ?: persistedExecution?.toolName.orEmpty(),
                result = resultText,
                isSuccess = false,
                summaryPrefix = stringResource(R.string.tool_not_executed),
                enableDialog = enableDialogs,
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun ToolExecutionResultDisplay(
    toolName: String,
    result: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
    summaryPrefix: String? = null,
    enableDialog: Boolean = true,
) {
    val fileDiff = remember(toolName, result, isSuccess) {
        parseFileDiffResult(toolName, result, isSuccess)
    }
    if (fileDiff != null) {
        FileDiffDisplay(
            diff = fileDiff,
            modifier = modifier,
            summaryPrefix = summaryPrefix,
            enableDialog = enableDialog,
        )
    } else {
        ToolResultDisplay(
            toolName = toolName,
            result = result,
            isSuccess = isSuccess,
            modifier = modifier,
            summaryPrefix = summaryPrefix,
            enableDialog = enableDialog,
        )
    }
}

internal fun parseFileDiffResult(
    toolName: String,
    result: String,
    isSuccess: Boolean,
): FileDiff? {
    if (!isSuccess || toolName !in FILE_EDIT_TOOL_NAMES) return null
    if (!result.contains("<file-diff")) return null

    val fileDiffBlock = Regex("""<file-diff\b[^>]*>[\s\S]*?</file-diff\s*>""")
        .find(result)
        ?.value
        ?: return null
    val path = Regex("""<file-diff\s+[^>]*path="([^"]+)"""")
        .find(fileDiffBlock)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val details = Regex("""\bdetails="([^"]*)"""")
        .find(fileDiffBlock)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val diffContent = Regex("""<!\[CDATA\[([\s\S]*?)]]>""")
        .find(fileDiffBlock)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: return null
    return FileDiff(path = path, diffContent = diffContent, details = details)
}

@Composable
private fun ToolPendingStatusRow(
    text: String,
    modifier: Modifier,
    isSuccess: Boolean = true,
    showStatusIcon: Boolean = false,
    onClick: (() -> Unit)? = null,
    onStopClick: (() -> Unit)? = null,
) {
    CanvasToolResultRow(
        summary = text,
        isSuccess = isSuccess,
        semanticDescription = text,
        modifier = modifier,
        showStatusIcon = showStatusIcon,
        onClick = onClick,
        onStopClick = onStopClick,
        stopDescription = stringResource(R.string.subagent_stop_task),
    )
}

@Composable
private fun SubagentTaskResultRow(
    agentName: String,
    summary: String,
    modifier: Modifier,
    isSuccess: Boolean,
    isInterrupted: Boolean = false,
    onClick: (() -> Unit)? = null,
    onStopClick: (() -> Unit)? = null,
) {
    val accentColor =
        if (isInterrupted) MaterialTheme.colorScheme.stoppedAttention else if (isSuccess) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    val rowClickModifier =
        if (onClick != null) {
            Modifier.clickable(
                role = Role.Button,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .then(rowClickModifier)
                .padding(start = 24.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.SubdirectoryArrowRight,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = agentName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isSuccess || isInterrupted) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onStopClick != null) {
            IconButton(
                onClick = onStopClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.subagent_stop_task),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Whether a spawn row has a failure of its own to report. It records the dispatch and nothing else:
 * what the agent did afterwards belongs to the rows that carry its messages and its result, so the
 * run's later state never reaches this row. Only a call that never started is a failure it owns.
 */
internal fun subagentSpawnCallNeverRan(
    runExists: Boolean,
    fallbackState: ToolExecutionState,
): Boolean = !runExists && fallbackState == ToolExecutionState.NOT_EXECUTED

/**
 * The call side of a v2 collaboration: the row keeps the child's badge and says that the task was
 * handed over instead of repeating the run's live status, and tapping it opens the floating card
 * that shows what the agent returned.
 */
internal enum class SubagentCallKind {
    DISPATCHED,
    MESSAGE_SENT,
    TASK_FOLLOWUP,
}

internal fun subagentCallKind(toolName: String): SubagentCallKind? =
    when (toolName) {
        "spawn_agent" -> SubagentCallKind.DISPATCHED
        "send_message" -> SubagentCallKind.MESSAGE_SENT
        "followup_task" -> SubagentCallKind.TASK_FOLLOWUP
        else -> null
    }

/** Why a collaboration row reports a failure of its own, or null when its call went through. */
internal enum class SubagentCallFailure {
    /** The call never started, so nothing was dispatched or delivered. */
    NEVER_RAN,

    /** The call ran and came back with an execution error. */
    EXECUTION_ERROR,
}

/**
 * Whether a collaboration call has a failure of its own to report. The row records what the call
 * did and nothing else: what the agent did afterwards belongs to the rows that carry its messages
 * and its result, so the run's later state never reaches this row. Only the call's own end does —
 * it never started, or it came back with an execution error while no run stands behind it.
 */
internal fun subagentCallFailure(
    fallbackState: ToolExecutionState,
    executionSuccess: Boolean,
    runOwnedByCall: Boolean,
): SubagentCallFailure? =
    when {
        // A run of its own says the call handed the work over, whatever the call did afterwards.
        runOwnedByCall -> null
        fallbackState == ToolExecutionState.NOT_EXECUTED -> SubagentCallFailure.NEVER_RAN
        fallbackState == ToolExecutionState.COMPLETED && !executionSuccess ->
            SubagentCallFailure.EXECUTION_ERROR
        else -> null
    }

/**
 * The run a collaboration row stands for: the one this call created, or failing that the agent it
 * addressed. A spawn row is the call's own, so its run answers to the call id; a follow-up row
 * writes to an agent that is already running and only knows the name it wrote to, which is the same
 * name the run wears as its path.
 */
internal fun findCallRun(
    runs: List<SubagentRunEntity>,
    callId: String?,
    target: String?,
    agentId: String? = null,
    at: Long? = null,
): SubagentRunEntity? {
    // The call's own generation is found among every run first (archived ones too), and only then
    // checked for being open: filtering archived runs first would hand the row of an archived agent
    // to an older agent that wore the same name.
    val candidates =
        runs.filter {
            it.externalOwnerType == CollaborationCoordinator.OWNER_TYPE
        }
    return findCallGeneration(candidates, callId, target, agentId, at)?.takeIf { it.archivedAt == null }
}

private fun findCallGeneration(
    candidates: List<SubagentRunEntity>,
    callId: String?,
    target: String?,
    agentId: String?,
    at: Long?,
): SubagentRunEntity? {
    val call = callId?.takeIf { it.isNotBlank() }
    if (call != null) {
        candidates.lastOrNull { it.parentToolCallId == call }?.let { return it }
    }
    // The call says which chat it reached; a name can belong to several agents over time, so a
    // chat that is gone links nowhere rather than to another agent of the same name.
    agentId?.takeIf { it.isNotBlank() }?.let { id ->
        return candidates.lastOrNull { it.childChatId == id }
    }
    val name = target?.takeIf { it.isNotBlank() } ?: return null
    val seed = agentBadgeSeed(name)
    val named = candidates.filter {
        val owner = it.externalOwnerId
        !owner.isNullOrBlank() && agentBadgeSeed(owner) == seed
    }
    // Older calls do not say which chat they reached; they keep the agent that wore the name when
    // their message began, and link nowhere when none did.
    return if (at == null) named.lastOrNull { it.archivedAt == null } else collaborationRunAt(named, at)
}

/** The chat a send or follow-up call reports it reached, read from its result. */
internal fun collaborationCallAgentId(resultText: String?): String? =
    resultText?.let { Regex("\"agent_id\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }

/**
 * The call side of a v2 collaboration: the row keeps the addressed agent's badge and says what the
 * call did — a task handed over, a message delivered — instead of repeating the run's live status,
 * and tapping it opens the floating card that shows what the call handed over.
 */
@Composable
private fun SubagentCallStatusDisplay(
    callKind: SubagentCallKind,
    callId: String?,
    rowTime: Long?,
    fallbackState: ToolExecutionState,
    executionSuccess: Boolean,
    executionResultText: String,
    requestedAgentName: String?,
    bodyText: String?,
    enableDialogs: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { SubagentRunRepository.getInstance(context) }
    val chatCore =
        remember(context) {
            ChatRuntimeHolder.getInstance(context.applicationContext).getCore(ChatRuntimeSlot.MAIN)
        }
    val host = LocalSubagentChatHost.current
    val mainChatId by chatCore.currentChatId.collectAsState()
    val parentChatId = host?.parentChatId ?: mainChatId
    val sharedRuns = LocalTranscriptRuns.current?.takeIf { it.chatId == parentChatId }
    val runsFlow =
        remember(parentChatId, repository) {
            parentChatId?.takeIf { it.isNotBlank() }?.let { repository.observeByParentChatId(it) }
        }
    val runs =
        sharedRuns?.runs
            ?: (runsFlow ?: flowOf(emptyList())).collectAsState(initial = emptyList()).value
    val run =
        remember(runs, callId, requestedAgentName, executionResultText, rowTime) {
            findCallRun(
                runs, callId, requestedAgentName,
                agentId = collaborationCallAgentId(executionResultText), at = rowTime,
            )
        }
    val childChatId = run?.childChatId
    val agentPath =
        run?.externalOwnerId?.takeIf { it.isNotBlank() }
            ?: requestedAgentName?.takeIf { it.isNotBlank() }
            ?: "subagent"
    val failure =
        subagentCallFailure(
            fallbackState = fallbackState,
            executionSuccess = executionSuccess,
            runOwnedByCall =
                callId?.isNotBlank() == true && run?.parentToolCallId == callId,
        )
    val openConversation: (() -> Unit)? =
        if (childChatId.isNullOrBlank()) {
            null
        } else {
            {
                host?.open?.invoke(childChatId) ?: chatCore.switchChat(childChatId, scrollToBottom = false)
            }
        }

    Column(modifier = modifier) {
        SubagentAgentCard(
            agentPath = agentPath,
            statusText =
                when (failure) {
                    null -> stringResource(subagentCallKindLabelRes(callKind))
                    SubagentCallFailure.NEVER_RAN -> stringResource(R.string.tool_not_executed)
                    SubagentCallFailure.EXECUTION_ERROR -> stringResource(R.string.execution_failed)
                },
            identity = remember(agentPath) { subagentAgentIdentity(agentPath) },
            statusColor =
                if (failure == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            modifier = Modifier.padding(start = 24.dp, end = 8.dp, bottom = 8.dp),
            chatId = parentChatId,
            childChatId = childChatId,
            body = bodyText,
            failureText =
                if (failure == null) null else executionResultText.takeIf { it.isNotBlank() },
            onOpenConversation = openConversation,
            enableDialogs = enableDialogs,
        )
    }
}

private fun subagentCallKindLabelRes(callKind: SubagentCallKind): Int =
    when (callKind) {
        SubagentCallKind.DISPATCHED -> R.string.subagent_status_dispatched
        SubagentCallKind.MESSAGE_SENT -> R.string.subagent_message_sent
        SubagentCallKind.TASK_FOLLOWUP -> R.string.subagent_task_followup
    }

@Composable
private fun SubagentTaskStatusDisplay(
    callId: String?,
    fallbackState: ToolExecutionState,
    fallbackStartedAtElapsedMs: Long?,
    fallbackDurationMs: Long?,
    executionSuccess: Boolean,
    executionResultText: String,
    requestedSubagentName: String?,
    requestedSubagentTaskId: String?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember(context) { SubagentRunRepository.getInstance(context) }
    val chatCore =
        remember(context) {
            ChatRuntimeHolder.getInstance(context.applicationContext)
                .getCore(ChatRuntimeSlot.MAIN)
        }
    val parentChatId by chatCore.currentChatId.collectAsState()
    val runFlow =
        remember(parentChatId, callId, requestedSubagentTaskId) {
            when (
                val lookup =
                    resolveSubagentRunLookup(
                        requestedTaskId = requestedSubagentTaskId,
                        parentChatId = parentChatId,
                        callId = callId,
                    )
            ) {
                is SubagentRunLookup.TaskId ->
                    repository.observeById(lookup.taskId).map { candidate ->
                        candidate?.takeIf { it.parentChatId == lookup.parentChatId }
                    }
                is SubagentRunLookup.ParentCall ->
                    repository.observeByParentToolCallId(lookup.parentChatId, lookup.callId)
                null -> flowOf(null)
            }
        }
    val sharedRuns = com.ai.assistance.operit.ui.features.chat.components.LocalTranscriptRuns.current
        ?.takeIf { it.chatId == parentChatId }
    val run = if (sharedRuns != null) {
        when (val lookup = resolveSubagentRunLookup(requestedSubagentTaskId, parentChatId, callId)) {
            is SubagentRunLookup.TaskId -> sharedRuns.runs.firstOrNull { it.id == lookup.taskId }
            is SubagentRunLookup.ParentCall -> sharedRuns.runs.lastOrNull {
                it.parentToolCallId == lookup.callId
            }
            null -> null
        }
    } else runFlow.collectAsState(initial = null).value

    if (run == null) {
        var fallbackElapsedMs by
            remember(fallbackStartedAtElapsedMs, fallbackDurationMs) {
                mutableLongStateOf(
                    fallbackDurationMs
                        ?: fallbackStartedAtElapsedMs
                            ?.let {
                                (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L)
                            }
                        ?: 0L
                )
            }
        LaunchedEffect(fallbackState, fallbackStartedAtElapsedMs, fallbackDurationMs) {
            while (
                fallbackState == ToolExecutionState.RUNNING &&
                    fallbackStartedAtElapsedMs != null
            ) {
                fallbackElapsedMs =
                    (SystemClock.elapsedRealtime() - fallbackStartedAtElapsedMs)
                        .coerceAtLeast(0L)
                delay(100L)
            }
        }

        val fallbackStatusText =
            when (fallbackState) {
                ToolExecutionState.WAITING_AUTHORIZATION ->
                    stringResource(R.string.tool_waiting_authorization)
                ToolExecutionState.WAITING_EXECUTION ->
                    stringResource(R.string.tool_waiting_execution)
                ToolExecutionState.RUNNING -> stringResource(R.string.subagent_status_thinking)
                ToolExecutionState.COMPLETED -> stringResource(R.string.subagent_status_completed)
                ToolExecutionState.NOT_EXECUTED -> stringResource(R.string.tool_not_executed)
            }
        val fallbackDurationText =
            if (fallbackDurationMs != null || fallbackStartedAtElapsedMs != null) {
                formatToolExecutionDuration(context, fallbackElapsedMs)
            } else {
                null
            }
        val rowContent =
            buildSubagentTaskRowContent(
                agentName = requestedSubagentName,
                durationText = fallbackDurationText,
                statusText = fallbackStatusText,
            )
        val isFallbackTerminal =
            fallbackState == ToolExecutionState.COMPLETED ||
                fallbackState == ToolExecutionState.NOT_EXECUTED
        val fallbackResult =
            extractSubagentTaskResult(executionResultText)
                .ifBlank { stringResource(R.string.subagent_result_empty) }
        var showFallbackResultDialog by
            remember(callId, fallbackState) {
                androidx.compose.runtime.mutableStateOf(false)
            }

        if (showFallbackResultDialog && isFallbackTerminal) {
            ToolResultDetailDialog(
                toolName = "task",
                result = fallbackResult,
                isSuccess = executionSuccess,
                titleOverride =
                    stringResource(
                        R.string.subagent_result_title,
                        rowContent.title,
                    ),
                metadata = rowContent.summary,
                onDismiss = { showFallbackResultDialog = false },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(fallbackResult))
                },
            )
        }

        SubagentTaskResultRow(
            agentName = rowContent.title,
            summary = rowContent.summary,
            modifier = modifier,
            isSuccess =
                fallbackState != ToolExecutionState.NOT_EXECUTED &&
                    (!isFallbackTerminal || executionSuccess),
            onClick =
                if (isFallbackTerminal) {
                    { showFallbackResultDialog = true }
                } else {
                    null
                },
        )
        return
    }

    val resolvedRun = requireNotNull(run)
    val parentRunsFlow =
        remember(resolvedRun.parentChatId) {
            repository.observeByParentChatId(resolvedRun.parentChatId)
        }
    val parentRuns = sharedRuns?.runs ?: parentRunsFlow.collectAsState(initial = emptyList()).value
    val processingStates by chatCore.inputProcessingStateByChatId.collectAsState()
    val lastToolNames by chatCore.lastToolNameByChatId.collectAsState()
    val lastTurnToolInvocationCounts by
        chatCore.lastTurnToolInvocationCountByChatId.collectAsState()
    val childProcessingState = processingStates[resolvedRun.childChatId]
    val status =
        runCatching { SubagentRunStatus.valueOf(resolvedRun.status) }
            .getOrDefault(SubagentRunStatus.FAILED)

    var nowMs by remember(resolvedRun.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status, resolvedRun.startedAt, resolvedRun.completedAt) {
        while (
            status == SubagentRunStatus.CREATED ||
                status == SubagentRunStatus.QUEUED ||
                status == SubagentRunStatus.RUNNING
        ) {
            nowMs = System.currentTimeMillis()
            delay(100L)
        }
    }
    val startMs = resolvedRun.startedAt ?: resolvedRun.createdAt
    val endMs = resolvedRun.completedAt ?: nowMs
    val durationText =
        formatToolExecutionDuration(context, (endMs - startMs).coerceAtLeast(0L))
    val queuePosition =
        parentRuns
            .asSequence()
            .filter { it.status == SubagentRunStatus.QUEUED.name }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
            .indexOfFirst { it.id == resolvedRun.id }
            .let { if (it >= 0) it + 1 else 1 }
    val currentTool =
        resolveSubagentDisplayedTool(
            childProcessingState = childProcessingState,
            lastToolName = lastToolNames[resolvedRun.childChatId],
        )
    val toolInvocationCount =
        maxOf(
            lastTurnToolInvocationCounts[resolvedRun.childChatId]?.coerceAtLeast(0) ?: 0,
            resolvedRun.toolInvocationCount.coerceAtLeast(0),
        )
    val baseStatusText =
        when (status) {
            SubagentRunStatus.CREATED -> stringResource(R.string.subagent_status_creating)
            SubagentRunStatus.QUEUED ->
                stringResource(R.string.subagent_status_queued, queuePosition)
            SubagentRunStatus.RUNNING ->
                if (currentTool.isNullOrBlank()) {
                    stringResource(R.string.subagent_status_thinking)
                } else {
                    stringResource(R.string.subagent_status_calling_tool, currentTool)
                }
            SubagentRunStatus.COMPLETED ->
                if (toolInvocationCount > 0) {
                    stringResource(
                        R.string.subagent_status_completed_with_tool_count,
                        toolInvocationCount,
                    )
                } else {
                    stringResource(R.string.subagent_status_completed)
                }
            SubagentRunStatus.FAILED -> stringResource(R.string.subagent_status_error)
            SubagentRunStatus.INTERRUPTED ->
                stringResource(R.string.subagent_status_interrupted)
            SubagentRunStatus.CANCELLED -> stringResource(R.string.subagent_status_cancelled)
        }
    val agentName =
        resolvedRun.agentProfileId.ifBlank {
            requestedSubagentName?.takeIf { it.isNotBlank() } ?: "subagent"
        }
    val statusText =
        stringResource(
            R.string.subagent_status_with_agent,
            agentName,
            baseStatusText,
        )
    val rowContent =
        buildSubagentTaskRowContent(
            agentName = agentName,
            durationText = durationText,
            statusText = baseStatusText,
        )
    val isSuccess =
        status != SubagentRunStatus.FAILED &&
            status != SubagentRunStatus.INTERRUPTED &&
            status != SubagentRunStatus.CANCELLED
    val isInterrupted = status == SubagentRunStatus.INTERRUPTED
    // A run the app stopped still owes the user the reason it stopped, the way the chat card does.
    val interruptionNote =
        if (isInterrupted) {
            subagentInterruptionHintRes(SubagentCardStatus.INTERRUPTED, resolvedRun.error)
                ?.let { stringResource(it) }
        } else {
            null
        }
    val isTerminal =
        status == SubagentRunStatus.COMPLETED ||
            status == SubagentRunStatus.FAILED ||
            status == SubagentRunStatus.INTERRUPTED ||
            status == SubagentRunStatus.CANCELLED
    val terminalResult =
        extractSubagentTaskResult(executionResultText)
            .ifBlank { stringResource(R.string.subagent_result_empty) }
    var showResultDialog by remember(resolvedRun.id) { androidx.compose.runtime.mutableStateOf(false) }

    if (showResultDialog && isTerminal) {
        ToolResultDetailDialog(
            toolName = "task",
            result = terminalResult,
            isSuccess = isSuccess,
            stoppedNote = interruptionNote,
            titleOverride =
                stringResource(
                    R.string.subagent_result_title,
                    resolvedRun.title,
                ),
            metadata = "$durationText · $statusText",
            onDismiss = { showResultDialog = false },
            onCopy = {
                clipboardManager.setText(AnnotatedString(terminalResult))
            },
            primaryActionLabel = stringResource(R.string.subagent_open_conversation),
            onPrimaryAction = {
                showResultDialog = false
                chatCore.switchChat(
                    resolvedRun.childChatId,
                    scrollToBottom = false,
                )
            },
        )
    }

    SubagentTaskResultRow(
        agentName = rowContent.title,
        summary = rowContent.summary,
        modifier = modifier,
        isSuccess = isSuccess,
        isInterrupted = isInterrupted,
        onClick = {
            if (isTerminal) {
                showResultDialog = true
            } else {
                chatCore.switchChat(
                    resolvedRun.childChatId,
                    scrollToBottom = false,
                )
            }
        },
        onStopClick =
            if (
                status == SubagentRunStatus.CREATED ||
                    status == SubagentRunStatus.QUEUED ||
                    status == SubagentRunStatus.RUNNING
            ) {
                {
                    coroutineScope.launch {
                        SubagentCoordinator.getInstance(context).cancelTask(resolvedRun.id)
                    }
                }
            } else {
                null
            },
    )
}

internal fun extractSubagentTaskResult(rawResult: String): String {
    val result =
        Regex("""<task_result>([\s\S]*?)</task_result>""", RegexOption.IGNORE_CASE)
            .find(rawResult)
            ?.groupValues
            ?.getOrNull(1)
        ?: Regex("""<task_error>([\s\S]*?)</task_error>""", RegexOption.IGNORE_CASE)
            .find(rawResult)
            ?.groupValues
            ?.getOrNull(1)
        ?: rawResult

    return result
        .trim()
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

internal fun resolveSubagentDisplayedTool(
    childProcessingState: InputProcessingState?,
    lastToolName: String?,
): String? =
    when (childProcessingState) {
        is InputProcessingState.ExecutingTool -> childProcessingState.toolName
        is InputProcessingState.ToolProgress -> childProcessingState.toolName
        is InputProcessingState.ProcessingToolResult -> childProcessingState.toolName
        else -> lastToolName
    }?.takeIf { it.isNotBlank() }

private fun resolveElapsedMs(snapshot: ToolExecutionTimingSnapshot?): Long {
    val startedAt = snapshot?.startedAtElapsedMs ?: return 0L
    return (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
}

private fun resolveResultText(
    liveExecution: ToolExecutionTimingSnapshot?,
    persistedExecution: PersistedToolExecution?,
    success: Boolean,
): String {
    val raw =
        if (persistedExecution != null) {
            persistedExecution.resultText
        } else if (liveExecution != null) {
            if (success) liveExecution.resultText else liveExecution.errorText.ifBlank {
                liveExecution.resultText
            }
        } else {
            ""
        }
    if (success) return raw

    return ChatMarkupRegex.errorTag.find(raw)?.groupValues?.getOrNull(1)?.trim() ?: raw
}

internal fun formatToolExecutionDuration(context: Context, durationMs: Long): String {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    return when {
        safeDurationMs < 1_000L ->
            context.getString(R.string.tool_duration_milliseconds, safeDurationMs)
        safeDurationMs < 60_000L ->
            context.getString(R.string.tool_duration_seconds, safeDurationMs / 1_000.0)
        else -> {
            val totalSeconds = safeDurationMs / 1_000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            context.getString(R.string.tool_duration_minutes_seconds, minutes, seconds)
        }
    }
}
