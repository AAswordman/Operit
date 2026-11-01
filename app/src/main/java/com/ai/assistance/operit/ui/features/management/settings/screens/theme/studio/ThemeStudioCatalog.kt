package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.theme.ThemeScriptContract
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.script.themeAppearanceAreaLabel
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.script.themeAppearanceIconLabel

internal enum class StudioWorkspace(val label: Int) { Design(R.string.studio_design), Source(R.string.studio_source), Resources(R.string.studio_resources) }
internal enum class StudioScene(val label: Int) { Chat(R.string.studio_scene_chat), Components(R.string.studio_scene_components), Navigation(R.string.studio_scene_navigation) }
internal enum class StudioProperty(val label: Int) { Appearance(R.string.studio_appearance), Layers(R.string.studio_layers), States(R.string.studio_states) }
internal enum class StudioKind { Surface, Icon, Typography, Palette }
internal enum class StudioGroup(val label: Int) { Global(R.string.studio_group_global), Messages(R.string.studio_group_messages), Input(R.string.studio_group_input), Navigation(R.string.studio_group_navigation), Buttons(R.string.studio_group_buttons), Settings(R.string.studio_group_settings), Panels(R.string.studio_group_panels), Icons(R.string.studio_group_icons) }

/** 能力目录同时驱动页签和编辑项，未开放的属性不生成空标签。 */
internal data class StudioTarget(
    val id: String,
    val kind: StudioKind,
    val group: StudioGroup,
    val scene: StudioScene,
    val states: List<String> = emptyList(),
) {
    val properties: List<StudioProperty> get() = buildList {
        add(StudioProperty.Appearance)
        if (kind == StudioKind.Surface) add(StudioProperty.Layers)
        if (states.isNotEmpty()) add(StudioProperty.States)
    }
    val glyph: ImageVector get() = when (id) {
        "typography" -> Icons.Default.TextFields
        "palette" -> Icons.Default.Palette
        "background" -> Icons.Default.Image
        "userMessage" -> Icons.Default.ChatBubbleOutline
        "assistantMessage" -> Icons.Default.ChatBubble
        "composer" -> Icons.Default.Keyboard
        "userAvatar" -> Icons.Default.Person
        "assistantAvatar" -> Icons.Default.SmartToy
        "appBar" -> Icons.Default.ViewComfy
        "navigation" -> Icons.Default.ViewSidebar
        "navigationItem" -> Icons.Default.FormatListBulleted
        "button" -> Icons.Default.TouchApp
        "iconButton" -> Icons.Default.Apps
        "input" -> Icons.Default.TextSnippet
        "settingsCard" -> Icons.Default.SettingsApplications
        "dialog" -> Icons.Default.Comment
        "sheet" -> Icons.Default.OpenInBrowser
        "menu" -> Icons.Default.MoreVert
        "listItem" -> Icons.Default.ViewList
        "status" -> Icons.Default.CheckCircle
        "toolCard" -> Icons.Default.Terminal
        "composer.settings" -> Icons.Default.Settings
        "composer.attachment" -> Icons.Default.AttachFile
        "composer.microphone" -> Icons.Default.Mic
        "composer.send" -> Icons.AutoMirrored.Filled.Send
        "composer.stop" -> Icons.Default.Stop
        "composer.fullscreen" -> Icons.Default.Fullscreen
        "composer.queue" -> Icons.Default.Queue
        "composer.expand" -> Icons.Default.ExpandMore
        "appBar.back" -> Icons.AutoMirrored.Filled.ArrowBack
        "appBar.menu" -> Icons.Default.Menu
        "appBar.workspace" -> Icons.Default.Code
        "appBar.floating" -> Icons.Default.PictureInPicture
        "navigation.item" -> Icons.Default.Chat
        else -> error("Unknown studio target: $id")
    }
}

internal val ThemeStudioTargets: List<StudioTarget> = buildList {
    add(StudioTarget("typography", StudioKind.Typography, StudioGroup.Global, StudioScene.Chat))
    add(StudioTarget("palette", StudioKind.Palette, StudioGroup.Global, StudioScene.Chat))
    ThemeScriptContract.surfaceIds.forEach { id ->
        val group = when (id) {
            "background" -> StudioGroup.Global
            "userMessage", "assistantMessage", "userAvatar", "assistantAvatar", "status", "toolCard" -> StudioGroup.Messages
            "composer", "input" -> StudioGroup.Input
            "appBar", "navigation", "navigationItem" -> StudioGroup.Navigation
            "button", "iconButton" -> StudioGroup.Buttons
            "settingsCard", "listItem" -> StudioGroup.Settings
            else -> StudioGroup.Panels
        }
        val scene = when (group) {
            StudioGroup.Navigation -> StudioScene.Navigation
            StudioGroup.Settings, StudioGroup.Panels -> StudioScene.Components
            StudioGroup.Messages -> if (id == "toolCard") StudioScene.Components else StudioScene.Chat
            StudioGroup.Buttons -> if (id == "button") StudioScene.Components else StudioScene.Chat
            else -> StudioScene.Chat
        }
        val states = when (id) {
            "button", "iconButton" -> listOf("pressed", "focused", "disabled")
            "navigationItem" -> listOf("selected")
            else -> emptyList()
        }
        add(StudioTarget(id, StudioKind.Surface, group, scene, states))
    }
    ThemeScriptContract.iconIds.forEach { id ->
        add(StudioTarget(id, StudioKind.Icon, StudioGroup.Icons, if (id.startsWith("appBar.") || id == "navigation.item") StudioScene.Navigation else StudioScene.Chat))
    }
}

@Composable
internal fun StudioTarget.label(): String = when (kind) {
    StudioKind.Surface -> themeAppearanceAreaLabel(id)
    StudioKind.Icon -> themeAppearanceIconLabel(id)
    StudioKind.Typography -> stringResource(R.string.studio_typography)
    StudioKind.Palette -> stringResource(R.string.studio_palette)
}

@Composable
internal fun studioStateLabel(state: String): String = stringResource(when (state) {
    "always" -> R.string.studio_default
    "pressed" -> R.string.theme_appearance_pressed
    "focused" -> R.string.theme_appearance_focused
    "selected" -> R.string.studio_selected
    "disabled" -> R.string.theme_appearance_disabled
    "error" -> R.string.studio_error_state
    else -> error("Unknown theme state")
})

internal val ThemeStudioPaletteLabels = mapOf(
    "primary" to R.string.studio_token_primary,
    "onPrimary" to R.string.studio_token_on_primary,
    "primaryContainer" to R.string.studio_token_primary_container,
    "onPrimaryContainer" to R.string.studio_token_on_primary_container,
    "inversePrimary" to R.string.studio_token_inverse_primary,
    "secondary" to R.string.studio_token_secondary,
    "onSecondary" to R.string.studio_token_on_secondary,
    "secondaryContainer" to R.string.studio_token_secondary_container,
    "onSecondaryContainer" to R.string.studio_token_on_secondary_container,
    "tertiary" to R.string.studio_token_tertiary,
    "onTertiary" to R.string.studio_token_on_tertiary,
    "tertiaryContainer" to R.string.studio_token_tertiary_container,
    "onTertiaryContainer" to R.string.studio_token_on_tertiary_container,
    "background" to R.string.studio_token_background,
    "onBackground" to R.string.studio_token_on_background,
    "surface" to R.string.studio_token_surface,
    "onSurface" to R.string.studio_token_on_surface,
    "surfaceVariant" to R.string.studio_token_surface_variant,
    "onSurfaceVariant" to R.string.studio_token_on_surface_variant,
    "surfaceTint" to R.string.studio_token_surface_tint,
    "inverseSurface" to R.string.studio_token_inverse_surface,
    "inverseOnSurface" to R.string.studio_token_inverse_on_surface,
    "error" to R.string.studio_token_error,
    "onError" to R.string.studio_token_on_error,
    "errorContainer" to R.string.studio_token_error_container,
    "onErrorContainer" to R.string.studio_token_on_error_container,
    "outline" to R.string.studio_token_outline,
    "outlineVariant" to R.string.studio_token_outline_variant,
    "scrim" to R.string.studio_token_scrim,
    "surfaceBright" to R.string.studio_token_surface_bright,
    "surfaceDim" to R.string.studio_token_surface_dim,
    "surfaceContainerLowest" to R.string.studio_token_surface_container_lowest,
    "surfaceContainerLow" to R.string.studio_token_surface_container_low,
    "surfaceContainer" to R.string.studio_token_surface_container,
    "surfaceContainerHigh" to R.string.studio_token_surface_container_high,
    "surfaceContainerHighest" to R.string.studio_token_surface_container_highest,
)
