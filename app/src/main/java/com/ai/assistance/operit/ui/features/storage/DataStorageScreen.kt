package com.ai.assistance.operit.ui.features.storage

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.standard.CookiePrivacyManager
import com.ai.assistance.operit.data.storage.CleanupTarget
import com.ai.assistance.operit.data.storage.CleanupTargetUsage
import com.ai.assistance.operit.data.storage.DataStorageSnapshot
import com.ai.assistance.operit.data.storage.StorageCategory
import com.ai.assistance.operit.data.storage.StorageCategoryUsage
import com.ai.assistance.operit.data.storage.StorageDetail
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.util.AppLogger
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DataStorageScreen(
    onManageCategory: (StorageCategory) -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(context) { DataStorageViewModel.Factory(context) }
    val storageViewModel: DataStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTargets by remember { mutableStateOf<Set<CleanupTarget>>(emptySet()) }
    var showCleanupConfirmation by remember { mutableStateOf(false) }
    var showClearCookieConfirm by remember { mutableStateOf(false) }

    val snapshot = state.snapshot
    val displayedCategories =
        snapshot?.let { displayCategoryUsages(it.categories) }.orEmpty()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                storageViewModel.refreshIfInvalidated()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snapshot?.scannedAtMillis) {
        val availableTargets =
            snapshot?.cleanupTargets
                ?.filter { it.fileCount > 0L }
                ?.mapTo(mutableSetOf()) { it.target }
                .orEmpty()
        selectedTargets = selectedTargets.intersect(availableTargets)
    }

    LaunchedEffect(state.cleanupResult) {
        val result = state.cleanupResult ?: return@LaunchedEffect
        selectedTargets = emptySet()
        val message =
            if (result.failedEntryCount == 0) {
                context.getString(
                    R.string.data_storage_cleanup_result,
                    formatStorageSize(result.deletedBytes),
                    result.deletedFileCount,
                )
            } else {
                context.getString(
                    R.string.data_storage_cleanup_partial_result,
                    formatStorageSize(result.deletedBytes),
                    result.deletedFileCount,
                    result.failedEntryCount,
                )
            }
        snackbarHostState.showSnackbar(message)
        storageViewModel.consumeCleanupResult()
    }

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isScanning && snapshot != null) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    )
                }
            }

            if (snapshot == null) {
                item {
                    InitialScanState(
                        isScanning = state.isScanning,
                        errorMessage = state.errorMessage,
                        onRetry = storageViewModel::refresh,
                    )
                }
            } else {
                item {
                    StorageOverview(
                        categories = displayedCategories,
                        isRefreshing = state.isScanning || state.isCleaning,
                        onRefresh = storageViewModel::refresh,
                    )
                }

                if (state.errorMessage != null) {
                    item {
                        ScanErrorNotice(
                            errorMessage = state.errorMessage,
                            onRetry = storageViewModel::refresh,
                        )
                    }
                }

                if (
                    snapshot.inaccessibleEntryCount > 0 ||
                        snapshot.skippedSymbolicLinkCount > 0
                ) {
                    item { PartialScanNotice(snapshot) }
                }

                item {
                    StorageCategories(
                        categories = displayedCategories,
                        onManageCategory = onManageCategory,
                    )
                }

                item {
                    CleanupSection(
                        targets = snapshot.cleanupTargets,
                        selectedTargets = selectedTargets,
                        isScanning = state.isScanning,
                        isCleaning = state.isCleaning,
                        onTargetChanged = { target, selected ->
                            selectedTargets =
                                if (selected) selectedTargets + target else selectedTargets - target
                        },
                        onCleanup = { showCleanupConfirmation = true },
                        onClearCookies = { showClearCookieConfirm = true },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }

    if (showClearCookieConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCookieConfirm = false },
            title = { Text(stringResource(R.string.clear_cookies_dialog_title)) },
            text = { Text(stringResource(R.string.clear_cookies_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCookieConfirm = false
                        scope.launch {
                            val message =
                                try {
                                    CookiePrivacyManager.clearAllCookies()
                                    context.getString(R.string.clear_cookies_success)
                                } catch (error: Exception) {
                                    AppLogger.e("DataStorageScreen", "Failed to clear cookies", error)
                                    context.getString(R.string.clear_cookies_failed)
                                }
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                ) {
                    Text(stringResource(R.string.clear_cookies_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCookieConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showCleanupConfirmation) {
        CleanupConfirmationDialog(
            selectedTargets = selectedTargets,
            targetUsages = snapshot?.cleanupTargets.orEmpty(),
            isCleaning = state.isCleaning,
            onDismiss = { showCleanupConfirmation = false },
            onConfirm = {
                showCleanupConfirmation = false
                storageViewModel.cleanup(selectedTargets)
            },
        )
    }
    }
}

@Composable
private fun InitialScanState(
    isScanning: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    val containerColor = groupedContainerColor()
    Surface(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.data_storage_scanning),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.data_storage_scan_failed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.data_storage_retry))
                }
            }
        }
    }
}

@Composable
private fun StorageOverview(
    categories: List<StorageCategoryUsage>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val locale = Locale.getDefault()
    val totalBytes = categories.sumOf { it.bytes.coerceAtLeast(0L) }
    val colors = storageCategoryColors()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = overviewContainerColor(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.data_storage_overview_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.data_storage_refresh),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = formatStorageSize(totalBytes, locale),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.data_storage_operit_tracked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            StorageCategorySegmentBar(categories, colors)
            Spacer(modifier = Modifier.height(14.dp))
            StorageCategoryLegend(categories, colors, locale)
        }
    }
}

@Composable
private fun StorageCategorySegmentBar(
    categories: List<StorageCategoryUsage>,
    colors: Map<StorageCategory, Color>,
) {
    val totalBytes = categories.sumOf { it.bytes.coerceAtLeast(0L) }
    val positiveCategories = categories.filter { it.bytes > 0L }

    if (positiveCategories.isEmpty() || totalBytes <= 0L) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        positiveCategories.forEach { usage ->
            Box(
                modifier =
                    Modifier.weight((usage.bytes.toDouble() / totalBytes).toFloat())
                        .fillMaxHeight()
                        .background(colors.getValue(usage.category)),
            )
        }
    }
}

@Composable
private fun StorageCategoryLegend(
    categories: List<StorageCategoryUsage>,
    colors: Map<StorageCategory, Color>,
    locale: Locale,
) {
    val items =
        categories.map { usage ->
            LegendValue(
                label = stringResource(usage.category.titleRes),
                value = formatStorageSize(usage.bytes, locale),
                color = colors.getValue(usage.category),
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item -> StorageLegendItem(item) }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.StorageLegendItem(item: LegendValue) {
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier.padding(top = 5.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(item.color),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageCategories(
    categories: List<StorageCategoryUsage>,
    onManageCategory: (StorageCategory) -> Unit,
) {
    val totalBytes = categories.sumOf { it.bytes.coerceAtLeast(0L) }
    val colors = storageCategoryColors()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.data_storage_categories_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        categories.forEach { usage ->
            StorageCategoryCard(
                usage = usage,
                totalBytes = totalBytes,
                accent = colors.getValue(usage.category),
                onClick = { onManageCategory(usage.category) },
            )
        }
    }
}

@Composable
private fun StorageCategoryCard(
    usage: StorageCategoryUsage,
    totalBytes: Long,
    accent: Color,
    onClick: () -> Unit,
) {
    val categoryTitle = stringResource(usage.category.titleRes)
    val fraction =
        if (totalBytes > 0L) {
            (usage.bytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    val percentage = (fraction * 100f).roundToInt()

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = groupedContainerColor(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier.size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = usage.category.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = categoryTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = categorySummary(usage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatStorageSize(usage.bytes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription =
                            stringResource(R.string.data_storage_manage_category, categoryTitle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.14f),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CleanupSection(
    targets: List<CleanupTargetUsage>,
    selectedTargets: Set<CleanupTarget>,
    isScanning: Boolean,
    isCleaning: Boolean,
    onTargetChanged: (CleanupTarget, Boolean) -> Unit,
    onCleanup: () -> Unit,
    onClearCookies: () -> Unit,
) {
    val selectedBytes = targets.filter { it.target in selectedTargets }.sumOf { it.bytes }
    val hasCleanableFiles = targets.any { it.fileCount > 0L }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = groupedContainerColor(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.data_storage_cleanup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.data_storage_cleanup_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (hasCleanableFiles) {
                targets.forEachIndexed { index, usage ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                    CleanupTargetRow(
                        usage = usage,
                        checked = usage.target in selectedTargets,
                        enabled = !isScanning && !isCleaning && usage.fileCount > 0L,
                        onCheckedChange = { selected -> onTargetChanged(usage.target, selected) },
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.data_storage_nothing_to_clean),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            Text(
                text = stringResource(R.string.data_storage_cleanup_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            OutlinedButton(
                onClick = onClearCookies,
                enabled = !isScanning && !isCleaning,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.settings_clear_cookies))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onCleanup,
                enabled = selectedTargets.isNotEmpty() && !isScanning && !isCleaning,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                if (isCleaning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.data_storage_cleanup_running))
                } else if (selectedTargets.isEmpty()) {
                    Text(stringResource(R.string.data_storage_cleanup_select))
                } else {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.data_storage_cleanup_action,
                            formatStorageSize(selectedBytes),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanupTargetRow(
    usage: CleanupTargetUsage,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(
                text = stringResource(usage.target.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(usage.target.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatStorageSize(usage.bytes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CleanupConfirmationDialog(
    selectedTargets: Set<CleanupTarget>,
    targetUsages: List<CleanupTargetUsage>,
    isCleaning: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val selectedBytes = targetUsages.filter { it.target in selectedTargets }.sumOf { it.bytes }
    AlertDialog(
        onDismissRequest = { if (!isCleaning) onDismiss() },
        title = { Text(stringResource(R.string.data_storage_cleanup_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.data_storage_cleanup_confirm_message,
                        formatStorageSize(selectedBytes),
                    ),
                )
                Text(
                    text = stringResource(R.string.data_storage_cleanup_confirm_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isCleaning) {
                Text(
                    text = stringResource(R.string.data_storage_cleanup_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCleaning) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun PartialScanNotice(snapshot: DataStorageSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.data_storage_partial_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        stringResource(
                            R.string.data_storage_partial_description,
                            snapshot.inaccessibleEntryCount,
                            snapshot.skippedSymbolicLinkCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ScanErrorNotice(
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text =
                    errorMessage?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.data_storage_scan_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.data_storage_retry),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

private val mainStorageCategoryOrder =
    listOf(
        StorageCategory.LINUX_ENVIRONMENT,
        StorageCategory.LOCAL_MODELS,
        StorageCategory.WORKSPACES_AND_MEDIA,
        StorageCategory.CHAT_HISTORY,
        StorageCategory.MEMORY_LIBRARY,
        StorageCategory.BACKUPS_AND_EXPORTS,
        StorageCategory.CONFIGURATION,
    )

private fun displayCategoryUsages(categories: List<StorageCategoryUsage>): List<StorageCategoryUsage> {
    val byCategory = categories.associateBy { it.category }
    val roleResources =
        mergeCategoryUsages(
            category = StorageCategory.CONFIGURATION,
            first = byCategory[StorageCategory.CONFIGURATION],
            second = byCategory[StorageCategory.PACKAGES_AND_PLUGINS],
        )

    return mainStorageCategoryOrder.map { category ->
        if (category == StorageCategory.CONFIGURATION) {
            roleResources
        } else {
            byCategory[category] ?: emptyCategoryUsage(category)
        }
    }
}

private fun emptyCategoryUsage(category: StorageCategory): StorageCategoryUsage =
    StorageCategoryUsage(category = category, bytes = 0L, fileCount = 0L, inaccessibleEntryCount = 0)

private fun mergeCategoryUsages(
    category: StorageCategory,
    first: StorageCategoryUsage?,
    second: StorageCategoryUsage?,
): StorageCategoryUsage {
    val usages = listOfNotNull(first, second)
    val firstSecondaryCount = first?.secondaryItemCount
    val secondPrimaryCount = second?.itemCount
    val secondaryCount =
        when {
            firstSecondaryCount != null && secondPrimaryCount != null ->
                firstSecondaryCount + secondPrimaryCount
            firstSecondaryCount != null -> firstSecondaryCount
            else -> secondPrimaryCount
        }
    return StorageCategoryUsage(
        category = category,
        bytes = usages.sumOf { it.bytes.coerceAtLeast(0L) },
        fileCount = usages.sumOf { it.fileCount.coerceAtLeast(0L) },
        inaccessibleEntryCount = usages.sumOf { it.inaccessibleEntryCount.coerceAtLeast(0) },
        itemCount = first?.itemCount,
        secondaryItemCount = secondaryCount,
        details = usages.flatMap { it.details },
    )
}

@Composable
private fun categorySummary(usage: StorageCategoryUsage): String {
    val parts = mutableListOf<String>()
    when (usage.category) {
        StorageCategory.CHAT_HISTORY -> {
            if (usage.itemCount != null && usage.secondaryItemCount != null) {
                parts +=
                    stringResource(
                        R.string.data_storage_chat_counts,
                        usage.itemCount,
                        usage.secondaryItemCount,
                    )
            }
        }

        StorageCategory.MEMORY_LIBRARY -> {
            if (usage.itemCount != null && usage.secondaryItemCount != null) {
                parts +=
                    stringResource(
                        R.string.data_storage_memory_counts,
                        usage.itemCount,
                        usage.secondaryItemCount,
                    )
            }
        }

        StorageCategory.CONFIGURATION -> {
            if (usage.itemCount != null && usage.secondaryItemCount != null) {
                parts +=
                    stringResource(
                        R.string.data_storage_configuration_counts,
                        usage.itemCount,
                        usage.secondaryItemCount,
                    )
            }
        }

        else -> usage.itemCount?.let { parts += stringResource(R.string.data_storage_item_count, it) }
    }
    if (parts.isEmpty()) {
        parts += stringResource(R.string.data_storage_file_count, usage.fileCount)
    }
    if (usage.inaccessibleEntryCount > 0) {
        parts += stringResource(R.string.data_storage_unavailable_entries, usage.inaccessibleEntryCount)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun storageCategoryColors(): Map<StorageCategory, Color> {
    val colors = MaterialTheme.colorScheme
    return mapOf(
        StorageCategory.LINUX_ENVIRONMENT to colors.primary,
        StorageCategory.LOCAL_MODELS to colors.secondary,
        StorageCategory.WORKSPACES_AND_MEDIA to colors.tertiary,
        StorageCategory.CHAT_HISTORY to colors.error,
        StorageCategory.MEMORY_LIBRARY to colors.primary.copy(alpha = 0.72f),
        StorageCategory.BACKUPS_AND_EXPORTS to colors.secondary.copy(alpha = 0.72f),
        StorageCategory.CONFIGURATION to colors.tertiary.copy(alpha = 0.72f),
    )
}

@Composable
private fun groupedContainerColor(): Color {
    val hasBackgroundImage = LocalThemePreferenceSnapshot.current.useBackgroundImage
    return if (hasBackgroundImage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    }
}

@Composable
private fun overviewContainerColor(): Color {
    val hasBackgroundImage = LocalThemePreferenceSnapshot.current.useBackgroundImage
    return if (hasBackgroundImage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
    }
}

private data class LegendValue(
    val label: String,
    val value: String,
    val color: Color,
)

private val StorageCategory.titleRes: Int
    @StringRes get() =
        when (this) {
            StorageCategory.LINUX_ENVIRONMENT -> R.string.data_storage_category_linux
            StorageCategory.LOCAL_MODELS -> R.string.data_storage_category_models
            StorageCategory.WORKSPACES_AND_MEDIA -> R.string.data_storage_category_workspaces
            StorageCategory.CHAT_HISTORY -> R.string.data_storage_category_chats
            StorageCategory.MEMORY_LIBRARY -> R.string.data_storage_category_memory
            StorageCategory.BACKUPS_AND_EXPORTS -> R.string.data_storage_category_backups
            StorageCategory.CONFIGURATION -> R.string.data_storage_category_roles_resources
            StorageCategory.PACKAGES_AND_PLUGINS -> R.string.data_storage_category_packages
            StorageCategory.CACHE_AND_TEMPORARY -> R.string.data_storage_category_cache
            StorageCategory.OTHER -> R.string.data_storage_category_other
        }

private val StorageCategory.icon: ImageVector
    get() =
        when (this) {
            StorageCategory.LINUX_ENVIRONMENT -> Icons.Default.Terminal
            StorageCategory.LOCAL_MODELS -> Icons.Default.SmartToy
            StorageCategory.WORKSPACES_AND_MEDIA -> Icons.Default.Folder
            StorageCategory.CHAT_HISTORY -> Icons.Default.Forum
            StorageCategory.MEMORY_LIBRARY -> Icons.Default.Psychology
            StorageCategory.BACKUPS_AND_EXPORTS -> Icons.Default.Backup
            StorageCategory.CONFIGURATION -> Icons.Default.Extension
            StorageCategory.PACKAGES_AND_PLUGINS -> Icons.Default.Extension
            StorageCategory.CACHE_AND_TEMPORARY -> Icons.Default.CleaningServices
            StorageCategory.OTHER -> Icons.Default.MoreHoriz
        }

private val StorageDetail.titleRes: Int
    @StringRes get() =
        when (this) {
            StorageDetail.LINUX_SYSTEM -> R.string.data_storage_detail_linux_system
            StorageDetail.TERMINAL_RUNTIME -> R.string.data_storage_detail_terminal_runtime
            StorageDetail.MNN_MODELS -> R.string.data_storage_detail_mnn_models
            StorageDetail.LLAMA_MODELS -> R.string.data_storage_detail_llama_models
            StorageDetail.SPEECH_MODELS -> R.string.data_storage_detail_speech_models
            StorageDetail.OTHER_MODELS -> R.string.data_storage_detail_other_models
            StorageDetail.INTERNAL_WORKSPACES -> R.string.data_storage_detail_internal_workspaces
            StorageDetail.SHARED_WORKSPACES -> R.string.data_storage_detail_shared_workspaces
            StorageDetail.MEDIA_POOLS -> R.string.data_storage_detail_media_pools
            StorageDetail.CHAT_DATABASE -> R.string.data_storage_detail_chat_database
            StorageDetail.MEMORY_DATABASES -> R.string.data_storage_detail_memory_databases
            StorageDetail.VECTOR_INDEXES -> R.string.data_storage_detail_vector_indexes
            StorageDetail.BACKUPS -> R.string.data_storage_detail_backups
            StorageDetail.EXPORTS -> R.string.data_storage_detail_exports
            StorageDetail.PREFERENCES -> R.string.data_storage_detail_preferences
            StorageDetail.CHARACTER_ASSETS -> R.string.data_storage_detail_character_assets
            StorageDetail.PLUGIN_FILES -> R.string.data_storage_detail_plugin_files
            StorageDetail.SKILL_FILES -> R.string.data_storage_detail_skill_files
            StorageDetail.APP_CACHE -> R.string.data_storage_detail_app_cache
            StorageDetail.CODE_CACHE -> R.string.data_storage_detail_code_cache
            StorageDetail.EXTERNAL_CACHE -> R.string.data_storage_detail_external_cache
            StorageDetail.TEMPORARY_FILES -> R.string.data_storage_detail_temporary_files
            StorageDetail.LOG_FILES -> R.string.data_storage_detail_log_files
            StorageDetail.PACKAGE_CACHE -> R.string.data_storage_detail_package_cache
            StorageDetail.LINUX_PACKAGE_CACHE -> R.string.data_storage_detail_linux_package_cache
            StorageDetail.BROWSER_DATA -> R.string.data_storage_detail_browser_data
            StorageDetail.OTHER_APP_DATA -> R.string.data_storage_detail_other_app_data
            StorageDetail.OTHER_USER_FILES -> R.string.data_storage_detail_other_user_files
        }

private val CleanupTarget.titleRes: Int
    @StringRes get() =
        when (this) {
            CleanupTarget.APP_CACHE -> R.string.data_storage_cleanup_app_cache
            CleanupTarget.TEMPORARY_FILES -> R.string.data_storage_cleanup_temporary_files
            CleanupTarget.LOG_FILES -> R.string.data_storage_cleanup_logs
            CleanupTarget.PACKAGE_CACHE -> R.string.data_storage_cleanup_package_cache
            CleanupTarget.LINUX_PACKAGE_CACHE -> R.string.data_storage_cleanup_linux_package_cache
        }

private val CleanupTarget.descriptionRes: Int
    @StringRes get() =
        when (this) {
            CleanupTarget.APP_CACHE -> R.string.data_storage_cleanup_app_cache_desc
            CleanupTarget.TEMPORARY_FILES -> R.string.data_storage_cleanup_temporary_files_desc
            CleanupTarget.LOG_FILES -> R.string.data_storage_cleanup_logs_desc
            CleanupTarget.PACKAGE_CACHE -> R.string.data_storage_cleanup_package_cache_desc
            CleanupTarget.LINUX_PACKAGE_CACHE -> R.string.data_storage_cleanup_linux_package_cache_desc
        }
