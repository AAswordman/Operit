package com.ai.assistance.operit.ui.features.management.settings.screens.theme

import android.net.Uri
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ThemeEditorSession(
    private val persistentPreferences: UserPreferencesManager,
    initialValues: ThemePreferenceValues,
    initialGlobalUserName: String,
    initialLayout: ChatLayoutSettings,
) {
    private val _values = MutableStateFlow(initialValues)
    private val _hasUnsavedChanges = MutableStateFlow(false)
    private var baselineValues = initialValues
    private val _globalUserName = MutableStateFlow(initialGlobalUserName)
    private var baselineGlobalUserName = initialGlobalUserName
    private val _layout = MutableStateFlow(initialLayout)
    private var baselineLayout = initialLayout
    private var resetRequested = false
    private val stagedAssetUris = mutableSetOf<String>()
    private var inFlightSavedValues: ThemePreferenceValues? = null
    private var disposed = false

    val values: StateFlow<ThemePreferenceValues> = _values.asStateFlow()
    val globalUserNameFlow: StateFlow<String> = _globalUserName.asStateFlow()
    val layout: StateFlow<ChatLayoutSettings> = _layout.asStateFlow()
    val hasUnsavedChangesFlow: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()
    val recentColorsFlow: Flow<List<Int>> = persistentPreferences.recentColorsFlow

    val currentValues: ThemePreferenceValues
        get() = _values.value

    val currentGlobalUserName: String
        get() = _globalUserName.value

    val currentLayout: ChatLayoutSettings
        get() = _layout.value

    val hasLayoutChanges: Boolean
        get() = currentLayout != baselineLayout

    val hasThemeChanges: Boolean
        get() = resetRequested || currentValues != baselineValues

    val hasGlobalUserNameChanges: Boolean
        get() = currentGlobalUserName != baselineGlobalUserName

    val hasUnsavedChanges: Boolean
        get() = hasThemeChanges || hasGlobalUserNameChanges || hasLayoutChanges

    // 全局名字参与编辑会话的保存和放弃，但不进入角色主题字段。
    fun setGlobalUserName(value: String) {
        if (disposed || value == currentGlobalUserName) return
        _globalUserName.value = value
        updateDirtyState()
    }

    fun updateLayout(transform: (ChatLayoutSettings) -> ChatLayoutSettings) {
        if (disposed) return
        val updated = transform(currentLayout)
        updated.validate()
        if (updated == currentLayout) return
        _layout.value = updated
        updateDirtyState()
    }

    fun resetLayout() = updateLayout { ChatLayoutSettings() }

    val isResetRequested: Boolean
        get() = resetRequested

    fun update(transform: (ThemePreferenceValues) -> ThemePreferenceValues) {
        if (disposed) return
        val updated = transform(currentValues)
        if (updated == currentValues) return
        _values.value = updated
        resetRequested = false
        deleteUnreferencedStagedAssets(updated)
        updateDirtyState()
    }

    fun setString(name: String, value: String) {
        update { it.withString(name, value) }
    }

    fun setOptionalString(name: String, value: String?) {
        update { it.withString(name, value?.takeIf(String::isNotBlank)) }
    }

    fun setBoolean(name: String, value: Boolean) {
        update { current ->
            var updated = current.withBoolean(name, value)
            if (value) {
                updated =
                    when (name) {
                        "chat_input_liquid_glass" ->
                            updated.withBoolean("chat_input_water_glass", false)

                        "chat_input_water_glass" ->
                            updated.withBoolean("chat_input_liquid_glass", false)

                        "cursor_user_bubble_liquid_glass" ->
                            updated.withBoolean("cursor_user_bubble_water_glass", false)

                        "cursor_user_bubble_water_glass" ->
                            updated.withBoolean("cursor_user_bubble_liquid_glass", false)

                        "bubble_user_bubble_liquid_glass" ->
                            updated
                                .withBoolean("bubble_user_bubble_water_glass", false)
                                .withBoolean("bubble_user_use_image", false)

                        "bubble_user_bubble_water_glass" ->
                            updated
                                .withBoolean("bubble_user_bubble_liquid_glass", false)
                                .withBoolean("bubble_user_use_image", false)

                        "bubble_ai_bubble_liquid_glass" ->
                            updated
                                .withBoolean("bubble_ai_bubble_water_glass", false)
                                .withBoolean("bubble_ai_use_image", false)

                        "bubble_ai_bubble_water_glass" ->
                            updated
                                .withBoolean("bubble_ai_bubble_liquid_glass", false)
                                .withBoolean("bubble_ai_use_image", false)

                        else -> updated
                    }
            }
            if (value && (name == "chat_input_liquid_glass" || name == "chat_input_water_glass")) {
                // 开启玻璃时同步启用透明背景，避免开关开启而输入框仍按不透明区域绘制。
                updated = updated.withBoolean("chat_input_transparent", true)
                val key =
                    com.ai.assistance.operit.data.preferences.transparency
                        .ThemeTransparencyPreferences
                        .INPUT
                if (updated.float(key) == 1f) updated = updated.copy(floats = updated.floats - key)
            }
            updated
        }
    }

    fun setInt(name: String, value: Int?) {
        update { it.withInt(name, value) }
    }

    fun setFloat(name: String, value: Float) {
        update { it.withFloat(name, value) }
    }

    fun reset() {
        val resetValues =
            ThemePreferenceValues.defaultVisual()
                .withString("custom_ai_avatar_uri", currentValues.string("custom_ai_avatar_uri"))
                .withString("custom_chat_title", currentValues.string("custom_chat_title"))
        _values.value = resetValues
        resetRequested = true
        deleteUnreferencedStagedAssets(resetValues)
        updateDirtyState()
    }

    fun discard() {
        deleteStagedAssets(stagedAssetUris.toSet())
        _values.value = baselineValues
        _globalUserName.value = baselineGlobalUserName
        _layout.value = baselineLayout
        resetRequested = false
        updateDirtyState()
    }

    fun beginSave(savedValues: ThemePreferenceValues) {
        inFlightSavedValues = savedValues
    }

    fun markSaved(savedValues: ThemePreferenceValues, savedGlobalUserName: String, savedLayout: ChatLayoutSettings) {
        stagedAssetUris.removeAll(savedValues.strings.values)
        inFlightSavedValues = null
        baselineValues = savedValues
        // 保存期间继续输入的名字保留为草稿，只更新本次保存的基线。
        baselineGlobalUserName = savedGlobalUserName
        // 只推进已完成保存的布局基线，保存期间继续调整的数值仍属于未保存草稿。
        baselineLayout = savedLayout
        resetRequested = false
        if (disposed) {
            deleteStagedAssets(stagedAssetUris.toSet())
        } else {
            deleteUnreferencedStagedAssets(currentValues)
        }
        updateDirtyState()
    }

    fun cancelSave() {
        inFlightSavedValues = null
        if (disposed) {
            deleteStagedAssets(stagedAssetUris.toSet())
        } else {
            deleteUnreferencedStagedAssets(currentValues)
        }
    }

    fun dispose() {
        disposed = true
        if (inFlightSavedValues == null) {
            deleteStagedAssets(stagedAssetUris.toSet())
        }
    }

    fun registerStagedAsset(uri: String) {
        if (disposed) {
            deleteStagedAssets(setOf(uri))
            return
        }
        stagedAssetUris += uri
    }

    suspend fun addRecentColor(color: Int) {
        persistentPreferences.addRecentColor(color)
    }

    private fun deleteUnreferencedStagedAssets(values: ThemePreferenceValues) {
        val referencedUris = buildSet {
            addAll(values.strings.values)
            inFlightSavedValues?.strings?.values?.let(::addAll)
        }
        deleteStagedAssets(stagedAssetUris.filterNot(referencedUris::contains).toSet())
    }

    private fun deleteStagedAssets(uris: Set<String>) {
        uris.forEach { uriString ->
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                uri.path?.let { path -> File(path).delete() }
            }
            stagedAssetUris.remove(uriString)
        }
    }

    private fun updateDirtyState() {
        _hasUnsavedChanges.value = hasUnsavedChanges
    }
}
