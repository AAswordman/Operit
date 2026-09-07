package com.ai.assistance.operit.ui.features.tokenstats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.normalizeProviderModel
import com.ai.assistance.operit.data.model.normalizeProviderTypeId
import com.ai.assistance.operit.data.stats.TokenActivityViewMode
import com.ai.assistance.operit.data.stats.TokenPriceResolver
import com.ai.assistance.operit.data.stats.TokenCostCalculator
import com.ai.assistance.operit.data.stats.TokenStatsDisplayModelBreakdown
import com.ai.assistance.operit.data.stats.TokenStatsDisplayUnit
import com.ai.assistance.operit.data.stats.TokenStatsGranularity
import com.ai.assistance.operit.data.stats.TokenStatsPriceDraft
import com.ai.assistance.operit.data.stats.TokenStatsPriceScope
import com.ai.assistance.operit.data.stats.TokenStatsPriceSetting
import com.ai.assistance.operit.data.stats.TokenStatsTimeRange
import com.ai.assistance.operit.data.stats.TokenStatsTokenAggregate
import com.ai.assistance.operit.data.stats.TokenStatsTotals
import com.ai.assistance.operit.data.stats.TokenStatsTrendBucket
import com.ai.assistance.operit.data.stats.formatTokenCount
import com.ai.assistance.operit.data.stats.tokenStatsPriceScopeForConfigId
import com.ai.assistance.operit.ui.common.icons.providerLogoColorFilter
import com.ai.assistance.operit.ui.common.icons.rememberProviderLogoPainter
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 统计页共享空间尺度（设计规范 §5 间距）：
 * 页面边距 16 / 大模块间距 8 / 卡片内边距 16 / 卡内元素间距 8。
 */
internal object TokenStatsSpacing {
    val page = 16.dp
    val section = 8.dp
    val card = 16.dp
    val content = 8.dp
}

// ==== 通用格式 ====

/** 金额：符号 + 4 位小数（图表/明细统一）。 */
internal fun formatMoney(amount: Double, currency: PricingCurrency): String =
    "${currency.symbol}${String.format(Locale.US, "%.4f", amount)}"

/** 总览/排名卡费用使用紧凑的两位小数。 */
internal fun formatLifetimeMoney(amount: Double, currency: PricingCurrency): String =
    "${currency.symbol}${String.format(Locale.US, "%.2f", amount)}"

internal fun formatCount(value: Long): String = String.format(Locale.US, "%,d", value)

internal fun formatRequestCount(value: Long): String = formatCount(value)

@Composable
internal fun formatCompactRequestCountLabel(value: Long): String =
    stringResource(R.string.token_stats_request_count_compact, formatCompactCount(value))

internal fun knownTokenSum(totals: TokenStatsTotals): Long = totals.totalTokens.knownSum

internal fun saturatedTokenSum(vararg values: Long): Long =
    values.fold(0L, TokenCostCalculator::saturatedAdd)

internal fun formatDateRangeLabel(range: TokenStatsTimeRange, zone: ZoneId): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    val start = java.time.Instant.ofEpochMilli(range.startMs).atZone(zone).toLocalDate()
    val end = java.time.Instant.ofEpochMilli(range.endMs - 1L).atZone(zone).toLocalDate()
    return if (start == end) start.format(formatter) else "${start.format(formatter)} - ${end.format(formatter)}"
}

internal fun formatCompactDateRangeLabel(range: TokenStatsTimeRange, zone: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("M/d", Locale.getDefault())
    val start = java.time.Instant.ofEpochMilli(range.startMs).atZone(zone).toLocalDate()
    val end = java.time.Instant.ofEpochMilli(range.endMs - 1L).atZone(zone).toLocalDate()
    return if (start == end) start.format(formatter) else "${start.format(formatter)}-${end.format(formatter)}"
}

private fun formatPercentage(value: Double): String =
    String.format(Locale.getDefault(), "%.1f%%", value)

// ==== 卡片容器 ====

/** 一级卡片：中性 surfaceContainer + 1dp 描边 + 12dp 圆角，主色不进入容器。 */
@Composable
internal fun TokenStatsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalTokenStatsColors.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardContainer,
            contentColor = colors.cardContent,
        ),
        border = BorderStroke(1.dp, colors.cardBorder),
        content = content,
    )
}

/** 卡片内模块标题：左侧标题（16sp SemiBold）+ 右侧附加信息。 */
@Composable
internal fun TokenStatsCardTitle(
    title: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalTokenStatsColors.current.cardContent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** 胶囊（连续天数徽章等）：二级容器底 + 弱化文字。 */
@Composable
internal fun TokenStatsPill(text: String) {
    val colors = LocalTokenStatsColors.current
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.innerContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.cardSupportingContent,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ==== 顶部时间控制：三段式选择器 ====

/**
 * 三段式“每日/每周/累计”（设计规范 §6.2）：
 * 选中 = 主色低透明度底 + 内容色文字；未选中透明底 + 辅助文字。
 */
@Composable
internal fun TokenStatsSegmentedControl(
    selected: TokenActivityViewMode,
    onSelect: (TokenActivityViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTokenStatsColors.current
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.innerContainer)
            .padding(3.dp),
    ) {
        TokenActivityViewMode.entries.forEach { mode ->
            val isSelected = selected == mode
            Text(
                text = stringResource(
                    when (mode) {
                        TokenActivityViewMode.DAILY -> R.string.token_activity_daily
                        TokenActivityViewMode.WEEKLY -> R.string.token_activity_weekly
                        TokenActivityViewMode.CUMULATIVE -> R.string.token_activity_cumulative
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) colors.cardContent else colors.cardSupportingContent,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isSelected) colors.selectedSegmentContainer else Color.Transparent,
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

// ==== 周期总览 ====

/**
 * 周期总览大卡（设计规范 §6.3）：周期标题 + 连续天数胶囊、主数字 Token、
 * 总费用、下半部 Token 面积折线趋势。
 */
@Composable
internal fun TokenStatsOverviewCard(
    tokensText: String,
    costText: String,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    tokenUnitToggleDescription: String,
    onToggleTokenDisplayUnit: () -> Unit,
    currentStreakText: String?,
    longestStreakText: String?,
    buckets: List<TokenStatsTrendBucket>,
    granularity: TokenStatsGranularity,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTokenStatsColors.current
    TokenStatsCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TokenStatsSpacing.card),
            verticalArrangement = Arrangement.spacedBy(TokenStatsSpacing.content),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.token_stats_period_overview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                currentStreakText?.let {
                    TokenStatsPill(it)
                    Spacer(Modifier.width(6.dp))
                }
                longestStreakText?.let { TokenStatsPill(it) }
            }

            Column {
                Row(
                    modifier = Modifier
                        .clickable(
                            role = Role.Button,
                            onClick = onToggleTokenDisplayUnit,
                        )
                        .semantics { contentDescription = tokenUnitToggleDescription },
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = tokensText,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.cardAccent,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = " Token",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.cardSupportingContent,
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = 4.dp),
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stringResource(R.string.settings_total_cost),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.cardSupportingContent,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = costText,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.cardContent,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = 8.dp),
                    )
                }
            }

            TokenStatsAreaChart(
                style = tokenStatsChartStyle(),
                buckets = buckets,
                granularity = granularity,
                zone = zone,
                formatValue = { formatTokenCount(it.toLong(), tokenDisplayUnit) },
                emptyText = stringResource(R.string.token_stats_no_data_in_range),
                chartLabel = stringResource(R.string.token_stats_chart_tokens),
                valueSelector = { it.totals.totalTokens.knownSum.toDouble() },
            )
        }
    }
}

// ==== 2×2 核心指标 ====

/** 指标迷你卡：图标底座（主色线性图标）+ 名称 + 大号数值。 */
@Composable
private fun TokenStatsMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueClick: (() -> Unit)? = null,
    valueClickDescription: String? = null,
) {
    val colors = LocalTokenStatsColors.current
    val cardModifier = onValueClick?.let { onClick ->
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = checkNotNull(valueClickDescription) }
    } ?: modifier
    TokenStatsCard(modifier = cardModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.innerContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.cardAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cardSupportingContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.cardContent,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 2×2 核心指标（设计规范 §6.4，顺序固定）：
 * 峰值 Token / 总请求、缓存率 / 输出。
 */
@Composable
internal fun TokenStatsMetricGrid(
    peakTokens: String,
    requests: String,
    cacheRate: String,
    output: String,
    tokenUnitToggleDescription: String,
    onToggleTokenDisplayUnit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TokenStatsSpacing.section)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TokenStatsSpacing.section)) {
            TokenStatsMetricCard(
                icon = Icons.Outlined.TrendingUp,
                label = stringResource(R.string.token_activity_peak_tokens),
                value = peakTokens,
                modifier = Modifier.weight(1f),
                onValueClick = onToggleTokenDisplayUnit,
                valueClickDescription = tokenUnitToggleDescription,
            )
            TokenStatsMetricCard(
                icon = Icons.Outlined.ShowChart,
                label = stringResource(R.string.settings_total_requests),
                value = requests,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TokenStatsSpacing.section)) {
            TokenStatsMetricCard(
                icon = Icons.Outlined.PieChart,
                label = stringResource(R.string.token_stats_cache_rate),
                value = cacheRate,
                modifier = Modifier.weight(1f),
            )
            TokenStatsMetricCard(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = stringResource(R.string.token_stats_token_output),
                value = output,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ==== Token 构成 ====

/** 构成行：名称 + 横向进度条 + 数值 + 百分比（零值保留、进度为 0）。 */
@Composable
private fun TokenStatsCompositionRow(
    label: String,
    aggregate: TokenStatsTokenAggregate,
    total: Long,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTokenStatsColors.current
    val fraction = if (total > 0L) {
        (aggregate.knownSum.toDouble() / total).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.cardSupportingContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(76.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.innerContainer),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.toFloat())
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.cardAccent),
            )
        }
        Text(
            text = formatTokenCount(aggregate.knownSum, tokenDisplayUnit),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = colors.cardContent,
            maxLines = 1,
        )
        Text(
            text = formatPercentage(fraction * 100.0),
            style = MaterialTheme.typography.labelSmall,
            color = colors.cardWeakContent,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** Token 构成卡：缓存读取 / 未缓存输入 / 输出 三条进度条。 */
@Composable
internal fun TokenStatsCompositionCard(
    summary: TokenStatsTotals?,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    modifier: Modifier = Modifier,
) {
    TokenStatsCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TokenStatsSpacing.card),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TokenStatsCardTitle(stringResource(R.string.token_stats_composition))
            val total = summary?.totalTokens?.knownSum ?: 0L
            TokenStatsCompositionRow(
                label = stringResource(R.string.token_stats_token_cached),
                aggregate = summary?.cachedInput ?: TokenStatsTokenAggregate(0L, 0L, 0L, 0L),
                total = total,
                tokenDisplayUnit = tokenDisplayUnit,
            )
            TokenStatsCompositionRow(
                label = stringResource(R.string.token_stats_token_uncached),
                aggregate = summary?.uncachedInput ?: TokenStatsTokenAggregate(0L, 0L, 0L, 0L),
                total = total,
                tokenDisplayUnit = tokenDisplayUnit,
            )
            TokenStatsCompositionRow(
                label = stringResource(R.string.token_stats_token_output),
                aggregate = summary?.output ?: TokenStatsTokenAggregate(0L, 0L, 0L, 0L),
                total = total,
                tokenDisplayUnit = tokenDisplayUnit,
            )
        }
    }
}

// ==== 模型累计（排名列表 + 占比进度条） ====

private const val LIFETIME_MODELS_COLLAPSED_COUNT = 5

/** 排名徽章：第一名主色低透明度底，其余二级容器底。 */
@Composable
private fun TokenStatsRankBadge(rank: Int) {
    val colors = LocalTokenStatsColors.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (rank == 1) colors.selectedSegmentContainer else colors.innerContainer,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (rank == 1) colors.cardContent else colors.cardSupportingContent,
        )
    }
}

@Composable
private fun TokenStatsModelRankRow(
    rank: Int,
    model: TokenStatsDisplayModelBreakdown,
    totalTokens: Long,
    currency: PricingCurrency,
    tokenDisplayUnit: TokenStatsDisplayUnit,
) {
    val colors = LocalTokenStatsColors.current
    val tokens = knownTokenSum(model.totals)
    val fraction = if (totalTokens > 0L) {
        (tokens.toDouble() / totalTokens).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
    val percentage = (fraction * 100).roundToInt()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TokenStatsRankBadge(rank)
            Spacer(Modifier.width(10.dp))
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(
                        R.string.token_stats_lifetime_model_value,
                        formatTokenCount(tokens, tokenDisplayUnit),
                        percentage,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.cardContent,
                    maxLines = 1,
                )
                Text(
                    text = formatLifetimeMoney(model.totals.cost.knownAmount, currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cardAccent,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.innerContainer),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.toFloat())
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.cardAccent),
            )
        }
    }
}

/** 模型累计卡：排名列表 + 占比进度条（替代旧圆环图）。 */
@Composable
internal fun TokenStatsModelRankSection(
    models: List<TokenStatsDisplayModelBreakdown>,
    currency: PricingCurrency,
    tokenDisplayUnit: TokenStatsDisplayUnit,
) {
    val colors = LocalTokenStatsColors.current
    val sortedModels = models.sortedByDescending { knownTokenSum(it.totals) }
    var showAllModels by rememberSaveable { mutableStateOf(false) }
    val visibleModels =
        if (showAllModels) {
            sortedModels
        } else {
            sortedModels.take(LIFETIME_MODELS_COLLAPSED_COUNT)
        }
    val totalTokens = sortedModels.fold(0L) { total, model ->
        TokenCostCalculator.saturatedAdd(total, knownTokenSum(model.totals))
    }

    TokenStatsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TokenStatsSpacing.card),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TokenStatsCardTitle(stringResource(R.string.token_stats_lifetime_models)) {
                Text(
                    text = stringResource(R.string.token_stats_model_count, sortedModels.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.cardSupportingContent,
                )
            }
            visibleModels.forEachIndexed { index, model ->
                TokenStatsModelRankRow(
                    rank = index + 1,
                    model = model,
                    totalTokens = totalTokens,
                    currency = currency,
                    tokenDisplayUnit = tokenDisplayUnit,
                )
            }
            if (sortedModels.size > LIFETIME_MODELS_COLLAPSED_COUNT) {
                TextButton(
                    onClick = { showAllModels = !showAllModels },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text =
                            if (showAllModels) {
                                stringResource(R.string.token_stats_model_collapse)
                            } else {
                                stringResource(
                                    R.string.token_stats_model_show_all,
                                    sortedModels.size,
                                )
                            },
                    )
                }
            }
        }
    }
}

// ==== 范围分析（筛选） ====

/** 范围分析卡：标题 + 单个全宽模型筛选下拉（沿用现有筛选逻辑）。 */
@Composable
internal fun TokenStatsFilterBar(
    selectedModels: Set<String>,
    availableModels: List<TokenStatsDisplayModelBreakdown>,
    knownModelNames: Map<String, String>,
    onToggleModel: (String) -> Unit,
    onSelectAllModels: () -> Unit,
) {
    TokenStatsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TokenStatsSpacing.card),
            verticalArrangement = Arrangement.spacedBy(TokenStatsSpacing.content),
        ) {
            TokenStatsCardTitle(stringResource(R.string.token_stats_range_analysis))
            ModelFilterDropdown(
                selectedModels,
                availableModels,
                knownModelNames,
                onToggleModel,
                onSelectAllModels,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun TokenStatsCurrencyDropdown(
    selected: PricingCurrency,
    onSelect: (PricingCurrency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = selected.code)
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            PricingCurrency.entries.forEach { currency ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = currency.code,
                            fontWeight = if (currency == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(currency)
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelFilterDropdown(
    selectedModels: Set<String>,
    availableModels: List<TokenStatsDisplayModelBreakdown>,
    knownModelNames: Map<String, String>,
    onToggleModel: (String) -> Unit,
    onSelectAllModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 可选项 = 当前范围可用模型 + 已被选中但被筛选出当前结果的模型（P1-5）
    val options: List<Pair<String, String>> = remember(availableModels, selectedModels, knownModelNames) {
        val byId = availableModels.associateBy { it.displayModelId }
        buildList {
            availableModels.forEach { add(it.displayModelId to it.displayName) }
            selectedModels.forEach { id ->
                if (id !in byId) add(id to (knownModelNames[id] ?: id))
            }
        }
    }
    FilterDropdown(
        modifier = modifier,
        label = if (selectedModels.isEmpty()) {
            stringResource(
                R.string.token_stats_filter_model_label,
                stringResource(R.string.token_stats_filter_all_models),
            )
        } else {
            stringResource(
                R.string.token_stats_filter_model_label,
                stringResource(R.string.token_stats_filter_models_count, selectedModels.size),
            )
        },
    ) { dismiss ->
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.token_stats_filter_all_models),
                    fontWeight = FontWeight.Bold,
                )
            },
            onClick = {
                onSelectAllModels()
                dismiss()
            },
        )
        options.forEach { (modelId, displayName) ->
            val checked = selectedModels.isEmpty() || modelId in selectedModels
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            // 由外层 DropdownMenuItem 统一处理点击，避免点击 Checkbox 时触发两次切换。
                            onCheckedChange = null,
                        )
                        Text(
                            displayName,
                            modifier = Modifier.padding(start = 4.dp),
                            maxLines = 1,
                        )
                    }
                },
                onClick = {
                    onToggleModel(modelId)
                    dismiss()
                },
            )
        }
    }
}

/** 下拉筛选 chip：中性面上直接使用 M3 默认配色（与主题绑定）。 */
@Composable
private fun FilterDropdown(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

// ==== 配置详情 ====

@Composable
internal fun TokenStatsConfigurationCardsSection(
    configurations: List<com.ai.assistance.operit.data.stats.TokenStatsIdentityBreakdown>,
    currency: PricingCurrency,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    configurationNames: Map<String, String>,
    priceSettings: List<TokenStatsPriceSetting>,
    onEditPrice: (TokenStatsPriceSetting?, TokenStatsPriceDraft, String?) -> Unit,
    onResetConfigurationPrice: (TokenStatsPriceSetting) -> Unit,
) {
    val colors = LocalTokenStatsColors.current
    var configurationsExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedIdentityKey by rememberSaveable { mutableStateOf<String?>(null) }
    val sortedConfigurations =
        configurations.sortedByDescending { it.totals.totalTokens.knownSum }
    TokenStatsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TokenStatsSpacing.card),
            verticalArrangement = Arrangement.spacedBy(TokenStatsSpacing.content),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_model_details),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.cardContent,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = stringResource(
                            R.string.token_stats_configuration_count,
                            configurations.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.cardSupportingContent,
                    )
                    IconButton(
                        onClick = {
                            configurationsExpanded = !configurationsExpanded
                            if (!configurationsExpanded) expandedIdentityKey = null
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (configurationsExpanded) {
                                    Icons.Filled.ExpandLess
                                } else {
                                    Icons.Filled.ExpandMore
                                },
                            contentDescription = stringResource(
                                if (configurationsExpanded) {
                                    R.string.token_stats_model_collapse
                                } else {
                                    R.string.token_stats_model_expand
                                },
                            ),
                            tint = colors.cardSupportingContent,
                        )
                    }
                }
            }
            if (configurationsExpanded) {
                sortedConfigurations.forEachIndexed { index, identity ->
                    if (index > 0) {
                        HorizontalDivider(color = colors.cardBorder)
                    }
                    val configurationName =
                        configurationNames[identity.configId]
                            ?: stringResource(R.string.token_stats_config_deleted)
                    val identityKey = tokenStatsIdentityKey(identity)
                    TokenStatsConfigurationRow(
                        identity = identity,
                        configurationName = configurationName,
                        currency = currency,
                        tokenDisplayUnit = tokenDisplayUnit,
                        priceSettings = priceSettings,
                        expanded = expandedIdentityKey == identityKey,
                        onToggleExpanded = {
                            expandedIdentityKey =
                                if (expandedIdentityKey == identityKey) null else identityKey
                        },
                        onEditPrice = onEditPrice,
                        onResetConfigurationPrice = onResetConfigurationPrice,
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenStatsConfigurationRow(
    identity: com.ai.assistance.operit.data.stats.TokenStatsIdentityBreakdown,
    configurationName: String,
    currency: PricingCurrency,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    priceSettings: List<TokenStatsPriceSetting>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEditPrice: (TokenStatsPriceSetting?, TokenStatsPriceDraft, String?) -> Unit,
    onResetConfigurationPrice: (TokenStatsPriceSetting) -> Unit,
) {
    val colors = LocalTokenStatsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .padding(vertical = 8.dp),
    ) {
        val totals = identity.totals
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.innerContainer),
                contentAlignment = Alignment.Center,
            ) {
                val providerLogo = rememberProviderLogoPainter(identity.provider, 20.dp)
                if (providerLogo != null) {
                    Image(
                        painter = providerLogo,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        colorFilter = providerLogoColorFilter(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = colors.cardAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = configurationName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = identity.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.cardContent,
                    softWrap = true,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = formatMoney(totals.cost.knownAmount, currency),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.cardAccent,
                    maxLines = 1,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cardSupportingContent,
                )
                Text(
                    text = formatCompactRequestCountLabel(totals.requests),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cardSupportingContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.token_stats_model_collapse
                    else R.string.token_stats_model_expand,
                ),
                modifier = Modifier.size(20.dp),
                tint = colors.cardSupportingContent,
            )
        }
        if (expanded) {
            val providerModel = normalizeProviderModel("${identity.provider}:${identity.model}")
            val priceScope = tokenStatsPriceScopeForConfigId(identity.configId)
            val priceOverride =
                priceSettings.firstOrNull {
                    it.scope == priceScope &&
                        normalizeProviderModel(it.providerModel)
                            .equals(providerModel, ignoreCase = true) &&
                        (priceScope == TokenStatsPriceScope.PROVIDER_MODEL ||
                            it.configId == identity.configId)
                }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 42.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 收起态优先展示 model；provider 放入展开区域，避免长 provider 挤掉 model。
                Text(
                    text = identity.provider,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cardSupportingContent,
                    softWrap = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.token_stats_token_uncached),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cardSupportingContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatTokenCount(
                                totals.uncachedInput.knownSum,
                                tokenDisplayUnit,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.token_stats_token_cached),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cardSupportingContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatTokenCount(
                                totals.cachedInput.knownSum,
                                tokenDisplayUnit,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.token_stats_token_output),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cardSupportingContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatTokenCount(totals.output.knownSum, tokenDisplayUnit),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
                if (totals.uncachedInput.unknownEventCount > 0L ||
                    totals.cachedInput.unknownEventCount > 0L ||
                    totals.output.unknownEventCount > 0L
                ) {
                    Text(
                        text = stringResource(
                            R.string.token_stats_unknown_parts,
                            totals.uncachedInput.unknownEventCount +
                                totals.cachedInput.unknownEventCount +
                                totals.output.unknownEventCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.unknownHint,
                        maxLines = 1,
                    )
                }
                if (totals.cost.unknownContributionCount > 0L) {
                    Text(
                        text = stringResource(R.string.token_stats_unknown_cost, totals.cost.unknownContributionCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.unknownHint,
                        maxLines = 1,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            onEditPrice(
                                priceOverride,
                                priceDraftForIdentity(identity, priceSettings),
                                configurationName,
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (priceScope == TokenStatsPriceScope.CONFIG) {
                                    R.string.token_stats_pricing_configuration
                                } else {
                                    R.string.token_stats_pricing_model
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    TextButton(
                        enabled = priceOverride != null,
                        onClick = { priceOverride?.let(onResetConfigurationPrice) },
                    ) {
                        Text(
                            text = stringResource(R.string.reset_to_default),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

// ==== 统计设置 ====

/**
 * 统计设置卡：币种常驻，CNY 汇率默认以摘要展示，按需进入行内编辑。
 */
@Composable
internal fun TokenStatsSettingsCard(
    targetCurrency: PricingCurrency,
    onSelectCurrency: (PricingCurrency) -> Unit,
    manualRate: Double,
    rateIsEstimated: Boolean,
    onSaveRate: (Double) -> Boolean,
) {
    val colors = LocalTokenStatsColors.current
    var rateInput by remember { mutableStateOf(formatRateInput(manualRate)) }
    var rateEditing by rememberSaveable { mutableStateOf(false) }
    val rateStatus = stringResource(
        if (rateIsEstimated) {
            R.string.token_stats_rate_estimated
        } else {
            R.string.token_stats_rate_manual
        },
    )
    // 汇率外部变化（如从 DataStore 重新加载）时同步输入框
    LaunchedEffect(manualRate) {
        rateInput = formatRateInput(manualRate)
    }
    LaunchedEffect(targetCurrency) {
        if (targetCurrency != PricingCurrency.CNY) rateEditing = false
    }

    TokenStatsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TokenStatsSpacing.card),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TokenStatsCardTitle(stringResource(R.string.token_stats_settings))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.token_stats_currency),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TokenStatsCurrencyDropdown(
                    selected = targetCurrency,
                    onSelect = {
                        rateEditing = false
                        onSelectCurrency(it)
                    },
                    modifier = Modifier.width(88.dp),
                )
            }
            if (targetCurrency == PricingCurrency.CNY) {
                HorizontalDivider(color = colors.cardBorder)
                if (rateEditing) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_exchange_rate_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = rateStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.cardSupportingContent,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.token_stats_rate_input_prefix),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            OutlinedTextField(
                                value = rateInput,
                                onValueChange = { rateInput = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .width(88.dp)
                                    .height(52.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = PricingCurrency.CNY.code,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    val parsed = rateInput.toDoubleOrNull()
                                    if (parsed == null || !onSaveRate(parsed)) {
                                        // 非法输入保持原值并提示（Toast 由调用方统一处理）
                                        rateInput = formatRateInput(manualRate)
                                    } else {
                                        rateEditing = false
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.settings_save),
                                    tint = colors.cardAccent,
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_exchange_rate_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                            Text(
                                text = stringResource(
                                    R.string.token_stats_rate_summary,
                                    formatRateInput(manualRate),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.cardSupportingContent,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = rateStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cardSupportingContent,
                            maxLines = 1,
                        )
                        IconButton(onClick = { rateEditing = true }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.token_stats_rate_edit),
                                tint = colors.cardSupportingContent,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==== 定价草稿推导（沿用） ====

private fun tokenStatsIdentityKey(
    identity: com.ai.assistance.operit.data.stats.TokenStatsIdentityBreakdown,
): String = "${identity.configId}\u001f${identity.provider}\u001f${identity.model}"

private fun priceDraftForIdentity(
    identity: com.ai.assistance.operit.data.stats.TokenStatsIdentityBreakdown,
    priceSettings: List<TokenStatsPriceSetting>,
): TokenStatsPriceDraft {
    val providerModel = normalizeProviderModel("${identity.provider}:${identity.model}")
    val priceScope = tokenStatsPriceScopeForConfigId(identity.configId)
    val providerSettings =
        priceSettings.firstOrNull {
            it.scope == TokenStatsPriceScope.PROVIDER_MODEL &&
                normalizeProviderModel(it.providerModel).equals(providerModel, ignoreCase = true)
        }?.toModelPriceSettings()
    val configurationSettings =
        if (priceScope == TokenStatsPriceScope.CONFIG) {
            priceSettings.firstOrNull {
                it.scope == TokenStatsPriceScope.CONFIG &&
                    normalizeProviderModel(it.providerModel)
                        .equals(providerModel, ignoreCase = true) &&
                    it.configId == identity.configId
            }?.toModelPriceSettings()
        } else {
            null
        }
    val resolved =
        TokenPriceResolver.resolve(
            providerModel,
            mergePriceSettings(providerSettings, configurationSettings),
        )
    return TokenStatsPriceDraft(
        scope = priceScope,
        provider = normalizeProviderTypeId(identity.provider),
        model = identity.model,
        configId = identity.configId.takeIf { priceScope == TokenStatsPriceScope.CONFIG },
        billingMode = resolved.billingMode,
        currency = resolved.currency,
        inputPricePerMillion = resolved.inputPricePerMillion,
        cachedInputPricePerMillion = resolved.cachedInputPricePerMillion,
        cacheWritePricePerMillion = resolved.cacheWritePricePerMillion,
        outputPricePerMillion = resolved.outputPricePerMillion,
        pricePerRequest = resolved.pricePerRequest,
    )
}

private fun mergePriceSettings(
    provider: com.ai.assistance.operit.data.stats.ModelPriceSettings?,
    configuration: com.ai.assistance.operit.data.stats.ModelPriceSettings?,
) =
    com.ai.assistance.operit.data.stats.ModelPriceSettings(
        billingMode = configuration?.billingMode ?: provider?.billingMode,
        currency = configuration?.currency ?: provider?.currency,
        inputPricePerMillion = configuration?.inputPricePerMillion ?: provider?.inputPricePerMillion,
        cachedInputPricePerMillion =
            configuration?.cachedInputPricePerMillion ?: provider?.cachedInputPricePerMillion,
        cacheWritePricePerMillion =
            configuration?.cacheWritePricePerMillion ?: provider?.cacheWritePricePerMillion,
        outputPricePerMillion = configuration?.outputPricePerMillion ?: provider?.outputPricePerMillion,
        pricePerRequest = configuration?.pricePerRequest ?: provider?.pricePerRequest,
    )

private fun TokenStatsPriceSetting.toModelPriceSettings() =
    com.ai.assistance.operit.data.stats.ModelPriceSettings(
        billingMode = billingMode,
        currency = currency,
        inputPricePerMillion = inputPricePerMillion,
        cachedInputPricePerMillion = cachedInputPricePerMillion,
        cacheWritePricePerMillion = cacheWritePricePerMillion,
        outputPricePerMillion = outputPricePerMillion,
        pricePerRequest = pricePerRequest,
    )

private fun formatRateInput(rate: Double): String =
    String.format(Locale.US, "%.4f", rate).trimEnd('0').trimEnd('.')
