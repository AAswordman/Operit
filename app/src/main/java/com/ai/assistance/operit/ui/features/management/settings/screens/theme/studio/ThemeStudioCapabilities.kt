package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import com.ai.assistance.operit.data.theme.script.ThemeScriptContract

/** 只列出实际接入的属性；固定尺寸、位图和原生浮层不展示没有消费入口的控件。 */
internal enum class StudioSurfaceControl {
    ContentColor, Stroke, Shape, Padding, MinHeight, Elevation, Typography, AvatarSize, MessageWidth, PopupOffset,
}

private val geometryControls = setOf(StudioSurfaceControl.Stroke, StudioSurfaceControl.Shape, StudioSurfaceControl.Padding)
private val textControls = setOf(StudioSurfaceControl.ContentColor, StudioSurfaceControl.Typography)

internal val ThemeStudioSurfaceControls = ThemeScriptContract.surfaceIds.associateWith { id ->
    if (id in ThemeScriptContract.actionButtonIds) geometryControls + StudioSurfaceControl.ContentColor else when (id) {
        "background" -> emptySet()
        "userAvatar", "assistantAvatar" -> geometryControls + StudioSurfaceControl.AvatarSize
        "iconButton" -> geometryControls + StudioSurfaceControl.ContentColor
        "appBar", "navigation", "navigationItem" -> geometryControls + textControls
        "userMessage", "assistantMessage" -> geometryControls + textControls + setOf(StudioSurfaceControl.MinHeight, StudioSurfaceControl.MessageWidth)
        "composer", "input", "settingsCard", "listItem", "status", "toolCard" -> geometryControls + textControls + StudioSurfaceControl.MinHeight
        "button", "dialog" -> geometryControls + textControls + setOf(StudioSurfaceControl.MinHeight, StudioSurfaceControl.Elevation)
        "menu", "sheet" -> geometryControls + StudioSurfaceControl.MinHeight
        "attachmentMenu", "modelMenu", "chatSettingsMenu" -> geometryControls + setOf(StudioSurfaceControl.MinHeight, StudioSurfaceControl.PopupOffset)
        else -> error("Surface controls have not been audited: $id")
    }
}

internal fun StudioTarget.supports(control: StudioSurfaceControl): Boolean =
    kind == StudioKind.Surface && control in ThemeStudioSurfaceControls.getValue(id)
