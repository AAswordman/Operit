package com.ai.assistance.operit.ui.features.conversation.chat.modules.export.htmlpackager

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Html
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.AndroidExportDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.ExportCompleteDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.ExportPlatformDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.ExportProgressDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.WindowsExportDialog
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.exportAndroidApp
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.exportWindowsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlPackagerScreen(onGoBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val resources = rememberPackagerResources()

    var webProjectUri by remember { mutableStateOf<Uri?>(null) }
    var htmlFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var selectedIndexFile by remember { mutableStateOf<DocumentFile?>(null) }
    var isIndexFileDropdownExpanded by remember { mutableStateOf(false) }

    var showExportPlatformDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showWindowsExportDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }

    var exportProgress by remember { mutableStateOf(0f) }
    var exportStatus by remember { mutableStateOf("") }
    var exportResult by remember { mutableStateOf<Result<String>?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            webProjectUri = uri
            val docFile = DocumentFile.fromTreeUri(context, uri)
            if (docFile != null && docFile.isDirectory) {
                htmlFiles = docFile.listFiles().filter { it.isFile && it.name?.endsWith(".html", true) == true }
                selectedIndexFile = htmlFiles.find { it.name.equals("index.html", ignoreCase = true) } ?: htmlFiles.firstOrNull()
            }
        }
    }

    // 进入工具时先确认模板可用，缺失时不展示可继续打包的表单。
    if (!resources.hasReadyTemplate && !showProgressDialog && !showCompleteDialog) {
        CustomScaffold { padding -> PackagerResourceGate(resources, Modifier.padding(padding)) }
        return
    }

    CustomScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {

            PackagerResourceSummary(resources)
            Spacer(Modifier.height(16.dp))

            // 第一步：选择项目文件夹
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = "Step 1", modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.htmlpackager_step1_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { folderPickerLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.htmlpackager_select_folder))
                    }
                    if (webProjectUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.htmlpackager_selected, DocumentFile.fromTreeUri(context, webProjectUri!!)?.name ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Select Index File
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Html, contentDescription = "Step 2", modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.htmlpackager_step2_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ExposedDropdownMenuBox(
                        expanded = isIndexFileDropdownExpanded,
                        onExpandedChange = { isIndexFileDropdownExpanded = !isIndexFileDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedIndexFile?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.htmlpackager_main_html)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isIndexFileDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = webProjectUri != null && htmlFiles.isNotEmpty()
                        )
                        ExposedDropdownMenu(
                            expanded = isIndexFileDropdownExpanded,
                            onDismissRequest = { isIndexFileDropdownExpanded = false }
                        ) {
                            htmlFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.name ?: "") },
                                    onClick = {
                                        selectedIndexFile = file
                                        isIndexFileDropdownExpanded = false
                                    },
                                    trailingIcon = if (selectedIndexFile?.uri == file.uri) { { Icon(Icons.Default.Check, "Selected") } } else null
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step 3: Package
            Button(
                onClick = { showExportPlatformDialog = true },
                enabled = selectedIndexFile != null && !resources.checking,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = "Package", modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.htmlpackager_generate_package), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showExportPlatformDialog) {
        ExportPlatformDialog(
            onDismiss = { showExportPlatformDialog = false },
            onSelectAndroid = {
                showExportPlatformDialog = false
                showExportDialog = true
            },
            onSelectWindows = {
                showExportPlatformDialog = false
                showWindowsExportDialog = true
            }
        )
    }

    if (showExportDialog && webProjectUri != null && selectedIndexFile != null) {
        val workDirForDialog = context.cacheDir.resolve("dialog_workdir_${System.currentTimeMillis()}")
        AndroidExportDialog(
            workDir = workDirForDialog,
            onDismiss = { showExportDialog = false },
            onExport = { packageName, appName, iconUri, versionName, versionCode ->
                showExportDialog = false
                showProgressDialog = true
                coroutineScope.launch {
                    val externalFilesDir = context.getExternalFilesDir(null)
                        ?: throw IllegalStateException("External files directory not available.")
                    val tempWorkDir = File(externalFilesDir, "html_packager_temp_${System.currentTimeMillis()}")
                    try {
                        withContext(Dispatchers.IO) {
                            if (tempWorkDir.exists()) tempWorkDir.deleteRecursively()
                            tempWorkDir.mkdirs()

                            // 2. 将用户选择的文件夹内容 (Uri) 完整复制到这个临时文件夹
                            val sourceFolder = DocumentFile.fromTreeUri(context, webProjectUri!!)!!
                            copyDocumentTreeTo(context, sourceFolder, tempWorkDir)

                            // 3. 将选定的主 HTML 文件重命名为 index.html
                            val originalFile = File(tempWorkDir, selectedIndexFile!!.name!!)
                            val indexFile = File(tempWorkDir, "index.html")
                            if (originalFile.exists() && !originalFile.name.equals("index.html", ignoreCase = true)) {
                                if (indexFile.exists()) indexFile.delete()
                                if (!originalFile.renameTo(indexFile)) {
                                    throw IOException("Failed to rename index file.")
                                }
                            }
                        }

                        // 4. 使用临时文件夹的绝对路径调用导出接口
                        exportAndroidApp(
                            context = context,
                            packageName = packageName,
                            appName = appName,
                            versionName = versionName,
                            versionCode = versionCode,
                            iconUri = iconUri,
                            webContentDir = tempWorkDir, // 使用包含绝对路径的File对象
                            onProgress = { progress, status ->
                                exportProgress = progress
                                exportStatus = status
                            },
                            onComplete = { success, filePath, errorMessage ->
                                exportResult = if (success && filePath != null) Result.success(filePath) else Result.failure(Exception(errorMessage))
                                showProgressDialog = false
                                showCompleteDialog = true
                            }
                        )
                    } catch (e: Exception) {
                        exportResult = Result.failure(e)
                        showProgressDialog = false
                        showCompleteDialog = true
                    } finally {
                        // 5. 无论成功与否，都清理临时文件夹
                        withContext(Dispatchers.IO) {
                            if (tempWorkDir.exists()) {
                                tempWorkDir.deleteRecursively()
                            }
                        }
                    }
                }
            }
        )
    }

    if (showWindowsExportDialog && webProjectUri != null && selectedIndexFile != null) {
        val workDirForDialog = context.cacheDir.resolve("windows_dialog_workdir_${System.currentTimeMillis()}")
        WindowsExportDialog(
            workDir = workDirForDialog,
            onDismiss = { showWindowsExportDialog = false },
            onExport = { appName, iconUri ->
                showWindowsExportDialog = false
                showProgressDialog = true
                coroutineScope.launch {
                    val externalFilesDir = context.getExternalFilesDir(null)
                        ?: throw IllegalStateException("External files directory not available.")
                    val tempWorkDir = File(externalFilesDir, "html_packager_temp_${System.currentTimeMillis()}")
                    try {
                        withContext(Dispatchers.IO) {
                            if (tempWorkDir.exists()) tempWorkDir.deleteRecursively()
                            tempWorkDir.mkdirs()

                            val sourceFolder = DocumentFile.fromTreeUri(context, webProjectUri!!)!!
                            copyDocumentTreeTo(context, sourceFolder, tempWorkDir)

                            val originalFile = File(tempWorkDir, selectedIndexFile!!.name!!)
                            val indexFile = File(tempWorkDir, "index.html")
                            if (originalFile.exists() && !originalFile.name.equals("index.html", ignoreCase = true)) {
                                if (indexFile.exists()) indexFile.delete()
                                if (!originalFile.renameTo(indexFile)) {
                                    throw IOException("Failed to rename index file.")
                                }
                            }
                        }

                        exportWindowsApp(
                            context = context,
                            appName = appName,
                            iconUri = iconUri,
                            webContentDir = tempWorkDir,
                            onProgress = { progress, status ->
                                exportProgress = progress
                                exportStatus = status
                            },
                            onComplete = { success, filePath, errorMessage ->
                                exportResult = if (success && filePath != null) Result.success(filePath) else Result.failure(Exception(errorMessage))
                                showProgressDialog = false
                                showCompleteDialog = true
                            }
                        )
                    } catch (e: Exception) {
                        exportResult = Result.failure(e)
                        showProgressDialog = false
                        showCompleteDialog = true
                    } finally {
                        withContext(Dispatchers.IO) {
                            if (tempWorkDir.exists()) {
                                tempWorkDir.deleteRecursively()
                            }
                        }
                    }
                }
            }
        )
    }

    if (showProgressDialog) {
        ExportProgressDialog(
            progress = exportProgress,
            status = exportStatus,
            onCancel = { /* Cancel logic can be added here */ }
        )
    }

    if (showCompleteDialog) {
        val result = exportResult
        ExportCompleteDialog(
            success = result?.isSuccess ?: false,
            filePath = result?.getOrNull(),
            errorMessage = result?.exceptionOrNull()?.message,
            onDismiss = { showCompleteDialog = false },
            onOpenFile = { filePath ->
                val openFileTool = AITool(
                    name = "open_file",
                    parameters = listOf(ToolParameter("path", filePath))
                )
                // 仅在打开导出文件时获取工具处理器，保留资源预检入口的轻量初始化。
                AIToolHandler.getInstance(context).executeTool(openFileTool)
            }
        )
    }
}

/**
 * Recursively copies a directory from a Storage Access Framework (SAF) Uri to a local File directory.
 */
private fun copyDocumentTreeTo(context: Context, sourceDoc: DocumentFile, destDir: File) {
    if (!destDir.exists()) {
        destDir.mkdirs()
    }

    sourceDoc.listFiles().forEach { docFile ->
        val destFile = File(destDir, docFile.name!!)
        if (docFile.isDirectory) {
            copyDocumentTreeTo(context, docFile, destFile)
        } else {
            try {
                context.contentResolver.openInputStream(docFile.uri)?.use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                // Log or handle error for a single file, but don't stop the whole process
                com.ai.assistance.operit.util.logging.AppLogger.e("HtmlPackager", "Failed to copy file: ${docFile.name}", e)
            }
        }
    }
}
