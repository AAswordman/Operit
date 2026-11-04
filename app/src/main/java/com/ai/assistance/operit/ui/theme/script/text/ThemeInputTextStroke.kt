package com.ai.assistance.operit.ui.theme.script.text

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.UiComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val outlineDirections = List(32) { index ->
    val angle = index * Math.PI / 16.0
    cos(angle).toFloat() to sin(angle).toFloat()
}

/**
 * 只包裹内部文字编辑器，复用绘制层扩张文字蒙版，滚动与输入法仍由原生编辑器持有。
 * 函数与插槽显式限定为 UI 目标，避免多层装饰器转发时让 Compose 编译器循环推断。
 */
@Composable
@UiComposable
internal fun ThemeInputTextStroke(stroke: ResolvedThemeTextStroke?, content: @Composable @UiComposable () -> Unit) {
    if (stroke == null) {
        content()
    } else {
        val layer = rememberGraphicsLayer()
        val paint = remember(stroke.color) { Paint().apply { colorFilter = ColorFilter.tint(stroke.color, BlendMode.SrcIn) } }
        Box(Modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            val radius = stroke.widthDp.dp.toPx()
            drawContext.canvas.saveLayer(Rect(-radius, -radius, size.width + radius, size.height + radius), paint)
            // 多圈采样填满轮廓内部，细字和较宽描边不会产生分离的影子。
            for (ring in 1..4) {
                val distance = radius * ring / 4f
                outlineDirections.forEach { (x, y) ->
                    translate(x * distance, y * distance) { drawLayer(layer) }
                }
            }
            drawContext.canvas.restore()
            drawContent()
        }, propagateMinConstraints = true) { content() }
    }
}
