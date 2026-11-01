package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 录音和确认共用面板外观，权限与录音会话仍由外层持有。 */
@Composable
internal fun VoiceMessageSheetContent(
    state: VoiceMessageState,
    sending: Boolean,
    onDismiss: () -> Unit,
    onFinish: () -> Unit,
    onRecordAgain: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isRecording = state.phase == VoiceMessagePhase.RECORDING
    val isPreparing = state.phase == VoiceMessagePhase.PREPARING
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.06f), Color.Transparent)))
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(42.dp).background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Mic, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(if (isRecording || isPreparing) R.string.voice_message_record_title else R.string.voice_message_confirm_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(if (isRecording || isPreparing || state.recording == null)
                            R.string.voice_message_record_hint else R.string.voice_message_confirm_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp).background(colors.onSurface.copy(alpha = 0.05f), CircleShape),
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.cancel), modifier = Modifier.size(20.dp))
                }
            }

            when (state.phase) {
                VoiceMessagePhase.RECORDING -> VoiceRecordingMeter(state)
                VoiceMessagePhase.PREPARING -> {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 190.dp).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
                        Text(stringResource(R.string.voice_message_preparing), color = colors.onSurfaceVariant)
                    }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.recording?.let { recording ->
                            VoiceMessagePlayer(
                                recording.file.absolutePath,
                                recording.durationMs,
                                recording.waveform,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        state.message?.takeIf { it.transcript.isNotBlank() }?.let { message ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.voice_message_transcript_title),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Surface(color = colors.onSurface.copy(alpha = 0.04f), shape = RoundedCornerShape(18.dp)) {
                                    Text(
                                        message.transcript,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)
                                            .verticalScroll(rememberScrollState()).padding(14.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                        state.error?.let { error ->
                            Surface(color = colors.errorContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp)) {
                                Text(error, Modifier.fillMaxWidth().padding(14.dp), color = colors.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            VoiceMessageSheetActions(state, sending, onFinish, onRecordAgain, onSend)
        }
    }
}

@Composable
private fun VoiceRecordingMeter(state: VoiceMessageState) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primary.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VoiceRecordingIndicator()
            Text(
                voiceDurationLabel(state.elapsedMs),
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
            )
            VoiceWaveform(state.levels, Modifier.fillMaxWidth().height(96.dp), live = true)
            Text(
                stringResource(R.string.voice_message_silence_hint),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VoiceRecordingIndicator() {
    val transition = rememberInfiniteTransition(label = "voice-recording")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "recording-indicator",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        // 指示灯表达录音状态，波形只表达实际采集到的音量。
        Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.error.copy(alpha = alpha), CircleShape))
        Text(stringResource(R.string.voice_message_recording), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VoiceMessageSheetActions(
    state: VoiceMessageState,
    sending: Boolean,
    onFinish: () -> Unit,
    onRecordAgain: () -> Unit,
    onSend: () -> Unit,
) {
    val phase = state.phase
    val showRecordAgain = state.recording != null && phase == VoiceMessagePhase.READY
    val busy = sending || phase == VoiceMessagePhase.PREPARING
    val action = when (phase) {
        VoiceMessagePhase.RECORDING -> onFinish
        VoiceMessagePhase.READY -> onSend
        VoiceMessagePhase.ERROR -> onRecordAgain
        else -> onFinish
    }
    val label = when {
        sending -> R.string.voice_message_sending
        phase == VoiceMessagePhase.RECORDING -> R.string.voice_message_done
        phase == VoiceMessagePhase.READY -> R.string.voice_message_send
        phase == VoiceMessagePhase.PREPARING -> R.string.voice_message_preparing
        else -> R.string.voice_message_rerecord
    }
    val icon = when (phase) {
        VoiceMessagePhase.RECORDING -> Icons.Default.Check
        VoiceMessagePhase.READY -> Icons.Default.Send
        else -> Icons.Default.Refresh
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showRecordAgain) {
            OutlinedButton(
                onClick = onRecordAgain,
                enabled = !sending,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(0.9f).heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.voice_message_rerecord), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(
            onClick = action,
            enabled = !busy,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(1.1f).heightIn(min = 52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(label), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}