package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeAssetActionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeColorControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorDefinitionV1
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import kotlinx.coroutines.launch

private data class NativeThemeColorPickerRequestV1(
    val definition: NativeThemeColorControlDefinitionV1,
    val initialColor: Int,
)

internal enum class ThemeSettingsBasicSection {
    COLORS_AND_MODE,
    TYPOGRAPHY,
}

@Composable
internal fun ThemeSettingsBasicTab(
    shared: ThemeSettingsShared,
    section: ThemeSettingsBasicSection,
) {
    val editorSession = shared.editorSession
    val editorDocument by editorSession.document.collectAsState()
    val values = editorDocument.draft
    val recentColors by editorSession.recentColorsFlow.collectAsState(initial = emptyList())
    var colorPickerRequest by
        remember(section) { mutableStateOf<NativeThemeColorPickerRequestV1?>(null) }
    val pickGlobalFont =
        rememberGlobalFontPicker(
            context = shared.context,
            shared = shared,
        )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        if (section == ThemeSettingsBasicSection.COLORS_AND_MODE) {
            NativeThemeEditorDefinitionV1.colorsAndMode.groups.forEachIndexed { index, group ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                NativeThemeEditorGroupV1(
                    definition = group,
                    values = values,
                    editorSession = editorSession,
                    onColorRequested = { definition, color ->
                        colorPickerRequest = NativeThemeColorPickerRequestV1(definition, color)
                    },
                )
            }
        }
        if (section == ThemeSettingsBasicSection.TYPOGRAPHY) {
            NativeThemeEditorDefinitionV1.typography.groups.forEachIndexed { index, group ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                NativeThemeEditorGroupV1(
                    definition = group,
                    values = values,
                    editorSession = editorSession,
                    onAssetRequested = { definition ->
                        when (definition.action) {
                            NativeThemeAssetActionV1.APP_FONT -> pickGlobalFont()
                            NativeThemeAssetActionV1.BACKGROUND_MEDIA ->
                                error("Unsupported typography asset action: ${definition.action}")
                        }
                    },
                )
            }
        }
    }

    if (section == ThemeSettingsBasicSection.COLORS_AND_MODE) {
        colorPickerRequest?.let { request ->
            key(request.definition.target) {
                ColorPickerDialog(
                    initialColor = request.initialColor,
                    title = request.definition.target.pickerTitle.localizedText(),
                    recentColors = recentColors,
                    onColorSelected = { color ->
                        editorSession.setInt(request.definition.target.field, color)
                        shared.scope.launch { editorSession.addRecentColor(color) }
                    },
                    onDismiss = { colorPickerRequest = null },
                )
            }
        }
    }
}

@Composable
private fun rememberGlobalFontPicker(
    context: Context,
    shared: ThemeSettingsShared,
): () -> Unit {
    val editorSession = shared.editorSession
    val fontPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                shared.scope.launch {
                    val operationGeneration = editorSession.beginAssetOperation() ?: return@launch
                    val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                    if (extension != null && (extension == "ttf" || extension == "otf" || extension == "ttc")) {
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, uri, "custom_font")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Font file saved to: $internalUri")
                            val internalUriString = internalUri.toString()
                            if (!editorSession.registerStagedAsset(internalUriString, operationGeneration)) {
                                return@launch
                            }
                            editorSession.update { current ->
                                current
                                    .withString(
                                        NativeThemeAssetActionV1.APP_FONT.field,
                                        internalUriString,
                                    )
                                    .withString(
                                        NativeThemePreferenceSchemaV1.fontType,
                                        NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
                                    )
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.font_file_saved, extension),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.font_file_save_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.unsupported_font_format),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    return { fontPickerLauncher.launch("*/*") }
}
