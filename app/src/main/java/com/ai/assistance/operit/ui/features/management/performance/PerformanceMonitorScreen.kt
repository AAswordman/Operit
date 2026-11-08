package com.ai.assistance.operit.ui.features.management.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.performance.PerformanceEntityKind
import com.ai.assistance.operit.core.performance.PerformanceEntitySample
import com.ai.assistance.operit.core.performance.PerformanceMonitorManager
import com.ai.assistance.operit.core.performance.PerformanceSnapshot
import com.ai.assistance.operit.ui.main.LocalTopBarActions
import com.ai.assistance.operit.ui.main.LocalTopBarTitleContent
import com.ai.assistance.operit.ui.main.components.LocalAppBarContentColor
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import java.util.Locale
import kotlin.math.abs

private enum class PerformanceTab(val titleRes: Int) {
    CPU(R.string.perf_tab_cpu),
    MEMORY(R.string.perf_tab_memory),
    NETWORK(R.string.perf_tab_network)
}

/**
 * 性能分析界面：类似桌面任务管理器，按 CPU / 内存 / 网络三个分析项切换，
 * 每项都按 软件 / 插件 / 终端 拆分，方便横向对比。
 * 界面存活期间驱动 [PerformanceMonitorManager] 采样，离开即停止。
 */
@Composable
fun PerformanceMonitorScreen() {
    val context = LocalContext.current
    val state by PerformanceMonitorManager.stateFlow.collectAsState()

    val setTopBarActions = LocalTopBarActions.current
    val setTopBarTitleContent = LocalTopBarTitleContent.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val appBarContentColor = LocalAppBarContentColor.current

    DisposableEffect(Unit) {
        PerformanceMonitorManager.start(context)
        onDispose { PerformanceMonitorManager.stop() }
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = PerformanceTab.entries[selectedTabIndex]

    // 页面只申请 TopAppBar 的 actions；返回键和标题由 app 级统一 AppBar 管理。
    LaunchedEffect(isCurrentScreen, state.paused, appBarContentColor) {
        if (!isCurrentScreen) {
            setTopBarActions {}
            return@LaunchedEffect
        }
        setTopBarTitleContent(null)
        setTopBarActions {
            IconButton(
                onClick = { PerformanceMonitorManager.setPaused(!state.paused) }
            ) {
                Icon(
                    imageVector =
                        if (state.paused) {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.Default.Pause
                        },
                    contentDescription =
                        stringResource(
                            if (state.paused) {
                                R.string.perf_action_resume
                            } else {
                                R.string.perf_action_pause
                            }
                        ),
                    tint = appBarContentColor
                )
            }
        }
    }

    CustomScaffold() { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                PerformanceTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(stringResource(tab.titleRes)) }
                    )
                }
            }

            val latest = state.latest
            if (latest == null) {
                SamplingHint()
            } else {
                when (selectedTab) {
                    PerformanceTab.CPU -> CpuPage(state.history, latest)
                    PerformanceTab.MEMORY -> MemoryPage(state.history, latest)
                    PerformanceTab.NETWORK -> NetworkPage(state.history, latest)
                }
            }
        }
    }
}

// ————————————————————————————— CPU —————————————————————————————

@Composable
private fun CpuPage(history: List<PerformanceSnapshot>, latest: PerformanceSnapshot) {
    val deviceColor = MaterialTheme.colorScheme.outline
    val appColor = MaterialTheme.colorScheme.primary
    val pluginColor = MaterialTheme.colorScheme.tertiary
    val terminalColor = MaterialTheme.colorScheme.secondary

    val appLabel = stringResource(R.string.perf_kind_app)
    val pluginLabel = stringResource(R.string.perf_kind_plugin)
    val terminalLabel = stringResource(R.string.perf_kind_terminal)
    val deviceLabel = stringResource(R.string.perf_kind_device)

    val series =
        remember(appLabel, pluginLabel, terminalLabel, deviceLabel) {
            listOf(
                PerformanceChartSeries(deviceLabel, deviceColor) { it.deviceCpuPercent },
                PerformanceChartSeries(appLabel, appColor) { snapshot ->
                    snapshot.entity(PerformanceEntityKind.APP)?.cpuPercent
                },
                PerformanceChartSeries(pluginLabel, pluginColor) { snapshot ->
                    snapshot.entities
                        .filter { entity -> entity.kind == PerformanceEntityKind.PLUGIN }
                        .takeIf { it.isNotEmpty() }
                        ?.sumOf { entity -> entity.cpuPercent }
                },
                PerformanceChartSeries(terminalLabel, terminalColor) { snapshot ->
                    snapshot.entities
                        .filter { entity -> entity.kind == PerformanceEntityKind.TERMINAL }
                        .takeIf { it.isNotEmpty() }
                        ?.sumOf { entity -> entity.cpuPercent }
                }
            )
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DeviceHeaderCard(
                title = stringResource(R.string.perf_cpu_device),
                value =
                    latest.deviceCpuPercent?.let { percent ->
                        String.format(Locale.US, "%.1f%%", percent)
                    } ?: "—",
                subtitle = stringResource(R.string.perf_cpu_cores, latest.cpuCoreCount),
                progress = (latest.deviceCpuPercent ?: 0.0).toFloat() / 100f
            )
        }
        item {
            ChartCard {
                PerformanceLiveChart(
                    history = history,
                    series = series,
                    fixedMax = 100.0,
                    valueFormatter = { value -> "${value.toInt()}%" }
                )
            }
        }
        entityListItems(latest, PerformanceTab.CPU)
    }
}

// ————————————————————————————— 内存 —————————————————————————————

@Composable
private fun MemoryPage(history: List<PerformanceSnapshot>, latest: PerformanceSnapshot) {
    val appColor = MaterialTheme.colorScheme.primary
    val pluginColor = MaterialTheme.colorScheme.tertiary
    val terminalColor = MaterialTheme.colorScheme.secondary

    val appLabel = stringResource(R.string.perf_kind_app)
    val pluginLabel = stringResource(R.string.perf_kind_plugin)
    val terminalLabel = stringResource(R.string.perf_kind_terminal)

    val series =
        remember(appLabel, pluginLabel, terminalLabel) {
            listOf(
                PerformanceChartSeries(appLabel, appColor) { snapshot ->
                    snapshot.entity(PerformanceEntityKind.APP)?.memoryKb?.toDouble()
                },
                PerformanceChartSeries(pluginLabel, pluginColor) { snapshot ->
                    snapshot.entities
                        .filter { entity -> entity.kind == PerformanceEntityKind.PLUGIN }
                        .takeIf { it.isNotEmpty() }
                        ?.sumOf { entity -> entity.memoryKb }?.toDouble()
                },
                PerformanceChartSeries(terminalLabel, terminalColor) { snapshot ->
                    snapshot.entities
                        .filter { entity -> entity.kind == PerformanceEntityKind.TERMINAL }
                        .takeIf { it.isNotEmpty() }
                        ?.sumOf { entity -> entity.memoryKb }?.toDouble()
                }
            )
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val usedMb = (latest.deviceTotalMemMb - latest.deviceAvailMemMb).coerceAtLeast(0)
            DeviceHeaderCard(
                title = stringResource(R.string.perf_memory_device),
                value = "${formatMb(usedMb * 1024L)} / ${formatMb(latest.deviceTotalMemMb * 1024L)}",
                subtitle =
                    stringResource(
                        R.string.perf_memory_available,
                        formatMb(latest.deviceAvailMemMb * 1024L)
                    ),
                progress =
                    if (latest.deviceTotalMemMb > 0) {
                        usedMb.toFloat() / latest.deviceTotalMemMb.toFloat()
                    } else {
                        0f
                    }
            )
        }
        item {
            ChartCard {
                PerformanceLiveChart(
                    history = history,
                    series = series,
                    valueFormatter = { value -> formatMb(value.toLong()) }
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.perf_memory_metric_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        entityListItems(latest, PerformanceTab.MEMORY)
    }
}

// ————————————————————————————— 网络 —————————————————————————————

@Composable
private fun NetworkPage(history: List<PerformanceSnapshot>, latest: PerformanceSnapshot) {
    val rxColor = MaterialTheme.colorScheme.primary
    val txColor = MaterialTheme.colorScheme.tertiary
    val appLabel = stringResource(R.string.perf_kind_app)
    val deviceLabel = stringResource(R.string.perf_kind_device)
    val rxLabel = stringResource(R.string.perf_network_rx)
    val txLabel = stringResource(R.string.perf_network_tx)

    val series =
        remember(appLabel, deviceLabel, rxLabel, txLabel) {
            listOf(
                PerformanceChartSeries("$appLabel · $rxLabel", rxColor) { snapshot ->
                    snapshot.entity(PerformanceEntityKind.APP)?.rxBytesPerSec?.toDouble()
                },
                PerformanceChartSeries("$appLabel · $txLabel", txColor) { snapshot ->
                    snapshot.entity(PerformanceEntityKind.APP)?.txBytesPerSec?.toDouble()
                },
                PerformanceChartSeries("$deviceLabel · $rxLabel", rxColor.copy(alpha = 0.5f)) { snapshot ->
                    snapshot.deviceRxBytesPerSec?.toDouble()
                },
                PerformanceChartSeries("$deviceLabel · $txLabel", txColor.copy(alpha = 0.5f)) { snapshot ->
                    snapshot.deviceTxBytesPerSec?.toDouble()
                }
            )
        }

    val networkAvailable = history.any { snapshot ->
        snapshot.entity(PerformanceEntityKind.APP)?.rxBytesPerSec != null ||
            snapshot.deviceRxBytesPerSec != null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val app = latest.entity(PerformanceEntityKind.APP)
            NetworkHeaderCard(
                appRx = app?.rxBytesPerSec?.let { formatRate(it) } ?: "—",
                appTx = app?.txBytesPerSec?.let { formatRate(it) } ?: "—",
                deviceRx = latest.deviceRxBytesPerSec?.let { formatRate(it) } ?: "—",
                deviceTx = latest.deviceTxBytesPerSec?.let { formatRate(it) } ?: "—",
                appLabel = appLabel,
                deviceLabel = deviceLabel
            )
        }
        item {
            ChartCard {
                PerformanceLiveChart(
                    history = history,
                    series = series,
                    valueFormatter = { value -> formatRate(value.toLong()) }
                )
            }
        }
        item {
            Text(
                text =
                    if (networkAvailable) {
                        stringResource(R.string.perf_network_note)
                    } else {
                        stringResource(R.string.perf_network_unavailable)
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ————————————————————————————— 实体明细 —————————————————————————————

private fun androidx.compose.foundation.lazy.LazyListScope.entityListItems(
    latest: PerformanceSnapshot,
    tab: PerformanceTab
) {
    val entities =
        latest.entities.sortedWith(
            compareByDescending { entity ->
                when (tab) {
                    PerformanceTab.CPU -> entity.cpuPercent
                    PerformanceTab.MEMORY -> entity.memoryKb.toDouble()
                    PerformanceTab.NETWORK -> 0.0
                }
            }
        )
    items(entities, key = { entity -> "${entity.kind}:${entity.id}" }) { entity ->
        EntityRow(entity = entity, tab = tab)
    }
}

@Composable
private fun EntityRow(entity: PerformanceEntitySample, tab: PerformanceTab) {
    val kindLabelRes =
        when (entity.kind) {
            PerformanceEntityKind.APP -> R.string.perf_kind_app
            PerformanceEntityKind.PLUGIN -> R.string.perf_kind_plugin
            PerformanceEntityKind.TERMINAL -> R.string.perf_kind_terminal
        }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(rowBackground())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = entity.kind.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.displayName.ifBlank { stringResource(kindLabelRes) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entity.detail.isNullOrBlank()) {
                Text(
                    text = entity.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text =
                when (tab) {
                    PerformanceTab.CPU ->
                        String.format(Locale.US, "%.1f%%", entity.cpuPercent)
                    PerformanceTab.MEMORY -> formatMb(entity.memoryKb)
                    PerformanceTab.NETWORK -> ""
                },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ————————————————————————————— 复用小组件 —————————————————————————————

@Composable
private fun rowBackground(): Color {
    val hasBackgroundImage = LocalThemePreferenceSnapshot.current.useBackgroundImage
    return if (hasBackgroundImage) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
}

@Composable
private fun DeviceHeaderCard(
    title: String,
    value: String,
    subtitle: String,
    progress: Float
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = rowBackground()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun NetworkHeaderCard(
    appRx: String,
    appTx: String,
    deviceRx: String,
    deviceTx: String,
    appLabel: String,
    deviceLabel: String
) {
    val rxLabel = stringResource(R.string.perf_network_rx)
    val txLabel = stringResource(R.string.perf_network_tx)
    Card(
        colors = CardDefaults.cardColors(containerColor = rowBackground()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NetworkHeaderLine(label = appLabel, rx = appRx, tx = appTx, rxLabel = rxLabel, txLabel = txLabel, emphasized = true)
            NetworkHeaderLine(label = deviceLabel, rx = deviceRx, tx = deviceTx, rxLabel = rxLabel, txLabel = txLabel, emphasized = false)
        }
    }
}

@Composable
private fun NetworkHeaderLine(
    label: String,
    rx: String,
    tx: String,
    rxLabel: String,
    txLabel: String,
    emphasized: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style =
                if (emphasized) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            color =
                if (emphasized) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$rxLabel $rx",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "$txLabel $tx",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun ChartCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = rowBackground()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun SamplingHint() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.perf_sampling_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ————————————————————————————— 工具函数 —————————————————————————————

private fun PerformanceEntityKind.icon(): ImageVector =
    when (this) {
        PerformanceEntityKind.APP -> Icons.Default.Speed
        PerformanceEntityKind.PLUGIN -> Icons.Default.Extension
        PerformanceEntityKind.TERMINAL -> Icons.Default.Terminal
    }

/** KB → 自适应 MB/GB 文本（输入单位 KB）。 */
internal fun formatMb(kb: Long): String =
    when {
        abs(kb) >= 1024L * 1024L ->
            String.format(Locale.US, "%.2fGB", kb / (1024.0 * 1024.0))
        abs(kb) >= 1024L ->
            String.format(Locale.US, "%.1fMB", kb / 1024.0)
        else -> "${kb}KB"
    }

/** 字节/秒 → 自适应 B/s、KB/s、MB/s 文本。 */
internal fun formatRate(bytesPerSec: Long): String =
    when {
        abs(bytesPerSec) >= 1024L * 1024L ->
            String.format(Locale.US, "%.2fMB/s", bytesPerSec / (1024.0 * 1024.0))
        abs(bytesPerSec) >= 1024L ->
            String.format(Locale.US, "%.1fKB/s", bytesPerSec / 1024.0)
        else -> "${bytesPerSec}B/s"
    }
