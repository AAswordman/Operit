package com.ai.assistance.operit.ui.features.automation.websession.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.BrowserDownloadFilter
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.BrowserDownloadItem
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.BrowserDownloadUiState
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.formatBytes

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WebSessionDownloadSheet(
    uiState: BrowserDownloadUiState,
    onFilterChange: (BrowserDownloadFilter) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadLocation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCount = remember(uiState.tasks) { uiState.tasks.count { it.status in setOf("queued", "connecting", "downloading") } }
    val failedCount = remember(uiState.tasks) { uiState.tasks.count { it.status == "failed" } }
    val filteredTasks =
        remember(uiState.tasks, uiState.selectedFilter) {
            uiState.tasks.filter { item ->
                when (uiState.selectedFilter) {
                    BrowserDownloadFilter.IN_PROGRESS ->
                        item.status in setOf("queued", "connecting", "downloading", "paused", "canceled")
                    BrowserDownloadFilter.COMPLETED -> item.status == "completed"
                    BrowserDownloadFilter.FAILED -> item.status == "failed"
                }
            }
        }

    WebSessionSheetScaffold(
        title = stringResource(R.string.web_session_downloads),
        subtitle = stringResource(R.string.web_session_downloads_summary, activeCount, failedCount),
        modifier = modifier
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BrowserDownloadFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter == uiState.selectedFilter,
                    onClick = { onFilterChange(filter) },
                    label = {
                        Text(
                            text =
                                when (filter) {
                                    BrowserDownloadFilter.IN_PROGRESS -> stringResource(R.string.web_session_downloads_filter_in_progress)
                                    BrowserDownloadFilter.COMPLETED -> stringResource(R.string.web_session_downloads_filter_completed)
                                    BrowserDownloadFilter.FAILED -> stringResource(R.string.web_session_downloads_filter_failed)
                                }
                        )
                    }
                )
            }
        }

        if (filteredTasks.isEmpty()) {
            WebSessionEmptyState(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.web_session_downloads_empty_title),
                message = stringResource(R.string.web_session_downloads_empty_message)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(filteredTasks, key = { it.id }) { item ->
                    BrowserDownloadTaskCard(
                        item = item,
                        onPauseDownload = onPauseDownload,
                        onResumeDownload = onResumeDownload,
                        onCancelDownload = onCancelDownload,
                        onRetryDownload = onRetryDownload,
                        onDeleteDownload = onDeleteDownload,
                        onOpenDownloadedFile = onOpenDownloadedFile,
                        onOpenDownloadLocation = onOpenDownloadLocation
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowserDownloadTaskCard(
    item: BrowserDownloadItem,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadLocation: (String) -> Unit
) {
    WebSessionItemCard(
        highlighted = item.status == "downloading" || item.status == "connecting"
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusLabel(item),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(item)
            )
            Text(
                text = itemProgressText(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.progress != null) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }
            Text(
                text = item.destinationPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.canPause) {
                    TextButton(onClick = { onPauseDownload(item.id) }) {
                        Text(stringResource(R.string.web_session_download_pause))
                    }
                }
                if (item.canResume) {
                    TextButton(onClick = { onResumeDownload(item.id) }) {
                        Text(stringResource(R.string.web_session_download_resume))
                    }
                }
                if (item.canCancel) {
                    TextButton(onClick = { onCancelDownload(item.id) }) {
                        Text(stringResource(R.string.web_session_download_cancel))
                    }
                }
                if (item.canRetry) {
                    TextButton(onClick = { onRetryDownload(item.id) }) {
                        Text(stringResource(R.string.web_session_download_retry))
                    }
                }
                if (item.canOpenFile) {
                    TextButton(onClick = { onOpenDownloadedFile(item.id) }) {
                        Text(stringResource(R.string.web_session_download_open_file))
                    }
                }
                if (item.canOpenLocation) {
                    TextButton(onClick = { onOpenDownloadLocation(item.id) }) {
                        Text(stringResource(R.string.web_session_download_open_location))
                    }
                }
                if (item.canDelete) {
                    TextButton(onClick = { onDeleteDownload(item.id, false) }) {
                        Text(stringResource(R.string.web_session_download_delete_record))
                    }
                }
                if (item.canDelete && item.canDeleteFile) {
                    TextButton(onClick = { onDeleteDownload(item.id, true) }) {
                        Text(stringResource(R.string.web_session_download_delete_file_and_record))
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(item: BrowserDownloadItem): String =
    when (item.status) {
        "queued" -> stringResource(R.string.web_session_download_status_queued)
        "connecting" -> stringResource(R.string.web_session_download_status_connecting)
        "downloading" -> stringResource(R.string.web_session_download_status_downloading)
        "paused" -> stringResource(R.string.web_session_download_status_paused)
        "completed" -> stringResource(R.string.web_session_download_status_completed)
        "failed" -> stringResource(R.string.web_session_download_status_failed)
        "canceled" -> stringResource(R.string.web_session_download_status_canceled)
        else -> item.status
    }

@Composable
private fun statusColor(item: BrowserDownloadItem) =
    when (item.status) {
        "failed" -> MaterialTheme.colorScheme.error
        "completed" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun itemProgressText(item: BrowserDownloadItem): String {
    val base =
        if (item.totalBytes > 0L) {
            "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
        } else {
            formatBytes(item.downloadedBytes)
        }
    val progressText =
        item.progress?.let { progress ->
            "${(progress * 100f).toInt()}% $base"
        } ?: base
    return if (item.speedBytesPerSecond > 0L) {
        "$progressText  |  ${formatBytes(item.speedBytesPerSecond)}/s"
    } else {
        progressText
    }
}
