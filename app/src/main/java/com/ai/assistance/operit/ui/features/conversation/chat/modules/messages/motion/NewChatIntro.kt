package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.motion

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/** 固定 1.8 秒，随机轨迹只在一轮开始时生成；画布不接管触摸和焦点。 */
@Composable
fun NewChatIntro(
    requestId: String?, currentChatId: String?, ready: Boolean, empty: Boolean, hasDraft: Boolean,
    onConsumed: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }
    val consume by rememberUpdatedState(onConsumed)
    val latestRequest by rememberUpdatedState(requestId)
    DisposableEffect(Unit) {
        onDispose { latestRequest?.let(consume) }
    }
    LaunchedEffect(requestId) {
        val id = requestId ?: return@LaunchedEffect
        // 创建后没有进入对应页面的请求不会在之后切回时意外播放。
        delay(5000)
        consume(id)
    }
    LaunchedEffect(requestId, currentChatId, ready, empty, hasDraft) {
        val id = requestId ?: return@LaunchedEffect
        if (id != currentChatId || !ready) return@LaunchedEffect
        if (hasDraft || !empty) {
            consume(id)
            return@LaunchedEffect
        }
        try {
            if (empty && !hasDraft && ValueAnimator.areAnimatorsEnabled()) {
                progress.snapTo(0f)
                playing = true
                progress.animateTo(1f, tween(1800, easing = androidx.compose.animation.core.LinearEasing))
            }
        } finally {
            playing = false
            consume(id)
        }
    }
    if (!playing) return
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val points = remember(widthPx, density.density) {
            wordmarkPoints(widthPx.coerceAtMost(with(density) { 280.dp.toPx() }), with(density) { 60.dp.toPx() })
        }
        val trajectories = remember(requestId, points.offsets.size) {
            val random = Random(requireNotNull(requestId).hashCode())
            List(points.offsets.size) { Offset(random.nextFloat(), random.nextFloat()) }
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            val assembly = FastOutSlowInEasing.transform((t / (1200f / 1800f)).coerceIn(0f, 1f))
            val opacity = 1f - ((t - 1550f / 1800f) / (250f / 1800f)).coerceIn(0f, 1f)
            val left = (widthPx - points.width) / 2f
            val top = heightPx * 0.36f - points.height / 2f
            points.offsets.forEachIndexed { index, target ->
                val trajectory = trajectories[index]
                val startX = if (rtl) widthPx * (0.9f + trajectory.x * 0.1f) else widthPx * trajectory.x * 0.1f
                val startY = heightPx * (0.12f + trajectory.y * 0.35f)
                val arc = sin(assembly * Math.PI).toFloat() * (trajectory.y - 0.5f) * heightPx * 0.22f
                val x = startX + (left + target.x - startX) * assembly
                val y = startY + (top + target.y - startY) * assembly + arc
                drawRect(lerp(primary, secondary, assembly * 0.35f).copy(alpha = opacity), Offset(x, y), Size(points.step, points.step))
            }
        }
    }
}

private data class WordmarkPoints(val offsets: List<Offset>, val width: Float, val height: Float, val step: Float)

/** 只采样字形的非透明区域，避免把文字边界框误画成一块矩形。 */
private fun wordmarkPoints(maxWidth: Float, fontSize: Float): WordmarkPoints {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = fontSize
    }
    if (paint.measureText("Operit") > maxWidth) paint.textSize *= maxWidth / paint.measureText("Operit")
    val width = paint.measureText("Operit").toInt().coerceAtLeast(1) + 2
    val height = (paint.fontMetrics.bottom - paint.fontMetrics.top).toInt().coerceAtLeast(1) + 2
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bitmap).drawText("Operit", 1f, 1f - paint.fontMetrics.top, paint)
    val step = (paint.textSize / 22f).toInt().coerceAtLeast(2)
    val offsets = buildList {
        for (y in 0 until height step step) for (x in 0 until width step step) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 100) add(Offset(x.toFloat(), y.toFloat()))
        }
    }
    bitmap.recycle()
    return WordmarkPoints(offsets, width.toFloat(), height.toFloat(), step.toFloat() * 0.84f)
}