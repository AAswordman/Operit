package com.ai.assistance.operit.ui.theme.script.text

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** 与底层文字保持同一排版结果，只在原填充之前增加主题轮廓。 */
@Composable
@UiComposable
fun ThemeBasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    autoSize: TextAutoSize? = null,
) {
    val stroke = LocalThemeTextStroke.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = text,
        modifier = if (stroke == null) modifier else modifier.drawWithContent {
            layout?.let { drawThemeTextStroke(it, stroke) }
            drawContent()
        },
        style = style, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        autoSize = autoSize, onTextLayout = { layout = it; onTextLayout?.invoke(it) },
    )
}
