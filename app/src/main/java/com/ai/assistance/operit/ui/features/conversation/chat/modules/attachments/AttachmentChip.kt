package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.AttachmentImagePreviewDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images.AttachmentPreviewImage
import java.io.File

@Composable
fun AttachmentChip(
    attachmentInfo: AttachmentInfo,
    attachments: List<AttachmentInfo>,
    onRemove: () -> Unit,
    onInsert: () -> Unit,
) {
    val context = LocalContext.current
    val isImage = attachmentInfo.mimeType.startsWith("image/")
    var previewVisible by remember(attachmentInfo.filePath) { mutableStateOf(false) }
    val icon: ImageVector = if (isImage) Icons.Default.Image else Icons.Default.Description
    val chipShape = RoundedCornerShape(13.dp)
    val imageModel =
        remember(attachmentInfo.filePath, isImage) {
            if (isImage) {
                Uri.fromFile(File(attachmentInfo.filePath))
            } else {
                null
            }
        }

    val images = remember(attachments) {
        attachments.filter { it.mimeType.startsWith("image/") }.map {
            AttachmentPreviewImage(Uri.fromFile(File(it.filePath)), it.fileName)
        }
    }

    if (isImage && previewVisible && images.any { it.uri == imageModel }) {
        AttachmentImagePreviewDialog(
            images = images,
            initialImageUri = checkNotNull(imageModel),
            onDismiss = { previewVisible = false },
        )
    }

    if (isImage) {
        Box(
            modifier =
                Modifier.size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { previewVisible = true }
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = attachmentInfo.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            Surface(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(18.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                IconButton(onClick = onRemove, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = context.getString(R.string.remove_attachment),
                        modifier = Modifier.size(10.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    } else {
        Surface(
            modifier =
                Modifier.height(26.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = chipShape,
                    ),
            shape = chipShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = attachmentInfo.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 80.dp),
                )

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(onClick = onRemove, modifier = Modifier.size(14.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = context.getString(R.string.remove_attachment),
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
