package com.ai.assistance.operit.ui.features.tokenstats

import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.stats.TokenActivityDay
import com.ai.assistance.operit.data.stats.TokenActivityViewMode
import com.ai.assistance.operit.data.stats.TokenStatsDisplayUnit
import com.ai.assistance.operit.data.stats.formatTokenCount
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 活跃记录卡（信息架构重构版，设计规范 §6.5）：
 * - 标题行右侧展示当前连续 / 最长连续胶囊（数据来自 activity.stats）；
 * - 热力图左侧固定星期标签（一~日），与网格同步滚动方向无关；
 * - 热力色阶 = 未激活灰格 + 主色 5 档透明度阶；
 * - 视图模式（每日/每周/累计）由页面顶部三段式选择器驱动，本卡只负责呈现。
 */
@Composable
internal fun TokenActivitySection(
    state: TokenActivityUiState,
    tokenDisplayUnit: TokenStatsDisplayUnit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val stats = state.rangeData?.stats

    TokenStatsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TokenStatsSpacing.card),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TokenStatsCardTitle(stringResource(R.string.token_activity_title)) {
                stats?.let {
                    TokenStatsPill(
                        stringResource(R.string.token_activity_current_streak_badge, it.currentStreak)
                    )
                    Spacer(Modifier.width(6.dp))
                    TokenStatsPill(
                        stringResource(R.string.token_activity_longest_streak_badge, it.longestStreak)
                    )
                }
            }
            Crossfade(
                targetState = state.viewMode,
                animationSpec = tween(150),
                label = "token_activity_heatmap",
            ) { mode ->
                TokenActivityVisualization(
                    state = state.copy(viewMode = mode),
                    locale = locale,
                    tokenDisplayUnit = tokenDisplayUnit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TokenActivityVisualization(
    state: TokenActivityUiState,
    locale: Locale,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    modifier: Modifier = Modifier,
) {
    if (state.loading || state.rangeData == null) {
        Box(modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LocalTokenStatsColors.current.chartAccent)
        }
        return
    }
    when (state.viewMode) {
        TokenActivityViewMode.DAILY -> TokenActivityDailyHeatmap(state, locale, tokenDisplayUnit, modifier)
        TokenActivityViewMode.WEEKLY -> TokenActivityWeeklyChart(state, locale, tokenDisplayUnit, modifier)
        TokenActivityViewMode.CUMULATIVE -> TokenActivityCumulativeChart(state, locale, tokenDisplayUnit, modifier)
    }
}

@Composable
private fun TokenActivityDailyHeatmap(
    state: TokenActivityUiState,
    locale: Locale,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    modifier: Modifier = Modifier,
) {
    val data = state.rangeData
    checkNotNull(data)

    val days = data.daily
    val firstDate = days.firstOrNull()?.date
    // 网格和左侧标签都按周一到周日排列；此前沿用周日首列偏移，导致标签与日期错行。
    val padding = firstDate?.let { (it.dayOfWeek.value - 1).coerceIn(0, 6) } ?: 0
    val density = LocalDensity.current
    val block = 12.dp
    val gap = 4.dp
    val stepPx = with(density) { (block + gap).toPx() }
    val blockPx = with(density) { block.toPx() }
    val radiusPx = with(density) { 3.dp.toPx() }
    val gridHeight = (block + gap) * 7 - gap
    val monthLabelHeight = 20.dp
    val weekdayLabelWidth = 24.dp
    val heatmapColumnGap = 6.dp
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val scroll = rememberScrollState()
    val palette = LocalTokenStatsColors.current
    // 色阶：level 0 = 未激活灰格；level 1..5 = 主色透明度阶（少→多）
    val levelColor: (Int) -> androidx.compose.ui.graphics.Color = { level ->
        if (level <= 0) palette.heatmapInactive
        else palette.heatmapLevels[(level - 1).coerceIn(0, palette.heatmapLevels.lastIndex)]
    }
    val heatmapLabelColor = palette.chartLabel
    val selectionColor = palette.chartAccent
    val selectionStroke = with(density) { 1.5.dp.toPx() }

    BoxWithConstraints(modifier) {
        val historyColumns = ((padding + days.size + 6) / 7).coerceAtLeast(1)
        val heatmapViewportWidth =
            (maxWidth - weekdayLabelWidth - heatmapColumnGap).coerceAtLeast(0.dp)
        val visibleColumns =
            ((heatmapViewportWidth.value + gap.value) / (block.value + gap.value))
                .toInt()
                .coerceAtLeast(1)
        val leadingColumns = (visibleColumns - historyColumns).coerceAtLeast(0)
        val columns = historyColumns + leadingColumns
        val grid = remember(days, padding, leadingColumns, columns) {
            List(columns) { column ->
                val sourceColumn = column - leadingColumns
                List<TokenActivityDay?>(7) { row ->
                    if (sourceColumn < 0) null
                    else days.getOrNull(sourceColumn * 7 + row - padding)
                }
            }
        }
        val width = (block + gap) * columns - gap
        var selectedDay by remember(days, state.viewMode) {
            mutableStateOf<TokenActivityDay?>(null)
        }
        var indicatorDay by remember(grid, state.viewMode) {
            mutableStateOf<TokenActivityDay?>(null)
        }
        var indicatorColumn by remember(grid, state.viewMode) {
            mutableIntStateOf(-1)
        }
        var indicatorRow by remember(grid, state.viewMode) {
            mutableIntStateOf(-1)
        }
        val monthLabels = remember(grid, locale) {
            val formatter = DateTimeFormatter.ofPattern("MMM", locale)
            val raw = buildList {
                var previousMonth = -1
                grid.forEachIndexed { index, week ->
                    val date = week.firstOrNull { it != null }?.date ?: return@forEachIndexed
                    if (index == 0 || date.monthValue != previousMonth) {
                        add(TokenActivityMonthLabel(index, formatter.format(date)))
                        previousMonth = date.monthValue
                    }
                }
            }
            raw.filterIndexed { index, label ->
                when {
                    index == 0 -> raw.getOrNull(1)?.let { it.column - label.column >= 3 } ?: true
                    index == raw.lastIndex -> columns - label.column >= 3
                    else -> true
                }
            }
        }
        val monthPaint = remember(density, heatmapLabelColor) {
            Paint().apply {
                textSize = with(density) { 12.sp.toPx() }
                color = heatmapLabelColor.toArgb()
                isAntiAlias = true
            }
        }

        LaunchedEffect(columns, days, state.viewMode) {
            snapshotFlow { scroll.maxValue }.first { it > 0 }
            scroll.scrollTo(scroll.maxValue)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(heatmapColumnGap)) {
                // 星期标签列与网格行高严格对齐。
                Column(
                    modifier = Modifier.width(weekdayLabelWidth),
                ) {
                    weekdayLabels.forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(block),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                // 标签行高不能超过方格高度，否则中文笔画会被 Text 约束裁切。
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                color = heatmapLabelColor,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (index < weekdayLabels.lastIndex) Spacer(Modifier.height(gap))
                    }
                }
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(width, gridHeight)
                                // 顺序：查看/滚动仲裁必须先于点击检测收到事件。
                                .pointerInput(grid, stepPx, blockPx) {
                                val viewSpeedThresholdPxPerMs =
                                    with(density) { HEATMAP_VIEW_SPEED_DP_PER_S.dp.toPx() } / 1_000f

                                fun updateIndicator(point: Offset) {
                                    if (point.x < 0f ||
                                        point.x >= size.width.toFloat() ||
                                        point.y < 0f ||
                                        point.y >= size.height.toFloat()
                                    ) {
                                        indicatorDay = null
                                        indicatorColumn = -1
                                        indicatorRow = -1
                                        return
                                    }
                                    if (point.x % stepPx >= blockPx || point.y % stepPx >= blockPx) {
                                        indicatorDay = null
                                        indicatorColumn = -1
                                        indicatorRow = -1
                                        return
                                    }
                                    val column = (point.x / stepPx).toInt().coerceIn(0, columns - 1)
                                    val row = (point.y / stepPx).toInt().coerceIn(0, 6)
                                    val day = grid.getOrNull(column)?.getOrNull(row)
                                    indicatorDay = day
                                    indicatorColumn = if (day == null) -1 else column
                                    indicatorRow = if (day == null) -1 else row
                                }

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var mode: HeatmapDragMode? = null
                                    var lastPosition = down.position
                                    var lastTime = SystemClock.uptimeMillis()
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    val slop = viewConfiguration.touchSlop
                                    val downTime = lastTime
                                    val longPressMs = viewConfiguration.longPressTimeoutMillis

                                    while (mode == null) {
                                        val remaining = longPressMs - (SystemClock.uptimeMillis() - downTime)
                                        val event = if (remaining > 0L) {
                                            withTimeoutOrNull(remaining) { awaitPointerEvent() }
                                        } else {
                                            null
                                        }
                                        if (event == null) {
                                            mode = HeatmapDragMode.VIEW
                                            break
                                        }
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        val current = change.position
                                        val now = SystemClock.uptimeMillis()
                                        val dx = current.x - lastPosition.x
                                        val dy = current.y - lastPosition.y
                                        val elapsed = (now - lastTime).coerceAtLeast(1L)
                                        val horizontalSpeed = abs(dx) / elapsed
                                        lastPosition = current
                                        lastTime = now
                                        totalDx += dx
                                        totalDy += dy
                                        if (abs(totalDx) > slop || abs(totalDy) > slop) {
                                            mode = if (
                                                abs(totalDx) > abs(totalDy) &&
                                                horizontalSpeed < viewSpeedThresholdPxPerMs
                                            ) {
                                                HeatmapDragMode.VIEW
                                            } else {
                                                HeatmapDragMode.SCROLL
                                            }
                                            if (mode == HeatmapDragMode.VIEW) change.consume()
                                        }
                                    }

                                    if (mode == HeatmapDragMode.VIEW) {
                                        updateIndicator(lastPosition)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            updateIndicator(change.position)
                                            change.consume()
                                            if (!change.pressed) break
                                        }
                                    } else if (mode == HeatmapDragMode.SCROLL) {
                                        indicatorDay = null
                                        indicatorColumn = -1
                                        indicatorRow = -1
                                    }
                                }
                            }
                            .pointerInput(grid) {
                                detectTapGestures { point ->
                                    indicatorDay = null
                                    indicatorColumn = -1
                                    indicatorRow = -1
                                    if (point.x % stepPx >= blockPx || point.y % stepPx >= blockPx) return@detectTapGestures
                                    val column = (point.x / stepPx).toInt()
                                    val row = (point.y / stepPx).toInt()
                                    val day = grid.getOrNull(column)?.getOrNull(row)
                                    if (day != null) selectedDay = if (selectedDay == day) null else day
                                }
                            },
                                ) {
                                    grid.forEachIndexed { column, week ->
                                        week.forEachIndexed { row, day ->
                                            drawRoundRect(
                                                color = levelColor(day?.level ?: 0),
                                                topLeft = Offset(column * stepPx, row * stepPx),
                                                size = Size(blockPx, blockPx),
                                                cornerRadius = CornerRadius(radiusPx),
                                            )
                                        }
                                    }

                                    val indicatorValid = when {
                                        indicatorDay != null -> grid.getOrNull(indicatorColumn)
                                            ?.getOrNull(indicatorRow) != null
                                        else -> false
                                    }
                                    if (indicatorValid && indicatorColumn in 0 until columns && indicatorRow in 0..6) {
                                        drawRoundRect(
                                            color = selectionColor,
                                            topLeft = Offset(indicatorColumn * stepPx, indicatorRow * stepPx),
                                            size = Size(blockPx, blockPx),
                                            cornerRadius = CornerRadius(radiusPx),
                                            style = Stroke(width = selectionStroke * 1.5f),
                                        )
                                    }
                                }

                        Spacer(Modifier.height(4.dp))
                        // 月份标签放在热力图下方，并与网格保持同一横向滚动位置。
                        Canvas(
                            modifier = Modifier.size(width, monthLabelHeight),
                        ) {
                            val baseline = -monthPaint.ascent()
                            monthLabels.forEach { label ->
                                drawIntoCanvas { canvas ->
                                    val x =
                                        (label.column * stepPx).coerceAtMost(
                                            (size.width - monthPaint.measureText(label.text))
                                                .coerceAtLeast(0f)
                                        )
                                    canvas.nativeCanvas.drawText(
                                        label.text,
                                        x,
                                        baseline,
                                        monthPaint,
                                    )
                                }
                            }
                        }
                    }
                }
            }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val text = when {
                indicatorDay != null -> stringResource(
                    R.string.token_activity_day_detail,
                    indicatorDay!!.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                    formatTokenCount(indicatorDay!!.tokens, tokenDisplayUnit),
                )
                selectedDay != null -> stringResource(
                    R.string.token_activity_day_detail,
                    selectedDay!!.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                    formatTokenCount(selectedDay!!.tokens, tokenDisplayUnit),
                )
                else -> stringResource(R.string.token_activity_tap_hint)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = heatmapLabelColor,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.token_activity_less),
                fontSize = 12.sp,
                color = heatmapLabelColor,
            )
            Spacer(Modifier.width(4.dp))
            palette.heatmapLevels.forEach { color ->
                Box(Modifier.size(block).background(color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(gap))
            }
            Text(
                stringResource(R.string.token_activity_more),
                fontSize = 12.sp,
                color = heatmapLabelColor,
            )
        }
    }
}
}

@Composable
private fun TokenActivityWeeklyChart(
    state: TokenActivityUiState,
    locale: Locale,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    modifier: Modifier = Modifier,
) {
    val data = checkNotNull(state.rangeData)
    val points = data.weekly.map { week ->
        TokenActivitySeriesPoint(
            startDate = week.startDate,
            endDate = week.startDate.plusDays(6),
            tokens = week.tokens,
        )
    }
    TokenActivityTimeSeriesChart(
        points = points,
        style = TokenActivitySeriesStyle.BAR,
        locale = locale,
        modifier = modifier,
        tapHint = stringResource(R.string.token_activity_chart_tap_hint),
    ) { point ->
        stringResource(
            R.string.token_activity_week_detail,
            point.startDate.format(localizedDateFormatter(locale)),
            point.endDate.format(localizedDateFormatter(locale)),
            formatTokenCount(point.tokens, tokenDisplayUnit),
        )
    }
}

@Composable
private fun TokenActivityCumulativeChart(
    state: TokenActivityUiState,
    locale: Locale,
    tokenDisplayUnit: TokenStatsDisplayUnit,
    modifier: Modifier = Modifier,
) {
    val data = checkNotNull(state.rangeData)
    val points = data.cumulative.map { day ->
        TokenActivitySeriesPoint(day.date, day.date, day.tokens)
    }
    TokenActivityTimeSeriesChart(
        points = points,
        style = TokenActivitySeriesStyle.LINE,
        locale = locale,
        modifier = modifier,
        tapHint = stringResource(R.string.token_activity_chart_tap_hint),
    ) { point ->
        stringResource(
            R.string.token_activity_cumulative_detail,
            point.startDate.format(localizedDateFormatter(locale)),
            formatTokenCount(point.tokens, tokenDisplayUnit),
        )
    }
}

@Composable
private fun TokenActivityTimeSeriesChart(
    points: List<TokenActivitySeriesPoint>,
    style: TokenActivitySeriesStyle,
    locale: Locale,
    modifier: Modifier = Modifier,
    tapHint: String,
    detailText: @Composable (TokenActivitySeriesPoint) -> String,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val pointWidth = if (style == TokenActivitySeriesStyle.BAR) 18.dp else 14.dp
    val chartWidth = (pointWidth * points.size).coerceAtLeast(280.dp)
    val plotHeight = 124.dp
    val labelHeight = 24.dp
    val canvasHeight = plotHeight + labelHeight
    val stepPx = with(density) { pointWidth.toPx() }
    val plotHeightPx = with(density) { plotHeight.toPx() }
    val maxTokens = points.maxOfOrNull(TokenActivitySeriesPoint::tokens)?.coerceAtLeast(1L) ?: 1L
    val palette = LocalTokenStatsColors.current
    val primary = palette.chartAccent
    val grid = palette.chartGrid
    val labelColor = palette.chartLabel
    val labelPaint = remember(density, labelColor) {
        Paint().apply {
            textSize = with(density) { 12.sp.toPx() }
            color = labelColor.toArgb()
            isAntiAlias = true
        }
    }
    val monthLabels = remember(points, locale) {
        val formatter = DateTimeFormatter.ofPattern("MMM", locale)
        buildList {
            var previousMonth = -1
            points.forEachIndexed { index, point ->
                if (index == 0 || point.startDate.monthValue != previousMonth) {
                    add(TokenActivityMonthLabel(index, formatter.format(point.startDate)))
                    previousMonth = point.startDate.monthValue
                }
            }
        }
    }
    var selectedPoint by remember(points, style) { mutableStateOf<TokenActivitySeriesPoint?>(null) }

    LaunchedEffect(points, style) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        scroll.scrollTo(scroll.maxValue)
    }

    Column(modifier) {
        Column(Modifier.horizontalScroll(scroll)) {
            Canvas(
                modifier = Modifier
                    .size(chartWidth, canvasHeight)
                    .pointerInput(points, style, stepPx) {
                        detectTapGestures { point ->
                            val index = (point.x / stepPx).toInt()
                            selectedPoint = points.getOrNull(index)
                        }
                    },
            ) {
                drawLine(
                    color = grid,
                    start = Offset(0f, plotHeightPx),
                    end = Offset(size.width, plotHeightPx),
                    strokeWidth = with(density) { 1.dp.toPx() },
                )
                if (style == TokenActivitySeriesStyle.BAR) {
                    points.forEachIndexed { index, point ->
                        val height = plotHeightPx * point.tokens.toFloat() / maxTokens.toFloat()
                        drawRoundRect(
                            color = primary.copy(alpha = 0.78f),
                            topLeft = Offset(index * stepPx + stepPx * 0.2f, plotHeightPx - height),
                            size = Size(stepPx * 0.6f, height),
                            cornerRadius = CornerRadius(stepPx * 0.2f),
                        )
                    }
                } else if (points.isNotEmpty()) {
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = index * stepPx + stepPx / 2f
                        val y = plotHeightPx - plotHeightPx * point.tokens.toFloat() / maxTokens.toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = primary,
                        style = Stroke(width = with(density) { 2.dp.toPx() }),
                    )
                    points.forEachIndexed { index, point ->
                        val x = index * stepPx + stepPx / 2f
                        val y = plotHeightPx - plotHeightPx * point.tokens.toFloat() / maxTokens.toFloat()
                        drawCircle(primary, radius = with(density) { 2.5.dp.toPx() }, center = Offset(x, y))
                    }
                }
                selectedPoint?.let { point ->
                    val index = points.indexOf(point)
                    if (index >= 0) {
                        drawLine(
                            color = primary,
                            start = Offset(index * stepPx + stepPx / 2f, 0f),
                            end = Offset(index * stepPx + stepPx / 2f, plotHeightPx),
                            strokeWidth = with(density) { 1.dp.toPx() },
                        )
                    }
                }
                drawIntoCanvas { canvas ->
                    val baseline = plotHeightPx + with(density) { 16.dp.toPx() }
                    monthLabels.forEach { label ->
                        canvas.nativeCanvas.drawText(label.text, label.column * stepPx, baseline, labelPaint)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text =
                    if (selectedPoint == null) {
                        tapHint
                    } else {
                        detailText(selectedPoint!!)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}

private data class TokenActivitySeriesPoint(
    val startDate: java.time.LocalDate,
    val endDate: java.time.LocalDate,
    val tokens: Long,
)

private enum class TokenActivitySeriesStyle { BAR, LINE }

private fun localizedDateFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

private enum class HeatmapDragMode { VIEW, SCROLL }

private data class TokenActivityMonthLabel(val column: Int, val text: String)

private const val HEATMAP_VIEW_SPEED_DP_PER_S = 150f
