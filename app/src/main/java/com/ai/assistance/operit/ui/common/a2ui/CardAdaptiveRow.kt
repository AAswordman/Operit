package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/** Null means stack. Only automatic items have already been measured. */
internal fun cardRowWidths(
    width: Int,
    gap: Int,
    weights: List<Int>,
    automaticWidths: List<Int>,
    minimum: Int,
): List<Int>? {
    val remaining = width - gap * (weights.size - 1).coerceAtLeast(0) -
        weights.indices.sumOf { if (weights[it] == 0) automaticWidths[it] else 0 }
    val totalWeight = weights.sum()
    val widths = weights.mapIndexed { i, weight ->
        if (weight == 0) automaticWidths[i]
        else (remaining.coerceAtLeast(0).toLong() * weight / totalWeight).toInt()
    }.toMutableList()
    if (remaining < 0 || weights.indices.any { weights[it] > 0 && widths[it] < minimum }) return null
    val last = weights.indexOfLast { it > 0 }
    if (last >= 0) widths[last] += remaining.coerceAtLeast(0) -
        weights.indices.sumOf { if (weights[it] > 0) widths[it] else 0 }
    return widths
}

/**
 * Width changes only affect measurement, never the composition tree or local input state.
 * Automatic items are measured once at natural width; flexible items are measured once
 * with the final width, after deciding whether this row needs to stack.
 */
@Composable
internal fun CardAdaptiveRow(
    weights: List<Int>,
    gap: Dp,
    minimum: Dp,
    arrangement: Arrangement.Horizontal,
    vertical: Alignment.Vertical,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val spacing = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val places = arrayOfNulls<Placeable>(measurables.size)
        val natural = MutableList(measurables.size) { 0 }
        measurables.forEachIndexed { i, measurable ->
            if (weights[i] == 0 || !constraints.hasBoundedWidth) {
                places[i] = measurable.measure(loose)
                natural[i] = places[i]!!.width
            }
        }
        val widths = if (constraints.hasBoundedWidth) cardRowWidths(
            constraints.maxWidth, spacing, weights, natural,
            (minimum.toPx() * fontScale.coerceAtLeast(1f)).toInt(),
        ) else natural
        measurables.forEachIndexed { i, measurable ->
            if (places[i] == null) {
                val width = widths?.get(i) ?: constraints.maxWidth
                places[i] = measurable.measure(Constraints(
                    minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight,
                ))
            }
        }
        val measured = places.map { requireNotNull(it) }
        val stacked = widths == null
        val width = constraints.constrainWidth(
            if (constraints.hasBoundedWidth && (stacked || weights.any { it > 0 })) constraints.maxWidth
            else measured.sumOf { it.width } + spacing * (measured.size - 1).coerceAtLeast(0)
        )
        val height = constraints.constrainHeight(
            if (stacked) measured.sumOf { it.height } + spacing * (measured.size - 1).coerceAtLeast(0)
            else measured.maxOfOrNull { it.height } ?: 0
        )
        val positions = IntArray(measured.size)
        if (!stacked) with(arrangement) {
            arrange(width, measured.map { it.width }.toIntArray(), layoutDirection, positions)
        }
        layout(width, height) {
            var y = 0
            measured.forEachIndexed { i, placeable ->
                if (stacked) {
                    placeable.placeRelative(0, y)
                    y += placeable.height + spacing
                } else placeable.place(positions[i], vertical.align(placeable.height, height))
            }
        }
    }
}
