package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.MediaFileUnavailableTag
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.mediaPreviewUri
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.rememberMediaPreviewRevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 保留单图预览入口，已选附件使用同一图片页展示多图。 */
@Composable
fun AttachmentImagePreviewDialog(
    imageUri: Uri,
    title: String?,
    onDismiss: () -> Unit,
) {
    AttachmentImagePreviewDialog(
        images = listOf(AttachmentPreviewImage(imageUri, title)),
        initialImageUri = imageUri,
        onDismiss = onDismiss,
    )
}

data class AttachmentPreviewImage(val uri: Uri, val title: String?)

/** 图片页只解码当前被组合的图；双击和双指操作优先，适配视口时单指横滑翻页。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentImagePreviewDialog(
    images: List<AttachmentPreviewImage>,
    initialImageUri: Uri,
    onDismiss: () -> Unit,
) {
    require(images.isNotEmpty())
    val initialPage = remember(images, initialImageUri) {
        images.indexOfFirst { it.uri == initialImageUri }.also { require(it >= 0) }
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { images.size })
    var activeScale by remember { mutableStateOf(1f) }
    var multiTouchActive by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        activeScale = 1f
        multiTouchActive = false
    }
    LaunchedEffect(images.size) {
        if (pagerState.currentPage > images.lastIndex) pagerState.scrollToPage(images.lastIndex)
    }
    val currentPage = pagerState.currentPage.coerceIn(0, images.lastIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = images.size > 1 && activeScale <= 1f && !multiTouchActive,
                key = { page -> "${images[page].uri}#$page" },
            ) { page ->
                PreviewImagePage(
                    imageUri = images[page].uri,
                    title = images[page].title,
                    isActive = page == pagerState.currentPage,
                    onScaleChanged = { scale ->
                        if (page == pagerState.currentPage) activeScale = scale
                    },
                    onMultiTouchChanged = { active ->
                        if (page == pagerState.currentPage) multiTouchActive = active
                    },
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val title = images[currentPage].title
                if (title != null) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (images.size > 1) {
                    Text(
                        text = "${currentPage + 1} / ${images.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape),
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PreviewImagePage(
    imageUri: Uri,
    title: String?,
    isActive: Boolean,
    onScaleChanged: (Float) -> Unit,
    onMultiTouchChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision = rememberMediaPreviewRevision()
    var missing by remember(imageUri) { mutableStateOf(false) }
    val localFile = imageUri.scheme == "file" || imageUri.scheme == "content"
    LaunchedEffect(imageUri, revision) {
        missing = localFile && withContext(Dispatchers.IO) { mediaPreviewUri(context, imageUri.toString()) == null }
    }
    var viewport by remember(imageUri) { mutableStateOf(Size.Zero) }
    var imageSize by remember(imageUri) { mutableStateOf(Size.Zero) }
    var transform by remember(imageUri, viewport) { mutableStateOf(ImagePreviewTransform()) }
    var loading by remember(imageUri) { mutableStateOf(true) }
    var failed by remember(imageUri) { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (!isActive) transform = ImagePreviewTransform()
    }
    val request = remember(context, imageUri) {
        // 限制解码尺寸，附件很多时也不会同时解码整组原图。
        ImageRequest.Builder(context).data(imageUri).size(4096).build()
    }

    Box(Modifier.fillMaxSize()) {
        if (missing) {
            MediaFileUnavailableTag(title, Modifier.align(Alignment.Center).padding(24.dp))
            return@Box
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .clipToBounds()
                .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(imageUri, viewport, imageSize) {
                    detectTapGestures(
                        onDoubleTap = { focus ->
                            if (viewport.width > 0f && viewport.height > 0f &&
                                imageSize.width > 0f && imageSize.height > 0f
                            ) {
                                transform = transformImagePreview(
                                    current = transform,
                                    targetScale = if (transform.scale > 1f) 1f else 3f,
                                    focus = focus,
                                    pan = Offset.Zero,
                                    viewport = viewport,
                                    image = imageSize,
                                )
                                onScaleChanged(transform.scale)
                            }
                        },
                    )
                }
                .pointerInput(imageUri, viewport, imageSize) {
                    // 双指位移在 Initial 阶段先交给图片，避免 Pager 抢走缩放手势。
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var transforming = transform.scale > 1f
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2) {
                                    transforming = true
                                    onMultiTouchChanged(true)
                                }
                                if (transforming && pressed > 0 &&
                                    viewport.width > 0f && viewport.height > 0f &&
                                    imageSize.width > 0f && imageSize.height > 0f
                                ) {
                                    transform = transformImagePreview(
                                        current = transform,
                                        targetScale = transform.scale * event.calculateZoom(),
                                        focus = event.calculateCentroid(useCurrent = true),
                                        pan = event.calculatePan(),
                                        viewport = viewport,
                                        image = imageSize,
                                    )
                                    onScaleChanged(transform.scale)
                                }
                                if (transforming) {
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            onMultiTouchChanged(false)
                        }
                    }
                },
        ) {
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.image_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.offset.x
                    translationY = transform.offset.y
                },
                onLoading = { loading = true; failed = false },
                onSuccess = {
                    loading = false
                    failed = false
                    imageSize = it.painter.intrinsicSize
                },
                onError = {
                    loading = false
                    failed = true
                    scope.launch {
                        missing = localFile && withContext(Dispatchers.IO) {
                            mediaPreviewUri(context, imageUri.toString()) == null
                        }
                    }
                },
            )
        }
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        if (failed) {
            MediaFileUnavailableTag(title, Modifier.align(Alignment.Center).padding(24.dp), missing = false)
        }
    }
}