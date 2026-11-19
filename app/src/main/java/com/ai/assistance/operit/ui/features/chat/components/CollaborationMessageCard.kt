package com.ai.assistance.operit.ui.features.chat.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.core.agent.collaboration.CollaborationCoordinator
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.SubagentRunRepository
import com.ai.assistance.operit.ui.features.chat.components.part.resolveSubagentDisplayedTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal data class CollaborationDisplayMessage(
    val sender: String,
    val kind: String,
    val body: String,
    /** The sending agent's chat, when the envelope names it (rows written before it never do). */
    val senderId: String? = null,
)

/** The envelope a collaboration row carries, anchored so quoted text cannot pose as one. */
private val collaborationEnvelopeHeader = Regex(
    "^Message ID: [0-9a-f-]{36}\\nMessage Type: (MESSAGE|NEW_TASK|FINAL_ANSWER|STATUS)\\n" +
        "Task name: [^\\n]+\\nSender: ([^\\n]+)\\n(?:Sender ID: ([^\\n]+)\\n)?(?:Sent at: [^\\n]+\\n)?" +
        "(?:Roles: [^\\n]+\\n)?Payload:\\n",
)

/** The label [CollaborationCoordinator.finish] puts in front of a status line's reason. */
private val collaborationStatusLabel = Regex("^[^\\n:]*: (?:FAILED|INTERRUPTED|COMPLETED): ")


/**
 * A status line without the run's own label, which is `"<path>: <status>: "`. The reason is what a
 * reader needs, and it is the part that can hold the instruction written for the model.
 */
internal fun collaborationStatusReason(body: String): String =
    collaborationStatusLabel.replaceFirst(body, "")

/**
 * The payload a browser should report for [content], or null when it is not a status row.
 *
 * The App's card reports a stopped run through its own line and never shows the payload; the browser
 * has no such card, so it would print the whole envelope as a message the user sent.
 */
internal fun collaborationStatusDisplayText(context: Context, content: String): String? {
    val match = collaborationEnvelopeHeader.find(content) ?: return null
    if (match.groupValues[1] != "STATUS") return null
    return content.substring(match.range.last + 1).trim()
}

internal fun collaborationDisplayMessages(content: String, sender: String): List<CollaborationDisplayMessage> {
    val match = collaborationEnvelopeHeader.find(content)
    if (match == null) {
        return listOf(CollaborationDisplayMessage(sender, "NEW_TASK", content))
    }
    return listOf(
        CollaborationDisplayMessage(
            sender, match.groupValues[1],
            content.substring(match.range.last + 1).trimEnd(),
            senderId = match.groupValues[3].takeIf { it.isNotBlank() },
        )
    )
}

/**
 * The row with its payload swapped for [body], its envelope left exactly as it was.
 *
 * The envelope is what the row's own label, its card and the prompt history read to know who spoke
 * and what kind of message it was, so a memory edit changes what the agent said and nothing about
 * who said it. A row that never carried an envelope is only text, so the text is all there is.
 */
internal fun collaborationBodyReplaced(content: String, body: String): String {
    val match = collaborationEnvelopeHeader.find(content) ?: return body
    val trailing = content.substring(match.range.last + 1).takeLastWhile { it.isWhitespace() }
    return content.substring(0, match.range.last + 1) + body + trailing
}

/**
 * Only a message the agent sent back is its return. A task the parent handed over and a status line
 * are not, so those rows fall through to the child conversation the way a spawn row does.
 */
internal fun collaborationReturnedBody(kind: String, body: String): String? =
    body.takeIf { kind == "MESSAGE" || kind == "FINAL_ANSWER" }

/**
 * The label a collaboration row carries for the kind of message it is. It is the row's own subtitle,
 * so a row whose text arrives on its own reads as what it is rather than as the run's last status.
 */
internal fun collaborationKindLabelRes(kind: String): Int =
    when (kind) {
        "MESSAGE" -> R.string.subagent_message_midway
        "FINAL_ANSWER" -> R.string.subagent_message_result
        "STATUS" -> R.string.subagent_message_status
        "NEW_TASK" -> R.string.subagent_message_task
        else -> R.string.subagent_message_note
    }

/**
 * A mid-way message is not the result the agent finished with. The run status of a live run would say
 * "completed" over it, so a message says what it is instead.
 */
internal fun collaborationMidwayLabelRes(kind: String): Int? =
    R.string.subagent_message_midway.takeIf { kind == "MESSAGE" }

/**
 * A row carrying the answer an agent finished with says so on its own, in words of its own: the run's
 * later status is about the run, not about the reply that is already in the transcript, so a reply
 * that arrived never reads as a status the run reached afterwards.
 */
internal fun collaborationResultLabelRes(kind: String): Int? =
    R.string.subagent_message_result.takeIf { kind == "FINAL_ANSWER" }

/**
 * Whether a row carries a reply that already arrived. Such a row says what it is and borrows nothing
 * from the run: not the run's words, not its colour, not its failure or interruption note. What the
 * run became afterwards belongs to the run, and the reply is already in the transcript.
 */
internal fun collaborationRowCarriesReturnedReply(kind: String): Boolean =
    collaborationResultLabelRes(kind) != null

/**
 * Resolves the run a collaboration row belongs to, or null when that run can no longer be opened.
 *
 * A name can pass to a new agent, so the row's own generation is found first among every run that
 * wore the name (archived ones included), and only then is it checked for being open. Picking among
 * open runs first would hand a row of an archived agent to an older agent of the same name.
 * v1 tasks and runs owned by another feature (the reading companion shares this table) never count.
 */
internal fun findCollaborationRun(
    runs: List<SubagentRunEntity>,
    sender: String?,
    at: Long? = null,
    senderId: String? = null,
): SubagentRunEntity? {
    val owner = sender?.takeIf { it.isNotBlank() } ?: return null
    val owned = runs.filter {
        it.externalOwnerId == owner && it.externalOwnerType == CollaborationCoordinator.OWNER_TYPE
    }
    // The envelope names the chat that wrote it; a deleted run then links nowhere.
    val run = senderId?.takeIf { it.isNotBlank() }?.let { id -> owned.lastOrNull { it.childChatId == id } }
        ?: if (senderId.isNullOrBlank()) {
            if (at == null) owned.firstOrNull { it.archivedAt == null } else collaborationRunAt(owned, at)
        } else {
            null
        }
    return run?.takeIf { it.archivedAt == null }
}

/**
 * Of the runs that wore one name over time (an archived agent's name can be used again), the one
 * a row written at [at] belongs to: the latest started by then. A run started later belongs to a
 * later row, so a row older than every run links to none.
 */
internal fun collaborationRunAt(runs: List<SubagentRunEntity>, at: Long): SubagentRunEntity? =
    runs.filter { it.createdAt <= at }.maxByOrNull { it.createdAt }

/**
 * A collaboration row that belongs to a real Subagent run: the row keeps the agent's badge, its
 * live status and the run statistics instead of the anonymous "note" line.
 */
@Composable
private fun SubagentRunEventCard(
    run: SubagentRunEntity,
    event: CollaborationDisplayMessage,
    chatId: String?,
    onOpenConversation: () -> Unit,
) {
    val context = LocalContext.current
    val chatCore = remember(context) {
        ChatRuntimeHolder.getInstance(context.applicationContext).getCore(ChatRuntimeSlot.MAIN)
    }
    val childChatId = run.childChatId
    val childProcessingState = perChatValue(chatCore.inputProcessingStateByChatId, childChatId)
    val childLastToolName = perChatValue(chatCore.lastToolNameByChatId, childChatId)

    val currentTool =
        resolveSubagentDisplayedTool(
            childProcessingState = childProcessingState,
            lastToolName = childLastToolName,
        )
    val cardState =
        subagentCardState(
            runStatus = run.status,
            currentTool = currentTool,
        )
    val agentPath = run.externalOwnerId?.takeIf { it.isNotBlank() } ?: event.sender
    val roleName = rememberMainAgentRole(agentPath)?.name
    val carriesReply = collaborationRowCarriesReturnedReply(event.kind)

    SubagentAgentCard(
        agentPath = collaborationRowTitle(agentPath, roleName),
        statusText =
            collaborationResultLabelRes(event.kind)?.let { stringResource(it) }
                ?: collaborationMidwayLabelRes(event.kind)?.let { stringResource(it) }
                ?: subagentCardStatusText(cardState),
        identity = remember(agentPath) { subagentAgentIdentity(agentPath) },
        // The reply already arrived, so no run status colours or annotates this row.
        statusColor =
            if (carriesReply) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                subagentCardStatusColor(cardState.status)
            },
        body = collaborationReturnedBody(event.kind, event.body),
        chatId = chatId,
        childChatId = childChatId,
        failureText =
            if (carriesReply) null else subagentFailureText(cardState.status, run.error),
        interruptionText =
            if (carriesReply) {
                null
            } else {
                subagentInterruptionHintRes(cardState.status, run.error)?.let { stringResource(it) }
            },
        onOpenConversation = onOpenConversation,
    )
}

/** Only host-marked collaboration rows use this display; normal user prose is never reclassified. */
@Composable
fun CollaborationMessageCard(message: ChatMessage, horizontalPadding: androidx.compose.ui.unit.Dp = 12.dp) {
    val events = remember(message.content, message.roleName) {
        collaborationDisplayMessages(message.content, message.roleName)
    }
    val context = LocalContext.current
    val chatCore = remember(context) {
        ChatRuntimeHolder.getInstance(context.applicationContext).getCore(ChatRuntimeSlot.MAIN)
    }
    val repository = remember(context) { SubagentRunRepository.getInstance(context) }
    val host = LocalSubagentChatHost.current
    val mainChatId by chatCore.currentChatId.collectAsState(initial = null)
    val currentChatId = host?.parentChatId ?: mainChatId
    val sharedRuns = LocalTranscriptRuns.current?.takeIf { it.chatId == currentChatId }
    val runsFlow = remember(currentChatId) {
        currentChatId?.let { repository.observeByParentChatId(it) }
    }
    val runs = sharedRuns?.runs ?: (runsFlow ?: flowOf(emptyList())).collectAsState(initial = emptyList()).value
    Column(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
            events.forEach { event ->
                // A message comes from the run that wore the sender's name when it arrived.
                val run = findCollaborationRun(runs, event.sender, message.timestamp, event.senderId)
                if (run != null) {
                    SubagentRunEventCard(
                        run = run,
                        event = event,
                        chatId = currentChatId,
                        onOpenConversation = {
                            host?.open?.invoke(run.childChatId)
                                ?: chatCore.switchChat(run.childChatId, scrollToBottom = false)
                        },
                    )
                    return@forEach
                }
                CollaborationAgentMessage(
                    sender = event.sender,
                    kindLabel = stringResource(collaborationKindLabelRes(event.kind)),
                    // A status line can carry the instruction written for the model, and this card is
                    // the only place a row without a live run shows its payload.
                    body =
                        if (event.kind == "STATUS") {
                            event.body
                        } else {
                            event.body
                        },
                    chatId = currentChatId,
                )
            }
    }
}

/**
 * A collaboration row with no live run to hang off, which is every row inside the subagent's own
 * conversation. It is the card the conversation's own agents wear: a picture, the name of the agent
 * that spoke, what kind of message it is, and the message itself in the floating card a tap opens.
 */
@Composable
private fun CollaborationAgentMessage(
    sender: String,
    kindLabel: String,
    body: String,
    chatId: String?,
) {
    val role = rememberMainAgentRole(sender)
    SubagentAgentCard(
        agentPath = collaborationRowTitle(sender, role?.name),
        statusText = kindLabel,
        identity = remember(sender) { subagentAgentIdentity(sender) },
        statusColor = MaterialTheme.colorScheme.onSurfaceVariant,
        body = body,
        avatarUri = role?.avatarUri,
        openDetailLabelRes = R.string.subagent_message_open_detail,
        chatId = chatId,
    )
}

/**
 * The main agent is the one-segment task path; every named subagent hangs off it.
 */
internal fun collaborationSenderIsMainAgent(sender: String): Boolean =
    sender.trim().trim('/').let { it.isNotEmpty() && !it.contains('/') }

/**
 * Who the main agent speaks as. A row that speaks for the main agent wears the main role card's name
 * and picture the way the conversation's other role cards do; a row from a named subagent keeps that
 * agent's own path and mark, which is what tells a family of agents apart.
 *
 * The chain is the chat's own: the active card's picture with the system cards' built-in picture
 * filled in, or for a character group the group's picture and then its first member's, and only
 * failing all of them the global AI picture.
 */
@Composable
internal fun rememberMainAgentRole(sender: String): MainAgentRole? {
    if (!collaborationSenderIsMainAgent(sender)) return null
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val characterCardManager = remember(context) { CharacterCardManager.getInstance(context) }
    val groupCardManager = remember(context) { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    val activePrompt by
        remember(activePromptManager) { activePromptManager.activePromptFlow }
            .collectAsState(
                initial =
                    ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
            )
    val cardId = (activePrompt as? ActivePrompt.CharacterCard)?.id
    val groupId = (activePrompt as? ActivePrompt.CharacterGroup)?.id
    val card by
        remember(characterCardManager, cardId) {
                cardId?.let { characterCardManager.getCharacterCardFlow(it) } ?: flowOf(null)
            }
            .collectAsState(initial = null)
    val cardAvatarUri by
        remember(preferences, cardId) { avatarUriFlow(preferences, cardId) }
            .collectAsState(initial = null)
    val groupAvatarUri by
        remember(preferences, groupId) {
                groupId?.let { preferences.getAiAvatarForCharacterGroupFlow(it) } ?: flowOf(null)
            }
            .collectAsState(initial = null)
    val group by
        remember(groupCardManager, groupId) {
                groupId?.let { groupCardManager.getCharacterGroupCardFlow(it) } ?: flowOf(null)
            }
            .collectAsState(initial = null)
    val firstMemberCardId =
        remember(group) {
            group?.members?.sortedBy { it.orderIndex }?.firstOrNull()?.characterCardId
        }
    val firstMemberAvatarUri by
        remember(preferences, firstMemberCardId) {
                avatarUriFlow(preferences, firstMemberCardId)
            }
            .collectAsState(initial = null)
    val globalAvatarUri = com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot.current.customAiAvatarUri
    return MainAgentRole(
        name = card?.name?.takeIf { it.isNotBlank() } ?: group?.name?.takeIf { it.isNotBlank() },
        avatarUri =
            preferredAvatarUri(
                cardAvatarUri = cardAvatarUri,
                groupAvatarUri = groupAvatarUri,
                firstMemberAvatarUri = firstMemberAvatarUri,
                globalAvatarUri = globalAvatarUri,
            ),
    )
}

/** The role card the main agent speaks as: the name the conversation calls it, and its picture. */
internal data class MainAgentRole(val name: String?, val avatarUri: String?)

/**
 * The label a row carries: the role card that speaks it, then the agent path it speaks from. Only the
 * main agent's row is named this way - a subagent's path is already what tells it from its siblings.
 */
internal fun collaborationRowTitle(sender: String, roleName: String?): String =
    roleName
        ?.trim()
        ?.takeIf { it.isNotEmpty() && collaborationSenderIsMainAgent(sender) }
        ?.let { "$it · $sender" }
        ?: sender

/**
 * Which picture wins when the role card, its group, the group's first member and the global one all
 * have one. A blank value is no picture at all, so it never stands in front of one that is.
 */
internal fun preferredAvatarUri(
    cardAvatarUri: String?,
    groupAvatarUri: String?,
    firstMemberAvatarUri: String?,
    globalAvatarUri: String?,
): String? =
    listOf(cardAvatarUri, groupAvatarUri, firstMemberAvatarUri, globalAvatarUri)
        .firstOrNull { !it.isNullOrBlank() }

private fun avatarUriFlow(
    preferences: UserPreferencesManager,
    characterCardId: String?,
): Flow<String?> =
    characterCardId?.let { preferences.getAiAvatarForCharacterCardFlow(it) } ?: flowOf(null)
