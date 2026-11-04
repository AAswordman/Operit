package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The host embeds UI without a frame; Card is the explicit, optional grouping container. */
internal object FramedCard : A2uiBasicCatalogV1.Card {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        childId: String, accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?, modifier: Modifier,
    ) {
        val child = observeA2uiComponentState(childId)
        val accessible = modifier.fillMaxWidth().semantics {
            listOfNotNull(accessibility?.label, accessibility?.description)
                .takeIf { it.isNotEmpty() }?.let { contentDescription = it.joinToString(". ") }
        }
        Surface(accessible, shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.padding(16.dp)) {
                when (child) {
                    is A2uiComponentState.Success -> A2uiComponent(child.component)
                    is A2uiComponentState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is A2uiComponentState.Error -> {
                        LaunchedEffect(child.exception) { reportError(child.exception) }
                        Text("⚠", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Fixed semantic colors remain readable with either card palette. No AI-supplied arbitrary colors. */
internal fun cardToneColors(tone: String?, scheme: ColorScheme): Pair<Color, Color> {
    return cardSemanticToneColors(tone, scheme.surface.luminance() < .5f)
        ?: (scheme.onSurfaceVariant to scheme.surfaceContainerHigh)
}

/** Ink and fill for success/info/warning/error; null for neutral or unknown tones. */
internal fun cardSemanticToneColors(tone: String?, dark: Boolean): Pair<Color, Color>? {
    val ink = when (tone) {
        "success" -> if (dark) Color(0xFF9BE0B0) else Color(0xFF256A3C)
        "info" -> if (dark) Color(0xFFABCFFF) else Color(0xFF245A98)
        "warning" -> if (dark) Color(0xFFFFD58A) else Color(0xFF7B5000)
        "error" -> if (dark) Color(0xFFFFB4B4) else Color(0xFF9C3030)
        else -> return null
    }
    val background = when (tone) {
        "success" -> if (dark) Color(0xFF213C2A) else Color(0xFFE0F2E5)
        "info" -> if (dark) Color(0xFF26384F) else Color(0xFFE4EEFC)
        "warning" -> if (dark) Color(0xFF433722) else Color(0xFFFFF1D7)
        else -> if (dark) Color(0xFF4A292B) else Color(0xFFFBE5E5)
    }
    return ink to background
}

/**
 * Text ink for a tone (Text, RichText runs, InfoRow values); null keeps the caller's default ink.
 * The fixed semantic hues are re-checked against the bubble behind a frameless card, so a custom
 * backdrop cannot wash them out; if no shade of the hue reads there, the card's own text color wins.
 */
internal fun cardToneInk(tone: String?, scheme: ColorScheme, backdrop: Color = Color.Unspecified): Color? = when (tone) {
    "primary" -> scheme.primary
    "muted" -> scheme.onSurfaceVariant
    else -> cardSemanticToneColors(tone, scheme.surface.luminance() < .5f)?.first?.let { hue ->
        val ground = if (backdrop.isSpecified) backdrop else scheme.surface
        ensureReadableOn(hue, ground, 4.5).takeIf { resolvedContrastRatio(it, ground) >= 4.5 } ?: scheme.onSurface
    }
}

@Composable
internal fun CardStatusBadge(text: String, tone: String?, modifier: Modifier = Modifier) {
    val (ink, fill) = cardToneColors(tone, MaterialTheme.colorScheme)
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = fill) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun CardHeader(text: String, subtitle: String, status: String, tone: String?, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Narrow cards and accessibility font sizes keep the title and status on separate lines.
        val stacked = maxWidth < 240.dp || LocalDensity.current.fontScale > 1.3f
        val badgeWidth = maxWidth * .4f
        val title: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title(Modifier.fillMaxWidth())
            if (status.isNotBlank()) CardStatusBadge(status, tone)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            title(Modifier.weight(1f))
            if (status.isNotBlank()) CardStatusBadge(status, tone, Modifier.widthIn(max = badgeWidth))
        }
    }
}

/**
 * The Display tiles of one grid row report the height their content needs, and each tile is then
 * stretched to the tallest one, so a row of statistics lines up. Heights are measured with no
 * minimum, so the row shrinks again when its content does.
 */
@Stable
internal class CardRowFill {
    private val heights = mutableStateMapOf<Any, Int>()
    val tallest: Int get() = heights.values.maxOrNull() ?: 0

    fun report(slot: Any, height: Int) {
        // Called while measuring. Writing only on change is what lets the row settle; an
        // unconditional write would remeasure the row forever.
        if (heights[slot] != height) heights[slot] = height
    }

    fun remove(slot: Any) {
        heights.remove(slot)
    }
}

/** Set only for a Display or Panel that shares a grid row with other cells. */
internal val LocalCardRowFill = compositionLocalOf<CardRowFill?> { null }

/** Stretches a tile to the tallest tile of its row. */
internal fun Modifier.cardRowStretch(rowFill: CardRowFill?): Modifier =
    if (rowFill == null) this else layout { measurable, constraints ->
        val tallest = rowFill.tallest.coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = tallest))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * Reports the height the tile's content needs. Put it inside the stretched surface, after
 * `wrapContentHeight`, so the content is measured without the stretched minimum and the row can
 * shrink again.
 */
internal fun Modifier.cardRowReport(rowFill: CardRowFill?, slot: Any): Modifier =
    if (rowFill == null) this else layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        rowFill.report(slot, placeable.height)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * True inside a cell of a grid with two or more columns. Large heading variants step down to one
 * shared size there, so short titles stay on one line and every cell of the row matches.
 */
internal val LocalCardCompactHeadings = compositionLocalOf { false }

/**
 * Large statistic value. Shrinks to fit one line so digits are never cut, then wraps at the
 * smallest size into balanced lines: a value inside a chat bubble must never need horizontal
 * dragging.
 */
@Composable
internal fun CardDisplayValue(text: String, atStart: Boolean) {
    val base = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold)
    // Room for every size step from 100% down to 50%, so remeasuring an unchanged value hits the cache.
    val measurer = rememberTextMeasurer(cacheSize = 12)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val largest = base.fontSize.value
        val smallest = largest * 0.5f
        var size = largest
        while (measurer.measure(text, style = base.copy(fontSize = size.sp),
                softWrap = false, maxLines = 1).size.width > width && size > smallest) size = (size - 2f).coerceAtLeast(smallest)
        val fitted = size.sp
        val lineHeight = if (base.lineHeight.isSp) base.lineHeight * (fitted.value / largest) else base.lineHeight
        Text(text, Modifier.fillMaxWidth(),
            style = base.copy(fontSize = fitted, lineHeight = lineHeight, lineBreak = LineBreak.Heading),
            textAlign = if (atStart) TextAlign.Start else TextAlign.End)
    }
}

@Composable
internal fun CardInfoRow(label: String, value: String, tone: String?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val valueColor = cardToneInk(tone, scheme, LocalA2uiBackdrop.current) ?: scheme.onSurface
    val labelStyle = MaterialTheme.typography.bodyMedium
    val valueStyle = labelStyle.copy(fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val required = measurer.measure(label, style = labelStyle, softWrap = false).size.width +
            measurer.measure(value, style = valueStyle, softWrap = false).size.width
        val stack = '\n' in label || '\n' in value || required > with(density) { (maxWidth - 16.dp).toPx() }
        if (stack) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = scheme.onSurfaceVariant, style = labelStyle)
            Text(value, color = valueColor, style = valueStyle)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = scheme.onSurfaceVariant, style = labelStyle)
            Text(value, color = valueColor, style = valueStyle, textAlign = TextAlign.End)
        }
    }
}
