package com.ai.assistance.operit.ui.floating.ui.fullscreen.components

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.ui.common.WaveVisualizer
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.cos
import kotlin.math.sin

/**
 * 波浪可视化和头像组件
 */
@Composable
fun WaveVisualizerSection(
    isWaveActive: Boolean,
    isRecording: Boolean,
    showAiLoadingEffect: Boolean,
    volumeLevelFlow: StateFlow<Float>?,
    aiAvatarUri: String?,
    avatarShape: Shape = CircleShape,
    activeWaveSize: Dp = 300.dp,
    inactiveWaveSize: Dp = 120.dp,
    activeAvatarSize: Dp = 120.dp,
    inactiveAvatarSize: Dp = 80.dp,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val waveSize by animateDpAsState(
        targetValue = if (isWaveActive) activeWaveSize else inactiveWaveSize,
        animationSpec = tween(500),
        label = "waveSize"
    )

    val waveOffsetY by animateDpAsState(
        targetValue = if (isWaveActive) 0.dp else (-100).dp,
        animationSpec = tween(500),
        label = "waveOffsetY"
    )

    val avatarSize by animateDpAsState(
        targetValue = if (isWaveActive) activeAvatarSize else inactiveAvatarSize,
        animationSpec = tween(500),
        label = "avatarSize"
    )

    Box(
        modifier = modifier.offset(y = waveOffsetY),
        contentAlignment = Alignment.Center
    ) {
        WaveVisualizer(
            modifier = Modifier.size(waveSize),
            isActive = isWaveActive,
            volumeFlow = if (isWaveActive && isRecording) volumeLevelFlow else null,
            waveColor = if (showAiLoadingEffect) {
                Color.White.copy(alpha = 0.32f)
            } else {
                Color.White.copy(alpha = 0.7f)
            },
            activeWaveColor = if (showAiLoadingEffect) {
                colors.tertiary
            } else {
                colors.primary
            },
            onToggleActive = onToggleActive
        )

        if (showAiLoadingEffect) {
            AiLoadingWaveOverlay(
                modifier = Modifier.size(avatarSize * 1.72f),
                avatarDiameterRatio = 1f / 1.72f,
                primaryColor = colors.primary,
                accentColor = colors.tertiary
            )
        }

        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(avatarShape)
                .clickable { onToggleActive() }
        ) {
            if (aiAvatarUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = Uri.parse(aiAvatarUri)),
                    contentDescription = "AI Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Assistant,
                    contentDescription = "AI Avatar",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun AiLoadingWaveOverlay(
    modifier: Modifier = Modifier,
    avatarDiameterRatio: Float,
    primaryColor: Color,
    accentColor: Color
) {
    val transition = rememberInfiniteTransition(label = "ai_loading_wave_overlay")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing)
        ),
        label = "loading_rotation"
    )
    val counterRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing)
        ),
        label = "loading_counter_rotation"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading_breathe"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading_pulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val avatarRadius = size.minDimension * avatarDiameterRatio * 0.5f
        val haloRadius = avatarRadius * 1.55f * breathe
        val outerRadius = avatarRadius * 1.28f
        val innerRadius = avatarRadius * 1.10f
        val strokeWidth = size.minDimension * 0.017f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.16f * pulse),
                    accentColor.copy(alpha = 0.10f * pulse),
                    Color.Transparent
                ),
                center = center,
                radius = haloRadius
            ),
            radius = haloRadius,
            center = center
        )

        rotate(rotation, center) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        primaryColor.copy(alpha = 0.10f),
                        primaryColor.copy(alpha = 0.54f),
                        accentColor.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = 210f,
                sweepAngle = 102f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2f, outerRadius * 2f),
                style = Stroke(width = strokeWidth)
            )
        }

        rotate(counterRotation, center) {
            drawArc(
                color = accentColor.copy(alpha = 0.34f + pulse * 0.25f),
                startAngle = 34f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2f, innerRadius * 2f),
                style = Stroke(width = strokeWidth * 0.9f)
            )
        }

        listOf(0f, 120f, 240f).forEachIndexed { index, baseAngle ->
            val angle = Math.toRadians((baseAngle + rotation * (if (index % 2 == 0) 1f else -0.75f)).toDouble())
            val orbitRadius = avatarRadius * (1.18f + index * 0.11f)
            val dotCenter = Offset(
                x = center.x + cos(angle).toFloat() * orbitRadius,
                y = center.y + sin(angle).toFloat() * orbitRadius
            )
            drawCircle(
                color = if (index == 1) {
                    accentColor.copy(alpha = 0.26f + pulse * 0.30f)
                } else {
                    primaryColor.copy(alpha = 0.18f + pulse * 0.24f)
                },
                radius = avatarRadius * (0.08f + index * 0.012f),
                center = dotCenter
            )
        }
    }
}
