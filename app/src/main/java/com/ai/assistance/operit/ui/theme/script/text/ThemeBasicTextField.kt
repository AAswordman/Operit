package com.ai.assistance.operit.ui.theme.script.text

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.UiComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/** 保留原生文字编辑、选择、光标与滚动，仅在内部文字层增加描边。 */
@Composable
@UiComposable
fun ThemeBasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    decorationBox: @Composable @UiComposable (innerTextField: @Composable @UiComposable () -> Unit) -> Unit = { it() },
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        enabled = enabled, readOnly = readOnly, textStyle = textStyle,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        visualTransformation = visualTransformation, onTextLayout = onTextLayout,
        interactionSource = interactionSource, cursorBrush = cursorBrush,
        decorationBox = { inner -> decorationBox { ThemeInputTextStroke(textStroke) { inner() } } },
    )
}

/** 保留原生文字编辑、选择、光标与滚动，仅在内部文字层增加描边。 */
@Composable
@UiComposable
fun ThemeBasicTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    decorationBox: @Composable @UiComposable (innerTextField: @Composable @UiComposable () -> Unit) -> Unit = { it() },
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        enabled = enabled, readOnly = readOnly, textStyle = textStyle,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        visualTransformation = visualTransformation, onTextLayout = onTextLayout,
        interactionSource = interactionSource, cursorBrush = cursorBrush,
        decorationBox = { inner -> decorationBox { ThemeInputTextStroke(textStroke) { inner() } } },
    )
}
