package com.ai.assistance.operit.ui.theme.script.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density

/** 字段状态与视口由表单持有；描边只修饰文字层，不重建编辑状态。 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
@UiComposable
fun ThemeOutlinedTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Attached(),
    label: @Composable @UiComposable (TextFieldLabelScope.() -> Unit)? = null,
    placeholder: @Composable @UiComposable (() -> Unit)? = null,
    leadingIcon: @Composable @UiComposable (() -> Unit)? = null,
    trailingIcon: @Composable @UiComposable (() -> Unit)? = null,
    prefix: @Composable @UiComposable (() -> Unit)? = null,
    suffix: @Composable @UiComposable (() -> Unit)? = null,
    supportingText: @Composable @UiComposable (() -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
    interactionSource: MutableInteractionSource? = null,
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    if (textStroke == null) {
        androidx.compose.material3.OutlinedTextField(
            state = state, modifier = modifier, enabled = enabled, readOnly = readOnly,
            textStyle = textStyle, labelPosition = labelPosition, label = label,
            placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            prefix = prefix, suffix = suffix, supportingText = supportingText, isError = isError,
            inputTransformation = inputTransformation, outputTransformation = outputTransformation,
            keyboardOptions = keyboardOptions, onKeyboardAction = onKeyboardAction,
            lineLimits = lineLimits, onTextLayout = onTextLayout, scrollState = scrollState,
            shape = shape, colors = colors, contentPadding = contentPadding,
            interactionSource = interactionSource,
        )
    } else {
        val source = interactionSource ?: remember { MutableInteractionSource() }
        val focused by source.collectIsFocusedAsState()
        val foreground = textStyle.color.takeOrElse {
            when {
                !enabled -> colors.disabledTextColor
                isError -> colors.errorTextColor
                focused -> colors.focusedTextColor
                else -> colors.unfocusedTextColor
            }
        }
        CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
            ThemeBasicTextField(
                state = state,
                modifier = modifier.then(
                    if (label != null && labelPosition !is TextFieldLabelPosition.Above) {
                        Modifier.semantics(mergeDescendants = true) {}.padding(
                            top = with(LocalDensity.current) {
                                MaterialTheme.typography.bodySmall.fontSize.toDp() / 2
                            },
                        )
                    } else Modifier,
                ).defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = OutlinedTextFieldDefaults.MinHeight,
                ),
                enabled = enabled, readOnly = readOnly,
                textStyle = textStyle.copy(color = foreground),
                inputTransformation = inputTransformation, outputTransformation = outputTransformation,
                keyboardOptions = keyboardOptions, onKeyboardAction = onKeyboardAction,
                lineLimits = lineLimits, onTextLayout = onTextLayout, scrollState = scrollState,
                interactionSource = source,
                cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
                textStroke = textStroke,
                decorator = OutlinedTextFieldDefaults.decorator(
                    state = state, enabled = enabled, lineLimits = lineLimits,
                    outputTransformation = outputTransformation, interactionSource = source,
                    labelPosition = labelPosition, label = label, placeholder = placeholder,
                    leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix,
                    suffix = suffix, supportingText = supportingText, isError = isError,
                    colors = colors, contentPadding = contentPadding,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled, isError = isError, interactionSource = source,
                            colors = colors, shape = shape,
                        )
                    },
                ),
            )
        }
    }
}
