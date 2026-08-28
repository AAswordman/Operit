package com.ai.assistance.operit.ui.features.settings.screens.theme

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
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeColorControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorDefinitionV1
import kotlinx.coroutines.launch

private data class NativeThemeColorPickerRequestV1(
    val definition: NativeThemeColorControlDefinitionV1,
    val initialColor: Int,
)

@Composable
internal fun ThemeSettingsInterfaceTab(
    shared: ThemeSettingsShared,
) {
    val editorSession = shared.editorSession
    val editorDocument by editorSession.document.collectAsState()
    val values = editorDocument.draft
    val recentColors by editorSession.recentColorsFlow.collectAsState(initial = emptyList())
    var colorPickerRequest by remember {
        mutableStateOf<NativeThemeColorPickerRequestV1?>(null)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        NativeThemeEditorDefinitionV1.appChrome.groups.forEachIndexed { index, group ->
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
