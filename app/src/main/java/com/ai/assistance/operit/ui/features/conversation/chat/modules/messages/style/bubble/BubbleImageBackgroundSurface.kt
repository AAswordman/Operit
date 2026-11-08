package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.collection.LruCache
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline.LocalChatMessageSlice
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
    val sourceLayout = remember(imageBitmap, imageStyle) {
        imageBitmap?.let { buildSliceLayout(it.width, it.height, imageStyle) }
    }
    val imageTiles = rememberBubbleImageTiles(
        imageBitmap,
        sourceLayout?.takeUnless { imageStyle.renderMode == BubbleImageRenderMode.NINE_PATCH },
    )
    val backgroundOpacity = imageStyle.backgroundOpacity.coerceIn(0f, 1f)
    val backgroundPaint = remember(backgroundOpacity) { Paint().apply { alpha = backgroundOpacity } }
    val slice = LocalChatMessageSlice.current

    Box(
        modifier =
            modifier
                .clip(shape)
                .drawWithContent {
                    if (backgroundOpacity > 0f) {
                        imageBitmap?.let { bitmap ->
                            val canvas = drawContext.canvas
                            if (backgroundOpacity < 1f) {
                                canvas.saveLayer(Rect(Offset.Zero, size), backgroundPaint)
                            }
                            try {
                                if (imageStyle.renderMode == BubbleImageRenderMode.NINE_PATCH) {
                                    drawStretchedNinePatchBubble(bitmap, imageStyle, slice?.first != false, slice?.last != false)
                                } else if (imageTiles != null) {
                                    drawRepeatedCenterBubble(bitmap, imageStyle, imageTiles, slice?.first != false, slice?.last != false)
                                }
                                if (showSliceGuides) {
                                    drawNinePatchSliceGuides(
                                        bitmap = bitmap,
                                        config = imageStyle,
                                        guideColor = sliceGuideColor,
                                    )
                                }
                            } finally {
                                if (backgroundOpacity < 1f) canvas.restore()
                            }
                        }
                    }
                    // 图层仅包含背景，正文和附件保持原有清晰度。
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

/** 四角各画一次，中间五块各用一次纹理填充，极小中段不会增加主线程绘制次数。 */
private fun DrawScope.drawRepeatedCenterBubble(
    bitmap: ImageBitmap,
    config: BubbleImageStyleConfig,
    tiles: BubbleImageTiles,
    first: Boolean = true,
    last: Boolean = true,
) {
    val layout = buildSliceLayout(bitmap.width, bitmap.height, config).forMessageBlock(first, last)
    val dstWidth = size.width.roundToInt().coerceAtLeast(1)
    val dstHeight = size.height.roundToInt().coerceAtLeast(1)
    val dst = computeDstSliceLayout(layout, dstWidth, dstHeight, config.imageScale)
    val rightX = layout.srcX + layout.srcWidth - layout.rightCapWidth
    val bottomY = layout.srcY + layout.srcHeight - layout.bottomCapHeight

    drawSlice(bitmap, layout.srcX, layout.srcY, layout.leftCapWidth, layout.topCapHeight,
        0, 0, dst.leftDstWidth, dst.topDstHeight)
    drawSlice(bitmap, rightX, layout.srcY, layout.rightCapWidth, layout.topCapHeight,
        dstWidth - dst.rightDstWidth, 0, dst.rightDstWidth, dst.topDstHeight)
    drawSlice(bitmap, layout.srcX, bottomY, layout.leftCapWidth, layout.bottomCapHeight,
        0, dstHeight - dst.bottomDstHeight, dst.leftDstWidth, dst.bottomDstHeight)
    drawSlice(bitmap, rightX, bottomY, layout.rightCapWidth, layout.bottomCapHeight,
        dstWidth - dst.rightDstWidth, dstHeight - dst.bottomDstHeight,
        dst.rightDstWidth, dst.bottomDstHeight)

    val centerWidth = dst.centerDstEndX - dst.centerDstStartX
    val centerHeight = dst.centerDstEndY - dst.centerDstStartY
    tiles.top?.draw(this, dst.centerDstStartX, 0, centerWidth, dst.topDstHeight,
        scale = dst.scaleX)
    tiles.bottom?.draw(this, dst.centerDstStartX, dstHeight - dst.bottomDstHeight,
        centerWidth, dst.bottomDstHeight, scale = dst.scaleX)
    tiles.left?.draw(this, 0, dst.centerDstStartY, dst.leftDstWidth, centerHeight,
        scale = dst.scaleY)
    tiles.right?.draw(this, dstWidth - dst.rightDstWidth, dst.centerDstStartY,
        dst.rightDstWidth, centerHeight, scale = dst.scaleY)
    tiles.center.draw(this, dst.centerDstStartX, dst.centerDstStartY, centerWidth, centerHeight,
        scale = dst.scaleX)
}

private fun DrawScope.drawStretchedNinePatchBubble(bitmap: ImageBitmap, config: BubbleImageStyleConfig, first: Boolean = true, last: Boolean = true) {
    val layout = buildSliceLayout(bitmap.width, bitmap.height, config).forMessageBlock(first, last)
    val dstWidth = size.width.roundToInt().coerceAtLeast(1)
    val dstHeight = size.height.roundToInt().coerceAtLeast(1)

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
    val layout = buildSliceLayout(bitmap.width, bitmap.height, config)
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
