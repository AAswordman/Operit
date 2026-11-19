package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

data class PendingQueueMessageItem(
    val id: Long,
    val text: String,
    internal val chatGeneration: Long = 0L,
    val isSteering: Boolean = false,
    val attachments: List<com.ai.assistance.operit.data.model.AttachmentInfo> = emptyList(),
    /** The message the user quoted when this was written; it goes along whenever this is sent. */
    val replyToMessage: com.ai.assistance.operit.data.model.ChatMessage? = null,
)

@Composable
fun PendingMessageQueuePanel(
    queuedMessages: List<PendingQueueMessageItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onEditMessage: (Long) -> Unit,
    onSteerMessage: (Long) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    itemColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier
) {
    if (queuedMessages.isEmpty()) return
    val actionIconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_pending_queue_title, queuedMessages.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription =
                        if (expanded) {
                            stringResource(R.string.collapse)
                        } else {
                            stringResource(R.string.expand)
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = queuedMessages,
                        key = { item -> item.id }
                    ) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = itemColor
                                ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!item.isSteering) QueueIconAction(
                                    onClick = { onEditMessage(item.id) },
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                    tint = actionIconTint
                                )
                                Text(
                                    text = listOf(item.text, item.attachments.joinToString { it.fileName })
                                        .filter { it.isNotBlank() }.joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (item.isSteering) R.string.chat_steer_pending else R.string.chat_steer_turn
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.clickable(enabled = !item.isSteering) {
                                            onSteerMessage(item.id)
                                        }.padding(6.dp),
                                    )
                                    if (!item.isSteering) QueueIconAction(
                                        onClick = { onDeleteMessage(item.id) },
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = actionIconTint
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueIconAction(
    onClick: () -> Unit,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}
