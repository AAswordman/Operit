package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import kotlin.math.*

internal data class CardChartPoint(val label: String, val x: Double, val y: Double)
internal data class CardChartSeries(val label: String, val points: List<CardChartPoint>)

/** Donut ring thickness as a share of the outer radius. */
private const val PIE_RING = .4f

/** Runtime validation also covers values supplied through bindings. */
internal fun cardChartSeries(value: Any?, labels: List<String>, type: String, strict: Boolean = false): List<CardChartSeries> {
    require(type in setOf("line", "bar", "pie", "scatter"))
    val raw = value as? List<*> ?: error("Expected chart series")
    require(raw.size <= if (type == "pie") 1 else 4)
    fun finite(v: Any?): Double = (v as? Number)?.toDouble()?.also {
        require(it.isFinite() && abs(it) <= 1e12)
    } ?: error("Invalid chart number")
    return raw.map { item ->
        val series = item as? Map<*, *> ?: error("Invalid series")
        // 新 Express 严格校验字段；已有消息继续按原协议读取，避免破坏历史卡片。
        if (strict) {
            val fields = if (type == "scatter") setOf("label", "points") else setOf("label", "values")
            require(series.keys.all { it in fields }) { "Unknown series field; use label and ${if (type == "scatter") "points" else "values"}, not name or data" }
            require("label" !in series || series["label"] is String) { "Series label must be a string" }
        }
        val name = (series["label"] as? String).orEmpty().take(120)
        val points = if (type == "scatter") {
            val data = series["points"] as? List<*> ?: error("Missing points")
            require(data.size <= 128)
            data.mapIndexed { i, v ->
                val p = v as? Map<*, *> ?: error("Invalid point")
                if (strict) require(p.keys.all { it in setOf("x", "y", "label") } && ("label" !in p || p["label"] is String)) { "Scatter points use numeric x/y and an optional string label" }
                CardChartPoint((p["label"] as? String) ?: (i + 1).toString(), finite(p["x"]), finite(p["y"]))
            }
        } else {
            val data = series["values"] as? List<*> ?: error("Missing values")
            require(data.size <= 128 && (labels.isEmpty() || labels.size == data.size))
            data.mapIndexed { i, v ->
                val y = finite(v)
                if (type == "pie") require(y >= 0)
                CardChartPoint(labels.getOrNull(i) ?: (i + 1).toString(), i.toDouble(), y)
            }
        }
        CardChartSeries(name, points)
    }.also { data ->
        if (type != "scatter" && data.isNotEmpty()) require(data.all { it.points.size == data.first().points.size })
    }
}

/**
 * Series colors: the theme primary first, then fixed hues that stay distinct from it and from
 * each other. Theme secondary/tertiary are often the same family as primary, so they are not used.
 */
internal fun cardChartPalette(primary: Color, dark: Boolean): List<Color> {
    // Light colors double as legend and tooltip text, so each keeps >= 4:1 contrast on white.
    val fixed = if (dark) listOf(0xFF8AB4F0, 0xFF6FD0C2, 0xFFF0C46A, 0xFFB9A2EC, 0xFFF29E7C, 0xFFA9B6C6)
        else listOf(0xFF3D7CC9, 0xFF22857A, 0xFF96690F, 0xFF8466C4, 0xFFB85A32, 0xFF6E7F94)
    fun hueSat(c: Color): Pair<Float, Float> {
        val max = maxOf(c.red, c.green, c.blue); val min = minOf(c.red, c.green, c.blue); val d = max - min
        if (d < 1e-4f) return 0f to 0f
        val h = when (max) {
            c.red -> 60f * (((c.green - c.blue) / d) % 6f)
            c.green -> 60f * ((c.blue - c.red) / d + 2f)
            else -> 60f * ((c.red - c.green) / d + 4f)
        }
        return ((h + 360f) % 360f) to (if (max <= 0f) 0f else d / max)
    }
    val (primaryHue, primarySat) = hueSat(primary)
    val colors = fixed.map { Color(it) }
    fun rgbDistance(a: Color, b: Color) = (a.red - b.red).pow(2) + (a.green - b.green).pow(2) + (a.blue - b.blue).pow(2)
    // A near-gray primary has no reliable hue: drop only the fixed color that looks closest to it.
    val closest = if (primarySat < .2f) colors.minBy { rgbDistance(it, primary) } else null
    val distinct = colors.filter { color ->
        if (color == closest) return@filter false
        val (hue, sat) = hueSat(color)
        val distance = abs(hue - primaryHue).let { minOf(it, 360f - it) }
        primarySat < .2f || sat < .2f || distance >= 35f
    }
    // A chart has up to 4 series: never let the filter leave fewer colors than that.
    return listOf(primary) + distinct + (colors - distinct.toSet()).take((3 - distinct.size).coerceAtLeast(0))
}

@Composable
internal fun CardChart(type: String, title: String, data: List<CardChartSeries>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val palette = remember(primary, dark) { cardChartPalette(primary, dark) }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    // Insets are translucent in embedded blocks; the floating tooltip must stay opaque.
    val tooltipBackground = MaterialTheme.colorScheme.surfaceContainerHigh.compositeOver(MaterialTheme.colorScheme.surface)
    val tooltipInk = MaterialTheme.colorScheme.onSurface
    val markerBackground = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var selected by remember(data, type) { mutableStateOf<Pair<Int, Int>?>(null) }
    val points = remember(data) { data.flatMap { it.points } }
    val pieColors = remember(points.size, palette, dark) {
        List(points.size) { i -> palette.getOrNull(i) ?: Color.hsv((i * 137.508f) % 360f, .45f, if (dark) .85f else .72f) }
    }
    val empty = points.isEmpty() || (type == "pie" && points.sumOf { it.y } <= 0)
    // Screen readers hear the values here; sighted users inspect by tapping, so no data table is shown.
    // The title is its own text node, and very dense charts are capped so focus does not read for minutes.
    val resources = LocalResources.current
    val chartValues = remember(data, type, resources) {
        // Split the budget so every series keeps some values, and say how many were left out.
        val perSeries = 64 / data.size.coerceAtLeast(1)
        data.joinToString("; ") { series ->
            val shown = series.points.take(perSeries)
            val values = (shown.map { p ->
                "${p.label} ${cardChartNumber(p.y)}" + if (type == "scatter") " (x ${cardChartNumber(p.x)})" else ""
            } + listOfNotNull((series.points.size - shown.size).takeIf { it > 0 }?.let { resources.getString(R.string.a2ui_chart_more, it) }))
                .joinToString(", ")
            if (series.label.isBlank()) values else "${series.label}: $values"
        }
    }
    val chartDescription = stringResource(R.string.a2ui_chart_description, points.size, chartValues)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
        if (empty) Text(stringResource(R.string.a2ui_no_data), style = MaterialTheme.typography.bodyMedium)
        else {
            val xAxis = remember(data, type) { if (type == "scatter")
                cardChartAxis(points.minOf { it.x }, points.maxOf { it.x }, false) else null }
            val minX = xAxis?.min ?: points.minOf { it.x }
            val maxX = xAxis?.max ?: points.maxOf { it.x }.let { if (it == minX) it + 1 else it }
            val axis = remember(data, type) { cardChartAxis(points.minOf { it.y }, points.maxOf { it.y }, type == "bar") }
            val minY = axis.min
            val maxY = axis.max
            val ticks = axis.ticks.asReversed()
            val tickStyle = TextStyle(color = ink, fontSize = 11.sp)
            val tickLabels = cardChartTickLabels(ticks).map { measurer.measure(it, tickStyle) }
            val left = tickLabels.maxOf { it.size.width }.toFloat() + with(density) { 8.dp.toPx() }
            val right = with(density) { 16.dp.toPx() }
            val top = with(density) { 12.dp.toPx() }
            val axisLabelStyle = TextStyle(color = ink, fontSize = 10.sp, textAlign = TextAlign.Center)
            // CJK fallback fonts are taller than Latin, so size the band from a CJK sample.
            val axisLineHeight = measurer.measure("Ag图", axisLabelStyle).size.height
            // Bar categories get two label lines so long names wrap instead of being dropped.
            val bottom = with(density) { if (type == "bar") maxOf(28.dp.toPx(), axisLineHeight * 2 + 8.dp.toPx()) else 28.dp.toPx() }
            fun plotWidth(w: Float) = (w - left - right).coerceAtLeast(1f)
            fun barWidth(w: Float) = plotWidth(w) / data.first().points.size.coerceAtLeast(1) * .7f / data.size
            fun position(p: CardChartPoint, w: Float, h: Float, seriesIndex: Int = 0): Offset {
                val x = if (type == "bar") {
                    val slot = plotWidth(w) / data.first().points.size.coerceAtLeast(1)
                    left + slot * (p.x.toFloat() + .15f) + (seriesIndex + .5f) * barWidth(w)
                } else if (type == "line" && data.first().points.size == 1) left + plotWidth(w) / 2
                else left + ((p.x - minX) / (maxX - minX)).toFloat() * plotWidth(w)
                return Offset(x, top + ((maxY - p.y) / (maxY - minY)).toFloat() * (h - top - bottom).coerceAtLeast(1f))
            }
            fun selectAt(tap: Offset, w: Float, h: Float) {
                if (type == "pie") {
                    val center = Offset(w / 2f, h / 2f)
                    val outer = minOf(w, h) * .43f
                    val distance = (tap - center).getDistance()
                    // The donut hole clears the selection; the ring and a small rim select a slice.
                    if (distance < outer * (1 - PIE_RING)) selected = null
                    else if (distance <= outer + with(density) { 8.dp.toPx() }) {
                        val angle = ((atan2(tap.y - center.y, tap.x - center.x) * 180 / PI + 90 + 360) % 360).toFloat()
                        val total = points.sumOf { it.y }; var end = 0f
                        val index = points.indexOfFirst { end += (it.y / total * 360).toFloat(); angle < end }
                        if (index >= 0) selected = 0 to index
                    }
                } else if (type == "line") {
                    selected = 0 to cardChartLineIndex(tap.x, left, plotWidth(w), data.first().points.size)
                } else if (type == "bar") {
                    selected = cardChartBarSelection(tap.x, left, plotWidth(w), data.first().points.size, data.size)
                } else {
                    selected = data.flatMapIndexed { s, series -> series.points.mapIndexed { i, p ->
                        val pos = position(p, w, h, s)
                        Triple(s, i, (pos - tap).getDistance())
                    } }.minByOrNull { it.third }?.let { it.first to it.second }
                }
            }
            Canvas(Modifier.fillMaxWidth().height(210.dp).semantics { contentDescription = chartDescription }
                .pointerInput(data, type, left, top, bottom, right) {
                    detectTapGestures { tap -> selectAt(tap, size.width.toFloat(), size.height.toFloat()) }
                }
                .pointerInput(data, type, left, top, bottom, right) {
                    // Consume only after the long press wins. Child consumption stops the
                    // phone layout's draggable sidebar while normal chat scrolling stays available.
                    detectDragGesturesAfterLongPress(
                        onDragStart = { point ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectAt(point, size.width.toFloat(), size.height.toFloat())
                        },
                    ) { change, _ ->
                        change.consume()
                        selectAt(change.position, size.width.toFloat(), size.height.toFloat())
                    }
                }) {
                val w = size.width; val h = size.height
                if (type == "pie") {
                    val center = Offset(w / 2f, h / 2f)
                    val outer = min(w, h) * .43f
                    val thickness = outer * PIE_RING
                    val ring = outer - thickness / 2
                    // Transparent gaps let the chat background show through, so no fill color is assumed.
                    val gap = if (points.count { it.y > 0 } > 1) 2.dp.toPx() / ring * (180f / PI.toFloat()) else 0f
                    val total = points.sumOf { it.y }; var start = -90f
                    val active = selected?.second
                    points.forEachIndexed { i, p ->
                        val sweep = (p.y / total * 360).toFloat()
                        val trim = if (sweep > gap * 2) gap else 0f
                        val chosen = active == i
                        val radius = if (chosen) ring + 3.dp.toPx() else ring
                        val color = if (active == null || chosen) pieColors[i] else pieColors[i].copy(alpha = .3f)
                        if (sweep > 0f) drawArc(color, start + trim / 2, sweep - trim, false, center - Offset(radius, radius),
                            Size(radius * 2, radius * 2), style = Stroke(if (chosen) thickness + 6.dp.toPx() else thickness))
                        start += sweep
                    }
                    active?.let { points.getOrNull(it) }?.let { p ->
                        val hole = ((outer - thickness) * 1.6f).toInt().coerceAtLeast(1)
                        // Shrink the share like Display values instead of eliding digits.
                        val shareText = "${cardChartPercent(p.y, total)}%"
                        fun shareAt(size: Float) = measurer.measure(shareText,
                            TextStyle(color = tooltipInk, fontSize = size.sp, fontWeight = FontWeight.SemiBold), softWrap = false, maxLines = 1)
                        var shareSize = 22f
                        var share = shareAt(shareSize)
                        while (share.size.width > hole && shareSize > 13f) { shareSize -= 1f; share = shareAt(shareSize) }
                        val name = measurer.measure(p.label, TextStyle(color = ink, fontSize = 12.sp, textAlign = TextAlign.Center),
                            overflow = TextOverflow.Ellipsis, maxLines = 2, constraints = Constraints(maxWidth = hole))
                        // Large font scales drop the name, then the share; the highlighted legend still shows both.
                        if (share.size.width <= hole) {
                            val both = share.size.height + name.size.height <= hole
                            val textHeight = share.size.height + if (both) name.size.height else 0
                            val textTop = center.y - textHeight / 2f
                            drawText(share, topLeft = Offset(center.x - share.size.width / 2f, textTop))
                            if (both) drawText(name, topLeft = Offset(center.x - name.size.width / 2f, textTop + share.size.height))
                        }
                    }
                } else {
                    ticks.forEachIndexed { i, y ->
                        val pos = position(CardChartPoint("", minX, y), w, h)
                        drawLine(grid, Offset(left, pos.y), Offset(w - right, pos.y), 1f)
                        drawText(tickLabels[i], topLeft = Offset(left - 8.dp.toPx() - tickLabels[i].size.width, pos.y - tickLabels[i].size.height / 2))
                    }
                    val zeroY = position(CardChartPoint("", minX, 0.0), w, h).y
                    selected?.let { (s, i) ->
                        data.getOrNull(s)?.points?.getOrNull(i)?.let { p ->
                            val pos = position(p, w, h, s)
                            if (type == "bar") {
                                drawRect(palette[s % palette.size].copy(alpha = .1f),
                                    Offset(pos.x - barWidth(w) / 2, top), Size(barWidth(w), (h - top - bottom).coerceAtLeast(1f)))
                            } else drawLine(ink.copy(alpha = .4f), Offset(pos.x, top), Offset(pos.x, h - bottom), 1.dp.toPx())
                        }
                    }
                    data.forEachIndexed { s, series ->
                        val color = palette[s % palette.size]
                        val path = Path()
                        series.points.forEachIndexed { i, p ->
                            val pos = position(p, w, h, s)
                            when (type) {
                                "bar" -> {
                                    val width = barWidth(w).coerceAtLeast(1f)
                                    // Bars occupy full category slots; no half-bar clipping at the ends.
                                    val x = pos.x - width / 2
                                    drawRect(color, Offset(x, minOf(pos.y, zeroY)), Size(width, abs(zeroY - pos.y).coerceAtLeast(1f)))
                                }
                                "scatter" -> drawCircle(color, 4.dp.toPx(), pos)
                                else -> {
                                    if (i == 0) path.moveTo(pos.x, pos.y) else path.lineTo(pos.x, pos.y)
                                    if (series.points.size == 1) drawCircle(color, 4.dp.toPx(), pos)
                                }
                            }
                        }
                        if (type == "line") drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        selected?.takeIf { type == "line" || it.first == s }?.second?.let { i ->
                            series.points.getOrNull(i)?.let {
                                val pos = position(it, w, h, s)
                                drawCircle(markerBackground, 6.dp.toPx(), pos)
                                drawCircle(color, 4.dp.toPx(), pos)
                            }
                        }
                    }
                    val labels = xAxis?.let { axis ->
                        cardChartTickLabels(axis.ticks).zip(axis.ticks).map { (label, x) -> CardChartPoint(label, x, 0.0) }
                    } ?: data.first().points
                    val slot = plotWidth(w) / labels.size.coerceAtLeast(1)
                    val slotText = (slot - 4.dp.toPx()).toInt()
                    // Bar categories each own a slot: label every bar inside it, wrapping to two lines.
                    // Only slots too narrow for readable text fall back to thinning below.
                    val everyLabel = type == "bar" && slotText >= 28.dp.toPx()
                    if (everyLabel) labels.forEachIndexed { i, p ->
                        val layout = measurer.measure(p.label, axisLabelStyle, overflow = TextOverflow.Ellipsis,
                            maxLines = 2, constraints = Constraints(maxWidth = slotText))
                        val x = left + slot * (i + .5f)
                        drawText(layout, topLeft = Offset(x - layout.size.width / 2, h - bottom + 4.dp.toPx()))
                    }
                    val step = max(1, ceil(labels.size / 4.0).toInt())
                    // Keep numeric labels intact. Prioritize endpoints and omit ticks that
                    // would overlap instead of turning distinct large numbers into identical prefixes.
                    val labelIndices = if (everyLabel) emptyList<Int>() else (listOf(0, labels.lastIndex) +
                        labels.indices.filter { it % step == 0 }).distinct().filter { it in labels.indices }
                    val occupied = mutableListOf<ClosedFloatingPointRange<Float>>()
                    labelIndices.forEach { i ->
                        val p = labels[i]
                        val x = if (type == "bar") left + plotWidth(w) / labels.size * (i + .5f) else position(p, w, h).x
                        val layout = measurer.measure(if (xAxis != null) p.label else p.label.take(8),
                            TextStyle(color = ink, fontSize = 10.sp))
                        val labelX = (x - layout.size.width / 2).coerceIn(0f, (w - layout.size.width).coerceAtLeast(0f))
                        val labelEnd = labelX + layout.size.width
                        if (labelEnd <= w && occupied.none { labelX < it.endInclusive + 8.dp.toPx() && labelEnd + 8.dp.toPx() > it.start }) {
                            drawText(layout, topLeft = Offset(labelX, h - bottom + 4.dp.toPx()))
                            occupied += labelX..labelEnd
                        }
                    }
                    // Drawn in the same Canvas: the readout cannot steal pointer events.
                    selected?.let { (s, i) -> data.getOrNull(s)?.points?.getOrNull(i)?.let tooltip@{ point ->
                        val rows = if (type == "line") data.mapIndexedNotNull { index, series ->
                            series.points.getOrNull(i)?.let { "${series.label}: ${cardChartNumber(it.y)}" to palette[index % palette.size] }
                        } else listOf("${data[s].label}: ${cardChartNumber(point.y)}" to palette[s % palette.size]) +
                            if (type == "scatter") listOf("x: ${cardChartNumber(point.x)}" to tooltipInk) else emptyList()
                        val padding = 10.dp.toPx()
                        val maxTextWidth = (minOf(220.dp.toPx(), w) - padding * 2).toInt().coerceAtLeast(1)
                        val textRows = (listOf(point.label to tooltipInk) + rows).map { (text, color) ->
                            measurer.measure(text, TextStyle(color = color, fontSize = 12.sp),
                                overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxTextWidth))
                        }
                        val gap = 3.dp.toPx()
                        val boxWidth = textRows.maxOf { it.size.width } + padding * 2
                        val boxHeight = textRows.sumOf { it.size.height } + gap * (textRows.size - 1) + padding * 2
                        // Large accessibility fonts can make the tooltip taller than the plot.
                        // The complete, scalable readout below the chart remains available.
                        if (boxHeight > h - bottom || boxWidth > w) return@tooltip
                        val pos = position(point, w, h, s)
                        val boxX = (if (pos.x + 12.dp.toPx() + boxWidth <= w) pos.x + 12.dp.toPx()
                            else pos.x - 12.dp.toPx() - boxWidth).coerceIn(0f, (w - boxWidth).coerceAtLeast(0f))
                        val boxY = (pos.y - boxHeight - 12.dp.toPx()).coerceIn(0f, (h - bottom - boxHeight).coerceAtLeast(0f))
                        drawRoundRect(tooltipBackground, Offset(boxX, boxY), Size(boxWidth, boxHeight),
                            androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                        drawRoundRect(grid, Offset(boxX, boxY), Size(boxWidth, boxHeight),
                            androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
                        var textY = boxY + padding
                        textRows.forEach { row ->
                            drawText(row, topLeft = Offset(boxX + padding, textY))
                            textY += row.size.height + gap
                        }
                    } }
                }
            }
            Text(stringResource(R.string.a2ui_chart_gesture), style = MaterialTheme.typography.labelSmall, color = ink)
            if (type == "pie") Column(Modifier.heightIn(max = 240.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val total = points.sumOf { it.y }
                // Values that already sum to 100 are percentages; repeating them would be noise.
                val showValue = abs(total - 100) > 1e-9
                points.indices.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { i ->
                            val point = points[i]
                            val chosen = selected?.second == i
                            Row(Modifier.weight(1f).height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp))
                                .background(if (chosen) pieColors[i].copy(alpha = .12f) else Color.Transparent)
                                .selectable(selected = chosen) { selected = if (chosen) null else 0 to i }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(pieColors[i]))
                                Column {
                                    Text(point.label, style = MaterialTheme.typography.labelMedium, color = ink,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("${cardChartPercent(point.y, total)}%", style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold)
                                        if (showValue) Text(cardChartNumber(point.y), style = MaterialTheme.typography.labelSmall,
                                            color = ink, modifier = Modifier.padding(bottom = 2.dp))
                                    }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else if (data.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Series colors identify, the shared ink reads: a colored label can vanish on a custom bubble.
                data.forEachIndexed { i, series -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("●", color = palette[i % palette.size], style = MaterialTheme.typography.labelMedium)
                    Text(series.label, color = ink, style = MaterialTheme.typography.labelMedium)
                } }
            }
            // The donut center and highlighted legend already show the selected slice.
            selected?.takeIf { type != "pie" }?.let { (s, i) -> data.getOrNull(s)?.points?.getOrNull(i)?.let { p ->
                if (type == "line") Column {
                    Text(p.label, style = MaterialTheme.typography.labelLarge)
                    data.forEachIndexed { index, series -> series.points.getOrNull(i)?.let {
                        Text("${series.label}: ${cardChartNumber(it.y)}", color = palette[index % palette.size], style = MaterialTheme.typography.bodyMedium)
                    } }
                } else Text("${data[s].label} ${p.label}: ${cardChartNumber(p.y)}" +
                    if (type == "scatter") " (x=${cardChartNumber(p.x)})" else "", style = MaterialTheme.typography.labelLarge)
            } }
        }
    }
}
