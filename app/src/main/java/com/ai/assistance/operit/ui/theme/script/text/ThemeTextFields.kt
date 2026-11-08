package com.ai.assistance.operit.ui.theme.script.text

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.UiComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/** 未启用描边时沿用 Material3；启用后复用其装饰框与原生编辑器的文字层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@UiComposable
fun ThemeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable @UiComposable (() -> Unit)? = null,
    placeholder: @Composable @UiComposable (() -> Unit)? = null,
    leadingIcon: @Composable @UiComposable (() -> Unit)? = null,
    trailingIcon: @Composable @UiComposable (() -> Unit)? = null,
    prefix: @Composable @UiComposable (() -> Unit)? = null,
    suffix: @Composable @UiComposable (() -> Unit)? = null,
    supportingText: @Composable @UiComposable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    if (textStroke == null) {
        androidx.compose.material3.TextField(
            value = value, onValueChange = onValueChange, modifier = modifier,
            enabled = enabled, readOnly = readOnly, textStyle = textStyle,
            label = label, placeholder = placeholder, leadingIcon = leadingIcon,
            trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
            supportingText = supportingText, isError = isError,
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions, singleLine = singleLine,
            maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
            shape = shape, colors = colors,
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
        ThemeBasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = modifier.defaultMinSize(minWidth = TextFieldDefaults.MinWidth, minHeight = TextFieldDefaults.MinHeight),
            enabled = enabled, readOnly = readOnly, textStyle = textStyle.copy(color = foreground),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            visualTransformation = visualTransformation, interactionSource = source,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            textStroke = textStroke,
            decorationBox = { inner ->
                TextFieldDefaults.DecorationBox(
                    value = value, innerTextField = inner, enabled = enabled,
                    singleLine = singleLine, visualTransformation = visualTransformation,
                    interactionSource = source, isError = isError, label = label,
                    placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    prefix = prefix, suffix = suffix, supportingText = supportingText, colors = colors,
                    container = {
                        TextFieldDefaults.Container(enabled = enabled, isError = isError,
                            interactionSource = source, colors = colors, shape = shape)
                    },
                )
            },
        )
    }
}

/** 未启用描边时沿用 Material3；启用后复用其装饰框与原生编辑器的文字层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@UiComposable
fun ThemeTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable @UiComposable (() -> Unit)? = null,
    placeholder: @Composable @UiComposable (() -> Unit)? = null,
    leadingIcon: @Composable @UiComposable (() -> Unit)? = null,
    trailingIcon: @Composable @UiComposable (() -> Unit)? = null,
    prefix: @Composable @UiComposable (() -> Unit)? = null,
    suffix: @Composable @UiComposable (() -> Unit)? = null,
    supportingText: @Composable @UiComposable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    if (textStroke == null) {
        androidx.compose.material3.TextField(
            value = value, onValueChange = onValueChange, modifier = modifier,
            enabled = enabled, readOnly = readOnly, textStyle = textStyle,
            label = label, placeholder = placeholder, leadingIcon = leadingIcon,
            trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
            supportingText = supportingText, isError = isError,
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions, singleLine = singleLine,
            maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
            shape = shape, colors = colors,
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
        ThemeBasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = modifier.defaultMinSize(minWidth = TextFieldDefaults.MinWidth, minHeight = TextFieldDefaults.MinHeight),
            enabled = enabled, readOnly = readOnly, textStyle = textStyle.copy(color = foreground),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            visualTransformation = visualTransformation, interactionSource = source,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            textStroke = textStroke,
            decorationBox = { inner ->
                TextFieldDefaults.DecorationBox(
                    value = value.text, innerTextField = inner, enabled = enabled,
                    singleLine = singleLine, visualTransformation = visualTransformation,
                    interactionSource = source, isError = isError, label = label,
                    placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    prefix = prefix, suffix = suffix, supportingText = supportingText, colors = colors,
                    container = {
                        TextFieldDefaults.Container(enabled = enabled, isError = isError,
                            interactionSource = source, colors = colors, shape = shape)
                    },
                )
            },
        )
    }
}

/** 未启用描边时沿用 Material3；启用后复用其装饰框与原生编辑器的文字层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@UiComposable
fun ThemeOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable @UiComposable (() -> Unit)? = null,
    placeholder: @Composable @UiComposable (() -> Unit)? = null,
    leadingIcon: @Composable @UiComposable (() -> Unit)? = null,
    trailingIcon: @Composable @UiComposable (() -> Unit)? = null,
    prefix: @Composable @UiComposable (() -> Unit)? = null,
    suffix: @Composable @UiComposable (() -> Unit)? = null,
    supportingText: @Composable @UiComposable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    if (textStroke == null) {
        androidx.compose.material3.OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier,
            enabled = enabled, readOnly = readOnly, textStyle = textStyle,
            label = label, placeholder = placeholder, leadingIcon = leadingIcon,
            trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
            supportingText = supportingText, isError = isError,
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions, singleLine = singleLine,
            maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
            shape = shape, colors = colors,
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
        ThemeBasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = modifier.then(if (label == null) Modifier else Modifier.padding(
                top = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.fontSize.toDp() / 2 },
            )).defaultMinSize(minWidth = OutlinedTextFieldDefaults.MinWidth, minHeight = OutlinedTextFieldDefaults.MinHeight),
            enabled = enabled, readOnly = readOnly, textStyle = textStyle.copy(color = foreground),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            visualTransformation = visualTransformation, interactionSource = source,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            textStroke = textStroke,
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value, innerTextField = inner, enabled = enabled,
                    singleLine = singleLine, visualTransformation = visualTransformation,
                    interactionSource = source, isError = isError, label = label,
                    placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    prefix = prefix, suffix = suffix, supportingText = supportingText, colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(enabled = enabled, isError = isError,
                            interactionSource = source, colors = colors, shape = shape)
                    },
                )
            },
        )
    }
}

/** 未启用描边时沿用 Material3；启用后复用其装饰框与原生编辑器的文字层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@UiComposable
fun ThemeOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable @UiComposable (() -> Unit)? = null,
    placeholder: @Composable @UiComposable (() -> Unit)? = null,
    leadingIcon: @Composable @UiComposable (() -> Unit)? = null,
    trailingIcon: @Composable @UiComposable (() -> Unit)? = null,
    prefix: @Composable @UiComposable (() -> Unit)? = null,
    suffix: @Composable @UiComposable (() -> Unit)? = null,
    supportingText: @Composable @UiComposable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    textStroke: ResolvedThemeTextStroke? = LocalThemeTextStroke.current,
) {
    if (textStroke == null) {
        androidx.compose.material3.OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier,
            enabled = enabled, readOnly = readOnly, textStyle = textStyle,
            label = label, placeholder = placeholder, leadingIcon = leadingIcon,
            trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
            supportingText = supportingText, isError = isError,
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions, singleLine = singleLine,
            maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
            shape = shape, colors = colors,
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
        ThemeBasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = modifier.then(if (label == null) Modifier else Modifier.padding(
                top = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.fontSize.toDp() / 2 },
            )).defaultMinSize(minWidth = OutlinedTextFieldDefaults.MinWidth, minHeight = OutlinedTextFieldDefaults.MinHeight),
            enabled = enabled, readOnly = readOnly, textStyle = textStyle.copy(color = foreground),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            visualTransformation = visualTransformation, interactionSource = source,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            textStroke = textStroke,
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value.text, innerTextField = inner, enabled = enabled,
                    singleLine = singleLine, visualTransformation = visualTransformation,
                    interactionSource = source, isError = isError, label = label,
                    placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    prefix = prefix, suffix = suffix, supportingText = supportingText, colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(enabled = enabled, isError = isError,
                            interactionSource = source, colors = colors, shape = shape)
                    },
                )
            },
        )
    }
}
