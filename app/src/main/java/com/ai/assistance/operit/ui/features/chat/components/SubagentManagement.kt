package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.data.model.SubagentRunStatus
import com.ai.assistance.operit.ui.features.chat.components.part.formatToolExecutionDuration
import com.ai.assistance.operit.ui.features.chat.components.part.resolveSubagentDisplayedTool
import kotlinx.coroutines.delay

internal enum class SubagentListFilter {
    ALL,
    RUNNING,
    QUEUED,
    COMPLETED,
    ARCHIVED,
}

internal fun filterAndSortSubagentRuns(
    runs: List<SubagentRunEntity>,
    filter: SubagentListFilter,
    query: String = "",
): List<SubagentRunEntity> {
    val normalizedQuery = query.trim()
    val filtered =
        runs.filter { run ->
            val status = run.status.toSubagentRunStatus()
            val matchesFilter =
                when (filter) {
                    SubagentListFilter.ALL -> run.archivedAt == null
                    SubagentListFilter.RUNNING ->
                        run.archivedAt == null &&
                            (status == SubagentRunStatus.CREATED ||
                                status == SubagentRunStatus.RUNNING)
                    SubagentListFilter.QUEUED ->
                        run.archivedAt == null && status == SubagentRunStatus.QUEUED
                    SubagentListFilter.COMPLETED ->
                        run.archivedAt == null && status == SubagentRunStatus.COMPLETED
                    SubagentListFilter.ARCHIVED -> run.archivedAt != null
                }
            matchesFilter &&
                (normalizedQuery.isEmpty() ||
                    run.agentProfileId.contains(normalizedQuery, ignoreCase = true) ||
                    run.title.contains(normalizedQuery, ignoreCase = true))
        }
    return filtered.sortedWith(
        compareBy<SubagentRunEntity> {
                if (filter == SubagentListFilter.ARCHIVED) {
                    0
                } else {
                    when (it.status.toSubagentRunStatus()) {
                        SubagentRunStatus.CREATED,
                        SubagentRunStatus.RUNNING -> 0
                        SubagentRunStatus.QUEUED -> 1
                        else -> 2
                    }
                }
            }
            .thenByDescending { it.archivedAt ?: it.createdAt }
            .thenByDescending { it.id }
    )
}

internal fun SubagentRunEntity.isActiveSubagentRun(): Boolean =
    when (status.toSubagentRunStatus()) {
        SubagentRunStatus.CREATED,
        SubagentRunStatus.QUEUED,
        SubagentRunStatus.RUNNING -> true
        else -> false
    }

private fun String.toSubagentRunStatus(): SubagentRunStatus =
    runCatching { SubagentRunStatus.valueOf(this) }.getOrDefault(SubagentRunStatus.FAILED)

@Composable
internal fun SubagentManageButton(
    hasActiveRun: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp),
    ) {
        Box {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = stringResource(R.string.subagent_manage),
                modifier = Modifier.size(20.dp),
            )
            if (hasActiveRun) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubagentSwitcherSheet(
    runs: List<SubagentRunEntity>,
    currentChildChatId: String,
    onSelect: (SubagentRunEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sortedRuns = remember(runs) {
        filterAndSortSubagentRuns(runs, SubagentListFilter.ALL)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.subagent_switch_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(
                items = sortedRuns,
                key = { it.id },
            ) { run ->
                SubagentRunRow(
                    run = run,
                    selected = run.childChatId == currentChildChatId,
                    showActions = false,
                    onClick = { onSelect(run) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubagentManagementDialog(
    parentTitle: String,
    runs: List<SubagentRunEntity>,
    currentChildChatId: String?,
    onSelect: (SubagentRunEntity) -> Unit,
    onStop: (SubagentRunEntity) -> Unit,
    onArchive: (SubagentRunEntity) -> Unit,
    onRestore: (SubagentRunEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(SubagentListFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    val visibleRuns = remember(runs, selectedFilter, searchQuery) {
        filterAndSortSubagentRuns(runs, selectedFilter, searchQuery)
    }
    val counts =
        remember(runs) {
            SubagentListFilter.entries.associateWith { filter ->
                filterAndSortSubagentRuns(runs, filter).size
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.subagent_manage),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = parentTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            placeholder = {
                                Text(stringResource(R.string.subagent_search_hint))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription =
                                                stringResource(R.string.clear_search),
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                        )
                    }
                    item(key = "filters") {
                        SubagentFilterRow(
                            selectedFilter = selectedFilter,
                            counts = counts,
                            onSelect = { selectedFilter = it },
                        )
                        HorizontalDivider()
                    }
                    if (visibleRuns.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text =
                                    if (searchQuery.isNotBlank()) {
                                        stringResource(R.string.subagent_search_empty)
                                    } else if (selectedFilter == SubagentListFilter.ARCHIVED) {
                                        stringResource(R.string.subagent_archived_empty)
                                    } else {
                                        stringResource(R.string.subagent_manage_empty)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        items(
                            items = visibleRuns,
                            key = { it.id },
                        ) { run ->
                            SubagentRunRow(
                                run = run,
                                selected = run.childChatId == currentChildChatId,
                                showActions = true,
                                onClick = { onSelect(run) },
                                onStop = { onStop(run) },
                                onArchive = { onArchive(run) },
                                onRestore = { onRestore(run) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubagentFilterRow(
    selectedFilter: SubagentListFilter,
    counts: Map<SubagentListFilter, Int>,
    onSelect: (SubagentListFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items =
                SubagentListFilter.entries.filter { filter ->
                    filter != SubagentListFilter.ARCHIVED || (counts[filter] ?: 0) > 0
                },
            key = { it.name },
        ) { filter ->
            val count = counts[filter] ?: 0
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onSelect(filter) },
                label = {
                    Text("${subagentFilterLabel(filter)} $count")
                }
            )
        }
    }
}

@Composable
private fun subagentFilterLabel(filter: SubagentListFilter): String =
    when (filter) {
        SubagentListFilter.ALL -> stringResource(R.string.subagent_filter_all)
        SubagentListFilter.RUNNING -> stringResource(R.string.subagent_filter_running)
        SubagentListFilter.QUEUED -> stringResource(R.string.subagent_filter_queued)
        SubagentListFilter.COMPLETED -> stringResource(R.string.subagent_filter_completed)
        SubagentListFilter.ARCHIVED -> stringResource(R.string.subagent_filter_archived)
    }

@Composable
private fun SubagentRunRow(
    run: SubagentRunEntity,
    selected: Boolean,
    showActions: Boolean,
    onClick: () -> Unit,
    onStop: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val chatCore =
        remember(context) {
            ChatRuntimeHolder.getInstance(context.applicationContext)
                .getCore(ChatRuntimeSlot.MAIN)
        }
    val processingStates by chatCore.inputProcessingStateByChatId.collectAsState()
    val lastToolNames by chatCore.lastToolNameByChatId.collectAsState()
    val lastTurnToolInvocationCounts by
        chatCore.lastTurnToolInvocationCountByChatId.collectAsState()
    val status = run.status.toSubagentRunStatus()
    var nowMs by remember(run.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) {
        while (run.isActiveSubagentRun()) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val currentTool =
        resolveSubagentDisplayedTool(
            childProcessingState = processingStates[run.childChatId],
            lastToolName = lastToolNames[run.childChatId],
        )
    val toolCount =
        maxOf(
            run.toolInvocationCount,
            lastTurnToolInvocationCounts[run.childChatId] ?: 0,
        )
    val duration =
        formatToolExecutionDuration(
            context,
            ((run.completedAt ?: nowMs) - (run.startedAt ?: run.createdAt))
                .coerceAtLeast(0L),
        )
    val statusText =
        subagentRunStatusText(
            status = status,
            currentTool = currentTool,
            toolCount = toolCount,
        )
    val statusColor =
        when (status) {
            SubagentRunStatus.FAILED,
            SubagentRunStatus.INTERRUPTED -> MaterialTheme.colorScheme.error
            SubagentRunStatus.RUNNING -> MaterialTheme.colorScheme.primary
            SubagentRunStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    var showMenu by remember(run.id) { mutableStateOf(false) }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        Color.Transparent
                    }
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = run.agentProfileId.ifBlank { "subagent" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = run.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$statusText · $duration",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showActions) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.subagent_more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        if (run.isActiveSubagentRun()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subagent_stop_task)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onStop?.invoke()
                                },
                            )
                        } else if (run.archivedAt == null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subagent_archive)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Archive, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onArchive?.invoke()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subagent_restore)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onRestore?.invoke()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun subagentRunStatusText(
    status: SubagentRunStatus,
    currentTool: String?,
    toolCount: Int,
): String =
    when (status) {
        SubagentRunStatus.CREATED -> stringResource(R.string.subagent_status_creating)
        SubagentRunStatus.QUEUED -> stringResource(R.string.subagent_filter_queued)
        SubagentRunStatus.RUNNING ->
            if (currentTool.isNullOrBlank()) {
                stringResource(R.string.subagent_status_thinking)
            } else {
                stringResource(R.string.subagent_status_calling_tool, currentTool)
            }
        SubagentRunStatus.COMPLETED ->
            if (toolCount > 0) {
                stringResource(
                    R.string.subagent_status_completed_with_tool_count,
                    toolCount,
                )
            } else {
                stringResource(R.string.subagent_status_completed)
            }
        SubagentRunStatus.CANCELLED -> stringResource(R.string.subagent_status_cancelled)
        SubagentRunStatus.FAILED,
        SubagentRunStatus.INTERRUPTED -> stringResource(R.string.subagent_status_error)
    }

@Composable
internal fun SubagentHeaderTitle(
    parentTitle: String,
    agentName: String,
    taskTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = parentTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = agentName.ifBlank { "subagent" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = stringResource(R.string.subagent_switch_title),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = taskTitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
