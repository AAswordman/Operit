package com.ai.assistance.operit.ui.components.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** 原生滑块负责触摸、步进与无障碍；轨道统一为细线和圆形滑块，不绘制刻度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkbenchSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    // 九宫格切片可固定为单一值，零长度区间按零比例绘制，避免 0/0 产生 NaN。
    val length = valueRange.endInclusive - valueRange.start
    val fraction = if (length == 0f) 0f else (value - valueRange.start) / length
    Slider(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        // 透传交互源，背景编辑可继续监听拖动开始、结束与取消。
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        thumb = { WorkbenchSliderThumb(enabled) },
        track = {
            WorkbenchSliderTrack(
                0f,
                fraction,
                enabled,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkbenchRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val length = valueRange.endInclusive - valueRange.start
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.fillMaxWidth(),
        startThumb = { WorkbenchSliderThumb(enabled) },
        endThumb = { WorkbenchSliderThumb(enabled) },
        track = {
            WorkbenchSliderTrack(
                (value.start - valueRange.start) / length,
                (value.endInclusive - valueRange.start) / length,
                enabled,
            )
        },
    )
}

@Composable
private fun WorkbenchSliderThumb(enabled: Boolean) {
    val accent =
        if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Box(
        Modifier.size(20.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, accent.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(11.dp).background(accent, CircleShape))
    }
}

@Composable
private fun WorkbenchSliderTrack(start: Float, end: Float, enabled: Boolean) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.65f else 0.3f)
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.3f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val y = size.height / 2f
        fun x(fraction: Float) =
            size.width * (if (rtl) 1f - fraction.coerceIn(0f, 1f) else fraction.coerceIn(0f, 1f))
        drawLine(track, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), StrokeCap.Round)
        if (end > start)
            drawLine(accent, Offset(x(start), y), Offset(x(end), y), 4.dp.toPx(), StrokeCap.Round)
    }
}
