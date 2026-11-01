package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 返回前台后重新检查原文件，避免用旧的预览状态判断文件仍存在。 */
@Composable
internal fun rememberMediaPreviewRevision(): Int {
    val owner = LocalLifecycleOwner.current
    var revision by remember(owner) { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return revision
}

/** 原文件可读时才提供预览地址，不从发送用媒体池恢复新附件。 */
internal fun mediaPreviewUri(context: Context, source: String): Uri? {
    return try {
        when {
            source.startsWith("content://") -> {
                val uri = Uri.parse(source)
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { uri }
            }
            source.startsWith("file://") -> {
                val uri = Uri.parse(source)
                uri.path?.takeIf { File(it).isFile }?.let { uri }
            }
            else -> File(source).takeIf { it.isFile }?.let(Uri::fromFile)
        }
    } catch (error: Exception) {
        AppLogger.e("ChatMediaPreview", "读取媒体原文件失败：$source", error)
        null
    }
}

@Composable
internal fun MediaFileUnavailableTag(
    fileName: String?,
    modifier: Modifier = Modifier,
    missing: Boolean = true,
) {
    val context = LocalContext.current
    val message = stringResource(if (missing) R.string.chat_media_file_missing else R.string.chat_media_preview_failed)
    Surface(
        modifier = modifier.clickable { OKToast.warning(context, message).show() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Description, null, Modifier.size(18.dp))
            Column {
                if (!fileName.isNullOrBlank()) Text(
                    fileName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 父布局和播放器共用文件状态，文件失效时可以同时移除气泡与播放资源。 */
@Composable
internal fun rememberMediaFileMissing(source: String?, mimeType: String): MutableState<Boolean> {
    val context = LocalContext.current
    val revision = rememberMediaPreviewRevision()
    val missing = remember(source) { mutableStateOf(false) }
    LaunchedEffect(source, mimeType, revision) {
        missing.value = source != null &&
            (mimeType.startsWith("image/", true) || mimeType.startsWith("audio/", true) || mimeType.startsWith("video/", true)) &&
            (source.startsWith("/") || source.startsWith("file://") || source.startsWith("content://")) &&
            withContext(Dispatchers.IO) { mediaPreviewUri(context, source) == null }
    }
    return missing
}

/** 正常文件使用原界面；文件消失后整体替换成标签，点击时再次核实。 */
@Composable
internal fun MediaFileGuard(
    source: String,
    fileName: String,
    mimeType: String,
    modifier: Modifier = Modifier,
    missingState: MutableState<Boolean> = rememberMediaFileMissing(source, mimeType),
    content: @Composable (checkFile: (() -> Unit) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracked = (mimeType.startsWith("image/", true) || mimeType.startsWith("audio/", true) || mimeType.startsWith("video/", true)) &&
        (source.startsWith("/") || source.startsWith("file://") || source.startsWith("content://"))
    var missing by missingState
    if (missing) {
        MediaFileUnavailableTag(fileName, modifier)
        return
    }
    content { action ->
        if (!tracked) action()
        else scope.launch {
            if (withContext(Dispatchers.IO) { mediaPreviewUri(context, source) != null }) action()
            else {
                missing = true
                OKToast.warning(context, context.getString(R.string.chat_media_file_missing)).show()
            }
        }
    }
}