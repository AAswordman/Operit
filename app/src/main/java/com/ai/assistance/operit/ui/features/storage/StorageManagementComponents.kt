package com.ai.assistance.operit.ui.features.storage

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.data.storage.formatStorageTimestamp
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import kotlinx.coroutines.delay

data class StorageJobState(
    val running: Boolean = false,
    val title: String = "",
    val currentName: String = "",
    val processed: Int = 0,
    val total: Int = 0,
    val releasedBytes: Long = 0L,
    val failed: Int = 0,
    val done: Boolean = false,
    val currentItemProgress: Float = 0f,
    val currentItemDeletedBytes: Long = 0L,
    val currentItemTotalBytes: Long = 0L,
) {
    val overallProgress: Float
        get()
            {
                if (total <= 0) return 0f
                val completed = processed.coerceAtLeast(0).toFloat()
                val current = currentItemProgress.coerceIn(0f, 1f)
                return ((completed + current) / total.toFloat()).coerceIn(0f, 1f)
            }
}

data class StorageChip(
    val id: String,
    val label: String,
)

data class StorageStatusTag(
    val label: String,
    val emphasis: Boolean = false,
)

@Composable
fun StorageManageScaffold(
    isBusy: Boolean,
    errorMessage: String?,
    selectedCount: Int = 0,
    bottomBar: @Composable () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val showBottomBar = selectedCount > 0
    val bottomPadding by animateDpAsState(
        targetValue = if (showBottomBar) 108.dp else 24.dp,
        label = "storageBottomPadding",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                if (isBusy) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().clip(CircleShape),
                        )
                    }
                }
                if (!errorMessage.isNullOrBlank()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = errorMessage,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                content()
            },
        )
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                bottomBar()
            }
        }
    }
}

@Composable
fun StorageSummaryCard(
    icon: ImageVector,
    title: String,
    primaryValue: String,
    extras: List<String>,
    scannedAtMillis: Long?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = storageSummaryColor(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingContent != null) {
                    leadingContent()
                } else {
                    Box(
                        modifier =
                            Modifier.size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    scannedAtMillis?.takeIf { it > 0L }?.let { scannedAt ->
                        Text(
                            text = stringResource(
                                R.string.data_storage_last_scan,
                                formatStorageTimestamp(scannedAt),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                actions()
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.data_storage_refresh),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            extras.forEach { extra ->
                Text(
                    text = extra,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StorageFilterRow(
    chips: List<StorageChip>,
    selectedId: String,
    onSelect: (String) -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            FilterChip(
                selected = selectedId == chip.id,
                onClick = { onSelect(chip.id) },
                label = { Text(chip.label) },
            )
        }
        trailing()
    }
}

@Composable
fun StorageSelectableRow(
    selected: Boolean,
    enabled: Boolean,
    locked: Boolean,
    title: String,
    subtitle: String,
    bytes: Long,
    note: String? = null,
    tags: List<String> = emptyList(),
    statusTags: List<StorageStatusTag> = emptyList(),
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    leadingInitial: String? = null,
    leadingCircular: Boolean = true,
    showBytes: Boolean = true,
    empty: Boolean = false,
    expanded: Boolean? = null,
    indent: Boolean = false,
    onToggle: () -> Unit,
    onExpand: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val resolvedTags =
        if (statusTags.isNotEmpty()) {
            statusTags
        } else {
            tags.map { StorageStatusTag(it) }
        }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 18.dp else 0.dp)
            .clickable(enabled = enabled || onExpand != null) {
                if (enabled) onToggle() else onExpand?.invoke()
            },
        shape = RoundedCornerShape(20.dp),
        color = storagePanelColor(),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (locked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    enabled = enabled && !empty,
                    colors = CheckboxDefaults.colors(
                        disabledUncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                        disabledCheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    ),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            when {
                leadingContent != null -> leadingContent()
                leadingPainter != null || leadingIcon != null || !leadingInitial.isNullOrBlank() -> {
                    StorageLeadingMark(
                        painter = leadingPainter,
                        icon = leadingIcon,
                        initial = leadingInitial ?: title,
                        circular = leadingCircular,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (resolvedTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        resolvedTags.take(3).forEach { tag ->
                            StorageStatusBadge(tag)
                        }
                    }
                }
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (locked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (showBytes) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatStorageSize(bytes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (empty) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (expanded != null && onExpand != null) {
                IconButton(onClick = onExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
fun StorageLeadingMark(
    painter: Painter? = null,
    icon: ImageVector? = null,
    initial: String? = null,
    size: Dp = 40.dp,
    circular: Boolean = true,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(12.dp)
    Box(
        modifier =
            Modifier.size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        val asyncPainter = painter as? AsyncImagePainter
        val painterReady = painter != null && (asyncPainter == null || asyncPainter.state is AsyncImagePainter.State.Success)
        when {
            painterReady && painter != null -> {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(if (circular) 0.dp else 4.dp),
                    contentScale = if (circular) ContentScale.Crop else ContentScale.Fit,
                )
            }
            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.52f),
                )
            }
            else -> {
                Text(
                    text = initial.orEmpty().trim().take(1).ifBlank { "?" }.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun rememberStorageAvatarPainter(avatarUri: String?): Painter? {
    if (avatarUri.isNullOrBlank()) return null
    return rememberAsyncImagePainter(model = Uri.parse(avatarUri))
}

@Composable
private fun StorageStatusBadge(tag: StorageStatusTag) {
    val container =
        if (tag.emphasis) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        }
    val content =
        if (tag.emphasis) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
    ) {
        Text(
            text = tag.label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = if (tag.emphasis) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun StorageSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = storagePanelColor(),
        tonalElevation = 1.dp,
        content = { Column(content = content) },
    )
}

@Composable
fun StorageJobCard(state: StorageJobState) {
    if (!state.running && !state.done) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = storagePanelColor(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = state.title.ifBlank { stringResource(R.string.data_storage_job_running) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.overallProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.data_storage_job_progress,
                    state.processed,
                    state.total.coerceAtLeast(1),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.currentName.isNotBlank()) {
                Text(
                    text = state.currentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    R.string.data_storage_job_released,
                    formatStorageSize(state.releasedBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.failed > 0) {
                Text(
                    text = stringResource(R.string.data_storage_job_failed_count, state.failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun StorageDeleteProgressDialog(
    state: StorageJobState,
    title: String = stringResource(R.string.data_storage_delete_progress_title),
) {
    if (!state.running) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        R.string.data_storage_delete_progress_overall,
                        (state.overallProgress * 100).toInt().coerceIn(0, 100),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { state.overallProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
                Text(
                    text = stringResource(
                        R.string.data_storage_delete_progress_item,
                        (state.processed + 1).coerceAtMost(state.total.coerceAtLeast(1)),
                        state.total.coerceAtLeast(1),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.currentName.isNotBlank()) {
                    Text(
                        text = state.currentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LinearProgressIndicator(
                    progress = { state.currentItemProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
                Text(
                    text = stringResource(
                        R.string.data_storage_delete_progress_current,
                        (state.currentItemProgress * 100).toInt().coerceIn(0, 100),
                        formatStorageSize(state.currentItemDeletedBytes),
                        formatStorageSize(state.currentItemTotalBytes),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
fun StorageEmptyCard(
    text: String,
    icon: ImageVector = Icons.Default.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = storagePanelColor(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier.size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun StorageBottomBar(
    selectedCount: Int,
    selectedBytes: Long,
    enabled: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.data_storage_selected_count, selectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.data_storage_selected_bytes,
                        formatStorageSize(selectedBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onAction,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun StorageConfirmDialog(
    title: String,
    message: String,
    warnings: List<String> = emptyList(),
    confirmLabel: String,
    countdownSeconds: Int = 5,
    typedPhrase: String? = null,
    typedHint: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(countdownSeconds.coerceAtLeast(0)) }
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(countdownSeconds) {
        remaining = countdownSeconds.coerceAtLeast(0)
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    val typedOk = typedPhrase.isNullOrBlank() || typed.trim() == typedPhrase
    val enabled = remaining == 0 && typedOk
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message)
                warnings.forEach { warning ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (!typedPhrase.isNullOrBlank()) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(typedHint ?: typedPhrase) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled) {
                Text(
                    text = if (remaining > 0) {
                        stringResource(R.string.data_storage_confirm_countdown, remaining)
                    } else {
                        confirmLabel
                    },
                    color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun storagePanelColor(): Color {
    return if (LocalThemePreferenceSnapshot.current.useBackgroundImage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    }
}

@Composable
private fun storageSummaryColor(): Color {
    return if (LocalThemePreferenceSnapshot.current.useBackgroundImage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
    }
}
