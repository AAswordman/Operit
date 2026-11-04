package com.ai.assistance.operit.ui.common.markdown

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView

private val MARKDOWN_VIDEO_EXTENSIONS =
    setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "avi", "ogv")

internal fun isLikelyVideoUrl(url: String): Boolean {
    val extension = normalizeMarkdownMediaUrl(url).substringAfterLast('.', "")
    return extension in MARKDOWN_VIDEO_EXTENSIONS
}

@Composable
fun MarkdownVideoRenderer(
    videoMarkdown: String,
    modifier: Modifier = Modifier,
    maxVideoHeight: Int = 220
) {
    if (!isCompleteImageMarkdown(videoMarkdown)) {
        return
    }

    val videoAlt = extractMarkdownImageAlt(videoMarkdown)
    val videoUrl = extractMarkdownImageUrl(videoMarkdown)
    if (videoUrl.isBlank() || !isLikelyVideoUrl(videoUrl)) {
        return
    }

    val context = LocalContext.current
    val videoUri = remember(videoUrl) { Uri.parse(videoUrl) }
    val player = remember(videoUrl) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(player, videoUri) {
        player.setMediaItem(MediaItem.fromUri(videoUri))
        player.prepare()
        player.playWhenReady = false
    }

    DisposableEffect(player) {
        onDispose {
            try {
                player.stop()
                player.clearMediaItems()
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    val accessibilityDesc = if (videoAlt.isNotBlank()) {
        "${stringResource(R.string.video_block)}: $videoAlt"
    } else {
        stringResource(R.string.video_block)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics { contentDescription = accessibilityDesc }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
        ) {
            AndroidView(
                factory = { ctx ->
                    StyledPlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view ->
                    if (view.player !== player) {
                        view.player = player
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .heightIn(max = maxVideoHeight.dp)
            )
        }

        if (videoAlt.isNotBlank()) {
            Text(
                text = videoAlt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
