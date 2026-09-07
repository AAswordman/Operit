package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityControl
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityMapping
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityOption
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThinkingQualitySlider(
    label: String,
    mapping: ThinkingQualityMapping,
    value: String,
    onValueChange: (String) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = mapping.options
    val selectedIndex = options.indexOfFirst { it.id == value }
    val maxIndex = (options.size - 1).coerceAtLeast(1)
    var sliderPosition by remember(options) { mutableFloatStateOf(selectedIndex.toFloat()) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()

    LaunchedEffect(selectedIndex) {
        sliderPosition = selectedIndex.toFloat()
    }

    if (mapping.control != ThinkingQualityControl.LEVELS || options.isEmpty() || selectedIndex < 0) {
        return
    }

    val currentIndex = sliderPosition.roundToInt().coerceIn(0, options.lastIndex)
    val selectedOption = options[currentIndex]
    val primary = MaterialTheme.colorScheme.primary
    val highlightColor = lerp(primary, MaterialTheme.colorScheme.onSurface, 0.62f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val shimmerPosition = remember { Animatable(-0.24f) }

    LaunchedEffect(isDragging) {
        if (!isDragging) {
            shimmerPosition.snapTo(-0.24f)
            return@LaunchedEffect
        }

        while (isActive) {
            shimmerPosition.snapTo(-0.24f)
            shimmerPosition.animateTo(
                targetValue = 1.24f,
                animationSpec = tween(durationMillis = 900, easing = LinearEasing),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = selectedOption.displayLabel,
                color = primary,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                sliderPosition = newValue
            },
            onValueChangeFinished = {
                onValueChange(options[sliderPosition.roundToInt().coerceIn(0, options.lastIndex)].id)
            },
            valueRange = 0f..maxIndex.toFloat(),
            steps = (options.size - 2).coerceAtLeast(0),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            thumb = {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(20.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(highlightColor, CircleShape),
                    )
                }
            },
            track = {
                ThinkingQualityTrack(
                    progress = sliderPosition / maxIndex.toFloat(),
                    selectedIndex = sliderPosition.roundToInt().coerceIn(0, options.lastIndex),
                    stopCount = options.size,
                    shimmerPosition = shimmerPosition.value,
                    showShimmer = isDragging,
                    primary = primary,
                    highlightColor = highlightColor,
                    trackColor = trackColor,
                    outlineColor = outlineColor,
                )
            },
        )

        ThinkingQualityLabels(
            options = options,
            selectedIndex = currentIndex,
            primary = primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ThinkingQualityLabels(
    options: List<ThinkingQualityOption>,
    selectedIndex: Int,
    primary: Color,
    modifier: Modifier = Modifier,
) {
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(
        content = {
            options.forEachIndexed { index, option ->
                Text(
                    text = option.displayLabel,
                    color = if (index == selectedIndex) primary else inactiveColor,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val edgeInset = 10.dp.roundToPx()
        val usableWidth = (constraints.maxWidth - edgeInset * 2).coerceAtLeast(0)
        val anchors = IntArray(options.size) { index ->
            if (options.size > 1) {
                edgeInset + usableWidth * index / (options.size - 1)
            } else {
                edgeInset
            }
        }
        val cellStarts = IntArray(options.size) { index ->
            if (index == 0) 0 else (anchors[index - 1] + anchors[index]) / 2
        }
        val cellEnds = IntArray(options.size) { index ->
            if (index == options.lastIndex) constraints.maxWidth else (anchors[index] + anchors[index + 1]) / 2
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(
                constraints.copy(
                    minWidth = 0,
                    maxWidth = (cellEnds[index] - cellStarts[index]).coerceAtLeast(1),
                    minHeight = 0,
                ),
            )
        }
        val layoutHeight = placeables.maxOfOrNull { it.height } ?: 0

        layout(constraints.maxWidth, layoutHeight) {
            placeables.forEachIndexed { index, placeable ->
                val minX = cellStarts[index]
                val maxX = (cellEnds[index] - placeable.width).coerceAtLeast(minX)
                val x = (anchors[index] - placeable.width / 2).coerceIn(minX, maxX)
                placeable.placeRelative(x, 0)
            }
        }
    }
}

@Composable
private fun ThinkingQualityTrack(
    progress: Float,
    selectedIndex: Int,
    stopCount: Int,
    shimmerPosition: Float,
    showShimmer: Boolean,
    primary: Color,
    highlightColor: Color,
    trackColor: Color,
    outlineColor: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .graphicsLayer { clip = false },
    ) {
        drawThinkingQualityTrack(
            progress = progress,
            selectedIndex = selectedIndex,
            stopCount = stopCount,
            shimmerPosition = shimmerPosition,
            showShimmer = showShimmer,
            primary = primary,
            highlightColor = highlightColor,
            trackColor = trackColor,
            outlineColor = outlineColor,
        )
    }
}

private fun DrawScope.drawThinkingQualityTrack(
    progress: Float,
    selectedIndex: Int,
    stopCount: Int,
    shimmerPosition: Float,
    showShimmer: Boolean,
    primary: Color,
    highlightColor: Color,
    trackColor: Color,
    outlineColor: Color,
) {
    val frameHeight = 20.dp.toPx()
    val trackHeight = 16.dp.toPx()
    val frameStrokeWidth = 1.dp.toPx()
    val frameRadius = frameHeight / 2f
    val trackRadius = trackHeight / 2f
    val valueStart = 0f
    val valueEnd = size.width
    val valueWidth = (valueEnd - valueStart).coerceAtLeast(0f)
    val frameStart = valueStart - frameRadius
    val frameEnd = valueEnd + frameRadius
    val activeStart = valueStart - trackRadius
    val centerY = size.height / 2f
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val activeCenter = if (isRtl) {
        valueEnd - valueWidth * progress.coerceIn(0f, 1f)
    } else {
        valueStart + valueWidth * progress.coerceIn(0f, 1f)
    }
    val filledStart = if (isRtl) activeCenter - trackRadius else activeStart
    val filledEnd = if (isRtl) valueEnd + trackRadius else activeCenter + trackRadius

    // The inactive side remains transparent; reveal the theme gradient only up to the thumb.
    if (filledEnd > filledStart) {
        val gradientColors = listOf(
            lerp(trackColor, primary, 0.18f),
            lerp(trackColor, primary, 0.56f),
            lerp(trackColor, primary, 0.84f),
        ).let { if (isRtl) it.reversed() else it }
        val activeTrackBrush = Brush.horizontalGradient(
            colors = gradientColors,
            startX = filledStart,
            endX = filledEnd,
        )
        drawRoundRect(
            brush = activeTrackBrush,
            topLeft = Offset(filledStart, centerY - trackHeight / 2f),
            size = Size(filledEnd - filledStart, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius, trackRadius),
        )
    }

    if (showShimmer && filledEnd > filledStart) {
        val shimmerWidth = 26.dp.toPx()
        val shimmerCenter = if (isRtl) {
            valueEnd - valueWidth * shimmerPosition
        } else {
            valueStart + valueWidth * shimmerPosition
        }
        val shimmerStart = (shimmerCenter - shimmerWidth).coerceIn(filledStart, filledEnd)
        val shimmerEnd = (shimmerCenter + shimmerWidth).coerceIn(filledStart, filledEnd)
        if (shimmerEnd > shimmerStart) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, highlightColor.copy(alpha = 0.42f), Color.Transparent),
                    startX = shimmerCenter - shimmerWidth,
                    endX = shimmerCenter + shimmerWidth,
                ),
                topLeft = Offset(shimmerStart, centerY - trackHeight / 2f),
                size = Size(shimmerEnd - shimmerStart, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius, trackRadius),
            )
        }
    }

    drawRoundRect(
        color = outlineColor.copy(alpha = 0.62f),
        topLeft = Offset(frameStart + frameStrokeWidth / 2f, centerY - frameHeight / 2f + frameStrokeWidth / 2f),
        size = Size(
            width = (frameEnd - frameStart - frameStrokeWidth).coerceAtLeast(0f),
            height = (frameHeight - frameStrokeWidth).coerceAtLeast(0f),
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(frameRadius, frameRadius),
        style = Stroke(width = frameStrokeWidth),
    )

    val stopRadius = 2.dp.toPx()
    repeat(stopCount) { index ->
        val fraction = if (stopCount <= 1) 0f else index.toFloat() / (stopCount - 1).toFloat()
        drawCircle(
            color = if (index <= selectedIndex) {
                highlightColor.copy(alpha = 0.62f)
            } else {
                outlineColor.copy(alpha = 0.78f)
            },
            radius = stopRadius,
            center = Offset(
                x = if (isRtl) valueEnd - valueWidth * fraction else valueStart + valueWidth * fraction,
                y = centerY,
            ),
        )
    }
}
