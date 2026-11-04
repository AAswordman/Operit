package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.stats.TokenStatsGranularity
import com.ai.assistance.operit.data.stats.TokenStatsTrendBucket
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/**
 * 统计图表（信息架构重构版）：纯 Compose Canvas，不引入图表依赖。
 *
 * 交互契约（避免与页面滚动互抢）：
 * - 点击与**水平拖动**才选中/切换桶详情（[detectTapGestures] +
 *   [detectHorizontalDragGestures]）；
 * - 垂直手势不消费，LazyColumn 纵向滚动不受影响；
 * - 桶详情以图表下方的 tooltip 卡片呈现（无悬浮层，不遮挡内容）。
 *
 * 紧凑模式由调用方通过 chartHeight、maxLabels 和 tooltipCard 控制，
 * 用于在单卡片内呈现可切换的趋势指标。
 */

/** 堆叠柱状图：每桶若干堆叠分量（值 + 颜色）。 */
@Composable
internal fun TokenStatsStackedBarChart(
    style: TokenStatsChartStyle,
    modifier: Modifier = Modifier,
    buckets: List<TokenStatsTrendBucket>,
    granularity: TokenStatsGranularity,
    zone: ZoneId,
    formatValue: (Double) -> String,
    emptyText: String,
    chartLabel: String = "",
    chartHeight: Dp = 158.dp,
    maxLabels: Int = 6,
    showRefValues: Boolean = true,
    tooltipCard: Boolean = true,
    stackSelector: (TokenStatsTrendBucket) -> List<Pair<Double, Color>>,
    stackLabels: (TokenStatsTrendBucket) -> List<String>,
    /**
     * tooltip/无障碍里的“合计”数值。默认 = 堆叠分量之和（诊断口径）；调用方可
     * 传入 canonical 合计（如 totalTokens）使展示总 Token 与聚合器口径一致，
     * 堆叠分量仍作为诊断明细展示。
     */
    stackTotalSelector: (TokenStatsTrendBucket) -> Double = { bucket ->
        stackSelector(bucket).sumOf { it.first }
    },
    unknownNote: (TokenStatsTrendBucket) -> String? = { null },
) {
    if (buckets.isEmpty()) {
        ChartEmptyText(emptyText, style, modifier)
        return
    }
    var selectedIndex by remember(buckets) { mutableIntStateOf(buckets.lastIndex) }
    val density = LocalDensity.current
    val d = density.density
    val chartTopPx = 12f * d
    val labelReservePx = 20f * d

    // 无障碍文案预取：semantics 块不是 Composable，不能在块内解析资源（P1-8）
    val summaryTemplate = stringResource(R.string.token_stats_chart_summary)
    val bucketPositionTemplate = stringResource(R.string.token_stats_chart_bucket_of)
    val prevBucketLabel = stringResource(R.string.token_stats_chart_prev_bucket)
    val nextBucketLabel = stringResource(R.string.token_stats_chart_next_bucket)

    val maxVal = buckets.maxOf { bucket -> stackSelector(bucket).sumOf { it.first } }.coerceAtLeast(0.0)
    val refTop = niceCeil(maxVal)
    val refHalf = refTop / 2.0
    val scale = refTop

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availPx = maxWidth.value * d
        val barAreaPx = availPx / buckets.size
        val barW = barAreaPx * 0.65f

        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .semantics(mergeDescendants = true) {
                        val selected = buckets[selectedIndex]
                        val stacks = stackSelector(selected)
                        val positionText =
                            String.format(bucketPositionTemplate, selectedIndex + 1, buckets.size)
                        val summary = String.format(
                            summaryTemplate,
                            chartLabel,
                            bucketTimeLabel(selected.bucketStartMs, granularity, zone),
                            positionText,
                            formatValue(stackTotalSelector(selected)),
                        )
                        val rows = stacks.mapIndexedNotNull { index, (value, _) ->
                            val label = stackLabels(selected).getOrNull(index) ?: ""
                            if (value > 0.0 || label.isNotEmpty()) {
                                "${label.ifEmpty { "" }} ${formatValue(value)}".trim()
                            } else {
                                null
                            }
                        }
                        contentDescription = chartAccessibilityDescription(summary, rows)
                        stateDescription = positionText
                        role = Role.Image
                        customActions = listOf(
                            CustomAccessibilityAction(prevBucketLabel) {
                                previousBucketIndex(selectedIndex, buckets.size)
                                    ?.let { selectedIndex = it; true } ?: false
                            },
                            CustomAccessibilityAction(nextBucketLabel) {
                                nextBucketIndex(selectedIndex, buckets.size)
                                    ?.let { selectedIndex = it; true } ?: false
                            },
                        )
                    }
                    .focusable()
                    .pointerInput(buckets) {
                        detectTapGestures { offset ->
                            val idx = (offset.x / barAreaPx).toInt().coerceIn(0, buckets.lastIndex)
                            selectedIndex = idx
                        }
                    }
                    .pointerInput(buckets) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val idx = (change.position.x / barAreaPx).toInt().coerceIn(0, buckets.lastIndex)
                            selectedIndex = idx
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                ) {
                    val chartBottomPx = size.height - labelReservePx
                    val chartHeightPx = chartBottomPx - chartTopPx
                    buckets.forEachIndexed { i, bucket ->
                        val x = i * barAreaPx + (barAreaPx - barW) / 2
                        var yBase = chartBottomPx
                        stackSelector(bucket).forEach { (value, color) ->
                            val h = (value / scale * chartHeightPx).toFloat().coerceAtLeast(0f)
                            drawRect(color, Offset(x, yBase - h), Size(barW, h))
                            yBase -= h
                        }
                        val (label, show) = bucketLabel(
                            bucket.bucketStartMs, granularity, zone, i, buckets.size, maxLabels,
                        )
                        if (show) {
                            drawContext.canvas.nativeCanvas.drawText(
                                label, x + barW / 2, chartBottomPx + labelReservePx - 4f * d,
                                android.graphics.Paint().apply {
                                    color = style.labelColor.toArgb()
                                    textSize = (if (tooltipCard) 10f else 7f) * d * density.fontScale
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                            )
                        }
                    }
                    // 参考线（满刻度与半刻度）+ 数值标签
                    val refY = chartBottomPx - (refTop / scale * chartHeightPx).toFloat()
                    val refHalfY = chartBottomPx - (refHalf / scale * chartHeightPx).toFloat()
                    drawLine(style.gridColor, Offset(0f, refY), Offset(size.width, refY), strokeWidth = 0.5f * d)
                    drawLine(style.gridColor, Offset(0f, refHalfY), Offset(size.width, refHalfY), strokeWidth = 0.5f * d)
                    if (showRefValues) {
                        val paint = android.graphics.Paint().apply {
                            color = style.labelColor.toArgb()
                            textSize = 8f * d * density.fontScale
                            textAlign = android.graphics.Paint.Align.LEFT
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(formatValue(refTop), 2f * d, refY - 2f * d, paint)
                        drawContext.canvas.nativeCanvas.drawText(formatValue(refHalf), 2f * d, refHalfY - 2f * d, paint)
                        drawContext.canvas.nativeCanvas.drawText(formatValue(0.0), 2f * d, chartBottomPx - 2f * d, paint)
                    }
                }
            }

            val selected = buckets[selectedIndex]
            val stacks = stackSelector(selected)
            ChartTooltip(
                title = bucketTimeLabel(selected.bucketStartMs, granularity, zone),
                rows =
                    if (tooltipCard) {
                        stacks.mapIndexedNotNull { index, (value, color) ->
                            val label = stackLabels(selected).getOrNull(index) ?: ""
                            if (value > 0.0 || label.isNotEmpty()) {
                                Triple(color, label, formatValue(value))
                            } else {
                                null
                            }
                        }
                    } else {
                        emptyList()
                    },
                total = formatValue(stackTotalSelector(selected)),
                unknownNote = unknownNote(selected),
                style = style,
                card = tooltipCard,
            )
        }
    }
}

/** 折线图：每桶一个值；无有效样本的桶不画点、线段断开。 */
@Composable
internal fun TokenStatsLineChart(
    style: TokenStatsChartStyle,
    modifier: Modifier = Modifier,
    buckets: List<TokenStatsTrendBucket>,
    granularity: TokenStatsGranularity,
    zone: ZoneId,
    formatValue: (Double) -> String,
    emptyText: String,
    chartLabel: String = "",
    chartHeight: Dp = 158.dp,
    maxLabels: Int = 6,
    showRefValues: Boolean = true,
    tooltipCard: Boolean = true,
    valueSelector: (TokenStatsTrendBucket) -> Double?,
    unknownNote: (TokenStatsTrendBucket) -> String? = { null },
) {
    if (buckets.isEmpty()) {
        ChartEmptyText(emptyText, style, modifier)
        return
    }
    var selectedIndex by remember(buckets) { mutableIntStateOf(buckets.lastIndex) }
    val density = LocalDensity.current
    val d = density.density
    val chartTopPx = 12f * d
    val labelReservePx = 20f * d

    // 无障碍文案预取：semantics 块不是 Composable，不能在块内解析资源（P1-8）
    val summaryTemplate = stringResource(R.string.token_stats_chart_summary)
    val bucketPositionTemplate = stringResource(R.string.token_stats_chart_bucket_of)
    val prevBucketLabel = stringResource(R.string.token_stats_chart_prev_bucket)
    val nextBucketLabel = stringResource(R.string.token_stats_chart_next_bucket)

    val knownValues = buckets.mapNotNull(valueSelector)
    val maxVal = (knownValues.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
    val refTop = niceCeil(maxVal)
    val refHalf = refTop / 2.0
    val scale = refTop

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availPx = maxWidth.value * d
        val barAreaPx = availPx / buckets.size

        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .semantics(mergeDescendants = true) {
                        val selected = buckets[selectedIndex]
                        val value = valueSelector(selected)
                        val positionText =
                            String.format(bucketPositionTemplate, selectedIndex + 1, buckets.size)
                        val summary = String.format(
                            summaryTemplate,
                            chartLabel,
                            bucketTimeLabel(selected.bucketStartMs, granularity, zone),
                            positionText,
                            if (value == null) "" else formatValue(value),
                        )
                        contentDescription = chartAccessibilityDescription(summary, emptyList())
                        stateDescription = positionText
                        role = Role.Image
                        customActions = listOf(
                            CustomAccessibilityAction(prevBucketLabel) {
                                previousBucketIndex(selectedIndex, buckets.size)
                                    ?.let { selectedIndex = it; true } ?: false
                            },
                            CustomAccessibilityAction(nextBucketLabel) {
                                nextBucketIndex(selectedIndex, buckets.size)
                                    ?.let { selectedIndex = it; true } ?: false
                            },
                        )
                    }
                    .focusable()
                    .pointerInput(buckets) {
                        detectTapGestures { offset ->
                            val idx = (offset.x / barAreaPx).toInt().coerceIn(0, buckets.lastIndex)
                            selectedIndex = idx
                        }
                    }
                    .pointerInput(buckets) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val idx = (change.position.x / barAreaPx).toInt().coerceIn(0, buckets.lastIndex)
                            selectedIndex = idx
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                ) {
                    val chartBottomPx = size.height - labelReservePx
                    val chartHeightPx = chartBottomPx - chartTopPx
                    val points = buckets.mapIndexed { i, bucket ->
                        val value = valueSelector(bucket)
                        if (value == null) {
                            null
                        } else {
                            Offset(
                                i * barAreaPx + barAreaPx / 2,
                                chartBottomPx - (value / scale * chartHeightPx).toFloat(),
                            )
                        }
                    }
                    // 分段连线：null 断段；每段只连接**相邻**有效点（此前从段首
                    // 重复连线会导致斜率错误）
                    lineSegments(points).forEach { (start, end) ->
                        drawLine(style.accentColor, start, end, strokeWidth = 2f * d)
                    }
                    points.forEachIndexed { _, point ->
                        if (point != null) {
                            drawCircle(style.accentColor, radius = 3f * d, center = point)
                        }
                    }
                    buckets.forEachIndexed { i, bucket ->
                        val (label, show) = bucketLabel(
                            bucket.bucketStartMs, granularity, zone, i, buckets.size, maxLabels,
                        )
                        if (show) {
                            drawContext.canvas.nativeCanvas.drawText(
                                label, i * barAreaPx + barAreaPx / 2, chartBottomPx + labelReservePx - 4f * d,
                                android.graphics.Paint().apply {
                                    color = style.labelColor.toArgb()
                                    textSize = (if (tooltipCard) 10f else 7f) * d * density.fontScale
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                            )
                        }
                    }
                    val refY = chartBottomPx - (refTop / scale * chartHeightPx).toFloat()
                    val refHalfY = chartBottomPx - (refHalf / scale * chartHeightPx).toFloat()
                    drawLine(style.gridColor, Offset(0f, refY), Offset(size.width, refY), strokeWidth = 0.5f * d)
                    drawLine(style.gridColor, Offset(0f, refHalfY), Offset(size.width, refHalfY), strokeWidth = 0.5f * d)
                    if (showRefValues) {
                        val paint = android.graphics.Paint().apply {
                            color = style.labelColor.toArgb()
                            textSize = 8f * d * density.fontScale
                            textAlign = android.graphics.Paint.Align.LEFT
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(formatValue(refTop), 2f * d, refY - 2f * d, paint)
                        drawContext.canvas.nativeCanvas.drawText(formatValue(refHalf), 2f * d, refHalfY - 2f * d, paint)
                        drawContext.canvas.nativeCanvas.drawText(formatValue(0.0), 2f * d, chartBottomPx - 2f * d, paint)
                    }
                }
            }

            val selected = buckets[selectedIndex]
            val value = valueSelector(selected)
            ChartTooltip(
                title = bucketTimeLabel(selected.bucketStartMs, granularity, zone),
                rows =
                    if (tooltipCard) {
                        value?.let { listOf(Triple(style.accentColor, "", formatValue(it))) }
                            ?: emptyList()
                    } else {
                        emptyList()
                    },
                total = if (value == null) null else formatValue(value),
                unknownNote = unknownNote(selected),
                style = style,
                card = tooltipCard,
            )
        }
    }
}

/**
 * 周期总览面积折线图（设计规范 §6.3）：
 * - 主色折线（1.5–2dp）+ 线下主色低透明度→透明渐变填充；
 * - 仅少量水平虚线网格；Y 轴只标 0 / 中间 / 最大；X 轴只标开始与结束日期；
 * - 最后一个数据点实心圆点；点击/拖动选中桶并在图下显示说明行。
 */
@Composable
internal fun TokenStatsAreaChart(
    style: TokenStatsChartStyle,
    modifier: Modifier = Modifier,
    buckets: List<TokenStatsTrendBucket>,
    granularity: TokenStatsGranularity,
    zone: ZoneId,
    formatValue: (Double) -> String,
    emptyText: String,
    chartLabel: String = "",
    chartHeight: Dp = 150.dp,
    valueSelector: (TokenStatsTrendBucket) -> Double?,
) {
    if (buckets.isEmpty()) {
        ChartEmptyText(emptyText, style, modifier)
        return
    }
    var selectedIndex by remember(buckets) { mutableIntStateOf(buckets.lastIndex) }
    val density = LocalDensity.current
    val d = density.density
    val chartTopPx = 12f * d
    val labelReservePx = 18f * d
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f * d, 4f * d))

    val summaryTemplate = stringResource(R.string.token_stats_chart_summary)
    val bucketPositionTemplate = stringResource(R.string.token_stats_chart_bucket_of)
    val prevBucketLabel = stringResource(R.string.token_stats_chart_prev_bucket)
    val nextBucketLabel = stringResource(R.string.token_stats_chart_next_bucket)

    val knownValues = buckets.mapNotNull(valueSelector)
    val maxVal = (knownValues.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
    val refTop = niceCeil(maxVal)
    val refHalf = refTop / 2.0
    val scale = refTop

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availPx = maxWidth.value * d
        val stepPx = availPx / buckets.size

        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .semantics(mergeDescendants = true) {
                        val selected = buckets[selectedIndex]
                        val value = valueSelector(selected)
                        val positionText =
                            String.format(bucketPositionTemplate, selectedIndex + 1, buckets.size)
                        val summary = String.format(
                            summaryTemplate,
                            chartLabel,
                            bucketTimeLabel(selected.bucketStartMs, granularity, zone),
                            positionText,
                            if (value == null) "" else formatValue(value),
                        )
                        contentDescription = chartAccessibilityDescription(summary, emptyList())
                        stateDescription = positionText
                        role = Role.Image
                        customActions = listOf(
                            CustomAccessibilityAction(prevBucketLabel) {
                                previousBucketIndex(selectedIndex, buckets.size)
                                    ?.let { selectedIndex = it; true } ?: false
                            },
                            CustomAccessibilityAction(nextBucketLabel) {
                                nextBucketIndex(selectedIndex, buckets.size)
                                    ?.let { selectedIndex = it; true } ?: false
                            },
                        )
                    }
                    .focusable()
                    .pointerInput(buckets) {
                        detectTapGestures { offset ->
                            val idx = (offset.x / stepPx).toInt().coerceIn(0, buckets.lastIndex)
                            selectedIndex = idx
                        }
                    }
                    .pointerInput(buckets) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val idx = (change.position.x / stepPx).toInt().coerceIn(0, buckets.lastIndex)
                            selectedIndex = idx
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                ) {
                    val chartBottomPx = size.height - labelReservePx
                    val chartHeightPx = chartBottomPx - chartTopPx
                    val points = buckets.mapIndexed { i, bucket ->
                        val value = valueSelector(bucket)
                        if (value == null) {
                            null
                        } else {
                            Offset(
                                i * stepPx + stepPx / 2,
                                chartBottomPx - (value / scale * chartHeightPx).toFloat(),
                            )
                        }
                    }
                    val lastPoint = points.lastOrNull { it != null }
                    if (lastPoint != null) {
                        // 面积填充：有效点连成折线后闭合到底部，纵向渐变（顶部
                        // 主色低透明度 → 底部透明）
                        val area = Path().apply {
                            var started = false
                            points.forEach { point ->
                                if (point != null) {
                                    if (!started) {
                                        moveTo(point.x, chartBottomPx)
                                        started = true
                                    }
                                    lineTo(point.x, point.y)
                                }
                            }
                            if (started) {
                                lineTo(lastPoint.x, chartBottomPx)
                                close()
                            }
                        }
                        drawPath(
                            path = area,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    style.accentColor.copy(alpha = 0.26f),
                                    Color.Transparent,
                                ),
                                startY = chartTopPx,
                                endY = chartBottomPx,
                            ),
                        )
                    }
                    lineSegments(points).forEach { (start, end) ->
                        drawLine(style.accentColor, start, end, strokeWidth = 1.75f * d)
                    }
                    points.forEachIndexed { _, point ->
                        if (point != null) {
                            drawCircle(style.accentColor, radius = 2.5f * d, center = point)
                        }
                    }
                    // 虚线网格：满刻度 / 半刻度 / 基线
                    val refY = chartBottomPx - (refTop / scale * chartHeightPx).toFloat()
                    val refHalfY = chartBottomPx - (refHalf / scale * chartHeightPx).toFloat()
                    drawLine(style.gridColor, Offset(0f, refY), Offset(size.width, refY), strokeWidth = 0.5f * d, pathEffect = dash)
                    drawLine(style.gridColor, Offset(0f, refHalfY), Offset(size.width, refHalfY), strokeWidth = 0.5f * d, pathEffect = dash)
                    // Y 轴刻度：0 / 中间 / 最大
                    val axisPaint = android.graphics.Paint().apply {
                        color = style.labelColor.toArgb()
                        textSize = 8f * d * density.fontScale
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(formatValue(refTop), 2f * d, refY - 2f * d, axisPaint)
                    drawContext.canvas.nativeCanvas.drawText(formatValue(refHalf), 2f * d, refHalfY - 2f * d, axisPaint)
                    drawContext.canvas.nativeCanvas.drawText(formatValue(0.0), 2f * d, chartBottomPx - 2f * d, axisPaint)
                    // X 轴：开始与结束日期
                    val xPaint = android.graphics.Paint().apply {
                        color = style.labelColor.toArgb()
                        textSize = 10f * d * density.fontScale
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    val firstLabel = bucketTimeLabel(buckets.first().bucketStartMs, granularity, zone)
                    val lastLabel = bucketTimeLabel(buckets.last().bucketStartMs, granularity, zone)
                    drawContext.canvas.nativeCanvas.drawText(
                        firstLabel, stepPx / 2, chartBottomPx + labelReservePx - 2f * d, xPaint,
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        lastLabel, size.width - stepPx / 2, chartBottomPx + labelReservePx - 2f * d, xPaint,
                    )
                }
            }

            // 选中桶说明行（点击/拖动切换；默认最后一个桶）
            val selected = buckets[selectedIndex]
            val value = valueSelector(selected)
            Text(
                text = "${bucketTimeLabel(selected.bucketStartMs, granularity, zone)} · " +
                    (value?.let(formatValue) ?: "--"),
                style = MaterialTheme.typography.bodySmall,
                color = style.labelColor,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** tooltip 卡片（图表下方，不遮挡内容）；非卡片模式用于紧凑摘要行。 */
@Composable
private fun ChartTooltip(
    title: String,
    rows: List<Triple<Color, String, String>>,
    total: String?,
    unknownNote: String?,
    style: TokenStatsChartStyle,
    card: Boolean,
) {
    if (card) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .heightIn(min = 64.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = style.tooltipContainerColor,
                contentColor = style.tooltipContentColor,
            ),
        ) {
            ChartTooltipContent(
                title,
                rows,
                total,
                unknownNote,
                style,
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .heightIn(min = 40.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = style.labelColor,
                    maxLines = 1,
                )
                total?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = style.accentColor,
                        maxLines = 1,
                    )
                }
            }
            unknownNote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = style.labelColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ChartTooltipContent(
    title: String,
    rows: List<Triple<Color, String, String>>,
    total: String?,
    unknownNote: String?,
    style: TokenStatsChartStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = style.tooltipContentColor,
            )
            total?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = style.accentColor,
                )
            }
            rows.forEach { (color, label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(color, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (label.isNotEmpty()) "$label $value" else value,
                        style = MaterialTheme.typography.bodySmall,
                        color = style.tooltipContentColor,
                        maxLines = 1,
                    )
                }
            }
            unknownNote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = style.tooltipContentColor.copy(alpha = 0.76f),
                    maxLines = 2,
                )
            }
    }
}

@Composable
private fun ChartEmptyText(
    text: String,
    style: TokenStatsChartStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = style.labelColor,
        )
    }
}

/** 桶起点时间标签（本地时区对齐，与聚合器同语义）。 */
internal fun bucketTimeLabel(startMs: Long, granularity: TokenStatsGranularity, zone: ZoneId): String {
    val zdt = Instant.ofEpochMilli(startMs).atZone(zone)
    return when (granularity) {
        TokenStatsGranularity.TEN_MINUTES, TokenStatsGranularity.HOURLY ->
            DateTimeFormatter.ofPattern("HH:mm").format(zdt)
        TokenStatsGranularity.DAILY ->
            DateTimeFormatter.ofPattern("MM/dd").format(zdt)
    }
}

/**
 * 底部时间轴标签抽稀：
 * - maxLabels >= 6：首尾 + 均匀抽稀（全尺寸图表）；
 * - maxLabels == 3：首 / 中 / 尾（趋势分析卡）；
 * - maxLabels <= 2：首尾（总览面积图由调用方自绘，此分支供极窄布局）。
 */
private fun bucketLabel(
    startMs: Long,
    granularity: TokenStatsGranularity,
    zone: ZoneId,
    index: Int,
    total: Int,
    maxLabels: Int,
): Pair<String, Boolean> {
    if (total <= 1) return bucketTimeLabel(startMs, granularity, zone) to true
    val show = when {
        maxLabels <= 2 -> index == 0 || index == total - 1
        maxLabels == 3 -> index == 0 || index == total / 2 || index == total - 1
        else -> {
            val stride = ceil(total / 6.0).toInt().coerceAtLeast(1)
            index == 0 || index == total - 1 || index % stride == 0
        }
    }
    return bucketTimeLabel(startMs, granularity, zone) to show
}

/** 向上取整到“漂亮”刻度（9→10、883→1000、150M→200M），与参考实现一致。 */
internal fun niceCeil(value: Double): Double {
    if (value <= 0.0) return 1.0
    val exp = log10(value).toInt()
    val magnitude = 10.0.pow(exp.toDouble())
    val normalized = value / magnitude
    val nice =
        when {
            normalized <= 1.0 -> 1.0
            normalized <= 1.15 -> 1.15
            normalized <= 1.25 -> 1.25
            normalized <= 1.5 -> 1.5
            normalized <= 2.0 -> 2.0
            normalized <= 2.5 -> 2.5
            normalized <= 3.0 -> 3.0
            normalized <= 4.0 -> 4.0
            normalized <= 5.0 -> 5.0
            normalized <= 7.5 -> 7.5
            else -> 10.0
        }
    return nice * magnitude
}

/** Token 数量紧凑格式：1.2K / 3.4M。 */
internal fun formatCompactCount(value: Long): String =
    when {
        value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", value / 1_000.0)
        else -> "$value"
    }

/** 千分位格式（图表 tooltip 明细用）。 */
internal fun formatCountWithComma(value: Long): String =
    String.format(java.util.Locale.US, "%,d", value)

// ==== 图表无障碍模型（P1-8，纯函数，供 JVM 测试） ====

/** 无障碍“上一桶”目标索引；已在最前或无桶返回 null（边界禁用）。 */
internal fun previousBucketIndex(current: Int, count: Int): Int? =
    if (count <= 1 || current <= 0) null else current - 1

/** 无障碍“下一桶”目标索引；已在最后或无桶返回 null（边界禁用）。 */
internal fun nextBucketIndex(current: Int, count: Int): Int? =
    if (count <= 1 || current >= count - 1) null else current + 1

/**
 * 图表无障碍描述（TalkBack 朗读）：[summary] 已由调用方按资源拼好（图表名、
 * 当前桶时间、第 n/m 桶、合计），[rows] 为“标签 值”明细行；无行时只读摘要。
 */
internal fun chartAccessibilityDescription(summary: String, rows: List<String>): String =
    if (rows.isEmpty()) summary else "$summary：${rows.joinToString("，")}"

/**
 * 折线分段：null 断段；每段连接**相邻**有效点（而非从段首重复连线）。
 * 返回线段对列表，供 Canvas 绘制与纯 JVM 测试共用。
 */
internal fun lineSegments(points: List<Offset?>): List<Pair<Offset, Offset>> {
    val segments = ArrayList<Pair<Offset, Offset>>()
    var previous: Offset? = null
    for (point in points) {
        if (point == null) {
            previous = null
        } else {
            previous?.let { segments += it to point }
            previous = point
        }
    }
    return segments
}
