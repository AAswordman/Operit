package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import android.content.Context

/** 编辑位置属于本机工程，不随主题导出，也不参与主题草稿的保存或放弃。 */
internal data class StudioLocation(
    val mode: String,
    val targetId: String,
    val workspace: StudioWorkspace,
    val scene: StudioScene,
    val property: StudioProperty,
    val previewState: String,
    val inputStyle: String,
    val previewCollapsed: Boolean,
)

internal class ThemeStudioLocationStore(context: Context, projectId: String) {
    private val preferences = context.applicationContext.getSharedPreferences("theme_studio_location", Context.MODE_PRIVATE)
    private val prefix = "$projectId."

    fun read(initial: StudioLocation): StudioLocation = StudioLocation(
        mode = requireNotNull(preferences.getString(prefix + "mode", initial.mode)),
        targetId = requireNotNull(preferences.getString(prefix + "target", initial.targetId)),
        workspace = StudioWorkspace.valueOf(requireNotNull(preferences.getString(prefix + "workspace", initial.workspace.name))),
        scene = StudioScene.valueOf(requireNotNull(preferences.getString(prefix + "scene", initial.scene.name))),
        property = StudioProperty.valueOf(requireNotNull(preferences.getString(prefix + "property", initial.property.name))),
        previewState = requireNotNull(preferences.getString(prefix + "state", initial.previewState)),
        inputStyle = requireNotNull(preferences.getString(prefix + "input_style", initial.inputStyle)),
        previewCollapsed = preferences.getBoolean(prefix + "preview_collapsed", initial.previewCollapsed),
    )

    fun write(location: StudioLocation) {
        // 一次事务保存整组位置，避免模式与编辑对象来自不同次切换。
        preferences.edit()
            .putString(prefix + "mode", location.mode)
            .putString(prefix + "target", location.targetId)
            .putString(prefix + "workspace", location.workspace.name)
            .putString(prefix + "scene", location.scene.name)
            .putString(prefix + "property", location.property.name)
            .putString(prefix + "state", location.previewState)
            .putString(prefix + "input_style", location.inputStyle)
            .putBoolean(prefix + "preview_collapsed", location.previewCollapsed)
            .apply()
    }
}
