package com.ai.assistance.operit.ui.features.chat.webview.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.webview.createAndGetDefaultWorkspace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.ui.features.chat.webview.createAndResetWorkspaceDirectory
import kotlinx.coroutines.*

/**
 * VSCode风格的工作区设置组件
 * 用于初始绑定工作区
 */
@Composable
fun WorkspaceSetup(chatId: String, onBindWorkspace: (String, String?) -> Unit, onFileOpen: (OpenFileInfo) -> Unit) {
    val context = LocalContext.current
    var showProjectTypeDialog by remember { mutableStateOf(false) }
    var projectTypeDialogError by remember { mutableStateOf<String?>(null) }
    var isImportingToolPkgTemplate by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val packageManager = remember { PackageManager.getInstance(context, toolHandler) }
    var toolPkgWorkspaceTemplates by remember { mutableStateOf<List<PackageManager.ToolPkgWorkspaceTemplate>>(emptyList()) }

    LaunchedEffect(Unit) {
        toolPkgWorkspaceTemplates = packageManager.getToolPkgWorkspaceTemplates(context)
    }

    fun bindBuiltInWorkspace(projectType: String?) {
        val workspaceDir =
            if (projectType == null) {
                createAndGetDefaultWorkspace(context, chatId)
            } else {
                createAndGetDefaultWorkspace(context, chatId, projectType)
            }
        onBindWorkspace(workspaceDir.absolutePath, null)
        showProjectTypeDialog = false
        projectTypeDialogError = null
    }

    fun importToolPkgWorkspaceTemplate(template: PackageManager.ToolPkgWorkspaceTemplate) {
        if (isImportingToolPkgTemplate) return
        projectTypeDialogError = null
        isImportingToolPkgTemplate = true
        scope.launch {
            val importAttempt =
                withContext(Dispatchers.IO) {
                    val workspaceDir = createAndResetWorkspaceDirectory(context, chatId)
                    packageManager.importToolPkgWorkspaceTemplate(
                        containerPackageName = template.containerPackageName,
                        templateId = template.templateId,
                        destinationDir = workspaceDir
                    ).fold(
                        onSuccess = { Result.success(workspaceDir to it) },
                        onFailure = {
                            if (workspaceDir.exists()) {
                                workspaceDir.deleteRecursively()
                            }
                            Result.failure(it)
                        }
                    )
                }

            importAttempt.fold(
                onSuccess = { (_, result) ->
                    showProjectTypeDialog = false
                    onBindWorkspace(result.workspacePath, null)
                },
                onFailure = {
                    projectTypeDialogError =
                        it.message ?: context.getString(R.string.workspace_template_import_failed)
                }
            )
            isImportingToolPkgTemplate = false
        }
    }

    if (showProjectTypeDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isImportingToolPkgTemplate) {
                    showProjectTypeDialog = false
                    projectTypeDialogError = null
                }
            },
            title = {
                Text(
                    text = context.getString(R.string.workspace_select_language_type_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = context.getString(R.string.workspace_select_language_type_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    projectTypeDialogError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (isImportingToolPkgTemplate) {
                        Text(
                            text = context.getString(R.string.workspace_template_importing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ProjectTypeCard(
                        icon = Icons.Default.CreateNewFolder,
                        title = context.getString(R.string.workspace_project_type_blank_title),
                        description = context.getString(R.string.workspace_project_type_blank_description),
                        onClick = {
                            bindBuiltInWorkspace("blank")
                        }
                    )

                    // Office 项目卡片
                    ProjectTypeCard(
                        icon = Icons.Default.Description,
                        title = context.getString(R.string.workspace_project_type_office_title),
                        description = context.getString(R.string.workspace_project_type_office_description),
                        onClick = {
                            bindBuiltInWorkspace("office")
                        }
                    )

                    // Web 项目卡片
                    ProjectTypeCard(
                        icon = Icons.Default.Language,
                        title = context.getString(R.string.workspace_project_type_web_title),
                        description = context.getString(R.string.workspace_project_type_web_description),
                        onClick = {
                            bindBuiltInWorkspace(null)
                        }
                    )

                    // Android 项目卡片
                    ProjectTypeCard(
                        icon = Icons.Default.PhoneAndroid,
                        title = context.getString(R.string.workspace_project_type_android_title),
                        description = context.getString(R.string.workspace_project_type_android_description),
                        onClick = {
                            bindBuiltInWorkspace("android")
                        }
                    )

                    // Flutter 项目卡片
                    ProjectTypeCard(
                        icon = Icons.Default.Widgets,
                        title = context.getString(R.string.workspace_project_type_flutter_title),
                        description = context.getString(R.string.workspace_project_type_flutter_description),
                        onClick = {
                            bindBuiltInWorkspace("flutter")
                        }
                    )

                    // Node.js 项目卡片
                    ProjectTypeCard(
                        icon = Icons.Default.Terminal,
                        title = context.getString(R.string.workspace_project_type_node_title),
                        description = context.getString(R.string.workspace_project_type_node_description),
                        onClick = {
                            bindBuiltInWorkspace("node")
                        }
                    )

                    // TypeScript 项目卡片
                    ProjectTypeCard(
                        icon = Icons.Default.Code,
                        title = context.getString(R.string.workspace_project_type_typescript_title),
                        description = context.getString(R.string.workspace_project_type_typescript_description),
                        onClick = {
                            bindBuiltInWorkspace("typescript")
                        }
                    )

                    // Python 项目卡片
                    ProjectTypeCard(
                        icon = Icons.Default.Code,
                        title = context.getString(R.string.workspace_project_type_python_title),
                        description = context.getString(R.string.workspace_project_type_python_description),
                        onClick = {
                            bindBuiltInWorkspace("python")
                        }
                    )

                    // Java 项目卡片
                    ProjectTypeCard(
                        icon = Icons.Default.Settings,
                        title = context.getString(R.string.workspace_project_type_java_title),
                        description = context.getString(R.string.workspace_project_type_java_description),
                        onClick = {
                            bindBuiltInWorkspace("java")
                        }
                    )

                    // Go 项目卡片
                    ProjectTypeCard(
                        icon = Icons.Default.Build,
                        title = context.getString(R.string.workspace_project_type_go_title),
                        description = context.getString(R.string.workspace_project_type_go_description),
                        onClick = {
                            bindBuiltInWorkspace("go")
                        }
                    )

                    if (toolPkgWorkspaceTemplates.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = context.getString(R.string.workspace_project_type_toolpkg_section),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        toolPkgWorkspaceTemplates.forEach { template ->
                            ProjectTypeCard(
                                icon = Icons.Default.Extension,
                                title = template.displayName,
                                description =
                                    buildString {
                                        append(template.containerPackageName)
                                        if (template.description.isNotBlank()) {
                                            append(" · ")
                                            append(template.description)
                                        }
                                    },
                                onClick = {
                                    importToolPkgWorkspaceTemplate(template)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showProjectTypeDialog = false
                        projectTypeDialogError = null
                    },
                    enabled = !isImportingToolPkgTemplate
                ) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = context.getString(R.string.setup_workspace),
                    // 设置页使用自己的主题前景，避免继承聊天区域的黑色文字。
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = context.getString(R.string.select_folder_from_device),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = {
                projectTypeDialogError = null
                showProjectTypeDialog = true
            }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(context.getString(R.string.create_default_workspace))
            }
        }
        HorizontalDivider()
        Box(modifier = Modifier.weight(1f)) {
            FileBrowser(
                initialPath = context.filesDir.absolutePath,
                onBindWorkspace = { path, env -> onBindWorkspace(path, env) },
                onCancel = {},
                showHeader = false,
                onFileOpen = onFileOpen
            )
        }
    }
}

/**
 * 项目类型卡片组件（IDE风格）
 */
@Composable
fun ProjectTypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // 文字内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 箭头指示
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
