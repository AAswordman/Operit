package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
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
            between != null && between.isNotEmpty() && between.all {
                messages[it].sender == "summary" || messages[it].displayMode.isCollaborationEvent
            }
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
    val chatId: String? = null,
    val messageIds: List<Long> = emptyList(),
)

@Composable
internal fun rememberResponseProcessState(
    messages: List<ChatMessage>,
    chatId: String?,
    enabled: Boolean = true,
): ResponseProcessState {
    val context = LocalContext.current
    val preferences = remember(context) { DisplayPreferencesManager.getInstance(context) }
    val collapse by preferences.collapseCompletedProcess.collectAsState(initial = true)
    val snapshot = messages.toList()
    val groups = remember(snapshot, collapse, enabled) {
        if (collapse && enabled) responseProcessGroups(snapshot) else emptyMap()
    }
    return ResponseProcessState(
        groups,
        isExpanded = { TranscriptExpansionState.isExpanded(chatId, "process-$it") },
        toggle = { TranscriptExpansionState.toggle(chatId, "process-$it") },
        expand = { TranscriptExpansionState.expand(chatId, "process-$it") },
        chatId = chatId,
        messageIds = snapshot.map { it.timestamp },
    )
}

@Composable
internal fun ResponseProcessMessage(
    state: ResponseProcessState,
    index: Int,
    textColor: Color,
    content: @Composable (Int) -> Unit,
) {
    val render: @Composable (Int) -> Unit = { messageIndex ->
        CompositionLocalProvider(
            LocalTranscriptChatId provides state.chatId,
            LocalTranscriptMessageId provides state.messageIds.getOrNull(messageIndex),
        ) {
            content(messageIndex)
        }
    }
    val group = state.groups[index]
    if (group == null) {
        Column { render(index) }
        return
    }
    val expanded = state.isExpanded(group.key)
    Column {
        if (index == group.firstIndex) {
            CompositionLocalProvider(LocalResponseMessageSection provides ResponseMessageSection.HEADER) {
                render(group.headerIndex)
            }
            ResponseActivityHeader(
                durationMs = group.durationMs,
                expanded = expanded,
                textColor = textColor,
                onClick = { state.toggle(group.key) },
            )
        }
        if (expanded || index == group.finalIndex) {
            CompositionLocalProvider(
                LocalResponseProcessExpanded provides expanded,
                LocalResponseMessageSection provides ResponseMessageSection.BODY,
            ) {
                render(index)
            }
        }
    }
}
