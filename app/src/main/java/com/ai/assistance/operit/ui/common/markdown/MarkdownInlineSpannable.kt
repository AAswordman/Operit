package com.ai.assistance.operit.ui.common.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.StaticLayout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import com.ai.assistance.operit.ui.common.displays.LatexCache
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.streamnative.NativeMarkdownSplitter
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.ceil
import kotlin.math.floor

private const val TAG = "MarkdownInlineSpannable"
internal const val INLINE_LATEX_PLACEHOLDER = '\uFFFC'
private const val MAX_INLINE_RENDER_DEPTH = 24

/**
 * Places a formula around the surrounding text's visual center instead of aligning the
 * drawable's bottom to the text baseline. ImageSpan.ALIGN_BASELINE treats the bottom of a
 * formula bitmap as its baseline, which shifts fractions, integrals and subscripts upward.
 *
 * The same span is also used for block formulas so both rendering paths derive their height
 * from identical drawable metrics.
 */
internal class LatexDrawableSpan(
    private val drawable: Drawable,
) : ReplacementSpan() {
    private val width = drawable.intrinsicWidth.coerceAtLeast(1)
    private val height = drawable.intrinsicHeight.coerceAtLeast(1)

    init {
        drawable.setBounds(0, 0, width, height)
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fontMetrics: Paint.FontMetricsInt?,
    ): Int {
        fontMetrics?.let { metrics ->
            val textMetrics = paint.fontMetricsInt
            val textCenter = (textMetrics.ascent + textMetrics.descent) / 2f
            val formulaTop = floor(textCenter - height / 2f).toInt()
            val formulaBottom = ceil(textCenter + height / 2f).toInt()

            metrics.ascent = minOf(textMetrics.ascent, formulaTop)
            metrics.descent = maxOf(textMetrics.descent, formulaBottom)
            metrics.top = minOf(textMetrics.top, metrics.ascent)
            metrics.bottom = maxOf(textMetrics.bottom, metrics.descent)
        }
        return width
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        baseline: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val textCenter = (paint.ascent() + paint.descent()) / 2f
        val formulaTop = baseline + textCenter - height / 2f
        canvas.save()
        canvas.translate(x, formulaTop)
        drawable.draw(canvas)
        canvas.restore()
    }
}

private object NestedInlineNodeCache {
    private const val MAX_ENTRIES = 256

    private val cache = LruCache<String, List<MarkdownNodeStable>>(MAX_ENTRIES)

    fun getOrParse(content: String): List<MarkdownNodeStable> {
        if (content.isEmpty()) return emptyList()

        synchronized(cache) {
            cache.get(content)
        }?.let { return it }

        val parsed = NativeMarkdownSplitter.parseInlineToStableNodes(content)
        synchronized(cache) {
            cache.put(content, parsed)
        }
        return parsed
    }
}

private fun inlineCodeBackgroundColor(textColor: Color): Int {
    val backgroundAlpha = if (textColor.luminance() > 0.5f) 0.18f else 0.12f
    return textColor.copy(alpha = backgroundAlpha).toArgb()
}

private class InlineCodeStyleSpan(
    private val textScale: Float,
) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: android.text.TextPaint) {
        applyStyle(textPaint)
    }

    override fun updateMeasureState(textPaint: android.text.TextPaint) {
        applyStyle(textPaint)
    }

    private fun applyStyle(textPaint: android.text.TextPaint) {
        textPaint.typeface = getMarkdownCodeTypeface()
        textPaint.textSize = textPaint.textSize * textScale
        textPaint.isAntiAlias = true
    }
}

private data class InlineCodeMarkerSpan(
    val backgroundColor: Int,
    val textScale: Float,
    val horizontalPaddingPx: Float,
    val verticalInsetPx: Float,
    val cornerRadiusPx: Float,
)

internal data class InlineCodeTextRange(
    val start: Int,
    val end: Int,
)

private fun createInlineCodeStyleSpan(): InlineCodeStyleSpan {
    return InlineCodeStyleSpan(textScale = 0.9f)
}

private fun createInlineCodeMarkerSpan(
    textColor: Color,
    density: Density?
): InlineCodeMarkerSpan {
    val densityScale = density?.density ?: 1f
    return InlineCodeMarkerSpan(
        backgroundColor = inlineCodeBackgroundColor(textColor),
        textScale = 0.9f,
        horizontalPaddingPx = 4f * densityScale,
        verticalInsetPx = 2f * densityScale,
        cornerRadiusPx = 4f * densityScale
    )
}

private fun createInlineCodePaint(basePaint: Paint, marker: InlineCodeMarkerSpan): Paint {
    return Paint(basePaint).apply {
        typeface = getMarkdownCodeTypeface()
        textSize = basePaint.textSize * marker.textScale
        isAntiAlias = true
    }
}

internal fun inlineCodeRangeAt(text: CharSequence, offset: Int): InlineCodeTextRange? {
    val spanned = text as? Spanned ?: return null
    if (spanned.isEmpty()) return null

    val point = offset.coerceIn(0, spanned.length)
    val probe = if (point == spanned.length) (point - 1).coerceAtLeast(0) else point
    val markers = spanned.getSpans(0, spanned.length, InlineCodeMarkerSpan::class.java)
    markers.forEach { marker ->
        val spanStart = spanned.getSpanStart(marker)
        val spanEnd = spanned.getSpanEnd(marker)
        if (
            spanStart >= 0 &&
                spanEnd > spanStart &&
                ((probe >= spanStart && probe < spanEnd) || point == spanEnd)
        ) {
            return InlineCodeTextRange(spanStart, spanEnd)
        }
    }
    return null
}

internal fun selectedTextWithInlineCodeMarkers(
    text: CharSequence,
    start: Int,
    end: Int,
): CharSequence {
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(0, text.length)
    if (safeStart >= safeEnd) return ""

    val spanned = text as? Spanned ?: return text.subSequence(safeStart, safeEnd)
    val markers =
        spanned
            .getSpans(safeStart, safeEnd, InlineCodeMarkerSpan::class.java)
            .mapNotNull { marker ->
                val spanStart = spanned.getSpanStart(marker)
                val spanEnd = spanned.getSpanEnd(marker)
                val selectedStart = maxOf(safeStart, spanStart)
                val selectedEnd = minOf(safeEnd, spanEnd)
                if (spanStart >= 0 && selectedStart < selectedEnd) {
                    InlineCodeTextRange(selectedStart, selectedEnd)
                } else {
                    null
                }
            }
            .sortedBy { it.start }

    if (markers.isEmpty()) {
        return text.subSequence(safeStart, safeEnd)
    }

    return buildString {
        var cursor = safeStart
        markers.forEach { range ->
            if (cursor < range.start) {
                append(text.subSequence(cursor, range.start))
            }
            append('`')
            append(text.subSequence(range.start, range.end))
            append('`')
            cursor = range.end
        }
        if (cursor < safeEnd) {
            append(text.subSequence(cursor, safeEnd))
        }
    }
}

internal fun drawInlineCodeBackgrounds(
    layout: StaticLayout,
    canvas: Canvas,
) {
    val spanned = layout.text as? Spanned ?: return
    val markers = spanned.getSpans(0, spanned.length, InlineCodeMarkerSpan::class.java)
    if (markers.isEmpty()) return

    val paint = layout.paint
    markers.forEach { marker ->
        val spanStart = spanned.getSpanStart(marker)
        val spanEnd = spanned.getSpanEnd(marker)
        if (spanStart < 0 || spanEnd <= spanStart) {
            return@forEach
        }

        val codePaint = createInlineCodePaint(paint, marker)
        val firstLine = layout.getLineForOffset(spanStart)
        val lastLine = layout.getLineForOffset((spanEnd - 1).coerceAtLeast(spanStart))

        for (lineIndex in firstLine..lastLine) {
            val lineStart = layout.getLineStart(lineIndex)
            val lineEnd = layout.getLineEnd(lineIndex)
            val segmentStart = maxOf(spanStart, lineStart)
            val segmentEnd = minOf(spanEnd, lineEnd)
            if (segmentStart >= segmentEnd) {
                continue
            }

            val startX = layout.getPrimaryHorizontal(segmentStart)
            val measuredWidth = codePaint.measureText(spanned, segmentStart, segmentEnd)
            val isRtl = layout.getParagraphDirection(lineIndex) == android.text.Layout.DIR_RIGHT_TO_LEFT
            val rawLeft = if (isRtl) startX - measuredWidth else startX
            val rawRight = if (isRtl) startX else startX + measuredWidth
            val segmentLeft = minOf(rawLeft, rawRight)
            val segmentRight = maxOf(rawLeft, rawRight)

            canvas.drawRoundRect(
                segmentLeft - marker.horizontalPaddingPx,
                layout.getLineTop(lineIndex).toFloat() + marker.verticalInsetPx,
                segmentRight + marker.horizontalPaddingPx,
                layout.getLineBottom(lineIndex).toFloat() - marker.verticalInsetPx,
                marker.cornerRadiusPx,
                marker.cornerRadiusPx,
                Paint().apply {
                    color = marker.backgroundColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            )
        }
    }
}

private fun stripUnderlineDelimiters(content: String): String {
    return if (content.startsWith("__") && content.endsWith("__") && content.length >= 4) {
        content.substring(2, content.length - 2)
    } else {
        content
    }
}

private fun extractInlineLatexContent(content: String): String {
    return when {
        content.startsWith("$$") && content.endsWith("$$") -> content.removeSurrounding("$$")
        content.startsWith("\\[") && content.endsWith("\\]") -> content.removeSurrounding("\\[", "\\]")
        content.startsWith("$") && content.endsWith("$") -> content.removeSurrounding("$")
        content.startsWith("\\(") && content.endsWith("\\)") -> content.removeSurrounding("\\(", "\\)")
        else -> content
    }
}

private fun appendInlineLatexFallback(
    builder: SpannableStringBuilder,
    rawContent: String
) {
    builder.append(rawContent)
}

private fun resolveNestedInlineText(node: MarkdownNodeStable): String {
    return when (node.type) {
        MarkdownProcessorType.LINK -> extractLinkText(node.content)
        MarkdownProcessorType.UNDERLINE -> stripUnderlineDelimiters(node.content)
        MarkdownProcessorType.HTML_BREAK -> "\n"
        else -> node.content
    }
}

private fun resolveNestedInlineChildren(node: MarkdownNodeStable): List<MarkdownNodeStable> {
    if (node.children.isNotEmpty()) {
        return node.children
    }

    val resolvedText = resolveNestedInlineText(node)
    val parsedChildren = NestedInlineNodeCache.getOrParse(resolvedText)
    if (parsedChildren.isSingleSelfReferenceOf(node, resolvedText)) {
        return emptyList()
    }
    return parsedChildren
}

private fun List<MarkdownNodeStable>.isSingleSelfReferenceOf(
    node: MarkdownNodeStable,
    resolvedText: String
): Boolean {
    if (size != 1) return false
    val onlyChild = first()
    return onlyChild.type == node.type &&
        onlyChild.content == resolvedText &&
        onlyChild.children.isEmpty()
}

private fun appendInlineNode(
    builder: SpannableStringBuilder,
    child: MarkdownNodeStable,
    textColor: Color,
    primaryColor: Color,
    density: Density? = null,
    fontSize: TextUnit? = null,
    visitedNodes: Set<MarkdownNodeStable> = emptySet(),
    depth: Int = 0
) {
    if (depth >= MAX_INLINE_RENDER_DEPTH) {
        builder.append(resolveNestedInlineText(child))
        return
    }
    if (child in visitedNodes) {
        builder.append(resolveNestedInlineText(child))
        return
    }
    val nextVisitedNodes = visitedNodes + child
    val nextDepth = depth + 1

    val content = child.content

    when (child.type) {
        MarkdownProcessorType.LINK -> {
            val linkUrl = extractLinkUrl(content)
            val linkText = extractLinkText(content)
            val nestedChildren = resolveNestedInlineChildren(child)
            val start = builder.length
            if (nestedChildren.isNotEmpty()) {
                nestedChildren.forEach {
                    appendInlineNode(
                        builder,
                        it,
                        textColor,
                        primaryColor,
                        density,
                        fontSize,
                        nextVisitedNodes,
                        nextDepth
                    )
                }
            } else {
                builder.append(linkText)
            }
            val end = builder.length
            if (start < end) {
                builder.setSpan(URLSpan(linkUrl), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(
                    ForegroundColorSpan(primaryColor.toArgb()),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        MarkdownProcessorType.BOLD,
        MarkdownProcessorType.ITALIC,
        MarkdownProcessorType.STRIKETHROUGH,
        MarkdownProcessorType.UNDERLINE -> {
            val nestedChildren = resolveNestedInlineChildren(child)
            val fallbackText = resolveNestedInlineText(child)
            val start = builder.length
            if (nestedChildren.isNotEmpty()) {
                nestedChildren.forEach {
                    appendInlineNode(
                        builder,
                        it,
                        textColor,
                        primaryColor,
                        density,
                        fontSize,
                        nextVisitedNodes,
                        nextDepth
                    )
                }
            } else {
                builder.append(fallbackText)
            }
            val end = builder.length
            if (start < end) {
                when (child.type) {
                    MarkdownProcessorType.BOLD ->
                        builder.setSpan(
                            StyleSpan(Typeface.BOLD),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                    MarkdownProcessorType.ITALIC ->
                        builder.setSpan(
                            StyleSpan(Typeface.ITALIC),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                    MarkdownProcessorType.STRIKETHROUGH ->
                        builder.setSpan(
                            StrikethroughSpan(),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                    MarkdownProcessorType.UNDERLINE ->
                        builder.setSpan(
                            UnderlineSpan(),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                    else -> Unit
                }
            }
        }

        MarkdownProcessorType.INLINE_LATEX -> {
            val latexContent = extractInlineLatexContent(content.trim())

            if (density != null && fontSize != null) {
                try {
                    val textSizePx = with(density) { fontSize.toPx() }
                    val drawable =
                        LatexCache.getDrawable(
                            latexContent,
                            JLatexMathDrawable.builder(latexContent)
                                .textSize(textSizePx)
                                .padding(2)
                                .color(textColor.toArgb())
                                .background(0x00000000)
                                .align(JLatexMathDrawable.ALIGN_LEFT)
                        )

                    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

                    val start = builder.length
                    builder.append(INLINE_LATEX_PLACEHOLDER)
                    val end = builder.length
                    builder.setSpan(
                        LatexDrawableSpan(drawable),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Inline LaTeX render failed, fallback to raw text: $latexContent", e)
                    appendInlineLatexFallback(builder, content)
                }
            } else {
                appendInlineLatexFallback(builder, content)
            }
        }

        MarkdownProcessorType.INLINE_CODE -> {
            val start = builder.length
            builder.append(content)
            val end = builder.length
            if (start < end) {
                builder.setSpan(
                    createInlineCodeStyleSpan(),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    createInlineCodeMarkerSpan(textColor, density),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        MarkdownProcessorType.HTML_BREAK -> {
            builder.append('\n')
        }

        else -> {
            builder.append(content)
        }
    }
}

internal fun buildMarkdownInlineSpannableFromChildren(
    children: List<MarkdownNodeStable>,
    textColor: Color,
    primaryColor: Color,
    density: Density? = null,
    fontSize: TextUnit? = null
): SpannableStringBuilder {
    val builder = SpannableStringBuilder()
    children.forEach { child ->
        appendInlineNode(builder, child, textColor, primaryColor, density, fontSize)
    }
    return builder
}

internal fun buildMarkdownInlineSpannableFromText(
    text: String,
    textColor: Color,
    primaryColor: Color,
    density: Density? = null,
    fontSize: TextUnit? = null
): SpannableStringBuilder {
    if (text.isEmpty()) return SpannableStringBuilder()

    val inlineNodes = NestedInlineNodeCache.getOrParse(text)
    if (inlineNodes.isEmpty()) {
        return SpannableStringBuilder(text)
    }

    return buildMarkdownInlineSpannableFromChildren(
        children = inlineNodes,
        textColor = textColor,
        primaryColor = primaryColor,
        density = density,
        fontSize = fontSize
    )
}
