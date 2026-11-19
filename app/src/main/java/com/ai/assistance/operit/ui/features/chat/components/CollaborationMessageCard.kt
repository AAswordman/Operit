package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage

internal data class CollaborationDisplayMessage(val sender: String, val kind: String, val body: String)

internal fun collaborationDisplayMessages(content: String, sender: String): List<CollaborationDisplayMessage> {
    val header = Regex(
        "^Message ID: [0-9a-f-]{36}\\nMessage Type: (MESSAGE|NEW_TASK|FINAL_ANSWER|STATUS)\\n" +
            "Task name: [^\\n]+\\nSender: ([^\\n]+)\\nPayload:\\n",
    )
    val match = header.find(content)
    if (match == null) {
        return listOf(CollaborationDisplayMessage(sender, "NEW_TASK", content))
    }
    return listOf(
        CollaborationDisplayMessage(
            sender, match.groupValues[1],
            content.substring(match.range.last + 1).trimEnd(),
        )
    )
}

/** Only host-marked collaboration rows use this display; normal user prose is never reclassified. */
@Composable
fun CollaborationMessageCard(message: ChatMessage) {
    val events = remember(message.content, message.roleName) {
        collaborationDisplayMessages(message.content, message.roleName)
    }
    val chatId = LocalTranscriptChatId.current
    val expansionId = "agent-event-${message.timestamp}"
    val expanded = TranscriptExpansionState.isExpanded(chatId, expansionId)
    val expansionState = stringResource(
        if (expanded) R.string.subagent_event_expanded else R.string.subagent_event_collapsed,
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
            events.forEach { event ->
                val kind = stringResource(when (event.kind) {
                    "FINAL_ANSWER" -> R.string.subagent_message_result
                    "STATUS" -> R.string.subagent_message_status
                    "NEW_TASK" -> R.string.subagent_message_task
                    else -> R.string.subagent_message_note
                })
                Row(
                    Modifier.fillMaxWidth()
                        .semantics { stateDescription = expansionState }
                        .clickable { TranscriptExpansionState.toggle(chatId, expansionId) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null,
                        modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.subagent_message_header, event.sender, kind),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                        contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expanded) {
                    SelectionContainer {
                        Text(
                            event.body,
                            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
    }
}
