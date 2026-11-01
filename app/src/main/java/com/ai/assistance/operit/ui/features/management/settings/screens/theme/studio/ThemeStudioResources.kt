package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.ActionSheetItem
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu
import com.ai.assistance.operit.data.model.theme.forMode
import com.ai.assistance.operit.ui.theme.script.ThemeScriptImage

@Composable
internal fun StudioAssetThumbnail(image: ThemeScriptImage, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        when (image) {
            is ThemeScriptImage.Bitmap -> Image(image.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            is ThemeScriptImage.Vector -> Canvas(Modifier.fillMaxSize()) { drawContext.canvas.nativeCanvas.drawPicture(image.picture, android.graphics.RectF(0f, 0f, size.width, size.height)) }
            is ThemeScriptImage.Font -> Text("Aa 字", fontFamily = image.family, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun assetKind(state: ThemeStudioState, id: String, path: String): String {
    if (path.substringAfterLast('.').lowercase() in setOf("ttf", "otf")) return "font"
    val all = state.bundle.manifest.appearance
    // 资源分类需展开全部模式，避免漏掉仅在另一模式绑定的图标。
    val bound = (listOf(all.copy(modes = emptyMap())) + all.modes.keys.map(all::forMode)).any { a -> a.icons.values.any { it.asset == id } }
    return if (bound || path.split('/').any { it == "icons" || it == "图标" }) "icon" else "image"
}

@Composable
internal fun StudioResources(state: ThemeStudioState, onImport: () -> Unit) {
    var addMenu by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<String?>(null) }
    var folderMenu by remember { mutableStateOf<String?>(null) }
    var assetMenu by remember { mutableStateOf<String?>(null) }
    var moveAsset by remember { mutableStateOf<String?>(null) }
    val all = state.bundle.manifest.assets
    val query = state.resourceQuery.trim()
    val files = all.filter { (id, path) ->
        val scope = query.isNotEmpty() || state.resourceFilter != "all" || path.substringBeforeLast('/') == state.currentFolder
        scope && (state.resourceFilter == "all" || assetKind(state, id, path) == state.resourceFilter) && (query.isEmpty() || path.contains(query, ignoreCase = true) || id.contains(query, ignoreCase = true))
    }.toList().sortedBy { it.second }
    val folders = if (query.isEmpty() && state.resourceFilter == "all") state.folders.filter { it != "assets" && it.substringBeforeLast('/') == state.currentFolder } else emptyList()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(state.resourceQuery, { state.resourceQuery = it }, singleLine = true, placeholder = { Text(stringResource(R.string.studio_search_resources)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.weight(1f))
            IconButton(onClick = { state.resourceList = !state.resourceList }) { Icon(Icons.Default.List, stringResource(R.string.studio_switch_resource_view)) }
            Box {
                IconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, stringResource(R.string.studio_add_resource)) }
                WorkbenchDropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    ActionSheetItem(label = stringResource(R.string.studio_import_resource), icon = Icons.Default.Upload, onClick = { addMenu = false; onImport() })
                    ActionSheetItem(label = stringResource(R.string.studio_new_folder), icon = Icons.Default.Folder, onClick = { addMenu = false; prompt = "create"; state.clearError() })
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val segments = state.currentFolder.split('/')
            segments.forEachIndexed { index, name ->
                if (index > 0) Text("›", Modifier.align(Alignment.CenterVertically))
                TextButton(onClick = { state.currentFolder = segments.take(index + 1).joinToString("/"); state.resourceQuery = ""; state.resourceFilter = "all" }) { Text(if (index == 0) stringResource(R.string.studio_resources) else name) }
            }
        }
        ScrollableTabRow(selectedTabIndex = listOf("all", "image", "icon", "font").indexOf(state.resourceFilter), edgePadding = 12.dp, containerColor = StudioBackground) {
            listOf("all" to R.string.studio_all, "image" to R.string.studio_images, "icon" to R.string.studio_icons, "font" to R.string.studio_fonts).forEach { (kind, label) ->
                val count = all.count { (id, path) -> kind == "all" || assetKind(state, id, path) == kind }
                Tab(selected = state.resourceFilter == kind, onClick = { state.resourceFilter = kind }, text = { Text(stringResource(label) + " " + count) })
            }
        }
        if (folders.isEmpty() && files.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { StudioHint(stringResource(R.string.studio_empty_resources)) }
        } else if (state.resourceList) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                items(folders, key = { "folder:$it" }) { path ->
                    Row(Modifier.fillMaxWidth().clickable { state.currentFolder = path }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = StudioAccent); Text(path.substringAfterLast('/'), Modifier.weight(1f).padding(horizontal = 12.dp)); IconButton(onClick = { folderMenu = path }) { Icon(Icons.Default.MoreVert, null) } }
                    HorizontalDivider(color = StudioLine)
                }
                items(files, key = { it.first }) { (id, path) ->
                    Row(Modifier.fillMaxWidth().clickable { assetMenu = id }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        StudioAssetThumbnail(state.images.getValue(id), Modifier.size(45.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis); StudioHint(path) }
                        IconButton(onClick = { assetMenu = id }) { Icon(Icons.Default.MoreVert, null) }
                    }
                    HorizontalDivider(color = StudioLine)
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.weight(1f).fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(folders, key = { "folder:$it" }) { path ->
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.15f).border(1.dp, StudioLine, RoundedCornerShape(8.dp)).background(StudioPanel, RoundedCornerShape(8.dp)).clickable { state.currentFolder = path }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Folder, null, Modifier.size(42.dp), tint = StudioAccent) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(path.substringAfterLast('/'), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall); IconButton(onClick = { folderMenu = path }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.MoreVert, null, Modifier.size(17.dp)) } }
                    }
                }
                items(files, key = { it.first }) { (id, path) ->
                    Column(Modifier.clickable { assetMenu = id }) {
                        StudioAssetThumbnail(state.images.getValue(id), Modifier.fillMaxWidth().aspectRatio(1.15f))
                        Text(path.substringAfterLast('/'), Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        Text(path.substringBeforeLast('/').removePrefix("assets/"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        HorizontalDivider(color = StudioLine)
        Text(stringResource(R.string.studio_resource_count, files.size, all.size), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (prompt != null) StudioNameDialog(state, stringResource(if (prompt == "create") R.string.studio_new_folder else R.string.studio_rename_folder), if (prompt == "create") "" else requireNotNull(folderMenu).substringAfterLast('/'), onClose = { prompt = null; folderMenu = null }) { name ->
        if (prompt == "create") state.createFolder(name) else state.renameFolder(requireNotNull(folderMenu), name)
        if (state.error == null) { prompt = null; folderMenu = null }
    }
    folderMenu?.let { folder ->
        if (prompt == null) AlertDialog(onDismissRequest = { folderMenu = null }, title = { Text(folder.substringAfterLast('/')) }, text = { Column {
            TextButton(onClick = { state.clearError(); prompt = "rename" }) { Text(stringResource(R.string.studio_rename_folder)) }
            TextButton(onClick = { if (state.deleteFolder(folder)) folderMenu = null }) { Text(stringResource(R.string.studio_delete_empty_folder)) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = { folderMenu = null }) { Text(stringResource(R.string.studio_close)) } })
    }
    assetMenu?.let { id ->
        AlertDialog(onDismissRequest = { assetMenu = null }, title = { Text(all.getValue(id).substringAfterLast('/')) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioAssetThumbnail(state.images.getValue(id), Modifier.fillMaxWidth().height(140.dp))
            StudioHint(all.getValue(id)); StudioHint(id)
            TextButton(onClick = { assetMenu = null; moveAsset = id; state.clearError() }) { Text(stringResource(R.string.studio_move_rename)) }
            TextButton(onClick = { if (state.deleteAsset(id)) assetMenu = null }) { Text(stringResource(R.string.studio_delete)) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = { assetMenu = null }) { Text(stringResource(R.string.studio_close)) } })
    }
    moveAsset?.let { id -> StudioMoveResourceDialog(state, id, onClose = { moveAsset = null }) }
}

@Composable
internal fun StudioAssetPicker(state: ThemeStudioState, fontsOnly: Boolean, title: String, onClose: () -> Unit, allowed: (String) -> Boolean = { true }, onChoose: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val files = (if (fontsOnly) state.fontAssets else state.imageAssets).filter { (id, path) -> allowed(id) && (query.isBlank() || path.contains(query, true) || id.contains(query, true)) }.toList().sortedBy { it.second }
    StudioFullSheet(title, onClose) {
        OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(stringResource(R.string.studio_search_resources)) }, modifier = Modifier.fillMaxWidth().padding(12.dp))
        if (files.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { StudioHint(stringResource(R.string.studio_import_first)) }
        else LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.weight(1f).fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(files, key = { it.first }) { (id, path) -> Column(Modifier.clickable { onChoose(id) }) { StudioAssetThumbnail(state.images.getValue(id), Modifier.fillMaxWidth().aspectRatio(1.15f)); Text(path.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis); StudioHint(path.substringBeforeLast('/')) } }
        }
    }
}

@Composable
internal fun StudioNameDialog(state: ThemeStudioState, title: String, initial: String, onClose: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) }, text = { Column { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text(stringResource(R.string.studio_name)) }); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.studio_save)) } }, dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.studio_cancel)) } })
}

@Composable
private fun StudioMoveResourceDialog(state: ThemeStudioState, id: String, onClose: () -> Unit) {
    val path = state.bundle.manifest.assets.getValue(id)
    var filename by remember { mutableStateOf(path.substringAfterLast('/')) }
    var folder by remember { mutableStateOf(path.substringBeforeLast('/')) }
    StudioFullSheet(stringResource(R.string.studio_move_rename), onClose) {
        OutlinedTextField(filename, { filename = it }, label = { Text(stringResource(R.string.studio_filename)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
        StudioHint(stringResource(R.string.studio_choose_folder))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) { items(state.folders) { target ->
            Row(Modifier.fillMaxWidth().clickable { folder = target }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = folder == target, onClick = { folder = target }); Icon(Icons.Default.Folder, null, tint = StudioAccent); Text(target, Modifier.padding(start = 10.dp)) }
        } }
        state.error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        Button(onClick = { if (state.moveAsset(id, folder, filename)) onClose() }, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text(stringResource(R.string.studio_save)) }
    }
}
