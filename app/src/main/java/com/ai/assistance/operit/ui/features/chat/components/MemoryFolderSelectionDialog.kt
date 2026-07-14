package com.ai.assistance.operit.ui.features.chat.components

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.memory.screens.FolderExpandedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer

/**
 * 文件夹树节点数据类
 */
data class FolderNode(
    val path: String,
    val name: String,
    val children: MutableList<FolderNode> = mutableListOf(),
    val level: Int = 0
)

/**
 * 记忆文件夹选择对话框
 * 允许用户选择一个或多个记忆文件夹用于附着到消息中
 */
@Composable
fun MemoryFolderSelectionDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    if (!visible) return
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 选中的文件夹路径
    var selectedFolders by remember { mutableStateOf(setOf<String>()) }
    
    // 使用 rememberLocal 持久化展开状态（与 FolderNavigator 共享同一个 key）
    var expandedFoldersState by rememberLocal(
        key = "folder_navigator_expanded_state",
        defaultValue = FolderExpandedState(),
        serializer = serializer()
    )
    
    // 所有可用的文件夹路径（扁平列表）
    var folderPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // 文件夹树结构
    var folderTree by remember { mutableStateOf<List<FolderNode>>(emptyList()) }
    
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 加载文件夹列表
    LaunchedEffect(visible) {
        if (visible) {
            isLoading = true
            errorMessage = null
            try {
                val folders = loadFolderPaths(context)
                folderPaths = folders
                // 构建树结构
                folderTree = buildFolderTree(context, folders)
                isLoading = false
            } catch (e: Exception) {
                AppLogger.e("MemoryFolderDialog", "Failed to load folders", e)
                errorMessage = context.getString(R.string.load_folder_failed, e.message)
                isLoading = false
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(context.getString(R.string.select_memory_folder))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                Text(
                    text = context.getString(R.string.select_memory_folder_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage ?: context.getString(R.string.unknown_error),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    folderPaths.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = context.getString(R.string.no_memory_folder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 递归渲染树结构
                            folderTree.forEach { rootNode ->
                                renderFolderTree(
                                    node = rootNode,
                                    selectedFolders = selectedFolders,
                                    expandedFolders = expandedFoldersState.expandedPaths,
                                    allFolderPaths = folderPaths,
                                    onToggleSelection = { path ->
                                        selectedFolders = if (path in selectedFolders) {
                                            // 取消选中：移除该文件夹及其所有子文件夹
                                            val toRemove = getSubfolders(path, folderPaths)
                                            selectedFolders - toRemove - path
                                        } else {
                                            // 选中：添加该文件夹及其所有子文件夹
                                            val toAdd = getSubfolders(path, folderPaths)
                                            selectedFolders + toAdd + path
                                        }
                                    },
                                    onToggleExpanded = { path ->
                                        // 持久化展开状态
                                        expandedFoldersState = if (path in expandedFoldersState.expandedPaths) {
                                            expandedFoldersState.copy(expandedPaths = expandedFoldersState.expandedPaths - path)
                                        } else {
                                            expandedFoldersState.copy(expandedPaths = expandedFoldersState.expandedPaths + path)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedFolders.toList())
                    onDismiss()
                },
                enabled = selectedFolders.isNotEmpty()
            ) {
                Text(context.getString(R.string.confirm_with_count, selectedFolders.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
}

/**
 * 递归渲染文件夹树
 */
private fun LazyListScope.renderFolderTree(
    node: FolderNode,
    selectedFolders: Set<String>,
    expandedFolders: Set<String>,
    allFolderPaths: List<String>,
    onToggleSelection: (String) -> Unit,
    onToggleExpanded: (String) -> Unit
) {
    // 渲染当前节点
    item(key = node.path) {
        FolderTreeItem(
            node = node,
            isSelected = node.path in selectedFolders,
            isExpanded = node.path in expandedFolders,
            hasChildren = node.children.isNotEmpty(),
            onToggleSelection = { onToggleSelection(node.path) },
            onToggleExpanded = { onToggleExpanded(node.path) }
        )
    }
    
    // 如果展开，递归渲染子节点
    if (node.path in expandedFolders && node.children.isNotEmpty()) {
        node.children.forEach { childNode ->
            renderFolderTree(
                node = childNode,
                selectedFolders = selectedFolders,
                expandedFolders = expandedFolders,
                allFolderPaths = allFolderPaths,
                onToggleSelection = onToggleSelection,
                onToggleExpanded = onToggleExpanded
            )
        }
    }
}

/**
 * 单个文件夹树项
 */
@Composable
private fun FolderTreeItem(
    node: FolderNode,
    isSelected: Boolean,
    isExpanded: Boolean,
    hasChildren: Boolean,
    onToggleSelection: () -> Unit,
    onToggleExpanded: () -> Unit
) {
    val context = LocalContext.current
    val indent = (node.level * 20).dp
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展开/折叠按钮
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpanded,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (isExpanded) context.getString(R.string.collapse) else context.getString(R.string.expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            // 占位符，保持对齐
            Spacer(modifier = Modifier.width(24.dp))
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // 复选框
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelection() },
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 文件夹图标
        Icon(
            imageVector = if (isExpanded && hasChildren) {
                Icons.Default.FolderOpen
            } else {
                Icons.Default.Folder
            },
            contentDescription = null,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 文件夹名称（可点击区域）
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .weight(1f)
                .clickable { onToggleSelection() }
                .padding(vertical = 8.dp)
        )
    }
}

/**
 * 构建文件夹树结构
 * 使用与 FolderNavigator 相同的逻辑，自动创建缺失的父节点
 */
private fun buildFolderTree(context: Context, folderPaths: List<String>): List<FolderNode> {
    val rootNodes = mutableListOf<FolderNode>()
    val nodeMap = mutableMapOf<String, FolderNode>()

    // 过滤掉空路径和"未分类"
    val uncategorized = context.getString(R.string.uncategorized)
    val validPaths = folderPaths.filter { it.isNotBlank() && it != uncategorized }

    // 遍历每个路径，自动创建所有中间节点
    validPaths.forEach { path ->
        val parts = path.split("/").filter { it.isNotBlank() }
        var currentPath = ""

        parts.forEachIndexed { index, part ->
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            val level = index

            // 如果节点不存在，创建它
            if (!nodeMap.containsKey(currentPath)) {
                val node = FolderNode(
                    path = currentPath,
                    name = part,
                    level = level
                )
                nodeMap[currentPath] = node

                if (level == 0) {
                    // 顶层节点
                    rootNodes.add(node)
                } else {
                    // 子节点，找到父节点并添加
                    val parentPath = parts.take(index).joinToString("/")
                    nodeMap[parentPath]?.children?.add(node)
                }
            }
        }
    }

    // 如果有"未分类"，将其添加到最前面
    if (uncategorized in folderPaths) {
        rootNodes.add(0, FolderNode(
            path = uncategorized,
            name = uncategorized,
            level = 0
        ))
    }

    return rootNodes
}

/**
 * 获取指定文件夹的所有子文件夹
 */
private fun getSubfolders(parentPath: String, allPaths: List<String>): Set<String> {
    return allPaths
        .filter { it.startsWith("$parentPath/") }
        .toSet()
}

/**
 * 从MemoryRepository加载文件夹路径列表
 */
private suspend fun loadFolderPaths(context: Context): List<String> = withContext(Dispatchers.IO) {
    try {
        val profileId = preferencesManager.activeMemorySpaceIdFlow.first()
        AppLogger.d("MemoryFolderDialog", "Loading folders for profileId: $profileId")
        val repository = MemoryRepository(context, profileId)
        val folders = repository.getAllFolderPaths()
        AppLogger.d("MemoryFolderDialog", "Loaded ${folders.size} folders: $folders")
        folders
    } catch (e: Exception) {
        AppLogger.e("MemoryFolderDialog", "Error loading folder paths", e)
        throw e
    }
}

