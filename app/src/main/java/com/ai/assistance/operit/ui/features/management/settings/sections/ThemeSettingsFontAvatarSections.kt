package com.ai.assistance.operit.ui.features.management.settings.sections

import android.content.Context
import androidx.compose.material3.CardColors
import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchFontEditor
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeTypographyColorEditor

@Composable
internal fun ThemeSettingsFontSection(
    cardColors: CardColors,
    context: Context,
    editorSession: ThemeEditorSession,
    useCustomFontInput: Boolean,
    fontTypeInput: String,
    systemFontNameInput: String,
    customFontPathInput: String?,
    fontScaleInput: Float,
    onPickFont: () -> Unit,
) {
    ThemeWorkbenchFontEditor(
        custom = useCustomFontInput,
        onCustomChange = { editorSession.setBoolean("use_custom_font", it) },
        type = fontTypeInput,
        onTypeChange = { editorSession.setString("font_type", it) },
        name = systemFontNameInput,
        onNameChange = { editorSession.setString("system_font_name", it) },
        path = customFontPathInput,
        onPick = onPickFont,
        onClear = { editorSession.setOptionalString("custom_font_path", null) },
        scale = fontScaleInput,
        onScaleChange = { editorSession.setFloat("font_scale", it) },
    )
    ThemeTypographyColorEditor(editorSession)
}
