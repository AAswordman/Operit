package com.ai.assistance.operit.ui.features.chat.components.part

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
        return live.takeIf { allowUnmatchedLiveExecution }
    }
    return live.takeIf { it.callId == persistedCallId }
}

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
) {
    val context = LocalContext.current
    val timings by ToolExecutionTimingRepository.timings.collectAsState()
    val liveExecution =
        resolveLiveToolExecution(
            liveExecution =
                timingScopeId
                    ?.let { scopeId -> timings[ToolExecutionTimingKey(scopeId, invocationIndex)] },
            persistedExecution = persistedExecution,
            allowUnmatchedLiveExecution = allowUnmatchedLiveExecution,
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
    onClick: (() -> Unit)? = null,
    onStopClick: (() -> Unit)? = null,
) {
    val accentColor =
        if (isSuccess) {
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
                    if (isSuccess) {
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
    val run by runFlow.collectAsState(initial = null)

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
    val parentRuns by parentRunsFlow.collectAsState(initial = emptyList())
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
            SubagentRunStatus.FAILED,
            SubagentRunStatus.INTERRUPTED -> stringResource(R.string.subagent_status_error)
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
