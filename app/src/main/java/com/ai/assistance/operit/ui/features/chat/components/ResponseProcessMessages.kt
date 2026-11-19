package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.ChatMessageProcessMetadata
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.services.core.isContinuedAcrossSummaries
import com.ai.assistance.operit.services.core.TranscriptStructure
import com.ai.assistance.operit.ui.common.markdown.LocalResponseProcessExpanded
import com.ai.assistance.operit.ui.common.markdown.ResponseActivityHeader

internal data class ResponseProcessGroup(
    val key: Long,
    val firstIndex: Int,
    val finalIndex: Int,
    val durationMs: Long,
    val headerIndex: Int = firstIndex,
)

/** One reply process and the rows it wrote, before compaction halves are merged back together. */
private data class ResponseProcessSegment(
    val group: ResponseProcessGroup,
    val members: List<Int>,
)

/** Agent input cards are part of the AI process; human steering stays outside the fold. */
internal fun responseProcessGroups(messages: List<ChatMessage>): Map<Int, ResponseProcessGroup> {
    val completed = mutableListOf<ResponseProcessSegment>()
    val pending = mutableListOf<Int>()
    val leadingCards = mutableListOf<Int>()
    messages.forEachIndexed { index, message ->
        if (message.sender != "ai") {
            if (pending.isEmpty()) {
                if (message.displayMode.isCollaborationEvent) leadingCards += index
                else leadingCards.clear()
            }
            return@forEachIndexed
        }
        if (pending.isNotEmpty() && messages[pending.first()].sentAt != message.sentAt) {
            pending.clear()
            leadingCards.clear()
        }
        if (message.sentAt > 0L &&
            message.displayMode == ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE
        ) {
            pending += index
        } else {
            if ((pending.isNotEmpty() || leadingCards.isNotEmpty()) &&
                message.sentAt > 0L &&
                message.displayMode == ChatMessageDisplayMode.NORMAL &&
                message.content.isNotBlank() &&
                message.completedAt > 0L && message.contentStream == null
            ) {
                val group =
                    ResponseProcessGroup(
                        key = message.sentAt,
                        firstIndex = leadingCards.firstOrNull() ?: pending.first(),
                        finalIndex = index,
                        durationMs = message.waitDurationMs + message.outputDurationMs,
                        headerIndex = pending.firstOrNull() ?: index,
                    )
                // Members are the rows this run wrote. A row that only sits in the gap between two
                // halves — a card the summary pushed out, or the summary itself — belongs to
                // neither half, so it stays visible outside the fold.
                completed +=
                    ResponseProcessSegment(
                        group,
                        (group.firstIndex..group.finalIndex).filter { member ->
                            messages[member].sender == "ai" ||
                                messages[member].displayMode.isCollaborationEvent
                        },
                    )
            }
            pending.clear()
            leadingCards.clear()
        }
    }
    // Compaction is written inside the run it reports and the run resumes right after it; an agent
    // input card is that same run reporting its subagent work. Both keep one fold and add up its
    // time instead of cutting the reply process in half there.
    val merged = mutableListOf<ResponseProcessSegment>()
    completed.forEach { segment ->
        val group = segment.group
        val previous = merged.lastOrNull()
        val between = previous?.let { (it.group.finalIndex + 1 until group.firstIndex).toList() }
        val continued =
            between != null &&
                isContinuedAcrossSummaries(
                    between.map { messages[it].sender },
                    between.map { messages[it].displayMode.name },
                )
        if (previous != null && continued) {
            merged[merged.lastIndex] =
                ResponseProcessSegment(
                    previous.group.copy(
                        finalIndex = group.finalIndex,
                        durationMs = previous.group.durationMs + group.durationMs,
                    ),
                    previous.members + segment.members,
                )
        } else {
            merged += segment
        }
    }
    val result = mutableMapOf<Int, ResponseProcessGroup>()
    merged.forEach { segment -> segment.members.forEach { result[it] = segment.group } }
    return result
}

internal class ResponseProcessState(
    val groups: Map<Int, ResponseProcessGroup>,
    val isExpanded: (Long) -> Boolean,
    val toggle: (Long) -> Unit,
    val expand: (Long) -> Unit,
    val hasMore: (Long) -> Boolean = { false },
    val loadMore: suspend (Long) -> Unit = {},
    val showSummary: Boolean = true,
    val ready: Boolean = true,
)

/** Map complete transcript structure to a bounded body window, including card-only pages. */
internal fun windowResponseProcessGroups(
    messages: List<ChatMessage>,
    metadata: List<ChatMessageProcessMetadata>,
): Map<Int, ResponseProcessGroup> =
    if (metadata.isEmpty()) responseProcessGroups(messages)
    else projectWindowProcessState(messages, buildWindowStructure(messages, metadata)).first

private fun buildWindowStructure(
    messages: List<ChatMessage>,
    metadata: List<ChatMessageProcessMetadata>,
): TranscriptStructure {
    val structuralMessages = metadata.associateBy { it.timestamp }.toMutableMap()
    // A live/finalized row in memory is newer than the persisted metadata snapshot.
    messages.forEach { message ->
        structuralMessages[message.timestamp] = ChatMessageProcessMetadata(
            message.timestamp, message.sender, message.displayMode.name, message.sentAt,
            if (message.contentStream == null) message.completedAt else 0L,
            message.waitDurationMs, message.outputDurationMs,
            hasContent = message.content.isNotBlank(),
        )
    }
    return TranscriptStructure(
        structuralMessages.values.sortedBy { it.timestamp })
}

private fun projectWindowProcessState(
    messages: List<ChatMessage>,
    structure: TranscriptStructure,
): Pair<Map<Int, ResponseProcessGroup>, Set<Long>> {
    val groupsByTimestamp = structure.turnByTimestamp
    val windowMembers = messages.indices.filter { messages[it].timestamp in groupsByTimestamp }
        .groupBy { groupsByTimestamp.getValue(messages[it].timestamp) }
    val groups = buildMap {
        windowMembers.forEach { (group, members) ->
            val visible = ResponseProcessGroup(
                key = group.key,
                durationMs = group.durationMs,
                firstIndex = members.first(),
                finalIndex = messages.indexOfFirst { it.timestamp == group.finalTimestamp },
                headerIndex = members.firstOrNull { messages[it].sender == "ai" } ?: -1,
            )
            members.forEach { put(it, visible) }
        }
    }
    val present = messages.mapTo(hashSetOf()) { it.timestamp }
    val missing = windowMembers.keys.filter { turn -> turn.memberTimestamps.any { it !in present } }
        .mapTo(hashSetOf()) { it.key }
    return groups to missing
}

@Composable
internal fun rememberResponseProcessState(
    messages: List<ChatMessage>,
    chatId: String?,
    enabled: Boolean = true,
    metadata: List<ChatMessageProcessMetadata> = emptyList(),
    loadProcess: (suspend (Long) -> Unit)? = null,
): ResponseProcessState {
    val context = LocalContext.current
    val preferences = remember(context) { DisplayPreferencesManager.getInstance(context) }
    val collapsePreference by remember(preferences) {
        preferences.collapseCompletedProcess.map { it as Boolean? }
    }.collectAsState(initial = null)
    val collapse = collapsePreference ?: true
    val snapshot = messages.toList()
    val computed by androidx.compose.runtime.key(chatId) {
        produceState<TranscriptStructure?>(null, snapshot, metadata) {
            value = withContext(Dispatchers.Default) {
                buildWindowStructure(snapshot, metadata)
            }
        }
    }
    // Keep the last immutable structure while computing. Project by timestamp, never by old
    // indices, so inserting process rows cannot unmount the list or misidentify a message.
    val projected = remember(snapshot, computed) {
        computed?.let { projectWindowProcessState(snapshot, it) }
    }
    val allGroups = projected?.first.orEmpty()
    val groups = allGroups
    val missingKeys = projected?.second.orEmpty()
    val scope = rememberCoroutineScope()
    val currentLoad by rememberUpdatedState(loadProcess)
    val expandedKeys = allGroups.values.map { it.key }.distinct().filter {
        !collapse || !enabled || TranscriptExpansionState.isExpanded(chatId, processSectionId(it))
    }
    LaunchedEffect(chatId, expandedKeys, metadata, messages.map { it.timestamp }) {
        expandedKeys.filter { key ->
            allGroups.entries.none { (index, group) ->
                group.key == key && index != group.finalIndex
            }
        }.forEach { currentLoad?.invoke(it) }
    }
    val expand: (Long) -> Unit = { key ->
        TranscriptExpansionState.expand(chatId, processSectionId(key))
        scope.launch { currentLoad?.invoke(key) }
    }
    return ResponseProcessState(
        groups,
        // An expanded section belongs to its conversation, not to the composition that shows it:
        // leaving the conversation must not fold the sections opened in it.
        isExpanded = { key -> !collapse || !enabled || TranscriptExpansionState.isExpanded(chatId, processSectionId(key)) },
        toggle = { key -> TranscriptExpansionState.toggle(chatId, processSectionId(key)) },
        expand = expand,
        hasMore = { key -> key in missingKeys },
        loadMore = { key -> currentLoad?.invoke(key) },
        showSummary = collapse && enabled,
        ready = collapsePreference != null && computed != null,
    )
}

private fun processSectionId(key: Long): String = "process-section-$key"

@Composable
internal fun ResponseProcessMessage(
    state: ResponseProcessState,
    index: Int,
    textColor: Color,
    content: @Composable (Int) -> Unit,
) {
    val group = state.groups[index]
    if (group == null) {
        Column { content(index) }
        return
    }
    val expanded = state.isExpanded(group.key)
    Column {
        if (index == group.firstIndex && state.showSummary) {
            if (group.headerIndex >= 0) {
                CompositionLocalProvider(LocalResponseMessageSection provides ResponseMessageSection.HEADER) {
                    content(group.headerIndex)
                }
            }
            ResponseActivityHeader(
                durationMs = group.durationMs,
                expanded = expanded,
                textColor = textColor,
                onClick = { state.toggle(group.key) },
            )
        }
        if (expanded || index == group.finalIndex) {
            if (expanded && index == group.finalIndex && state.hasMore(group.key)) {
                LaunchedEffect(group.key, index) { state.loadMore(group.key) }
            }
            CompositionLocalProvider(
                LocalResponseProcessExpanded provides expanded,
                LocalResponseMessageSection provides if (state.showSummary) ResponseMessageSection.BODY else ResponseMessageSection.ALL,
            ) {
                content(index)
            }
        }
    }
}
