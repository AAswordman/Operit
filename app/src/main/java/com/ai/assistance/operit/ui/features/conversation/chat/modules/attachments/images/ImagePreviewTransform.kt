package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

internal data class ImagePreviewTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

/** 按手势焦点缩放，并根据图片实际适配尺寸限制拖动，避免图片被拖出视口。 */
internal fun transformImagePreview(
    current: ImagePreviewTransform,
    targetScale: Float,
    focus: Offset,
    pan: Offset,
    viewport: Size,
    image: Size,
): ImagePreviewTransform {
    require(viewport.width > 0f && viewport.height > 0f)
    require(image.width > 0f && image.height > 0f)
    val scale = targetScale.coerceIn(1f, 6f)
    val center = Offset(viewport.width / 2f, viewport.height / 2f)
    val relativeFocus = focus - center
    val offset = (current.offset - relativeFocus) * (scale / current.scale) + relativeFocus + pan
    val fitScale = min(viewport.width / image.width, viewport.height / image.height)
    val maxX = ((image.width * fitScale * scale - viewport.width) / 2f).coerceAtLeast(0f)
    val maxY = ((image.height * fitScale * scale - viewport.height) / 2f).coerceAtLeast(0f)
    return ImagePreviewTransform(
        scale = scale,
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY)),
    )
}
