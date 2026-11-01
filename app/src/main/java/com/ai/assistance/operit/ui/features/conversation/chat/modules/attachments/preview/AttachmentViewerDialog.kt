package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.preview

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.MediaFileUnavailableTag
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.mediaPreviewUri
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.rememberMediaPreviewRevision
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.compactDialogHeightWhenShort
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.rememberCompactDialogMetrics
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.media.ImageBitmapLimiter
import com.ai.assistance.operit.util.media.MediaBase64Limiter
import com.ai.assistance.operit.util.media.MediaPoolManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChatAttachment(
    val id: String,
    val filename: String,
    val mimeType: String,
    val size: Long = 0,
    val content: String = ""
)

@Composable
fun AttachmentViewerDialog(
    visible: Boolean,
    attachment: ChatAttachment?,
    onDismiss: () -> Unit
) {
    if (!visible || attachment == null) return

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dialogMetrics = rememberCompactDialogMetrics()
    val mediaMaxHeight = if (dialogMetrics.isCompactHeight) 220.dp else 500.dp

    val isImage = attachment.mimeType.startsWith("image/")
    val isAudio = attachment.mimeType.startsWith("audio/")
    val isVideo = attachment.mimeType.startsWith("video/")
    val isTextLike = isTextLikeMimeType(attachment.mimeType)

    val mediaPoolId = remember(attachment.id) {
        attachment.id.takeIf { it.startsWith("media_pool:") }?.removePrefix("media_pool:")
    }

    val previewRevision = rememberMediaPreviewRevision()
    var mediaPoolResolved by remember(mediaPoolId, attachment.mimeType) { mutableStateOf(mediaPoolId == null) }
    val mediaPoolFileState = produceState<File?>(initialValue = null, key1 = mediaPoolId, key2 = attachment.mimeType) {
        if (mediaPoolId.isNullOrBlank()) return@produceState

        try {
            val mediaData = MediaPoolManager.getMedia(mediaPoolId) ?: return@produceState
            val estimatedBytes = MediaBase64Limiter.estimateDecodedSizeBytes(mediaData.base64)
            if (estimatedBytes == null || estimatedBytes > 20 * 1024 * 1024) {
                AppLogger.w("AttachmentViewerDialog", "Media pool item too large to preview: $mediaPoolId, estimatedBytes=$estimatedBytes")
                return@produceState
            }
            val bytes = try {
                Base64.decode(mediaData.base64, Base64.DEFAULT)
            } catch (e: Exception) {
                AppLogger.e("AttachmentViewerDialog", "Failed to decode media base64: $mediaPoolId", e)
                return@produceState
            }

            if (bytes.size > 20 * 1024 * 1024) {
                AppLogger.w("AttachmentViewerDialog", "Media pool item too large to preview after decode: $mediaPoolId, bytes=${bytes.size}")
                return@produceState
            }

            val dir = File(context.cacheDir, "media_pool_preview")
            withContext(Dispatchers.IO) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }

            val ext = fileExtForMimeType(mediaData.mimeType)
            val outFile = File(dir, "$mediaPoolId.$ext")
            try {
                withContext(Dispatchers.IO) {
                    outFile.writeBytes(bytes)
                }
                value = outFile
            } catch (e: Exception) {
                AppLogger.e("AttachmentViewerDialog", "Failed to write media pool file: $mediaPoolId", e)
            }
        } finally {
            mediaPoolResolved = true
        }
    }

    var originalResolved by remember(attachment.id, previewRevision) { mutableStateOf(false) }
    val originalUriState = produceState<Uri?>(initialValue = null, key1 = attachment.id, key2 = previewRevision) {
        value = null
        try {
            if (mediaPoolId == null) {
                value = withContext(Dispatchers.IO) { mediaPreviewUri(context, attachment.id) }
            }
        } finally {
            originalResolved = true
        }
    }
    val originalUri = originalUriState.value
    val fileFromPath = remember(originalUri) {
        originalUri?.takeIf { it.scheme == "file" }?.path?.let(::File)
    }
    val file = mediaPoolFileState.value ?: fileFromPath
    val fileUri = if (mediaPoolId != null) file?.let(Uri::fromFile) else originalUri
    val sourceResolved = if (mediaPoolId != null) mediaPoolResolved else originalResolved
    var imageResolved by remember(fileUri, previewRevision) { mutableStateOf(false) }

    val imageBitmapState = produceState<Bitmap?>(initialValue = null, key1 = fileUri, key2 = previewRevision) {
        value = null
        try {
            if (!isImage) return@produceState
            val uri = fileUri ?: return@produceState
            value = withContext(Dispatchers.IO) {
                val bytes = readBytesLimited(context, uri, 20 * 1024 * 1024)
                ImageBitmapLimiter.decodeDownsampledBitmap(bytes)
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("AttachmentViewerDialog", "读取图片原文件失败：$fileUri", error)
        } finally {
            imageResolved = true
        }
    }

    val textContentState = produceState<String?>(initialValue = attachment.content.takeIf { it.isNotBlank() }, key1 = attachment.id, key2 = attachment.content, key3 = attachment.mimeType to file) {
        if (value != null) return@produceState
        if (!isTextLike) return@produceState
        val f = file ?: return@produceState

        if (f.length() > 512 * 1024) {
            value = null
            return@produceState
        }

        value = try {
            withContext(Dispatchers.IO) {
                f.readText()
            }
        } catch (e: Exception) {
            AppLogger.e("AttachmentViewerDialog", "Failed to read text attachment: ${attachment.id}", e)
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .compactDialogHeightWhenShort(dialogMetrics, defaultMaxHeight = 520.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val icon = when {
                            isImage -> Icons.Default.Image
                            isAudio -> Icons.Default.VolumeUp
                            isVideo -> Icons.Default.PlayArrow
                            else -> Icons.Default.Description
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = attachment.filename,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                when {
                    (isImage || isAudio || isVideo) && fileUri == null && sourceResolved -> {
                        MediaFileUnavailableTag(attachment.filename)
                    }
                    (isImage || isAudio || isVideo) && fileUri == null -> {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                    isImage -> {
                        if (imageBitmapState.value != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = mediaMaxHeight)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = imageBitmapState.value!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else if (!imageResolved) {
                            androidx.compose.material3.CircularProgressIndicator()
                        } else {
                            MediaFileUnavailableTag(attachment.filename, missing = false)
                        }
                    }

                    isAudio -> {
                        if (fileUri != null) {
                            AudioAttachmentPlayer(
                                uri = fileUri,
                                modifier = Modifier.fillMaxWidth(),
                                autoPlay = false
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.cannot_open_file, attachment.filename),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    isVideo -> {
                        if (fileUri != null) {
                            VideoAttachmentPlayer(
                                uri = fileUri,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min = if (dialogMetrics.isCompactHeight) 120.dp else 180.dp,
                                        max = if (dialogMetrics.isCompactHeight) 220.dp else 420.dp
                                    ),
                                autoPlay = false
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.cannot_open_file, attachment.filename),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    textContentState.value != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.Top)
                                .weight(1f, fill = false)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = textContentState.value.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.size(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(textContentState.value.orEmpty()))
                                    onDismiss()
                                }
                            ) {
                                Text(stringResource(R.string.copy_content))
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = stringResource(R.string.open_file),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (file != null) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                openFile(context, file, attachment.mimeType, attachment.filename)
                            }
                        ) {
                            Text(stringResource(R.string.open_file))
                        }
                    }
                }
            }
        }
    }
}

private fun isTextLikeMimeType(mimeType: String): Boolean {
    val mt = mimeType.lowercase().substringBefore(';')
    return mt.startsWith("text/") ||
        mt.contains("json") ||
        mt.contains("xml") ||
        mt.contains("yaml") ||
        mt.contains("yml") ||
        mt.contains("csv")
}

private fun fileExtForMimeType(mimeType: String): String {
    val mt = mimeType.lowercase().substringBefore(';')
    return when (mt) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/ogg", "audio/opus" -> "ogg"
        "audio/webm" -> "webm"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/ogg" -> "ogv"
        else -> mt.substringAfter('/', "bin").ifBlank { "bin" }
    }
}

private fun openFile(context: Context, file: File, mimeType: String, fileNameForToast: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, if (mimeType.isNotBlank()) mimeType else "*/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        OKToast.error(
            context,
            context.getString(R.string.file_open_failed, fileNameForToast),
            OKToast.LENGTH_SHORT
        ).show()
    } catch (e: Exception) {
        AppLogger.e("AttachmentViewerDialog", "openFile failed", e)
        OKToast.error(
            context,
            context.getString(R.string.file_open_failed, fileNameForToast),
            OKToast.LENGTH_SHORT
        ).show()
    }
}

private fun readBytesLimited(context: Context, uri: Uri, maxBytes: Int): ByteArray {
    if (maxBytes <= 0) throw IllegalArgumentException("maxBytes must be positive")

    val input = if (uri.scheme.equals("file", ignoreCase = true)) {
        val path = uri.path ?: throw IllegalArgumentException("file uri without path")
        FileInputStream(File(path))
    } else {
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException(context.getString(R.string.cannot_open_uri_input_stream))
    }

    input.use { stream ->
        val buffer = ByteArray(64 * 1024)
        val out = ByteArrayOutputStream()
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            if (out.size() + read > maxBytes) {
                throw IllegalArgumentException("Input exceeds limit: $maxBytes bytes")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
