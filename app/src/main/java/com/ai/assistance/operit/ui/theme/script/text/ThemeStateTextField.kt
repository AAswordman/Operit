package com.ai.assistance.operit.ui.theme.script.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density

/** 状态式输入保留滚动与文本转换，复制面板和资料编辑与其他文字入口使用同一描边规则。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeBasicTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    outputTransformation: OutputTransformation? = null,
    decorator: TextFieldDecorator? = null,
    scrollState: ScrollState = rememberScrollState(),
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    androidx.compose.foundation.text.BasicTextField(
        state = state, modifier = modifier, enabled = enabled, readOnly = readOnly,
        inputTransformation = inputTransformation, textStyle = textStyle,
        keyboardOptions = keyboardOptions, onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits, onTextLayout = onTextLayout,
        interactionSource = interactionSource, cursorBrush = cursorBrush,
        outputTransformation = outputTransformation, scrollState = scrollState,
        decorator = TextFieldDecorator { inner ->
            if (decorator == null) {
                ThemeInputTextStroke(textStroke, inner)
            } else {
                decorator.Decoration { ThemeInputTextStroke(textStroke, inner) }
            }
        },
    )
}
