package com.ai.assistance.operit.ui.features.tokenstats

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.stats.TokenStatsDisplayModelBreakdown
import com.ai.assistance.operit.data.stats.TokenStatsDisplayUnit
import com.ai.assistance.operit.data.stats.TokenStatsGranularity
import com.ai.assistance.operit.data.stats.TokenStatsPriceDraft
import com.ai.assistance.operit.data.stats.TokenStatsPriceSetting
import com.ai.assistance.operit.data.stats.TokenStatsRangeData
import com.ai.assistance.operit.data.stats.cacheRate
import com.ai.assistance.operit.data.stats.formatTokenCount
import com.ai.assistance.operit.ui.components.CustomScaffold
import java.time.ZoneId
import java.util.Locale

private enum class ChartDetailMetric { COST, REQUESTS, TOKENS }

private fun TokenStatsDisplayUnit.labelResource(): Int =
    when (this) {
        TokenStatsDisplayUnit.MILLIONS -> R.string.token_stats_unit_millions
        TokenStatsDisplayUnit.BILLIONS -> R.string.token_stats_unit_billions
    }

/**
 * Token 统计页（信息架构重构版，设计规范 2026-08-18）。
 * 页面顺序：时间控制 → 周期总览 → 2×2 核心指标 → 活跃记录 → Token 构成 →
 * 模型累计 → 范围分析 → 趋势分析（单卡片指标切换）→ 配置详情 → 统计设置。
 * 数据源、筛选、日期选择、定价与保存逻辑全部沿用原有 ViewModel。
 */
@Composable
fun TokenUsageStatisticsScreen(
    onBackPressed: () -> Unit,
) {
    val context = LocalContext.current
    // P1-3：VM 由路由级 ViewModelStore 管理（AppContent 为该 route 提供
    // LocalViewModelStoreOwner，键 = screenKey）——配置变化保留实例，
    // 路由出栈/替换/清栈时 store.clear() 触发 onCleared，viewModelScope
    // 取消；Factory 只持有 applicationContext。
    val viewModel: TokenUsageStatisticsViewModel =
        viewModel(factory = TokenUsageStatisticsViewModel.Factory(context))
    val state by viewModel.state.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()

    // 瞬态 UI 状态：可存 rememberSaveable 的在配置变化后保留（P1-3）；
    // 筛选已在 VM state 中，天然跨配置变化保留。
    var showDateRange by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { message ->
            Toast.makeText(context, message.text, Toast.LENGTH_SHORT).show()
            viewModel.consumeActionMessage()
        }
    }
    LaunchedEffect(Unit) { viewModel.loadForEntry() }

    TokenStatsColorsProvider {
        val colors = LocalTokenStatsColors.current
        CustomScaffold(
            containerColor = colors.pageBackground,
            contentColor = colors.pageContent,
        ) { paddingValues ->
            val content: @Composable () -> Unit = {
                when {
                    state.loading && (state.range == null || state.lifetime == null) -> {
                        LoadingState()
                    }
                    state.errorMessage != null && state.range == null -> {
                        ErrorState(
                            message = state.errorMessage.orEmpty(),
                            onRetry = viewModel::load,
                        )
                    }
                    else -> {
                        TokenStatsPageContent(
                            state = state,
                            viewModel = viewModel,
                            zone = viewModel.zone,
                            onSelectDateRange = { showDateRange = true },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                content()
            }
        }

        if (showDateRange) {
            TokenStatsDateRangeDialog(
                zone = viewModel.zone,
                maxRangeDays = TokenUsageStatisticsViewModel.MAX_CUSTOM_RANGE_DAYS,
                initialRange = state.currentRange,
                onConfirm = { start, end -> viewModel.setCustomRange(start, end) },
                onDismiss = { showDateRange = false },
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalTokenStatsColors.current.pageSupportingContent,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.token_stats_retry))
            }
        }
    }
}

@Composable
private fun TokenStatsPageContent(
    state: TokenStatsUiState,
    viewModel: TokenUsageStatisticsViewModel,
    zone: ZoneId,
    onSelectDateRange: () -> Unit,
) {
    val lifetime = state.lifetime ?: return
    val hasAnyData =
        lifetime.totals.requests > 0L || lifetime.totals.totalTokens.totalEventCount > 0L
    val context = LocalContext.current
    val range = state.range
    val activityStats = state.activity.rangeData?.stats
    val tokenUnitToggleDescription = stringResource(
        R.string.token_stats_unit_toggle_description,
        stringResource(state.tokenDisplayUnit.labelResource()),
        stringResource(state.tokenDisplayUnit.toggled().labelResource()),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(TokenStatsSpacing.page),
        verticalArrangement = Arrangement.spacedBy(TokenStatsSpacing.section),
    ) {
        // 1. 时间控制行：三段式视图 + 日期范围 + 日历
        item {
            TokenStatsTimeControlRow(
                viewMode = state.activity.viewMode,
                onSelectMode = viewModel::setActivityViewMode,
                dateRange = state.currentRange,
                zone = zone,
                onSelectDateRange = onSelectDateRange,
            )
        }

        // 2. 周期总览：总 Token + 总费用 + 连续天数 + 面积趋势
        item {
            TokenStatsOverviewCard(
                tokensText = range?.summary?.totalTokens?.knownSum
                    ?.let { formatTokenCount(it, state.tokenDisplayUnit) } ?: "–",
                costText = range?.summary?.cost?.knownAmount
                    ?.let { formatLifetimeMoney(it, state.targetCurrency) } ?: "–",
                tokenDisplayUnit = state.tokenDisplayUnit,
                tokenUnitToggleDescription = tokenUnitToggleDescription,
                onToggleTokenDisplayUnit = viewModel::toggleTokenDisplayUnit,
                currentStreakText = activityStats?.let {
                    stringResource(R.string.token_activity_current_streak_badge, it.currentStreak)
                },
                longestStreakText = activityStats?.let {
                    stringResource(R.string.token_activity_longest_streak_badge, it.longestStreak)
                },
                buckets = range?.buckets.orEmpty(),
                granularity = range?.granularity ?: TokenStatsGranularity.DAILY,
                zone = zone,
            )
        }

        // 3. 2×2 核心指标：峰值 Token / 总请求、缓存率 / 输出
        item {
            TokenStatsMetricGrid(
                peakTokens = activityStats?.let {
                    formatTokenCount(it.peakTokens, state.tokenDisplayUnit)
                } ?: "–",
                requests = range?.summary?.requests?.let { formatCount(it) } ?: "–",
                cacheRate = range?.summary?.cacheRate?.let {
                    String.format(Locale.getDefault(), "%.1f%%", it * 100.0)
                } ?: "–",
                output = range?.summary?.output?.knownSum
                    ?.let { formatTokenCount(it, state.tokenDisplayUnit) } ?: "–",
                tokenUnitToggleDescription = tokenUnitToggleDescription,
                onToggleTokenDisplayUnit = viewModel::toggleTokenDisplayUnit,
            )
        }

        // 4. 活跃记录（热力图 / 每周 / 累计）
        item {
            TokenActivitySection(
                state = state.activity,
                tokenDisplayUnit = state.tokenDisplayUnit,
            )
        }

        // 5. Token 构成：缓存读取 / 未缓存输入 / 输出三条进度条
        item {
            TokenStatsCompositionCard(
                summary = range?.summary,
                tokenDisplayUnit = state.tokenDisplayUnit,
            )
        }

        // 6. 模型累计：使用当前周期、当前筛选后的动态模型列表，避免展示与筛选脱节。
        if (!range?.displayModels.isNullOrEmpty()) {
            item {
                TokenStatsModelRankSection(
                    models = range?.displayModels.orEmpty(),
                    currency = state.targetCurrency,
                    tokenDisplayUnit = state.tokenDisplayUnit,
                )
            }
        }

        // 7. 范围分析：模型筛选
        item {
            Column(verticalArrangement = Arrangement.spacedBy(TokenStatsSpacing.section)) {
                TokenStatsFilterBar(
                    selectedModels = state.selectedModels,
                    availableModels = state.availableDisplayModels,
                    knownModelNames = state.knownModelNames,
                    onToggleModel = viewModel::toggleModel,
                    onSelectAllModels = viewModel::selectAllModels,
                )
                when {
                    range == null -> NoDataCard(text = stringResource(R.string.token_stats_no_data_in_range))
                    !hasAnyData -> EmptyStateCard()
                    range.eventCount == 0L -> {
                        NoDataCard(text = stringResource(R.string.token_stats_no_data_in_range))
                    }
                }
            }
        }

        // 8/9. 趋势分析（单卡片指标切换）+ 配置详情
        if (range != null && hasAnyData && range.eventCount > 0L) {
            item {
                TokenStatsTrendCard(
                    range = range,
                    currency = state.targetCurrency,
                    tokenDisplayUnit = state.tokenDisplayUnit,
                    zone = zone,
                )
            }

            item {
                TokenStatsModelDetailsSection(
                    models = range.displayModels,
                    currency = state.targetCurrency,
                    tokenDisplayUnit = state.tokenDisplayUnit,
                    configurationNames = state.configurationNames,
                    priceSettings = state.priceSettings,
                    onSavePrice = viewModel::savePrice,
                    onDeletePrice = viewModel::deletePrice,
                )
            }
        }

        // 10. 统计设置：币种 + 汇率 + 保存
        item {
            val rateInvalidText = stringResource(R.string.token_stats_rate_invalid)
            TokenStatsSettingsCard(
                targetCurrency = state.targetCurrency,
                onSelectCurrency = viewModel::setTargetCurrency,
                manualRate = state.manualRate,
                rateIsEstimated = state.rateIsEstimated,
                onSaveRate = { rate ->
                    val ok = viewModel.setManualRate(rate)
                    if (!ok) {
                        Toast.makeText(context, rateInvalidText, Toast.LENGTH_SHORT).show()
                    }
                    ok
                },
            )
        }

        item {
            Spacer(Modifier.height(96.dp))
        }
    }
}

// ==== 时间控制行 ====

@Composable
private fun TokenStatsTimeControlRow(
    viewMode: com.ai.assistance.operit.data.stats.TokenActivityViewMode,
    onSelectMode: (com.ai.assistance.operit.data.stats.TokenActivityViewMode) -> Unit,
    dateRange: com.ai.assistance.operit.data.stats.TokenStatsTimeRange?,
    zone: ZoneId,
    onSelectDateRange: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenStatsSegmentedControl(selected = viewMode, onSelect = onSelectMode)
        Spacer(Modifier.weight(1f))
        Text(
            text = dateRange?.let { formatCompactDateRangeLabel(it, zone) }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = LocalTokenStatsColors.current.pageSupportingContent,
            maxLines = 1,
        )
        IconButton(onClick = onSelectDateRange) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription =
                    dateRange?.let { formatDateRangeLabel(it, zone) }
                        ?: stringResource(R.string.token_stats_date_range),
            )
        }
    }
}

// ==== 配置详情（含定价编辑入口） ====

@Composable
private fun TokenStatsModelDetailsSection(
    models: List<TokenStatsDisplayModelBreakdown>,
    currency: PricingCurrency,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    configurationNames: Map<String, String>,
    priceSettings: List<TokenStatsPriceSetting>,
    onSavePrice: (TokenStatsPriceDraft) -> Unit,
    onDeletePrice: (TokenStatsPriceSetting) -> Unit,
) {
    var priceEditor by remember { mutableStateOf<PriceEditorTarget?>(null) }
    TokenStatsConfigurationCardsSection(
        configurations = models.flatMap(TokenStatsDisplayModelBreakdown::identities),
        currency = currency,
        tokenDisplayUnit = tokenDisplayUnit,
        configurationNames = configurationNames,
        priceSettings = priceSettings,
        onEditPrice = { existing, draft, configurationName ->
            priceEditor = PriceEditorTarget(existing, draft, configurationName)
        },
        onResetConfigurationPrice = onDeletePrice,
    )
    priceEditor?.let { target ->
        PriceSettingsDialog(
            existing = target.existing,
            initialDraft = target.draft,
            configurationName = target.configurationName,
            onSave = onSavePrice,
            onDelete = target.existing?.let { setting -> { onDeletePrice(setting) } },
            onDismiss = { priceEditor = null },
        )
    }
}

private data class PriceEditorTarget(
    val existing: TokenStatsPriceSetting?,
    val draft: TokenStatsPriceDraft,
    val configurationName: String?,
)

// ==== 空态 ====

@Composable
private fun EmptyStateCard() {
    TokenStatsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Analytics,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = LocalTokenStatsColors.current.cardSupportingContent,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.token_stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalTokenStatsColors.current.cardSupportingContent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.token_stats_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalTokenStatsColors.current.cardWeakContent,
            )
        }
    }
}

@Composable
private fun NoDataCard(text: String) {
    TokenStatsCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalTokenStatsColors.current.cardSupportingContent,
            modifier = Modifier.padding(24.dp),
        )
    }
}

// ==== 趋势分析：单卡片指标切换 ====

@Composable
private fun TokenStatsTrendCard(
    range: TokenStatsRangeData,
    currency: PricingCurrency,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    zone: ZoneId,
) {
    var selectedMetric by rememberSaveable { mutableStateOf(ChartDetailMetric.COST) }
    var detailMetric by rememberSaveable { mutableStateOf<ChartDetailMetric?>(null) }
    val style = tokenStatsChartStyle()

    TokenStatsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TokenStatsSpacing.card),
            verticalArrangement = Arrangement.spacedBy(TokenStatsSpacing.content),
        ) {
            TokenStatsCardTitle(stringResource(R.string.token_stats_trends))
            TokenStatsTrendMetricSelector(
                selected = selectedMetric,
                onSelect = {
                    selectedMetric = it
                    detailMetric = null
                },
            )
            when (selectedMetric) {
                ChartDetailMetric.COST -> {
                    val chartTitle = stringResource(R.string.token_stats_chart_cost)
                    TokenStatsTrendMetricSummary(
                        title = chartTitle,
                        summary = formatLifetimeMoney(range.summary.cost.knownAmount, currency),
                        onClick = { detailMetric = selectedMetric },
                    )
                    val unknownCostTemplate = stringResource(R.string.token_stats_unknown_cost)
                    TokenStatsLineChart(
                        style = style,
                        buckets = range.buckets,
                        granularity = range.granularity,
                        zone = zone,
                        formatValue = { formatMoney(it, currency) },
                        emptyText = stringResource(R.string.token_stats_no_data_in_range),
                        chartLabel = chartTitle,
                        chartHeight = 168.dp,
                        maxLabels = 3,
                        showRefValues = true,
                        tooltipCard = false,
                        valueSelector = { it.totals.cost.knownAmount },
                        unknownNote = { bucket ->
                            val unknown = bucket.totals.cost.unknownContributionCount
                            if (unknown > 0L) String.format(unknownCostTemplate, unknown) else null
                        },
                    )
                }
                ChartDetailMetric.REQUESTS -> {
                    val chartTitle = stringResource(R.string.token_stats_chart_requests)
                    TokenStatsTrendMetricSummary(
                        title = chartTitle,
                        summary = formatRequestCount(range.summary.requests),
                        onClick = { detailMetric = selectedMetric },
                    )
                    TokenStatsLineChart(
                        style = style,
                        buckets = range.buckets,
                        granularity = range.granularity,
                        zone = zone,
                        formatValue = { formatCount(it.toLong()) },
                        emptyText = stringResource(R.string.token_stats_no_data_in_range),
                        chartLabel = chartTitle,
                        chartHeight = 168.dp,
                        maxLabels = 3,
                        showRefValues = true,
                        tooltipCard = false,
                        valueSelector = { it.totals.requests.toDouble() },
                    )
                }
                ChartDetailMetric.TOKENS -> {
                    val colors = LocalTokenStatsColors.current
                    val chartTitle = stringResource(R.string.token_stats_chart_tokens)
                    val outputLabel = stringResource(R.string.token_stats_token_output)
                    val cachedLabel = stringResource(R.string.token_stats_token_cached)
                    val uncachedLabel = stringResource(R.string.token_stats_token_uncached)
                    val unknownPartsTemplate = stringResource(R.string.token_stats_unknown_parts)
                    TokenStatsTrendMetricSummary(
                        title = chartTitle,
                        summary = formatTokenCount(
                            range.summary.totalTokens.knownSum,
                            tokenDisplayUnit,
                        ),
                        onClick = { detailMetric = selectedMetric },
                    )
                    TokenStatsStackedBarChart(
                        style = style,
                        buckets = range.buckets,
                        granularity = range.granularity,
                        zone = zone,
                        formatValue = { formatTokenCount(it.toLong(), tokenDisplayUnit) },
                        emptyText = stringResource(R.string.token_stats_no_data_in_range),
                        chartLabel = chartTitle,
                        chartHeight = 168.dp,
                        maxLabels = 3,
                        showRefValues = true,
                        tooltipCard = false,
                        stackSelector = { bucket ->
                            listOf(
                                bucket.totals.uncachedInput.knownSum.toDouble() to colors.componentColors[0],
                                bucket.totals.cachedInput.knownSum.toDouble() to colors.componentColors[1],
                                bucket.totals.output.knownSum.toDouble() to colors.componentColors[2],
                            )
                        },
                        stackLabels = { listOf(uncachedLabel, cachedLabel, outputLabel) },
                        stackTotalSelector = { bucket ->
                            bucket.totals.totalTokens.knownSum.toDouble()
                        },
                        unknownNote = { bucket ->
                            val unknown = bucket.totals.totalTokens.unknownEventCount
                            if (unknown > 0L) String.format(unknownPartsTemplate, unknown) else null
                        },
                    )
                }
            }
        }
    }

    detailMetric?.let { metric ->
        TokenStatsChartDetailDialog(
            metric = metric,
            range = range,
            currency = currency,
            tokenDisplayUnit = tokenDisplayUnit,
            onDismiss = { detailMetric = null },
        )
    }
}

@Composable
private fun TokenStatsTrendMetricSelector(
    selected: ChartDetailMetric,
    onSelect: (ChartDetailMetric) -> Unit,
) {
    val colors = LocalTokenStatsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.innerContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChartDetailMetric.entries.forEach { metric ->
            val label = stringResource(
                when (metric) {
                    ChartDetailMetric.COST -> R.string.token_stats_metric_cost
                    ChartDetailMetric.REQUESTS -> R.string.token_stats_metric_requests
                    ChartDetailMetric.TOKENS -> R.string.token_stats_metric_tokens
                },
            )
            val isSelected = metric == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) colors.selectedSegmentContainer else colors.innerContainer,
                    )
                    .clickable { onSelect(metric) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colors.cardContent else colors.cardSupportingContent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TokenStatsTrendMetricSummary(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val colors = LocalTokenStatsColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.cardContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.cardAccent,
            maxLines = 1,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
private fun TokenStatsChartDetailDialog(
    metric: ChartDetailMetric,
    range: TokenStatsRangeData,
    currency: PricingCurrency,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    onDismiss: () -> Unit,
) {
    val accent = LocalTokenStatsColors.current.cardAccent
    val title = stringResource(
        when (metric) {
            ChartDetailMetric.COST -> R.string.token_stats_detail_cost
            ChartDetailMetric.REQUESTS -> R.string.token_stats_detail_requests
            ChartDetailMetric.TOKENS -> R.string.token_stats_detail_tokens
        }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                when (metric) {
                    ChartDetailMetric.COST -> range.displayModels.forEach { model ->
                        if (model.totals.cost.knownAmount > 0.0) {
                            TokenStatsDetailRow(
                                model.displayName,
                                formatMoney(model.totals.cost.knownAmount, currency),
                                accent,
                            )
                        }
                    }
                    ChartDetailMetric.REQUESTS -> range.displayModels.forEach { model ->
                        if (model.totals.requests > 0L) {
                            TokenStatsDetailRow(
                                model.displayName,
                                formatRequestCount(model.totals.requests),
                                accent,
                            )
                        }
                    }
                    ChartDetailMetric.TOKENS -> {
                        // canonical 总 Token 为权威合计；缓存/非缓存/输出仍是诊断分量
                        TokenStatsDetailRow(
                            stringResource(R.string.token_stats_tokens_total),
                            formatTokenCount(range.summary.totalTokens.knownSum, tokenDisplayUnit),
                            accent,
                        )
                        TokenStatsDetailRow(
                            stringResource(R.string.token_stats_token_cached),
                            formatTokenCount(range.summary.cachedInput.knownSum, tokenDisplayUnit),
                            accent,
                        )
                        TokenStatsDetailRow(
                            stringResource(R.string.token_stats_token_uncached),
                            formatTokenCount(range.summary.uncachedInput.knownSum, tokenDisplayUnit),
                            accent,
                        )
                        TokenStatsDetailRow(
                            stringResource(R.string.token_stats_token_output),
                            formatTokenCount(range.summary.output.knownSum, tokenDisplayUnit),
                            accent,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.token_stats_detail_close))
            }
        },
    )
}

@Composable
private fun TokenStatsDetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium,
        )
    }
}
