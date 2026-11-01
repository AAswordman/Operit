package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AttachmentInfo

/** A component that shows previews of attachments to be sent with a message */
@Composable
fun AttachmentPreview(
        attachments: List<AttachmentInfo>,
        onRemoveAttachment: (String) -> Unit,
        onInsertAttachment: (AttachmentInfo) -> Unit,
        modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    val context = LocalContext.current
    Column(modifier = modifier) {
        Text(
                text = context.getString(R.string.attachments_count, attachments.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            items(attachments) { attachment ->
                AttachmentItem(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment.filePath) },
                        onInsert = { onInsertAttachment(attachment) }
                )

                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/** Individual attachment item */
@Composable
private fun AttachmentItem(attachment: AttachmentInfo, onRemove: () -> Unit, onInsert: () -> Unit) {
    val icon =
            when {
                attachment.fileName.startsWith("camera_") -> Icons.Default.PhotoCamera
                attachment.mimeType.startsWith("image/") -> Icons.Default.Image
                attachment.filePath.startsWith("screen_") -> Icons.Default.ScreenshotMonitor
                else -> Icons.Default.Description
            }

    Box(
            modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                            .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable(onClick = onInsert)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Icon based on file type
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // File info
            Column {
                Text(
                        text = AttachmentUtils.getDisplayName(attachment),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                if (attachment.fileSize > 0) {
                    Text(
                            text = AttachmentUtils.formatFileSize(attachment.fileSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Remove button
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove attachment",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
