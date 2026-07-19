package com.ai.assistance.operit.ui.common.markdown

import android.graphics.Typeface
import android.os.SystemClock
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.LocalAiMarkdownTextLayoutSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp

private val TABLE_MIN_COLUMN_WIDTH = 80.dp
private val TABLE_MAX_COLUMN_WIDTH = 320.dp
private val TABLE_CELL_HORIZONTAL_PADDING = 8.dp
private val TABLE_CELL_VERTICAL_PADDING = 8.dp
private val TABLE_OUTER_VERTICAL_PADDING = 8.dp
private val TABLE_CORNER_RADIUS = 4.dp
private val TABLE_BORDER_WIDTH = 1.dp
private val TABLE_GRID_WIDTH = 0.5.dp
private const val TABLE_MAX_MEASURE_LINE_CHARS = 512
private const val TABLE_LINE_SPACING_MULTIPLIER = 1.3f
private const val TABLE_FLING_DECAY_RATE = 4.5f
private const val TABLE_MIN_FLING_VELOCITY = 120f
private const val TABLE_MAX_FLING_VELOCITY = 9000f

/** 供表格渲染和复制为纯文本共用的表格解析结果。 */
internal data class TableData(
    val rows: List<List<String>>,
    val hasHeader: Boolean
)

private data class TableCellRenderData(
    val layout: StaticLayout,
    val isHeader: Boolean
)

private data class TableRenderLayout(
    val columnWidthsPx: List<Int>,
    val rowHeightsPx: List<Int>,
    val cells: List<List<TableCellRenderData>>,
    val totalWidthPx: Int,
    val totalHeightPx: Int,
    val rowCount: Int,
    val columnCount: Int,
    val cellCount: Int,
    val measuredLineCount: Int,
    val overlongCellCount: Int,
)

@Composable
fun EnhancedTableBlock(
    tableContent: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val textLayoutSettings = LocalAiMarkdownTextLayoutSettings.current
    val coroutineScope = rememberCoroutineScope()
    val fontFamily = typography.bodyMedium.fontFamily
    val resolver = LocalFontFamilyResolver.current
    val normalTypeface = remember(resolver, fontFamily) {
        (resolver.resolve(fontFamily = fontFamily, fontWeight = FontWeight.Normal).value as? Typeface)
            ?: Typeface.DEFAULT
    }
    val boldTypeface = remember(resolver, fontFamily) {
        (resolver.resolve(fontFamily = fontFamily, fontWeight = FontWeight.Bold).value as? Typeface)
            ?: Typeface.DEFAULT_BOLD
    }

    val tableData = remember(tableContent) { parseTable(tableContent) }

    if (tableData.rows.isEmpty()) return

    var scrollOffsetPx by remember(tableContent) { mutableStateOf(0f) }
    var dragVelocityPxPerSec by remember(tableContent) { mutableStateOf(0f) }
    var lastDragEventTimeMs by remember(tableContent) { mutableStateOf(0L) }
    var flingJob by remember(tableContent) { mutableStateOf<Job?>(null) }
    val tableBlockDesc = stringResource(R.string.table_block)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val primaryColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = tableBlockDesc }
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx() }.toInt()
        if (availableWidthPx <= 0) return@BoxWithConstraints

        val renderLayout = remember(
            tableContent,
            availableWidthPx,
            textColor,
            primaryColor,
            typography.bodyMedium,
            density,
            textLayoutSettings.lineHeightMultiplier,
            textLayoutSettings.letterSpacingSp,
            normalTypeface,
            boldTypeface,
        ) {
            measureTableLayout(
                tableData = tableData,
                textColor = textColor,
                density = density,
                bodyTextStyle = typography.bodyMedium,
                normalTypeface = normalTypeface,
                boldTypeface = boldTypeface,
                primaryColor = primaryColor,
                globalLineHeightMultiplier = textLayoutSettings.lineHeightMultiplier,
                globalLetterSpacingSp = textLayoutSettings.letterSpacingSp,
            )
        }

        val totalHeightDp = with(density) { renderLayout.totalHeightPx.toDp() }
        val outerBorderWidthPx = with(density) { TABLE_BORDER_WIDTH.toPx() }
        val gridLineWidthPx = with(density) { TABLE_GRID_WIDTH.toPx() }
        val cornerRadiusPx = with(density) { TABLE_CORNER_RADIUS.toPx() }
        val cellHorizontalPaddingPx = with(density) { TABLE_CELL_HORIZONTAL_PADDING.toPx() }
        val cellVerticalPaddingPx = with(density) { TABLE_CELL_VERTICAL_PADDING.toPx() }
        val maxScrollPx = (renderLayout.totalWidthPx - availableWidthPx).coerceAtLeast(0).toFloat()

        fun cancelFling() {
            flingJob?.cancel()
            flingJob = null
        }

        fun startFling(initialVelocityPxPerSec: Float) {
            if (maxScrollPx <= 0f) return
            val clampedVelocity =
                initialVelocityPxPerSec
                    .coerceIn(-TABLE_MAX_FLING_VELOCITY, TABLE_MAX_FLING_VELOCITY)
            if (abs(clampedVelocity) < TABLE_MIN_FLING_VELOCITY) return

            cancelFling()
            flingJob =
                coroutineScope.launch {
                    var velocity = clampedVelocity
                    var lastFrameNanos = 0L
                    while (isActive && abs(velocity) >= TABLE_MIN_FLING_VELOCITY) {
                        val frameNanos = withFrameNanos { it }
                        if (lastFrameNanos == 0L) {
                            lastFrameNanos = frameNanos
                            continue
                        }

                        val deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                        lastFrameNanos = frameNanos
                        if (deltaSeconds <= 0f) continue

                        val nextOffset =
                            (scrollOffsetPx + velocity * deltaSeconds).coerceIn(0f, maxScrollPx)
                        val hitEdge = nextOffset <= 0f || nextOffset >= maxScrollPx
                        scrollOffsetPx = nextOffset
                        velocity *= exp(-TABLE_FLING_DECAY_RATE * deltaSeconds)

                        if (hitEdge) {
                            break
                        }
                    }
                    flingJob = null
                }
        }

        SideEffect {
            if (scrollOffsetPx > maxScrollPx) {
                scrollOffsetPx = maxScrollPx
                dragVelocityPxPerSec = 0f
                cancelFling()
            }
        }

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = TABLE_OUTER_VERTICAL_PADDING)
                    .height(totalHeightDp)
                    .pointerInput(maxScrollPx) {
                        if (maxScrollPx <= 0f) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = {
                                cancelFling()
                                dragVelocityPxPerSec = 0f
                                lastDragEventTimeMs = SystemClock.uptimeMillis()
                            },
                            onDragCancel = {
                                dragVelocityPxPerSec = 0f
                                lastDragEventTimeMs = 0L
                            },
                            onDragEnd = {
                                startFling(dragVelocityPxPerSec)
                                dragVelocityPxPerSec = 0f
                                lastDragEventTimeMs = 0L
                            },
                        ) { _, dragAmount ->
                            val nowMs = SystemClock.uptimeMillis()
                            val deltaMs = (nowMs - lastDragEventTimeMs).coerceAtLeast(1L)
                            val instantVelocity = (-dragAmount / deltaMs.toFloat()) * 1000f
                            dragVelocityPxPerSec =
                                if (dragVelocityPxPerSec == 0f) {
                                    instantVelocity
                                } else {
                                    dragVelocityPxPerSec * 0.35f + instantVelocity * 0.65f
                                }
                            lastDragEventTimeMs = nowMs
                            scrollOffsetPx = (scrollOffsetPx - dragAmount).coerceIn(0f, maxScrollPx)
                        }
                    }
        ) {
            translate(left = -scrollOffsetPx, top = 0f) {
                drawRoundRect(
                    color = borderColor,
                    size = Size(renderLayout.totalWidthPx.toFloat(), renderLayout.totalHeightPx.toFloat()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = outerBorderWidthPx)
                )

                var currentY = 0f
                renderLayout.rowHeightsPx.forEachIndexed { rowIndex, rowHeightPx ->
                    var currentX = 0f
                    renderLayout.columnWidthsPx.forEachIndexed { colIndex, colWidthPx ->
                        val cell = renderLayout.cells[rowIndex][colIndex]

                        if (cell.isHeader) {
                            drawRect(
                                color = headerBackground,
                                topLeft = Offset(currentX, currentY),
                                size = Size(colWidthPx.toFloat(), rowHeightPx.toFloat())
                            )
                        }

                        if (rowIndex > 0) {
                            drawLine(
                                color = borderColor,
                                start = Offset(currentX, currentY),
                                end = Offset(currentX + colWidthPx, currentY),
                                strokeWidth = gridLineWidthPx,
                                cap = StrokeCap.Square
                            )
                        }

                        if (colIndex > 0) {
                            drawLine(
                                color = borderColor,
                                start = Offset(currentX, currentY),
                                end = Offset(currentX, currentY + rowHeightPx),
                                strokeWidth = gridLineWidthPx,
                                cap = StrokeCap.Square
                            )
                        }

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.save()
                            canvas.nativeCanvas.translate(
                                currentX + cellHorizontalPaddingPx,
                                currentY + cellVerticalPaddingPx
                            )
                            drawInlineCodeBackgrounds(cell.layout, canvas.nativeCanvas)
                            cell.layout.draw(canvas.nativeCanvas)
                            canvas.nativeCanvas.restore()
                        }

                        currentX += colWidthPx
                    }
                    currentY += rowHeightPx
                }
            }
        }
    }
}

private fun measureTableLayout(
    tableData: TableData,
    textColor: Color,
    density: androidx.compose.ui.unit.Density,
    bodyTextStyle: androidx.compose.ui.text.TextStyle,
    normalTypeface: Typeface,
    boldTypeface: Typeface,
    primaryColor: Color,
    globalLineHeightMultiplier: Float,
    globalLetterSpacingSp: Float,
): TableRenderLayout {
    val rowCount = tableData.rows.size
    val columnCount = tableData.rows.maxOfOrNull { it.size } ?: 0
    val cellCount = tableData.rows.sumOf { it.size }
    if (rowCount == 0 || columnCount == 0) {
        return TableRenderLayout(emptyList(), emptyList(), emptyList(), 0, 0, 0, 0, 0, 0, 0)
    }

    val minColumnWidthPx = with(density) { TABLE_MIN_COLUMN_WIDTH.roundToPx() }
    val maxColumnWidthPx = with(density) { TABLE_MAX_COLUMN_WIDTH.roundToPx() }
    val cellHorizontalPaddingPx = with(density) { TABLE_CELL_HORIZONTAL_PADDING.roundToPx() }
    val cellVerticalPaddingPx = with(density) { TABLE_CELL_VERTICAL_PADDING.roundToPx() }
    val innerMinWidthPx = (minColumnWidthPx - cellHorizontalPaddingPx * 2).coerceAtLeast(1)
    val innerMaxWidthPx = (maxColumnWidthPx - cellHorizontalPaddingPx * 2).coerceAtLeast(innerMinWidthPx)
    val bodyFontSizePx = with(density) { bodyTextStyle.fontSize.toPx() }
    val headerFontSizePx = bodyFontSizePx
    val bodyPaint = TextPaint().apply {
        color = textColor.toArgb()
        textSize = bodyFontSizePx
        isAntiAlias = true
        typeface = normalTypeface
        letterSpacing = calculateLetterSpacingEm(bodyTextStyle.fontSize.value, globalLetterSpacingSp)
    }
    val headerPaint = TextPaint().apply {
        color = textColor.toArgb()
        textSize = headerFontSizePx
        isAntiAlias = true
        typeface = boldTypeface
        letterSpacing = calculateLetterSpacingEm(bodyTextStyle.fontSize.value, globalLetterSpacingSp)
    }
    val lineSpacingMultiplier = TABLE_LINE_SPACING_MULTIPLIER * globalLineHeightMultiplier

    val normalizedRows =
        tableData.rows.map { row ->
            if (row.size >= columnCount) {
                row
            } else {
                row + List(columnCount - row.size) { "" }
            }
        }

    val preparedRows =
        normalizedRows.map { row ->
            row.map { cell ->
                buildMarkdownInlineSpannableFromText(
                    text = cell,
                    textColor = textColor,
                    primaryColor = primaryColor,
                    density = density,
                    fontSize = bodyTextStyle.fontSize
                ) as CharSequence
            }
        }

    var measuredLineCount = 0
    var overlongCellCount = 0
    val columnWidthsPx = MutableList(columnCount) { minColumnWidthPx }

    normalizedRows.forEachIndexed { rowIndex, row ->
        val isHeaderRow = rowIndex == 0 && tableData.hasHeader
        val paint = if (isHeaderRow) headerPaint else bodyPaint
        row.forEachIndexed { colIndex, cell ->
            val preparedCell = preparedRows[rowIndex][colIndex]
            val rawLineWidthPx =
                if (cell.isEmpty()) {
                    0f
                } else {
                    cell.lineSequence().forEach { line ->
                        if (line.length > TABLE_MAX_MEASURE_LINE_CHARS) {
                            overlongCellCount += 1
                        }
                    }
                    measureCellDesiredWidth(
                        text = preparedCell,
                        paint = paint,
                        widthPx = innerMaxWidthPx,
                        lineSpacingMultiplier = lineSpacingMultiplier
                    ).also {
                        measuredLineCount += preparedCell.lineCountHint()
                    }
                }
            val cellWidthPx =
                ceil(rawLineWidthPx).toInt()
                    .plus(cellHorizontalPaddingPx * 2)
                    .coerceIn(minColumnWidthPx, maxColumnWidthPx)
            if (cellWidthPx > columnWidthsPx[colIndex]) {
                columnWidthsPx[colIndex] = cellWidthPx
            }
        }
    }

    val cells = mutableListOf<List<TableCellRenderData>>()
    val rowHeightsPx = MutableList(rowCount) { 0 }

    preparedRows.forEachIndexed { rowIndex, row ->
        val isHeaderRow = rowIndex == 0 && tableData.hasHeader
        val paint = if (isHeaderRow) headerPaint else bodyPaint
        val cellLayouts = mutableListOf<TableCellRenderData>()
        var maxRowHeightPx = 0

        row.forEachIndexed { colIndex, cell ->
            val innerWidthPx =
                (columnWidthsPx[colIndex] - cellHorizontalPaddingPx * 2).coerceAtLeast(innerMinWidthPx)
            val layout = createCellStaticLayout(cell, paint, innerWidthPx, lineSpacingMultiplier)
            val cellHeightPx = layout.height + cellVerticalPaddingPx * 2
            if (cellHeightPx > maxRowHeightPx) {
                maxRowHeightPx = cellHeightPx
            }
            cellLayouts += TableCellRenderData(layout = layout, isHeader = isHeaderRow)
        }

        rowHeightsPx[rowIndex] = maxRowHeightPx.coerceAtLeast(cellVerticalPaddingPx * 2 + layoutLineHeightFallback(paint))
        cells += cellLayouts
    }

    val totalWidthPx = columnWidthsPx.sum().coerceAtLeast(1)
    val totalHeightPx = rowHeightsPx.sum().coerceAtLeast(1)
    return TableRenderLayout(
        columnWidthsPx = columnWidthsPx,
        rowHeightsPx = rowHeightsPx,
        cells = cells,
        totalWidthPx = totalWidthPx,
        totalHeightPx = totalHeightPx,
        rowCount = rowCount,
        columnCount = columnCount,
        cellCount = cellCount,
        measuredLineCount = measuredLineCount,
        overlongCellCount = overlongCellCount,
    )
}

private fun createCellStaticLayout(
    text: CharSequence,
    paint: TextPaint,
    widthPx: Int,
    lineSpacingMultiplier: Float,
): StaticLayout {
    val safeWidth = widthPx.coerceAtLeast(1)
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .build()
    } else {
        @Suppress("DEPRECATION")
        StaticLayout(
            text,
            paint,
            safeWidth,
            android.text.Layout.Alignment.ALIGN_CENTER,
            lineSpacingMultiplier,
            0f,
            false
        )
    }
}

private fun measureCellDesiredWidth(
    text: CharSequence,
    paint: TextPaint,
    widthPx: Int,
    lineSpacingMultiplier: Float,
): Float {
    if (text.isEmpty()) return 0f

    val layout = createCellStaticLayout(text, paint, widthPx, lineSpacingMultiplier)
    var maxWidth = 0f
    for (lineIndex in 0 until layout.lineCount) {
        maxWidth = maxOf(maxWidth, layout.getLineWidth(lineIndex))
        if (maxWidth >= widthPx) {
            return widthPx.toFloat()
        }
    }
    return maxWidth
}

private fun CharSequence.lineCountHint(): Int {
    if (isEmpty()) return 0
    return count { it == '\n' } + 1
}

private fun layoutLineHeightFallback(paint: TextPaint): Int {
    val metrics = paint.fontMetricsInt
    return (metrics.descent - metrics.ascent).coerceAtLeast(1)
}

private fun calculateLetterSpacingEm(fontSizeSp: Float, letterSpacingSp: Float): Float {
    if (!fontSizeSp.isFinite() || fontSizeSp <= 0f) return 0f
    return letterSpacingSp / fontSizeSp
}

/** 将原始 Markdown 表格文本解析成行列结构，供渲染和复制为纯文本共用。 */
internal fun parseTable(content: String): TableData {
    fun isHeaderSeparatorLine(line: String): Boolean {
        return line.trim().matches(
            Regex("^\\s*\\|?\\s*[-:]+\\s*(\\|\\s*[-:]+\\s*)+\\|?\\s*$")
        )
    }

    fun parseCells(line: String): MutableList<String> {
        val trimmed = line.trim()
        var parts = trimmed.split('|').toMutableList()
        if (trimmed.startsWith("|")) {
            parts = parts.drop(1).toMutableList()
        }
        if (trimmed.endsWith("|") && parts.isNotEmpty()) {
            parts.removeAt(parts.lastIndex)
        }
        return parts
            .map { it.trim().replace(Regex("(?i)<br\\s*/?>"), "\n") }
            .toMutableList()
    }

    val lines = content.lines().filter { it.trim().isNotEmpty() && it.contains('|') }
    if (lines.isEmpty()) {
        return TableData(emptyList(), false)
    }

    val hasHeader = lines.size > 1 && isHeaderSeparatorLine(lines[1])
    val rawRows = mutableListOf<MutableList<String>>()
    var maxColumns = 0

    lines.forEachIndexed { index, line ->
        if (index == 1 && hasHeader) return@forEachIndexed
        val cells = parseCells(line)
        maxColumns = maxOf(maxColumns, cells.size)
        rawRows += cells
    }

    val rows =
        rawRows.map { row ->
            while (row.size < maxColumns) {
                row += ""
            }
            row.toList()
        }

    return TableData(rows = rows, hasHeader = hasHeader)
}
