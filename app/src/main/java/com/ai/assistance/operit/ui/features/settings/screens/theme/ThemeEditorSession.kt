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
import com.ai.assistance.operit.ui.features.settings.theme.editor.state.ThemeEditorDocument
import com.ai.assistance.operit.ui.features.settings.theme.editor.state.ThemeEditorDocumentAction
import com.ai.assistance.operit.ui.features.settings.theme.editor.state.ThemeEditorSaveRequest
import com.ai.assistance.operit.ui.features.settings.theme.editor.state.reduceThemeEditorDocument
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ThemeEditorSession(
    private val persistentPreferences: UserPreferencesManager,
    initialValues: ThemePreferenceValues,
) {
    private val _document =
        MutableStateFlow(ThemeEditorDocument(baseline = initialValues, draft = initialValues))
    private val stagedAssetUris = mutableSetOf<String>()
    private var assetOperationGeneration = 0L
    private var disposed = false

    val document: StateFlow<ThemeEditorDocument> = _document.asStateFlow()
    val recentColorsFlow: Flow<List<Int>> = persistentPreferences.recentColorsFlow

    val currentValues: ThemePreferenceValues
        get() = _document.value.draft

    val hasUnsavedChanges: Boolean
        get() = _document.value.hasUnsavedChanges

    @Synchronized
    fun update(transform: (ThemePreferenceValues) -> ThemePreferenceValues) {
        if (disposed) return
        val updated = transform(currentValues)
        if (!dispatch(ThemeEditorDocumentAction.Edit(updated))) return
        deleteUnreferencedStagedAssets(updated)
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

    @Synchronized
    fun reset() {
        assetOperationGeneration += 1
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
        dispatch(ThemeEditorDocumentAction.ResetVisual(resetValues))
        deleteUnreferencedStagedAssets(resetValues)
    }

    @Synchronized
    fun discard() {
        assetOperationGeneration += 1
        deleteStagedAssets(stagedAssetUris.toSet())
        dispatch(ThemeEditorDocumentAction.Discard)
    }

    @Synchronized
    fun beginSave(): ThemeEditorSaveRequest {
        val current = _document.value
        val request = ThemeEditorSaveRequest(values = current.draft, saveMode = current.saveMode)
        dispatch(ThemeEditorDocumentAction.BeginSave(request.values))
        return request
    }

    @Synchronized
    fun markSaved(savedValues: ThemePreferenceValues) {
        stagedAssetUris.removeAll(savedValues.strings.values)
        dispatch(ThemeEditorDocumentAction.SaveSucceeded(savedValues))
        if (disposed) {
            deleteStagedAssets(stagedAssetUris.toSet())
        } else {
            deleteUnreferencedStagedAssets(currentValues)
        }
    }

    @Synchronized
    fun cancelSave() {
        dispatch(ThemeEditorDocumentAction.SaveFailed)
        if (disposed) {
            deleteStagedAssets(stagedAssetUris.toSet())
        } else {
            deleteUnreferencedStagedAssets(currentValues)
        }
    }

    @Synchronized
    fun dispose() {
        disposed = true
        assetOperationGeneration += 1
        if (_document.value.savingValues == null) {
            deleteStagedAssets(stagedAssetUris.toSet())
        }
    }

    @Synchronized
    fun registerStagedAsset(uri: String) {
        if (disposed) {
            deleteStagedAssets(setOf(uri))
            return
        }
        stagedAssetUris += uri
    }

    @Synchronized
    fun beginAssetOperation(): Long? {
        if (disposed) return null
        assetOperationGeneration += 1
        return assetOperationGeneration
    }

    @Synchronized
    fun registerStagedAsset(uri: String, generation: Long): Boolean {
        if (disposed || generation != assetOperationGeneration) {
            deleteStagedAssets(setOf(uri))
            return false
        }
        stagedAssetUris += uri
        return true
    }

    suspend fun addRecentColor(color: Int) {
        persistentPreferences.addRecentColor(color)
    }

    private fun deleteUnreferencedStagedAssets(values: ThemePreferenceValues) {
        val referencedUris = buildSet {
            addAll(values.strings.values)
            _document.value.savingValues?.strings?.values?.let(::addAll)
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

    @Synchronized
    private fun dispatch(action: ThemeEditorDocumentAction): Boolean {
        val current = _document.value
        val updated = reduceThemeEditorDocument(current, action)
        if (updated == current) return false
        _document.value = updated
        return true
    }
}
