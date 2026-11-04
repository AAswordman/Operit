package com.ai.assistance.operit.ui.common.markdown

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.ai.assistance.operit.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MarkdownImageRenderer"

/** 判断Markdown图片语法是否完整 检查是否有完整的 ![alt](url) 语法 */
internal fun isCompleteImageMarkdown(content: String): Boolean {
    // 最基本的检查：包含![和]和(和)
    return content.contains("![") &&
            content.contains("](") &&
            content.indexOf(')') > content.indexOf('(')
}

/** 从Markdown图片语法中提取Alt文本 */
internal fun extractMarkdownImageAlt(imageContent: String): String {
    val startMarker = imageContent.indexOf("![")
    if (startMarker == -1) return ""

    val startBracket = startMarker + 2 // 跳过"!["
    val endBracket = imageContent.indexOf(']', startBracket)

    if (endBracket == -1) return ""
    return imageContent.substring(startBracket, endBracket).trim()
}

/** 从Markdown图片语法中提取URL */
internal fun extractMarkdownImageUrl(imageContent: String): String {
    val startParenthesis = imageContent.indexOf('(')
    if (startParenthesis == -1) return ""

    val endParenthesis = imageContent.indexOf(')', startParenthesis)
    if (endParenthesis == -1) return ""

    return imageContent.substring(startParenthesis + 1, endParenthesis).trim()
}

/** 判断是否为大图片 简单根据图片URL后缀来判断 */
private fun isLargeImage(url: String): Boolean {
    // 通常视为"大图片"的文件格式
    val largeImageExtensions = listOf(".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".webp")
    return largeImageExtensions.any { url.lowercase().endsWith(it) }
}

/** Markdown图片渲染器组件 支持流式渲染和图片预览功能 */
@Composable
fun MarkdownImageRenderer(
        imageMarkdown: String,
        modifier: Modifier = Modifier,
        maxImageHeight: Int = 160, // 更小的默认最大高度
        enableDialogs: Boolean = true
) {
    // 只有完整的Markdown图片语法才会被渲染
    if (!isCompleteImageMarkdown(imageMarkdown)) {
        return
    }

    val imageAlt = extractMarkdownImageAlt(imageMarkdown)
    val imageUrl = extractMarkdownImageUrl(imageMarkdown)

    if (imageUrl.isEmpty()) {
        return
    }

    if (isLikelyVideoUrl(imageUrl)) {
        MarkdownVideoRenderer(
            videoMarkdown = imageMarkdown,
            modifier = modifier,
            maxVideoHeight = 220
        )
        return
    }

    if (isLikelyAudioUrl(imageUrl)) {
        MarkdownAudioRenderer(
            audioMarkdown = imageMarkdown,
            modifier = modifier
        )
        return
    }

    // 全屏预览状态
    var showFullScreen by remember { mutableStateOf(false) }

    // 无障碍朗读描述：只朗读块类型
    val accessibilityDesc = if (imageAlt.isNotEmpty()) {
        "${stringResource(R.string.image_block)}: $imageAlt"
    } else {
        stringResource(R.string.image_block)
    }

    Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .semantics { contentDescription = accessibilityDesc }
    ) {
        // 图片容器 - 移除容器的圆角，只保留基本的布局
        Box(
                modifier =
                        Modifier.wrapContentHeight()
                            .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)) // 增加圆角大小为12dp
                                .clickable(enabled = enableDialogs) { showFullScreen = true }
        ) {
            SubcomposeAsyncImage(
                    model =
                            ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                    contentDescription = null,
                    modifier =
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .align(Alignment.Center)
                                    .heightIn(max = maxImageHeight.dp),
                    contentScale = ContentScale.Fit,
                    onSuccess = { /* 移除复杂的宽高比计算 */},
                    loading = {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(50.dp) // 更小的加载区域
                                                .background(
                                                        Color.LightGray.copy(alpha = 0.1f)
                                                ), // 更淡的背景
                                contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp), // 更小的加载指示器
                                    strokeWidth = 2.dp, // 更细的加载指示器
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    error = {
                        Text(
                                text = stringResource(R.string.image_load_failed),
                                color = Color.Red.copy(alpha = 0.7f), // 更淡的文字颜色
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                    }
            )
        }

        // 显示图片说明，仅在需要时显示
        if (imageAlt.isNotEmpty()) {
            Text(
                    text = imageAlt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier =
                            Modifier.fillMaxWidth() // 确保文本占据整个宽度
                                    .padding(horizontal = 2.dp, vertical = 1.dp),
                    textAlign = TextAlign.Center, // 居中对齐
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }

    // 全屏预览对话框
    if (enableDialogs && showFullScreen) {
        FullScreenImageDialog(
                imageUrl = imageUrl,
                imageAlt = imageAlt,
                onDismiss = { showFullScreen = false }
        )
    }
}

/** 全屏图片预览对话框 */
@Composable
private fun FullScreenImageDialog(imageUrl: String, imageAlt: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var savedSuccess by remember { mutableStateOf<Boolean?>(null) }

    // 缩放和平移状态
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
            onDismissRequest = onDismiss,
            properties =
                    DialogProperties(
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true,
                            usePlatformDefaultWidth = false
                    )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.9f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 图片内容
                SubcomposeAsyncImage(
                        model =
                                ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .build(),
                        contentDescription = imageAlt,
                        modifier =
                                Modifier.fillMaxSize()
                                        .graphicsLayer(
                                                scaleX = scale,
                                                scaleY = scale,
                                                translationX = offset.x,
                                                translationY = offset.y
                                        )
                                        .pointerInput(Unit) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                // 限制缩放范围
                                                scale = (scale * zoom).coerceIn(0.5f, 3f)

                                                // 修复缩放限制计算错误
                                                // 根据缩放计算最大偏移值，使用绝对值确保正数
                                                val maxX =
                                                        (size.width * (scale - 1) / 2)
                                                                .coerceAtLeast(0f)
                                                val maxY =
                                                        (size.height * (scale - 1) / 2)
                                                                .coerceAtLeast(0f)

                                                // 使用安全的范围限制
                                                offset =
                                                        Offset(
                                                                x =
                                                                        if (maxX > 0)
                                                                                (offset.x + pan.x)
                                                                                        .coerceIn(
                                                                                                -maxX,
                                                                                                maxX
                                                                                        )
                                                                        else 0f,
                                                                y =
                                                                        if (maxY > 0)
                                                                                (offset.y + pan.y)
                                                                                        .coerceIn(
                                                                                                -maxY,
                                                                                                maxY
                                                                                        )
                                                                        else 0f
                                                        )
                                            }
                                        },
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(48.dp)
                                )
                            }
                        },
                        error = {
                            Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                            ) {
                                Text(
                                        stringResource(R.string.common_load_failed),
                                        color = Color.Red,
                                        style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                )

                // 顶部工具栏
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(8.dp)
                                        .align(Alignment.TopCenter)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = Color.White
                        )
                    }

                    Text(
                            text = imageAlt,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.TopCenter),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                            onClick = {
                                if (!isSaving) {
                                    isSaving = true
                                    scope.launch {
                                        try {
                                            savedSuccess = saveImageFromUrl(context, imageUrl)
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                            )
                        } else {
                            Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = stringResource(R.string.save_image),
                                    tint = Color.White
                            )
                        }
                    }
                }

                // 显示保存结果
                savedSuccess?.let { success ->
                    Box(
                            modifier =
                                    Modifier.align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                                text = if (success) stringResource(R.string.image_saved) else stringResource(R.string.save_failed),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // 重置缩放按钮
                if (scale != 1f || offset != Offset.Zero) {
                    IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    ) {
                        Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.common_reset_zoom),
                                tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/** 从URL保存图片到设备相册 */
private suspend fun saveImageFromUrl(context: Context, imageUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // 创建图片文件名
                val fileName =
                        "markdown_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"

                // 根据Android版本选择不同的保存策略
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues =
                            ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                put(
                                        MediaStore.MediaColumns.RELATIVE_PATH,
                                        "${Environment.DIRECTORY_PICTURES}/Markdown"
                                )
                            }

                    val uri =
                            context.contentResolver.insert(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    contentValues
                            )
                    uri?.let { imageUri ->
                        context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                            // 下载图片
                            val connection = java.net.URL(imageUrl).openConnection()
                            connection.connect()
                            connection.getInputStream().use { input -> input.copyTo(outputStream) }
                            return@withContext true
                        }
                    }
                } else {
                    // 较老版本的Android使用传统文件访问方式
                    val imagesDir =
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_PICTURES
                            )
                    val markdownDir = File(imagesDir, "Markdown").apply { if (!exists()) mkdirs() }

                    val imageFile = File(markdownDir, fileName)
                    FileOutputStream(imageFile).use { outputStream ->
                        // 下载图片
                        val connection = java.net.URL(imageUrl).openConnection()
                        connection.connect()
                        connection.getInputStream().use { input -> input.copyTo(outputStream) }
                        return@withContext true
                    }
                }

                return@withContext false
            } catch (e: Exception) {
                AppLogger.e(TAG, "保存图片失败", e)
                return@withContext false
            }
        }
