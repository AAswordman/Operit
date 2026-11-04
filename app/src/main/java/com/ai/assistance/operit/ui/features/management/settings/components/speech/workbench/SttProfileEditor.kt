package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.stt.SttServiceFactory.SpeechServiceType
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences.SttProfile

@Composable
internal fun SttProfileEditor(profile: SttProfile, onChange: (SttProfile) -> Unit) {
    val context = LocalContext.current
    var engines by remember { mutableStateOf(false) }
    val type = profile.serviceType
    val config = profile.httpConfig
    val local = type == SpeechServiceType.SHERPA_NCNN || type == SpeechServiceType.SHERPA_ONNX
    val deepgram = type == SpeechServiceType.DEEPGRAM_STT
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SpeechSectionLabel(stringResource(R.string.speech_workbench_connection))
        SpeechWorkbenchCard {
            SpeechWorkbenchRow(
                stringResource(R.string.speech_services_stt_engine), stringResource(type.titleRes()), { engines = true },
                description = stringResource(if (local) R.string.speech_workbench_local else R.string.speech_workbench_online), icon = Icons.Default.Mic,
            )
            if (type == SpeechServiceType.SHERPA_ONNX) {
                InstalledSpeechModelField(com.ai.assistance.operit.data.resources.models.SpeechModelPurpose.STT, config.localModelKey) { model ->
                    onChange(profile.copy(httpConfig = config.copy(localModelKey = model.key)))
                }
            }
            if (local) {
                Text(stringResource(if (type == SpeechServiceType.SHERPA_ONNX) R.string.hub_onnx_stt_hint else R.string.speech_workbench_local_stt_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SpeechWorkbenchField(
                    stringResource(if (deepgram) R.string.speech_services_deepgram_stt_url else R.string.speech_services_openai_stt_url),
                    config.endpointUrl, { onChange(profile.copy(httpConfig = config.copy(endpointUrl = it))) },
                    placeholder = stringResource(if (deepgram) R.string.speech_services_deepgram_stt_url_placeholder else R.string.speech_services_openai_stt_url_placeholder),
                    hint = stringResource(if (deepgram) R.string.speech_services_deepgram_stt_url_hint else R.string.speech_services_openai_stt_url_hint),
                )
                SpeechWorkbenchField(
                    stringResource(R.string.speech_services_openai_stt_api_key), config.apiKey, { onChange(profile.copy(httpConfig = config.copy(apiKey = it))) }, secret = true,
                )
                SpeechWorkbenchField(
                    stringResource(R.string.speech_services_openai_stt_model), config.modelName, { onChange(profile.copy(httpConfig = config.copy(modelName = it))) },
                    placeholder = stringResource(if (deepgram) R.string.speech_services_deepgram_stt_model_placeholder else R.string.speech_services_openai_stt_model_placeholder),
                )
            }
        }
    }
    if (engines) SpeechChoicePanel(
        title = stringResource(R.string.speech_services_stt_engine),
        choices = SpeechServiceType.values().map { SpeechChoice(it.name, context.getString(it.titleRes()), context.getString(if (it == SpeechServiceType.SHERPA_NCNN || it == SpeechServiceType.SHERPA_ONNX) R.string.speech_workbench_local else R.string.speech_workbench_online)) },
        selected = type.name,
        onSelect = { onChange(profile.copy(serviceType = SpeechServiceType.valueOf(it))); engines = false },
        onDismiss = { engines = false },
    )
}
