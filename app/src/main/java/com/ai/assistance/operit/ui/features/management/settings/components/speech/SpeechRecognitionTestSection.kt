package com.ai.assistance.operit.ui.features.management.settings.components.speech

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptFilledTonalButton as FilledTonalButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.stt.SttService
import com.ai.assistance.operit.api.speech.stt.SttServiceFactory
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences.SttHttpConfig
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.*
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechChoice
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechChoicePanel
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWorkbenchRow

private class RecognitionTestState {
    var service: SttService? by mutableStateOf(null)
    var initialized by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var changing by mutableStateOf(false)
    var ownsRecording by mutableStateOf(false)
    var text by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var language by mutableStateOf("zh-CN")
    var languages by mutableStateOf<List<String>>(emptyList())
    var commandJob: Job? = null

    fun updateRecognitionState(service: SttService, recognitionState: SttService.RecognitionState) {
        busy = recognitionState == SttService.RecognitionState.PREPARING ||
            recognitionState == SttService.RecognitionState.RECOGNIZING ||
            recognitionState == SttService.RecognitionState.PROCESSING
        if (!busy && ownsRecording && !changing) {
            // 最终结果与空闲状态可能先后到达，结束时读取同一次测试的最后结果。
            text = service.recognitionResultFlow.value.text
            ownsRecording = false
        }
    }
}

/** 在当前识别配置下测试录音，配置变化或离开页面时释放本次测试。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechRecognitionTestSection(
    profileId: String,
    serviceType: SttServiceFactory.SpeechServiceType,
    httpConfig: SttHttpConfig,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    val state = remember(profileId, serviceType, httpConfig, hasPermission, isCurrentScreen) { RecognitionTestState() }

    LaunchedEffect(state) {
        if (!hasPermission || !isCurrentScreen) return@LaunchedEffect
        var created: SttService? = null
        try {
            val service = SttServiceFactory.createSpeechService(context.applicationContext, serviceType, httpConfig)
            created = service
            state.service = service
            state.initialized = service.initialize()
            if (!state.initialized) {
                state.error = context.getString(R.string.engine_init_failed, serviceType.name)
                return@LaunchedEffect
            }
            state.languages = service.getSupportedLanguages()
            if (state.languages.isNotEmpty() && state.language !in state.languages) {
                state.language = state.languages.first()
            }
            launch {
                service.recognitionResultFlow.collect { if (state.ownsRecording) state.text = it.text }
            }
            launch {
                service.recognitionErrorFlow.collect {
                    if (it.message.isNotBlank()) state.error = context.getString(R.string.recognition_error, it.message)
                }
            }
            launch {
                service.recognitionStateFlow.collect {
                    state.updateRecognitionState(service, it)
                }
            }
            awaitCancellation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("SpeechRecognitionTest", "识别测试初始化失败", error)
            state.error = context.getString(R.string.recognition_error, error.message.orEmpty())
        } finally {
            state.commandJob?.cancel()
            withContext(NonCancellable) {
                state.commandJob?.join()
                try {
                    // 本地识别服务可能由唤醒功能共享，只取消由测试区发起的录音。
                    if (state.ownsRecording) withContext(Dispatchers.IO) { created?.cancelRecognition() }
                } catch (error: Exception) {
                    AppLogger.e("SpeechRecognitionTest", "取消识别测试失败", error)
                } finally {
                    try {
                        withContext(Dispatchers.IO) { created?.shutdown() }
                    } catch (error: Exception) {
                        AppLogger.e("SpeechRecognitionTest", "释放识别测试服务失败", error)
                    } finally {
                        state.service = null
                        state.initialized = false
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val pendingCommand = state.commandJob
                pendingCommand?.cancel()
                scope.launch {
                    try {
                        pendingCommand?.join()
                        if (state.ownsRecording) withContext(Dispatchers.IO) { state.service?.cancelRecognition() }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLogger.e("SpeechRecognitionTest", "停止后台识别测试失败", error)
                        state.error = context.getString(R.string.stop_recognition_error, error.message.orEmpty())
                    } finally {
                        state.ownsRecording = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var languagePanel by remember(state) { mutableStateOf(false) }
    SpeechWorkbenchCard(modifier) {
        Text(stringResource(R.string.speech_workbench_stt_preview_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.speech_services_test_stt_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasPermission) {
            Text(stringResource(R.string.microphone_permission_required), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(stringResource(R.string.request_microphone_permission))
            }
        } else {
            SpeechWorkbenchRow(
                title = stringResource(R.string.recognition_language),
                value = state.language,
                onClick = { languagePanel = true },
                enabled = state.initialized && !state.busy && !state.changing,
            )
            val recording = state.ownsRecording && state.busy
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.initialized && !state.changing && (!state.busy || state.ownsRecording),
                onClick = {
                    val service = state.service ?: return@Button
                    if (recording) {
                        state.changing = true
                        state.commandJob = scope.launch {
                            try {
                                if (!withContext(Dispatchers.IO) { service.stopRecognition() }) state.error = context.getString(R.string.stop_recognition_error, serviceType.name)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                state.error = context.getString(R.string.stop_recognition_error, error.message.orEmpty())
                                AppLogger.e("SpeechRecognitionTest", "停止识别测试失败", error)
                            } finally {
                                state.changing = false
                                state.updateRecognitionState(service, service.currentState)
                            }
                        }
                    } else {
                        if (service.isRecognizing || service.currentState == SttService.RecognitionState.PREPARING || service.currentState == SttService.RecognitionState.PROCESSING) {
                            state.error = context.getString(R.string.speech_services_test_stt_busy)
                            return@Button
                        }
                        state.error = null
                        state.text = ""
                        state.changing = true
                        state.commandJob = scope.launch {
                            try {
                                state.ownsRecording = true
                                val local = serviceType == SttServiceFactory.SpeechServiceType.SHERPA_NCNN || serviceType == SttServiceFactory.SpeechServiceType.SHERPA_ONNX
                                if (!withContext(Dispatchers.IO) { service.startRecognition(state.language, continuousMode = local, partialResults = local) }) {
                                    state.ownsRecording = false
                                    state.error = context.getString(R.string.start_recognition_error, serviceType.name)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                state.error = context.getString(R.string.start_recognition_error, error.message.orEmpty())
                                AppLogger.e("SpeechRecognitionTest", "启动识别测试失败", error)
                            } finally {
                                state.changing = false
                                state.updateRecognitionState(service, service.currentState)
                            }
                        }
                    }
                },
            ) {
                Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (recording) R.string.stop_recognition else R.string.start_recognition))
            }
            Text(
                text = when {
                    state.busy && !state.ownsRecording -> stringResource(R.string.speech_services_test_stt_busy)
                    state.busy -> stringResource(R.string.recognizing)
                    state.initialized -> stringResource(R.string.speech_engine_initialized)
                    else -> stringResource(R.string.speech_engine_not_initialized)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recognition_result), modifier = Modifier.weight(1f))
            IconButton(enabled = state.text.isNotBlank(), onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("recognized_text", state.text))
            }) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_text))
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
        ) {
            SelectionContainer {
                Text(
                    text = state.text.ifBlank { stringResource(R.string.speech_recognition_result_placeholder) },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (languagePanel) SpeechChoicePanel(
        title = stringResource(R.string.recognition_language),
        choices = state.languages.map { SpeechChoice(it, it) },
        selected = state.language,
        onSelect = { state.language = it; languagePanel = false },
        onDismiss = { languagePanel = false },
    )
}
