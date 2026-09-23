package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import kotlin.math.max

@Composable
internal fun CanvasExpandableHeaderRow(
    title: String,
    semanticDescription: String,
    expanded: Boolean,
    titleColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    rotationDegrees: Float = if (expanded) 90f else 0f,
    shimmerShiftPx: Float? = null,
    titleAlpha: Float = 1f,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val arrowPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowRight)
    val interactionSource = remember { MutableInteractionSource() }
    val arrowTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val shimmerHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
    val titleStyle =
        MaterialTheme.typography.labelMedium.copy(
            color = titleColor.copy(alpha = titleColor.alpha * titleAlpha),
        )

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = title
                    stateDescription = semanticDescription
                    role = Role.Button
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                )
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }.fastCoerceAtLeast(1)
        val topBottomPaddingPx = 0
        val iconSizePx = with(density) { 20.dp.roundToPx().toFloat() }
        val gapPx = with(density) { 4.dp.roundToPx().toFloat() }
        val textMaxWidth = (widthPx - iconSizePx.toInt() - gapPx.toInt()).fastCoerceAtLeast(0)
        val titleLayout =
            remember(title, titleStyle, textMeasurer, textMaxWidth) {
                textMeasurer.measure(
                    text = AnnotatedString(title),
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = textMaxWidth),
                )
            }
        val contentHeightPx = max(iconSizePx.toInt(), titleLayout.size.height)
        val totalHeightPx = contentHeightPx + topBottomPaddingPx * 2

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { totalHeightPx.toDp() })
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val contentTop = topBottomPaddingPx.toFloat()
                val arrowTop = contentTop + (contentHeightPx - iconSizePx) / 2f
                val arrowLeft = 0f
                rotate(
                    degrees = rotationDegrees,
                    pivot = Offset(arrowLeft + iconSizePx / 2f, arrowTop + iconSizePx / 2f),
                ) {
                    translate(left = arrowLeft, top = arrowTop) {
                        with(arrowPainter) {
                            draw(
                                size = Size(iconSizePx, iconSizePx),
                                colorFilter = ColorFilter.tint(arrowTint),
                            )
                        }
                    }
                }

                val titleX = iconSizePx + gapPx
                val titleY = contentTop + (contentHeightPx - titleLayout.size.height) / 2f
                val titleTopLeft = Offset(titleX, titleY)
                val titleSize = Size(titleLayout.size.width.toFloat(), titleLayout.size.height.toFloat())
                val hasShimmer = shimmerShiftPx != null

                if (hasShimmer) {
                    drawContext.canvas.saveLayer(
                        bounds =
                            Rect(
                                left = titleTopLeft.x,
                                top = titleTopLeft.y,
                                right = titleTopLeft.x + titleSize.width,
                                bottom = titleTopLeft.y + titleSize.height,
                            ),
                        paint = Paint(),
                    )
                }

                drawText(titleLayout, topLeft = titleTopLeft)

                if (shimmerShiftPx != null) {
                    drawRect(
                        brush =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        titleColor.copy(alpha = 0.20f),
                                        shimmerHighlightColor,
                                        titleColor.copy(alpha = 0.20f),
                                    ),
                                start = Offset(titleX + shimmerShiftPx - 140f, titleY),
                                end = Offset(titleX + shimmerShiftPx + 140f, titleY + titleLayout.size.height),
                            ),
                        topLeft = titleTopLeft,
                        size = titleSize,
                        blendMode = BlendMode.SrcAtop,
                    )
                    drawContext.canvas.restore()
                }
            }
        }
    }
}

@Composable
internal fun CanvasFontTextBlock(
    text: String,
    textColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.roundToPx() }.fastCoerceAtLeast(1)
        val outerVerticalPaddingPx = with(density) { 2.dp.roundToPx() }
        val horizontalPaddingPx = if (backgroundColor != Color.Transparent) with(density) { 8.dp.roundToPx() } else 0
        val innerVerticalPaddingPx = if (backgroundColor != Color.Transparent) with(density) { 6.dp.roundToPx() } else 0
        val cornerRadiusPx = with(density) { 6.dp.toPx() }

        val textLayout =
            remember(text, style, textMeasurer, widthPx, horizontalPaddingPx) {
                textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = style.copy(color = textColor),
                    constraints = Constraints(maxWidth = (widthPx - horizontalPaddingPx * 2).fastCoerceAtLeast(0)),
                )
            }

        val blockHeightPx = textLayout.size.height + innerVerticalPaddingPx * 2
        val totalHeightPx = blockHeightPx + outerVerticalPaddingPx * 2

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { totalHeightPx.toDp() })
        ) {
            val blockTop = outerVerticalPaddingPx.toFloat()
            if (backgroundColor != Color.Transparent) {
                drawRoundRect(
                    color = backgroundColor.copy(alpha = 0.18f),
                    topLeft = Offset(0f, blockTop),
                    size = Size(size.width, blockHeightPx.toFloat()),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )
            }

            drawText(
                textLayout,
                topLeft = Offset(horizontalPaddingPx.toFloat(), blockTop + innerVerticalPaddingPx),
            )
        }
    }
}

@Composable
internal fun CanvasPillLabel(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, color = textColor)

    BoxWithConstraints(modifier = modifier) {
        val horizontalPaddingPx = with(density) { 10.dp.roundToPx() }
        val verticalPaddingPx = with(density) { 6.dp.roundToPx() }
        val cornerRadiusPx = with(density) { 999.dp.toPx() }
        val borderWidthPx = with(density) { 1.dp.toPx() }

        val textLayout =
            remember(text, textStyle, textMeasurer) {
                textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

        val widthPx = textLayout.size.width + horizontalPaddingPx * 2
        val heightPx = textLayout.size.height + verticalPaddingPx * 2

        Canvas(
            modifier =
                Modifier
                    .width(with(density) { widthPx.toDp() })
                    .height(with(density) { heightPx.toDp() })
        ) {
            drawRoundRect(
                color = backgroundColor,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
            )
            drawRoundRect(
                color = borderColor,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = borderWidthPx),
            )
            drawText(
                textLayout,
                topLeft = Offset(horizontalPaddingPx.toFloat(), verticalPaddingPx.toFloat()),
            )
        }
    }
}
