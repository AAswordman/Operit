package com.ai.assistance.operit.ui.features.chat.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager as ToolPackageManager
import com.ai.assistance.operit.data.skill.SkillRepository
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

/** 简约风格的附件选择器组件 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentSelectorPanel(
        visible: Boolean,
        onAttachImage: (String) -> Unit,
        onAttachFile: (String) -> Unit,
        onAttachScreenContent: () -> Unit,
        onAttachNotifications: () -> Unit = {},
        onAttachLocation: () -> Unit = {},
        onAttachMemory: () -> Unit = {},
        onAttachPackage: (String) -> Unit = {},
        onTakePhoto: (Uri) -> Unit,
        userQuery: String = "",
        onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val launchCameraCapture = rememberCameraCaptureLauncher(
        onTakePhoto = onTakePhoto,
        onDismiss = onDismiss,
    )

    var showPackageDialog by remember { mutableStateOf(false) }

    // 文件/图片选择器启动器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                uris.forEach { uri ->
                    getAttachmentSource(uri)?.let { path ->
                        onAttachImage(path)
                    }
                }
                onDismiss()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                uris.forEach { uri ->
                    getAttachmentSource(uri)?.let { path ->
                        onAttachFile(path)
                    }
                }
                onDismiss()
            }
        }
    }

    // 附件选择面板 - 使用展开动画，从下方向上展开
    AnimatedVisibility(
            visible = visible,
            enter =
                    expandVertically(
                            animationSpec = tween(200),
                            expandFrom = androidx.compose.ui.Alignment.Bottom
                    ) + fadeIn(),
            exit =
                    shrinkVertically(
                            animationSpec = tween(200),
                            shrinkTowards = androidx.compose.ui.Alignment.Bottom
                    ) + fadeOut()
    ) {
        Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                // 顶部指示器
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HorizontalDivider(
                            modifier =
                                    Modifier.width(32.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp)),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val panelItems =
                        listOf(
                                AttachmentPanelItem(
                                        icon = Icons.Default.Image,
                                        label = context.getString(R.string.attachment_photo),
                                        onClick = { imagePickerLauncher.launch("image/*") }
                                ),
                                AttachmentPanelItem(
                                        icon = Icons.Default.PhotoCamera,
                                        label = context.getString(R.string.attachment_camera),
                                        onClick = launchCameraCapture
                                ),
                                AttachmentPanelItem(
                                        icon = Icons.Default.Memory,
                                        label = context.getString(R.string.attachment_memory),
                                        onClick = {
                                            onAttachMemory()
                                            onDismiss()
                                        }
                                ),
                                AttachmentPanelItem(
                                        icon = Icons.Default.Description,
                                        label = context.getString(R.string.attachment_file),
                                        onClick = { filePickerLauncher.launch("*/*") }
                                ),
                                AttachmentPanelItem(
                                        icon = Icons.Default.ScreenshotMonitor,
                                        label = context.getString(R.string.attachment_screen_content),
                                        onClick = {
                                            onAttachScreenContent()
                                            onDismiss()
                                        }
                                ),
                                AttachmentPanelItem(
                                        icon = Icons.Default.Notifications,
                                        label = context.getString(R.string.attachment_notifications),
                                        onClick = {
                                            onAttachNotifications()
                                            onDismiss()
                                        }
                                ),
                                AttachmentPanelItem(
                                        icon = Icons.Default.LocationOn,
                                        label = context.getString(R.string.attachment_location),
                                        onClick = {
                                            onAttachLocation()
                                            onDismiss()
                                        }
                                ),
                                AttachmentPanelItem(
                                        icon = Icons.Default.AutoAwesome,
                                        label = context.getString(R.string.attachment_package),
                                        onClick = { showPackageDialog = true }
                                )
                        )

                val pages = panelItems.chunked(8).ifEmpty { listOf(emptyList()) }
                val pagerState = rememberPagerState(pageCount = { pages.size })

                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { pageIndex ->
                    val pageItems = pages[pageIndex]
                    val paddedItems = pageItems + List(8 - pageItems.size) { null }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 第一行选项
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                                .heightIn(min = 96.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            paddedItems.take(4).forEach { item ->
                                if (item == null) {
                                    AttachmentOptionPlaceholder()
                                } else {
                                    AttachmentOption(
                                            icon = item.icon,
                                            label = item.label,
                                            onClick = item.onClick
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 第二行选项
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                                .heightIn(min = 96.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            paddedItems.drop(4).take(4).forEach { item ->
                                if (item == null) {
                                    AttachmentOptionPlaceholder()
                                } else {
                                    AttachmentOption(
                                            icon = item.icon,
                                            label = item.label,
                                            onClick = item.onClick
                                    )
                                }
                            }
                        }
                    }
                }

                if (pages.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pages.size) { index ->
                            val selected = index == pagerState.currentPage
                            Box(
                                    modifier =
                                            Modifier.padding(horizontal = 4.dp)
                                                    .size(if (selected) 7.dp else 6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                            if (selected)
                                                                MaterialTheme.colorScheme.primary
                                                            else
                                                                MaterialTheme.colorScheme.onSurface.copy(
                                                                        alpha = 0.2f
                                                                )
                                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    // 包选择对话框
    PackageSelectorDialog(
        visible = showPackageDialog,
        onDismiss = { showPackageDialog = false },
        onPackageSelected = { packageName ->
            onAttachPackage(packageName)
            showPackageDialog = false
            onDismiss()
        }
    )
}
@Composable
fun AttachmentSelectorPopupPanel(
        visible: Boolean,
        onAttachImage: (String) -> Unit,
        onAttachFile: (String) -> Unit,
        onAttachScreenContent: () -> Unit,
        onAttachNotifications: () -> Unit = {},
        onAttachLocation: () -> Unit = {},
        onAttachMemory: () -> Unit = {},
        onAttachPackage: (String) -> Unit = {},
        onTakePhoto: (Uri) -> Unit,
        onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val launchCameraCapture = rememberCameraCaptureLauncher(
            onTakePhoto = onTakePhoto,
            onDismiss = onDismiss,
    )

    var showPackageDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                uris.forEach { uri ->
                    getAttachmentSource(uri)?.let { path ->
                        onAttachImage(path)
                    }
                }
                onDismiss()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                uris.forEach { uri ->
                    getAttachmentSource(uri)?.let { path ->
                        onAttachFile(path)
                    }
                }
                onDismiss()
            }
        }
    }

    val panelItems =
            listOf(
                    AttachmentPanelItem(
                            icon = Icons.Default.Image,
                            label = context.getString(R.string.attachment_photo),
                            onClick = { imagePickerLauncher.launch("image/*") }
                    ),
                    AttachmentPanelItem(
                            icon = Icons.Default.PhotoCamera,
                            label = context.getString(R.string.attachment_camera),
                            onClick = launchCameraCapture
                    ),
                    AttachmentPanelItem(
                            icon = Icons.Default.Memory,
                            label = context.getString(R.string.attachment_memory),
                            onClick = {
                                onAttachMemory()
                                onDismiss()
                            }
                    ),
                    AttachmentPanelItem(
                            icon = Icons.Default.Description,
                            label = context.getString(R.string.attachment_file),
                            onClick = { filePickerLauncher.launch("*/*") }
                    ),
                    AttachmentPanelItem(
                            icon = Icons.Default.ScreenshotMonitor,
                            label = context.getString(R.string.attachment_screen_content),
                            onClick = {
                                onAttachScreenContent()
                                onDismiss()
                            }
                    ),
                    AttachmentPanelItem(
                            icon = Icons.Default.Notifications,
                            label = context.getString(R.string.attachment_notifications),
                            onClick = {
                                onAttachNotifications()
                                onDismiss()
                            }
                    ),
                    AttachmentPanelItem(
                            icon = Icons.Default.LocationOn,
                            label = context.getString(R.string.attachment_location),
                            onClick = {
                                onAttachLocation()
                                onDismiss()
                            }
                    ),
                    AttachmentPanelItem(
                            icon = Icons.Default.AutoAwesome,
                            label = context.getString(R.string.attachment_package),
                            onClick = { showPackageDialog = true }
                    )
            )

    Popup(
            alignment = Alignment.TopStart,
            onDismissRequest = onDismiss,
            properties =
                    PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = false
                    )
    ) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onDismiss
                                ),
                contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier =
                            Modifier.padding(bottom = 44.dp, end = 12.dp)
                                    .width(200.dp)
                                    .heightIn(max = 420.dp)
                                    .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {}
                                    )
            ) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .verticalScroll(rememberScrollState())
                ) {
                    panelItems.forEach { item ->
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(36.dp)
                                                .clickable(onClick = item.onClick)
                                                .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // 包选择对话框
    PackageSelectorDialog(
        visible = showPackageDialog,
        onDismiss = { showPackageDialog = false },
        onPackageSelected = { packageName ->
            onAttachPackage(packageName)
            showPackageDialog = false
            onDismiss()
        }
    )
}

private data class AttachmentPanelItem(
        val icon: ImageVector,
        val label: String,
        val onClick: () -> Unit
)

private enum class AttachmentPackageKind {
    PACKAGE,
    SKILL,
    MCP
}

private data class AttachmentPackageOption(
        val packageName: String,
        val title: String,
        val description: String,
        val kind: AttachmentPackageKind
)

@Composable
private fun rememberCameraCaptureLauncher(
        onTakePhoto: (Uri) -> Unit,
        onDismiss: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val latestOnTakePhoto by rememberUpdatedState(onTakePhoto)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
                val capturedUri = tempCameraUri
                tempCameraUri = null
                if (success && capturedUri != null) {
                    latestOnTakePhoto(capturedUri)
                    latestOnDismiss()
                }
            }

    fun launchCameraCapture() {
        val uri = createTempCameraUri(context)
        tempCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    val requestCameraPermissionLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    launchCameraCapture()
                } else {
                    Toast.makeText(
                                    context,
                                    context.getString(R.string.camera_permission_denied_toast),
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }

    return {
        val hasCameraPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            launchCameraCapture()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

private fun createTempCameraUri(context: Context): Uri {
    val authority = "${context.applicationContext.packageName}.fileprovider"
    val tmpFile =
            File.createTempFile("temp_image_", ".jpg", context.cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }
    return FileProvider.getUriForFile(context, authority, tmpFile)
}

private fun getAttachmentSource(uri: Uri): String? {
    return when (uri.scheme) {
        "file" -> uri.path
        "content" -> uri.toString()
        else -> null
    }
}

/** 简约的附件选项组件 */
@Composable
private fun AttachmentOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                    Modifier.clickable(onClick = onClick)
                            .width(70.dp)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // 图标区域 - 改为圆角方形
        Box(
                modifier =
                        Modifier.size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(
                                                alpha = 0.7f
                                        )
                                ),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 标签
        Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AttachmentOptionPlaceholder() {
    Spacer(modifier = Modifier.width(70.dp).padding(horizontal = 8.dp, vertical = 8.dp))
}

/** 包选择对话框 */
@Composable
fun PackageSelectorDialog(
        visible: Boolean,
        onDismiss: () -> Unit,
        onPackageSelected: (String) -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val toolHandler = remember { AIToolHandler.getInstance(context.applicationContext) }
    val packageManager = remember {
        ToolPackageManager.getInstance(context.applicationContext, toolHandler)
    }
    val skillRepository = remember { SkillRepository.getInstance(context.applicationContext) }
    var packageOptions by remember { mutableStateOf<List<AttachmentPackageOption>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredPackageOptions =
            remember(packageOptions, searchQuery) {
                val query = searchQuery.trim()
                if (query.isEmpty()) {
                    packageOptions
                } else {
                    packageOptions.filter { option ->
                        option.title.contains(query, ignoreCase = true) ||
                            option.packageName.contains(query, ignoreCase = true) ||
                            option.description.contains(query, ignoreCase = true)
                    }
                }
            }

    LaunchedEffect(visible) {
        if (visible) {
            searchQuery = ""
            packageOptions =
                buildAttachmentPackageOptions(
                    context = context.applicationContext,
                    packageManager = packageManager,
                    skillRepository = skillRepository
                )
        }
    }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                        text = context.getString(R.string.attachment_package_select_title),
                        style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                if (packageOptions.isEmpty()) {
                    Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                                text = context.getString(R.string.attachment_package_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                placeholder = {
                                    Text(context.getString(R.string.attachment_package_search_placeholder))
                                },
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = context.getString(R.string.search)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = context.getString(R.string.clear_search)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )

                        if (filteredPackageOptions.isEmpty()) {
                            Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                            ) {
                                Text(
                                        text = context.getString(R.string.attachment_package_search_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            filteredPackageOptions.forEach { option ->
                                Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Transparent,
                                        onClick = { onPackageSelected(option.packageName) }
                                ) {
                                    Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                    text = option.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                    text = buildAttachmentPackageSubtitle(option),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(context.getString(R.string.cancel))
                }
            }
    )
}

private fun buildAttachmentPackageOptions(
        context: Context,
        packageManager: ToolPackageManager,
        skillRepository: SkillRepository
): List<AttachmentPackageOption> {
    val options = linkedMapOf<String, AttachmentPackageOption>()

    packageManager.getAvailablePackages().toSortedMap().forEach { (packageName, toolPackage) ->
        if (packageManager.isToolPkgContainer(packageName)) {
            return@forEach
        }
        options.putIfAbsent(
                packageName,
                AttachmentPackageOption(
                        packageName = packageName,
                        title = toolPackage.displayName.resolve(context).ifBlank { packageName },
                        description = toolPackage.description.resolve(context),
                        kind = AttachmentPackageKind.PACKAGE
                )
        )
    }

    skillRepository.getAiVisibleSkillPackages().toSortedMap().forEach { (skillName, skillPackage) ->
        options.putIfAbsent(
                skillName,
                AttachmentPackageOption(
                        packageName = skillName,
                        title = skillName,
                        description = skillPackage.description,
                        kind = AttachmentPackageKind.SKILL
                )
        )
    }

    packageManager.getAvailableServerPackages().toSortedMap().forEach { (serverName, serverConfig) ->
        options.putIfAbsent(
                serverName,
                AttachmentPackageOption(
                        packageName = serverName,
                        title = serverConfig.name.ifBlank { serverName },
                        description = serverConfig.description,
                        kind = AttachmentPackageKind.MCP
                )
        )
    }

    return options.values.toList()
}

private fun buildAttachmentPackageSubtitle(option: AttachmentPackageOption): String {
    val typeLabel =
            when (option.kind) {
                AttachmentPackageKind.PACKAGE -> "包"
                AttachmentPackageKind.SKILL -> "技能"
                AttachmentPackageKind.MCP -> "MCP"
            }
    return if (option.description.isBlank()) {
        typeLabel
    } else {
        "$typeLabel · ${option.description}"
    }
}
