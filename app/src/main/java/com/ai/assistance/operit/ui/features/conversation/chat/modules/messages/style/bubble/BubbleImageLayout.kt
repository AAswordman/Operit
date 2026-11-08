package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble

import kotlin.math.max
import kotlin.math.roundToInt

/** 图片气泡的已保存参数，比例使用 0～1，保留现有配置语义。 */
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
    val backgroundOpacity: Float = 1f,
)

object BubbleImageRenderMode {
    const val TILED_NINE_SLICE = "tiled_nine_slice"
    const val NINE_PATCH = "nine_patch"
}

internal data class BubbleSliceLayout(
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
internal fun BubbleSliceLayout.forMessageBlock(first: Boolean, last: Boolean): BubbleSliceLayout {
    val removedTop = if (first) 0 else topCapHeight
    val removedBottom = if (last) 0 else bottomCapHeight
    return copy(
        srcY = srcY + removedTop,
        srcHeight = (srcHeight - removedTop - removedBottom).coerceAtLeast(1),
        topCapHeight = if (first) topCapHeight else 0,
        bottomCapHeight = if (last) bottomCapHeight else 0,
    )
}

internal data class BubbleDstSliceLayout(
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

internal fun buildSliceLayout(imageWidth: Int, imageHeight: Int, config: BubbleImageStyleConfig): BubbleSliceLayout {
    val width = imageWidth.coerceAtLeast(1)
    val height = imageHeight.coerceAtLeast(1)

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

internal fun computeDstSliceLayout(
    layout: BubbleSliceLayout,
    dstWidth: Int,
    dstHeight: Int,
    imageScale: Float,
): BubbleDstSliceLayout {
    // 保持边角的统一缩放；中间五块由平铺或拉伸模式填充。
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

/** 按原像素取整规则计算平铺周期，最小一像素也直接交给纹理重复。 */
internal fun bubbleTileExtent(sourceExtent: Int, scale: Float): Int =
    max(1, (sourceExtent * scale).roundToInt())
