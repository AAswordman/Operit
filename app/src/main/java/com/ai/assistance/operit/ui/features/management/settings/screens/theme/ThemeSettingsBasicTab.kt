package com.ai.assistance.operit.ui.features.management.settings.screens.theme

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.management.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.management.settings.language.ThemeLanguageSection
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.*
import com.ai.assistance.operit.ui.features.management.settings.sections.ThemeSettingsColorCustomizationSection
import com.ai.assistance.operit.ui.features.management.settings.sections.ThemeSettingsFontSection
import com.ai.assistance.operit.ui.features.management.settings.sections.ThemeSettingsThemeModeSection
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.files.FileUtils
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDialog
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchRow
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.themeWorkbenchFontLabel

@Composable
internal fun ThemeSettingsBasicTab(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
    languageChangeEnabled: Boolean,
    onLanguageSelected: (String) -> Unit,
) {
    val editorSession = shared.editorSession
    val values by editorSession.values.collectAsState()
    val pickGlobalFont = rememberGlobalFontPicker(context = shared.context, shared = shared)

    ThemeSettingsThemeModeSection(
        cardColors = cardColors,
        editorSession = editorSession,
        useSystemThemeInput = values.requiredBoolean("use_system_theme"),
        themeModeInput = values.requiredString("theme_mode"),
    )

    var showColors by remember { mutableStateOf(false) }
    var showFont by remember { mutableStateOf(false) }
    ThemeWorkbenchCard(stringResource(R.string.theme_title_color), icon = Icons.Default.Palette) {
        ThemeWorkbenchRow(
            title = stringResource(R.string.theme_custom_color),
            value =
                stringResource(
                    if (values.requiredBoolean("use_custom_colors")) R.string.theme_workbench_custom
                    else R.string.theme_workbench_default
                ),
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        Modifier.size(18.dp)
                            .background(
                                Color(
                                    values.int("custom_primary_color")
                                        ?: MaterialTheme.colorScheme.primary.toArgb()
                                ),
                                CircleShape,
                            )
                    )
                    Box(
                        Modifier.size(18.dp)
                            .background(
                                Color(
                                    values.int("custom_secondary_color")
                                        ?: MaterialTheme.colorScheme.secondary.toArgb()
                                ),
                                CircleShape,
                            )
                    )
                }
            },
            onClick = { showColors = true },
        )
    }
    ThemeWorkbenchCard(
        stringResource(R.string.theme_font_settings),
        icon = Icons.Default.TextFields,
    ) {
        ThemeWorkbenchRow(
            title = stringResource(R.string.theme_font_settings),
            subtitle =
                themeWorkbenchFontLabel(
                    values.requiredBoolean("use_custom_font"),
                    values.requiredString("font_type"),
                    values.requiredString("system_font_name"),
                    values.string("custom_font_path"),
                ),
            value =
                stringResource(
                    R.string.theme_workbench_font_scale,
                    (values.requiredFloat("font_scale") * 100).toInt(),
                ),
            onClick = { showFont = true },
        )
    }
    if (showColors)
        ThemeWorkbenchDialog(stringResource(R.string.theme_custom_color), { showColors = false }) {
            ThemeSettingsBasicColorPanel(shared, cardColors, values)
        }
    if (showFont)
        ThemeWorkbenchDialog(stringResource(R.string.theme_font_settings), { showFont = false }) {
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
    com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio.ThemeStudioEntry(editorSession, shared.projectKey, shared.targetName)
    ThemeLanguageSection(cardColors, languageChangeEnabled, onLanguageSelected)
}

@Composable
private fun rememberGlobalFontPicker(context: Context, shared: ThemeSettingsShared): () -> Unit {
    val editorSession = shared.editorSession
    val fontPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
            uri: Uri? ->
            if (uri != null) {
                shared.scope.launch {
                    val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                    if (
                        extension != null &&
                            (extension == "ttf" || extension == "otf" || extension == "ttc")
                    ) {
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, uri, "custom_font")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Font file saved to: $internalUri")
                            val internalUriString = internalUri.toString()
                            editorSession.registerStagedAsset(internalUriString)
                            editorSession.update { current ->
                                current
                                    .withString("custom_font_path", internalUriString)
                                    .withString("font_type", UserPreferencesManager.FONT_TYPE_FILE)
                                    .withBoolean("use_custom_font", true)
                            }
                            OKToast.success(
                                    context,
                                    context.getString(R.string.font_file_saved, extension),
                                    OKToast.LENGTH_SHORT,
                                )
                                .show()
                        } else {
                            OKToast.error(
                                    context,
                                    context.getString(R.string.font_file_save_failed),
                                    OKToast.LENGTH_LONG,
                                )
                                .show()
                        }
                    } else {
                        OKToast.warning(
                                context,
                                context.getString(R.string.unsupported_font_format),
                                OKToast.LENGTH_LONG,
                            )
                            .show()
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
    val defaultPrimaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val defaultSecondaryColor = MaterialTheme.colorScheme.secondary.toArgb()
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
        useCustomColorsInput = useCustomColorsInput,
        primaryColorInput = primaryColorInput,
        secondaryColorInput = secondaryColorInput,
        onColorModeInput = onColorModeInput,
        onShowColorPicker = {
            currentColorPickerMode = it
            showColorPicker = true
        },
    )

    if (showColorPicker) {
        ColorPickerDialog(
            showColorPicker = showColorPicker,
            currentColorPickerMode = currentColorPickerMode,
            primaryColorInput = primaryColorInput,
            secondaryColorInput = secondaryColorInput,
            cursorUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleAiBubbleColorInput = MaterialTheme.colorScheme.surface.toArgb(),
            bubbleUserTextColorInput = MaterialTheme.colorScheme.onPrimaryContainer.toArgb(),
            bubbleAiTextColorInput = MaterialTheme.colorScheme.onSurface.toArgb(),
            recentColors = recentColors,
            onColorSelected = { primary, secondary, _, _, _, _, _ ->
                updateDraftThemeColor(
                    shared = shared,
                    currentColorPickerMode = currentColorPickerMode,
                    primaryColor = primary,
                    secondaryColor = secondary,
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
) {
    val selectedColor = primaryColor ?: secondaryColor
    val editorSession = shared.editorSession
    when (currentColorPickerMode) {
        "primary" -> primaryColor?.let { editorSession.setInt("custom_primary_color", it) }
        "secondary" -> secondaryColor?.let { editorSession.setInt("custom_secondary_color", it) }
    }
    selectedColor?.let { color -> shared.scope.launch { editorSession.addRecentColor(color) } }
}
