package com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.theme.getSystemFontFamily
import com.ai.assistance.operit.ui.theme.resolveConfiguredFontFamily
import kotlin.math.roundToInt

@Composable
internal fun themeWorkbenchFontLabel(
    custom: Boolean,
    type: String,
    name: String,
    path: String?,
    message: Boolean = false,
): String {
    if (!custom)
        return stringResource(
            if (message) R.string.theme_workbench_follow_theme else R.string.theme_font_default
        )
    if (type == UserPreferencesManager.FONT_TYPE_FILE)
        return path?.substringAfterLast('/') ?: stringResource(R.string.custom_font_file)
    return stringResource(
        when (name) {
            UserPreferencesManager.SYSTEM_FONT_SERIF -> R.string.theme_font_serif
            UserPreferencesManager.SYSTEM_FONT_SANS_SERIF -> R.string.theme_font_sans_serif
            UserPreferencesManager.SYSTEM_FONT_MONOSPACE -> R.string.theme_font_monospace
            UserPreferencesManager.SYSTEM_FONT_CURSIVE -> R.string.theme_font_cursive
            else -> R.string.theme_font_default
        }
    )
}

/** 界面与消息字体使用相同的选择行和样张，文件仍由原选择器暂存。 */
@Composable
internal fun ThemeWorkbenchFontEditor(
    custom: Boolean,
    onCustomChange: (Boolean) -> Unit,
    type: String,
    onTypeChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    path: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    message: Boolean = false,
    scale: Float? = null,
    onScaleChange: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val inherited = MaterialTheme.typography.bodyMedium.fontFamily
    val family =
        remember(context, custom, type, name, path, inherited, message) {
            if (!custom && !message) FontFamily.Default
            else if (!custom) inherited
            else resolveConfiguredFontFamily(context, custom, type, name, path)
        }
    Column(Modifier.selectableGroup()) {
        ThemeWorkbenchChoiceRow(
            stringResource(
                if (message) R.string.theme_workbench_follow_theme
                else R.string.theme_workbench_system_default
            ),
            selected =
                !custom ||
                    (custom &&
                        type == UserPreferencesManager.FONT_TYPE_SYSTEM &&
                        name == UserPreferencesManager.SYSTEM_FONT_DEFAULT),
            onClick = { onCustomChange(false) },
        )
        listOf(
                UserPreferencesManager.SYSTEM_FONT_SERIF to R.string.theme_font_serif,
                UserPreferencesManager.SYSTEM_FONT_SANS_SERIF to R.string.theme_font_sans_serif,
                UserPreferencesManager.SYSTEM_FONT_MONOSPACE to R.string.theme_font_monospace,
                UserPreferencesManager.SYSTEM_FONT_CURSIVE to R.string.theme_font_cursive,
            )
            .forEach { (font, label) ->
                ThemeWorkbenchDivider()
                ThemeWorkbenchChoiceRow(
                    stringResource(label),
                    selected =
                        custom && type == UserPreferencesManager.FONT_TYPE_SYSTEM && name == font,
                    fontFamily = getSystemFontFamily(font),
                    onClick = {
                        onTypeChange(UserPreferencesManager.FONT_TYPE_SYSTEM)
                        onNameChange(font)
                        onCustomChange(true)
                    },
                )
            }
        ThemeWorkbenchDivider()
        ThemeWorkbenchChoiceRow(
            stringResource(R.string.custom_font_file),
            selected = custom && type == UserPreferencesManager.FONT_TYPE_FILE,
            subtitle =
                path?.substringAfterLast('/') ?: stringResource(R.string.font_file_support_desc),
            onClick = {
                if (path.isNullOrBlank()) onPick()
                else {
                    onTypeChange(UserPreferencesManager.FONT_TYPE_FILE)
                    onCustomChange(true)
                }
            },
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onPick,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.select_font_file))
        }
        if (!path.isNullOrBlank())
            TextButton(onClick = onClear) { Text(stringResource(R.string.clear_font)) }
    }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            stringResource(R.string.theme_workbench_font_sample),
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = family,
        )
    }
    if (scale != null && onScaleChange != null)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.theme_workbench_font_size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${(scale*100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ThemeWorkbenchSlider(
                scale,
                onValueChange = onScaleChange,
                valueRange = 0.8f..1.5f,
                steps = 6,
            )
        }
}
