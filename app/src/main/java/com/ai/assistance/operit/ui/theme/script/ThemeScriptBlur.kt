package com.ai.assistance.operit.ui.theme.script

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.theme.ThemeScriptLayer
import kotlin.math.ceil

/** 素材独立录制；只有图层、图片、尺寸或密度改变时重新生成显示列表。 */
@RequiresApi(31)
internal class ThemeBlurRecording {
    private val node = RenderNode("theme-blur")
    private val recordingScope = CanvasDrawScope()
    private var previous: Frame? = null
    private data class Frame(
        val layer: ThemeScriptLayer,
        val image: ThemeScriptImage?,
        val size: Size,
        val density: Float,
        val direction: LayoutDirection,
        val opacity: Float,
    )

    fun draw(scope: DrawScope, layer: ThemeScriptLayer, images: Map<String, ThemeScriptImage>, opacity: Float) {
        if (scope.size.width <= 0f || scope.size.height <= 0f) return
        val frame = Frame(layer, layer.asset?.let(images::get), scope.size, scope.density, scope.layoutDirection, opacity)
        if (frame != previous) {
            val radius = with(scope) { layer.blur.dp.toPx() }
            node.setPosition(0, 0, ceil(scope.size.width).toInt(), ceil(scope.size.height).toInt())
            node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL))
            val canvas = node.beginRecording()
            try {
                // 显式使用独立 CanvasDrawScope，录制内容不包含当前正文或自身渲染节点。
                recordingScope.draw(scope, scope.layoutDirection, Canvas(canvas), scope.size) {
                    clipRect { drawThemeLayer(layer, images, opacity) }
                }
            } finally {
                node.endRecording()
            }
            previous = frame
        }
        scope.drawContext.canvas.nativeCanvas.drawRenderNode(node)
    }

    fun release() {
        node.discardDisplayList()
        previous = null
    }
}
