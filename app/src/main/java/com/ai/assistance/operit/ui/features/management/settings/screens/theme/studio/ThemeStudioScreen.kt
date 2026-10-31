package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.ActionSheetItem
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchRow
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import kotlinx.coroutines.launch

/** 入口只打开所选角色的制作工程，正式持久化仍经过主题页保存。 */
@Composable
internal fun ThemeStudioEntry(session: ThemeEditorSession, projectKey: String, targetName: String) {
    var opened by remember(session) { mutableStateOf(false) }
    val values by session.values.collectAsState()
    ThemeWorkbenchCard(stringResource(R.string.studio_title), icon = Icons.Default.Palette) {
        ThemeWorkbenchRow(
            title = stringResource(R.string.studio_create_theme),
            subtitle = stringResource(R.string.studio_entry_description),
            onClick = { opened = true },
        )
        if (values.string(ThemePreferenceValues.SCRIPT_URI_KEY) != null) {
            TextButton(onClick = { session.setOptionalString(ThemePreferenceValues.SCRIPT_URI_KEY, null) }) {
                Text(stringResource(R.string.theme_script_remove))
            }
        }
    }
    if (opened) ThemeStudioScreen(session, projectKey, targetName) { opened = false }
}

/** 原生全屏工作室，源码与资源使用完整工作区，设计区预览只在固定高度内展开。 */
@Composable
private fun ThemeStudioScreen(session: ThemeEditorSession, projectKey: String, targetName: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val state = remember(session, projectKey) { ThemeStudioState(context, session, projectKey, targetName) }
    val snapshot = LocalThemePreferenceSnapshot.current
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var expandedPreview by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    fun close() { if (!state.busy) { if (state.hasChanges) leaving = true else onClose() } }
    LaunchedEffect(state) { state.load() }
    val importAsset = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { state.importResource(uri) }
    }
    val importSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { state.importSource(uri) }
    }
    val importProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { state.importProject(uri) }
    }
    val exportProject = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch { if (state.exportProject(uri, snapshot)) notice = R.string.studio_exported }
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        StudioTheme {
            Box(Modifier.fillMaxSize().background(StudioBackground).systemBarsPadding().imePadding()) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::close, enabled = !state.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.studio_close)) }
                        Column(Modifier.weight(1f)) {
                            Text(state.project?.manifest?.studio?.name?.ifBlank { targetName } ?: targetName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.studio_title), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = state::undo, enabled = !state.busy && state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.studio_undo)) }
                        IconButton(onClick = state::redo, enabled = !state.busy && state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.studio_redo)) }
                        Box {
                            IconButton(onClick = { more = true }, enabled = !state.busy && state.project != null) { Icon(Icons.Default.MoreVert, stringResource(R.string.studio_project_actions)) }
                            WorkbenchDropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                ActionSheetItem(label = stringResource(R.string.studio_project_info), icon = Icons.Default.Settings, onClick = { more = false; info = true })
                                ActionSheetItem(label = stringResource(R.string.studio_save_draft), icon = Icons.Default.Save, onClick = { more = false; scope.launch { if (state.saveProjectDraft()) notice = R.string.studio_draft_saved } })
                                ActionSheetItem(label = stringResource(R.string.studio_import_project), icon = Icons.Default.Upload, onClick = { more = false; if (!state.sourceDirty || state.synchronizeSource()) importProject.launch(arrayOf("*/*")) })
                                ActionSheetItem(label = stringResource(R.string.studio_export_project), icon = Icons.Default.Download, onClick = {
                                    more = false
                                    if (!state.sourceDirty || state.synchronizeSource()) exportProject.launch("theme.oktheme")
                                })
                                HorizontalDivider()
                                ActionSheetItem(label = stringResource(R.string.theme_appearance_forest), icon = Icons.Default.Palette, onClick = { more = false; preset = "forest" })
                                ActionSheetItem(label = stringResource(R.string.theme_appearance_paper), icon = Icons.Default.Palette, onClick = { more = false; preset = "paper" })
                            }
                        }
                    }
                    HorizontalDivider(color = StudioLine)
                    if (state.project != null) {
                        TabRow(selectedTabIndex = state.workspace.ordinal, containerColor = StudioBackground) {
                            StudioWorkspace.entries.forEach { workspace -> Tab(selected = workspace == state.workspace, onClick = { state.enterWorkspace(workspace) }, text = { Text(stringResource(workspace.label)) }) }
                        }
                        state.error?.let { message ->
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = state::clearError) { Icon(Icons.Default.Close, stringResource(R.string.studio_close), tint = MaterialTheme.colorScheme.onErrorContainer) }
                            }
                        }
                        notice?.let { label ->
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = StudioAccent)
                                IconButton(onClick = { notice = null }) { Icon(Icons.Default.Close, stringResource(R.string.studio_close)) }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (state.workspace) {
                                StudioWorkspace.Design -> StudioDesign(state, onPickTarget = { picker = true }, onFullPreview = { expandedPreview = true })
                                StudioWorkspace.Source -> StudioSource(state, onImport = { importSource.launch(arrayOf("*/*")) })
                                StudioWorkspace.Resources -> StudioResources(state, onImport = { importAsset.launch(arrayOf("*/*")) })
                            }
                        }
                        HorizontalDivider(color = StudioLine)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(targetName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.studio_role_draft_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { scope.launch { if (state.saveProjectDraft()) notice = R.string.studio_draft_saved } }, enabled = !state.busy) { Icon(Icons.Default.Save, stringResource(R.string.studio_save_draft)) }
                            Button(onClick = { scope.launch { if (state.applyToRole(snapshot)) onClose() } }, enabled = !state.busy) { Text(stringResource(R.string.studio_apply_to_role)) }
                        }
                    } else if (!state.busy) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            Button(onClick = { scope.launch { state.load() } }) { Text(stringResource(R.string.studio_retry)) }
                        }
                    }
                }
                if (state.busy) Box(Modifier.matchParentSize().background(StudioBackground.copy(alpha = .7f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            if (picker) StudioTargetPicker(state) { picker = false }
            if (info) StudioProjectInfo(state) { info = false }
            if (expandedPreview) StudioFullSheet(stringResource(R.string.studio_preview), { expandedPreview = false }) { StudioPreview(state, Modifier.fillMaxWidth().weight(1f)) }
            if (leaving) AlertDialog(
                onDismissRequest = { leaving = false },
                title = { Text(stringResource(R.string.studio_unsaved_title)) },
                text = { Column { Text(stringResource(R.string.studio_unsaved_message)); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
                confirmButton = { TextButton(onClick = { scope.launch { if (state.saveProjectDraft()) onClose() } }, enabled = !state.busy) { Text(stringResource(R.string.studio_save_and_close)) } },
                dismissButton = { Row { TextButton(onClick = onClose, enabled = !state.busy) { Text(stringResource(R.string.studio_discard)) }; TextButton(onClick = { leaving = false }) { Text(stringResource(R.string.studio_cancel)) } } },
            )
            preset?.let { id -> AlertDialog(
                onDismissRequest = { preset = null }, title = { Text(stringResource(R.string.studio_load_preset)) },
                text = { Text(stringResource(R.string.studio_load_preset_hint)) },
                confirmButton = { TextButton(onClick = { preset = null; scope.launch { state.loadPreset(id) } }) { Text(stringResource(R.string.studio_continue)) } },
                dismissButton = { TextButton(onClick = { preset = null }) { Text(stringResource(R.string.studio_cancel)) } },
            ) }
        }
    }
}

@Composable
private fun StudioDesign(state: ThemeStudioState, onPickTarget: () -> Unit, onFullPreview: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val previewHeight = (maxHeight * .44f).coerceIn(260.dp, 340.dp)
            .coerceAtMost((maxHeight - 160.dp).coerceAtLeast(0.dp))
        Column(Modifier.fillMaxSize()) {
            StudioPreviewToolbar(state, onFullPreview)
            if (!state.previewCollapsed) StudioPreview(state, Modifier.fillMaxWidth().height(previewHeight))
            Row(Modifier.fillMaxWidth().clickable(onClick = onPickTarget).background(StudioPanel).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioTargetThumbnail(state, state.target)
                Column(Modifier.weight(1f)) { Text(state.target.label(), style = MaterialTheme.typography.titleSmall); Text(stringResource(R.string.studio_select_component), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.Default.ChevronRight, null)
            }
            val properties = state.target.properties
            if (properties.size > 1) TabRow(selectedTabIndex = properties.indexOf(state.property), containerColor = StudioBackground) {
                properties.forEach { property -> Tab(selected = state.property == property, onClick = { state.property = property; if (property != StudioProperty.States) state.previewState = "always" }, text = { Text(stringResource(property.label)) }) }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) { key(state.target.id, state.property, state.mode) { StudioProperties(state) } }
        }
    }
}

@Composable
private fun StudioTargetPicker(state: ThemeStudioState, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<StudioGroup?>(null) }
    val labels = ThemeStudioTargets.associateWith { it.label() }
    val matches = ThemeStudioTargets.filter { query.isBlank() || labels.getValue(it).contains(query, true) || it.id.contains(query, true) }
    StudioFullSheet(stringResource(R.string.studio_select_component), onClose) {
        OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(stringResource(R.string.studio_search_components)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(88.dp).fillMaxHeight().background(StudioPanel).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { selectedGroup = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.studio_all), color = if (selectedGroup == null) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant) }
                StudioGroup.entries.forEach { group ->
                    TextButton(onClick = { selectedGroup = group }, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) { Text(stringResource(group.label), style = MaterialTheme.typography.labelSmall, color = if (selectedGroup == group) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
            StudioGroup.entries.forEach { group ->
                val targets = matches.filter { it.group == group && (selectedGroup == null || selectedGroup == group || query.isNotBlank()) }
                if (targets.isNotEmpty()) {
                    item(key = "group:$group") { Text(stringResource(group.label), Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.labelLarge, color = StudioAccent) }
                    items(targets, key = { it.id }) { target ->
                        Row(Modifier.fillMaxWidth().background(if (state.target == target) StudioPanel else StudioBackground).clickable { state.select(target); onClose() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StudioTargetThumbnail(state, target); Text(labels.getValue(target), Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null)
                        }
                        HorizontalDivider(color = StudioLine)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun StudioProjectInfo(state: ThemeStudioState, onClose: () -> Unit) {
    var info by remember { mutableStateOf(state.bundle.manifest.studio) }
    StudioFullSheet(stringResource(R.string.studio_project_info), onClose) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(info.name, { info = info.copy(name = it) }, label = { Text(stringResource(R.string.studio_project_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(info.id, { info = info.copy(id = it) }, label = { Text(stringResource(R.string.studio_project_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(info.author, { info = info.copy(author = it) }, label = { Text(stringResource(R.string.studio_author)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(info.version, { info = info.copy(version = it) }, label = { Text(stringResource(R.string.studio_version)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(info.description, { info = info.copy(description = it) }, label = { Text(stringResource(R.string.studio_description)) }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(info.license, { info = info.copy(license = it) }, label = { Text(stringResource(R.string.studio_license)) }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
            item { StudioHint(stringResource(R.string.studio_info_hint)) }
        }
        state.error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        Button(onClick = { if (state.metadata(info)) onClose() }, modifier = Modifier.fillMaxWidth().padding(14.dp)) { Text(stringResource(R.string.studio_save)) }
    }
}
