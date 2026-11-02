package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 始终按完整聊天窗口测量，然后整体缩放；预览面板高度不参与菜单坐标计算。 */
@Composable
internal fun StudioPreviewCanvas(
    viewport: IntSize,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = {
        Box(
            Modifier.fillMaxSize()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp)),
        ) { content() }
    }) { measurables, constraints ->
        val available = IntSize(constraints.maxWidth, constraints.maxHeight)
        val transform = studioPreviewTransform(viewport, available)
        val scene = measurables.single().measure(Constraints.fixed(viewport.width, viewport.height))
        layout(available.width, available.height) {
            scene.placeWithLayer(transform.position.x, transform.position.y) {
                scaleX = transform.scale
                scaleY = transform.scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

internal data class StudioPreviewTransform(val scale: Float, val position: IntOffset)

internal fun studioPreviewTransform(viewport: IntSize, available: IntSize): StudioPreviewTransform {
    val scale = minOf(available.width.toFloat() / viewport.width, available.height.toFloat() / viewport.height, 1f)
    return StudioPreviewTransform(
        scale,
        IntOffset(
            ((available.width - viewport.width * scale) / 2f).roundToInt(),
            ((available.height - viewport.height * scale) / 2f).roundToInt(),
        ),
    )
}
