package com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer

import android.text.format.Formatter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.files.transfer.FileCopyPhase

@Composable
internal fun WorkspaceCopyProgressDialog(progress: WorkspaceCopyProgress, cancelling: Boolean, onCancel: () -> Unit) {
    val context = LocalContext.current
    val detail = progress.detail
    val fraction = detail?.fraction?.let { if (progress.moving) it.coerceAtMost(0.999f) else it }
    val unknown = "—"
    fun size(bytes: Long): String = Formatter.formatFileSize(context, bytes)
    val currentFile = if (detail == null) progress.source.trimEnd('/').substringAfterLast('/') else detail.currentFile
    val status = when {
        progress.finishing -> R.string.workspace_transfer_finishing
        detail?.phase == FileCopyPhase.SCANNING -> R.string.workspace_transfer_scanning
        detail == null -> R.string.workspace_transfer_processing
        progress.moving -> R.string.workspace_transfer_moving
        else -> R.string.workspace_transfer_copying
    }
    WorkspaceFileOperationDialog(
        title = stringResource(if (progress.moving) R.string.workspace_transfer_moving else R.string.workspace_transfer_copying),
        status = stringResource(status),
        fraction = fraction,
        cancelling = cancelling,
        onCancel = onCancel,
    ) {
        if (progress.itemCount > 1) {
            Text(stringResource(R.string.workspace_transfer_item, progress.itemIndex, progress.itemCount),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_name), currentFile)
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_source), "${progress.sourceEnvironment}:${progress.source}")
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_destination), "${progress.destinationEnvironment}:${progress.destination}")
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_copied),
            if (detail == null) unknown else "${size(detail.copiedBytes)} / ${detail.totalBytes?.let(::size) ?: unknown}")
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_remaining), detail?.remainingBytes?.let(::size) ?: unknown)
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_speed), detail?.bytesPerSecond?.let { "${size(it.toLong())}/s" } ?: unknown)
        if (detail != null) {
            Text(stringResource(R.string.workspace_transfer_files, detail.copiedFiles, detail.totalFiles),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
