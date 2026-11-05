package com.ai.assistance.operit.ui.features.annualreport.entry

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.annualreport.AnnualReportEdition
import com.ai.assistance.operit.ui.features.annualreport.AnnualReportLauncher
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private class ParsedStroke(val stroke: BadgeStroke, val path: Path, val color: Color)

// 徽章是一张贴在抽屉上的奶油色纸片，深浅色主题下颜色相同，像真实的贴纸。
private fun colorOf(ink: BadgeInk): Color =
    when (ink) {
        BadgeInk.PAPER -> Color(0xFFFFF4DA)
        BadgeInk.INK -> Color(0xFF26283B)
        BadgeInk.GHOST -> Color(0x6626283B)
        BadgeInk.LAVENDER -> Color(0xFF8574FF)
        BadgeInk.CORAL -> Color(0xFFFF6F5E)
        BadgeInk.YELLOW -> Color(0xFFFFC93A)
        BadgeInk.TAPE -> Color(0x618574FF)
        BadgeInk.SHADOW -> Color(0xD926283B)
    }

/**
 * 抽屉头部的年度报告入口：代码绘制的手绘徽章。
 * 抽屉打开时播放动画：线条按低帧率在几个手绘版本之间轮换，闪光一明一暗，纸片轻轻摇摆；
 * 抽屉收起时停在第一帧，不占用绘制。
 */
@Composable
fun AnnualReportEntryBadge(
    active: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val frames =
        remember {
            AnnualReportBadgeArt.frames.map { frame ->
                frame.map { stroke ->
                    ParsedStroke(stroke, PathParser().parsePathString(stroke.pathData).toPath(), colorOf(stroke.ink))
                }
            }
        }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (pressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
            label = "annualReportBadgePress",
        )
    val seconds by
        produceState(initialValue = 0f, active) {
            if (!active) {
                value = 0f
                return@produceState
            }
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> value = (now - start) / 1_000_000_000f }
            }
        }
    val description = stringResource(R.string.annual_report_entry_description, AnnualReportEdition.YEAR)

    Canvas(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    rotationZ = sin(seconds * 1.6f) * 1.4f
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = description,
                ) {
                    AnnualReportLauncher.launch(context, AnnualReportEdition.YEAR, dark)
                },
        contentDescription = description,
    ) {
        // 等比缩放到可用区域内并居中。
        val fit = min(size.width / AnnualReportBadgeArt.WIDTH, size.height / AnnualReportBadgeArt.HEIGHT)
        val offsetX = (size.width - AnnualReportBadgeArt.WIDTH * fit) / 2f
        val offsetY = (size.height - AnnualReportBadgeArt.HEIGHT * fit) / 2f
        val frame = frames[(seconds * BOIL_FPS).toInt() % frames.size]
        withTransform({
            translate(offsetX, offsetY)
            scale(fit, fit, pivot = Offset.Zero)
        }) {
            for (parsed in frame) {
                if (parsed.stroke.kind == BadgeStrokeKind.FILL) {
                    drawPath(parsed.path, parsed.color)
                } else {
                    drawPath(
                        parsed.path,
                        parsed.color,
                        style = Stroke(width = parsed.stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            for (sparkle in AnnualReportBadgeArt.sparkles) {
                val pulse = 0.72f + 0.28f * sin(((seconds * 0.9f + sparkle.phase) * 2f * PI).toFloat())
                drawSparkle(sparkle, if (active) pulse else 1f)
            }
        }
    }
}

private const val BOIL_FPS = 6.5f

private fun DrawScope.drawSparkle(sparkle: BadgeSparkle, pulse: Float) {
    val size = sparkle.size * pulse
    val waist = size * 0.22f
    val path =
        Path().apply {
            moveTo(sparkle.x, sparkle.y - size)
            lineTo(sparkle.x + waist, sparkle.y - waist)
            lineTo(sparkle.x + size, sparkle.y)
            lineTo(sparkle.x + waist, sparkle.y + waist)
            lineTo(sparkle.x, sparkle.y + size)
            lineTo(sparkle.x - waist, sparkle.y + waist)
            lineTo(sparkle.x - size, sparkle.y)
            lineTo(sparkle.x - waist, sparkle.y - waist)
            close()
        }
    drawPath(path, colorOf(sparkle.ink).copy(alpha = 0.55f + 0.45f * pulse))
}
