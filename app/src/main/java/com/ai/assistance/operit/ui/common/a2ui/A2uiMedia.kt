package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.ai.assistance.operit.R

/** Creating a decoder/player is gated by an explicit click for this particular URL. */
@Composable
internal fun CardMedia(kind: String, url: String, title: String, modifier: Modifier = Modifier) {
    var requested by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var visible by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val enabled = LocalCardBrowse.current
    Column(modifier.fillMaxWidth().onGloballyPositioned {
        val bounds = it.boundsInWindow()
        visible = bounds.width > 0 && bounds.height > 0
    }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelLarge)
        if (!A2uiProtocol.safeUrl(url)) Text(stringResource(R.string.a2ui_bad_url), color = MaterialTheme.colorScheme.error)
        else if (!requested) OutlinedButton(
            onClick = { requested = true; failed = false }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (kind == "Image") R.string.a2ui_load_image else R.string.a2ui_play_media)) }
        else if (kind == "Image") {
            AsyncImage(
                model = remember(url) { ImageRequest.Builder(context).data(url).size(1280, 1280).build() },
                contentDescription = title,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 300.dp),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )
        } else {
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) { failed = true }
                    })
                    prepare()
                    playWhenReady = false
                }
            }
            DisposableEffect(player, lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) player.pause()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer); player.release() }
            }
            LaunchedEffect(player, visible) { if (!visible) player.pause() }
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; useController = true; controllerShowTimeoutMs = 0 } },
                update = { it.player = player },
                modifier = Modifier.fillMaxWidth().height(if (kind == "Audio") 88.dp else 210.dp),
            )
            TextButton(onClick = { requested = false }) { Text(stringResource(R.string.a2ui_close_media)) }
        }
        if (failed) {
            Text(stringResource(R.string.a2ui_media_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { requested = false }) { Text(stringResource(R.string.a2ui_retry_media)) }
        }
    }
}
