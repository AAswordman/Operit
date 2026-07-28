package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorContentMode
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorCustomizationSection
import com.ai.assistance.operit.ui.main.components.rememberNavigationDrawerAppearance
import kotlinx.coroutines.launch

@Composable
internal fun ThemeSettingsInterfaceTab(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
) {
    ThemeSettingsInterfaceColorPanel(
        shared = shared,
        cardColors = cardColors,
    )
}


@Composable
private fun ThemeSettingsInterfaceColorPanel(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
) {
    val editorSession = shared.editorSession
    val navigationDrawerAppearance = rememberNavigationDrawerAppearance()
    val defaultPrimaryColor = Color.Magenta.toArgb()
    val defaultSecondaryColor = Color.Blue.toArgb()
    val defaultNavigationDrawerBackgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val defaultNavigationDrawerAccentColor = navigationDrawerAppearance.titleColor.toArgb()
    val defaultStatusBarColor = MaterialTheme.colorScheme.surface.toArgb()
    val defaultAppBarColor = MaterialTheme.colorScheme.surface.toArgb()
    val defaultHeaderIconColor = Color.Gray.toArgb()

    val values by editorSession.values.collectAsState()
    val statusBarHiddenInput = values.requiredBoolean("status_bar_hidden")
    val statusBarTransparentInput = values.requiredBoolean("status_bar_transparent")
    val useCustomStatusBarColorInput = values.requiredBoolean("use_custom_status_bar_color")
    val customStatusBarColorInput =
        values.int("custom_status_bar_color") ?: defaultStatusBarColor
    val toolbarTransparentInput = values.requiredBoolean("toolbar_transparent")
    val useCustomAppBarColorInput = values.requiredBoolean("use_custom_app_bar_color")
    val customAppBarColorInput = values.int("custom_app_bar_color") ?: defaultAppBarColor
    val navigationDrawerWaterGlassInput =
        values.requiredBoolean("navigation_drawer_water_glass")
    val navigationDrawerButtonLiquidGlassInput =
        values.requiredBoolean("navigation_drawer_button_liquid_glass")
    val useCustomNavigationDrawerBackgroundColorInput =
        values.requiredBoolean("use_custom_navigation_drawer_background_color")
    val navigationDrawerBackgroundColorInput =
        values.int("custom_navigation_drawer_background_color")
            ?: defaultNavigationDrawerBackgroundColor
    val useCustomNavigationDrawerAccentColorInput =
        values.requiredBoolean("use_custom_navigation_drawer_accent_color")
    val navigationDrawerAccentColorInput =
        values.int("custom_navigation_drawer_accent_color")
            ?: defaultNavigationDrawerAccentColor
    val chatHeaderTransparentInput = values.requiredBoolean("chat_header_transparent")
    val chatHeaderOverlayModeInput = values.requiredBoolean("chat_header_overlay_mode")
    val forceAppBarContentColorInput =
        values.requiredBoolean("force_app_bar_content_color_enabled")
    val appBarContentColorModeInput = values.requiredString("app_bar_content_color_mode")
    val historyIconColorInput =
        values.int("chat_header_history_icon_color") ?: defaultHeaderIconColor
    val pipIconColorInput = values.int("chat_header_pip_icon_color") ?: defaultHeaderIconColor
    val useCustomColorsInput = values.requiredBoolean("use_custom_colors")
    val primaryColorInput = values.int("custom_primary_color") ?: defaultPrimaryColor
    val secondaryColorInput = values.int("custom_secondary_color") ?: defaultSecondaryColor
    val onColorModeInput = values.requiredString("on_color_mode")
    val recentColors by editorSession.recentColorsFlow.collectAsState(initial = emptyList())
    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorPickerMode by remember { mutableStateOf("primary") }

    ThemeSettingsColorCustomizationSection(
        cardColors = cardColors,
        editorSession = editorSession,
        statusBarHiddenInput = statusBarHiddenInput,
        statusBarTransparentInput = statusBarTransparentInput,
        useCustomStatusBarColorInput = useCustomStatusBarColorInput,
        customStatusBarColorInput = customStatusBarColorInput,
        toolbarTransparentInput = toolbarTransparentInput,
        useCustomAppBarColorInput = useCustomAppBarColorInput,
        customAppBarColorInput = customAppBarColorInput,
        navigationDrawerWaterGlassInput = navigationDrawerWaterGlassInput,
        navigationDrawerButtonLiquidGlassInput = navigationDrawerButtonLiquidGlassInput,
        useCustomNavigationDrawerBackgroundColorInput = useCustomNavigationDrawerBackgroundColorInput,
        navigationDrawerBackgroundColorInput = navigationDrawerBackgroundColorInput,
        useCustomNavigationDrawerAccentColorInput = useCustomNavigationDrawerAccentColorInput,
        navigationDrawerAccentColorInput = navigationDrawerAccentColorInput,
        chatHeaderTransparentInput = chatHeaderTransparentInput,
        chatHeaderOverlayModeInput = chatHeaderOverlayModeInput,
        forceAppBarContentColorInput = forceAppBarContentColorInput,
        appBarContentColorModeInput = appBarContentColorModeInput,
        chatHeaderHistoryIconColorInput = historyIconColorInput,
        chatHeaderPipIconColorInput = pipIconColorInput,
        useCustomColorsInput = useCustomColorsInput,
        primaryColorInput = primaryColorInput,
        secondaryColorInput = secondaryColorInput,
        onColorModeInput = onColorModeInput,
        onShowColorPicker = {
            currentColorPickerMode = it
            showColorPicker = true
        },
        contentMode = ThemeSettingsColorContentMode.INTERFACE,
    )

    if (showColorPicker) {
        ColorPickerDialog(
            showColorPicker = showColorPicker,
            currentColorPickerMode = currentColorPickerMode,
            primaryColorInput = primaryColorInput,
            secondaryColorInput = secondaryColorInput,
            statusBarColorInput = customStatusBarColorInput,
            appBarColorInput = customAppBarColorInput,
            navigationDrawerBackgroundColorInput = navigationDrawerBackgroundColorInput,
            navigationDrawerAccentColorInput = navigationDrawerAccentColorInput,
            historyIconColorInput = historyIconColorInput,
            pipIconColorInput = pipIconColorInput,
            cursorUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleAiBubbleColorInput = MaterialTheme.colorScheme.surface.toArgb(),
            bubbleUserTextColorInput = MaterialTheme.colorScheme.onPrimaryContainer.toArgb(),
            bubbleAiTextColorInput = MaterialTheme.colorScheme.onSurface.toArgb(),
            recentColors = recentColors,
            onColorSelected = { primary,
                secondary,
                statusBar,
                appBar,
                navigationDrawerBackground,
                navigationDrawerAccent,
                historyIcon,
                pipIcon,
                _,
                _,
                _,
                _,
                _ ->
                updateDraftThemeColor(
                    shared = shared,
                    currentColorPickerMode = currentColorPickerMode,
                    primaryColor = primary,
                    secondaryColor = secondary,
                    statusBarColor = statusBar,
                    appBarColor = appBar,
                    navigationDrawerBackgroundColor = navigationDrawerBackground,
                    navigationDrawerAccentColor = navigationDrawerAccent,
                    historyIconColor = historyIcon,
                    pipIconColor = pipIcon,
                )
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

private fun updateDraftThemeColor(
    shared: ThemeSettingsShared,
    currentColorPickerMode: String,
    primaryColor: Int?,
    secondaryColor: Int?,
    statusBarColor: Int?,
    appBarColor: Int?,
    navigationDrawerBackgroundColor: Int?,
    navigationDrawerAccentColor: Int?,
    historyIconColor: Int?,
    pipIconColor: Int?,
) {
    val selectedColor =
        primaryColor ?: secondaryColor ?: statusBarColor ?: appBarColor
            ?: navigationDrawerBackgroundColor ?: navigationDrawerAccentColor
            ?: historyIconColor ?: pipIconColor
    val editorSession = shared.editorSession
    selectedColor?.let { shared.scope.launch { editorSession.addRecentColor(it) } }
    when (currentColorPickerMode) {
        "primary" -> primaryColor?.let { editorSession.setInt("custom_primary_color", it) }
        "secondary" -> secondaryColor?.let { editorSession.setInt("custom_secondary_color", it) }
        "statusBar" -> statusBarColor?.let { editorSession.setInt("custom_status_bar_color", it) }
        "appBar" -> appBarColor?.let { editorSession.setInt("custom_app_bar_color", it) }
        "navigationDrawerBackground" -> navigationDrawerBackgroundColor?.let {
            editorSession.setInt("custom_navigation_drawer_background_color", it)
        }
        "navigationDrawerAccent" -> navigationDrawerAccentColor?.let {
            editorSession.setInt("custom_navigation_drawer_accent_color", it)
        }
        "historyIcon" -> historyIconColor?.let {
            editorSession.setInt("chat_header_history_icon_color", it)
        }
        "pipIcon" -> pipIconColor?.let {
            editorSession.setInt("chat_header_pip_icon_color", it)
        }
    }
}
