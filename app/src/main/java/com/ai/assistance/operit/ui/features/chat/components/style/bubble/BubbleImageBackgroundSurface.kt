package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import androidx.collection.LruCache
import com.ai.assistance.operit.ui.features.chat.components.timeline.LocalChatMessageSlice
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bubble image rendering config.
 * Ratios are all normalized to [0f, 1f].
 */
data class BubbleImageStyleConfig(
    val imageUri: String,
    val cropLeftRatio: Float = 0f,
    val cropTopRatio: Float = 0f,
    val cropRightRatio: Float = 0f,
    val cropBottomRatio: Float = 0f,
    val repeatXStartRatio: Float = 0.35f,
    val repeatXEndRatio: Float = 0.65f,
    val repeatYStartRatio: Float = 0.35f,
    val repeatYEndRatio: Float = 0.65f,
    val imageScale: Float = 1f,
    val renderMode: String = BubbleImageRenderMode.TILED_NINE_SLICE,
)

object BubbleImageRenderMode {
    const val TILED_NINE_SLICE = "tiled_nine_slice"
    const val NINE_PATCH = "nine_patch"
}

@Composable
fun BubbleImageBackgroundSurface(
    imageStyle: BubbleImageStyleConfig,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    showSliceGuides: Boolean = false,
    sliceGuideColor: Color = Color.White.copy(alpha = 0.35f),
    content: @Composable BoxScope.() -> Unit,
) {
    val imageBitmap = rememberBubbleImageBitmap(imageStyle.imageUri)
    val slice = LocalChatMessageSlice.current

    Box(
        modifier =
            modifier
                .clip(shape)
                .drawWithContent {
                    imageBitmap?.let { bitmap ->
                        if (imageStyle.renderMode == BubbleImageRenderMode.NINE_PATCH) {
                            drawStretchedNinePatchBubble(bitmap, imageStyle, slice?.first != false, slice?.last != false)
                        } else {
                            drawRepeatedCenterBubble(bitmap, imageStyle, slice?.first != false, slice?.last != false)
                        }
                        if (showSliceGuides) {
                            drawNinePatchSliceGuides(
                                bitmap = bitmap,
                                config = imageStyle,
                                guideColor = sliceGuideColor,
                            )
                        }
                    }
                    drawContent()
                }
                .padding(contentPadding),
        content = content,
    )
}

// 内容块共用有大小边界的位图缓存，避免滑动重入时重复解码同一张背景。
private val bubbleBitmapCache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width.toLong() * value.height * 4L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}
private val bubbleBitmapLoadMutex = Mutex()

@Composable
private fun rememberBubbleImageBitmap(uriString: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(bubbleBitmapCache[uriString]) }

    LaunchedEffect(context, uriString) {
        bitmap =
            withContext(Dispatchers.IO) {
                bubbleBitmapLoadMutex.withLock {
                    bubbleBitmapCache[uriString] ?: runCatching {
                        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                            BitmapFactory.decodeStream(input)?.asImageBitmap()
                        }
                    }.getOrNull()?.also { bubbleBitmapCache.put(uriString, it) }
                }
            }
    }

    return bitmap
}

private data class BubbleSliceLayout(
    val srcX: Int,
    val srcY: Int,
    val srcWidth: Int,
    val srcHeight: Int,
    val leftCapWidth: Int,
    val centerWidth: Int,
    val rightCapWidth: Int,
    val topCapHeight: Int,
    val centerHeight: Int,
    val bottomCapHeight: Int,
)

/** 图片的顶底装饰只画在消息首尾，连续内容块使用相同的中心切片。 */
private fun BubbleSliceLayout.forMessageBlock(first: Boolean, last: Boolean): BubbleSliceLayout {
    val removedTop = if (first) 0 else topCapHeight
    val removedBottom = if (last) 0 else bottomCapHeight
    return copy(
        srcY = srcY + removedTop,
        srcHeight = (srcHeight - removedTop - removedBottom).coerceAtLeast(1),
        topCapHeight = if (first) topCapHeight else 0,
        bottomCapHeight = if (last) bottomCapHeight else 0,
    )
}

private data class BubbleDstSliceLayout(
    val leftDstWidth: Int,
    val rightDstWidth: Int,
    val topDstHeight: Int,
    val bottomDstHeight: Int,
    val centerDstStartX: Int,
    val centerDstEndX: Int,
    val centerDstStartY: Int,
    val centerDstEndY: Int,
    val scaleX: Float,
    val scaleY: Float,
)

private fun buildSliceLayout(bitmap: ImageBitmap, config: BubbleImageStyleConfig): BubbleSliceLayout {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)

    val cropLeft = config.cropLeftRatio.coerceIn(0f, 0.45f)
    val cropTop = config.cropTopRatio.coerceIn(0f, 0.45f)
    val cropRight = config.cropRightRatio.coerceIn(0f, 0.45f)
    val cropBottom = config.cropBottomRatio.coerceIn(0f, 0.45f)

    val srcLeft = (width * cropLeft).roundToInt().coerceIn(0, width - 1)
    val srcTop = (height * cropTop).roundToInt().coerceIn(0, height - 1)
    val srcRight = (width * (1f - cropRight)).roundToInt().coerceIn(srcLeft + 1, width)
    val srcBottom = (height * (1f - cropBottom)).roundToInt().coerceIn(srcTop + 1, height)

    val croppedWidth = (srcRight - srcLeft).coerceAtLeast(1)
    val croppedHeight = (srcBottom - srcTop).coerceAtLeast(1)

    val repeatXStart = config.repeatXStartRatio.coerceIn(0.05f, 0.9f)
    val repeatXEnd = config.repeatXEndRatio.coerceIn(repeatXStart + 0.01f, 0.95f)
    val repeatYStart = config.repeatYStartRatio.coerceIn(0.05f, 0.9f)
    val repeatYEnd = config.repeatYEndRatio.coerceIn(repeatYStart + 0.01f, 0.95f)
    val repeatStartXPx =
        if (croppedWidth < 3) {
            0
        } else {
            (croppedWidth * repeatXStart).roundToInt().coerceIn(1, croppedWidth - 2)
        }
    val repeatEndXPx =
        if (croppedWidth < 3) {
            croppedWidth
        } else {
            (croppedWidth * repeatXEnd).roundToInt().coerceIn(repeatStartXPx + 1, croppedWidth - 1)
        }

    val repeatStartYPx =
        if (croppedHeight < 3) {
            0
        } else {
            (croppedHeight * repeatYStart).roundToInt().coerceIn(1, croppedHeight - 2)
        }
    val repeatEndYPx =
        if (croppedHeight < 3) {
            croppedHeight
        } else {
            (croppedHeight * repeatYEnd).roundToInt().coerceIn(repeatStartYPx + 1, croppedHeight - 1)
        }

    return BubbleSliceLayout(
        srcX = srcLeft,
        srcY = srcTop,
        srcWidth = croppedWidth,
        srcHeight = croppedHeight,
        leftCapWidth = repeatStartXPx,
        centerWidth = max(1, repeatEndXPx - repeatStartXPx),
        rightCapWidth = max(0, croppedWidth - repeatEndXPx),
        topCapHeight = repeatStartYPx,
        centerHeight = max(1, repeatEndYPx - repeatStartYPx),
        bottomCapHeight = max(0, croppedHeight - repeatEndYPx),
    )
}

private fun computeDstSliceLayout(
    layout: BubbleSliceLayout,
    dstWidth: Int,
    dstHeight: Int,
    imageScale: Float,
): BubbleDstSliceLayout {
    // Standard 9-slice: only middle 5 regions are repeated (tiled), not globally stretched.
    // `imageScale` uniformly scales all 9 regions before tiling/clamping.
    val uniformScale = imageScale.coerceIn(0.2f, 3f)
    var leftDstWidth = (layout.leftCapWidth * uniformScale).roundToInt().coerceAtLeast(0)
    var rightDstWidth = (layout.rightCapWidth * uniformScale).roundToInt().coerceAtLeast(0)
    var topDstHeight = (layout.topCapHeight * uniformScale).roundToInt().coerceAtLeast(0)
    var bottomDstHeight = (layout.bottomCapHeight * uniformScale).roundToInt().coerceAtLeast(0)

    if (leftDstWidth + rightDstWidth >= dstWidth) {
        val target = max(0, dstWidth - 1)
        val total = max(1, leftDstWidth + rightDstWidth)
        val ratio = target.toFloat() / total.toFloat()
        leftDstWidth = (leftDstWidth * ratio).roundToInt().coerceAtLeast(0)
        rightDstWidth = (rightDstWidth * ratio).roundToInt().coerceAtLeast(0)
        val overflow = leftDstWidth + rightDstWidth - target
        if (overflow > 0) {
            rightDstWidth = (rightDstWidth - overflow).coerceAtLeast(0)
        }
    }

    if (topDstHeight + bottomDstHeight >= dstHeight) {
        val target = max(0, dstHeight - 1)
        val total = max(1, topDstHeight + bottomDstHeight)
        val ratio = target.toFloat() / total.toFloat()
        topDstHeight = (topDstHeight * ratio).roundToInt().coerceAtLeast(0)
        bottomDstHeight = (bottomDstHeight * ratio).roundToInt().coerceAtLeast(0)
        val overflow = topDstHeight + bottomDstHeight - target
        if (overflow > 0) {
            bottomDstHeight = (bottomDstHeight - overflow).coerceAtLeast(0)
        }
    }

    val centerDstStartX = leftDstWidth
    val centerDstEndX = (dstWidth - rightDstWidth).coerceAtLeast(centerDstStartX)
    val centerDstStartY = topDstHeight
    val centerDstEndY = (dstHeight - bottomDstHeight).coerceAtLeast(centerDstStartY)

    return BubbleDstSliceLayout(
        leftDstWidth = leftDstWidth,
        rightDstWidth = rightDstWidth,
        topDstHeight = topDstHeight,
        bottomDstHeight = bottomDstHeight,
        centerDstStartX = centerDstStartX,
        centerDstEndX = centerDstEndX,
        centerDstStartY = centerDstStartY,
        centerDstEndY = centerDstEndY,
        scaleX = uniformScale,
        scaleY = uniformScale,
    )
}

private fun DrawScope.drawRepeatedCenterBubble(bitmap: ImageBitmap, config: BubbleImageStyleConfig, first: Boolean = true, last: Boolean = true) {
    val layout = buildSliceLayout(bitmap, config).forMessageBlock(first, last)
    val dstWidth = size.width.roundToInt().coerceAtLeast(1)
    val dstHeight = size.height.roundToInt().coerceAtLeast(1)

    if (
        layout.centerWidth <= 0 ||
            layout.centerHeight <= 0 ||
            layout.srcWidth <= 0 ||
            layout.srcHeight <= 0
    ) {
        drawSlice(
            bitmap = bitmap,
            srcX = layout.srcX,
            srcY = layout.srcY,
            srcW = layout.srcWidth,
            srcH = layout.srcHeight,
            dstX = 0,
            dstY = 0,
            dstW = dstWidth,
            dstH = dstHeight,
        )
        return
    }

    val dstLayout = computeDstSliceLayout(
        layout = layout,
        dstWidth = dstWidth,
        dstHeight = dstHeight,
        imageScale = config.imageScale,
    )
    val leftDstWidth = dstLayout.leftDstWidth
    val rightDstWidth = dstLayout.rightDstWidth
    val topDstHeight = dstLayout.topDstHeight
    val bottomDstHeight = dstLayout.bottomDstHeight
    val centerDstStartX = dstLayout.centerDstStartX
    val centerDstEndX = dstLayout.centerDstEndX
    val centerDstStartY = dstLayout.centerDstStartY
    val centerDstEndY = dstLayout.centerDstEndY
    val scaleX = dstLayout.scaleX
    val scaleY = dstLayout.scaleY

    val srcLeftX = layout.srcX
    val srcCenterX = layout.srcX + layout.leftCapWidth
    val srcRightX = layout.srcX + layout.srcWidth - layout.rightCapWidth
    val srcTopY = layout.srcY
    val srcCenterY = layout.srcY + layout.topCapHeight
    val srcBottomY = layout.srcY + layout.srcHeight - layout.bottomCapHeight

    // 4 corners
    drawSlice(
        bitmap = bitmap,
        srcX = srcLeftX,
        srcY = srcTopY,
        srcW = layout.leftCapWidth,
        srcH = layout.topCapHeight,
        dstX = 0,
        dstY = 0,
        dstW = leftDstWidth,
        dstH = topDstHeight,
    )
    drawSlice(
        bitmap = bitmap,
        srcX = srcRightX,
        srcY = srcTopY,
        srcW = layout.rightCapWidth,
        srcH = layout.topCapHeight,
        dstX = dstWidth - rightDstWidth,
        dstY = 0,
        dstW = rightDstWidth,
        dstH = topDstHeight,
    )
    drawSlice(
        bitmap = bitmap,
        srcX = srcLeftX,
        srcY = srcBottomY,
        srcW = layout.leftCapWidth,
        srcH = layout.bottomCapHeight,
        dstX = 0,
        dstY = dstHeight - bottomDstHeight,
        dstW = leftDstWidth,
        dstH = bottomDstHeight,
    )
    drawSlice(
        bitmap = bitmap,
        srcX = srcRightX,
        srcY = srcBottomY,
        srcW = layout.rightCapWidth,
        srcH = layout.bottomCapHeight,
        dstX = dstWidth - rightDstWidth,
        dstY = dstHeight - bottomDstHeight,
        dstW = rightDstWidth,
        dstH = bottomDstHeight,
    )

    // top/bottom edges (tile horizontally)
    drawTiledHorizontally(
        bitmap = bitmap,
        srcX = srcCenterX,
        srcY = srcTopY,
        srcW = layout.centerWidth,
        srcH = layout.topCapHeight,
        dstX = centerDstStartX,
        dstY = 0,
        dstW = (centerDstEndX - centerDstStartX),
        dstH = topDstHeight,
        scaleX = scaleX,
    )
    drawTiledHorizontally(
        bitmap = bitmap,
        srcX = srcCenterX,
        srcY = srcBottomY,
        srcW = layout.centerWidth,
        srcH = layout.bottomCapHeight,
        dstX = centerDstStartX,
        dstY = dstHeight - bottomDstHeight,
        dstW = (centerDstEndX - centerDstStartX),
        dstH = bottomDstHeight,
        scaleX = scaleX,
    )

    // left/right edges (tile vertically)
    drawTiledVertically(
        bitmap = bitmap,
        srcX = srcLeftX,
        srcY = srcCenterY,
        srcW = layout.leftCapWidth,
        srcH = layout.centerHeight,
        dstX = 0,
        dstY = centerDstStartY,
        dstW = leftDstWidth,
        dstH = (centerDstEndY - centerDstStartY),
        scaleY = scaleY,
    )
    drawTiledVertically(
        bitmap = bitmap,
        srcX = srcRightX,
        srcY = srcCenterY,
        srcW = layout.rightCapWidth,
        srcH = layout.centerHeight,
        dstX = dstWidth - rightDstWidth,
        dstY = centerDstStartY,
        dstW = rightDstWidth,
        dstH = (centerDstEndY - centerDstStartY),
        scaleY = scaleY,
    )

    // center (tile in both directions)
    drawTiled2D(
        bitmap = bitmap,
        srcX = srcCenterX,
        srcY = srcCenterY,
        srcW = layout.centerWidth,
        srcH = layout.centerHeight,
        dstX = centerDstStartX,
        dstY = centerDstStartY,
        dstW = (centerDstEndX - centerDstStartX),
        dstH = (centerDstEndY - centerDstStartY),
        scaleX = scaleX,
        scaleY = scaleY,
    )
}

private fun DrawScope.drawStretchedNinePatchBubble(bitmap: ImageBitmap, config: BubbleImageStyleConfig, first: Boolean = true, last: Boolean = true) {
    val layout = buildSliceLayout(bitmap, config).forMessageBlock(first, last)
    val dstWidth = size.width.roundToInt().coerceAtLeast(1)
    val dstHeight = size.height.roundToInt().coerceAtLeast(1)

    if (
        layout.centerWidth <= 0 ||
            layout.centerHeight <= 0 ||
            layout.srcWidth <= 0 ||
            layout.srcHeight <= 0
    ) {
        drawSlice(
            bitmap = bitmap,
            srcX = layout.srcX,
            srcY = layout.srcY,
            srcW = layout.srcWidth,
            srcH = layout.srcHeight,
            dstX = 0,
            dstY = 0,
            dstW = dstWidth,
            dstH = dstHeight,
        )
        return
    }

    val dstLayout = computeDstSliceLayout(
        layout = layout,
        dstWidth = dstWidth,
        dstHeight = dstHeight,
        imageScale = config.imageScale,
    )
    val leftDstWidth = dstLayout.leftDstWidth
    val rightDstWidth = dstLayout.rightDstWidth
    val topDstHeight = dstLayout.topDstHeight
    val bottomDstHeight = dstLayout.bottomDstHeight
    val centerDstStartX = dstLayout.centerDstStartX
    val centerDstEndX = dstLayout.centerDstEndX
    val centerDstStartY = dstLayout.centerDstStartY
    val centerDstEndY = dstLayout.centerDstEndY

    val srcLeftX = layout.srcX
    val srcCenterX = layout.srcX + layout.leftCapWidth
    val srcRightX = layout.srcX + layout.srcWidth - layout.rightCapWidth
    val srcTopY = layout.srcY
    val srcCenterY = layout.srcY + layout.topCapHeight
    val srcBottomY = layout.srcY + layout.srcHeight - layout.bottomCapHeight

    // 4 corners
    drawSlice(
        bitmap = bitmap,
        srcX = srcLeftX,
        srcY = srcTopY,
        srcW = layout.leftCapWidth,
        srcH = layout.topCapHeight,
        dstX = 0,
        dstY = 0,
        dstW = leftDstWidth,
        dstH = topDstHeight,
    )
    drawSlice(
        bitmap = bitmap,
        srcX = srcRightX,
        srcY = srcTopY,
        srcW = layout.rightCapWidth,
        srcH = layout.topCapHeight,
        dstX = dstWidth - rightDstWidth,
        dstY = 0,
        dstW = rightDstWidth,
        dstH = topDstHeight,
    )
    drawSlice(
        bitmap = bitmap,
        srcX = srcLeftX,
        srcY = srcBottomY,
        srcW = layout.leftCapWidth,
        srcH = layout.bottomCapHeight,
        dstX = 0,
        dstY = dstHeight - bottomDstHeight,
        dstW = leftDstWidth,
        dstH = bottomDstHeight,
    )
    drawSlice(
        bitmap = bitmap,
        srcX = srcRightX,
        srcY = srcBottomY,
        srcW = layout.rightCapWidth,
        srcH = layout.bottomCapHeight,
        dstX = dstWidth - rightDstWidth,
        dstY = dstHeight - bottomDstHeight,
        dstW = rightDstWidth,
        dstH = bottomDstHeight,
    )

    // top/bottom edges (stretch horizontally)
    drawSlice(
        bitmap = bitmap,
        srcX = srcCenterX,
        srcY = srcTopY,
        srcW = layout.centerWidth,
        srcH = layout.topCapHeight,
        dstX = centerDstStartX,
        dstY = 0,
        dstW = (centerDstEndX - centerDstStartX),
        dstH = topDstHeight,
    )
    drawSlice(
        bitmap = bitmap,
        srcX = srcCenterX,
        srcY = srcBottomY,
        srcW = layout.centerWidth,
        srcH = layout.bottomCapHeight,
        dstX = centerDstStartX,
        dstY = dstHeight - bottomDstHeight,
        dstW = (centerDstEndX - centerDstStartX),
        dstH = bottomDstHeight,
    )

    // left/right edges (stretch vertically)
    drawSlice(
        bitmap = bitmap,
        srcX = srcLeftX,
        srcY = srcCenterY,
        srcW = layout.leftCapWidth,
        srcH = layout.centerHeight,
        dstX = 0,
        dstY = centerDstStartY,
        dstW = leftDstWidth,
        dstH = (centerDstEndY - centerDstStartY),
    )
    drawSlice(
        bitmap = bitmap,
        srcX = srcRightX,
        srcY = srcCenterY,
        srcW = layout.rightCapWidth,
        srcH = layout.centerHeight,
        dstX = dstWidth - rightDstWidth,
        dstY = centerDstStartY,
        dstW = rightDstWidth,
        dstH = (centerDstEndY - centerDstStartY),
    )

    // center (stretch in both directions)
    drawSlice(
        bitmap = bitmap,
        srcX = srcCenterX,
        srcY = srcCenterY,
        srcW = layout.centerWidth,
        srcH = layout.centerHeight,
        dstX = centerDstStartX,
        dstY = centerDstStartY,
        dstW = (centerDstEndX - centerDstStartX),
        dstH = (centerDstEndY - centerDstStartY),
    )
}

private fun DrawScope.drawNinePatchSliceGuides(
    bitmap: ImageBitmap,
    config: BubbleImageStyleConfig,
    guideColor: Color,
) {
    val layout = buildSliceLayout(bitmap, config)
    val dstWidth = size.width.roundToInt().coerceAtLeast(1)
    val dstHeight = size.height.roundToInt().coerceAtLeast(1)
    val dstLayout = computeDstSliceLayout(
        layout = layout,
        dstWidth = dstWidth,
        dstHeight = dstHeight,
        imageScale = config.imageScale,
    )
    val strokeWidth = 1.dp.toPx().coerceAtLeast(1f)
    val halfStroke = strokeWidth / 2f
    val xMin = halfStroke
    val yMin = halfStroke
    val xMax = (size.width - halfStroke).coerceAtLeast(xMin)
    val yMax = (size.height - halfStroke).coerceAtLeast(yMin)

    val leftX = dstLayout.centerDstStartX.toFloat().coerceIn(xMin, xMax)
    val rightX = dstLayout.centerDstEndX.toFloat().coerceIn(xMin, xMax)
    val topY = dstLayout.centerDstStartY.toFloat().coerceIn(yMin, yMax)
    val bottomY = dstLayout.centerDstEndY.toFloat().coerceIn(yMin, yMax)

    drawLine(
        color = guideColor,
        start = androidx.compose.ui.geometry.Offset(leftX, 0f),
        end = androidx.compose.ui.geometry.Offset(leftX, size.height),
        strokeWidth = strokeWidth,
    )
    drawLine(
        color = guideColor,
        start = androidx.compose.ui.geometry.Offset(rightX, 0f),
        end = androidx.compose.ui.geometry.Offset(rightX, size.height),
        strokeWidth = strokeWidth,
    )
    drawLine(
        color = guideColor,
        start = androidx.compose.ui.geometry.Offset(0f, topY),
        end = androidx.compose.ui.geometry.Offset(size.width, topY),
        strokeWidth = strokeWidth,
    )
    drawLine(
        color = guideColor,
        start = androidx.compose.ui.geometry.Offset(0f, bottomY),
        end = androidx.compose.ui.geometry.Offset(size.width, bottomY),
        strokeWidth = strokeWidth,
    )
}

private fun DrawScope.drawSlice(
    bitmap: ImageBitmap,
    srcX: Int,
    srcY: Int,
    srcW: Int,
    srcH: Int,
    dstX: Int,
    dstY: Int,
    dstW: Int,
    dstH: Int,
) {
    if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return

    val bitmapWidth = bitmap.width
    val bitmapHeight = bitmap.height
    if (bitmapWidth <= 0 || bitmapHeight <= 0) return

    val safeSrcX = srcX.coerceIn(0, bitmapWidth - 1)
    val safeSrcY = srcY.coerceIn(0, bitmapHeight - 1)
    val safeSrcW = min(srcW, bitmapWidth - safeSrcX)
    val safeSrcH = min(srcH, bitmapHeight - safeSrcY)
    if (safeSrcW <= 0 || safeSrcH <= 0) return

    val safeDstW =
        if (safeSrcW == srcW) {
            dstW
        } else {
            max(1, (dstW * (safeSrcW.toFloat() / srcW.toFloat())).roundToInt())
        }
    val safeDstH =
        if (safeSrcH == srcH) {
            dstH
        } else {
            max(1, (dstH * (safeSrcH.toFloat() / srcH.toFloat())).roundToInt())
        }

    drawImage(
        image = bitmap,
        srcOffset = IntOffset(safeSrcX, safeSrcY),
        srcSize = IntSize(safeSrcW, safeSrcH),
        dstOffset = IntOffset(dstX, dstY),
        dstSize = IntSize(safeDstW, safeDstH),
    )
}

private fun DrawScope.drawTiledHorizontally(
    bitmap: ImageBitmap,
    srcX: Int,
    srcY: Int,
    srcW: Int,
    srcH: Int,
    dstX: Int,
    dstY: Int,
    dstW: Int,
    dstH: Int,
    scaleX: Float,
) {
    if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return

    val baseTileDstWidth = max(1, (srcW * scaleX).roundToInt())
    var currentX = dstX
    val dstEnd = dstX + dstW

    while (currentX < dstEnd) {
        val remaining = dstEnd - currentX
        val tileDstWidth = min(baseTileDstWidth, remaining)
        val tileSrcWidth =
            if (tileDstWidth == baseTileDstWidth) {
                srcW
            } else {
                max(1, (srcW * (tileDstWidth.toFloat() / baseTileDstWidth.toFloat())).roundToInt())
            }

        drawSlice(
            bitmap = bitmap,
            srcX = srcX,
            srcY = srcY,
            srcW = tileSrcWidth,
            srcH = srcH,
            dstX = currentX,
            dstY = dstY,
            dstW = tileDstWidth,
            dstH = dstH,
        )

        currentX += tileDstWidth
    }
}

private fun DrawScope.drawTiledVertically(
    bitmap: ImageBitmap,
    srcX: Int,
    srcY: Int,
    srcW: Int,
    srcH: Int,
    dstX: Int,
    dstY: Int,
    dstW: Int,
    dstH: Int,
    scaleY: Float,
) {
    if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return

    val baseTileDstHeight = max(1, (srcH * scaleY).roundToInt())
    var currentY = dstY
    val dstEnd = dstY + dstH

    while (currentY < dstEnd) {
        val remaining = dstEnd - currentY
        val tileDstHeight = min(baseTileDstHeight, remaining)
        val tileSrcHeight =
            if (tileDstHeight == baseTileDstHeight) {
                srcH
            } else {
                max(1, (srcH * (tileDstHeight.toFloat() / baseTileDstHeight.toFloat())).roundToInt())
            }

        drawSlice(
            bitmap = bitmap,
            srcX = srcX,
            srcY = srcY,
            srcW = srcW,
            srcH = tileSrcHeight,
            dstX = dstX,
            dstY = currentY,
            dstW = dstW,
            dstH = tileDstHeight,
        )

        currentY += tileDstHeight
    }
}

private fun DrawScope.drawTiled2D(
    bitmap: ImageBitmap,
    srcX: Int,
    srcY: Int,
    srcW: Int,
    srcH: Int,
    dstX: Int,
    dstY: Int,
    dstW: Int,
    dstH: Int,
    scaleX: Float,
    scaleY: Float,
) {
    if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return

    val baseTileDstWidth = max(1, (srcW * scaleX).roundToInt())
    val baseTileDstHeight = max(1, (srcH * scaleY).roundToInt())
    var currentY = dstY
    val dstEndY = dstY + dstH

    while (currentY < dstEndY) {
        val remainingY = dstEndY - currentY
        val tileDstHeight = min(baseTileDstHeight, remainingY)
        val tileSrcHeight =
            if (tileDstHeight == baseTileDstHeight) {
                srcH
            } else {
                max(1, (srcH * (tileDstHeight.toFloat() / baseTileDstHeight.toFloat())).roundToInt())
            }

        var currentX = dstX
        val dstEndX = dstX + dstW
        while (currentX < dstEndX) {
            val remainingX = dstEndX - currentX
            val tileDstWidth = min(baseTileDstWidth, remainingX)
            val tileSrcWidth =
                if (tileDstWidth == baseTileDstWidth) {
                    srcW
                } else {
                    max(
                        1,
                        (srcW * (tileDstWidth.toFloat() / baseTileDstWidth.toFloat())).roundToInt(),
                    )
                }

            drawSlice(
                bitmap = bitmap,
                srcX = srcX,
                srcY = srcY,
                srcW = tileSrcWidth,
                srcH = tileSrcHeight,
                dstX = currentX,
                dstY = currentY,
                dstW = tileDstWidth,
                dstH = tileDstHeight,
            )

            currentX += tileDstWidth
        }

        currentY += tileDstHeight
    }
}
