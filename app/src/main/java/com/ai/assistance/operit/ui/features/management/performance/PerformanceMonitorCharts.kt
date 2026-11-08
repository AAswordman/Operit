package com.ai.assistance.operit.ui.features.management.performance

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.performance.PerformanceSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/** 性能曲线的一个数据系列；value 返回 null 表示该时刻无数据（断线）。 */
internal data class PerformanceChartSeries(
    val label: String,
    val color: Color,
    val value: (PerformanceSnapshot) -> Double?
)

/**
 * 任务管理器风格的滚动性能曲线：多系列折线 + 半透明填充 + 网格与时间轴。
 * 数据从左（旧）到右（新）铺满整个宽度。
 */
@Composable
internal fun PerformanceLiveChart(
    history: List<PerformanceSnapshot>,
    series: List<PerformanceChartSeries>,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    fixedMax: Double? = null,
    chartHeight: Dp = 168.dp
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        PerformanceChartLegend(series = series)
        Spacer(modifier = Modifier.height(8.dp))
        if (history.size < 2) {
            Box(modifier = Modifier.fillMaxWidth().height(chartHeight)) {}
        } else {
            PerformanceChartCanvas(
                history = history,
                series = series,
                fixedMax = fixedMax,
                gridColor = gridColor,
                labelColor = labelColor,
                valueFormatter = valueFormatter,
                chartHeight = chartHeight
            )
        }
    }
}

@Composable
private fun PerformanceChartLegend(series: List<PerformanceChartSeries>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        series.forEachIndexed { index, entry ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Box(
                modifier =
                    Modifier.size(8.dp).clip(CircleShape).background(entry.color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PerformanceChartCanvas(
    history: List<PerformanceSnapshot>,
    series: List<PerformanceChartSeries>,
    fixedMax: Double?,
    gridColor: Color,
    labelColor: Color,
    valueFormatter: (Double) -> String,
    chartHeight: Dp
) {
    val density = LocalDensity.current
    val labelPx = with(density) { 10.dp.toPx() }
    val topPadPx = with(density) { 4.dp.toPx() }
    val bottomPadPx = with(density) { 18.dp.toPx() }

    val yMax = fixedMax ?: remember(series, history) { dynamicNiceMax(history, series) }
    val yLabels =
        remember(yMax, valueFormatter) {
            listOf(yMax, yMax / 2.0, 0.0).map { valueFormatter(it) }
        }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeLabels =
        remember(history, timeFormat) {
            val n = history.size
            listOf(0, (n - 1) / 2, n - 1).map { index ->
                timeFormat.format(Date(history[index].timestampMs))
            }
        }

    Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
        val chartBottom = size.height - bottomPadPx
        val chartTop = topPadPx
        val chartH = chartBottom - chartTop
        val width = size.width
        val n = history.size

        fun xAt(index: Int): Float = if (n <= 1) 0f else index.toFloat() / (n - 1) * width
        fun yAt(value: Double): Float =
            (chartBottom - (value / yMax * chartH).toFloat()).coerceIn(chartTop, chartBottom)

        // 横向网格：1/4、1/2、3/4
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            val y = chartBottom - chartH * fraction
            drawLine(
                color = gridColor.copy(alpha = 0.6f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // 纵向网格：两端与中点（与时间标签对齐）
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val x = width * fraction
            drawLine(
                color = gridColor.copy(alpha = 0.6f),
                start = Offset(x, chartTop),
                end = Offset(x, chartBottom),
                strokeWidth = 1f
            )
        }

        val textPaint =
            Paint().apply {
                color = labelColor.toArgb()
                textSize = labelPx
                isAntiAlias = true
            }

        // y 轴标签（左侧，靠上/中/下对齐）
        drawContext.canvas.nativeCanvas.drawText(yLabels[0], 4f, chartTop + labelPx, textPaint)
        drawContext.canvas.nativeCanvas.drawText(yLabels[1], 4f, (chartTop + chartBottom) / 2f + labelPx / 3f, textPaint)
        drawContext.canvas.nativeCanvas.drawText(yLabels[2], 4f, chartBottom - 2f, textPaint)

        // x 轴时间标签（左对齐 / 居中 / 右对齐）
        drawContext.canvas.nativeCanvas.drawText(timeLabels[0], 4f, size.height - 4f, textPaint)
        val centerWidth = textPaint.measureText(timeLabels[1])
        drawContext.canvas.nativeCanvas.drawText(timeLabels[1], width / 2f - centerWidth / 2f, size.height - 4f, textPaint)
        val rightWidth = textPaint.measureText(timeLabels[2])
        drawContext.canvas.nativeCanvas.drawText(timeLabels[2], width - rightWidth - 4f, size.height - 4f, textPaint)

        // 数据系列：分段折线 + 填充 + 末端圆点
        series.forEach { entry ->
            var segment = mutableListOf<Offset>()
            val segments = mutableListOf<List<Offset>>()
            history.forEachIndexed { index, snapshot ->
                val value = entry.value(snapshot)
                if (value == null) {
                    if (segment.size > 1) {
                        segments.add(segment)
                    }
                    segment = mutableListOf()
                } else {
                    segment.add(Offset(xAt(index), yAt(value)))
                }
            }
            if (segment.size > 1) {
                segments.add(segment)
            }

            segments.forEach { points ->
                if (points.size < 2) {
                    return@forEach
                }
                val linePath = Path().apply { moveTo(points.first().x, points.first().y) }
                points.forEach { point -> linePath.lineTo(point.x, point.y) }
                val fillPath =
                    Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, chartBottom)
                        lineTo(points.first().x, chartBottom)
                        close()
                    }
                drawPath(fillPath, color = entry.color.copy(alpha = 0.12f))
                drawPath(linePath, color = entry.color, style = Stroke(width = 4f))
                drawCircle(
                    color = entry.color,
                    radius = 5f,
                    center = points.last()
                )
            }
        }
    }
}

/** 自适应量程：取全部系列最大值向上取整到 2/5/10×10^k，保证曲线不贴顶。 */
private fun dynamicNiceMax(
    history: List<PerformanceSnapshot>,
    series: List<PerformanceChartSeries>
): Double {
    val max = series.maxOfOrNull { entry -> history.maxOfOrNull { entry.value(it) ?: 0.0 } ?: 0.0 } ?: 0.0
    if (max <= 0.0) {
        return 1.0
    }
    val exponent = ceil(log10(max)).toInt()
    val base = 10.0.pow(exponent)
    val candidates = listOf(base * 0.2, base * 0.5, base)
    return candidates.firstOrNull { it >= max } ?: base
}
