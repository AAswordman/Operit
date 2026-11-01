package com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R

@Composable
internal fun WorkspaceDeleteProgressDialog(progress: WorkspaceDeleteProgress, cancelling: Boolean, onCancel: () -> Unit) {
    val target = progress.target
    WorkspaceFileOperationDialog(
        title = stringResource(R.string.workspace_transfer_deleting),
        status = stringResource(R.string.workspace_transfer_deleting),
        fraction = progress.fraction,
        cancelling = cancelling,
        onCancel = onCancel,
    ) {
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_name), target.path.trimEnd('/').substringAfterLast('/'))
        WorkspaceOperationDetail(stringResource(R.string.workspace_transfer_source), "${target.environment ?: "android"}:${target.path}")
        Text(stringResource(R.string.workspace_transfer_deleted_items, progress.completedItems, progress.totalItems),
            style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.workspace_transfer_delete_scope),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.workspace_transfer_delete_cancel_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
