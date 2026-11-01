package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException

private sealed interface GalleryLoadState {
    data object Loading : GalleryLoadState
    data object Failed : GalleryLoadState
    data class Ready(val images: List<GalleryImage>) : GalleryLoadState
}

private data class GalleryAlbum(val id: String, val name: String, val count: Int)

/** 应用内媒体库选择器；仅确认后提交 URI，取消或关闭预览均不添加附件。 */
@Composable
fun ChatImagePickerDialog(
    onDismiss: () -> Unit,
    onImagesSelected: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }
    var access by remember { mutableStateOf(imageLibraryAccess(context)) }
    var refreshVersion by remember { mutableStateOf(0) }
    var loadState by remember { mutableStateOf<GalleryLoadState>(GalleryLoadState.Loading) }
    var selectedUris by rememberSaveable { mutableStateOf(listOf<String>()) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumName by rememberSaveable { mutableStateOf<String?>(null) }
    var albumMenuExpanded by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<GalleryImage?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = imageLibraryAccess(context)
        refreshVersion += 1
    }
    val requestAccess = { permissionLauncher.launch(imageLibraryPermissions()) }
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    // 从系统设置或权限界面返回时重查授权和图片，撤销授权的 URI 不再保留在选择中。
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = imageLibraryAccess(context)
                refreshVersion += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(access, refreshVersion) {
        if (access == ImageLibraryAccess.NONE) {
            selectedUris = emptyList()
            previewImage = null
            return@LaunchedEffect
        }
        loadState = GalleryLoadState.Loading
        try {
            val images = loadGalleryImages(context)
            val accessibleUris = images.mapTo(hashSetOf()) { it.uri.toString() }
            selectedUris = selectedUris.filter { it in accessibleUris }
            val currentPreview = previewImage
            if (currentPreview != null && currentPreview.uri.toString() !in accessibleUris) previewImage = null
            loadState = GalleryLoadState.Ready(images)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("ChatImagePicker", "读取媒体库图片失败", e)
            access = imageLibraryAccess(context)
            loadState = GalleryLoadState.Failed
        }
    }
    val images = (loadState as? GalleryLoadState.Ready)?.images.orEmpty()
    val albums = remember(images) {
        images.groupBy { it.albumId }.mapNotNull { (id, entries) ->
            val name = entries.first().albumName
            if (id == null || name.isNullOrBlank()) null else GalleryAlbum(id, name, entries.size)
        }.sortedBy { it.name.lowercase() }
    }
    val visibleImages = remember(images, selectedAlbumId) {
        if (selectedAlbumId == null) images else images.filter { it.albumId == selectedAlbumId }
    }
    val selectedSet = remember(selectedUris) { selectedUris.toHashSet() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(selectedAlbumId) { gridState.scrollToItem(0) }
    val dragSelection = galleryDragSelectionModifier(
        gridState = gridState,
        visibleUris = remember(visibleImages) { visibleImages.map { it.uri.toString() } },
        selectedUris = selectedUris,
        onSelectionChanged = { selectedUris = it },
    )
    val selectedPreview = remember(images, selectedUris) {
        val firstSelected = selectedUris.firstOrNull()
        images.firstOrNull { it.uri.toString() == firstSelected }
    }
    val toggleSelection: (GalleryImage) -> Unit = { image ->
        val uri = image.uri.toString()
        selectedUris = if (uri in selectedUris) selectedUris - uri else selectedUris + uri
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.chat_image_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                }
                if (access == ImageLibraryAccess.NONE) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.chat_image_picker_permission_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.chat_image_picker_permission_message))
                        Button(onClick = requestAccess) { Text(stringResource(R.string.chat_image_picker_grant_access)) }
                        TextButton(onClick = openSettings) { Text(stringResource(R.string.open_settings)) }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick = { albumMenuExpanded = true }) {
                                Text(
                                    selectedAlbumName ?: stringResource(R.string.chat_image_picker_all_photos),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(
                                expanded = albumMenuExpanded,
                                onDismissRequest = { albumMenuExpanded = false },
                                modifier = Modifier.heightIn(max = 360.dp),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_image_picker_all_photos)) },
                                    onClick = { selectedAlbumId = null; selectedAlbumName = null; albumMenuExpanded = false },
                                )
                                albums.forEach { album ->
                                    DropdownMenuItem(
                                        text = { Text("${album.name} (${album.count})") },
                                        onClick = { selectedAlbumId = album.id; selectedAlbumName = album.name; albumMenuExpanded = false },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { refreshVersion += 1 }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.chat_image_picker_refresh))
                        }
                    }
                    if (access == ImageLibraryAccess.SELECTED) {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.chat_image_picker_partial_access), modifier = Modifier.weight(1f))
                            TextButton(onClick = requestAccess) { Text(stringResource(R.string.chat_image_picker_manage_access)) }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when (loadState) {
                            GalleryLoadState.Loading -> CircularProgressIndicator()
                            GalleryLoadState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.chat_image_picker_load_failed), modifier = Modifier.padding(16.dp))
                                TextButton(onClick = { refreshVersion += 1 }) { Text(stringResource(R.string.chat_image_picker_refresh)) }
                                TextButton(onClick = openSettings) { Text(stringResource(R.string.open_settings)) }
                            }
                            is GalleryLoadState.Ready -> if (visibleImages.isEmpty()) {
                                Text(stringResource(R.string.chat_image_picker_empty))
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(104.dp),
                                    state = gridState,
                                    contentPadding = PaddingValues(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxSize().then(dragSelection),
                                ) {
                                    items(visibleImages, key = { it.uri.toString() }) { image ->
                                        GalleryImageCell(
                                            image = image,
                                            selected = image.uri.toString() in selectedSet,
                                            onToggle = { toggleSelection(image) },
                                            onPreview = { previewImage = image },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            enabled = selectedPreview != null && loadState is GalleryLoadState.Ready,
                            onClick = { previewImage = selectedPreview },
                        ) { Text(stringResource(R.string.image_preview)) }
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = selectedUris.isNotEmpty() && loadState is GalleryLoadState.Ready,
                            onClick = { onImagesSelected(selectedUris.map(Uri::parse)) },
                        ) { Text(stringResource(R.string.chat_image_picker_add_selected, selectedUris.size)) }
                    }
                }
            }
        }
    }
    previewImage?.let { image ->
        AttachmentImagePreviewDialog(image.uri, image.displayName, onDismiss = { previewImage = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryImageCell(
    image: GalleryImage,
    selected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    val context = LocalContext.current
    val request = remember(context, image.uri) {
        ImageRequest.Builder(context).data(image.uri).size(320).build()
    }
    Box(
        Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onToggle, onLongClick = onPreview, onLongClickLabel = stringResource(R.string.image_preview)),
    ) {
        AsyncImage(request, image.displayName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)))
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            // 减淡圆形衬底，保留勾选对比度并让图片细节透出来。
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.08f)),
            colors = CheckboxDefaults.colors(uncheckedColor = Color.White),
        )
    }
}