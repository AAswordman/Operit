package com.ai.assistance.operit.ui.features.management.settings.screens.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.data.preferences.transparency.ThemeTransparencyPreferences
import com.ai.assistance.operit.ui.features.management.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.management.settings.components.transparency.BackgroundTransparencySlider
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.*
import com.ai.assistance.operit.ui.features.management.settings.sections.glass.GlassParametersEntry
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchColorRow
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDialog
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDivider
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchRow
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSegments
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSwitchRow
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchTextureEditor
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.themeWorkbenchTextureLabel

@Composable
internal fun ThemeSettingsInputTab(shared: ThemeSettingsShared, cardColors: CardColors) {
    val editorSession = shared.editorSession
    val values by editorSession.values.collectAsState()
    val inputStyle = values.requiredString("input_style")
    val isDarkTheme = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val legacyOpaqueOpacity =
        when {
            !values.requiredBoolean("use_background_image") -> 1f
            inputStyle == UserPreferencesManager.INPUT_STYLE_AGENT && isDarkTheme -> 0.82f
            else -> 0.85f
        }
    val chatInputFloating = values.requiredBoolean("chat_input_floating")
    val chatInputLiquidGlass = values.requiredBoolean("chat_input_liquid_glass")
    val chatInputWaterGlass = values.requiredBoolean("chat_input_water_glass")
    val legacyGlassEnabled =
        (chatInputWaterGlass && isWaterGlassSupported()) ||
            (!chatInputWaterGlass && chatInputLiquidGlass && isLiquidGlassSupported())
    val legacyTransparentOpacity =
        if (legacyGlassEnabled) values.requiredFloat("glass_tint_opacity") else 0f
    val inputOpacity =
        ThemeTransparencyPreferences.inputOpacity(
            values,
            opaqueOpacity = legacyOpaqueOpacity,
            transparentOpacity = legacyTransparentOpacity,
        )
    val thinkingParticleColorInput = values.int("thinking_particle_color") ?: Color.White.toArgb()
    val recentColors by editorSession.recentColorsFlow.collectAsState(initial = emptyList())
    var showThinkingParticleColorPicker by remember { mutableStateOf(false) }

    var showInputTexture by remember { mutableStateOf(false) }
    ThemeWorkbenchCard(stringResource(R.string.input_style_title), icon = Icons.Default.Keyboard) {
        ThemeWorkbenchSegments(
            options =
                listOf(
                    UserPreferencesManager.INPUT_STYLE_CLASSIC to
                        stringResource(R.string.input_style_classic),
                    UserPreferencesManager.INPUT_STYLE_AGENT to
                        stringResource(R.string.input_style_agent),
                ),
            selected = inputStyle,
            onSelected = { editorSession.setString("input_style", it) },
        )
        ThemeWorkbenchDivider()
        ThemeWorkbenchSwitchRow(
            stringResource(R.string.theme_chat_input_floating),
            chatInputFloating,
            subtitle = stringResource(R.string.theme_chat_input_floating_desc),
        ) {
            editorSession.setBoolean("chat_input_floating", it)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            BackgroundTransparencySlider(
                stringResource(R.string.theme_input_background_transparency),
                inputOpacity,
            ) { opacity ->
                editorSession.update {
                    it.withFloat(ThemeTransparencyPreferences.INPUT, opacity)
                        .withBoolean("chat_input_transparent", opacity < 1f)
                }
            }
        }
        ThemeWorkbenchDivider()
        ThemeWorkbenchRow(
            title = stringResource(R.string.theme_workbench_input_texture),
            value = themeWorkbenchTextureLabel(chatInputLiquidGlass, chatInputWaterGlass),
            onClick = { showInputTexture = true },
        )
        ThemeWorkbenchDivider()
        ThemeWorkbenchColorRow(
            stringResource(R.string.theme_thinking_particle_color_title),
            Color(thinkingParticleColorInput),
        ) {
            showThinkingParticleColorPicker = true
        }
    }
    if (showInputTexture)
        ThemeWorkbenchDialog(
            stringResource(R.string.theme_workbench_input_texture),
            { showInputTexture = false },
        ) {
            ThemeWorkbenchTextureEditor(
                editorSession,
                "chat_input",
                chatInputLiquidGlass,
                chatInputWaterGlass,
                rows = true,
            )
            ThemeWorkbenchDivider()
            GlassParametersEntry(editorSession, GlassRegion.INPUT)
        }
    if (showThinkingParticleColorPicker) {
        ColorPickerDialog(
            showColorPicker = true,
            currentColorPickerMode = "thinkingParticle",
            primaryColorInput = MaterialTheme.colorScheme.primary.toArgb(),
            secondaryColorInput = MaterialTheme.colorScheme.secondary.toArgb(),
            cursorUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleAiBubbleColorInput = MaterialTheme.colorScheme.surface.toArgb(),
            bubbleUserTextColorInput = MaterialTheme.colorScheme.onPrimaryContainer.toArgb(),
            bubbleAiTextColorInput = MaterialTheme.colorScheme.onSurface.toArgb(),
            recentColors = recentColors,
            thinkingParticleColorInput = thinkingParticleColorInput,
            onColorSelected = { _, _, _, _, _, _, _ -> },
            onSimpleColorSelected = { color ->
                editorSession.setInt("thinking_particle_color", color)
                shared.scope.launch { editorSession.addRecentColor(color) }
            },
            onDismiss = { showThinkingParticleColorPicker = false },
        )
    }
}
