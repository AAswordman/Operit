package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun cardSpacing(token: String?, default: Int = 8) =
    when (token) { "none" -> 0; "xs" -> 4; "sm" -> 8; "md" -> 16; "lg" -> 24; else -> default }.dp

// Panels are transparent wrappers for sizing: a logo inside a row must not claim
// the entire row. Vertical containers reset this before laying out their children.
internal val LocalCardRowItem = compositionLocalOf { false }

internal fun cardAutoColumns(width: Float, minimum: Float, fontScale: Float): Int =
    ((width + 8) / (minimum * fontScale.coerceAtLeast(1f) + 8)).toInt().coerceIn(1, 4)

internal fun cardFrameSize(bounds: Constraints, ratio: Float): androidx.compose.ui.unit.IntSize {
    val width = minOf(bounds.maxWidth.toFloat(), bounds.maxHeight * ratio)
        .toInt().coerceIn(bounds.minWidth, bounds.maxWidth)
    val height = (width / ratio).toInt().coerceIn(bounds.minHeight, bounds.maxHeight)
    return androidx.compose.ui.unit.IntSize(width, height)
}

/** Bounded layout tokens shared by all authored cards, independent of screen size. */
internal class CardLayoutComponent(override val name: String) : A2uiComponent {
    private val children = A2uiProperty.childList("children")
    private val child = A2uiProperty.dynamicString("child")
    private val text = A2uiProperty.dynamicString("text")
    private val variant = A2uiProperty.dynamicString("variant")
    private val align = A2uiProperty.dynamicString("align")
    private val textAlign = A2uiProperty.dynamicString("textAlign")
    private val gap = A2uiProperty.dynamicString("gap")
    private val justify = A2uiProperty.dynamicString("justify")
    private val wrap = A2uiProperty.dynamicBoolean("wrap")
    private val weights = A2uiProperty.dynamicValue("weights")
    private val minItemWidth = A2uiProperty.dynamicNumber("minItemWidth")
    private val padding = A2uiProperty.dynamicString("padding")
    private val radius = A2uiProperty.dynamicString("radius")
    private val background = A2uiProperty.dynamicString("background")
    private val border = A2uiProperty.dynamicBoolean("border")
    private val maxWidth = A2uiProperty.dynamicNumber("maxWidth")
    private val maxHeight = A2uiProperty.dynamicNumber("maxHeight")
    private val minWidth = A2uiProperty.dynamicNumber("minWidth")
    private val minHeight = A2uiProperty.dynamicNumber("minHeight")
    private val aspectRatio = A2uiProperty.dynamicNumber("aspectRatio")
    private val lineClamp = A2uiProperty.dynamicNumber("lineClamp")
    private val tone = A2uiProperty.dynamicString("tone")
    override val description = "Bounded responsive $name layout."
    override val properties = listOf(children, child, text, variant, align, textAlign, gap, justify,
        wrap, weights, minItemWidth, padding, radius, background, border, maxWidth, maxHeight, minWidth, minHeight, aspectRatio, lineClamp, tone)

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val refs = properties.bindChildReferences(children).orEmpty()
        val single = properties.bind(child)
        val render: @Composable (Boolean) -> Unit = { wrapItems ->
            if (single != null) {
                if (wrapItems) Box(propagateMinConstraints = true) { CardChild(single) } else CardChild(single)
            }
            else refs.forEach { ref ->
                key(ref.id, ref.baseDataPath) {
                    val item: @Composable () -> Unit = { when (val state = observeA2uiComponentState(ref)) {
                        is A2uiComponentState.Success -> A2uiComponent(state.component)
                        is A2uiComponentState.Error -> {
                            LaunchedEffect(state.exception) { reportError(state.exception) }
                            Text("⚠", color = MaterialTheme.colorScheme.error)
                        }
                        A2uiComponentState.Loading -> LinearProgressIndicator()
                    } }
                    if (wrapItems) Box(propagateMinConstraints = true) { item() } else item()
                }
            }
        }
        val spacing = cardSpacing(properties.bind(gap))
        val horizontal = when (properties.bind(align)) {
            "center" -> Alignment.CenterHorizontally; "end" -> Alignment.End; else -> Alignment.Start
        }
        val vertical = when (properties.bind(align)) {
            "center" -> Alignment.CenterVertically; "end" -> Alignment.Bottom; else -> Alignment.Top
        }
        when (name) {
            "Frame" -> {
                val ratio = properties.bind(aspectRatio)?.toFloat()
                Box(modifier
                    .widthIn(min = (properties.bind(minWidth)?.toInt() ?: 0).dp, max = (properties.bind(maxWidth)?.toInt() ?: 960).dp)
                    .heightIn(min = (properties.bind(minHeight)?.toInt() ?: 0).dp, max = (properties.bind(maxHeight)?.toInt() ?: 480).dp)
                    .then(if (ratio != null) Modifier.layout { measurable, bounds ->
                        val size = cardFrameSize(bounds, ratio)
                        val measured = measurable.measure(Constraints.fixed(size.width, size.height))
                        layout(size.width, size.height) { measured.place(0, 0) }
                    } else Modifier).clipToBounds()) { render(false) }
            }
            "Text" -> Text(properties.bind(text).orEmpty(),
                (if (properties.bind(textAlign) != null) modifier.fillMaxWidth() else modifier)
                    .then(if (properties.bind(variant) in setOf("h1","h2","h3","h4","h5")) Modifier.semantics { heading() } else Modifier),
                // A multi-column cell is narrow: large headings share one smaller size there.
                style = if (LocalCardCompactHeadings.current && properties.bind(variant) in setOf("h1","h2","h3","h4"))
                    MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp)
                else when (properties.bind(variant)) {
                    "h1" -> MaterialTheme.typography.headlineLarge
                    "h2" -> MaterialTheme.typography.headlineMedium
                    "h3" -> MaterialTheme.typography.headlineSmall
                    "h4" -> MaterialTheme.typography.titleLarge
                    "h5" -> MaterialTheme.typography.titleMedium
                    "caption" -> MaterialTheme.typography.bodySmall
                    else -> MaterialTheme.typography.bodyMedium
                },
                // Unspecified keeps the surrounding ink (e.g. an accentMuted Panel) for untoned text.
                color = cardToneInk(properties.bind(tone), MaterialTheme.colorScheme, LocalA2uiBackdrop.current)
                    ?: Color.Unspecified,
                textAlign = when (properties.bind(textAlign)) {
                    "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start
                },
                maxLines = properties.bind(lineClamp)?.toInt()?.coerceIn(1, 8) ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis)
            "Column" -> Column(modifier, Arrangement.spacedBy(spacing), horizontal) {
                CompositionLocalProvider(LocalCardRowItem provides false) { render(false) }
            }
            // Chat items (and Scroll children) receive unbounded height. The public List
            // contract is at most 32 static children, so let the enclosing container scroll
            // instead of nesting AndroidX's unbounded LazyColumn here.
            "List" -> Column(modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                CompositionLocalProvider(LocalCardRowItem provides false) { render(false) }
            }
            "Row" -> {
                val rowModifier = if (properties.bind(justify) != null) modifier.fillMaxWidth() else modifier
                val arrangement = when (properties.bind(justify)) {
                    "between" -> Arrangement.SpaceBetween
                    "center" -> Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
                    "end" -> Arrangement.spacedBy(spacing, Alignment.End)
                    else -> Arrangement.spacedBy(spacing)
                }
                CompositionLocalProvider(LocalCardRowItem provides true) {
                    val shares = (properties.bind(weights) as? List<*>)?.map { (it as Number).toInt() }
                    // Measure items against the available line width, then move an item
                    // to the next line instead of squeezing it into the previous remainder.
                    if (shares != null || properties.bind(wrap) == false) CardAdaptiveRow(
                        weights = shares ?: List(if (single != null) 1 else refs.size) { 0 }, gap = spacing,
                        minimum = (properties.bind(minItemWidth)?.toInt() ?: 120).dp,
                        arrangement = arrangement, vertical = vertical, modifier = rowModifier,
                    ) { render(true) }
                    else FlowRow(rowModifier,
                        horizontalArrangement = arrangement, verticalArrangement = Arrangement.spacedBy(spacing),
                        itemVerticalAlignment = vertical) { render(false) }
                }
            }
            "Panel" -> {
                val style = cardPanelStyle(properties.bind(background), MaterialTheme.colorScheme, LocalA2uiBackdrop.current)
                // A panel sharing a grid row stretches to the tallest cell, like a Display tile.
                val rowFill = LocalCardRowFill.current
                val slot = remember { Any() }
                if (rowFill != null) DisposableEffect(rowFill) { onDispose { rowFill.remove(slot) } }
                Surface(modifier.widthIn(max = (properties.bind(maxWidth)?.toInt() ?: 960).dp)
                    .then(if (LocalCardRowItem.current) Modifier else Modifier.fillMaxWidth())
                    .cardRowStretch(rowFill),
                    color = style.fill, contentColor = style.colors.onSurface,
                    shape = RoundedCornerShape(cardSpacing(properties.bind(radius), 16)),
                    border = if (properties.bind(border) == true) BorderStroke(1.dp, style.colors.outlineVariant) else null) {
                    MaterialTheme(colorScheme = style.colors) {
                        // Tiles nested in this panel belong to it, not to the outer row.
                        CompositionLocalProvider(LocalA2uiBackdrop provides style.backdrop, LocalCardRowFill provides null) {
                            Box(Modifier.wrapContentHeight(Alignment.Top).cardRowReport(rowFill, slot)
                                .padding(cardSpacing(properties.bind(padding), 16))) { render(false) }
                        }
                    }
                }
            }
            // The height constraint MUST precede scrolling inside a vertically unbounded chat.
            "Scroll" -> Box(modifier.fillMaxWidth()
                .heightIn(max = (properties.bind(maxHeight)?.toInt() ?: 240).coerceIn(80, 480).dp)
                .verticalScroll(rememberScrollState())) {
                CompositionLocalProvider(LocalCardRowItem provides false) { render(false) }
            }
        }
    }
}

@Composable
internal fun A2uiComponentScope.CardChild(id: String) {
    when (val state = observeA2uiComponentState(id)) {
        is A2uiComponentState.Success -> A2uiComponent(state.component)
        is A2uiComponentState.Error -> {
            LaunchedEffect(state.exception) { reportError(state.exception) }
            Text("⚠", color = MaterialTheme.colorScheme.error)
        }
        A2uiComponentState.Loading -> LinearProgressIndicator()
    }
}
