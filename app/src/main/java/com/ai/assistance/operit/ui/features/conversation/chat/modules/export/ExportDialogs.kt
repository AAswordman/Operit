package com.ai.assistance.operit.ui.features.conversation.chat.modules.export

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.ui.features.conversation.chat.modules.export.htmlpackager.PackagerPlatformDialog
import com.ai.assistance.operit.core.subpack.ApkEditor
import com.ai.assistance.operit.core.subpack.ExeEditor
import com.ai.assistance.operit.core.subpack.KeyStoreHelper
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.data.serialization.UriSerializer
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.compactDialogHeightOrWrapContent
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.rememberCompactDialogMetrics
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.rememberCompactDialogScrollState
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.verticalScrollWhenShort
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 在进入平台配置前校验对应的导出模板，缺失时提供明确的准备入口。 */
@Composable
fun ExportPlatformDialog(
    onDismiss: () -> Unit,
    onSelectAndroid: () -> Unit,
    onSelectWindows: () -> Unit,
) {
    PackagerPlatformDialog(onDismiss, onSelectAndroid, onSelectWindows)
}

/** Android导出配置对话框 */
@Composable
fun AndroidExportDialog(
        workDir: File,
        onDismiss: () -> Unit,
        onExport: (
                packageName: String,
                appName: String,
                iconUri: Uri?,
                versionName: String,
                versionCode: String
        ) -> Unit
) {
    var packageName by rememberLocal(key = "export_package_name_${workDir.absolutePath}", "com.example.webproject")
    var appName by rememberLocal(key = "export_app_name_${workDir.absolutePath}", "Web Project")
    var versionName by rememberLocal(key = "export_version_name_${workDir.absolutePath}", "1.0.0")
    var versionCode by rememberLocal(key = "export_version_code_${workDir.absolutePath}", "1")
    var iconUri by rememberLocal<Uri?>(key = "export_icon_uri_${workDir.absolutePath}", null, serializer = UriSerializer)

    var isPackageNameError by remember { mutableStateOf(false) }
    var isVersionNameError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val dialogMetrics = rememberCompactDialogMetrics()
    val compactScrollState = rememberCompactDialogScrollState()
    val cardModifier =
            Modifier
                    .fillMaxWidth(0.95f)
                    .compactDialogHeightOrWrapContent(dialogMetrics)
    val contentModifier =
            Modifier
                    .padding(20.dp)
                    .verticalScrollWhenShort(dialogMetrics, compactScrollState)
    val imageCropLauncher =
            rememberLauncherForActivityResult(contract = CropImageContract()) { result ->
                if (result.isSuccessful) {
                    result.uriContent?.let { croppedUri ->
                        iconUri = croppedUri
                    }
                } else {
                    val cropError = result.error
                    if (cropError != null) {
                        AppLogger.e("ExportDialogs", "Android export icon crop failed", cropError)
                    }
                }
            }
    val imagePicker =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                uri?.let { sourceUri ->
                    imageCropLauncher.launch(createExportIconCropOptions(context, sourceUri))
                }
            }

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.Start
            ) {
                Text(
                        context.getString(R.string.configure_android_app),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                        value = packageName,
                        onValueChange = { 
                            // 只允许小写字母、数字和点号
                            val filtered = it.filter { c -> c.isLowerCase() || c.isDigit() || c == '.' }
                            
                            // 验证包名格式
                            val isValid = when {
                                filtered.isEmpty() -> true  // 允许空输入
                                filtered.startsWith(".") -> false  // 不能以点开头
                                filtered.endsWith(".") -> false    // 不能以点结尾
                                filtered.contains("..") -> false   // 不能有连续的点
                                else -> {
                                    // 检查每个段是否以字母开头
                                    filtered.split('.').all { segment ->
                                        segment.isNotEmpty() && segment[0].isLetter()
                                    }
                                }
                            }
                            
                            isPackageNameError = !isValid && filtered.isNotEmpty()
                            packageName = filtered
                        },
                        label = { Text(context.getString(R.string.package_name_label)) },
                        placeholder = { Text("com.example.webproject") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isPackageNameError,
                        supportingText = { 
                            if (isPackageNameError) {
                                Text(context.getString(R.string.export_invalid_package_name_format))
                            }
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text(context.getString(R.string.app_name_label)) },
                        placeholder = { Text("Web Project") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 图标选择区域
                Row(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            context.getString(R.string.app_icon),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                    )

                    // 图标显示/选择区域
                    Box(
                            modifier =
                                    Modifier.size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(8.dp)
                                            )
                                            .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                    ) {
                        if (iconUri != null) {
                            val bitmap =
                                    remember(iconUri) {
                                        try {
                                            val inputStream =
                                                    context.contentResolver.openInputStream(
                                                            iconUri!!
                                                    )
                                            val bitmap = BitmapFactory.decodeStream(inputStream)
                                            inputStream?.close()
                                            bitmap?.asImageBitmap()
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }

                            if (bitmap != null) {
                                Image(
                                        bitmap = bitmap,
                                        contentDescription = context.getString(R.string.app_icon),
                                        contentScale = ContentScale.Fit,
                                        modifier =
                                                Modifier.size(70.dp).clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = context.getString(R.string.load_folder_failed),
                                        tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = context.getString(R.string.app_icon),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 提示文字
                    Text(
                            context.getString(R.string.click_select_icon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                        value = versionName,
                        onValueChange = { 
                            val newValue = it.filter { c -> c.isDigit() || c == '.' || c == '-' }
                            // 简单的版本号格式验证，例如 1.0.0 或 1.0.0-beta
                            val regex = "^\\d+(\\.\\d+){0,2}(-[a-zA-Z0-9]+)?$".toRegex()
                            isVersionNameError = !regex.matches(newValue) && newValue.isNotEmpty()
                            versionName = newValue
                        },
                        label = { Text(context.getString(R.string.version_name)) },
                        placeholder = { Text("1.0.0") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isVersionNameError,
                        supportingText = {
                            if (isVersionNameError) {
                                Text(context.getString(R.string.export_invalid_version_format))
                            }
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                        value = versionCode,
                        onValueChange = { versionCode = it.filter { c -> c.isDigit() } },
                        label = { Text(context.getString(R.string.version_code)) },
                        placeholder = { Text("1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮区域
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                            onClick = { onExport(packageName, appName, iconUri, versionName, versionCode) },
                            enabled = packageName.isNotEmpty() && !isPackageNameError && appName.isNotEmpty() && versionName.isNotEmpty() && !isVersionNameError && versionCode.isNotEmpty()
                    ) { Text(context.getString(R.string.export)) }
                }
            }
        }
    }
}

/** Windows导出配置对话框 */
@Composable
fun WindowsExportDialog(
        workDir: File,
        onDismiss: () -> Unit,
        onExport: (appName: String, iconUri: Uri?) -> Unit
) {
    var appName by remember { mutableStateOf("Web Project") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val dialogMetrics = rememberCompactDialogMetrics()
    val compactScrollState = rememberCompactDialogScrollState()
    val cardModifier =
            Modifier
                    .fillMaxWidth(0.95f)
                    .compactDialogHeightOrWrapContent(dialogMetrics)
    val contentModifier =
            Modifier
                    .padding(20.dp)
                    .verticalScrollWhenShort(dialogMetrics, compactScrollState)
    val imageCropLauncher =
            rememberLauncherForActivityResult(contract = CropImageContract()) { result ->
                if (result.isSuccessful) {
                    result.uriContent?.let { croppedUri ->
                        iconUri = croppedUri
                    }
                } else {
                    val cropError = result.error
                    if (cropError != null) {
                        AppLogger.e("ExportDialogs", "Windows export icon crop failed", cropError)
                    }
                }
            }
    val imagePicker =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri
                ->
                uri?.let { sourceUri ->
                    imageCropLauncher.launch(createExportIconCropOptions(context, sourceUri))
                }
            }

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.Start
            ) {
                Text(
                        context.getString(R.string.configure_windows_app),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text(context.getString(R.string.app_name)) },
                        placeholder = { Text("Web Project") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 图标选择区域
                Row(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            context.getString(R.string.app_icon),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                    )

                    // 图标显示/选择区域
                    Box(
                            modifier =
                                    Modifier.size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(8.dp)
                                            )
                                            .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                    ) {
                        if (iconUri != null) {
                            val bitmap =
                                    remember(iconUri) {
                                        try {
                                            val inputStream =
                                                    context.contentResolver.openInputStream(
                                                            iconUri!!
                                                    )
                                            val bitmap = BitmapFactory.decodeStream(inputStream)
                                            inputStream?.close()
                                            bitmap?.asImageBitmap()
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }

                            if (bitmap != null) {
                                Image(
                                        bitmap = bitmap,
                                        contentDescription = context.getString(R.string.app_icon),
                                        contentScale = ContentScale.Fit,
                                        modifier =
                                                Modifier.size(70.dp).clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = context.getString(R.string.load_folder_failed),
                                        tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = context.getString(R.string.app_icon),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 提示文字
                    Text(
                            context.getString(R.string.click_select_icon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮区域
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                            onClick = { onExport(appName, iconUri) },
                            enabled = appName.isNotEmpty()
                    ) { Text(context.getString(R.string.export)) }
                }
            }
        }
    }
}

private fun createExportIconCropOptions(context: Context, sourceUri: Uri): CropImageContractOptions {
    val isNightMode =
            context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

    var primaryColor: Int
    var onPrimaryColor: Int
    var surfaceColor: Int
    var statusBarColor: Int

    try {
        val typedValue = android.util.TypedValue()

        context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        primaryColor = typedValue.data

        context.theme.resolveAttribute(android.R.attr.colorPrimaryDark, typedValue, true)
        statusBarColor = typedValue.data

        context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        surfaceColor = typedValue.data

        onPrimaryColor = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    } catch (_: Exception) {
        primaryColor = if (isNightMode) 0xFF9C27B0.toInt() else 0xFF6200EE.toInt()
        statusBarColor = if (isNightMode) 0xFF7B1FA2.toInt() else 0xFF3700B3.toInt()
        surfaceColor = if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        onPrimaryColor = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    }

    return CropImageContractOptions(
            sourceUri,
            CropImageOptions().apply {
                guidelines = CropImageView.Guidelines.ON
                outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                outputCompressQuality = 100
                fixAspectRatio = true
                aspectRatioX = 1
                aspectRatioY = 1
                cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                activityTitle = context.getString(R.string.theme_crop_image)
                toolbarColor = primaryColor
                toolbarBackButtonColor = onPrimaryColor
                toolbarTitleColor = onPrimaryColor
                activityBackgroundColor = surfaceColor
                backgroundColor = surfaceColor
                statusBarColor = statusBarColor
                activityMenuIconColor = onPrimaryColor
                showCropOverlay = true
                showProgressBar = true
                multiTouchEnabled = true
                autoZoomEnabled = true
            }
    )
}

/** 导出进度对话框 */
@Composable
fun ExportProgressDialog(progress: Float, status: String, onCancel: () -> Unit) {
    val context = LocalContext.current
    Dialog(
            onDismissRequest = { /* 不允许点击外部关闭 */},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        context.getString(R.string.export_in_progress_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onCancel) { Text(context.getString(R.string.cancel)) }
            }
        }
    }
}

/** 导出完成对话框 */
@Composable
fun ExportCompleteDialog(
        success: Boolean,
        filePath: String?,
        errorMessage: String?,
        onDismiss: () -> Unit,
        onOpenFile: (String) -> Unit
) {
    val context = LocalContext.current
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        if (success) context.getString(R.string.export_success) else context.getString(R.string.export_failed),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (success) Color.Green else Color.Red
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (success && filePath != null) {
                    Text(context.getString(R.string.file_saved_to), style = MaterialTheme.typography.bodyMedium)

                    Text(
                            filePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (errorMessage != null) {
                    // 使用可滚动的列表显示错误信息，特别是对于长消息
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .heightIn(min = 80.dp, max = 200.dp)
                                            .border(
                                                    1.dp,
                                                    Color.Red.copy(alpha = 0.5f),
                                                    RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                    ) {
                        Text(
                                errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Red,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onDismiss) { Text(context.getString(R.string.close)) }

                    if (success && filePath != null) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Button(onClick = { onOpenFile(filePath) }) { Text(context.getString(R.string.open_file)) }
                    }
                }
            }
        }
    }
}

/**
 * 处理Android应用导出
 * @param context 应用上下文
 * @param packageName 包名
 * @param appName 应用名称
 * @param iconUri 图标URI
 * @param webContentDir 网页内容目录
 * @param onProgress 进度回调
 * @param onComplete 完成回调
 */
suspend fun exportAndroidApp(
        context: Context,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: String,
        iconUri: Uri?,
        webContentDir: File,
        onProgress: (Float, String) -> Unit,
        onComplete: (success: Boolean, filePath: String?, errorMessage: String?) -> Unit
) {
    try {
        withContext(Dispatchers.IO) {
            onProgress(0.1f, context.getString(R.string.export_prepare_base_apk))

            // 1. 初始化APK编辑器
            val template = OfflineResources.copyExportTemplate(context, true, File(context.cacheDir, "apk_editor_android.apk"))
            val apkEditor = ApkEditor.fromFile(context, template)

            // 2. 修改包名和应用名
            onProgress(0.3f, context.getString(R.string.export_modify_app_info))
            apkEditor.changePackageName(packageName)
            apkEditor.changeAppName(appName)
            apkEditor.changeVersionName(versionName)
            apkEditor.changeVersionCode(versionCode)

            // 4. 更改图标（如果提供）
            if (iconUri != null) {
                onProgress(0.4f, context.getString(R.string.export_change_app_icon))
                context.contentResolver.openInputStream(iconUri)?.use { inputStream ->
                    apkEditor.changeIcon(inputStream)
                }
            }

            // 6. 准备签名文件
            onProgress(0.7f, context.getString(R.string.export_prepare_signing))
            // 使用KeyStoreHelper获取密钥库
            val keyStoreFile = KeyStoreHelper.getOrCreateKeystore(context)
            AppLogger.d(
                    "ExportDialogs",
                    "签名使用密钥库: ${keyStoreFile.absolutePath}, 大小: ${keyStoreFile.length()}"
            )

            // 7. 设置签名信息并执行签名
            onProgress(0.8f, context.getString(R.string.export_signing_apk))
            // 使用下载目录下的Operit/exports子目录
            val outputDir =
                    File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS
                            ),
                            "Operit/exports"
                    )
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val outputName = "WebApp_${Date().time}.apk"
            val outputFile = File(outputDir, outputName)

            AppLogger.d("ExportDialogs", "即将签名APK，使用密钥: ${keyStoreFile.absolutePath}, 别名: androidkey")
            apkEditor
                    .withSignature(
                            keyStoreFile,
                            "android", // 密码
                            "androidkey", // 别名
                            "android" // 密钥密码
                    )
                    .setOutput(outputFile)

            try {
                onProgress(0.5f, context.getString(R.string.export_pack_web_content))
                onProgress(0.9f, context.getString(R.string.export_finish_packaging))
                val signedApk = apkEditor.repackAndSignWithWebContent(webContentDir)

                // 9. 清理
                apkEditor.cleanup()

                onProgress(1.0f, context.getString(R.string.export_completed))
                onComplete(true, signedApk.absolutePath, null)
            } catch (e: Exception) {
                AppLogger.e("ExportDialogs", "签名APK失败", e)
                onComplete(false, null, context.getString(R.string.export_sign_apk_failed, e.message ?: ""))
                apkEditor.cleanup() // 确保失败时也清理资源
            }
        }
    } catch (e: Exception) {
        AppLogger.e("ExportDialogs", "导出失败", e)
        onComplete(false, null, context.getString(R.string.export_failed_with_reason, e.message ?: ""))
    }
}

/** 处理Windows应用导出 */
suspend fun exportWindowsApp(
        context: Context,
        appName: String,
        iconUri: Uri?,
        webContentDir: File,
        onProgress: (Float, String) -> Unit,
        onComplete: (success: Boolean, filePath: String?, errorMessage: String?) -> Unit
) {
    try {
        withContext(Dispatchers.IO) {
            onProgress(0.1f, context.getString(R.string.export_prepare_windows_template))

            // 创建输出目录 - 使用下载目录下的Operit/exports子目录
            val outputDir =
                    File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS
                            ),
                            "Operit/exports"
                    )
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // 创建临时工作目录
            val tempDir = File(context.cacheDir, "windows_export_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()

            try {
                // 先取得已校验模板的工作副本，再执行解压和修改。
                onProgress(0.2f, context.getString(R.string.export_copy_template))
                val templateZip = OfflineResources.copyExportTemplate(context, false, File(tempDir, "windows.zip"))

                // 2. 解压windows.zip
                onProgress(0.3f, context.getString(R.string.export_extract_template))
                val extractedDir = File(tempDir, "extracted")
                extractedDir.mkdirs()

                // 解压ZIP文件
                java.util.zip.ZipFile(templateZip).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryFile = File(extractedDir, entry.name)

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(entryFile).use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }

                // 3. 如果提供了图标，修改assistance_subpack.exe的图标
                if (iconUri != null) {
                    onProgress(0.4f, context.getString(R.string.export_change_app_icon))
                    val mainExe = File(extractedDir, "assistance_subpack.exe")
                    if (mainExe.exists()) {
                        try {
                            val exeEditor = ExeEditor.fromFile(context, mainExe)
                            context.contentResolver.openInputStream(iconUri)?.use { input ->
                                exeEditor.changeIcon(input).setOutput(mainExe).process()
                            }
                            AppLogger.d("ExportDialogs", "已更换Windows应用图标")
                        } catch (e: Exception) {
                            AppLogger.e("ExportDialogs", "更换Windows应用图标失败", e)
                            // 继续执行，不因图标失败而中断整个导出流程
                        }
                    } else {
                        AppLogger.e("ExportDialogs", "未找到assistance_subpack.exe文件")
                    }
                }

                // 4. 复制网页内容到data\flutter_assets\assets\web_content
                onProgress(0.5f, context.getString(R.string.export_copy_web_content))
                val webContentTarget = File(extractedDir, "data/flutter_assets/assets/web_content")
                if (!webContentTarget.exists()) {
                    webContentTarget.mkdirs()
                }

                AppLogger.d(
                        "ExportDialogs",
                        "复制网页文件到Windows应用: ${webContentDir.absolutePath} -> ${webContentTarget.absolutePath}"
                )
                copyDirectory(webContentDir, webContentTarget)

                // 5. 创建最终输出文件名
                val timestamp =
                        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                                .format(java.util.Date())
                val safeName = appName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val outputZip = File(outputDir, "${safeName}_${timestamp}.zip")

                // 6. 重新打包为ZIP
                onProgress(0.8f, context.getString(R.string.export_pack_app))

                // 确保输出文件不存在
                if (outputZip.exists()) {
                    outputZip.delete()
                }

                // 创建ZIP文件
                val buffer = ByteArray(1024)
                java.util.zip.ZipOutputStream(FileOutputStream(outputZip)).use { zipOut ->
                    // 添加文件到ZIP
                    addDirToZip(extractedDir, extractedDir, zipOut, buffer)
                }

                onProgress(1.0f, context.getString(R.string.export_completed))
                onComplete(true, outputZip.absolutePath, null)
            } catch (e: Exception) {
                AppLogger.e("ExportDialogs", "Windows应用导出过程失败", e)
                onComplete(false, null, context.getString(R.string.export_process_failed, e.message ?: ""))
            } finally {
                // 7. 清理临时文件
                try {
                    onProgress(0.9f, context.getString(R.string.export_cleanup_temp_files))
                    tempDir.deleteRecursively()
                } catch (e: Exception) {
                    AppLogger.e("ExportDialogs", "清理临时文件失败", e)
                }
            }
        }
    } catch (e: Exception) {
        AppLogger.e("ExportDialogs", "Windows应用导出失败", e)
        onComplete(false, null, context.getString(R.string.export_failed_with_reason, e.message ?: ""))
    }
}

/** 递归添加目录到ZIP文件 */
private fun addDirToZip(
        rootDir: File,
        currentDir: File,
        zipOut: java.util.zip.ZipOutputStream,
        buffer: ByteArray
) {
    currentDir.listFiles()?.forEach { file ->
        val relativePath =
                file.absolutePath.substring(rootDir.absolutePath.length + 1).replace("\\", "/")

        if (file.isDirectory) {
            if (file.listFiles()?.isNotEmpty() == true) {
                addDirToZip(rootDir, file, zipOut, buffer)
            } else {
                val entry = java.util.zip.ZipEntry("$relativePath/")
                zipOut.putNextEntry(entry)
                zipOut.closeEntry()
            }
        } else {
            try {
                val entry = java.util.zip.ZipEntry(relativePath)
                zipOut.putNextEntry(entry)

                FileInputStream(file).use { input ->
                    var len: Int
                    while (input.read(buffer).also { len = it } > 0) {
                        zipOut.write(buffer, 0, len)
                    }
                }

                zipOut.closeEntry()
                AppLogger.d("ExportDialogs", "已添加文件到ZIP: $relativePath")
            } catch (e: Exception) {
                AppLogger.e("ExportDialogs", "添加文件到ZIP失败: $relativePath", e)
            }
        }
    }
}

/** 创建或获取应用签名密钥库 */
private fun createOrGetKeystore(context: Context): File {
    // 直接使用KeyStoreHelper
    return KeyStoreHelper.getOrCreateKeystore(context)
}

/** 验证密钥库文件是否有效 */
private fun validateKeystore(file: File, type: String, password: String): Boolean {
    // 直接使用KeyStoreHelper
    return KeyStoreHelper.validateKeystore(file, type, password)
}

/** 复制目录及其内容 */
private fun copyDirectory(sourceDir: File, destDir: File) {
    if (!destDir.exists()) {
        destDir.mkdirs()
    }

    sourceDir.listFiles()?.forEach { file ->
        val destFile = File(destDir, file.name)
        if (file.isDirectory) {
            copyDirectory(file, destFile)
        } else {
            try {
                // 如果目标文件已存在，则先删除
                if (destFile.exists()) {
                    destFile.delete()
                }

                // 确保父目录存在
                destFile.parentFile?.mkdirs()

                // 复制文件内容
                file.inputStream().use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }

                AppLogger.d("ExportDialogs", "成功复制文件: ${file.absolutePath} -> ${destFile.absolutePath}")
            } catch (e: Exception) {
                AppLogger.e(
                        "ExportDialogs",
                        "复制文件失败: ${file.absolutePath} -> ${destFile.absolutePath}",
                        e
                )
            }
        }
    }
}
