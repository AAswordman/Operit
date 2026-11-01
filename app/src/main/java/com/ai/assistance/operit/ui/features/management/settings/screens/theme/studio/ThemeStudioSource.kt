package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio.source.rememberThemeSourceHighlighting
import com.ai.assistance.operit.ui.components.ActionSheetItem
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu

/** 三个文件共享一份工程；解析失败时保留原稿，成功同步后再修改可视属性。 */
@Composable
internal fun StudioSource(state: ThemeStudioState, onImport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var editor by remember(state.sourceFile) { mutableStateOf(TextFieldValue(state.sourceText)) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val syntaxHighlighting = rememberThemeSourceHighlighting(state.sourceFile.endsWith(".json"))
    val matches = remember(query, state.sourceText) {
        if (query.isEmpty()) emptyList() else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(state.sourceText).map { it.range }.toList()
    }
    LaunchedEffect(state.sourceText) {
        if (editor.text != state.sourceText) editor = TextFieldValue(state.sourceText, TextRange(editor.selection.start.coerceAtMost(state.sourceText.length)))
    }
    fun find(next: Boolean) {
        if (matches.isEmpty()) return
        val position = editor.selection
        val index = if (next) matches.indexOfFirst { it.first >= position.end } else matches.indexOfLast { it.last < position.start }
        val selected = if (index >= 0) matches[index] else if (next) matches.first() else matches.last()
        editor = editor.copy(selection = TextRange(selected.first, selected.last + 1))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { menu = true }) { Text(state.sourceFile, maxLines = 1); Icon(Icons.Default.ArrowDropDown, null) }
                WorkbenchDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf("theme.json", "manifest.json", state.bundle.manifest.entry).forEach { name ->
                        ActionSheetItem(label = name, icon = if (name.endsWith(".json")) Icons.Default.Description else Icons.Default.Code, selected = state.sourceFile == name, onClick = { state.selectFile(name); menu = false })
                    }
                }
            }
            TextButton(onClick = state::formatSource, enabled = state.sourceFile.endsWith(".json")) { Text(stringResource(R.string.studio_format)) }
            TextButton(onClick = { state.synchronizeSource() }, enabled = state.sourceDirty) { Text(stringResource(R.string.studio_sync)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.studio_find_source)) }, singleLine = true, modifier = Modifier.weight(1f))
            Text(matches.size.toString(), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = { find(false) }, enabled = matches.isNotEmpty()) { Icon(Icons.Default.ExpandLess, stringResource(R.string.studio_previous_match)) }
            IconButton(onClick = { find(true) }, enabled = matches.isNotEmpty()) { Icon(Icons.Default.ExpandMore, stringResource(R.string.studio_next_match)) }
        }
        HorizontalDivider(color = StudioLine)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(StudioBackground)) {
            val minimumWidth = maxWidth
            val textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            Row(Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal).widthIn(min = minimumWidth).padding(vertical = 12.dp)) {
                Text((1..(state.sourceText.count { it == '\n' } + 1)).joinToString("\n"), Modifier.widthIn(min = 40.dp).padding(horizontal = 8.dp), style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = editor,
                    onValueChange = { value -> editor = value; if (value.text != state.sourceText) state.setSource(value.text) },
                    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(StudioAccent),
                    visualTransformation = syntaxHighlighting,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.widthIn(min = (minimumWidth - 48.dp).coerceAtLeast(0.dp)).padding(end = 12.dp),
                )
            }
        }
        HorizontalDivider(color = StudioLine)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (state.sourceDirty) R.string.studio_source_pending else R.string.studio_source_synced), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onImport) { Text(stringResource(R.string.studio_import_script)) }
        }
    }
}
