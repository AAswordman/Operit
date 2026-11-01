package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.tts.TtsService
import com.ai.assistance.operit.api.speech.tts.TtsServiceFactory
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.speech.text.TtsCleaner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.api.speech.runtime.SpeechServiceDisposal
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private class SynthesisTestState {
    var service by mutableStateOf<TtsService?>(null)
    var preparing by mutableStateOf(false)
    var speaking by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var command: Job? = null
    var generation = 0

    fun release() {
        generation++
        val pending = command
        val retiring = service
        pending?.cancel()
        command = null
        service = null
        preparing = false
        speaking = false
        // 先解除界面引用，再在独立 IO 协程等待原生任务结束，禁止在滑块重组时等待推理锁。
        if (pending != null || retiring != null) SpeechServiceDisposal.submit("SpeechSynthesisTest") {
            try {
                pending?.join()
            } finally {
                retiring?.shutdown()
            }
        }
    }
}

/** 试听拥有独立实例，配置变化、切换标签、离页和退到后台均结束本次播放。 */
@Composable
internal fun SpeechSynthesisTestSection(draft: TtsProfileDraft) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val scope = rememberCoroutineScope()
    val defaultText = stringResource(R.string.speech_workbench_sample_text)
    var text by rememberSaveable { mutableStateOf(defaultText) }
    val state = remember(draft) { SynthesisTestState() }
    val service = state.service
    LaunchedEffect(service) {
        service?.speakingStateFlow?.collect { state.speaking = it }
    }
    DisposableEffect(state, lifecycleOwner, isCurrentScreen) {
        if (!isCurrentScreen) state.release()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.release()
        }
    }
    SpeechWorkbenchCard {
        Text(stringResource(R.string.speech_workbench_preview_title), style = MaterialTheme.typography.titleSmall)
        SpeechWorkbenchField(stringResource(R.string.tts_input_text), text, { text = it }, multiline = true)
        Text(
            stringResource(when {
                state.preparing -> R.string.speech_workbench_preparing
                state.speaking -> R.string.tts_playing
                else -> R.string.speech_workbench_preview_hint
            }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        val active = state.preparing || state.speaking
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = isCurrentScreen && (active || text.isNotBlank()),
            onClick = {
                if (active) {
                    val playingService = state.service
                    state.generation++
                    val generation = state.generation
                    state.command?.cancel()
                    state.preparing = true
                    state.command = scope.launch {
                        try {
                            withContext(Dispatchers.IO) { playingService?.stop() }
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            AppLogger.e("SpeechSynthesisTest", "停止试听失败", failure)
                            if (generation == state.generation) state.error = context.getString(R.string.tts_stop_error, failure.localizedMessage.orEmpty())
                        } finally {
                            if (generation == state.generation) state.release()
                        }
                    }
                } else {
                    state.release()
                    state.error = null
                    state.preparing = true
                    val generation = state.generation
                    state.command = scope.launch {
                        try {
                            val profile = draft.toProfile()
                            val cleaned = TtsCleaner.clean(text, profile.cleanerRegexs).trim()
                            require(cleaned.isNotBlank()) { context.getString(R.string.tts_input_empty) }
                            val created = TtsServiceFactory.createVoiceService(context.applicationContext, profile)
                            state.service = created
                            check(created.initialize()) { context.getString(R.string.tts_init_failed) }
                            ensureActive()
                            check(created.speak(cleaned, interrupt = true, rate = profile.speechRate, pitch = profile.pitch)) { context.getString(R.string.tts_speak_failed) }
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            AppLogger.e("SpeechSynthesisTest", "朗读试听失败", failure)
                            if (generation == state.generation) {
                                state.error = failure.localizedMessage ?: failure.javaClass.simpleName
                                state.release()
                            }
                        } finally {
                            if (generation == state.generation) state.preparing = false
                        }
                    }
                }
            },
        ) {
            Icon(if (active) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (active) R.string.tts_stop_speech else R.string.speech_workbench_preview_play))
        }
    }
}
