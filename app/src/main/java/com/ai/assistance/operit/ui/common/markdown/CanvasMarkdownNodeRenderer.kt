package com.ai.assistance.operit.ui.common.markdown

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import com.ai.assistance.operit.util.AppLogger
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.common.displays.LatexCache
import com.ai.assistance.operit.ui.theme.LocalAiMarkdownTextLayoutSettings
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.floor

private const val TAG = "CanvasMarkdownRenderer"
private const val MAX_CANVAS_HEIGHT_PX = 250_000f
private const val MAX_COMPOSE_CONSTRAINT_HEIGHT_PX = 262_000f
private const val TYPEWRITER_WINDOW_MS = 200
private const val DEFAULT_CANVAS_LINE_SPACING_MULTIPLIER = 1.3f
private const val DEFAULT_PARAGRAPH_BREAK_DP = 4f

private const val FALLBACK_MAX_TEXT_CHARS = 20_000

/**
 * 通用性能优化 Modifier：仅在组件进入屏幕可见区域时才进行绘制。
 * 
 * 实现原理：
 * 1.  使用 `onGloballyPositioned` 监听组件的布局位置和大小。
 * 2.  获取 `LocalView.current.getGlobalVisibleRect()` 来确定当前窗口的可见区域。
 * 3.  通过 `layoutCoordinates.boundsInWindow()` 获取组件在窗口中的边界。
 * 4.  比较组件边界和窗口可见边界，判断组件是否应该被渲染。
 * 5.  使用 `drawWithContent`，如果组件不可见，则跳过其内容的绘制阶段，但其空间占用不变。
 *
 * @return 返回一个配置好的 Modifier。
 */
@Composable
private fun Modifier.drawOnlyWhenVisible(): Modifier {
    var isVisible by remember { mutableStateOf(false) }
    val view = LocalView.current

    return this
            .onGloballyPositioned { layoutCoordinates ->
                val windowRect = android.graphics.Rect()
                // getGlobalVisibleRect 提供了视图在全局坐标系中的可见部分。
                // 对于根视图，这给了我们应用窗口的可见部分。
                view.getGlobalVisibleRect(windowRect)

                val componentBounds = layoutCoordinates.boundsInWindow()

                // 检查组件的垂直边界是否与窗口的可见垂直边界重叠。
                // 这是检查可滚动列表中可见性的一个简单而有效的方法。
                val newVisibility = componentBounds.top < windowRect.bottom && componentBounds.bottom > windowRect.top

                if (newVisibility != isVisible) {
                    isVisible = newVisibility
                }
            }
            .drawWithContent {
                // 仅当可组合项可见时才绘制内容。
                if (isVisible) {
                    drawContent()
                }
            }
}

/** 扩展函数：去除字符串首尾的所有空白字符 */
private fun String.trimAll(): String {
    return this.trim { it.isWhitespace() }
}

private fun CharSequence.trimAllPreservingSpans(): CharSequence {
    var start = 0
    var end = length

    while (start < end && this[start].isWhitespace()) {
        start++
    }

    while (end > start && this[end - 1].isWhitespace()) {
        end--
    }

    return if (start == 0 && end == length) this else subSequence(start, end)
}

private fun splitPlainTextParagraphs(content: CharSequence): List<CharSequence> {
    if (content.isEmpty()) return emptyList()

    val rawText = content.toString()
    val paragraphs = mutableListOf<CharSequence>()
    var paragraphStart = 0
    var index = 0

    while (index < rawText.length) {
        if (rawText[index] != '\n') {
            index++
            continue
        }

        val newlineRunStart = index
        while (index < rawText.length && rawText[index] == '\n') {
            index++
        }

        if (index - newlineRunStart >= 2) {
            if (newlineRunStart > paragraphStart) {
                paragraphs += content.subSequence(paragraphStart, newlineRunStart)
            }
            paragraphStart = index
        }
    }

    if (paragraphStart < content.length) {
        paragraphs += content.subSequence(paragraphStart, content.length)
    }

    return paragraphs.filter { it.isNotEmpty() }
}

/**
 * Paint 对象池 - 避免重复创建相同样式的 Paint
 */
private object PaintCache {
    private data class PaintKey(
        val colorArgb: Int,
        val textSize: Float,
        val typeface: Typeface,
        val letterSpacingEm: Float = 0f
    )

    private val paintCache = ConcurrentHashMap<PaintKey, android.graphics.Paint>()
    private val textPaintCache = ConcurrentHashMap<PaintKey, TextPaint>()

    fun getPaint(color: Color, textSize: Float, typeface: Typeface): android.graphics.Paint {
        val key = PaintKey(color.toArgb(), textSize, typeface)
        return paintCache.getOrPut(key) {
            android.graphics.Paint().apply {
                this.color = key.colorArgb
                this.textSize = textSize
                this.isAntiAlias = true
                this.typeface = key.typeface
            }
        }
    }

    fun getTextPaint(
        color: Color,
        textSize: Float,
        typeface: Typeface,
        letterSpacingEm: Float
    ): TextPaint {
        val key = PaintKey(color.toArgb(), textSize, typeface, letterSpacingEm)
        return textPaintCache.getOrPut(key) {
            TextPaint().apply {
                this.color = key.colorArgb
                this.textSize = textSize
                this.isAntiAlias = true
                this.typeface = key.typeface
                this.letterSpacing = key.letterSpacingEm
            }
        }
    }

    fun clear() {
        paintCache.clear()
        textPaintCache.clear()
    }
}

/**
 * StaticLayout 缓存 - 避免重复创建相同的 StaticLayout
 * 使用 LRU 策略，最多缓存 100 个
 */
private fun safeLayoutWidth(width: Int): Int = width.coerceAtLeast(1)

private fun calculateCanvasLetterSpacingEm(fontSize: TextUnit, letterSpacingSp: Float): Float {
    val fontSizeSp = fontSize.value
    if (!java.lang.Float.isFinite(fontSizeSp) || fontSizeSp <= 0f) {
        return 0f
    }
    return letterSpacingSp / fontSizeSp
}

private fun calculateCanvasLineSpacingMultiplier(lineHeightMultiplier: Float): Float {
    return DEFAULT_CANVAS_LINE_SPACING_MULTIPLIER * lineHeightMultiplier
}

private fun createSafeInlineStaticLayout(
    children: List<MarkdownNodeStable>,
    fallbackText: CharSequence,
    textColor: Color,
    primaryColor: Color,
    density: Density?,
    fontSize: TextUnit?,
    textPaint: TextPaint,
    width: Int,
    lineSpacingMultiplier: Float,
    contextLabel: String
): StaticLayout {
    return try {
        val spannable = buildSpannableFromChildren(
            children = children,
            textColor = textColor,
            primaryColor = primaryColor,
            density = density,
            fontSize = fontSize
        )
        createStaticLayout(spannable, textPaint, width, lineSpacingMultiplier)
    } catch (t: Throwable) {
        AppLogger.w(TAG, "Inline markdown layout failed in $contextLabel, fallback to plain text", t)
        createStaticLayout(fallbackText, textPaint, width, lineSpacingMultiplier)
    }
}

private object LayoutCache {
    private data class LayoutKey(
        val text: String,
        val colorArgb: Int,
        val textSize: Float,
        val width: Int,
        val typeface: Typeface,
        val letterSpacing: Float,
        val lineSpacingMultiplier: Float
    )

    private val cache = LruCache<LayoutKey, StaticLayout>(100)

    fun getLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        color: Color,
        typeface: Typeface,
        lineSpacingMultiplier: Float
    ): StaticLayout {
        val safeWidth = safeLayoutWidth(width)
        val key = LayoutKey(
            text = text,
            colorArgb = color.toArgb(),
            textSize = paint.textSize,
            width = safeWidth,
            typeface = paint.typeface,
            letterSpacing = paint.letterSpacing,
            lineSpacingMultiplier = lineSpacingMultiplier
        )

        return cache.get(key) ?: createStaticLayout(text, paint, safeWidth, lineSpacingMultiplier).also {
            cache.put(key, it)
        }
    }

    fun clear() {
        cache.evictAll()
    }

    private fun createStaticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        lineSpacingMultiplier: Float
    ): StaticLayout {
        val safeWidth = safeLayoutWidth(width)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineSpacingMultiplier)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                paint,
                safeWidth,
                android.text.Layout.Alignment.ALIGN_NORMAL,
                lineSpacingMultiplier,
                0f,
                false
            )
        }
    }
}

/**
 * 绘制指令 - 用于描述如何在 Canvas 上绘制内容
 */
internal sealed class DrawInstruction {
    data class Text(
        val text: String,
        val x: Float,
        val y: Float,
        val paint: android.graphics.Paint
    ) : DrawInstruction()
    
    data class Line(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val paint: android.graphics.Paint
    ) : DrawInstruction()
    
    data class TextLayout(
        val layout: StaticLayout,
        val x: Float,
        val y: Float,
        val text: CharSequence? = null // 添加字段以存储Spannable
    ) : DrawInstruction()
}

/**
 * 布局计算结果 - 包含高度、实际使用的宽度和绘制指令
 */
private data class LayoutResult(
    val height: Float,
    val actualWidth: Float,  // 实际使用的最大宽度
    val instructions: List<DrawInstruction>
)

/**
 * 从绘制指令中提取文本内容用于无障碍朗读
 */
private fun extractAccessibleText(instructions: List<DrawInstruction>): String {
    return buildString {
        instructions.forEach { instruction ->
            when (instruction) {
                is DrawInstruction.Text -> {
                    if (isNotEmpty()) append(" ")
                    append(instruction.text)
                }
                is DrawInstruction.TextLayout -> {
                    if (isNotEmpty()) append(" ")
                    // 优先使用原始文本，否则从layout中提取
                    if (instruction.text != null) {
                        append(instruction.text.toString())
                    } else {
                        append(instruction.layout.text.toString())
                    }
                }
                is DrawInstruction.Line -> {
                    // 线条不需要朗读
                }
            }
        }
    }.trim()
}

/**
 * Canvas 版本的 Markdown 节点渲染器
 * 
 * 优化策略：
 * - 使用单个大 Canvas 绘制所有简单文本（标题、段落、列表项）
 * - 复杂组件（代码块、表格、LaTeX）保留原有的 Compose 组件
 * - 最大程度减少组件数量，提高流式渲染性能
 * 
 * 稳定性优化：
 * - 使用 remember 缓存字体大小，避免每次从 MaterialTheme 读取
 * - 稳定化 lambda 参数，减少不必要的 recompose
 */
@Composable
internal fun CanvasMarkdownNodeRenderer(
    nodeKey: String,
    node: MarkdownNodeStable,
    textColor: Color,
    fontSize: TextUnit = Unspecified,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    index: Int,
    xmlRenderer: XmlContentRenderer,
    xmlStream: Stream<String>? = null,
    enableDialogs: Boolean = true,
    fillMaxWidth: Boolean = true,
    isLastNode: Boolean = false
) {
    
    val density = LocalDensity.current
    
    // 缓存字体大小 - 避免每次 recompose 都从 MaterialTheme 读取
    // 只有当 MaterialTheme 真正变化时才会重新计算
    val typography = MaterialTheme.typography
    val fontSizes = remember(typography, fontSize) {
        val defaultBodySize = typography.bodyMedium.fontSize
        val scale =
            if (
                fontSize == Unspecified ||
                    !defaultBodySize.value.isFinite() ||
                    defaultBodySize.value <= 0f ||
                    !fontSize.value.isFinite() ||
                    fontSize.value <= 0f
            ) {
                1f
            } else {
                fontSize.value / defaultBodySize.value
            }
        FontSizes(
            bodyMedium = scaleMarkdownTextUnit(typography.bodyMedium.fontSize, scale),
            headlineLarge = scaleMarkdownTextUnit(typography.headlineLarge.fontSize, scale),
            headlineMedium = scaleMarkdownTextUnit(typography.headlineMedium.fontSize, scale),
            headlineSmall = scaleMarkdownTextUnit(typography.headlineSmall.fontSize, scale),
            titleLarge = scaleMarkdownTextUnit(typography.titleLarge.fontSize, scale),
            titleMedium = scaleMarkdownTextUnit(typography.titleMedium.fontSize, scale),
            titleSmall = scaleMarkdownTextUnit(typography.titleSmall.fontSize, scale)
        )
    }
    
    // 【关键优化】稳定化 xmlRenderer 和 onLinkClick
    // 这两个参数虽然每次传入的引用可能不同，但实际功能是相同的
    // 使用 rememberUpdatedState 确保我们总是使用最新的值，但不会因为引用变化而触发不必要的重组
    val currentXmlRenderer = rememberUpdatedState(xmlRenderer)
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    
    // 直接从 node 读取内容，不使用外层 key()
    // 让 Compose 根据节点的实际变化自然地触发 recompose，而不是强制重建
    // 这样可以保持 XML 渲染器等组件的内部状态（如折叠/展开状态）
    val content = node.content
    
    // 【不使用 key() 包裹】直接调用 renderNodeContent
    // 让内部的 remember 和组件自己根据 content 的变化来决定是否重组
    // 这样 xmlRenderer 和 onLinkClick 的引用变化不会导致重组
    renderNodeContent(
        nodeKey = nodeKey,
        node = node,
        content = content,
        textColor = textColor,
        fontSizes = fontSizes,
        density = density,
        modifier = modifier,
        onLinkClick = currentOnLinkClick.value,
        xmlRenderer = currentXmlRenderer.value,
        xmlStream = xmlStream,
        index = index,
        enableDialogs = enableDialogs,
        fillMaxWidth = fillMaxWidth,
        isLastNode = isLastNode
    )
}

/** 字体大小数据类 */
private data class FontSizes(
    val bodyMedium: TextUnit,
    val headlineLarge: TextUnit,
    val headlineMedium: TextUnit,
    val headlineSmall: TextUnit,
    val titleLarge: TextUnit,
    val titleMedium: TextUnit,
    val titleSmall: TextUnit
)

private fun scaleMarkdownTextUnit(
    base: TextUnit,
    scale: Float
): TextUnit {
    if (!scale.isFinite() || scale <= 0f || scale == 1f) {
        return base
    }
    return (base.value * scale).sp
}

@Composable
private fun renderNodeContent(
    nodeKey: String,
    node: MarkdownNodeStable,
    content: String,
    textColor: Color,
    fontSizes: FontSizes,
    density: Density,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)?,
    xmlRenderer: XmlContentRenderer,
    xmlStream: Stream<String>?,
    index: Int,
    enableDialogs: Boolean,
    fillMaxWidth: Boolean,
    isLastNode: Boolean = false
) {
    // 【关键优化】只要节点内容不变，就记住原始节点实例，防止不必要的重组
    val stableNode = remember(content) { node }

    when (stableNode.type) {
        // ========== 简单文本类型：使用单个大 Canvas 绘制 ==========
        MarkdownProcessorType.HEADER,
        MarkdownProcessorType.ORDERED_LIST,
        MarkdownProcessorType.UNORDERED_LIST -> {
            UnifiedCanvasRenderer(
                nodeKey = nodeKey,
                node = stableNode,
                textColor = textColor,
                bodyMediumSize = fontSizes.bodyMedium,
                headlineLargeSize = fontSizes.headlineLarge,
                headlineMediumSize = fontSizes.headlineMedium,
                headlineSmallSize = fontSizes.headlineSmall,
                titleLargeSize = fontSizes.titleLarge,
                titleMediumSize = fontSizes.titleMedium,
                titleSmallSize = fontSizes.titleSmall,
                density = density,
                modifier = modifier,
                onLinkClick = onLinkClick,
                fillMaxWidth = fillMaxWidth,
                isLastNode = isLastNode
            )
        }

        MarkdownProcessorType.PLAIN_TEXT -> {
            UnifiedCanvasRenderer(
                nodeKey = nodeKey,
                node = stableNode,
                textColor = textColor,
                bodyMediumSize = fontSizes.bodyMedium,
                headlineLargeSize = fontSizes.headlineLarge,
                headlineMediumSize = fontSizes.headlineMedium,
                headlineSmallSize = fontSizes.headlineSmall,
                titleLargeSize = fontSizes.titleLarge,
                titleMediumSize = fontSizes.titleMedium,
                titleSmallSize = fontSizes.titleSmall,
                density = density,
                modifier = modifier,
                onLinkClick = onLinkClick,
                fillMaxWidth = fillMaxWidth,
                isLastNode = isLastNode
            )
        }

        MarkdownProcessorType.HTML_BREAK -> {
            SingleTextCanvas(
                text = "\n",
                textColor = textColor,
                fontSize = fontSizes.bodyMedium,
                fontWeight = FontWeight.Normal,
                density = density,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // ========== 代码块：保留原组件 ==========
        MarkdownProcessorType.CODE_BLOCK -> {
            val codeLines = content.trimAll().lines()
            val firstLine = codeLines.firstOrNull() ?: ""
            val language = if (firstLine.startsWith("```")) {
                firstLine.removePrefix("```").trim()
            } else ""
            
            val codeContent = codeLines
                .dropWhile { it.startsWith("```") }
                .dropLastWhile { it.endsWith("```") }
                .joinToString("\n")
            
            // 不使用 key()，让 Compose 根据位置自然识别组件
            // 这样可以保留内部状态（如"已复制"提示、Mermaid 渲染状态）
            EnhancedCodeBlock(
                code = codeContent,
                language = language,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // ========== 表格：保留原组件 ==========
        MarkdownProcessorType.TABLE -> {
            // 不使用 key()，让 Compose 根据位置自然识别组件
            EnhancedTableBlock(
                tableContent = content,
                textColor = textColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // ========== 引用块：使用 Canvas 绘制文本 + 边框 ==========
        MarkdownProcessorType.BLOCK_QUOTE -> {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                ) {
                    val quoteText = content.lines().joinToString("\n") {
                        it.removePrefix("> ").removePrefix(">")
                    }

                    SingleTextCanvas(
                        text = quoteText,
                        textColor = textColor,
                        fontSize = fontSizes.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        density = density,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // ========== 分隔线 ==========
        MarkdownProcessorType.HORIZONTAL_RULE -> {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
        
        // ========== XML块：保留原组件 ==========
        MarkdownProcessorType.XML_BLOCK -> {
            xmlRenderer.RenderXmlContent(
                xmlContent = content,
                modifier = Modifier.fillMaxWidth(),
                textColor = textColor,
                xmlStream = xmlStream,
                renderInstanceKey = nodeKey
            )
        }
        
        // ========== 图片：保留原组件 ==========
        MarkdownProcessorType.IMAGE -> {
            val imageContent = content.trimAll()
            if (isCompleteImageMarkdown(imageContent)) {
                // 不使用 key()，让 Compose 根据位置自然识别组件
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                ) {
                    MarkdownImageRenderer(
                        imageMarkdown = imageContent,
                        modifier = Modifier.fillMaxWidth(),
                        maxImageHeight = 140,
                        enableDialogs = enableDialogs
                    )
                }
            } else {
                SingleTextCanvas(
                    text = content,
                    textColor = textColor,
                    fontSize = fontSizes.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    density = density,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)
                )
            }
        }
        
        // ========== 块级 LaTeX：保留原组件 ==========
        MarkdownProcessorType.BLOCK_LATEX -> {
            val formulaTextSizePx = with(density) { fontSizes.bodyMedium.toPx() }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                color = Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 提取LaTeX内容，移除各种分隔符
                    val latexContent = extractLatexContent(content.trimAll())
                    val horizontalScrollState = rememberScrollState()
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScrollState),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // 使用AndroidView和JLatexMath渲染LaTeX公式
                        AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    includeFontPadding = false
                                    gravity = Gravity.CENTER
                                    setPadding(0, 0, 0, 0)
                                    setTextSize(TypedValue.COMPLEX_UNIT_PX, formulaTextSizePx)
                                }
                            },
                            update = { textView ->
                                try {
                                    val drawable = LatexCache.getDrawable(
                                        latexContent.trim(),
                                        JLatexMathDrawable.builder(latexContent)
                                            .textSize(formulaTextSizePx)
                                            .padding(2)
                                            .background(0x00000000)
                                            .align(JLatexMathDrawable.ALIGN_CENTER)
                                            .color(textColor.toArgb())
                                    )
                                    val formulaText =
                                        SpannableStringBuilder(INLINE_LATEX_PLACEHOLDER.toString())
                                    formulaText.setSpan(
                                        LatexDrawableSpan(drawable),
                                        0,
                                        formulaText.length,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                    textView.apply {
                                        includeFontPadding = false
                                        gravity = Gravity.CENTER
                                        setPadding(0, 0, 0, 0)
                                        setTextSize(TypedValue.COMPLEX_UNIT_PX, formulaTextSizePx)
                                        setTextColor(textColor.toArgb())
                                        typeface = Typeface.DEFAULT
                                        text = formulaText
                                    }
                                } catch (e: Exception) {
                                    AppLogger.w(TAG, "Block LaTeX render failed, fallback to raw text: $latexContent", e)
                                    textView.text = content.trimAll()
                                    textView.setTextColor(textColor.toArgb())
                                    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, formulaTextSizePx)
                                    textView.typeface = Typeface.MONOSPACE
                                }
                            },
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
        
        // ========== 其他：Canvas 绘制 ==========
        else -> {
            if (content.trimAll().isEmpty()) return
            
            SingleTextCanvas(
                text = content.trimAll(),
                textColor = textColor,
                fontSize = fontSizes.bodyMedium,
                fontWeight = FontWeight.Normal,
                density = density,
                modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)
            )
        }
    }
}

/**
 * 统一的 Canvas 渲染器
 * 在一个大 Canvas 中绘制标题、段落、列表等简单文本
 */
@Composable
private fun UnifiedCanvasRenderer(
    nodeKey: String,
    node: MarkdownNodeStable,
    textColor: Color,
    bodyMediumSize: TextUnit,
    headlineLargeSize: TextUnit,
    headlineMediumSize: TextUnit,
    headlineSmallSize: TextUnit,
    titleLargeSize: TextUnit,
    titleMediumSize: TextUnit,
    titleSmallSize: TextUnit,
    density: Density,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)?,
    fillMaxWidth: Boolean = true,
    isLastNode: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textLayoutSettings = LocalAiMarkdownTextLayoutSettings.current

    val fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
    val resolver = LocalFontFamilyResolver.current

    val normalTypeface = remember(resolver, fontFamily) {
        (resolver.resolve(fontFamily = fontFamily, fontWeight = FontWeight.Normal).value as? android.graphics.Typeface)
            ?: android.graphics.Typeface.DEFAULT
    }
    val boldTypeface = remember(resolver, fontFamily) {
        (resolver.resolve(fontFamily = fontFamily, fontWeight = FontWeight.Bold).value as? android.graphics.Typeface)
            ?: android.graphics.Typeface.DEFAULT_BOLD
    }

    BoxWithConstraints(modifier = modifier) {
        val localDensity = LocalDensity.current
        val availableWidthPx = with(localDensity) { maxWidth.toPx() }.toInt()
        if (availableWidthPx <= 0) return@BoxWithConstraints

        val enableTypewriter = !nodeKey.startsWith("static-node-") && isLastNode && (node.content.isNotEmpty() || node.children.isNotEmpty()) &&
                (node.type == MarkdownProcessorType.PLAIN_TEXT ||
                        node.type == MarkdownProcessorType.HEADER ||
                        node.type == MarkdownProcessorType.ORDERED_LIST ||
                        node.type == MarkdownProcessorType.UNORDERED_LIST)

        val contentKey = node.content.length

        // 计算布局和绘制指令（用于稳定高度/宽度）
        val layoutResult = remember(
            contentKey,
            textColor,
            availableWidthPx,
            node.type,
            normalTypeface,
            boldTypeface,
            isLastNode,
            node.children,
            textLayoutSettings.lineHeightMultiplier,
            textLayoutSettings.letterSpacingSp
        ) {
            calculateLayout(
                node = node,
                textColor = textColor,
                primaryColor = primaryColor,
                bodyMediumSize = bodyMediumSize,
                headlineLargeSize = headlineLargeSize,
                headlineMediumSize = headlineMediumSize,
                headlineSmallSize = headlineSmallSize,
                titleLargeSize = titleLargeSize,
                titleMediumSize = titleMediumSize,
                titleSmallSize = titleSmallSize,
                normalTypeface = normalTypeface,
                boldTypeface = boldTypeface,
                density = localDensity,
                availableWidthPx = availableWidthPx,
                isLastNode = isLastNode,
                globalLineHeightMultiplier = textLayoutSettings.lineHeightMultiplier,
                globalLetterSpacingSp = textLayoutSettings.letterSpacingSp,
                globalParagraphSpacingDp = textLayoutSettings.paragraphSpacingDp
            )
        }

        val revealInstruction = layoutResult.instructions.filterIsInstance<DrawInstruction.TextLayout>().singleOrNull()
        val targetLength = revealInstruction?.layout?.text?.length ?: 0
        val revealHasImageSpans =
            (revealInstruction?.text as? Spanned)
                ?.getSpans(0, targetLength, ImageSpan::class.java)
                ?.isNotEmpty() == true
        val shouldAnimateTypewriter = enableTypewriter && !revealHasImageSpans
        val revealAnim = remember(nodeKey) { Animatable(0f) }
        LaunchedEffect(shouldAnimateTypewriter) {
            if (!shouldAnimateTypewriter) {
                revealAnim.snapTo(targetLength.toFloat())
            }
        }
        LaunchedEffect(targetLength, shouldAnimateTypewriter) {
            if (!shouldAnimateTypewriter) {
                return@LaunchedEffect
            }
            if (targetLength <= 0) {
                return@LaunchedEffect
            }
            val current = revealAnim.value
            if (targetLength.toFloat() < current) {
                revealAnim.snapTo(targetLength.toFloat())
            } else {
                val deltaChars = (targetLength - floor(current).toInt()).coerceAtLeast(0)
                if (deltaChars <= 0) {
                    return@LaunchedEffect
                }
                val durationMs = TYPEWRITER_WINDOW_MS
                revealAnim.animateTo(
                    targetValue = targetLength.toFloat(),
                    animationSpec = tween(
                        durationMillis = durationMs,
                        easing = LinearEasing
                    )
                )
            }
        }

        val revealValue = if (shouldAnimateTypewriter) revealAnim.value else targetLength.toFloat()
        val baseLen = floor(revealValue).toInt().coerceIn(0, targetLength)
        val partial = if (shouldAnimateTypewriter) {
            (revealValue - baseLen.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
        
        // 提取文本内容用于无障碍朗读
        val accessibleText = remember(layoutResult.instructions) {
            extractAccessibleText(layoutResult.instructions)
        }
        val safeAccessibleText = remember(accessibleText) {
            if (accessibleText.length > FALLBACK_MAX_TEXT_CHARS) {
                accessibleText.substring(0, FALLBACK_MAX_TEXT_CHARS)
            } else {
                accessibleText
            }
        }
        val maxHeightPx = minOf(MAX_CANVAS_HEIGHT_PX, MAX_COMPOSE_CONSTRAINT_HEIGHT_PX)
        val clampedHeightDp = with(localDensity) {
            layoutResult.height.coerceIn(0f, maxHeightPx).toDp()
        }
        val canvasModifier =
            (if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                // bubble模式：使用实际宽度，如果宽度为0则wrapContent
                if (layoutResult.actualWidth > 0f) {
                    Modifier.width(with(localDensity) { layoutResult.actualWidth.toDp() })
                } else {
                    Modifier
                }
            })
                .height(clampedHeightDp)
                .semantics {
                    contentDescription = safeAccessibleText
                }
        
        // 使用单个 Canvas 绘制所有内容
        SafeMeasureOrFallback(
            fallback = {
                Text(
                    text = safeAccessibleText,
                    color = textColor,
                    maxLines = 200,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Canvas(
                modifier = canvasModifier.pointerInput(layoutResult.instructions, onLinkClick) {
                    awaitEachGesture {
                        val down = awaitPointerEvent(PointerEventPass.Initial).changes.first()
                        val downTime = System.currentTimeMillis()
                        val downPosition = down.position

                        val up = awaitPointerEvent(PointerEventPass.Initial).changes.first()
                        val upTime = System.currentTimeMillis()
                        val upPosition = up.position

                        val isTap = (upTime - downTime) < 500 &&
                            (upPosition - downPosition).getDistance() < 10f

                        if (isTap) {
                            var clickedLink = false
                            layoutResult.instructions.forEach { instruction ->
                                if (instruction is DrawInstruction.TextLayout) {
                                    val layout = instruction.layout
                                    val text = instruction.text
                                    if (text is Spanned) {
                                        val bounds = android.graphics.RectF(
                                            instruction.x,
                                            instruction.y,
                                            instruction.x + layout.width,
                                            instruction.y + layout.height
                                        )
                                        if (bounds.contains(upPosition.x, upPosition.y)) {
                                            val relativeX = upPosition.x - instruction.x
                                            val relativeY = upPosition.y - instruction.y
                                            val line = layout.getLineForVertical(relativeY.toInt())
                                            val lineOffset = layout.getOffsetForHorizontal(line, relativeX)

                                            val spans = text.getSpans(lineOffset, lineOffset, URLSpan::class.java)
                                            spans.firstOrNull()?.let { span ->
                                                onLinkClick?.invoke(span.url)
                                                clickedLink = true
                                            }
                                        }
                                    }
                                }
                            }

                            if (clickedLink) {
                                up.consume()
                            }
                        }
                    }
                }
            ) {
                drawIntoCanvas { canvas ->
                    // 获取可见区域（屏幕内区域）
                    val clipBounds = android.graphics.Rect()
                    canvas.nativeCanvas.getClipBounds(clipBounds)

                    // 只绘制在可见区域内的指令
                    layoutResult.instructions.forEach { instruction ->
                        when (instruction) {
                            is DrawInstruction.Text -> {
                                // 判断文本是否在可见区域内
                                val textTop = instruction.y - instruction.paint.textSize
                                val textBottom = instruction.y + instruction.paint.descent()

                                if (textBottom >= clipBounds.top && textTop <= clipBounds.bottom) {
                                    canvas.nativeCanvas.drawText(
                                        instruction.text,
                                        instruction.x,
                                        instruction.y,
                                        instruction.paint
                                    )
                                }
                            }
                            is DrawInstruction.Line -> {
                                // 判断线条是否在可见区域内
                                val lineTop = minOf(instruction.startY, instruction.endY)
                                val lineBottom = maxOf(instruction.startY, instruction.endY)

                                if (lineBottom >= clipBounds.top && lineTop <= clipBounds.bottom) {
                                    canvas.nativeCanvas.drawLine(
                                        instruction.startX,
                                        instruction.startY,
                                        instruction.endX,
                                        instruction.endY,
                                        instruction.paint
                                    )
                                }
                            }
                            is DrawInstruction.TextLayout -> {
                                // 使用 StaticLayout 绘制（自动换行）
                                val layoutTop = instruction.y
                                val layoutBottom = instruction.y + instruction.layout.height

                                if (layoutBottom >= clipBounds.top && layoutTop <= clipBounds.bottom) {
                                    canvas.nativeCanvas.save()
                                    canvas.nativeCanvas.translate(instruction.x, instruction.y)

                                    if (shouldAnimateTypewriter && revealInstruction === instruction && targetLength > 0 && baseLen < targetLength) {
                                        val layout = instruction.layout

                                        val offsetForLine = baseLen.coerceIn(0, (targetLength - 1).coerceAtLeast(0))
                                        val line = layout.getLineForOffset(offsetForLine)
                                        val lineTopPx = layout.getLineTop(line).toFloat()
                                        val lineBottomPx = layout.getLineBottom(line).toFloat()

                                        val safeBaseLen = baseLen.coerceIn(0, targetLength)
                                        val safeNextLen = (baseLen + 1).coerceAtMost(targetLength)
                                        val x0 = layout.getPrimaryHorizontal(safeBaseLen)
                                        
                                        // 检查下一个字符是否换行
                                        val lineOfNext = if (safeNextLen < layout.text.length) layout.getLineForOffset(safeNextLen) else line
                                        val x1 = if (lineOfNext != line) {
                                            // 如果换行了，说明当前字符是该行的最后一个字符（可能是\n，或者是被wrap的字符）
                                            // 这种情况下 getPrimaryHorizontal(safeNextLen) 会返回下一行的坐标（通常是0），导致计算出的 charWidth 巨大且不仅确
                                            // 我们需要手动测量这个字符的宽度，并加在 x0 上 (假设 LTR)
                                            // 注意：如果是 RTL，逻辑需要反过来，这里简单处理常规情况，更严谨可以使用 layout.getParagraphDirection
                                            val charWidthMeasured = layout.paint.measureText(layout.text, safeBaseLen, safeNextLen)
                                            if (layout.getParagraphDirection(line) == android.text.Layout.DIR_RIGHT_TO_LEFT) {
                                                x0 - charWidthMeasured
                                            } else {
                                                x0 + charWidthMeasured
                                            }
                                        } else {
                                            layout.getPrimaryHorizontal(safeNextLen)
                                        }

                                        val charMinX = minOf(x0, x1)
                                        val charMaxX = maxOf(x0, x1)
                                        val charWidth = (charMaxX - charMinX).coerceAtLeast(0f)
                                        // 修正闪烁问题：
                                        // 之前对 charWidth <= 0.01f 的处理（直接显示整行）会导致在换行处如果有零宽字符（如某些空格或换行符处理），
                                        // 会瞬间显示出该行后续的文字，产生闪烁。
                                        // 现在统一使用分段绘制逻辑，并仅在字符宽度有效时才绘制淡入部分。

                                        val visibleRight = (charMinX + charWidth * partial).coerceIn(charMinX, charMaxX)

                                        // 1. 绘制当前行之前的所有行
                                        canvas.nativeCanvas.save()
                                        canvas.nativeCanvas.clipRect(0f, 0f, layout.width.toFloat(), lineTopPx)
                                        drawInlineCodeBackgrounds(layout, canvas.nativeCanvas)
                                        layout.draw(canvas.nativeCanvas)
                                        canvas.nativeCanvas.restore()

                                        // 2. 绘制当前行，直到当前正在显示的字符之前
                                        canvas.nativeCanvas.save()
                                        canvas.nativeCanvas.clipRect(0f, lineTopPx, charMinX, lineBottomPx)
                                        drawInlineCodeBackgrounds(layout, canvas.nativeCanvas)
                                        layout.draw(canvas.nativeCanvas)
                                        canvas.nativeCanvas.restore()

                                        // 3. 绘制当前正在显示的字符（带淡入效果）
                                        if (charWidth > 0.01f) {
                                            val alphaInt = (partial * 255f).toInt().coerceIn(0, 255)
                                            canvas.nativeCanvas.save()
                                            canvas.nativeCanvas.clipRect(charMinX, lineTopPx, visibleRight, lineBottomPx)
                                            // 注意：saveLayerAlpha 在某些情况下可能会对其包含的内容做混合，如果不需要可以简化
                                            canvas.nativeCanvas.saveLayerAlpha(charMinX, lineTopPx, visibleRight, lineBottomPx, alphaInt)
                                            drawInlineCodeBackgrounds(layout, canvas.nativeCanvas)
                                            layout.draw(canvas.nativeCanvas)
                                            canvas.nativeCanvas.restore()
                                            canvas.nativeCanvas.restore()
                                        }
                                    } else {
                                        drawInlineCodeBackgrounds(instruction.layout, canvas.nativeCanvas)
                                        instruction.layout.draw(canvas.nativeCanvas)
                                    }
                                    canvas.nativeCanvas.restore()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 计算 StaticLayout 的实际使用宽度（取所有行的最大宽度）
 * @param layout 要计算的 StaticLayout
 * @param offsetX 水平偏移量（用于列表项等有缩进的情况）
 * @param availableWidthPx 最大可用宽度，用于提前终止遍历
 * @return 实际使用的最大宽度
 */
private inline fun calculateActualWidth(
    layout: StaticLayout,
    offsetX: Float = 0f,
    availableWidthPx: Int
): Float {
    var maxWidth = 0f
    for (line in 0 until layout.lineCount) {
        val lineWidth = offsetX + layout.getLineWidth(line)
        maxWidth = maxOf(maxWidth, lineWidth)
        // 如果某行已达到最大可用宽度，无需继续遍历
        if (lineWidth >= availableWidthPx) break
    }
    return maxWidth
}

/**
 * 计算布局和生成绘制指令
 */
private fun calculateLayout(
    node: MarkdownNodeStable,
    textColor: Color,
    primaryColor: Color,
    bodyMediumSize: TextUnit,
    headlineLargeSize: TextUnit,
    headlineMediumSize: TextUnit,
    headlineSmallSize: TextUnit,
    titleLargeSize: TextUnit,
    titleMediumSize: TextUnit,
    titleSmallSize: TextUnit,
    normalTypeface: Typeface,
    boldTypeface: Typeface,
    density: Density,
    availableWidthPx: Int,
    isLastNode: Boolean = false,
    disableLayoutCache: Boolean = false,
    typewriterTailAlpha: Float = 1f,
    globalLineHeightMultiplier: Float = 1f,
    globalLetterSpacingSp: Float = 0f,
    globalParagraphSpacingDp: Float = 0f
): LayoutResult {
    if (availableWidthPx <= 0) return LayoutResult(0f, 0f, emptyList())

    val safeAvailableWidthPx = safeLayoutWidth(availableWidthPx)
    val lineSpacingMultiplier = calculateCanvasLineSpacingMultiplier(globalLineHeightMultiplier)
    val content = node.content
    val instructions = mutableListOf<DrawInstruction>()
    var currentY = 0f
    var maxWidth = 0f  // 追踪实际使用的最大宽度
    
    when (node.type) {
        MarkdownProcessorType.HEADER -> {
            val level = determineHeaderLevel(content)
            val headerText = content.trimStart('#', ' ').trimAll()
            
            // 减小标题字号：使用更小一级的字体
            val fontSize = when (level) {
                1 -> headlineMediumSize  // 原：headlineLargeSize
                2 -> headlineSmallSize   // 原：headlineMediumSize
                3 -> titleLargeSize      // 原：headlineSmallSize
                4 -> titleMediumSize     // 原：titleLargeSize
                5 -> titleSmallSize      // 原：titleMediumSize
                else -> bodyMediumSize   // 原：titleSmallSize
            }
            
            // 增大上下间距，提高可读性
            val topPadding = when (level) {
                1 -> 12f  // 原：8f
                2 -> 10f  // 原：6f
                3 -> 8f   // 原：4f
                else -> 6f // 原：3f
            } * density.density
            
            val bottomPadding = when (level) {
                1, 2 -> 4f  // 原：2f
                else -> 2f  // 原：1f
            }
            val bottomPaddingPx = bottomPadding * density.density
            
            currentY += topPadding
            
            val textSizePx = with(density) { fontSize.toPx() }
            val textPaint = PaintCache.getTextPaint(
                textColor,
                textSizePx,
                boldTypeface,
                calculateCanvasLetterSpacingEm(fontSize, globalLetterSpacingSp)
            )

            val layout = if (node.children.isNotEmpty()) {
                // 处理子节点列表，去除第一个子节点中的标题标记
                val modifiedChildren = node.children.toMutableList()
                val firstChild = modifiedChildren[0]
                val newContent = firstChild.content.trimStart('#', ' ')
                val newFirstChild = MarkdownNodeStable(firstChild.type, content = newContent, children = firstChild.children)
                modifiedChildren[0] = newFirstChild

                createSafeInlineStaticLayout(
                    children = modifiedChildren,
                    fallbackText = headerText,
                    textColor = textColor,
                    primaryColor = primaryColor,
                    density = density,
                    fontSize = fontSize,
                    textPaint = textPaint,
                    width = safeAvailableWidthPx,
                    lineSpacingMultiplier = lineSpacingMultiplier,
                    contextLabel = "header"
                )
            } else {
                LayoutCache.getLayout(
                    headerText,
                    textPaint,
                    safeAvailableWidthPx,
                    textColor,
                    boldTypeface,
                    lineSpacingMultiplier
                )
            }
            
            instructions.add(DrawInstruction.TextLayout(layout, 0f, currentY, layout.text))
            currentY += layout.height
            maxWidth = maxOf(maxWidth, calculateActualWidth(layout, 0f, safeAvailableWidthPx))
            
            // 最后一个节点不添加底部间距
            if (!isLastNode) {
                currentY += bottomPaddingPx
            }
        }
        
        MarkdownProcessorType.ORDERED_LIST -> {
            val itemContent = content.trimAll()
            val numberMatch = Regex("""^(\d+)\.\s*""").find(itemContent)
            val numberStr = numberMatch?.groupValues?.getOrNull(1) ?: ""
            val itemText = numberMatch?.let { 
                val startIndex = (it.range.last + 1).coerceAtMost(itemContent.length)
                itemContent.substring(startIndex)
            } ?: itemContent
            
            val startPadding = 4f * density.density
            val markerEndPadding = 4f * density.density
            
            val textSizePx = with(density) { bodyMediumSize.toPx() }
            val boldPaint = PaintCache.getPaint(textColor, textSizePx, boldTypeface)
            val textPaint = PaintCache.getTextPaint(
                textColor,
                textSizePx,
                normalTypeface,
                calculateCanvasLetterSpacingEm(bodyMediumSize, globalLetterSpacingSp)
            )
            
            // 测量标记宽度
            val markerWidth = boldPaint.measureText("$numberStr.")
            val contentX = startPadding + markerWidth + markerEndPadding
            
            // 绘制标记
            val markerY = currentY + textSizePx
            instructions.add(
                DrawInstruction.Text(
                    text = "$numberStr.",
                    x = startPadding,
                    y = markerY,
                    paint = boldPaint,
                )
            )
            
            // 使用 StaticLayout 绘制内容（支持自动换行）
            val contentWidth = (safeAvailableWidthPx - contentX.toInt()).coerceAtLeast(1)
            
            val layout = if (node.children.isNotEmpty()) {
                // 处理子节点列表，去除第一个子节点中的列表标记
                val modifiedChildren = node.children.toMutableList()
                val firstChild = modifiedChildren[0]
                val newContent = numberMatch?.let {
                val startIndex = (it.range.last + 1).coerceAtMost(firstChild.content.length)
                firstChild.content.substring(startIndex)
            } ?: firstChild.content
                val newFirstChild = MarkdownNodeStable(firstChild.type, content = newContent, children = firstChild.children)
                modifiedChildren[0] = newFirstChild

                createSafeInlineStaticLayout(
                    children = modifiedChildren,
                    fallbackText = itemText,
                    textColor = textColor,
                    primaryColor = primaryColor,
                    density = density,
                    fontSize = bodyMediumSize,
                    textPaint = textPaint,
                    width = contentWidth,
                    lineSpacingMultiplier = lineSpacingMultiplier,
                    contextLabel = "ordered-list"
                )
            } else {
                LayoutCache.getLayout(
                    itemText,
                    textPaint,
                    contentWidth,
                    textColor,
                    normalTypeface,
                    lineSpacingMultiplier
                )
            }
            instructions.add(
                DrawInstruction.TextLayout(
                    layout = layout,
                    x = contentX,
                    y = currentY,
                    text = layout.text,
                )
            )
            currentY += layout.height
            maxWidth = maxOf(maxWidth, calculateActualWidth(layout, contentX, safeAvailableWidthPx))
            
            // 最后一个节点不添加底部间距
            if (!isLastNode) {
                currentY += 2f * density.density
            }
        }
        
        MarkdownProcessorType.UNORDERED_LIST -> {
            val itemContent = content.trimAll()
            val markerMatch = Regex("""^[-*+]\s+""").find(itemContent)
            val itemText = markerMatch?.let { 
                val startIndex = (it.range.last + 1).coerceAtMost(itemContent.length)
                itemContent.substring(startIndex)
            } ?: itemContent
            
            val startPadding = 4f * density.density
            val markerEndPadding = 4f * density.density
            
            val textSizePx = with(density) { bodyMediumSize.toPx() }
            val textPaint = PaintCache.getTextPaint(
                textColor,
                textSizePx,
                normalTypeface,
                calculateCanvasLetterSpacingEm(bodyMediumSize, globalLetterSpacingSp)
            )
            val markerPaint = PaintCache.getPaint(textColor, textSizePx, normalTypeface)
            
            // 测量标记宽度
            val markerWidth = markerPaint.measureText("•")
            val contentX = startPadding + markerWidth + markerEndPadding

            // 使用 StaticLayout 绘制内容（支持自动换行）
            val contentWidth = (safeAvailableWidthPx - contentX.toInt()).coerceAtLeast(1)
            
            val layout = if (node.children.isNotEmpty()) {
                // 处理子节点列表，去除第一个子节点中的列表标记
                val modifiedChildren = node.children.toMutableList()
                val firstChild = modifiedChildren[0]
                val newContent = markerMatch?.let {
                val startIndex = (it.range.last + 1).coerceAtMost(firstChild.content.length)
                firstChild.content.substring(startIndex)
            } ?: firstChild.content
                val newFirstChild = MarkdownNodeStable(firstChild.type, content = newContent, children = firstChild.children)
                modifiedChildren[0] = newFirstChild

                createSafeInlineStaticLayout(
                    children = modifiedChildren,
                    fallbackText = itemText,
                    textColor = textColor,
                    primaryColor = primaryColor,
                    density = density,
                    fontSize = bodyMediumSize,
                    textPaint = textPaint,
                    width = contentWidth,
                    lineSpacingMultiplier = lineSpacingMultiplier,
                    contextLabel = "unordered-list"
                )
            } else {
                LayoutCache.getLayout(
                    itemText,
                    textPaint,
                    contentWidth,
                    textColor,
                    normalTypeface,
                    lineSpacingMultiplier
                )
            }

            // 使用首行真实基线定位圆点，避免不同字体/字重下出现垂直漂移
            val markerY = currentY + layout.getLineBaseline(0)
            instructions.add(
                DrawInstruction.Text(
                    text = "•",
                    x = startPadding,
                    y = markerY,
                    paint = markerPaint,
                )
            )
            instructions.add(
                DrawInstruction.TextLayout(
                    layout = layout,
                    x = contentX,
                    y = currentY,
                    text = layout.text,
                )
            )
            currentY += layout.height
            maxWidth = maxOf(maxWidth, calculateActualWidth(layout, contentX, safeAvailableWidthPx))
            
            // 最后一个节点不添加底部间距
            if (!isLastNode) {
                currentY += 2f * density.density
            }
        }
        
        MarkdownProcessorType.PLAIN_TEXT -> {
            if (content.trimAll().isEmpty()) return LayoutResult(0f, 0f, emptyList())
            
            val textSizePx = with(density) { bodyMediumSize.toPx() }
            val textPaint = PaintCache.getTextPaint(
                textColor,
                textSizePx,
                normalTypeface,
                calculateCanvasLetterSpacingEm(bodyMediumSize, globalLetterSpacingSp)
            )

            val trimmedContent: CharSequence =
                if (node.children.isNotEmpty()) {
                    try {
                        buildSpannableFromChildren(
                            children = node.children,
                            textColor = textColor,
                            primaryColor = primaryColor,
                            density = density,
                            fontSize = bodyMediumSize
                        ).trimAllPreservingSpans()
                    } catch (t: Throwable) {
                        AppLogger.w(TAG, "Inline markdown layout failed in plain-text, fallback to raw text", t)
                        content.trimAll()
                    }
                } else {
                    content.trimAll()
                }

            val paragraphs = splitPlainTextParagraphs(trimmedContent)
            if (paragraphs.isEmpty()) return LayoutResult(0f, 0f, emptyList())

            val paragraphBreakHeight = with(density) { DEFAULT_PARAGRAPH_BREAK_DP.dp.toPx() }
            val paragraphSpacingPx = with(density) { globalParagraphSpacingDp.dp.toPx() }

            paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                val layout =
                    if (
                        paragraphs.size == 1 &&
                            paragraph is String &&
                            !disableLayoutCache
                    ) {
                        LayoutCache.getLayout(
                            paragraph,
                            textPaint,
                            safeAvailableWidthPx,
                            textColor,
                            normalTypeface,
                            lineSpacingMultiplier
                        )
                    } else if (paragraphs.size == 1 && disableLayoutCache) {
                        val tail = typewriterTailAlpha.coerceIn(0f, 1f)
                        if (tail < 0.999f && paragraph.isNotEmpty()) {
                            val spannable = SpannableStringBuilder(paragraph)
                            val lastIndex = spannable.length - 1
                            val fadedColor = textColor.copy(alpha = textColor.alpha * tail)
                            spannable.setSpan(
                                ForegroundColorSpan(fadedColor.toArgb()),
                                lastIndex,
                                lastIndex + 1,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            createStaticLayout(spannable, textPaint, safeAvailableWidthPx, lineSpacingMultiplier)
                        } else {
                            createStaticLayout(paragraph, textPaint, safeAvailableWidthPx, lineSpacingMultiplier)
                        }
                    } else {
                        createStaticLayout(paragraph, textPaint, safeAvailableWidthPx, lineSpacingMultiplier)
                    }

                instructions.add(DrawInstruction.TextLayout(layout, 0f, currentY, paragraph))
                currentY += layout.height
                maxWidth = maxOf(maxWidth, calculateActualWidth(layout, 0f, safeAvailableWidthPx))

                if (paragraphIndex < paragraphs.lastIndex) {
                    currentY += paragraphBreakHeight + paragraphSpacingPx
                }
            }
            
            // 最后一个节点不添加底部间距
            if (!isLastNode) {
                currentY += 6f * density.density
            }
        }

        else -> {
            // 其他类型暂不处理
        }
    }
    
    return LayoutResult(currentY, maxWidth, instructions)
}

private fun buildSpannableFromChildren(
    children: List<MarkdownNodeStable>,
    textColor: Color,
    primaryColor: Color,
    density: Density? = null,
    fontSize: TextUnit? = null
): SpannableStringBuilder {
    return buildMarkdownInlineSpannableFromChildren(
        children = children,
        textColor = textColor,
        primaryColor = primaryColor,
        density = density,
        fontSize = fontSize
    )
}

/**
 * 单个文本的 Canvas 渲染器（用于引用块等简单场景）
 */
@Composable
private fun SingleTextCanvas(
    text: String,
    textColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    density: Density,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) return

    val textLayoutSettings = LocalAiMarkdownTextLayoutSettings.current
    val fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
    val resolver = LocalFontFamilyResolver.current
    val typeface = remember(resolver, fontFamily, fontWeight) {
        (resolver.resolve(fontFamily = fontFamily, fontWeight = fontWeight).value as? android.graphics.Typeface)
            ?: if (fontWeight == FontWeight.Bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    BoxWithConstraints(modifier = modifier) {
        val localDensity = LocalDensity.current
        val availableWidthPxRaw = with(localDensity) { maxWidth.toPx() }.toInt()
        if (availableWidthPxRaw <= 0) return@BoxWithConstraints
        val availableWidthPx = safeLayoutWidth(availableWidthPxRaw)
        val textSizePx = with(localDensity) { fontSize.toPx() }
        val letterSpacingEm = calculateCanvasLetterSpacingEm(fontSize, textLayoutSettings.letterSpacingSp)
        val lineSpacingMultiplier = calculateCanvasLineSpacingMultiplier(textLayoutSettings.lineHeightMultiplier)

        val textPaint = remember(textColor, textSizePx, typeface, letterSpacingEm) {
            PaintCache.getTextPaint(textColor, textSizePx, typeface, letterSpacingEm)
        }

        val layout = remember(text, textPaint, availableWidthPx, textColor, typeface, lineSpacingMultiplier) {
            LayoutCache.getLayout(
                text,
                textPaint,
                availableWidthPx,
                textColor,
                typeface,
                lineSpacingMultiplier
            )
        }
        
        val totalHeight = layout.height.toFloat()
        val maxHeightPx = minOf(MAX_CANVAS_HEIGHT_PX, MAX_COMPOSE_CONSTRAINT_HEIGHT_PX)
        val clampedHeightDp = with(localDensity) {
            totalHeight.coerceIn(0f, maxHeightPx).toDp()
        }

        val safeText = remember(text) {
            if (text.length > FALLBACK_MAX_TEXT_CHARS) {
                text.substring(0, FALLBACK_MAX_TEXT_CHARS)
            } else {
                text
            }
        }

        SafeMeasureOrFallback(
            fallback = {
                Text(
                    text = safeText,
                    color = textColor,
                    maxLines = 200,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(clampedHeightDp)
                    .semantics {
                        contentDescription = text
                    }
            ) {
                drawIntoCanvas { canvas ->
                    // 获取可见区域
                    val clipBounds = android.graphics.Rect()
                    canvas.nativeCanvas.getClipBounds(clipBounds)
                    
                    // 判断是否在可见区域内
                    if (totalHeight >= clipBounds.top && 0f <= clipBounds.bottom) {
                        drawInlineCodeBackgrounds(layout, canvas.nativeCanvas)
                        layout.draw(canvas.nativeCanvas)
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeMeasureOrFallback(
    fallback: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Layout(
        content = {
            Box { content() }
            Box { fallback() }
        }
    ) { measurables, constraints ->
        val primary = measurables[0]
        val fallbackMeasurable = measurables[1]
        try {
            val placeable = primary.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Markdown renderer measure failed, fallback to text", t)
            val placeable = fallbackMeasurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }
}

/**
 * 判断标题级别
 */
private fun determineHeaderLevel(content: String): Int {
    val headerPrefix = content.takeWhile { it == '#' }
    return headerPrefix.length.coerceIn(1, 6)
}

/**
 * 直接创建 StaticLayout (用于Spannable, 不走缓存)
 */
/** 提取LaTeX内容，移除各种分隔符 */
private fun extractLatexContent(content: String): String {
    return when {
        content.startsWith("$$") && content.endsWith("$$") -> content.removeSurrounding("$$")
        content.startsWith("\\[") && content.endsWith("\\]") -> content.removeSurrounding("\\[", "\\]")
        content.startsWith("$") && content.endsWith("$") -> content.removeSurrounding("$")
        content.startsWith("\\(") && content.endsWith("\\)") -> content.removeSurrounding("\\(", "\\)")
        else -> content
    }
}

private fun createStaticLayout(
    text: CharSequence,
    paint: TextPaint,
    width: Int,
    lineSpacingMultiplier: Float
): StaticLayout {
    val safeWidth = safeLayoutWidth(width)
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .build()
    } else {
        @Suppress("DEPRECATION")
        StaticLayout(
            text,
            paint,
            safeWidth,
            android.text.Layout.Alignment.ALIGN_NORMAL,
            lineSpacingMultiplier,
            0f,
            false
        )
    }
}
