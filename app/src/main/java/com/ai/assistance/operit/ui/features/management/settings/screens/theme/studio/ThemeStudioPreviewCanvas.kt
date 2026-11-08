package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.script.LocalThemePreviewFocus
import com.ai.assistance.operit.ui.theme.script.ThemePreviewFocus
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 完整聊天窗口独立测量；小预览满宽滚动，全屏完整适配，均不改变部件坐标。 */
@Composable
internal fun StudioPreviewCanvas(
    viewport: IntSize,
    modifier: Modifier = Modifier,
    fitWidth: Boolean = false,
    focusTarget: String = "",
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier.clipToBounds()) {
        val available = IntSize(constraints.maxWidth, constraints.maxHeight)
        val transform = studioPreviewTransform(viewport, available, fitWidth)
        val focus = if (fitWidth) ThemePreviewFocus(focusTarget, viewport, available) else null
        val sceneModifier = if (fitWidth) Modifier.fillMaxSize().verticalScroll(scrollState) else Modifier.fillMaxSize()
        CompositionLocalProvider(LocalThemePreviewFocus provides focus) {
            Layout(modifier = sceneModifier, content = {
                Box(
                    Modifier.fillMaxSize()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp)),
                ) { content() }
            }) { measurables, _ ->
                val scene = measurables.single().measure(Constraints.fixed(viewport.width, viewport.height))
                // 滚动容器按缩放后的完整画布计量，定位和内容仍使用原始聊天尺寸。
                layout(available.width, transform.contentHeight) {
                    scene.placeWithLayer(transform.position.x, transform.position.y) {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
        }
    }
}

internal data class StudioPreviewTransform(val scale: Float, val position: IntOffset, val contentHeight: Int)

internal fun studioPreviewTransform(viewport: IntSize, available: IntSize, fitWidth: Boolean = false): StudioPreviewTransform {
    val widthScale = available.width.toFloat() / viewport.width
    val scale = if (fitWidth) widthScale else minOf(widthScale, available.height.toFloat() / viewport.height, 1f)
    val remainingHeight = available.height - viewport.height * scale
    return StudioPreviewTransform(
        scale,
        IntOffset(
            if (fitWidth) 0 else ((available.width - viewport.width * scale) / 2f).roundToInt(),
            ((if (fitWidth) remainingHeight.coerceAtLeast(0f) else remainingHeight) / 2f).roundToInt(),
        ),
        if (fitWidth) maxOf(available.height, ceil(viewport.height * scale).toInt()) else available.height,
    )
}
