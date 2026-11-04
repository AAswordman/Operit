package com.ai.assistance.operit.ui.theme.script.text

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.UiComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** 复用同一文字布局先画轮廓再画原文，保留原有链接、选择和无障碍节点。 */
@Composable
@UiComposable
fun ThemeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    autoSize: TextAutoSize? = null,
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    if (textStroke == null) {
        androidx.compose.material3.Text(
            text = text, modifier = modifier, color = color, fontSize = fontSize,
            fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
            letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap,
            maxLines = maxLines, minLines = minLines, onTextLayout = { onTextLayout?.invoke(it) }, style = style, autoSize = autoSize,
        )
    } else {
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        androidx.compose.material3.Text(
            text = text, modifier = modifier.drawWithContent {
                layout?.let { result ->
                    drawThemeTextStroke(result, textStroke)
                }
                drawContent()
            }, color = color, fontSize = fontSize, fontStyle = fontStyle,
            fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
            textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
            onTextLayout = { result -> layout = result; onTextLayout?.invoke(result) },
            style = style, autoSize = autoSize,
        )
    }
}

/** 复用同一文字布局先画轮廓再画原文，保留原有链接、选择和无障碍节点。 */
@Composable
@UiComposable
fun ThemeText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    autoSize: TextAutoSize? = null,
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    if (textStroke == null) {
        androidx.compose.material3.Text(
            text = text, modifier = modifier, color = color, fontSize = fontSize,
            fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
            letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap,
            maxLines = maxLines, minLines = minLines, onTextLayout = { onTextLayout?.invoke(it) }, style = style, autoSize = autoSize, inlineContent = inlineContent,
        )
    } else {
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        androidx.compose.material3.Text(
            text = text, modifier = modifier.drawWithContent {
                layout?.let { result ->
                    drawThemeTextStroke(result, textStroke)
                }
                drawContent()
            }, color = color, fontSize = fontSize, fontStyle = fontStyle,
            fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
            textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
            onTextLayout = { result -> layout = result; onTextLayout?.invoke(result) },
            style = style, autoSize = autoSize, inlineContent = inlineContent,
        )
    }
}
