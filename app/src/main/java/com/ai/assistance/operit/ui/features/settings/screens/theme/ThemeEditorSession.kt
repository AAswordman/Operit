package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.net.Uri
import com.ai.assistance.operit.data.preferences.NativeThemeBooleanField
import com.ai.assistance.operit.data.preferences.NativeThemeFloatField
import com.ai.assistance.operit.data.preferences.NativeThemeIntField
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceRulesV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.NativeThemeStringField
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ThemeEditorSession(
    private val persistentPreferences: UserPreferencesManager,
    initialValues: ThemePreferenceValues,
) {
    private val _values = MutableStateFlow(initialValues)
    private val _hasUnsavedChanges = MutableStateFlow(false)
    private var baselineValues = initialValues
    private var resetRequested = false
    private val stagedAssetUris = mutableSetOf<String>()
    private var inFlightSavedValues: ThemePreferenceValues? = null
    private var disposed = false

    val values: StateFlow<ThemePreferenceValues> = _values.asStateFlow()
    val hasUnsavedChangesFlow: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()
    val recentColorsFlow: Flow<List<Int>> = persistentPreferences.recentColorsFlow

    val currentValues: ThemePreferenceValues
        get() = _values.value

    val hasUnsavedChanges: Boolean
        get() = resetRequested || currentValues != baselineValues

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

    fun setString(field: NativeThemeStringField, value: String) {
        update { it.withString(field, value) }
    }

    fun setOptionalString(name: String, value: String?) {
        update { it.withString(name, value?.takeIf(String::isNotBlank)) }
    }

    fun setOptionalString(field: NativeThemeStringField, value: String?) {
        update { it.withString(field, value?.takeIf(String::isNotBlank)) }
    }

    fun setBoolean(name: String, value: Boolean) {
        setBoolean(NativeThemePreferenceSchemaV1.requireBooleanField(name), value)
    }

    fun setBoolean(field: NativeThemeBooleanField, value: Boolean) {
        update { current -> NativeThemePreferenceRulesV1.applyBooleanChange(current, field, value) }
    }

    fun setInt(name: String, value: Int?) {
        update { it.withInt(name, value) }
    }

    fun setInt(field: NativeThemeIntField, value: Int?) {
        update { it.withInt(field, value) }
    }

    fun setFloat(name: String, value: Float) {
        update { it.withFloat(name, value) }
    }

    fun setFloat(field: NativeThemeFloatField, value: Float) {
        update { it.withFloat(field, value) }
    }

    fun reset() {
        val resetValues =
            ThemePreferenceValues.defaultVisual()
                .withString(
                    NativeThemePreferenceSchemaV1.customAiAvatarUri,
                    currentValues.string(NativeThemePreferenceSchemaV1.customAiAvatarUri),
                )
                .withString(
                    NativeThemePreferenceSchemaV1.customChatTitle,
                    currentValues.string(NativeThemePreferenceSchemaV1.customChatTitle),
                )
        _values.value = resetValues
        resetRequested = true
        deleteUnreferencedStagedAssets(resetValues)
        updateDirtyState()
    }

    fun discard() {
        deleteStagedAssets(stagedAssetUris.toSet())
        _values.value = baselineValues
        resetRequested = false
        updateDirtyState()
    }

    fun beginSave(savedValues: ThemePreferenceValues) {
        inFlightSavedValues = savedValues
    }

    fun markSaved(savedValues: ThemePreferenceValues) {
        stagedAssetUris.removeAll(savedValues.strings.values)
        inFlightSavedValues = null
        baselineValues = savedValues
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
