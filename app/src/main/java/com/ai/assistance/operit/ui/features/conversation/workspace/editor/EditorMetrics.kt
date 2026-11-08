package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil

internal class EditorMetrics(
    private val paint: Paint,
    textSizePx: Float
) {
    var charWidth: Float = 0f
        private set
    var charHeight: Float = 0f
        private set
    var lineHeight: Float = 0f
        private set
    var baseline: Float = 0f
        private set

    init {
        paint.typeface = Typeface.MONOSPACE
        update(textSizePx)
    }

    fun update(textSizePx: Float) {
        paint.textSize = textSizePx
        charWidth = ceil(paint.measureText("M").toDouble()).toFloat()
        val fontMetrics = paint.fontMetrics
        charHeight = fontMetrics.descent - fontMetrics.ascent
        lineHeight = charHeight * 1.25f
        baseline = -fontMetrics.ascent + (lineHeight - charHeight) / 2f
    }

    fun getCellWidth(char: Char): Int = editorCellWidth(char)
}
