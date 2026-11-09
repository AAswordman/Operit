package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import kotlin.math.max

private const val CANVAS_SINGLE_LINE_TEXT_LIMIT = 160

@Composable
internal fun CanvasToolSummaryRow(
    toolName: String,
    summary: String,
    semanticDescription: String,
    leadingIcon: ImageVector,
    titleColor: Color,
    summaryColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val iconPainter = rememberVectorPainter(leadingIcon)
    val titleStyle = MaterialTheme.typography.labelMedium.copy(color = titleColor)
    val summaryStyle = MaterialTheme.typography.bodySmall.copy(color = summaryColor)
    val measuredSummary = remember(summary) { summary.toCanvasSingleLineText() }
    val interactionSource = remember { MutableInteractionSource() }
    val clickableModifier =
        if (onClick != null) {
            Modifier
                .semantics {
                    contentDescription = semanticDescription
                    role = Role.Button
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                )
        } else {
            Modifier.semantics { contentDescription = semanticDescription }
        }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().then(clickableModifier)) {
        val widthPx = with(density) { maxWidth.roundToPx() }.fastCoerceAtLeast(1)
        val topPaddingPx = with(density) { 4.dp.roundToPx() }
        val iconSizePx = with(density) { 16.dp.roundToPx().toFloat() }
        val gap1Px = with(density) { 8.dp.roundToPx().toFloat() }
        val gap2Px = with(density) { 8.dp.roundToPx().toFloat() }
        val titleMinWidthPx = with(density) { 80.dp.roundToPx() }
        val titleMaxWidthPx = with(density) { 120.dp.roundToPx() }

        val unconstrainedTitleLayout =
            remember(toolName, titleStyle, textMeasurer) {
                textMeasurer.measure(
                    text = AnnotatedString(toolName),
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        val titleWidthPx =
            unconstrainedTitleLayout.size.width
                .coerceAtLeast(titleMinWidthPx)
                .coerceAtMost(titleMaxWidthPx)
        val titleLayout =
            remember(toolName, titleStyle, textMeasurer, titleWidthPx) {
                textMeasurer.measure(
                    text = AnnotatedString(toolName),
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = titleWidthPx),
                )
            }

        val summaryMaxWidth =
            (widthPx - iconSizePx.toInt() - gap1Px.toInt() - titleWidthPx - gap2Px.toInt())
                .fastCoerceAtLeast(0)
        val summaryLayout =
            remember(measuredSummary, summaryStyle, textMeasurer, summaryMaxWidth) {
                textMeasurer.measure(
                    text = AnnotatedString(measuredSummary),
                    style = summaryStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = summaryMaxWidth),
                )
            }

        val contentHeightPx =
            max(
                iconSizePx.toInt(),
                max(titleLayout.size.height, summaryLayout.size.height),
            )
        val totalHeightPx = topPaddingPx + contentHeightPx

        Canvas(
            modifier = Modifier.fillMaxWidth().height(with(density) { totalHeightPx.toDp() })
        ) {
            val iconTop = topPaddingPx + (contentHeightPx - iconSizePx) / 2f
            translate(top = iconTop) {
                with(iconPainter) {
                    draw(
                        size = Size(iconSizePx, iconSizePx),
                        colorFilter = ColorFilter.tint(titleColor.copy(alpha = 0.7f)),
                    )
                }
            }

            val textTopBase = topPaddingPx.toFloat()
            val titleX = iconSizePx + gap1Px
            val titleY = textTopBase + (contentHeightPx - titleLayout.size.height) / 2f
            drawText(titleLayout, topLeft = Offset(titleX, titleY))

            val summaryX = titleX + titleWidthPx + gap2Px
            val summaryY = textTopBase + (contentHeightPx - summaryLayout.size.height) / 2f
            drawText(summaryLayout, topLeft = Offset(summaryX, summaryY))
        }
    }
}

private fun String.toCanvasSingleLineText(): String {
    val normalized = replace('\r', ' ').replace('\n', ' ').trim()
    return if (normalized.length <= CANVAS_SINGLE_LINE_TEXT_LIMIT) {
        normalized
    } else {
        normalized.take(CANVAS_SINGLE_LINE_TEXT_LIMIT) + "..."
    }
}

@Composable
internal fun CanvasToolResultRow(
    summary: String,
    isSuccess: Boolean,
    semanticDescription: String,
    modifier: Modifier = Modifier,
    emphasizeSummary: Boolean = false,
    showStatusIcon: Boolean = true,
    onClick: (() -> Unit)? = null,
    onCopyClick: (() -> Unit)? = null,
    onStopClick: (() -> Unit)? = null,
    stopDescription: String = "",
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val arrowPainter = rememberVectorPainter(Icons.Default.SubdirectoryArrowRight)
    val statusPainter =
        rememberVectorPainter(
            if (isSuccess) {
                Icons.Default.Check
            } else {
                Icons.Default.Close
            }
        )
    val copyPainter = rememberVectorPainter(Icons.Default.ContentCopy)
    val stopPainter = rememberVectorPainter(Icons.Default.Stop)
    val summaryColor =
        if (isSuccess) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        }
    val summaryStyle =
        MaterialTheme.typography.bodySmall.copy(
            color = summaryColor,
        )
    val leadingTint =
        if (isSuccess) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        }
    val statusTint =
        if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val copyTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val rowInteractionSource = remember { MutableInteractionSource() }
    val copyInteractionSource = remember { MutableInteractionSource() }
    val stopInteractionSource = remember { MutableInteractionSource() }
    val rowModifier =
        if (onClick != null) {
            Modifier
                .semantics {
                    contentDescription = semanticDescription
                    role = Role.Button
                }
                .indication(rowInteractionSource, ripple())
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
        } else {
            Modifier.semantics { contentDescription = semanticDescription }
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .then(rowModifier)
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }.fastCoerceAtLeast(1)
        val startPaddingPx = with(density) { 24.dp.roundToPx() }
        val endPaddingPx = with(density) { 16.dp.roundToPx() }
        val verticalPaddingPx = with(density) { 2.dp.roundToPx() }
        val arrowSizePx = with(density) { 18.dp.roundToPx().toFloat() }
        val statusSizePx =
            if (showStatusIcon) with(density) { 14.dp.roundToPx().toFloat() } else 0f
        val gapPx = with(density) { 8.dp.roundToPx().toFloat() }
        val trailingSlotPx =
            if (onCopyClick != null || onStopClick != null) {
                with(density) { 24.dp.roundToPx() }
            } else {
                0
            }
        val trailingIconPx = with(density) { 14.dp.roundToPx().toFloat() }

        val summaryWidthPx =
            (widthPx - startPaddingPx - endPaddingPx - arrowSizePx.toInt() - gapPx.toInt() -
                statusSizePx.toInt() - gapPx.toInt() - trailingSlotPx)
                .fastCoerceAtLeast(0)
        val summaryLayout =
            remember(summary, summaryStyle, textMeasurer, summaryWidthPx) {
                textMeasurer.measure(
                    text = AnnotatedString(summary),
                    style = summaryStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = summaryWidthPx),
                )
            }

        val contentHeightPx =
            max(
                max(arrowSizePx.toInt(), statusSizePx.toInt()),
                max(summaryLayout.size.height, trailingSlotPx),
            )
        val totalHeightPx = verticalPaddingPx * 2 + contentHeightPx
        val trailingStartPx = widthPx - endPaddingPx - trailingSlotPx

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { totalHeightPx.toDp() })
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val contentTop = verticalPaddingPx.toFloat()
                val arrowTop = contentTop + (contentHeightPx - arrowSizePx) / 2f
                translate(left = startPaddingPx.toFloat(), top = arrowTop) {
                    with(arrowPainter) {
                        draw(
                            size = Size(arrowSizePx, arrowSizePx),
                            colorFilter = ColorFilter.tint(leadingTint),
                        )
                    }
                }

                val statusLeft = startPaddingPx + arrowSizePx + gapPx
                if (showStatusIcon) {
                    val statusTop = contentTop + (contentHeightPx - statusSizePx) / 2f
                    translate(left = statusLeft, top = statusTop) {
                        with(statusPainter) {
                            draw(
                                size = Size(statusSizePx, statusSizePx),
                                colorFilter = ColorFilter.tint(statusTint),
                            )
                        }
                    }
                }

                val textX = statusLeft + statusSizePx + gapPx
                val textY = contentTop + (contentHeightPx - summaryLayout.size.height) / 2f
                drawText(summaryLayout, topLeft = Offset(textX, textY))

                if (onCopyClick != null || onStopClick != null) {
                    val trailingLeft =
                        widthPx - endPaddingPx - trailingSlotPx +
                            (trailingSlotPx - trailingIconPx) / 2f
                    val trailingTop = contentTop + (contentHeightPx - trailingIconPx) / 2f
                    translate(left = trailingLeft, top = trailingTop) {
                        with(if (onStopClick != null) stopPainter else copyPainter) {
                            draw(
                                size = Size(trailingIconPx, trailingIconPx),
                                colorFilter = ColorFilter.tint(copyTint),
                            )
                        }
                    }
                }
            }

            val trailingAction = onStopClick ?: onCopyClick
            if (trailingAction != null && trailingSlotPx > 0) {
                Box(
                    modifier =
                        Modifier
                            .offset(x = with(density) { trailingStartPx.toDp() })
                            .width(with(density) { trailingSlotPx.toDp() })
                            .fillMaxHeight()
                            .semantics {
                                contentDescription =
                                    if (onStopClick != null) stopDescription else "$semanticDescription, copy"
                                role = Role.Button
                            }
                            .indication(
                                if (onStopClick != null) stopInteractionSource else copyInteractionSource,
                                ripple(bounded = false),
                            )
                            .clickable(
                                interactionSource =
                                    if (onStopClick != null) {
                                        stopInteractionSource
                                    } else {
                                        copyInteractionSource
                                    },
                                indication = null,
                                role = Role.Button,
                                onClick = trailingAction,
                            )
                )
            }
        }
    }
}

@Composable
internal fun CanvasStatusCard(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.roundToPx() }.fastCoerceAtLeast(1)
        val horizontalPaddingPx = with(density) { 12.dp.roundToPx() }
        val verticalPaddingPx = with(density) { 12.dp.roundToPx() }
        val outerVerticalPx = with(density) { 4.dp.roundToPx() }
        val cornerRadiusPx = with(density) { 8.dp.toPx() }
        val borderWidthPx = with(density) { 1.dp.toPx() }

        val textLayout =
            remember(text, textStyle, textMeasurer, widthPx, horizontalPaddingPx) {
                textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = textStyle,
                    constraints = Constraints(maxWidth = (widthPx - horizontalPaddingPx * 2).fastCoerceAtLeast(0)),
                )
            }
        val cardHeightPx = textLayout.size.height + verticalPaddingPx * 2
        val totalHeightPx = cardHeightPx + outerVerticalPx * 2

        Canvas(modifier = Modifier.fillMaxWidth().height(with(density) { totalHeightPx.toDp() })) {
            val cardTop = outerVerticalPx.toFloat()
            drawRoundRect(
                color = backgroundColor,
                topLeft = Offset(0f, cardTop),
                size = Size(size.width, cardHeightPx.toFloat()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx),
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(0f, cardTop),
                size = Size(size.width, cardHeightPx.toFloat()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = borderWidthPx),
            )

            drawText(
                textLayout,
                topLeft = Offset(horizontalPaddingPx.toFloat(), cardTop + verticalPaddingPx),
            )
        }
    }
}

@Composable
internal fun CanvasWarningStatusRow(
    summaryText: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    val textColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
    val textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor)
    val interactionSource = remember { MutableInteractionSource() }
    val clickableModifier =
        if (onClick != null) {
            Modifier
                .semantics { role = Role.Button }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                )
        } else {
            Modifier
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickableModifier)
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }.fastCoerceAtLeast(1)
        val barWidthPx = with(density) { 2.dp.toPx() }
        val barHeightPx = with(density) { 16.dp.toPx() }
        val gapPx = with(density) { 8.dp.roundToPx().toFloat() }
        val verticalPaddingPx = with(density) { 6.dp.roundToPx() }
        val cornerRadiusPx = with(density) { 999.dp.toPx() }

        val textLayout =
            remember(summaryText, textStyle, textMeasurer, widthPx, gapPx, barWidthPx) {
                textMeasurer.measure(
                    text = AnnotatedString(summaryText),
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints =
                        Constraints(
                            maxWidth =
                                (widthPx - barWidthPx.toInt() - gapPx.toInt()).fastCoerceAtLeast(0)
                        ),
                )
            }

        val contentHeightPx = max(barHeightPx.toInt(), textLayout.size.height)
        val totalHeightPx = verticalPaddingPx * 2 + contentHeightPx

        Canvas(modifier = Modifier.fillMaxWidth().height(with(density) { totalHeightPx.toDp() })) {
            val top = verticalPaddingPx.toFloat()
            val barTop = top + (contentHeightPx - barHeightPx) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(0f, barTop),
                size = Size(barWidthPx, barHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx),
            )

            drawText(
                textLayout,
                topLeft = Offset(barWidthPx + gapPx, top + (contentHeightPx - textLayout.size.height) / 2f),
            )
        }
    }
}
