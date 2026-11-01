package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/** 波形仍由真实采样驱动，只放大较轻的人声；零音量不产生虚构的起伏。 */
@Composable
internal fun VoiceWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    live: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val count = if (live) 32 else 16
    val samples = remember(levels, live) {
        val source = if (live) levels.takeLast(count) else levels
        val reduced = if (source.size > count) {
            List(count) { index ->
                source.subList(index * source.size / count, (index + 1) * source.size / count).max()
            }
        } else {
            List(count - source.size) { 0f } + source
        }
        reduced.map { it.coerceIn(0f, 1f).pow(0.4f) }
    }
    val heights = if (live) {
        samples.map { level ->
            animateFloatAsState(level, animationSpec = tween(90), label = "voice-level").value
        }
    } else {
        samples
    }
    val liveBrush = Brush.verticalGradient(
        listOf(color.copy(alpha = 0.55f), color, color.copy(alpha = 0.7f)),
    )
    Canvas(modifier) {
        val step = size.width / count
        val center = size.height / 2
        heights.forEachIndexed { index, level ->
            val minimum = if (live) 3.dp.toPx() else 1.5.dp.toPx()
            val halfHeight = maxOf(minimum, level * size.height * 0.46f)
            val x = step * (index + 0.5f)
            val start = Offset(x, center - halfHeight)
            val end = Offset(x, center + halfHeight)
            val strokeWidth = minOf(if (live) 5.dp.toPx() else 3.dp.toPx(), step * 0.6f)
            if (live) {
                drawLine(liveBrush, start, end, strokeWidth, StrokeCap.Round)
            } else {
                val played = progress <= 0f || (index + 0.5f) / count <= progress
                drawLine(color.copy(alpha = if (played) 1f else 0.3f), start, end, strokeWidth, StrokeCap.Round)
            }
        }
    }
}
