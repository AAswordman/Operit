package com.ai.assistance.operit.ui.theme.script.text

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private data class StrokeLayoutKey(val layout: StaticLayout, val color: Int, val width: Float)
private val strokeLayouts = LruCache<StrokeLayoutKey, StaticLayout>(32)

/** 行内图片保留测量位置，文字轮廓这一遍不重复绘制图片。 */
private class OutlinePlaceholder(private val original: ReplacementSpan) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        original.getSize(paint, text, start, end, fm)
    override fun draw(canvas: android.graphics.Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint) = Unit
}

private fun strokePaint(original: Paint, stroke: ResolvedThemeTextStroke, widthPx: Float): TextPaint =
    TextPaint(original).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthPx * 2f
        strokeJoin = Paint.Join.ROUND
        clearShadowLayer()
        color = android.graphics.Color.BLACK
        // 链接和强调文本仍保留原有填充色，这一遍统一使用独立描边色。
        colorFilter = PorterDuffColorFilter(stroke.color.toArgb(), PorterDuff.Mode.SRC_IN)
    }

private fun outlineLayout(layout: StaticLayout, stroke: ResolvedThemeTextStroke, widthPx: Float): StaticLayout {
    val key = StrokeLayoutKey(layout, stroke.color.toArgb(), widthPx)
    strokeLayouts.get(key)?.let { return it }
    val text = SpannableString(layout.text)
    text.getSpans(0, text.length, Any::class.java).forEach { span ->
        when (span) {
            is ForegroundColorSpan, is ClickableSpan, is BackgroundColorSpan, is UnderlineSpan, is StrikethroughSpan -> text.removeSpan(span)
            is ReplacementSpan -> {
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                val flags = text.getSpanFlags(span)
                text.removeSpan(span)
                text.setSpan(OutlinePlaceholder(span), start, end, flags)
            }
        }
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, strokePaint(layout.paint, stroke, widthPx), layout.width)
        .setAlignment(layout.alignment)
        .setLineSpacing(layout.spacingAdd, layout.spacingMultiplier)
        .setIncludePad(false)
        .build().also { strokeLayouts.put(key, it) }
}

/** 使用原有布局坐标双遍绘制，流式裁剪和链接命中继续由原渲染器持有。 */
fun DrawScope.drawThemeTextLayout(layout: StaticLayout, canvas: android.graphics.Canvas, textStroke: ResolvedThemeTextStroke?) {
    if (textStroke != null) outlineLayout(layout, textStroke, textStroke.widthDp.dp.toPx()).draw(canvas)
    layout.draw(canvas)
}

fun drawThemeNativeText(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, paint: Paint,
    textStroke: ResolvedThemeTextStroke?, density: Float) {
    if (textStroke != null) canvas.drawText(text, x, y, strokePaint(paint, textStroke, textStroke.widthDp * density))
    canvas.drawText(text, x, y, paint)
}

fun drawThemeNativeText(canvas: android.graphics.Canvas, text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint,
    textStroke: ResolvedThemeTextStroke?, density: Float) {
    if (textStroke != null) canvas.drawText(text, start, end, x, y, strokePaint(paint, textStroke, textStroke.widthDp * density))
    canvas.drawText(text, start, end, x, y, paint)
}

/** 工具行等 Compose Canvas 文本使用同一份排版结果，文字填充与轮廓分别着色。 */
fun DrawScope.drawThemeText(
    textLayoutResult: TextLayoutResult,
    color: Color = Color.Unspecified,
    topLeft: Offset = Offset.Zero,
    alpha: Float = Float.NaN,
    shadow: Shadow? = null,
    textDecoration: TextDecoration? = null,
    drawStyle: DrawStyle? = null,
    textStroke: ResolvedThemeTextStroke? = null,
) {
    if (textStroke != null) drawThemeTextStroke(textLayoutResult, textStroke, topLeft, alpha)
    drawText(textLayoutResult, color = color, topLeft = topLeft, alpha = alpha, shadow = shadow,
        textDecoration = textDecoration, drawStyle = drawStyle)
}

/** 颜色过滤统一轮廓色，避免富文本自身颜色覆盖独立描边色。 */
internal fun DrawScope.drawThemeTextStroke(
    layout: TextLayoutResult,
    stroke: ResolvedThemeTextStroke,
    topLeft: Offset = Offset.Zero,
    alpha: Float = Float.NaN,
) {
    val width = stroke.widthDp.dp.toPx()
    drawContext.canvas.saveLayer(Rect(-width, -width, size.width + width, size.height + width), stroke.tintPaint)
    drawText(layout, color = Color.Black, topLeft = topLeft, alpha = alpha,
        drawStyle = Stroke(width * 2f, join = StrokeJoin.Round))
    drawContext.canvas.restore()
}
