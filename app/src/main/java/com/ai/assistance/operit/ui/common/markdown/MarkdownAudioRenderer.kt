package com.ai.assistance.operit.ui.common.markdown

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView

private val MARKDOWN_AUDIO_EXTENSIONS =
    setOf("mp3", "wav", "ogg", "oga", "m4a", "aac", "flac", "opus", "weba")

internal fun normalizeMarkdownMediaUrl(url: String): String {
    return url
        .substringBefore('#')
        .substringBefore('?')
        .trim()
        .lowercase()
}

internal fun isLikelyAudioUrl(url: String): Boolean {
    val extension = normalizeMarkdownMediaUrl(url).substringAfterLast('.', "")
    return extension in MARKDOWN_AUDIO_EXTENSIONS
}

@Composable
fun MarkdownAudioRenderer(
    audioMarkdown: String,
    modifier: Modifier = Modifier
) {
    if (!isCompleteImageMarkdown(audioMarkdown)) {
        return
    }

    val audioAlt = extractMarkdownImageAlt(audioMarkdown)
    val audioUrl = extractMarkdownImageUrl(audioMarkdown)
    if (audioUrl.isBlank() || !isLikelyAudioUrl(audioUrl)) {
        return
    }

    val context = LocalContext.current
    val audioUri = remember(audioUrl) { Uri.parse(audioUrl) }
    val player = remember(audioUrl) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(player, audioUri) {
        player.setMediaItem(MediaItem.fromUri(audioUri))
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

    val accessibilityDesc = if (audioAlt.isNotBlank()) {
        "${stringResource(R.string.audio_block)}: $audioAlt"
    } else {
        stringResource(R.string.audio_block)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics { contentDescription = accessibilityDesc }
    ) {
        AndroidView(
            factory = { ctx ->
                StyledPlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
                .padding(vertical = 8.dp)
        )

        if (audioAlt.isNotBlank()) {
            Text(
                text = audioAlt,
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
