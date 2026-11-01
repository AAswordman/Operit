package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.theme.ThemeScriptBounds
import com.ai.assistance.operit.data.model.theme.ThemeScriptInsets
import com.ai.assistance.operit.ui.theme.script.ThemeScriptImage
import com.ai.assistance.operit.ui.theme.script.drawThemeLayer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** 切线直接编辑源图像像素，并使用同一原生九宫格绘制函数展示拉伸结果。 */
@Composable
internal fun StudioNineSliceEditor(state: ThemeStudioState, index: Int, onClose: () -> Unit) {
    val layer = state.surface().layers[index]
    val bitmap = (state.images.getValue(requireNotNull(layer.asset)) as ThemeScriptImage.Bitmap).image
    val current by rememberUpdatedState(layer.slices)
    fun setEdge(side: String, value: Float) {
        state.updateLayer(index) { old ->
            val e = old.slices
            val maximum = min(2048f, when (side) { "start" -> bitmap.width - e.end - 1; "end" -> bitmap.width - e.start - 1; "top" -> bitmap.height - e.bottom - 1; else -> bitmap.height - e.top - 1 }.toFloat())
            val n = value.roundToInt().toFloat().coerceIn(0f, maximum.coerceAtLeast(0f))
            val next = when (side) { "start" -> e.copy(start = n); "end" -> e.copy(end = n); "top" -> e.copy(top = n); else -> e.copy(bottom = n) }
            old.copy(type = "nineSlice", slices = next)
        }
    }
    fun fit(w: Float, h: Float): Rect { val scale = min(w / bitmap.width, h / bitmap.height); val width = bitmap.width * scale; val height = bitmap.height * scale; return Rect((w - width) / 2f, (h - height) / 2f, (w + width) / 2f, (h + height) / 2f) }
    StudioFullSheet(stringResource(R.string.studio_nine_slice), onClose) {
        StudioHint(stringResource(R.string.studio_nine_slice_hint))
        Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(bitmap) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val rect = fit(size.width.toFloat(), size.height.toFloat())
                val e = current
                val lines = listOf("start" to abs(down.position.x - rect.left - rect.width * e.start / bitmap.width), "end" to abs(down.position.x - rect.right + rect.width * e.end / bitmap.width), "top" to abs(down.position.y - rect.top - rect.height * e.top / bitmap.height), "bottom" to abs(down.position.y - rect.bottom + rect.height * e.bottom / bitmap.height))
                val nearest = lines.minBy { it.second }
                if (rect.contains(down.position) && nearest.second <= 24.dp.toPx()) {
                    down.consume(); state.beginGesture()
                    fun update(point: Offset) {
                        val value = when (nearest.first) { "start" -> (point.x - rect.left) / rect.width * bitmap.width; "end" -> (rect.right - point.x) / rect.width * bitmap.width; "top" -> (point.y - rect.top) / rect.height * bitmap.height; else -> (rect.bottom - point.y) / rect.height * bitmap.height }
                        setEdge(nearest.first, value)
                    }
                    update(down.position)
                    try { drag(down.id) { change -> change.consume(); update(change.position) } } finally { state.endGesture() }
                }
            }
        }) {
            val rect = fit(size.width, size.height)
            drawImage(bitmap, dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()), dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()))
            val e = layer.slices
            val color = Color(0xFF48E1B9)
            val left = rect.left + rect.width * e.start / bitmap.width; val right = rect.right - rect.width * e.end / bitmap.width
            val top = rect.top + rect.height * e.top / bitmap.height; val bottom = rect.bottom - rect.height * e.bottom / bitmap.height
            drawLine(color, Offset(left, rect.top), Offset(left, rect.bottom), 2.dp.toPx()); drawLine(color, Offset(right, rect.top), Offset(right, rect.bottom), 2.dp.toPx())
            drawLine(color, Offset(rect.left, top), Offset(rect.right, top), 2.dp.toPx()); drawLine(color, Offset(rect.left, bottom), Offset(rect.right, bottom), 2.dp.toPx())
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            listOf("start" to R.string.studio_start, "top" to R.string.studio_top, "end" to R.string.studio_end, "bottom" to R.string.studio_bottom).forEach { (side, label) ->
                val e = layer.slices
                val value = when (side) { "start" -> e.start; "top" -> e.top; "end" -> e.end; else -> e.bottom }
                val maximum = min(2048f, when (side) { "start" -> bitmap.width - e.end - 1; "end" -> bitmap.width - e.start - 1; "top" -> bitmap.height - e.bottom - 1; else -> bitmap.height - e.top - 1 }.toFloat()).coerceAtLeast(0f)
                StudioSlider(state, stringResource(label), value, 0f..maximum, "px") { setEdge(side, it) }
            }
            Canvas(Modifier.fillMaxWidth().height(66.dp)) { drawThemeLayer(layer.copy(type = "nineSlice", bounds = ThemeScriptBounds(), blur = 0f), state.images, 1f) }
            Button(onClick = { state.updateLayer(index) { it.copy(type = "nineSlice") }; onClose() }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Text(stringResource(R.string.studio_done)) }
        }
    }
}
