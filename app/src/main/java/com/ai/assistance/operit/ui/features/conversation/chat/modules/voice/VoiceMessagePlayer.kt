package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.MediaFileGuard
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.rememberMediaFileMissing
import com.ai.assistance.operit.util.logging.AppLogger
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private object VoicePlaybackCoordinator {
    var stopCurrent: (() -> Unit)? = null
}

internal fun voiceDurationLabel(milliseconds: Long): String =
    String.format(Locale.ROOT, "%02d:%02d", milliseconds / 60000, milliseconds / 1000 % 60)

/** 短语音使用短条，时长增加只在有限范围内增加宽度。 */
private fun voiceMessageWidth(durationMs: Long) =
    (168f + durationMs.coerceIn(0L, 60000L) / 1000f).dp

private fun voiceCompactDurationLabel(durationMs: Long): String {
    val seconds = maxOf(1L, durationMs / 1000L)
    return if (seconds < 60) "${seconds}″" else "${seconds / 60}′${(seconds % 60).toString().padStart(2, '0')}″"
}

/** 点击时才创建播放器，历史列表中的未播放语音条不会分配播放资源。 */
@Composable
fun VoiceMessagePlayer(
    filePath: String,
    durationMs: Long,
    waveform: List<Float>,
    transcript: String = "",
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    missingState: MutableState<Boolean> = rememberMediaFileMissing(filePath, "audio/wav"),
) {
    MediaFileGuard(filePath, File(filePath).name, "audio/wav", modifier, missingState) { checkFile ->
        VoiceMessagePlayerContent(filePath, durationMs, waveform, transcript, modifier, embedded, contentColor, checkFile)
    }
}

@Composable
private fun VoiceMessagePlayerContent(
    filePath: String,
    durationMs: Long,
    waveform: List<Float>,
    transcript: String = "",
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    contentColor: Color,
    checkFile: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember(filePath) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember(filePath) { mutableStateOf(false) }
    var preparing by remember(filePath) { mutableStateOf(false) }
    var position by remember(filePath) { mutableStateOf(0L) }
    var error by remember(filePath) { mutableStateOf(false) }
    var expired by remember(filePath) { mutableStateOf(false) }
    var showText by remember(filePath) { mutableStateOf(false) }
    val stop = remember(filePath) {
        {
            player?.release()
            player = null
            playing = false
            preparing = false
            position = 0L
        }
    }
    DisposableEffect(lifecycleOwner, filePath) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (VoicePlaybackCoordinator.stopCurrent === stop) VoicePlaybackCoordinator.stopCurrent = null
            stop()
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = player?.currentPosition ?: 0L
            delay(150)
        }
    }
    fun toggleAvailable() {
        if (preparing) return
        if (playing) { player?.pause(); return }
        error = false
        expired = false
        if (!File(filePath).isFile) { checkFile {}; return }
        if (player == null) {
            VoicePlaybackCoordinator.stopCurrent?.invoke()
            VoicePlaybackCoordinator.stopCurrent = stop
            preparing = true
            val created = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
                setHandleAudioBecomingNoisy(true)
            }
            player = created
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    preparing = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_ENDED) { playing = false; position = 0 }
                }
                override fun onPlayerError(playbackError: PlaybackException) {
                    AppLogger.e("VoiceMessagePlayer", "播放语音消息失败", playbackError)
                    expired = !File(filePath).isFile
                    error = true
                    checkFile {}
                    stop()
                }
            })
            created.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
            created.prepare()
        }
        val active = player ?: return
        if (active.playbackState == Player.STATE_ENDED) active.seekTo(0)
        active.play()
    }
    fun toggle() { checkFile { toggleAvailable() } }
    val playAction = stringResource(if (playing) R.string.voice_message_pause else R.string.voice_message_play)
    val textAction = stringResource(if (showText) R.string.voice_message_hide_text else R.string.voice_message_show_text)
    Surface(
        modifier = modifier.animateContentSize().width(if (showText) 280.dp else voiceMessageWidth(durationMs)),
        shape = RoundedCornerShape(18.dp),
        color = if (embedded) Color.Transparent else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(horizontal = if (embedded) 0.dp else 12.dp, vertical = if (embedded) 0.dp else 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !preparing, role = Role.Button, onClickLabel = playAction, onClick = ::toggle),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(30.dp).background(contentColor.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (preparing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = contentColor)
                        else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            playAction, tint = contentColor, modifier = Modifier.size(20.dp))
                    }
                    VoiceWaveform(waveform, Modifier.weight(1f).height(28.dp),
                        progress = if (durationMs > 0) position.toFloat() / durationMs else 0f, color = contentColor)
                    Text(voiceCompactDurationLabel(durationMs), style = MaterialTheme.typography.labelLarge,
                        color = contentColor, maxLines = 1)
                }
                if (transcript.isNotBlank()) {
                    Box(Modifier.width(1.dp).height(16.dp).background(contentColor.copy(alpha = 0.15f)))
                    Box(
                        Modifier.heightIn(min = 44.dp).widthIn(min = 28.dp).clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClickLabel = textAction) { showText = !showText }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(if (showText) R.string.voice_message_collapse_text else R.string.voice_message_text),
                            color = contentColor.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (error) Text(stringResource(if (expired) R.string.voice_message_expired else R.string.voice_message_play_failed), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (showText && transcript.isNotBlank()) {
                Column(Modifier.padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(contentColor.copy(alpha = 0.12f)))
                    Text(transcript, style = MaterialTheme.typography.bodyMedium, color = contentColor)
                }
            }
        }
    }
}
