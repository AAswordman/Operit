package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** 只描述同一个外壳的可见首尾，不依赖消息解析器或离屏内容的高度。 */
internal data class ThemeSurfaceFragment(val first: Boolean, val last: Boolean) {
    val whole: Boolean get() = first && last

    fun shape(original: CornerBasedShape): CornerBasedShape {
        if (whole) return original
        val zero = CornerSize(0.dp)
        return original.copy(
            topStart = if (first) original.topStart else zero,
            topEnd = if (first) original.topEnd else zero,
            bottomEnd = if (last) original.bottomEnd else zero,
            bottomStart = if (last) original.bottomStart else zero,
        )
    }

    fun shape(original: Shape): Shape =
        if (original is CornerBasedShape) shape(original) else original

    companion object {
        val Whole = ThemeSurfaceFragment(first = true, last = true)
    }
}

internal val LocalThemeSurfaceFragments = staticCompositionLocalOf<Map<String, ThemeSurfaceFragment>> { emptyMap() }

/** 连续段把接缝处的横边放到裁剪区外，左右描边仍保留完整宽度。 */
internal fun Modifier.themeFragmentBorder(border: BorderStroke, shape: Shape, fragment: ThemeSurfaceFragment): Modifier {
    if (fragment.whole) return this.border(border, shape)
    return drawWithCache {
        val width = ceil(border.width.toPx()).coerceAtLeast(1f)
        val before = if (fragment.first) 0f else width * 2f
        val after = if (fragment.last) 0f else width * 2f
        val outline = shape.createOutline(Size(size.width, size.height + before + after), layoutDirection, this)
        onDrawWithContent {
            drawContent()
            clipRect {
                translate(top = -before) {
                    // 外层按同一形状裁剪，双倍居中笔宽只留下内侧的完整描边。
                    drawOutline(outline, border.brush, style = Stroke(width * 2f))
                }
            }
        }
    }
}

internal data class ThemeNineSliceAxis(val source: List<Int>, val destination: List<Float>)

/** 九宫格的中段始终读取同一源区间，首尾装饰只属于整条外壳的两端。 */
internal fun themeNineSliceAxis(
    sourceSize: Int,
    start: Float,
    end: Float,
    extent: Float,
    density: Float,
    fragment: ThemeSurfaceFragment,
): ThemeNineSliceAxis {
    val startCap = if (fragment.first) start else 0f
    val endCap = if (fragment.last) end else 0f
    val scale = if (startCap + endCap > 0f) min(1f, extent / ((startCap + endCap) * density)) else 1f
    return ThemeNineSliceAxis(
        source = listOf(
            if (fragment.first) 0 else start.roundToInt(),
            start.roundToInt(),
            sourceSize - end.roundToInt(),
            if (fragment.last) sourceSize else sourceSize - end.roundToInt(),
        ),
        destination = listOf(0f, startCap * density * scale, extent - endCap * density * scale, extent),
    )
}
