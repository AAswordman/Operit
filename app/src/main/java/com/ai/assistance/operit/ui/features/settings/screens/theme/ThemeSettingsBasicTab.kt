package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorContentMode
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorCustomizationSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsFontSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsThemeModeSection
import com.ai.assistance.operit.ui.main.components.rememberNavigationDrawerAppearance
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import kotlinx.coroutines.launch

@Composable
internal fun ThemeSettingsBasicTab(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
) {
    val editorSession = shared.editorSession
    val values by editorSession.values.collectAsState()
    val pickGlobalFont = rememberGlobalFontPicker(
        context = shared.context,
        shared = shared,
    )

    ThemeSettingsThemeModeSection(
        cardColors = cardColors,
        editorSession = editorSession,
        useSystemThemeInput = values.requiredBoolean("use_system_theme"),
        themeModeInput = values.requiredString("theme_mode"),
    )

    ThemeSettingsBasicColorPanel(
        shared = shared,
        cardColors = cardColors,
        values = values,
    )

    ThemeSettingsFontSection(
        cardColors = cardColors,
        context = shared.context,
        editorSession = editorSession,
        useCustomFontInput = values.requiredBoolean("use_custom_font"),
        fontTypeInput = values.requiredString("font_type"),
        systemFontNameInput = values.requiredString("system_font_name"),
        customFontPathInput = values.string("custom_font_path"),
        fontScaleInput = values.requiredFloat("font_scale"),
        onPickFont = pickGlobalFont,
    )
}

@Composable
private fun rememberGlobalFontPicker(
    context: Context,
    shared: ThemeSettingsShared,
): () -> Unit {
    val editorSession = shared.editorSession
    val fontPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                shared.scope.launch {
                    val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                    if (extension != null && (extension == "ttf" || extension == "otf" || extension == "ttc")) {
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, uri, "custom_font")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Font file saved to: $internalUri")
                            val internalUriString = internalUri.toString()
                            editorSession.registerStagedAsset(internalUriString)
                            editorSession.update { current ->
                                current
                                    .withString("custom_font_path", internalUriString)
                                    .withString(
                                        "font_type",
                                        UserPreferencesManager.FONT_TYPE_FILE,
                                    )
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.font_file_saved, extension),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.font_file_save_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.unsupported_font_format),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    return { fontPickerLauncher.launch("*/*") }
}

@Composable
private fun ThemeSettingsBasicColorPanel(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
    values: ThemePreferenceValues,
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
    val statusBarHiddenInput = values.requiredBoolean("status_bar_hidden")
    val statusBarTransparentInput = values.requiredBoolean("status_bar_transparent")
    val useCustomStatusBarColorInput = values.requiredBoolean("use_custom_status_bar_color")
    val customStatusBarColorInput = values.int("custom_status_bar_color") ?: defaultStatusBarColor
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
        contentMode = ThemeSettingsColorContentMode.PALETTE,
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
    selectedColor?.let { color ->
        shared.scope.launch { editorSession.addRecentColor(color) }
    }
}
