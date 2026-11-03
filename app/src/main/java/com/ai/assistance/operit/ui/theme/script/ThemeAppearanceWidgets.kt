package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Button as NativeButton
import androidx.compose.material3.FilledTonalButton as NativeFilledTonalButton
import androidx.compose.material3.OutlinedButton as NativeOutlinedButton
import androidx.compose.material3.TextButton as NativeTextButton
import androidx.compose.material3.IconButton as NativeIconButton
import androidx.compose.material3.AlertDialog as NativeAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** 原生按钮保留事件、交互源和无障碍，只将声明式外观接入现有插槽。 */
@Composable
fun ThemeScriptButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    ThemeInteractionScope("button", interactionSource, enabled) { activeSource ->
        val style = themeSurface("button")
        val resolvedColors = if (style == null) colors else colors.copy(
            containerColor = themeScriptFill("button", colors.containerColor),
            contentColor = themeScriptContentColor("button", colors.contentColor),
            disabledContainerColor = if ("disabled" in style.states) themeScriptFill("button", colors.disabledContainerColor) else colors.disabledContainerColor,
            disabledContentColor = if ("disabled" in style.states) themeScriptContentColor("button", colors.disabledContentColor) else colors.disabledContentColor,
        )
        NativeButton(
            onClick = onClick, modifier = modifier.alpha(themeSurface("button")?.opacity ?: 1f), enabled = enabled,
            shape = themeScriptShape("button", shape), colors = resolvedColors,
            elevation = if (style?.elevation == null) elevation else ButtonDefaults.buttonElevation(defaultElevation = style.elevation.dp),
            border = themeScriptBorder("button", border),
            contentPadding = if (style == null) contentPadding else PaddingValues(0.dp),
            interactionSource = activeSource,
        ) {
            if (style == null) content() else {
                ThemeScriptSurface("button", enabled = enabled, drawFill = false, drawStroke = false, originalShape = shape) {
                    Row(
                        Modifier.defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight).padding(contentPadding),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeScriptFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    ThemeInteractionScope("button", interactionSource, enabled) { activeSource ->
        val style = themeSurface("button")
        val resolvedColors = if (style == null) colors else colors.copy(
            containerColor = themeScriptFill("button", colors.containerColor),
            contentColor = themeScriptContentColor("button", colors.contentColor),
            disabledContainerColor = if ("disabled" in style.states) themeScriptFill("button", colors.disabledContainerColor) else colors.disabledContainerColor,
            disabledContentColor = if ("disabled" in style.states) themeScriptContentColor("button", colors.disabledContentColor) else colors.disabledContentColor,
        )
        NativeFilledTonalButton(
            onClick = onClick, modifier = modifier.alpha(themeSurface("button")?.opacity ?: 1f), enabled = enabled,
            shape = themeScriptShape("button", shape), colors = resolvedColors,
            elevation = if (style?.elevation == null) elevation else ButtonDefaults.buttonElevation(defaultElevation = style.elevation.dp),
            border = themeScriptBorder("button", border),
            contentPadding = if (style == null) contentPadding else PaddingValues(0.dp),
            interactionSource = activeSource,
        ) {
            if (style == null) content() else {
                ThemeScriptSurface("button", enabled = enabled, drawFill = false, drawStroke = false, originalShape = shape) {
                    Row(
                        Modifier.defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight).padding(contentPadding),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeScriptOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    ThemeInteractionScope("button", interactionSource, enabled) { activeSource ->
        val style = themeSurface("button")
        val resolvedColors = if (style == null) colors else colors.copy(
            containerColor = themeScriptFill("button", colors.containerColor),
            contentColor = themeScriptContentColor("button", colors.contentColor),
            disabledContainerColor = if ("disabled" in style.states) themeScriptFill("button", colors.disabledContainerColor) else colors.disabledContainerColor,
            disabledContentColor = if ("disabled" in style.states) themeScriptContentColor("button", colors.disabledContentColor) else colors.disabledContentColor,
        )
        NativeOutlinedButton(
            onClick = onClick, modifier = modifier.alpha(themeSurface("button")?.opacity ?: 1f), enabled = enabled,
            shape = themeScriptShape("button", shape), colors = resolvedColors,
            elevation = if (style?.elevation == null) elevation else ButtonDefaults.buttonElevation(defaultElevation = style.elevation.dp),
            border = themeScriptBorder("button", border),
            contentPadding = if (style == null) contentPadding else PaddingValues(0.dp),
            interactionSource = activeSource,
        ) {
            if (style == null) content() else {
                ThemeScriptSurface("button", enabled = enabled, drawFill = false, drawStroke = false, originalShape = shape) {
                    Row(
                        Modifier.defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight).padding(contentPadding),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeScriptTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    ThemeInteractionScope("button", interactionSource, enabled) { activeSource ->
        val style = themeSurface("button")
        val resolvedColors = if (style == null) colors else colors.copy(
            containerColor = themeScriptFill("button", colors.containerColor),
            contentColor = themeScriptContentColor("button", colors.contentColor),
            disabledContainerColor = if ("disabled" in style.states) themeScriptFill("button", colors.disabledContainerColor) else colors.disabledContainerColor,
            disabledContentColor = if ("disabled" in style.states) themeScriptContentColor("button", colors.disabledContentColor) else colors.disabledContentColor,
        )
        NativeTextButton(
            onClick = onClick, modifier = modifier.alpha(themeSurface("button")?.opacity ?: 1f), enabled = enabled,
            shape = themeScriptShape("button", shape), colors = resolvedColors,
            elevation = if (style?.elevation == null) elevation else ButtonDefaults.buttonElevation(defaultElevation = style.elevation.dp),
            border = themeScriptBorder("button", border),
            contentPadding = if (style == null) contentPadding else PaddingValues(0.dp),
            interactionSource = activeSource,
        ) {
            if (style == null) content() else {
                ThemeScriptSurface("button", enabled = enabled, drawFill = false, drawStroke = false, originalShape = shape) {
                    Row(
                        Modifier.defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight).padding(contentPadding),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeScriptIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = IconButtonDefaults.standardShape,
    content: @Composable () -> Unit,
) {
    ThemeInteractionScope("iconButton", interactionSource, enabled) { activeSource ->
        val style = themeSurface("iconButton")
        NativeIconButton(
            onClick = onClick,
            modifier = modifier.alpha(themeSurface("iconButton")?.opacity ?: 1f),
            shape = themeScriptShape("iconButton", shape),
            enabled = enabled,
            colors = if (style == null) colors else colors.copy(
                containerColor = themeScriptFill("iconButton", colors.containerColor),
                contentColor = themeScriptContentColor("iconButton", colors.contentColor),
                disabledContainerColor = if ("disabled" in style.states) themeScriptFill("iconButton", colors.disabledContainerColor) else colors.disabledContainerColor,
                disabledContentColor = if ("disabled" in style.states) themeScriptContentColor("iconButton", colors.disabledContentColor) else colors.disabledContentColor,
            ),
            interactionSource = activeSource,
        ) {
            if (style == null) content() else Box(Modifier.fillMaxSize().themeScriptDecoration("iconButton", enabled = enabled, drawFill = false, originalShape = shape), contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
fun ThemeScriptAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val style = themeSurface("dialog")
    val resolvedShape = themeScriptShape("dialog", shape)
    val resolvedModifier = if (style == null) modifier else modifier
        .clip(resolvedShape).background(themeScriptFill("dialog", containerColor))
        .themeScriptDecoration("dialog", drawFill = false, originalShape = resolvedShape)
    MaterialTheme(typography = applyThemeTypography(MaterialTheme.typography, style?.textStyle ?: com.ai.assistance.operit.data.theme.script.ThemeAppearanceText(), (LocalThemeScriptState.current as? ThemeScriptState.Ready)?.images.orEmpty())) {
    NativeAlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = resolvedModifier,
        dismissButton = dismissButton, icon = icon, title = title, text = text,
        shape = resolvedShape, containerColor = if (style == null) containerColor else Color.Transparent,
        iconContentColor = themeScriptContentColor("dialog", iconContentColor),
        titleContentColor = themeScriptContentColor("dialog", titleContentColor),
        textContentColor = themeScriptContentColor("dialog", textContentColor),
        tonalElevation = themeSurface("dialog")?.elevation?.dp ?: tonalElevation,
        properties = properties,
    )
    }
}

/** 自定义内容弹窗沿用原有布局，将素材装饰放在业务内容外层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScriptAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        ThemeScriptSurface("dialog", content = content)
    }
}
