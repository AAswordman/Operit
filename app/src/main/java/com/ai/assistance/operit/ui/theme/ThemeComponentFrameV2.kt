package com.ai.assistance.operit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.theme.packages.ThemeComponentFrameSpecV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentFrameStrokeV2
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenIdV1
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneTokenResolverV1

@Immutable
internal data class ResolvedThemeComponentFrameStrokeV2(
    val color: Color,
    val widthDp: Float,
)

@Immutable
internal sealed interface ResolvedThemeComponentFrameV2 {
    @Immutable
    data object None : ResolvedThemeComponentFrameV2

    @Immutable
    data class RoundRect(
        val cornerRadiusDp: Float,
        val border: ResolvedThemeComponentFrameStrokeV2?,
    ) : ResolvedThemeComponentFrameV2

    @Immutable
    data class CutCorners(
        val cutSizeDp: Float,
        val border: ResolvedThemeComponentFrameStrokeV2,
        val accent: ResolvedThemeComponentFrameStrokeV2?,
    ) : ResolvedThemeComponentFrameV2

    @Immutable
    data class HudNotched(
        val cutSizeDp: Float,
        val notchWidthFraction: Float,
        val notchDepthDp: Float,
        val border: ResolvedThemeComponentFrameStrokeV2,
        val accent: ResolvedThemeComponentFrameStrokeV2?,
    ) : ResolvedThemeComponentFrameV2

    @Immutable
    data class CornerBrackets(
        val cornerCutDp: Float,
        val bracketLengthDp: Float,
        val border: ResolvedThemeComponentFrameStrokeV2,
        val accent: ResolvedThemeComponentFrameStrokeV2?,
    ) : ResolvedThemeComponentFrameV2

    @Immutable
    data class SegmentedRail(
        val cornerCutDp: Float,
        val railInsetDp: Float,
        val segmentLengthDp: Float,
        val border: ResolvedThemeComponentFrameStrokeV2,
        val accent: ResolvedThemeComponentFrameStrokeV2,
    ) : ResolvedThemeComponentFrameV2
}

internal data class ThemeComponentFrameRenderPlanV2(
    val pathStrokes: List<ThemeComponentFramePathStrokeV2>,
    val lineStrokes: List<ThemeComponentFrameLineStrokeV2>,
)

internal data class ThemeComponentFramePathStrokeV2(
    val path: Path,
    val color: Color,
    val widthPx: Float,
)

internal data class ThemeComponentFrameLineStrokeV2(
    val start: Offset,
    val end: Offset,
    val color: Color,
    val widthPx: Float,
)

internal fun ThemeComponentFrameSpecV2.resolve(
    tokens: ThemeSceneTokenResolverV1,
    darkTheme: Boolean,
): ResolvedThemeComponentFrameV2 =
    when (this) {
        ThemeComponentFrameSpecV2.None -> ResolvedThemeComponentFrameV2.None
        is ThemeComponentFrameSpecV2.RoundRect ->
            ResolvedThemeComponentFrameV2.RoundRect(
                cornerRadiusDp = cornerRadiusDp,
                border = border?.resolve(tokens, darkTheme),
            )

        is ThemeComponentFrameSpecV2.CutCorners ->
            ResolvedThemeComponentFrameV2.CutCorners(
                cutSizeDp = cutSizeDp,
                border = border.resolve(tokens, darkTheme),
                accent = accent?.resolve(tokens, darkTheme),
            )

        is ThemeComponentFrameSpecV2.HudNotched ->
            ResolvedThemeComponentFrameV2.HudNotched(
                cutSizeDp = cutSizeDp,
                notchWidthFraction = notchWidthFraction,
                notchDepthDp = notchDepthDp,
                border = border.resolve(tokens, darkTheme),
                accent = accent?.resolve(tokens, darkTheme),
            )

        is ThemeComponentFrameSpecV2.CornerBrackets ->
            ResolvedThemeComponentFrameV2.CornerBrackets(
                cornerCutDp = cornerCutDp,
                bracketLengthDp = bracketLengthDp,
                border = border.resolve(tokens, darkTheme),
                accent = accent?.resolve(tokens, darkTheme),
            )

        is ThemeComponentFrameSpecV2.SegmentedRail ->
            ResolvedThemeComponentFrameV2.SegmentedRail(
                cornerCutDp = cornerCutDp,
                railInsetDp = railInsetDp,
                segmentLengthDp = segmentLengthDp,
                border = border.resolve(tokens, darkTheme),
                accent = accent.resolve(tokens, darkTheme),
            )
    }

private fun ThemeComponentFrameStrokeV2.resolve(
    tokens: ThemeSceneTokenResolverV1,
    darkTheme: Boolean,
): ResolvedThemeComponentFrameStrokeV2 =
    ResolvedThemeComponentFrameStrokeV2(
        color = tokens.color(ThemeSceneTokenIdV1(token), darkTheme),
        widthDp = widthDp,
    )

internal fun ResolvedThemeComponentFrameV2.toComposeShape(): Shape =
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline = Outline.Generic(this@toComposeShape.framePath(size, density))
    }

internal fun ResolvedThemeComponentFrameV2.createRenderPlan(
    size: Size,
    density: Density,
): ThemeComponentFrameRenderPlanV2 {
    val pathStrokes = mutableListOf<ThemeComponentFramePathStrokeV2>()
    val lineStrokes = mutableListOf<ThemeComponentFrameLineStrokeV2>()
    val frame = this
    fun addPath(
        path: Path,
        stroke: ResolvedThemeComponentFrameStrokeV2,
    ) {
        pathStrokes +=
            ThemeComponentFramePathStrokeV2(
                path = path,
                color = stroke.color,
                widthPx = stroke.widthDp.px(density),
            )
    }

    when (frame) {
        ResolvedThemeComponentFrameV2.None -> Unit
        is ResolvedThemeComponentFrameV2.RoundRect -> {
            frame.border?.let { stroke -> addPath(frame.framePath(size, density), stroke) }
        }

        is ResolvedThemeComponentFrameV2.CutCorners -> {
            addPath(frame.framePath(size, density), frame.border)
            frame.accent?.let { accent ->
                lineStrokes += cutCornerAccentLines(frame, accent, size, density)
            }
        }

        is ResolvedThemeComponentFrameV2.HudNotched -> {
            addPath(frame.framePath(size, density), frame.border)
            frame.accent?.let { accent ->
                lineStrokes += hudAccentLines(frame, accent, size, density)
            }
        }

        is ResolvedThemeComponentFrameV2.CornerBrackets -> {
            val cut = frame.cornerCutDp.px(density).coerceAtMost(minOf(size.width, size.height) / 2f)
            val length = frame.bracketLengthDp.px(density).coerceAtMost(minOf(size.width, size.height) / 2f)
            bracketPath(size, BracketCorner.TOP_START, cut, length, frame.border.widthDp.px(density))
                .let { path -> addPath(path, frame.border) }
            bracketPath(size, BracketCorner.BOTTOM_END, cut, length, frame.border.widthDp.px(density))
                .let { path -> addPath(path, frame.border) }
            frame.accent?.let { accent ->
                bracketPath(size, BracketCorner.TOP_END, cut, length, accent.widthDp.px(density))
                    .let { path -> addPath(path, accent) }
                bracketPath(size, BracketCorner.BOTTOM_START, cut, length, accent.widthDp.px(density))
                    .let { path -> addPath(path, accent) }
            }
        }

        is ResolvedThemeComponentFrameV2.SegmentedRail -> {
            lineStrokes += segmentedRailLines(frame, size, density)
        }
    }
    return ThemeComponentFrameRenderPlanV2(pathStrokes = pathStrokes, lineStrokes = lineStrokes)
}

internal fun DrawScope.drawThemeComponentFrame(plan: ThemeComponentFrameRenderPlanV2) {
    plan.pathStrokes.forEach { stroke ->
        drawPath(path = stroke.path, color = stroke.color, style = Stroke(width = stroke.widthPx))
    }
    plan.lineStrokes.forEach { stroke ->
        drawLine(
            color = stroke.color,
            start = stroke.start,
            end = stroke.end,
            strokeWidth = stroke.widthPx,
        )
    }
}

private fun ResolvedThemeComponentFrameV2.framePath(
    size: Size,
    density: Density,
): Path =
    when (this) {
        ResolvedThemeComponentFrameV2.None -> rectanglePath(size)
        is ResolvedThemeComponentFrameV2.RoundRect -> roundRectPath(size, cornerRadiusDp.px(density))
        is ResolvedThemeComponentFrameV2.CutCorners -> cutCornerPath(size, cutSizeDp.px(density))
        is ResolvedThemeComponentFrameV2.HudNotched ->
            hudNotchedPath(
                size = size,
                cutPx = cutSizeDp.px(density),
                notchWidthFraction = notchWidthFraction,
                notchDepthPx = notchDepthDp.px(density),
            )

        is ResolvedThemeComponentFrameV2.CornerBrackets -> cutCornerPath(size, cornerCutDp.px(density))
        is ResolvedThemeComponentFrameV2.SegmentedRail -> cutCornerPath(size, cornerCutDp.px(density))
    }

private fun rectanglePath(size: Size): Path =
    Path().apply {
        addRect(Rect(0f, 0f, size.width, size.height))
    }

private fun roundRectPath(
    size: Size,
    requestedRadiusPx: Float,
): Path {
    val radius = requestedRadiusPx.coerceAtMost(minOf(size.width, size.height) / 2f)
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(radius, radius),
            ),
        )
    }
}

private fun cutCornerPath(
    size: Size,
    requestedCutPx: Float,
): Path {
    val cut = requestedCutPx.coerceAtMost(minOf(size.width, size.height) / 2f)
    return Path().apply {
        moveTo(cut, 0f)
        lineTo(size.width - cut, 0f)
        lineTo(size.width, cut)
        lineTo(size.width, size.height - cut)
        lineTo(size.width - cut, size.height)
        lineTo(cut, size.height)
        lineTo(0f, size.height - cut)
        lineTo(0f, cut)
        close()
    }
}

private fun hudNotchedPath(
    size: Size,
    cutPx: Float,
    notchWidthFraction: Float,
    notchDepthPx: Float,
): Path {
    val cut = cutPx.coerceAtMost(minOf(size.width, size.height) / 2f)
    val notchWidth = (size.width * notchWidthFraction).coerceAtMost(size.width - cut * 2f)
    val notchStart = (size.width - notchWidth) / 2f
    val notchEnd = notchStart + notchWidth
    val notchDepth = notchDepthPx.coerceAtMost(size.height / 3f)
    val slope = notchDepth.coerceAtMost(notchWidth / 4f)
    return Path().apply {
        moveTo(cut, 0f)
        lineTo(notchStart, 0f)
        lineTo(notchStart + slope, notchDepth)
        lineTo(notchEnd - slope, notchDepth)
        lineTo(notchEnd, 0f)
        lineTo(size.width - cut, 0f)
        lineTo(size.width, cut)
        lineTo(size.width, size.height - cut)
        lineTo(size.width - cut, size.height)
        lineTo(notchEnd, size.height)
        lineTo(notchEnd - slope, size.height - notchDepth)
        lineTo(notchStart + slope, size.height - notchDepth)
        lineTo(notchStart, size.height)
        lineTo(cut, size.height)
        lineTo(0f, size.height - cut)
        lineTo(0f, cut)
        close()
    }
}

private fun cutCornerAccentLines(
    frame: ResolvedThemeComponentFrameV2.CutCorners,
    accent: ResolvedThemeComponentFrameStrokeV2,
    size: Size,
    density: Density,
): List<ThemeComponentFrameLineStrokeV2> {
    val cut = frame.cutSizeDp.px(density).coerceAtMost(minOf(size.width, size.height) / 2f)
    val length = minOf(size.width * 0.28f, 56f.px(density))
    val width = accent.widthDp.px(density)
    val inset = width / 2f
    return listOf(
        ThemeComponentFrameLineStrokeV2(
            start = Offset(cut + inset, inset),
            end = Offset((cut + length).coerceAtMost(size.width - cut), inset),
            color = accent.color,
            widthPx = width,
        ),
        ThemeComponentFrameLineStrokeV2(
            start = Offset((size.width - cut - length).coerceAtLeast(cut), size.height - inset),
            end = Offset(size.width - cut - inset, size.height - inset),
            color = accent.color,
            widthPx = width,
        ),
    )
}

private fun hudAccentLines(
    frame: ResolvedThemeComponentFrameV2.HudNotched,
    accent: ResolvedThemeComponentFrameStrokeV2,
    size: Size,
    density: Density,
): List<ThemeComponentFrameLineStrokeV2> {
    val cut = frame.cutSizeDp.px(density).coerceAtMost(minOf(size.width, size.height) / 2f)
    val notchWidth = (size.width * frame.notchWidthFraction).coerceAtMost(size.width - cut * 2f)
    val notchStart = (size.width - notchWidth) / 2f
    val notchEnd = notchStart + notchWidth
    val width = accent.widthDp.px(density)
    val inset = width / 2f
    return listOf(
        ThemeComponentFrameLineStrokeV2(
            start = Offset(cut + inset, inset),
            end = Offset(notchStart - inset, inset),
            color = accent.color,
            widthPx = width,
        ),
        ThemeComponentFrameLineStrokeV2(
            start = Offset(notchEnd + inset, size.height - inset),
            end = Offset(size.width - cut - inset, size.height - inset),
            color = accent.color,
            widthPx = width,
        ),
    )
}

private enum class BracketCorner {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

private fun bracketPath(
    size: Size,
    corner: BracketCorner,
    cut: Float,
    length: Float,
    strokeWidth: Float,
): Path {
    val inset = strokeWidth / 2f
    return Path().apply {
        when (corner) {
            BracketCorner.TOP_START -> {
                moveTo(cut + length, inset)
                lineTo(cut, inset)
                lineTo(inset, cut)
                lineTo(inset, cut + length)
            }

            BracketCorner.TOP_END -> {
                moveTo(size.width - cut - length, inset)
                lineTo(size.width - cut, inset)
                lineTo(size.width - inset, cut)
                lineTo(size.width - inset, cut + length)
            }

            BracketCorner.BOTTOM_START -> {
                moveTo(inset, size.height - cut - length)
                lineTo(inset, size.height - cut)
                lineTo(cut, size.height - inset)
                lineTo(cut + length, size.height - inset)
            }

            BracketCorner.BOTTOM_END -> {
                moveTo(size.width - cut - length, size.height - inset)
                lineTo(size.width - cut, size.height - inset)
                lineTo(size.width - inset, size.height - cut)
                lineTo(size.width - inset, size.height - cut - length)
            }
        }
    }
}

private fun segmentedRailLines(
    frame: ResolvedThemeComponentFrameV2.SegmentedRail,
    size: Size,
    density: Density,
): List<ThemeComponentFrameLineStrokeV2> {
    val cut = frame.cornerCutDp.px(density).coerceAtMost(minOf(size.width, size.height) / 2f)
    val inset = frame.railInsetDp.px(density).coerceAtMost(minOf(size.width, size.height) / 3f)
    val segmentLength = frame.segmentLengthDp.px(density).coerceAtMost(size.width / 2f)
    val borderWidth = frame.border.widthDp.px(density)
    val accentWidth = frame.accent.widthDp.px(density)
    val top = inset + borderWidth / 2f
    val bottom = size.height - inset - borderWidth / 2f
    val left = cut + borderWidth / 2f
    val right = size.width - cut - borderWidth / 2f

    return listOf(
        ThemeComponentFrameLineStrokeV2(
            start = Offset(left, top),
            end = Offset(right, top),
            color = frame.border.color,
            widthPx = borderWidth,
        ),
        ThemeComponentFrameLineStrokeV2(
            start = Offset(left, bottom),
            end = Offset(right, bottom),
            color = frame.border.color,
            widthPx = borderWidth,
        ),
        ThemeComponentFrameLineStrokeV2(
            start = Offset(borderWidth / 2f, cut),
            end = Offset(borderWidth / 2f, size.height - cut),
            color = frame.border.color,
            widthPx = borderWidth,
        ),
        ThemeComponentFrameLineStrokeV2(
            start = Offset(size.width - borderWidth / 2f, cut),
            end = Offset(size.width - borderWidth / 2f, size.height - cut),
            color = frame.border.color,
            widthPx = borderWidth,
        ),
        ThemeComponentFrameLineStrokeV2(
            start = Offset(left + inset, top),
            end = Offset((left + inset + segmentLength).coerceAtMost(right), top),
            color = frame.accent.color,
            widthPx = accentWidth,
        ),
        ThemeComponentFrameLineStrokeV2(
            start = Offset((right - inset - segmentLength).coerceAtLeast(left), bottom),
            end = Offset(right - inset, bottom),
            color = frame.accent.color,
            widthPx = accentWidth,
        ),
    )
}

private fun Float.px(density: Density): Float = with(density) { this@px.dp.toPx() }
