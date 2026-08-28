package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorDefinitionV1

@Composable
internal fun ThemeSettingsInputTab(
    editorSession: ThemeEditorSession,
) {
    val editorDocument by editorSession.document.collectAsState()
    val values = editorDocument.draft
    val visibleGroups =
        NativeThemeEditorDefinitionV1.composer.groups.filter { group -> group.isVisible(values) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        visibleGroups.forEachIndexed { index, group ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            NativeThemeEditorGroupV1(
                definition = group,
                values = values,
                editorSession = editorSession,
            )
        }
    }
}
