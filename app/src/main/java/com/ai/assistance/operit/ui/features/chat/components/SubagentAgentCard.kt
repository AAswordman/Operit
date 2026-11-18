package com.ai.assistance.operit.ui.features.chat.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.SubagentResultExtractor
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.SubagentInterruption
import com.ai.assistance.operit.ui.theme.stoppedAttention
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A stable badge per agent path, so a parent conversation with several children stays scannable
 * by sight the way each named Codex subagent carries its own mark.
 */
internal data class SubagentAgentIdentity(val icon: ImageVector, val accent: Color)

private val subagentAgentIcons = listOf<ImageVector>(
    Icons.Default.AutoAwesome,
    Icons.Default.Bolt,
    Icons.Default.Science,
    Icons.Default.BugReport,
    Icons.Default.Psychology,
    Icons.Default.RocketLaunch,
    Icons.Default.Hub,
    Icons.Default.TravelExplore,
)

private val subagentAgentAccents = listOf(
    Color(0xFFF59E0B),
    Color(0xFF8B5CF6),
    Color(0xFFEF4444),
    Color(0xFF10B981),
    Color(0xFF3B82F6),
    Color(0xFF06B6D4),
)

internal fun subagentAgentIdentity(seed: String): SubagentAgentIdentity {
    // String.hashCode is fully specified, so the same agent keeps its badge across processes.
    val hash = agentBadgeSeed(seed).hashCode()
    return SubagentAgentIdentity(
        icon = subagentAgentIcons[Math.floorMod(hash, subagentAgentIcons.size)],
        accent =
            subagentAgentAccents[
                Math.floorMod(hash / subagentAgentIcons.size, subagentAgentAccents.size)
            ],
    )
}

/**
 * The part of an agent reference that names the agent: `worker` for both `worker` and
 * `/root/worker`. A run is still being created when its spawn row first appears, so the row only
 * knows the bare task name at that moment and only learns the canonical path afterwards; hashing
 * the name keeps the badge from changing in between.
 */
internal fun agentBadgeSeed(seed: String): String =
    seed.trim().trimEnd('/').substringAfterLast('/').ifEmpty { "subagent" }

/**
 * Follows one chat's slice of a per-chat flow, so unrelated chats' tool activity no longer
 * recomposes every collaboration row in the conversation.
 */
@Composable
internal fun <T> perChatValue(flow: Flow<Map<String, T>>, chatId: String?): T? =
    remember(flow, chatId) { flow.map { state -> chatId?.let(state::get) }.distinctUntilChanged() }
        .collectAsState(initial = null)
        .value

/** Lifecycle states a named subagent can show in the conversation. */
internal enum class SubagentCardStatus {
    QUEUED,
    STARTED,
    CALLING_TOOL,
    COMPLETED,
    FAILED,
    /** Never reached a terminal state; the app or the parent chat stopped it. Not a failure. */
    INTERRUPTED,
    CANCELLED,
}

internal data class SubagentCardState(
    val status: SubagentCardStatus,
    val toolName: String? = null,
    /** Only known when the queue is visible to the caller; null means "queued, position unknown". */
    val queuePosition: Int? = null,
)

internal fun subagentCardState(
    runStatus: String?,
    currentTool: String? = null,
    queuePosition: Int? = null,
): SubagentCardState {
    return when (runStatus?.trim()?.uppercase()) {
        "QUEUED" ->
            SubagentCardState(
                SubagentCardStatus.QUEUED,
                queuePosition = queuePosition?.coerceAtLeast(1),
            )
        "RUNNING" ->
            SubagentCardState(
                if (currentTool.isNullOrBlank()) SubagentCardStatus.STARTED else SubagentCardStatus.CALLING_TOOL,
                toolName = currentTool?.takeIf { it.isNotBlank() },
            )
        "COMPLETED" -> SubagentCardState(SubagentCardStatus.COMPLETED)
        "FAILED" -> SubagentCardState(SubagentCardStatus.FAILED)
        "INTERRUPTED" -> SubagentCardState(SubagentCardStatus.INTERRUPTED)
        "CANCELLED" -> SubagentCardState(SubagentCardStatus.CANCELLED)
        // CREATED and anything unknown read as "the subagent has begun working".
        else -> SubagentCardState(SubagentCardStatus.STARTED)
    }
}

/** One stats fragment; kept as a resource handle so the layout stays free of formatting logic. */
internal data class SubagentStatLine(val labelResId: Int, val args: List<Any>)

internal fun subagentStatLines(
    durationText: String?,
    toolCount: Int,
    roundCount: Int,
): List<SubagentStatLine> =
    buildList {
        durationText?.takeIf { it.isNotBlank() }?.let {
            add(SubagentStatLine(R.string.subagent_card_elapsed, listOf(it)))
        }
        if (toolCount > 0) {
            add(SubagentStatLine(R.string.subagent_card_tool_count, listOf(toolCount)))
        }
        if (roundCount > 0) {
            add(SubagentStatLine(R.string.subagent_card_round_count, listOf(roundCount)))
        }
    }

@Composable
internal fun subagentCardStatusText(state: SubagentCardState): String =
    when (state.status) {
        SubagentCardStatus.QUEUED ->
            state.queuePosition
                ?.let { stringResource(R.string.subagent_status_queued, it) }
                ?: stringResource(R.string.subagent_status_queued_pending)
        SubagentCardStatus.STARTED -> stringResource(R.string.subagent_status_started)
        SubagentCardStatus.CALLING_TOOL ->
            stringResource(R.string.subagent_status_calling_tool, state.toolName.orEmpty())
        SubagentCardStatus.COMPLETED -> stringResource(R.string.subagent_status_completed)
        SubagentCardStatus.FAILED -> stringResource(R.string.subagent_status_error)
        SubagentCardStatus.INTERRUPTED -> stringResource(R.string.subagent_status_interrupted)
        SubagentCardStatus.CANCELLED -> stringResource(R.string.subagent_status_cancelled)
    }

@Composable
internal fun subagentCardStatusColor(status: SubagentCardStatus): Color =
    when (status) {
        SubagentCardStatus.FAILED -> MaterialTheme.colorScheme.error
        SubagentCardStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
        SubagentCardStatus.INTERRUPTED -> MaterialTheme.colorScheme.stoppedAttention
        SubagentCardStatus.STARTED, SubagentCardStatus.CALLING_TOOL ->
            MaterialTheme.colorScheme.primary
        SubagentCardStatus.COMPLETED,
        SubagentCardStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
internal fun subagentCardStatsText(
    durationText: String?,
    toolCount: Int,
    roundCount: Int,
): String? {
    val parts =
        subagentStatLines(durationText, toolCount, roundCount).map { line ->
            stringResource(line.labelResId, *line.args.toTypedArray())
        }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * What a subagent returned: the message the parent was handed when the row carries one, otherwise
 * the child conversation's last finished answer. A half-finished turn is process, not the answer,
 * and a persisted answer still carries the provider reasoning envelope and the tool calls around it.
 */
internal fun subagentReturnedContent(messages: List<ChatMessage>?, body: String?): String? {
    body?.takeIf { it.isNotBlank() }?.let {
        return it
    }
    return messages
        // A half-finished turn is process, not the answer the agent returned.
        ?.lastOrNull {
            it.sender == "ai" &&
                it.displayMode != ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE
        }
        ?.content
        ?.let(SubagentResultExtractor::forTranscript)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

/**
 * Why a run failed, when it did. A failure that left no answer behind still has a reason, and the
 * card is the place the user looks for one.
 */
internal fun subagentFailureText(status: SubagentCardStatus, error: String?): String? =
    error?.takeIf { it.isNotBlank() && status == SubagentCardStatus.FAILED }

/**
 * Why an interrupted run stopped, as a resource handle: the stored reason is an English sentence
 * written by whoever stopped the run, and the card owes the user that reason in their language.
 */
internal fun subagentInterruptionHintRes(status: SubagentCardStatus, error: String?): Int? =
    if (status != SubagentCardStatus.INTERRUPTED) {
        null
    } else {
        when (error) {
            SubagentInterruption.APP_RESTART -> R.string.subagent_interrupted_app_restart
            SubagentInterruption.ARCHIVE_IMPORT -> R.string.subagent_interrupted_archive_import
            SubagentInterruption.CHAT_BRANCH -> R.string.subagent_interrupted_chat_branch
            SubagentInterruption.READING_COMPANION ->
                R.string.subagent_interrupted_reading_companion
            else -> R.string.subagent_interrupted_unknown
        }
    }

/**
 * The card the user tapped, held outside the transcript: loading older history drops the message
 * that owns a card, and the floating card must not go with it.
 */
internal data class SubagentDetailRequest(
    val chatId: String?,
    /** Every open is its own read, so a card reopened after the run finished shows the answer. */
    val token: Long,
    val agentPath: String,
    /** The role card's picture when the row speaks for one, otherwise null and the badge is used. */
    val avatarUri: String?,
    val statusText: String,
    val failureText: String?,
    /** Why the run stopped rather than failed; shown the way a failure is, in its own tone. */
    val interruptionText: String?,
    val identity: SubagentAgentIdentity,
    val statusColor: Color,
    val body: String?,
    val childChatId: String?,
    val onOpenConversation: (() -> Unit)?,
)

/** Holds the one open card so the transcript can render it above every message. */
internal object SubagentDetailHost {
    private var request by mutableStateOf<SubagentDetailRequest?>(null)
    private var issued = 0L

    fun requestDetail(
        chatId: String?,
        agentPath: String,
        avatarUri: String?,
        statusText: String,
        failureText: String?,
        interruptionText: String?,
        identity: SubagentAgentIdentity,
        statusColor: Color,
        body: String?,
        childChatId: String?,
        onOpenConversation: (() -> Unit)?,
    ) {
        request =
            SubagentDetailRequest(
                chatId = chatId,
                token = ++issued,
                agentPath = agentPath,
                avatarUri = avatarUri,
                statusText = statusText,
                failureText = failureText,
                interruptionText = interruptionText,
                identity = identity,
                statusColor = statusColor,
                body = body,
                childChatId = childChatId,
                onOpenConversation = onOpenConversation,
            )
    }

    /** Only the conversation that owns the open card closes it. */
    fun dismiss(chatId: String?) {
        val open = request ?: return
        if (open.chatId == null || open.chatId == chatId) request = null
    }

    /** Drops whatever card was open, e.g. because the user left the conversation it belonged to. */
    fun clear() {
        request = null
    }

    /** The card open in this conversation, if any. */
    fun requestFor(chatId: String?): SubagentDetailRequest? =
        request?.takeIf { it.chatId == null || it.chatId == chatId }
}

/**
 * The returned content, read once per open. A reply row already carries it and needs no read; a
 * spawn row has to ask the child conversation, which a row that is still working cannot answer yet.
 */
@Composable
private fun rememberSubagentReturnedContent(
    childChatId: String?,
    body: String?,
    token: Long,
): String? {
    val handed = body?.takeIf { it.isNotBlank() }
    val chatId = childChatId?.takeIf { it.isNotBlank() }
    if (handed != null || chatId == null) return handed
    val context = LocalContext.current
    val historyManager = remember(context) { ChatHistoryManager.getInstance(context) }
    return produceState<String?>(initialValue = null, chatId, token) {
            value =
                historyManager.latestSubagentAnswer(chatId)
        }
        .value
}

/** The agent's own mark, so a family of agents stays scannable by sight. */
@Composable
private fun SubagentAgentBadge(identity: SubagentAgentIdentity, boxSize: Dp, iconSize: Dp) {
    Box(
        modifier =
            Modifier
                .size(boxSize)
                .clip(RoundedCornerShape(boxSize / 3))
                .background(identity.accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = identity.icon,
            contentDescription = null,
            tint = identity.accent,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The picture an agent is known by. A row that speaks for a role card wears that card's own picture,
 * shaped the way the conversation draws it; everything else keeps the agent's own mark.
 */
@Composable
internal fun SubagentAgentAvatar(
    avatarUri: String?,
    identity: SubagentAgentIdentity,
    boxSize: Dp,
    iconSize: Dp,
) {
    if (avatarUri.isNullOrBlank()) {
        SubagentAgentBadge(identity = identity, boxSize = boxSize, iconSize = iconSize)
        return
    }
    val context = LocalContext.current
    val theme = com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot.current
    val avatarShapePref = theme.avatarShape
    val avatarCornerRadius = theme.avatarCornerRadius
    val shape = remember(avatarShapePref, avatarCornerRadius) {
        if (avatarShapePref == UserPreferencesManager.AVATAR_SHAPE_SQUARE) {
            RoundedCornerShape(avatarCornerRadius.dp)
        } else {
            CircleShape
        }
    }
    Image(
        painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
        contentDescription = null,
        modifier = Modifier.size(boxSize).clip(shape),
        contentScale = ContentScale.Crop,
    )
}

/**
 * The shared subagent card: the agent's mark, its path and its live status. Tapping it opens the
 * floating detail card rather than jumping straight into the child conversation. The card itself is
 * rendered by [ChatArea], so loading older history cannot take it away.
 */
@Composable
internal fun SubagentAgentCard(
    agentPath: String,
    statusText: String,
    identity: SubagentAgentIdentity,
    statusColor: Color,
    modifier: Modifier = Modifier,
    body: String? = null,
    avatarUri: String? = null,
    openDetailLabelRes: Int = R.string.subagent_card_open_detail,
    chatId: String? = null,
    childChatId: String? = null,
    failureText: String? = null,
    interruptionText: String? = null,
    onOpenConversation: (() -> Unit)? = null,
    enableDialogs: Boolean = true,
) {
    val openable =
        enableDialogs && LocalSubagentDetailsEnabled.current &&
            (onOpenConversation != null || !body.isNullOrBlank() || !childChatId.isNullOrBlank())
    val openDetailLabel = stringResource(openDetailLabelRes)
    val openDetail = {
        SubagentDetailHost.requestDetail(
            chatId = chatId,
            agentPath = agentPath,
            avatarUri = avatarUri,
            statusText = statusText,
            failureText = failureText,
            interruptionText = interruptionText,
            identity = identity,
            statusColor = statusColor,
            body = body,
            childChatId = childChatId,
            onOpenConversation = onOpenConversation,
        )
    }
    val rowModifier =
        if (openable) Modifier.clickable(role = Role.Button) { openDetail() } else Modifier
    val rowSemantics =
        if (openable) Modifier.semantics { contentDescription = openDetailLabel } else Modifier

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .then(rowSemantics)
            .then(rowModifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubagentAgentAvatar(
                avatarUri = avatarUri,
                identity = identity,
                boxSize = 22.dp,
                iconSize = 14.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            // The path and its status share one weighted band that reaches the card's trailing
            // edge, so what is left over after them stays inside the band and the chevron comes
            // to rest against that edge however long the path runs.
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = agentPath,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (openable) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The floating card a tap opens: what this subagent returned, and the way into its conversation.
 * [ChatArea] renders it, so it outlives the message that was tapped.
 */
@Composable
internal fun SubagentDetailCard(request: SubagentDetailRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val returned =
        rememberSubagentReturnedContent(
            childChatId = request.childChatId,
            body = request.body,
            token = request.token,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubagentAgentAvatar(
                        avatarUri = request.avatarUri,
                        identity = request.identity,
                        boxSize = 28.dp,
                        iconSize = 17.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = request.agentPath,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = request.statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = request.statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        text = {
            Box(
                modifier =
                    Modifier.padding(vertical = 4.dp)
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
            ) {
                Column {
                    // A run that ended badly says why before it says what it managed to return.
                    // A failure the automatic review caused stores the instruction written for the
                    // model, so the reader gets the same conclusion the tool row shows.
                    request.failureText
                        ?.takeIf { it.isNotBlank() }
                        ?.let { text ->
                        Text(
                            text = text,
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // A run that stopped is not a run that failed, and it says which.
                    request.interruptionText?.takeIf { it.isNotBlank() }?.let { text ->
                        Text(
                            text = text,
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.stoppedAttention,
                        )
                    }
                    SelectionContainer {
                        Text(
                            text = returned ?: stringResource(R.string.subagent_detail_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (returned == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            request.onOpenConversation?.let { open ->
                Button(
                    onClick = {
                        onDismiss()
                        open()
                    }
                ) {
                    Text(stringResource(R.string.subagent_open_conversation))
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.subagent_detail_close))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
    )
}
