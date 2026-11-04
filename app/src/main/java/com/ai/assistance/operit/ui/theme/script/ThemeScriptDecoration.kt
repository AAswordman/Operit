package com.ai.assistance.operit.ui.theme.script

import android.os.Build
import com.ai.assistance.operit.ui.theme.script.text.LocalThemeTextStroke
import com.ai.assistance.operit.ui.theme.script.text.themeTextStroke
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
// 可写状态委托会隐式调用 setValue，不能按普通标识符引用删除此导入。
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.theme.script.ThemeScriptLayer
import com.ai.assistance.operit.data.theme.script.forState
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 正文、颜色与装饰共用状态；事件仍由原生部件持有。 */
@Composable
fun ThemeScriptSurface(
    surfaceId: String,
    backgroundOpacity: Float = 1f,
    selected: Boolean = false,
    enabled: Boolean = true,
    error: Boolean = false,
    drawFill: Boolean = true,
    drawStroke: Boolean = true,
    originalShape: Shape = RectangleShape,
    drawElevation: Boolean = true,
    content: @Composable () -> Unit,
) {
    var pressed by remember(surfaceId) { mutableStateOf(false) }
    var focused by remember(surfaceId) { mutableStateOf(false) }
    val states = LocalThemeSurfaceStates.current
    val active = states[surfaceId] ?: themeStateName(pressed, focused, selected, enabled, error)
    CompositionLocalProvider(LocalThemeSurfaceStates provides (states + (surfaceId to active))) {
        val style = themeSurface(surfaceId)
        val images = (LocalThemeScriptState.current as? ThemeScriptState.Ready)?.images.orEmpty()
        val typography = applyThemeTypography(MaterialTheme.typography, surfaceTypography(style), images)
        MaterialTheme(typography = typography) {
            CompositionLocalProvider(
                LocalThemeTextStroke provides themeTextStroke(surfaceId),
                LocalContentColor provides themeScriptContentColor(surfaceId, LocalContentColor.current),
                LocalTextStyle provides themeScriptTextStyle(surfaceId, LocalTextStyle.current),
            ) {
                Box(
                    Modifier.pointerInput(surfaceId) {
                        try {
                            awaitPointerEventScope {
                                while (true) pressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                            }
                        } finally { pressed = false }
                    }.onFocusChanged { focused = it.hasFocus }
                        .themeScriptDecoration(surfaceId, backgroundOpacity, selected, enabled, error, drawFill, drawStroke, originalShape, drawElevation),
                    propagateMinConstraints = true,
                ) { content() }
            }
        }
    }
}

/** 容器填色、正文和图层各自合成透明度，不能用填色 alpha 隐藏素材。 */
@Composable
fun Modifier.themeScriptDecoration(
    surfaceId: String,
    backgroundOpacity: Float = 1f,
    selected: Boolean = false,
    enabled: Boolean = true,
    error: Boolean = false,
    drawFill: Boolean = true,
    drawStroke: Boolean = true,
    originalShape: Shape = RectangleShape,
    drawElevation: Boolean = true,
): Modifier {
    val state = LocalThemeScriptState.current
    if (state !is ThemeScriptState.Ready) return this
    val base = state.appearance.surfaces[surfaceId] ?: return this
    val fragment = LocalThemeSurfaceFragments.current.getOrDefault(surfaceId, ThemeSurfaceFragment.Whole)
    var pressed by remember(surfaceId) { mutableStateOf(false) }
    var focused by remember(surfaceId) { mutableStateOf(false) }
    val active = LocalThemeSurfaceStates.current[surfaceId] ?: themeStateName(pressed, focused, selected, enabled, error)
    val surface = base.forState(active)
    val recordingLayers = surface.layers.mapIndexed { index, layer ->
        key(layer.id, index) {
            val node = remember { if (Build.VERSION.SDK_INT >= 31) ThemeBlurRecording() else null }
            DisposableEffect(node) { onDispose { if (Build.VERSION.SDK_INT >= 31) node?.release() } }
            node
        }
    }
    val opacity = if (surfaceId in LocalThemeHandledOpacity.current) 1f else surface.opacity
    val shape = fragment.shape(if (surface.shape == null) originalShape else appearanceShape(surface.shape))
    val border = if (drawStroke) appearanceBorder(surface, null, backgroundOpacity.coerceIn(0f, 1f)) else null
    val pointer = if (surface.layers.any { it.state == "pressed" } || "pressed" in surface.states) Modifier.pointerInput(surfaceId) {
        try { awaitPointerEventScope { while (true) pressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed } } } finally { pressed = false }
    } else Modifier
    // 最小高度和投影属于整条容器，拆块后不能给每一行再加一份。
    return this.heightIn(min = if (fragment.whole) surface.minHeight.dp else 0.dp)
        .alpha(opacity)
        .then(if (!drawElevation || surface.elevation == null || !fragment.whole) Modifier else Modifier.shadow(surface.elevation.dp, shape, clip = false))
        .clip(shape)
        .then(pointer).onFocusChanged { focused = it.hasFocus }
        // border 在形状内侧绘制，避免居中描边被裁掉半宽；原生边框由宿主选择单独绘制。
        .then(if (border == null) Modifier else Modifier.themeFragmentBorder(border, shape, fragment))
        .drawWithContent {
            fun drawPlacement(placement: String) {
                surface.layers.forEachIndexed { index, layer ->
                    val visible = when (layer.state) {
                        "always" -> true
                        "pressed" -> (pressed || active == "pressed") && enabled
                        "focused" -> focused || active == "focused"
                        "selected" -> selected || active == "selected"
                        "disabled" -> !enabled || active == "disabled"
                        "error" -> error || active == "error"
                        else -> false
                    }
                    if (visible && layer.placement == placement) {
                        if (layer.blur > 0f && Build.VERSION.SDK_INT >= 31) {
                            clipRect { requireNotNull(recordingLayers[index]).draw(this, layer, state.images, backgroundOpacity.coerceIn(0f, 1f), fragment) }
                        } else clipRect { drawThemeLayer(layer, state.images, backgroundOpacity.coerceIn(0f, 1f), fragment = fragment) }
                    }
                }
            }
            if (drawFill) surface.fillColor?.let { drawRect(themeColor(it), alpha = backgroundOpacity.coerceIn(0f, 1f)) }
            drawPlacement("background")
            drawContent()
            drawPlacement("foreground")
        }.padding(
            start = surface.padding.start.dp,
            top = if (fragment.first) surface.padding.top.dp else 0.dp,
            end = surface.padding.end.dp,
            bottom = if (fragment.last) surface.padding.bottom.dp else 0.dp,
        )
}

internal fun DrawScope.drawThemeLayer(
    layer: ThemeScriptLayer,
    images: Map<String, ThemeScriptImage>,
    backgroundOpacity: Float,
    tintColor: Color? = null,
    fragment: ThemeSurfaceFragment = ThemeSurfaceFragment.Whole,
) {
    val b = layer.bounds
    val topLeft = Offset(size.width * b.x, size.height * b.y)
    val extent = Size(size.width * b.width, size.height * b.height)
    if (extent.width <= 0f || extent.height <= 0f) return
    val opacity = layer.opacity * backgroundOpacity
    val width = layer.strokeWidth.dp.toPx()
    val fill = layer.color?.let(::themeColor)
    val stroke = layer.strokeColor?.let(::themeColor)
    val quality = if (layer.sampling == "nearest") FilterQuality.None else FilterQuality.Low
    when (layer.type) {
        "rect" -> {
            val corner = CornerRadius(layer.radius.dp.toPx())
            if (fill != null) drawRoundRect(fill, topLeft, extent, corner, alpha = opacity)
            if (stroke != null && width > 0f) drawRoundRect(stroke, topLeft, extent, corner, alpha = opacity, style = Stroke(width))
        }
        "ellipse" -> {
            if (fill != null) drawOval(fill, topLeft, extent, alpha = opacity)
            if (stroke != null && width > 0f) drawOval(stroke, topLeft, extent, alpha = opacity, style = Stroke(width))
        }
        "line" -> drawLine(requireNotNull(stroke ?: fill), topLeft, topLeft + Offset(extent.width, extent.height), strokeWidth = width.coerceAtLeast(1f), alpha = opacity)
        "nineSlice" -> {
            val bitmap = (images.getValue(requireNotNull(layer.asset)) as ThemeScriptImage.Bitmap).image
            val edge = layer.slices
            val sx = listOf(0, edge.start.roundToInt(), bitmap.width - edge.end.roundToInt(), bitmap.width)
            val vertical = themeNineSliceAxis(bitmap.height, edge.top, edge.bottom, extent.height, density, fragment)
            val sy = vertical.source
            val scaleX = if (edge.start + edge.end > 0f) min(1f, extent.width / (edge.start + edge.end).dp.toPx()) else 1f
            val dx = listOf(topLeft.x, topLeft.x + edge.start.dp.toPx() * scaleX, topLeft.x + extent.width - edge.end.dp.toPx() * scaleX, topLeft.x + extent.width)
            val dy = vertical.destination.map { topLeft.y + it }
            for (row in 0..2) for (column in 0..2) {
                val sw = sx[column + 1] - sx[column]; val sh = sy[row + 1] - sy[row]
                val dw = (dx[column + 1] - dx[column]).roundToInt(); val dh = (dy[row + 1] - dy[row]).roundToInt()
                if (sw > 0 && sh > 0 && dw > 0 && dh > 0) drawImage(bitmap, srcOffset = IntOffset(sx[column], sy[row]), srcSize = IntSize(sw, sh), dstOffset = IntOffset(dx[column].roundToInt(), dy[row].roundToInt()), dstSize = IntSize(dw, dh), alpha = opacity, filterQuality = quality)
            }
        }
        "image" -> {
            val image = images.getValue(requireNotNull(layer.asset))
            val source = when (image) {
                is ThemeScriptImage.Bitmap -> Size(image.image.width.toFloat(), image.image.height.toFloat())
                is ThemeScriptImage.Vector -> Size(image.picture.width.toFloat(), image.picture.height.toFloat())
                is ThemeScriptImage.Font -> error("Font cannot be used as a drawing layer")
            }
            clipRect(topLeft.x, topLeft.y, topLeft.x + extent.width, topLeft.y + extent.height) {
                if (layer.fit == "tile" && image is ThemeScriptImage.Bitmap) {
                    val shader = android.graphics.BitmapShader(image.image.asAndroidBitmap(), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
                    shader.setLocalMatrix(android.graphics.Matrix().apply { setScale(density, density); postTranslate(topLeft.x, topLeft.y) })
                    val paint = imagePaint(opacity, tintColor, layer.sampling).apply { this.shader = shader }
                    drawContext.canvas.nativeCanvas.drawRect(topLeft.x, topLeft.y, topLeft.x + extent.width, topLeft.y + extent.height, paint)
                } else if (layer.fit == "tile") {
                    val tile = Size(source.width.dp.toPx(), source.height.dp.toPx())
                    for (y in 0 until ceil(extent.height / tile.height).toInt()) for (x in 0 until ceil(extent.width / tile.width).toInt()) {
                        drawThemeImage(image, android.graphics.RectF(topLeft.x + x * tile.width, topLeft.y + y * tile.height, topLeft.x + (x + 1) * tile.width, topLeft.y + (y + 1) * tile.height), opacity, tintColor, layer.sampling)
                    }
                } else {
                    val scale = if (layer.fit == "crop") max(extent.width / source.width, extent.height / source.height) else min(extent.width / source.width, extent.height / source.height)
                    val w = if (layer.fit == "fill") extent.width else source.width * scale
                    val h = if (layer.fit == "fill") extent.height else source.height * scale
                    val remainingX = extent.width - w; val remainingY = extent.height - h
                    val left = topLeft.x + when (layer.alignment) { "start" -> if (layoutDirection == LayoutDirection.Ltr) 0f else remainingX; "end" -> if (layoutDirection == LayoutDirection.Ltr) remainingX else 0f; else -> remainingX / 2f }
                    val top = topLeft.y + when (layer.alignment) { "top" -> 0f; "bottom" -> remainingY; else -> remainingY / 2f }
                    drawThemeImage(image, android.graphics.RectF(left, top, left + w, top + h), opacity, tintColor, layer.sampling)
                }
            }
        }
    }
}

private fun imagePaint(opacity: Float, tint: Color?, sampling: String) = android.graphics.Paint().apply {
    alpha = (opacity * 255).roundToInt().coerceIn(0, 255)
    isFilterBitmap = sampling != "nearest"
    tint?.let { colorFilter = android.graphics.PorterDuffColorFilter(it.toArgb(), android.graphics.PorterDuff.Mode.SRC_IN) }
}

private fun DrawScope.drawThemeImage(image: ThemeScriptImage, rect: android.graphics.RectF, opacity: Float, tint: Color?, sampling: String) {
    val canvas = drawContext.canvas.nativeCanvas
    val paint = imagePaint(opacity, tint, sampling)
    when (image) {
        is ThemeScriptImage.Bitmap -> canvas.drawBitmap(image.image.asAndroidBitmap(), null, rect, paint)
        is ThemeScriptImage.Vector -> { val save = canvas.saveLayer(rect, paint); try { canvas.drawPicture(image.picture, rect) } finally { canvas.restoreToCount(save) } }
        is ThemeScriptImage.Font -> error("Font cannot be painted as an image")
    }
}
