package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityControl
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityMapping
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

private data class ThinkingSliderStop(
    val id: String?,
    val displayLabel: String,
    val isOff: Boolean = false,
)

@Composable
internal fun ThinkingQualitySlider(
    label: String,
    mapping: ThinkingQualityMapping,
    enabled: Boolean,
    onEnabledChange: () -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 关闭是 UI 自己的 stop，厂商映射中的真实 option 仍保持原顺序和原 ID。
    val stops = when (mapping.control) {
        ThinkingQualityControl.LEVELS -> buildList {
            add(
                ThinkingSliderStop(
                    id = null,
                    displayLabel = stringResource(R.string.thinking_type_off),
                    isOff = true,
                )
            )
            mapping.options.forEach { option ->
                add(ThinkingSliderStop(id = option.id, displayLabel = option.displayLabel))
            }
        }
        ThinkingQualityControl.TOGGLE_ONLY -> listOf(
            ThinkingSliderStop(
                id = null,
                displayLabel = stringResource(R.string.thinking_type_off),
                isOff = true,
            ),
            ThinkingSliderStop(
                id = null,
                displayLabel = stringResource(R.string.thinking_type_mode),
            ),
        )
        ThinkingQualityControl.UNSUPPORTED -> emptyList()
    }

    if (stops.size < 2 || (mapping.control == ThinkingQualityControl.LEVELS && mapping.options.isEmpty())) {
        return
    }

    val minIndex = if (mapping.reasoningRequired) 1 else 0
    val selectedIndex = when {
        !enabled && !mapping.reasoningRequired -> 0
        mapping.control == ThinkingQualityControl.TOGGLE_ONLY -> 1
        else -> {
            val optionIndex = stops.indexOfFirst { !it.isOff && it.id == value }
            (if (optionIndex >= 0) optionIndex else 1).coerceAtLeast(minIndex)
        }
    }
    val lastIndex = stops.lastIndex
    var sliderPosition by remember(stops) { mutableFloatStateOf(selectedIndex.toFloat()) }
    var lastAppliedIndex by remember(stops) { mutableIntStateOf(selectedIndex) }
    var appliedEnabled by remember(stops) { mutableStateOf(enabled || mapping.reasoningRequired) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex, enabled) {
        if (!isDragging) {
            sliderPosition = selectedIndex.toFloat()
            lastAppliedIndex = selectedIndex
            appliedEnabled = enabled || mapping.reasoningRequired
        }
    }
    fun applyStop(index: Int) {
        val safeIndex = index.coerceIn(minIndex, lastIndex)
        if (safeIndex == lastAppliedIndex) return
        val stop = stops[safeIndex]
        val shouldBeEnabled = !stop.isOff
        if (!mapping.reasoningRequired && shouldBeEnabled != appliedEnabled) {
            onEnabledChange()
            appliedEnabled = shouldBeEnabled
        }
        if (
            shouldBeEnabled &&
            mapping.control == ThinkingQualityControl.LEVELS &&
            stop.id != null &&
            stop.id != value
        ) {
            onValueChange(stop.id)
        }
        lastAppliedIndex = safeIndex
    }
    val currentIndex = sliderPosition.roundToInt().coerceIn(minIndex, lastIndex)
    val selectedStop = stops[currentIndex]
    val density = LocalDensity.current
    val thumbStartPx = with(density) { 11.dp.toPx() }
    val trackTravelInsetPx = with(density) { 25.dp.toPx() }
    fun updateSliderPosition(x: Float, trackWidth: Float) {
        val travel = (trackWidth - trackTravelInsetPx).coerceAtLeast(1f)
        val fraction = ((x - thumbStartPx) / travel).coerceIn(0f, 1f)
        val next = (fraction * lastIndex).roundToInt().coerceIn(minIndex, lastIndex)
        sliderPosition = next.toFloat()
    }
    fun commitSliderPosition() {
        applyStop(sliderPosition.roundToInt())
    }
    fun cancelSliderPosition() {
        sliderPosition = selectedIndex.toFloat()
        lastAppliedIndex = selectedIndex
        appliedEnabled = enabled || mapping.reasoningRequired
    }
    val primary = MaterialTheme.colorScheme.primary
    val isOn = !selectedStop.isOff
    val activeLevelFraction = if (isOn && lastIndex > minIndex) {
        ((currentIndex - minIndex).toFloat() / (lastIndex - minIndex).toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onInfoClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = label,
                tint = if (isOn) primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label:",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = selectedStop.displayLabel,
            color = if (isOn) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.widthIn(max = 64.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        ThinkingQualityTrack(
            progress = sliderPosition / lastIndex.toFloat(),
            selectedIndex = currentIndex,
            stopCount = stops.size,
            isOn = isOn,
            isDragging = isDragging,
            particleLevelFraction = activeLevelFraction,
            particleColor = Color(LocalThemePreferenceSnapshot.current.thinkingParticleColor),
            onDraggingChange = { isDragging = it },
            onPositionChange = ::updateSliderPosition,
            onPositionCommit = ::commitSliderPosition,
            onPositionCancel = ::cancelSliderPosition,
            accessibilityDescription = "$label: ${selectedStop.displayLabel}",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThinkingQualityTrack(
    progress: Float,
    selectedIndex: Int,
    stopCount: Int,
    isOn: Boolean,
    isDragging: Boolean,
    particleLevelFraction: Float,
    particleColor: Color,
    onDraggingChange: (Boolean) -> Unit,
    onPositionChange: (Float, Float) -> Unit,
    onPositionCommit: () -> Unit,
    onPositionCancel: () -> Unit,
    accessibilityDescription: String,
    modifier: Modifier = Modifier,
) {
    val latestOnDraggingChange by rememberUpdatedState(onDraggingChange)
    val latestOnPositionChange by rememberUpdatedState(onPositionChange)
    val latestOnPositionCommit by rememberUpdatedState(onPositionCommit)
    val latestOnPositionCancel by rememberUpdatedState(onPositionCancel)
    val colorScheme = MaterialTheme.colorScheme
    val highlightColor = lerp(colorScheme.primary, colorScheme.onSurface, 0.62f)
    val shimmerPosition = remember { Animatable(-0.24f) }
    val particlePosition = remember { Animatable(-0.14f) }
    val particleCycleDurationMillis =
        (1800 - 1200 * particleLevelFraction).roundToInt().coerceIn(600, 1800)

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

    LaunchedEffect(isDragging, isOn, particleCycleDurationMillis) {
        if (isDragging || !isOn) {
            particlePosition.snapTo(-0.14f)
            return@LaunchedEffect
        }

        while (isActive) {
            particlePosition.snapTo(-0.14f)
            particlePosition.animateTo(
                targetValue = 1.14f,
                animationSpec = tween(
                    durationMillis = particleCycleDurationMillis,
                    easing = LinearEasing,
                ),
            )
        }
    }

    val openProgress by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = tween(durationMillis = 460),
        label = "thinkingTrackOpenProgress",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (isDragging) snap() else tween(durationMillis = 300),
        label = "thinkingTrackProgress",
    )

    Canvas(
        modifier = modifier
            .height(21.dp)
            .clip(RoundedCornerShape(999.dp))
            .semantics {
                contentDescription = accessibilityDescription
            }
            .pointerInput(stopCount) {
                detectTapGestures { offset ->
                    latestOnPositionChange(offset.x, size.width.toFloat())
                    latestOnPositionCommit()
                }
            }
            .pointerInput(stopCount) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latestOnDraggingChange(true)
                        latestOnPositionChange(offset.x, size.width.toFloat())
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        latestOnPositionChange(change.position.x, size.width.toFloat())
                    },
                    onDragEnd = {
                        latestOnPositionCommit()
                        latestOnDraggingChange(false)
                    },
                    onDragCancel = {
                        latestOnPositionCancel()
                        latestOnDraggingChange(false)
                    },
                )
            },
    ) {
        drawThinkingQualityTrack(
            progress = animatedProgress,
            shimmerPosition = shimmerPosition.value,
            showShimmer = isDragging,
            particlePosition = particlePosition.value,
            showParticle = !isDragging && isOn,
            particleColor = particleColor,
            selectedIndex = selectedIndex,
            stopCount = stopCount,
            openProgress = openProgress,
            primary = colorScheme.primary,
            highlightColor = highlightColor,
            trackColor = colorScheme.surfaceVariant,
            outlineColor = colorScheme.outline,
        )
    }
}

private fun DrawScope.drawThinkingQualityTrack(
    progress: Float,
    shimmerPosition: Float,
    showShimmer: Boolean,
    particlePosition: Float,
    showParticle: Boolean,
    particleColor: Color,
    selectedIndex: Int,
    stopCount: Int,
    openProgress: Float,
    primary: Color,
    highlightColor: Color,
    trackColor: Color,
    outlineColor: Color,
) {
    val frameHeight = size.height
    val frameRadius = frameHeight / 2f
    val frameStrokeWidth = 1.5.dp.toPx()
    val activeAlpha = openProgress.coerceIn(0f, 1f)
    val closedAlpha = 1f - activeAlpha
    val thumbTravel = (size.width - 25.dp.toPx()).coerceAtLeast(0f)
    val thumbCenterX = 11.dp.toPx() + thumbTravel * progress.coerceIn(0f, 1f)
    val fillWidth = (
        19.dp.toPx() + progress.coerceIn(0f, 1f) * (thumbTravel + 3.dp.toPx())
    ).coerceIn(0f, size.width)

    drawRoundRect(
        color = trackColor,
        topLeft = Offset.Zero,
        size = Size(size.width, frameHeight),
        cornerRadius = CornerRadius(frameRadius, frameRadius),
    )

    val activeTrackBrush = Brush.horizontalGradient(
        colors = listOf(
            lerp(trackColor, primary, 0.18f).copy(alpha = activeAlpha),
            lerp(trackColor, primary, 0.56f).copy(alpha = activeAlpha),
            lerp(trackColor, primary, 0.84f).copy(alpha = activeAlpha),
        ),
        startX = 0f,
        endX = fillWidth.coerceAtLeast(1f),
    )

    if (fillWidth > 0f && activeAlpha > 0f) {
        drawRoundRect(
            brush = activeTrackBrush,
            topLeft = Offset.Zero,
            size = Size(fillWidth, frameHeight),
            cornerRadius = CornerRadius(frameRadius, frameRadius),
        )
    }

    // 拖动时让高光从左向当前已选轨道循环扫过。
    if (showShimmer && fillWidth > 0f && activeAlpha > 0f) {
        val shimmerWidth = 26.dp.toPx()
        val shimmerCenter = fillWidth * shimmerPosition
        val shimmerStart = (shimmerCenter - shimmerWidth).coerceIn(0f, fillWidth)
        val shimmerEnd = (shimmerCenter + shimmerWidth).coerceIn(0f, fillWidth)
        if (shimmerEnd > shimmerStart) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.46f * activeAlpha),
                        Color.Transparent,
                    ),
                    startX = shimmerCenter - shimmerWidth,
                    endX = shimmerCenter + shimmerWidth,
                ),
                topLeft = Offset(shimmerStart, 0f),
                size = Size(shimmerEnd - shimmerStart, frameHeight),
                cornerRadius = CornerRadius(frameRadius, frameRadius),
            )
        }
    }

    // 松手后用主题设置的粒子颜色持续流动，粒子速度由已选档位决定。
    if (showParticle && fillWidth > 0f && activeAlpha > 0f) {
        val particleCount = (fillWidth / 4.dp.toPx()).roundToInt().coerceIn(5, 18)
        val particleSpacing = 1f / particleCount.toFloat()
        repeat(particleCount) { index ->
            val normalizedPosition = particlePosition + index * particleSpacing
            val wrappedPosition = normalizedPosition - kotlin.math.floor(normalizedPosition)
            val particleX = fillWidth * wrappedPosition
            val particleY = frameHeight / 2f + ((index % 5) - 2) * 0.7.dp.toPx()
            val particleRadius = (0.75f + (index % 3) * 0.35f).dp.toPx()
            val particleAlpha = particleColor.alpha * (0.34f + (index % 4) * 0.13f) * activeAlpha
            drawCircle(
                color = particleColor.copy(alpha = particleAlpha * 0.22f),
                radius = particleRadius * 2.2f,
                center = Offset(particleX, particleY),
            )
            drawCircle(
                color = particleColor.copy(alpha = particleAlpha),
                radius = particleRadius,
                center = Offset(particleX, particleY),
            )
        }
    }

    if (closedAlpha > 0f) {
        val blockInset = 2.dp.toPx()
        val blockHeight = (frameHeight - 6.dp.toPx()).coerceAtLeast(0f)
        val blockWidth = ((size.width - blockInset * 2f) * 0.5f).coerceAtLeast(0f)
        val blockLeft = blockInset + (size.width - blockInset * 2f - blockWidth) * activeAlpha
        drawRoundRect(
            color = primary.copy(alpha = 0.55f * closedAlpha),
            topLeft = Offset(blockLeft, 3.dp.toPx()),
            size = Size(blockWidth, blockHeight),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
        )
    }

    if (closedAlpha > 0f) {
        drawRoundRect(
            color = outlineColor.copy(alpha = closedAlpha),
            topLeft = Offset(frameStrokeWidth / 2f, frameStrokeWidth / 2f),
            size = Size(
                (size.width - frameStrokeWidth).coerceAtLeast(0f),
                (frameHeight - frameStrokeWidth).coerceAtLeast(0f),
            ),
            cornerRadius = CornerRadius(
                frameRadius - frameStrokeWidth / 2f,
                frameRadius - frameStrokeWidth / 2f,
            ),
            style = Stroke(width = frameStrokeWidth),
        )
    }

    if (activeAlpha > 0f && stopCount > 1) {
        val stopRadius = 2.dp.toPx()
        repeat(stopCount) { index ->
            val fraction = index.toFloat() / (stopCount - 1).toFloat()
            val x = 11.dp.toPx() + thumbTravel * fraction
            val dotColor = if (index <= selectedIndex) highlightColor else outlineColor
            drawCircle(
                color = dotColor.copy(alpha = activeAlpha),
                radius = stopRadius,
                center = Offset(x, frameHeight / 2f),
            )
        }
    }

    if (activeAlpha > 0f) {
        drawCircle(
            color = highlightColor.copy(alpha = activeAlpha),
            radius = 8.dp.toPx() * activeAlpha,
            center = Offset(thumbCenterX, frameHeight / 2f),
        )
    }
}
