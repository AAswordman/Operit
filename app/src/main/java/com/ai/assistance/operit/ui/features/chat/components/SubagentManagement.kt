package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.ui.theme.stoppedAttention
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.features.chat.components.part.formatToolExecutionDuration
import com.ai.assistance.operit.ui.features.chat.components.part.resolveSubagentDisplayedTool
import kotlinx.coroutines.delay

internal enum class SubagentListFilter {
    ALL,
    RUNNING,
    QUEUED,
    COMPLETED,
    INTERRUPTED, ERROR,
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
                    SubagentListFilter.INTERRUPTED -> run.archivedAt == null && status in setOf(SubagentRunStatus.INTERRUPTED, SubagentRunStatus.CANCELLED)
                    SubagentListFilter.ERROR -> run.archivedAt == null && status == SubagentRunStatus.FAILED
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
            if (sortedRuns.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.subagent_manage_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
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
    var chosenFilter by rememberSaveable { mutableStateOf<SubagentListFilter?>(null) }
    val initialFilter = if (runs.isNotEmpty() && runs.all { it.archivedAt != null }) {
        SubagentListFilter.ARCHIVED
    } else {
        SubagentListFilter.ALL
    }
    val selectedFilter = chosenFilter ?: initialFilter
    // Run records arrive asynchronously. Pick the first populated view once, then keep the
    // user's selection even if the underlying run statuses change.
    LaunchedEffect(runs) {
        if (chosenFilter == null && runs.isNotEmpty()) chosenFilter = initialFilter
    }
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
                            onSelect = { chosenFilter = it },
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
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.subagent_kind_foreground), style = MaterialTheme.typography.labelLarge)
        SubagentStatusFilterButton(selectedFilter, counts, onSelect)
    }
}

@Composable
private fun SubagentStatusFilterButton(
    selectedStatus: SubagentListFilter,
    statusCounts: Map<SubagentListFilter, Int>,
    onSelectStatus: (SubagentListFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedStatus != SubagentListFilter.ALL,
            onClick = { expanded = true },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = {
                Text(
                    text = subagentFilterLabel(selectedStatus),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = (statusCounts[selectedStatus] ?: 0).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.subagent_filter_menu),
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SubagentListFilter.entries.forEach { status ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = subagentFilterLabel(status),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = (statusCounts[status] ?: 0).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        if (status == selectedStatus) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectStatus(status)
                    },
                )
            }
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
        SubagentListFilter.INTERRUPTED -> stringResource(R.string.subagent_status_interrupted)
        SubagentListFilter.ERROR -> stringResource(R.string.subagent_status_error)
        SubagentListFilter.ARCHIVED -> stringResource(R.string.subagent_filter_archived)
    }

/**
 * A named v2 agent shows its own badge here too, the way the chat cards do; the auto review run and
 * anything that is not a named v2 agent keep the plain status mark.
 */
internal fun subagentRunShowsAgentIdentity(isAutoReview: Boolean, isV2Agent: Boolean): Boolean =
    !isAutoReview && isV2Agent

/** The agent's own accent for a named agent, the run status colour otherwise. */
internal fun subagentRunBadgeColor(
    showsAgentIdentity: Boolean,
    statusColor: Color,
    accent: Color,
): Color = if (showsAgentIdentity) accent else statusColor

/**
 * Only a v2 run names itself by its canonical agent path; another feature's owner id (the reading
 * companion's run id) is not a name, so it falls back to the version and the profile id.
 */
internal fun subagentRunTitle(
    isV2Agent: Boolean,
    externalOwnerId: String?,
    agentProfileId: String,
): String {
    val version = if (isV2Agent) "v2" else "v1"
    val ownerName = if (isV2Agent) externalOwnerId?.takeIf { it.isNotBlank() } else null
    return ownerName ?: "$version · ${agentProfileId.ifBlank { "subagent" }}"
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
    val childProcessingState = perChatValue(chatCore.inputProcessingStateByChatId, run.childChatId)
    val childLastToolName = perChatValue(chatCore.lastToolNameByChatId, run.childChatId)
    val childToolInvocations = perChatValue(chatCore.lastTurnToolInvocationCountByChatId, run.childChatId) ?: 0
    val isV2Agent = run.externalOwnerType == com.ai.assistance.operit.core.agent.collaboration.CollaborationCoordinator.OWNER_TYPE
    val agentIdentity = remember(run.externalOwnerId, run.agentProfileId) { subagentAgentIdentity(run.externalOwnerId ?: run.agentProfileId) }
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
            childProcessingState = childProcessingState,
            lastToolName = childLastToolName,
        )
    val toolCount = maxOf(run.toolInvocationCount, childToolInvocations)
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
        )
    val statusColor =
        when (status) {
            SubagentRunStatus.FAILED -> MaterialTheme.colorScheme.error
            SubagentRunStatus.INTERRUPTED, SubagentRunStatus.CANCELLED -> MaterialTheme.colorScheme.stoppedAttention
            SubagentRunStatus.RUNNING -> MaterialTheme.colorScheme.primary
            SubagentRunStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    // A v2 agent keeps its own badge colour here too, so the list reads like the chat cards do.
    val showsAgentIdentity = subagentRunShowsAgentIdentity(false, isV2Agent)
    val badgeColor = subagentRunBadgeColor(showsAgentIdentity, statusColor, agentIdentity.accent)
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
                        .background(badgeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (showsAgentIdentity) agentIdentity.icon else Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subagentRunTitle(isV2Agent, run.externalOwnerId, run.agentProfileId),
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
                    text =
                        listOfNotNull(
                                statusText,
                                subagentCardStatsText(duration, toolCount, run.modelRoundCount),
                            )
                            .joinToString(" · "),
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
        // The tool count and the rest of the run statistics follow as their own line fragment.
        SubagentRunStatus.COMPLETED -> stringResource(R.string.subagent_status_completed)
        SubagentRunStatus.CANCELLED -> stringResource(R.string.subagent_status_cancelled)
        SubagentRunStatus.FAILED -> stringResource(R.string.subagent_status_error)
        SubagentRunStatus.INTERRUPTED ->
            stringResource(R.string.subagent_status_interrupted)
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
