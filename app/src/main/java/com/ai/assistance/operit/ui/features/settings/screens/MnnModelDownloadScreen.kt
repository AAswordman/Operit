package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mnn.DownloadState
import com.ai.assistance.operit.data.mnn.MnnModel
import com.ai.assistance.operit.data.mnn.MnnModelDownloadManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnnModelDownloadScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { MnnModelDownloadManager.getInstance(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    var modelList by remember { mutableStateOf<List<MnnModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 过滤后的模型列表
    val filteredModels = remember(modelList, searchQuery) {
        if (searchQuery.isBlank()) {
            modelList
        } else {
            modelList.filter { model ->
                model.modelName.contains(searchQuery, ignoreCase = true) ||
                model.description.contains(searchQuery, ignoreCase = true) ||
                model.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    // 加载模型列表
    val loadFailedMessage = stringResource(R.string.mnn_load_failed)
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        val result = downloadManager.fetchModelList()
        if (result.isSuccess) {
            modelList = result.getOrNull() ?: emptyList()
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: loadFailedMessage
        }
        isLoading = false
    }
    
    CustomScaffold() { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    // 加载中
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.mnn_loading_models))
                    }
                }
                errorMessage != null -> {
                    // 加载失败
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.mnn_load_failed),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    val result = downloadManager.fetchModelList()
                                    if (result.isSuccess) {
                                        modelList = result.getOrNull() ?: emptyList()
                                    } else {
                                        errorMessage = result.exceptionOrNull()?.message ?: loadFailedMessage
                                    }
                                    isLoading = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.mnn_retry))
                        }
                    }
                }
                modelList.isEmpty() -> {
                    // 模型列表为空
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.mnn_no_models_downloaded),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                else -> {
                    // 显示模型列表
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 搜索栏
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                placeholder = { Text(stringResource(R.string.mnn_search_models)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = null)
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { keyboardController?.hide() }
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        
                        // 模型列表
                        if (filteredModels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.mnn_no_search_results),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredModels, key = { it.modelName }) { model ->
                                    ModelCard(
                                        model = model,
                                        downloadManager = downloadManager,
                                        onDelete = { showDeleteDialog = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
    showDeleteDialog?.let { modelName ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.mnn_delete_model)) },
            text = { Text(stringResource(R.string.mnn_confirm_delete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            downloadManager.deleteModel(modelName)
                            showDeleteDialog = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ModelCard(
    model: MnnModel,
    downloadManager: MnnModelDownloadManager,
    onDelete: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val downloadState by downloadManager.getDownloadState(model.modelName).collectAsState()

    val canDelete = !downloadManager.isModelInUse(model.modelName) &&
        (downloadState !is DownloadState.Idle || downloadManager.isModelDownloaded(model.modelName))
    
    // 优先使用ModelScope源
    val downloadUrl = model.sources["ModelScope"] 
        ?: model.sources["HuggingFace"] 
        ?: model.sources.values.firstOrNull()
        ?: ""
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // 模型名称和标签行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                if (model.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        model.tags.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 模型大小
            Text(
                text = stringResource(R.string.mnn_model_size, model.size_gb),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 描述
            if (model.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 下载进度和按钮
            when (downloadState) {
                is DownloadState.Idle, is DownloadState.Completed -> {
                    if (downloadManager.isModelDownloaded(model.modelName)) {
                        // 已下载，显示删除按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.mnn_download_complete),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            IconButton(
                                onClick = { onDelete(model.modelName) },
                                modifier = Modifier.size(32.dp),
                                enabled = canDelete
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.mnn_delete_model),
                                    tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        // 未下载，显示下载按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        downloadManager.downloadModel(model.modelName, downloadUrl)
                                    }
                                },
                                modifier = Modifier.height(32.dp),
                                enabled = downloadUrl.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download, 
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.mnn_start_download),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }

                is DownloadState.Connecting -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.mnn_connecting),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onDelete(model.modelName) },
                            modifier = Modifier.size(32.dp),
                            enabled = canDelete
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.mnn_delete_model),
                                tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                
                is DownloadState.Downloading -> {
                    val state = downloadState as DownloadState.Downloading
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 进度条
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // 进度信息
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // 如果有多个文件，显示当前文件进度
                                if (state.totalFiles > 1) {
                                    Text(
                                        text = "${state.currentFileIndex}/${state.totalFiles} · ${state.currentFile}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${(state.progress * 100).toInt()}% · ${downloadManager.formatFileSize(state.downloadedBytes)} / ${downloadManager.formatFileSize(state.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    maxLines = 1
                                )
                                Text(
                                    text = state.speed,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { downloadManager.pauseDownload(model.modelName) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = stringResource(R.string.mnn_pause_download),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(model.modelName) },
                                    modifier = Modifier.size(32.dp),
                                    enabled = canDelete
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.mnn_delete_model),
                                        tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                is DownloadState.Paused -> {
                    val state = downloadState as DownloadState.Paused
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.mnn_download_progress,
                                    (state.progress * 100).toInt()
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 继续下载按钮
                                Button(
                                    onClick = {
                                        scope.launch {
                                            downloadManager.downloadModel(model.modelName, downloadUrl)
                                        }
                                    },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.mnn_resume_download),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                    )
                                }

                                // 删除按钮
                                IconButton(
                                    onClick = { onDelete(model.modelName) },
                                    modifier = Modifier.size(32.dp),
                                    enabled = canDelete
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.mnn_delete_model),
                                        tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                is DownloadState.Completed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.mnn_download_complete),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        IconButton(
                            onClick = { onDelete(model.modelName) },
                            modifier = Modifier.size(32.dp),
                            enabled = canDelete
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.mnn_delete_model),
                                tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                
                is DownloadState.Failed -> {
                    val state = downloadState as DownloadState.Failed
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.mnn_download_failed, state.error),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            downloadManager.downloadModel(model.modelName, downloadUrl)
                                        }
                                    },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.mnn_retry),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(model.modelName) },
                                    modifier = Modifier.size(32.dp),
                                    enabled = canDelete
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.mnn_delete_model),
                                        tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

