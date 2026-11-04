package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.tts.SherpaOnnxVoiceProvider
import com.ai.assistance.operit.api.speech.tts.TtsService
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 按所选模型读取实际音色，避免用户猜测编号或把目录中的数量当作运行时范围。 */
@Composable
internal fun InstalledSpeechSpeakerField(modelKey: String, speakerId: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    key(modelKey) {
        var choosing by remember { mutableStateOf(false) }
        var voices by remember { mutableStateOf<List<TtsService.Voice>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var revision by remember { mutableIntStateOf(0) }
        // 空配置沿用语音服务的默认编号 0，不修改已保存的配置。
        val selected = if (speakerId.isBlank()) "0" else speakerId
        LaunchedEffect(choosing, revision) {
            if (!choosing) return@LaunchedEffect
            loading = true
            error = null
            voices = emptyList()
            try {
                voices = withContext(Dispatchers.IO) {
                    // 枚举不依赖当前编号是否有效，错误的旧配置也能重新选择。
                    val provider = SherpaOnnxVoiceProvider(context, modelKey, "", 1f, 1f)
                    try {
                        provider.getAvailableVoices()
                    } finally {
                        provider.shutdown()
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                AppLogger.e("SpeechSpeakerPicker", "读取本地模型说话人列表失败", failure)
                error = failure.toString()
            } finally {
                loading = false
            }
        }
        SpeechWorkbenchRow(
            title = stringResource(R.string.speech_services_vits_speaker_id),
            value = if (modelKey.isBlank()) stringResource(R.string.hub_select_model) else selected,
            description = stringResource(R.string.hub_speaker_picker_hint),
            enabled = modelKey.isNotBlank(),
            onClick = { choosing = true },
        )
        if (choosing) SpeechChoicePanel(
            title = stringResource(R.string.speech_services_vits_speaker_id),
            choices = voices.map { SpeechChoice(it.id, it.name) },
            selected = selected,
            loading = loading,
            error = error,
            onSelect = { onSelect(it); choosing = false },
            onDismiss = { choosing = false },
            onRefresh = { revision++ },
            footer = { Text(stringResource(R.string.hub_speaker_picker_hint)) },
        )
    }
}
