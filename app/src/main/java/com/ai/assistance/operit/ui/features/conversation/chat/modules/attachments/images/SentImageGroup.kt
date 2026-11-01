package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.MediaFileUnavailableTag
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.mediaPreviewUri
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.rememberMediaPreviewRevision
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.media.ImagePoolManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SentMessageImage(val id: String, val title: String? = null, val poolId: String? = null)
private data class ResolvedSentImage(val source: SentMessageImage, val uri: Uri?)

/** 图片组读取原文件；池链接只用于兼容没有原文件描述的旧消息。 */
@Composable
internal fun SentImageGroup(sources: List<SentMessageImage>, enabled: Boolean, modifier: Modifier = Modifier) {
    if (sources.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val poolRevision by ImagePoolManager.revision.collectAsState()
    val previewRevision = rememberMediaPreviewRevision()
    var refresh by remember(sources) { mutableIntStateOf(0) }
    var failedIds by remember(sources) { mutableStateOf(emptySet<String>()) }
    var previewImages by remember(sources) { mutableStateOf(emptyList<AttachmentPreviewImage>()) }

    suspend fun resolveSources(): List<ResolvedSentImage> = withContext(Dispatchers.IO) {
        sources.map { source ->
            val uri = try {
                if (source.poolId != null) {
                    ImagePoolManager.getPreviewFile(source.poolId)?.let(Uri::fromFile)
                } else {
                    mediaPreviewUri(context, source.id)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("SentImageGroup", "读取已发送图片失败：${source.id}", error)
                null
            }
            ResolvedSentImage(source, uri)
        }
    }

    val state by produceState<List<ResolvedSentImage>?>(
        initialValue = null,
        key1 = sources,
        key2 = poolRevision,
        key3 = previewRevision to refresh,
    ) {
        value = null
        value = resolveSources()
    }
    val images = state.orEmpty().filter { it.uri != null && it.source.id !in failedIds }
    if (enabled && previewImages.isNotEmpty()) {
        AttachmentImagePreviewDialog(
            images = previewImages,
            initialImageUri = previewImages.first().uri,
            onDismiss = { previewImages = emptyList(); refresh++ },
        )
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
        if (state == null) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp))
            }
        } else if (images.isNotEmpty()) {
            val layers = images.take(3)
            Box(Modifier.width((112 + (layers.size - 1) * 10).dp).height(122.dp).clickable(enabled = enabled) {
                scope.launch {
                    // 点击时绕过缩略图缓存核实原文件，删除后不再打开空白图片页。
                    val latest = resolveSources()
                    if (latest.any { it.uri == null }) {
                        refresh++
                        OKToast.warning(context, context.getString(R.string.chat_media_file_missing)).show()
                    }
                    previewImages = latest.filter { it.uri != null && it.source.id !in failedIds }
                        .map { AttachmentPreviewImage(requireNotNull(it.uri), it.source.title) }
                }
            }) {
                layers.indices.reversed().forEach { index ->
                    val image = layers[index]
                    var loading by remember(image.source.id) { mutableStateOf(true) }
                    Surface(
                        modifier = Modifier.offset(x = (index * 10).dp, y = (index * 4).dp).size(112.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 1.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = image.uri,
                                contentDescription = image.source.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onLoading = { loading = true },
                                onSuccess = { loading = false },
                                onError = {
                                    failedIds = failedIds + image.source.id
                                    refresh++
                                },
                            )
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp))
                        }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ) {
                    Text(
                        stringResource(R.string.sent_image_count, images.size),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        // 同一条消息的缺失图片只有一个入口，避免重复列出无法预览的文件名。
        val expiredCount = state.orEmpty().count { it.uri == null }
        if (expiredCount > 0) {
            ExpiredImageGroupTag(count = expiredCount, enabled = enabled)
        }
        state.orEmpty().filter { it.uri != null && it.source.id in failedIds }.forEach { image ->
            MediaFileUnavailableTag(image.source.title, missing = false)
        }
    }
}
