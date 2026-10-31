package com.ai.assistance.operit.ui.features.management.settings.screens.theme.script

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R

@Composable
internal fun themeAppearanceAreaLabel(id: String): String = stringResource(when (id) {
    "background" -> R.string.theme_area_background
    "userMessage" -> R.string.theme_area_user_message
    "assistantMessage" -> R.string.theme_area_assistant_message
    "composer" -> R.string.theme_area_composer
    "userAvatar" -> R.string.theme_area_user_avatar
    "assistantAvatar" -> R.string.theme_area_assistant_avatar
    "appBar" -> R.string.theme_area_app_bar
    "navigation" -> R.string.theme_area_navigation
    "navigationItem" -> R.string.theme_area_navigation_item
    "button" -> R.string.theme_area_button
    "iconButton" -> R.string.theme_area_icon_button
    "input" -> R.string.theme_area_input
    "settingsCard" -> R.string.theme_area_settings_card
    "dialog" -> R.string.theme_area_dialog
    "sheet" -> R.string.theme_area_sheet
    "menu" -> R.string.theme_area_menu
    "listItem" -> R.string.theme_area_list_item
    "status" -> R.string.theme_area_status
    "toolCard" -> R.string.theme_area_tool_card
    else -> error("Unknown appearance area")
})

@Composable
internal fun themeAppearanceIconLabel(id: String): String = stringResource(when (id) {
    "composer.settings" -> R.string.theme_icon_settings
    "composer.attachment" -> R.string.theme_icon_attachment
    "composer.microphone" -> R.string.theme_icon_microphone
    "composer.send" -> R.string.theme_icon_send
    "composer.stop" -> R.string.theme_icon_stop
    "composer.fullscreen" -> R.string.theme_icon_fullscreen
    "composer.queue" -> R.string.theme_icon_queue
    "composer.expand" -> R.string.theme_icon_expand
    "appBar.back" -> R.string.theme_icon_back
    "appBar.menu" -> R.string.theme_icon_menu
    "appBar.workspace" -> R.string.workspace_hub_entry
    "appBar.floating" -> R.string.theme_icon_floating
    "navigation.item" -> R.string.theme_icon_navigation
    else -> error("Unknown appearance icon")
})