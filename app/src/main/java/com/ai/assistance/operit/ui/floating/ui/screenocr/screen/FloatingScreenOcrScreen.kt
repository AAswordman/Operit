package com.ai.assistance.operit.ui.floating.ui.screenocr.screen

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import com.ai.assistance.operit.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.util.OCRUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.hypot

private data class CropBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

private fun computeCropBounds(srcBitmap: Bitmap, rectUi: Rect, overlay: IntSize): CropBounds? {
    if (overlay.width <= 0 || overlay.height <= 0) return null

    val scale = min(
        overlay.width.toFloat() / srcBitmap.width.toFloat(),
        overlay.height.toFloat() / srcBitmap.height.toFloat()
    )
    val drawW = srcBitmap.width * scale
    val drawH = srcBitmap.height * scale
    val offsetX = (overlay.width - drawW) / 2f
    val offsetY = (overlay.height - drawH) / 2f

    val clampedLeft = rectUi.left.coerceIn(offsetX, offsetX + drawW)
    val clampedTop = rectUi.top.coerceIn(offsetY, offsetY + drawH)
    val clampedRight = rectUi.right.coerceIn(offsetX, offsetX + drawW)
    val clampedBottom = rectUi.bottom.coerceIn(offsetY, offsetY + drawH)

    val leftPx = ((clampedLeft - offsetX) / scale).toInt().coerceIn(0, srcBitmap.width - 1)
    val topPx = ((clampedTop - offsetY) / scale).toInt().coerceIn(0, srcBitmap.height - 1)
    val rightPx = ((clampedRight - offsetX) / scale).toInt().coerceIn(leftPx + 1, srcBitmap.width)
    val bottomPx = ((clampedBottom - offsetY) / scale).toInt().coerceIn(topPx + 1, srcBitmap.height)

    val cropW = (rightPx - leftPx).coerceAtLeast(1)
    val cropH = (bottomPx - topPx).coerceAtLeast(1)
    if (cropW <= 1 || cropH <= 1) return null

    return CropBounds(left = leftPx, top = topPx, width = cropW, height = cropH)
}

private fun hitTestHandle(pos: Offset, rect: Rect, handleRadiusPx: Float): SelectionDragHandle {
    val tl = rect.topLeft
    val tr = rect.topRight
    val bl = rect.bottomLeft
    val br = rect.bottomRight
    val tm = Offset(rect.center.x, rect.top)
    val bm = Offset(rect.center.x, rect.bottom)
    val lm = Offset(rect.left, rect.center.y)
    val rm = Offset(rect.right, rect.center.y)

    fun near(a: Offset): Boolean = (pos - a).getDistance() <= handleRadiusPx

    return when {
        near(tl) -> SelectionDragHandle.TOP_LEFT
        near(tr) -> SelectionDragHandle.TOP_RIGHT
        near(bl) -> SelectionDragHandle.BOTTOM_LEFT
        near(br) -> SelectionDragHandle.BOTTOM_RIGHT
        near(tm) -> SelectionDragHandle.TOP
        near(bm) -> SelectionDragHandle.BOTTOM
        near(lm) -> SelectionDragHandle.LEFT
        near(rm) -> SelectionDragHandle.RIGHT
        rect.contains(pos) -> SelectionDragHandle.MOVE
        else -> SelectionDragHandle.NONE
    }
}

private fun clampSelectionRect(rect: Rect, overlay: IntSize, minSizePx: Float): Rect {
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom

    if (right < left) {
        val t = left
        left = right
        right = t
    }
    if (bottom < top) {
        val t = top
        top = bottom
        bottom = t
    }

    val w = (right - left).coerceAtLeast(minSizePx)
    val h = (bottom - top).coerceAtLeast(minSizePx)
    right = left + w
    bottom = top + h

    val maxW = overlay.width.toFloat().coerceAtLeast(1f)
    val maxH = overlay.height.toFloat().coerceAtLeast(1f)

    if (left < 0f) {
        val dx = -left
        left += dx
        right += dx
    }
    if (top < 0f) {
        val dy = -top
        top += dy
        bottom += dy
    }
    if (right > maxW) {
        val dx = right - maxW
        left -= dx
        right -= dx
    }
    if (bottom > maxH) {
        val dy = bottom - maxH
        top -= dy
        bottom -= dy
    }

    left = left.coerceIn(0f, maxW - minSizePx)
    top = top.coerceIn(0f, maxH - minSizePx)
    right = right.coerceIn(left + minSizePx, maxW)
    bottom = bottom.coerceIn(top + minSizePx, maxH)

    return Rect(left, top, right, bottom)
}

private fun applyHandleDelta(start: Rect, delta: Offset, handle: SelectionDragHandle): Rect {
    return when (handle) {
        SelectionDragHandle.MOVE -> Rect(
            left = start.left + delta.x,
            top = start.top + delta.y,
            right = start.right + delta.x,
            bottom = start.bottom + delta.y,
        )
        SelectionDragHandle.TOP_LEFT -> Rect(
            left = start.left + delta.x,
            top = start.top + delta.y,
            right = start.right,
            bottom = start.bottom,
        )
        SelectionDragHandle.TOP_RIGHT -> Rect(
            left = start.left,
            top = start.top + delta.y,
            right = start.right + delta.x,
            bottom = start.bottom,
        )
        SelectionDragHandle.BOTTOM_LEFT -> Rect(
            left = start.left + delta.x,
            top = start.top,
            right = start.right,
            bottom = start.bottom + delta.y,
        )
        SelectionDragHandle.BOTTOM_RIGHT -> Rect(
            left = start.left,
            top = start.top,
            right = start.right + delta.x,
            bottom = start.bottom + delta.y,
        )
        SelectionDragHandle.LEFT -> Rect(
            left = start.left + delta.x,
            top = start.top,
            right = start.right,
            bottom = start.bottom,
        )
        SelectionDragHandle.RIGHT -> Rect(
            left = start.left,
            top = start.top,
            right = start.right + delta.x,
            bottom = start.bottom,
        )
        SelectionDragHandle.TOP -> Rect(
            left = start.left,
            top = start.top + delta.y,
            right = start.right,
            bottom = start.bottom,
        )
        SelectionDragHandle.BOTTOM -> Rect(
            left = start.left,
            top = start.top,
            right = start.right,
            bottom = start.bottom + delta.y,
        )
        SelectionDragHandle.NONE -> start
    }
}

private enum class SelectionDragHandle {
    NONE,
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

@Composable
fun FloatingScreenOcrScreen(floatContext: FloatContext) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleRadiusPx = with(density) { 18.dp.toPx() }
    val minSizePx = with(density) { 32.dp.toPx() }


    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }

    val points = remember { mutableStateListOf<Offset>() }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    var activeHandle by remember { mutableStateOf(SelectionDragHandle.NONE) }
    var handleStartRect by remember { mutableStateOf<Rect?>(null) }
    var handleStartPos by remember { mutableStateOf(Offset.Zero) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val rippleProgress = remember { Animatable(0f) }
    var showRipple by remember { mutableStateOf(false) }

    val fxTransition = rememberInfiniteTransition(label = "screen_ocr_fx")
    val hueShift by
        fxTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 5200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
            label = "hueShift"
        )
    val glowPulse by
        fxTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
            label = "glowPulse"
        )

    LaunchedEffect(Unit) {
        captureError = null
        screenshotBitmap = null

        val toolHandler = AIToolHandler.getInstance(context)
        val result = withContext(Dispatchers.IO) {
            toolHandler.executeTool(AITool(name = "capture_screenshot"))
        }

        if (!result.success) {
            captureError = result.error ?: context.getString(R.string.screen_ocr_capture_failed)
            return@LaunchedEffect
        }

        val path = result.result.toString().trim()
        if (path.isBlank()) {
            captureError = context.getString(R.string.screen_ocr_capture_failed)
            return@LaunchedEffect
        }

        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)
        }

        if (bitmap == null) {
            captureError = context.getString(R.string.screen_ocr_load_failed)
            return@LaunchedEffect
        }

        screenshotBitmap = bitmap
    }

    LaunchedEffect(screenshotBitmap, overlaySize) {
        val bitmap = screenshotBitmap ?: return@LaunchedEffect
        if (overlaySize.width <= 0 || overlaySize.height <= 0) return@LaunchedEffect

        // A simple one-shot ripple to indicate "进入圈选模式".
        showRipple = true
        rippleProgress.snapTo(0f)
        rippleProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
        showRipple = false
    }

    LaunchedEffect(showConfirm, selectionRect, screenshotBitmap, overlaySize) {
        if (!showConfirm) {
            previewBitmap = null
            return@LaunchedEffect
        }

        val src = screenshotBitmap ?: return@LaunchedEffect
        val rectUi = selectionRect ?: return@LaunchedEffect
        val bounds = computeCropBounds(src, rectUi, overlaySize) ?: return@LaunchedEffect

        previewBitmap = withContext(Dispatchers.Default) {
            val cropped = Bitmap.createBitmap(src, bounds.left, bounds.top, bounds.width, bounds.height)
            val maxDim = 280
            val w = cropped.width
            val h = cropped.height
            val scale = min(maxDim.toFloat() / w.toFloat(), maxDim.toFloat() / h.toFloat()).coerceAtMost(1f)
            if (scale >= 1f) {
                cropped
            } else {
                Bitmap.createScaledBitmap(cropped, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
    ) {
        val bitmap = screenshotBitmap

        // 1. 底层：截图
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // 2. 遮罩与交互层
        // 使用 graphicsLayer { alpha = 0.99f } 开启离屏缓冲，以支持 BlendMode.Clear 创建"挖洞"效果
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.99f }
                .pointerInput(isBusy, showConfirm, overlaySize) {
                    if (isBusy) return@pointerInput

                    if (!showConfirm) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                points.clear()
                                points.add(offset)
                                selectionRect = null
                                showConfirm = false
                            },
                            onDrag = { change, _ ->
                                val pos = change.position
                                val last = points.lastOrNull()
                                if (last == null || (pos - last).getDistance() >= 2.5f) {
                                    points.add(pos)
                                }
                            },
                            onDragCancel = {
                                points.clear()
                                selectionRect = null
                                showConfirm = false
                            },
                            onDragEnd = {
                                if (points.size < 3) {
                                    OKToast.warning(context, context.getString(R.string.screen_ocr_selection_too_small)).show()
                                    points.clear()
                                    selectionRect = null
                                    showConfirm = false
                                    return@detectDragGestures
                                }

                                val minX = points.minOf { it.x }
                                val minY = points.minOf { it.y }
                                val maxX = points.maxOf { it.x }
                                val maxY = points.maxOf { it.y }

                                val w = maxX - minX
                                val h = maxY - minY
                                if (w < 10f || h < 10f) {
                                    OKToast.warning(context, context.getString(R.string.screen_ocr_selection_too_small)).show()
                                    points.clear()
                                    selectionRect = null
                                    showConfirm = false
                                    return@detectDragGestures
                                }

                                selectionRect = clampSelectionRect(Rect(minX, minY, maxX, maxY), overlaySize, minSizePx)
                                showConfirm = true
                            }
                        )
                        return@pointerInput
                    }

                    detectDragGestures(
                        onDragStart = { offset ->
                            val rect = selectionRect ?: return@detectDragGestures
                            val handle = hitTestHandle(offset, rect, handleRadiusPx)
                            activeHandle = handle
                            if (handle == SelectionDragHandle.NONE) {
                                handleStartRect = null
                                return@detectDragGestures
                            }
                            handleStartRect = rect
                            handleStartPos = offset
                        },
                        onDragCancel = {
                            activeHandle = SelectionDragHandle.NONE
                            handleStartRect = null
                        },
                        onDragEnd = {
                            activeHandle = SelectionDragHandle.NONE
                            handleStartRect = null
                        },
                        onDrag = { change, _ ->
                            val handle = activeHandle
                            val start = handleStartRect ?: return@detectDragGestures
                            if (handle == SelectionDragHandle.NONE) return@detectDragGestures
                            val delta = change.position - handleStartPos
                            selectionRect = clampSelectionRect(applyHandleDelta(start, delta, handle), overlaySize, minSizePx)
                            change.consumeAllChanges()
                        }
                    )
                }
        ) {
            // 背景遮罩色：动画过渡
            // showRipple 时使用深色背景(灰色开始)以增强网格光效对比度
            // 结束后过渡到用户想要的淡蓝色
            // 注意：在Canvas内使用渐变动画需要通过rippleProgress来插值
            val overlayColor = if (rippleProgress.value < 1f) {
                // 通过 rippleProgress 插值：从深色过渡到蓝色
                val t = rippleProgress.value
                val darkColor = Color.Black.copy(alpha = 0.75f)
                val blueColor = Color(0xFF2196F3).copy(alpha = 0.35f)
                Color(
                    red = darkColor.red + (blueColor.red - darkColor.red) * t,
                    green = darkColor.green + (blueColor.green - darkColor.green) * t,
                    blue = darkColor.blue + (blueColor.blue - darkColor.blue) * t,
                    alpha = darkColor.alpha + (blueColor.alpha - darkColor.alpha) * t
                )
            } else {
                Color(0xFF2196F3).copy(alpha = 0.35f)
            }

            // 绘制全屏遮罩
            drawRect(color = overlayColor)

            // 如果有选区，挖空选区部分
            selectionRect?.let { rect ->
                drawRect(
                    color = Color.Transparent,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    blendMode = BlendMode.Clear
                )

                // 绘制选区边框
                drawRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 2.dp.toPx())
                )

                // 简单的四个角装饰
                val cornerLen = 10.dp.toPx()
                val strokeW = 4.dp.toPx()
                val capColor = Color.White
                // TopLeft
                drawLine(capColor, rect.topLeft, rect.topLeft + Offset(cornerLen, 0f), strokeW)
                drawLine(capColor, rect.topLeft, rect.topLeft + Offset(0f, cornerLen), strokeW)
                // TopRight
                drawLine(capColor, rect.topRight, rect.topRight - Offset(cornerLen, 0f), strokeW)
                drawLine(capColor, rect.topRight, rect.topRight + Offset(0f, cornerLen), strokeW)
                // BottomLeft
                drawLine(capColor, rect.bottomLeft, rect.bottomLeft + Offset(cornerLen, 0f), strokeW)
                drawLine(capColor, rect.bottomLeft - Offset(0f, cornerLen), rect.bottomLeft, strokeW)
                // BottomRight
                drawLine(capColor, rect.bottomRight, rect.bottomRight - Offset(cornerLen, 0f), strokeW)
                drawLine(capColor, rect.bottomRight - Offset(0f, cornerLen), rect.bottomRight, strokeW)

                if (showConfirm) {
                    val r = 7.dp.toPx()
                    val handleColor = Color.White
                    val alpha = 0.95f
                    drawCircle(handleColor.copy(alpha = alpha), r, rect.topLeft)
                    drawCircle(handleColor.copy(alpha = alpha), r, rect.topRight)
                    drawCircle(handleColor.copy(alpha = alpha), r, rect.bottomLeft)
                    drawCircle(handleColor.copy(alpha = alpha), r, rect.bottomRight)
                    drawCircle(handleColor.copy(alpha = alpha), r, Offset(rect.center.x, rect.top))
                    drawCircle(handleColor.copy(alpha = alpha), r, Offset(rect.center.x, rect.bottom))
                    drawCircle(handleColor.copy(alpha = alpha), r, Offset(rect.left, rect.center.y))
                    drawCircle(handleColor.copy(alpha = alpha), r, Offset(rect.right, rect.center.y))
                }
            }

            // 绘制拖动轨迹（带发光效果）
            if (!showConfirm && points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                 }
                 
                 // 1. 外发光 (Glow)
                 drawPath(
                     path = path, 
                     color = Color(0xFF00E5FF).copy(alpha = 0.4f), 
                     style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                     blendMode = BlendMode.Screen
                 )
                 // 2. 内高亮 (Highlight)
                 drawPath(
                     path = path, 
                     color = Color(0xFF00E5FF).copy(alpha = 0.8f), 
                     style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                     blendMode = BlendMode.Screen
                 )
                 // 3. 核心白光 (Core)
                 drawPath(
                     path = path, 
                     color = Color.White, 
                     style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                 )
            }
            
            // Grid Ripple
            if (showRipple) {
                    val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val p = rippleProgress.value
                    
                    // Grid Configuration
                    val gridSize = 40.dp.toPx()
                    val gridColor = Color.hsv(hueShift, 0.6f, 1f)
                    
                    val currentRadius = (maxRadius * p).coerceAtLeast(1f)
                    val waveWidth = 180f
                    val maxDisplacement = 60f * (1f - p * 0.5f)
                    val visualPeakRadius = currentRadius + maxDisplacement
                    val startFraction = ((visualPeakRadius - waveWidth) / visualPeakRadius).coerceIn(0f, 1f)
                    
                    val rippleBrush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            startFraction to Color.Transparent,
                            (startFraction * 0.3f + 0.7f) to gridColor.copy(alpha = 1f),
                            1f to gridColor.copy(alpha = 0.2f)
                        ),
                        center = center,
                        radius = visualPeakRadius + 20f
                    )

                    // Helper to calculate displaced point
                    fun getDisplacedPoint(x: Float, y: Float): Offset {
                        val dx = x - center.x
                        val dy = y - center.y
                        val dist = hypot(dx, dy)
                        val delta = dist - currentRadius
                        if (kotlin.math.abs(delta) < waveWidth) {
                            val ratio = delta / waveWidth
                            val strength = (1f - ratio * ratio).coerceAtLeast(0f)
                            val disp = maxDisplacement * strength
                            val scale = 1f + disp / dist
                            return Offset(center.x + dx * scale, center.y + dy * scale)
                        }
                        return Offset(x, y)
                    }

                    // Draw Grid
                    val rows = (size.height / gridSize).toInt()
                    val path = Path() 
                    val stepSize = 15f 
                    for (i in 0..rows) {
                        val y = i * gridSize + (size.height % gridSize) / 2f
                        path.reset()
                        val startPt = getDisplacedPoint(0f, y)
                        path.moveTo(startPt.x, startPt.y)
                        var x = stepSize
                        while (x < size.width) {
                            val pt = getDisplacedPoint(x, y)
                            path.lineTo(pt.x, pt.y)
                            x += stepSize
                        }
                        val endPt = getDisplacedPoint(size.width, y)
                        path.lineTo(endPt.x, endPt.y)
                        drawPath(path, rippleBrush, style = Stroke(width = 2.5f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
                    }
                    val cols = (size.width / gridSize).toInt()
                    for (i in 0..cols) {
                        val x = i * gridSize + (size.width % gridSize) / 2f
                        path.reset()
                        val startPt = getDisplacedPoint(x, 0f)
                        path.moveTo(startPt.x, startPt.y)
                        var y = stepSize
                        while (y < size.height) {
                            val pt = getDisplacedPoint(x, y)
                            path.lineTo(pt.x, pt.y)
                            y += stepSize
                        }
                        val endPt = getDisplacedPoint(x, size.height)
                        path.lineTo(endPt.x, endPt.y)
                        drawPath(path, rippleBrush, style = Stroke(width = 2.5f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
                    }
            }
        }

        // 3. 操作按钮：仅在有选区时显示
        if (showConfirm && selectionRect != null) {
            val rect = selectionRect!!
            // 计算按钮位置：在选区底部居中，如果太靠下则放在内部
            val density = androidx.compose.ui.platform.LocalDensity.current
            val btnSize = 56.dp
            val btnSizePx = with(density) { btnSize.toPx() }
            val spacing = 16.dp
            val spacingPx = with(density) { spacing.toPx() }
            
            // 默认显示在 Rect 底部下方
            var btnY = rect.bottom + spacingPx
            // 如果超出屏幕底部，则显示在 Rect 内部底部
            if (btnY + btnSizePx > overlaySize.height) {
                btnY = rect.bottom - btnSizePx - spacingPx
            }
            // 居中 X
            val centerX = rect.left + rect.width / 2f
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, btnY.toInt()) }, // 使用 Absolute offset 可能更简单，这里用box align logic?
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ) {
                 // To position strictly relative to the rect, we might need a Box container or explicit offset.
                 // A modifier.offset with calculation relative to screen 0,0 is easiest if usage is Absolute.
                 // But Row is inside Box(fillMaxSize). So offset is relative to screen.
            }
            
            // 使用 Box + offset 而不是 Row + alignment，因为要精确定位到 Rect 附近
            
            Box(
                modifier = Modifier
                    .offset { IntOffset(centerX.toInt() - (btnSizePx.toInt() * 2 + spacingPx.toInt()) / 2, btnY.toInt()) }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 取消/重选按钮
                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            points.clear()
                            selectionRect = null
                            showConfirm = false
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(Icons.Default.Close, context.getString(R.string.cancel))
                    }

                    // 确认按钮
                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            if (isBusy) return@FloatingActionButton
                            val srcBitmap = screenshotBitmap ?: return@FloatingActionButton
                            val bounds = computeCropBounds(srcBitmap, rect, overlaySize) ?: return@FloatingActionButton

                            fun fmt4(v: Float): String {
                                val scaled = (v * 10000f).roundToInt() / 10000f
                                return scaled.toString()
                            }
                            val cropRight = bounds.left + bounds.width
                            val cropBottom = bounds.top + bounds.height
                            val imgW = srcBitmap.width.toFloat().coerceAtLeast(1f)
                            val imgH = srcBitmap.height.toFloat().coerceAtLeast(1f)
                            val positionInfo =
                                buildString {
                                    append(context.getString(R.string.screen_ocr_position, "screen_selection"))
                                    append("; image_px=${srcBitmap.width}x${srcBitmap.height}")
                                    append(
                                        "; rect_norm=${fmt4(bounds.left / imgW)},${fmt4(bounds.top / imgH)},${fmt4(cropRight / imgW)},${fmt4(cropBottom / imgH)}"
                                    )
                                }
                            
                            isBusy = true
                            floatContext.coroutineScope.launch {
                                try {
                                    val croppedBitmap = withContext(Dispatchers.Default) {
                                        Bitmap.createBitmap(srcBitmap, bounds.left, bounds.top, bounds.width, bounds.height)
                                    }

                                    val ocrText = withContext(Dispatchers.IO) {
                                        OCRUtils.recognizeText(
                                            context = context,
                                            bitmap = croppedBitmap,
                                            quality = OCRUtils.Quality.HIGH
                                        ).trim()
                                    }
                                    
                                    if (ocrText.isBlank()) {
                                        OKToast.warning(context, context.getString(R.string.screen_ocr_no_text_recognized)).show()
                                    }

                                    val content =
                                        buildString {
                                            append(context.getString(R.string.screen_ocr_selection_ocr_header))
                                            append(positionInfo)
                                            append("\n\n")
                                            if (ocrText.isBlank()) {
                                                append(context.getString(R.string.screen_ocr_no_text_recognized))
                                            } else {
                                                append(ocrText)
                                            }
                                            append("\n\n")
                                            append(context.getString(R.string.screen_ocr_inline_instruction))
                                        }
                                    val textAttachment = AttachmentInfo(
                                        filePath = "screen_ocr_${System.currentTimeMillis()}",
                                        fileName = "screen_ocr.txt",
                                        mimeType = "text/plain",
                                        fileSize = content.length.toLong(),
                                        content = content
                                    )

                                    floatContext.chatService
                                        ?.getChatCore()
                                        ?.getAttachmentDelegate()
                                        ?.addAttachments(listOf(textAttachment))
                                    
                                    OKToast.success(context, context.getString(R.string.screen_ocr_selection_content_captured)).show()
                                    
                                    // Set pending flag for Auto-Check in Fullscreen
                                    floatContext.pendingScreenSelection = true
                                    
                                    floatContext.onModeChange(floatContext.previousMode)
                                } catch (e: Exception) {
                                    OKToast.error(context, context.getString(R.string.screen_ocr_error_prefix, e.message ?: "")).show()
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        if (isBusy) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Check, context.getString(R.string.confirm))
                        }
                    }
                }
            }
        }
        
        // 关闭按钮 (Top Left) - 精致化，更小，且带立体感 (Card 风格)
        val cardShape = RoundedCornerShape(8.dp)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(32.dp),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f)
                            )
                        ),
                        shape = cardShape
                    )
                    .clickable {
                        floatContext.onModeChange(floatContext.previousMode)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = context.getString(R.string.close),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
