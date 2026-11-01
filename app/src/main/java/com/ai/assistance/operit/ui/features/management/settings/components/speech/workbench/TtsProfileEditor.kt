package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.model.ModelListFetcher
import com.ai.assistance.operit.api.speech.tts.TtsServiceFactory.VoiceServiceType
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences.TtsProfile
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences.TtsHttpConfig
import com.ai.assistance.operit.ui.components.slider.WorkbenchSlider
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import com.ai.assistance.operit.api.speech.tts.DoubaoVoiceProvider
import com.ai.assistance.operit.api.speech.tts.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.api.speech.tts.MimoVoiceProvider
import com.ai.assistance.operit.api.speech.tts.OpenAIRealtimeVoiceProvider
import com.ai.assistance.operit.api.speech.tts.OpenAIVoiceProvider
import com.ai.assistance.operit.api.speech.tts.SiliconFlowVoiceProvider
import com.ai.assistance.operit.api.speech.tts.SimpleVoiceProvider
import com.ai.assistance.operit.api.speech.tts.TtsService
import com.ai.assistance.operit.api.speech.tts.VoiceListFetcher

@Composable
internal fun TtsProfileEditor(draft: TtsProfileDraft, onChange: (TtsProfileDraft) -> Unit) {
    val context = LocalContext.current
    val profile = draft.profile
    val config = profile.httpConfig
    val type = profile.serviceType
    val adjustments = draft.adjustmentControls()
    var enginePanel by remember { mutableStateOf(false) }
    fun updateConfig(updated: TtsHttpConfig) { onChange(draft.copy(profile = profile.copy(httpConfig = updated))) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SpeechSectionLabel(stringResource(R.string.speech_workbench_connection))
        SpeechWorkbenchCard {
            SpeechWorkbenchRow(
                stringResource(R.string.speech_services_tts_engine),
                stringResource(type.titleRes()),
                { enginePanel = true },
                description = stringResource(if (type.local) R.string.speech_workbench_local else R.string.speech_workbench_online),
                icon = Icons.Default.VolumeUp,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            when (type) {
                VoiceServiceType.SIMPLE_TTS -> SystemTtsFields(profile, ::updateConfig)
                VoiceServiceType.SHERPA_ONNX_TTS -> {
                    InstalledSpeechModelField(com.ai.assistance.operit.data.resources.models.SpeechModelPurpose.TTS, profile.vitsConfig.installedModelKey) { model ->
                        onChange(draft.copy(profile = profile.copy(vitsConfig = profile.vitsConfig.copy(installedModelKey = model.key, speakerId = "0"))))
                    }
                    InstalledSpeechSpeakerField(profile.vitsConfig.installedModelKey, profile.vitsConfig.speakerId) { speakerId ->
                        onChange(draft.copy(profile = profile.copy(vitsConfig = profile.vitsConfig.copy(speakerId = speakerId))))
                    }
                }
                VoiceServiceType.VITS_TTS -> {
                    SpeechWorkbenchField(stringResource(R.string.speech_services_vits_package_path), profile.vitsConfig.packagePath, { onChange(draft.copy(profile = profile.copy(vitsConfig = profile.vitsConfig.copy(packagePath = it)))) }, placeholder = stringResource(R.string.speech_services_vits_package_path_placeholder), hint = stringResource(R.string.speech_services_vits_package_path_hint))
                    SpeechWorkbenchField(stringResource(R.string.speech_services_vits_speaker_id), profile.vitsConfig.speakerId, { onChange(draft.copy(profile = profile.copy(vitsConfig = profile.vitsConfig.copy(speakerId = it)))) }, hint = stringResource(R.string.speech_services_vits_speaker_id_hint))
                }
                else -> {
                    if (type != VoiceServiceType.SILICONFLOW_TTS) SpeechWorkbenchField(
                        stringResource(type.urlLabel()), config.urlTemplate, { updateConfig(config.copy(urlTemplate = it)) },
                        placeholder = stringResource(type.urlPlaceholder()),
                        hint = if (type == VoiceServiceType.HTTP_TTS) "" else stringResource(type.urlHint()),
                    )
                    SpeechWorkbenchField(
                        stringResource(if (type == VoiceServiceType.DOUBAO_TTS) R.string.speech_services_doubao_token else R.string.speech_services_http_api_key),
                        config.apiKey, { updateConfig(config.copy(apiKey = it)) }, secret = true,
                    )
                    if (type != VoiceServiceType.HTTP_TTS) OnlineTtsVoiceFields(profile, ::updateConfig)
                }
            }
        }
        if (type == VoiceServiceType.HTTP_TTS) SpeechWorkbenchCard {
            SpeechWorkbenchFold(stringResource(R.string.advanced_settings), stringResource(R.string.speech_workbench_http_advanced_hint)) {
                var methodPanel by remember { mutableStateOf(false) }
                SpeechWorkbenchRow(stringResource(R.string.speech_services_http_method), config.httpMethod, { methodPanel = true })
                if (methodPanel) SpeechChoicePanel(
                    title = stringResource(R.string.speech_services_http_method),
                    choices = listOf(SpeechChoice("GET", "GET"), SpeechChoice("POST", "POST")),
                    selected = config.httpMethod,
                    onSelect = { updateConfig(config.copy(httpMethod = it)); methodPanel = false },
                    onDismiss = { methodPanel = false },
                )
                SpeechWorkbenchField(stringResource(R.string.speech_services_http_content_type), config.contentType, { updateConfig(config.copy(contentType = it)) })
                SpeechWorkbenchField(
                    stringResource(R.string.speech_services_http_headers), draft.headers, { onChange(draft.copy(headers = it)) }, multiline = true,
                    error = if (runCatching { parseStringMap(draft.headers) }.isFailure) stringResource(R.string.speech_services_http_headers_error) else null,
                )
                if (config.httpMethod == "POST") SpeechWorkbenchField(
                    stringResource(R.string.speech_services_http_request_body), config.requestBody, { updateConfig(config.copy(requestBody = it)) }, multiline = true, placeholder = stringResource(R.string.speech_services_http_request_body_placeholder),
                )
                SpeechWorkbenchField(
                    stringResource(R.string.speech_services_http_response_pipeline), draft.responsePipeline, { onChange(draft.copy(responsePipeline = it)) }, multiline = true,
                    hint = stringResource(R.string.speech_services_http_response_pipeline_hint),
                    error = if (runCatching { HttpTtsResponsePipelineStep.parseList(draft.responsePipeline) }.isFailure) stringResource(R.string.speech_services_http_response_pipeline_error) else null,
                )
            }
        }
        if (type == VoiceServiceType.VITS_TTS) SpeechWorkbenchCard {
            SpeechWorkbenchFold(stringResource(R.string.speech_services_vits_options), stringResource(R.string.speech_services_vits_options_hint)) {
                SpeechWorkbenchField(
                    stringResource(R.string.speech_services_vits_options), draft.options, { onChange(draft.copy(options = it)) }, multiline = true,
                    error = if (runCatching { parseStringMap(draft.options) }.isFailure) stringResource(R.string.speech_services_vits_options_error) else null,
                )
            }
        }
        SpeechSectionLabel(stringResource(R.string.speech_workbench_voice_style))
        SpeechWorkbenchCard {
            if (adjustments.rate || adjustments.pitch) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (adjustments.rate) Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.speech_services_tts_speech_rate_value, profile.speechRate), style = MaterialTheme.typography.bodySmall)
                        WorkbenchSlider(value = profile.speechRate, onValueChange = { onChange(draft.copy(profile = profile.copy(speechRate = it))) }, valueRange = 0.5f..2.0f, steps = 5)
                    }
                    if (adjustments.pitch) Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.speech_services_tts_pitch_value, profile.pitch), style = MaterialTheme.typography.bodySmall)
                        WorkbenchSlider(value = profile.pitch, onValueChange = { onChange(draft.copy(profile = profile.copy(pitch = it))) }, valueRange = 0.5f..2.0f, steps = 5)
                    }
                }
                if (type == VoiceServiceType.MIMO_TTS) Text(
                    stringResource(R.string.speech_workbench_mimo_adjustment_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            TtsCleanerEditor(profile.cleanerRegexs) { onChange(draft.copy(profile = profile.copy(cleanerRegexs = it))) }
        }
    }
    if (enginePanel) SpeechChoicePanel(
        title = stringResource(R.string.speech_services_tts_engine),
        choices = VoiceServiceType.values().map { SpeechChoice(it.name, context.getString(it.titleRes()), context.getString(if (it.local) R.string.speech_workbench_local else R.string.speech_workbench_online)) },
        selected = type.name,
        onSelect = { name ->
            val selected = VoiceServiceType.valueOf(name)
            val selectedConfig = if (selected == VoiceServiceType.DOUBAO_TTS && type != selected) config.copy(urlTemplate = DoubaoVoiceProvider.DEFAULT_ENDPOINT_URL, voiceId = DoubaoVoiceProvider.DEFAULT_VOICE_ID, contentType = "application/json") else config
            onChange(draft.copy(profile = profile.copy(serviceType = selected, httpConfig = selectedConfig)))
            enginePanel = false
        },
        onDismiss = { enginePanel = false },
    )
}

@Composable
private fun SystemTtsFields(profile: TtsProfile, onChange: (TtsHttpConfig) -> Unit) {
    val context = LocalContext.current
    val config = profile.httpConfig
    var panel by remember { mutableStateOf<String?>(null) }
    var voices by remember { mutableStateOf<List<TtsService.Voice>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    LaunchedEffect(panel, revision) {
        if (panel == null) return@LaunchedEffect
        loading = true
        error = null
        var provider: SimpleVoiceProvider? = null
        try {
            provider = SimpleVoiceProvider(context.applicationContext, config.localeTag, config.voiceId)
            voices = provider.getAvailableVoices().distinctBy { it.id }
        } catch (failure: CancellationException) { throw failure
        } catch (failure: Exception) {
            AppLogger.e("SpeechTtsChoices", "读取系统音色失败", failure)
            error = failure.localizedMessage ?: failure.javaClass.simpleName
        } finally { provider?.shutdown(); loading = false }
    }
    SpeechWorkbenchRow(stringResource(R.string.speech_services_simple_tts_locale), config.localeTag.ifBlank { stringResource(R.string.speech_services_simple_tts_locale_follow_system) }, { panel = "language" })
    SpeechWorkbenchRow(stringResource(R.string.speech_services_simple_tts_voice), voices.firstOrNull { it.id == config.voiceId }?.name ?: config.voiceId.ifBlank { stringResource(R.string.speech_services_simple_tts_voice_default) }, { panel = "voice" })
    val selection = panel
    if (selection != null) {
        val choices = if (selection == "language") listOf(SpeechChoice("", context.getString(R.string.speech_services_simple_tts_locale_follow_system))) + voices.map { it.locale }.filter(String::isNotBlank).distinct().sorted().map { SpeechChoice(it, it) }
        else listOf(SpeechChoice("", context.getString(R.string.speech_services_simple_tts_voice_default))) + voices.filter { config.localeTag.isBlank() || it.locale.equals(config.localeTag, true) }.map { SpeechChoice(it.id, it.name, "${it.locale} · ${it.id}") }
        SpeechChoicePanel(
            title = stringResource(if (selection == "language") R.string.speech_services_simple_tts_locale else R.string.speech_services_simple_tts_voice_select),
            choices = choices, selected = if (selection == "language") config.localeTag else config.voiceId,
            loading = loading, error = error, onRefresh = { revision++ },
            manualValue = if (selection == "language") config.localeTag else config.voiceId,
            onSelect = { value ->
                if (selection == "language") onChange(config.copy(localeTag = value, voiceId = if (value.equals(config.localeTag, true)) config.voiceId else ""))
                else {
                    val selectedVoice = voices.firstOrNull { it.id == value }
                    val locale = if (config.localeTag.isBlank() && selectedVoice != null) selectedVoice.locale else config.localeTag
                    onChange(config.copy(voiceId = value, localeTag = locale))
                }
                panel = null
            }, onDismiss = { panel = null },
        )
    }
}

@Composable
private fun OnlineTtsVoiceFields(profile: TtsProfile, onChange: (TtsHttpConfig) -> Unit) {
    val context = LocalContext.current
    val config = profile.httpConfig
    val type = profile.serviceType
    var panel by remember(type) { mutableStateOf<String?>(null) }
    var remoteVoices by remember(type, config.urlTemplate, config.apiKey) { mutableStateOf<List<TtsService.Voice>>(emptyList()) }
    var models by remember(type, config.urlTemplate, config.apiKey) { mutableStateOf<List<SpeechChoice>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember(type, config.urlTemplate, config.apiKey) { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    val presets = remember(type) { when (type) {
        VoiceServiceType.SILICONFLOW_TTS -> SiliconFlowVoiceProvider.getAvailableVoices(context)
        VoiceServiceType.MIMO_TTS -> MimoVoiceProvider.AVAILABLE_VOICES
        VoiceServiceType.DOUBAO_TTS -> DoubaoVoiceProvider.AVAILABLE_VOICES
        VoiceServiceType.OPENAI_WS_TTS -> OpenAIRealtimeVoiceProvider.AVAILABLE_VOICES
        VoiceServiceType.OPENAI_TTS -> OpenAIVoiceProvider.AVAILABLE_VOICES
        else -> emptyList()
    } }
    LaunchedEffect(panel, revision, type, config.urlTemplate, config.apiKey) {
        error = null
        if (panel == null || type != VoiceServiceType.OPENAI_TTS || config.urlTemplate.isBlank()) return@LaunchedEffect
        loading = true
        try {
            if (panel == "model") models = ModelListFetcher.getModelsList(context = context, apiKey = config.apiKey, apiEndpoint = config.urlTemplate, apiProviderType = ApiProviderType.OPENAI_GENERIC).getOrThrow().map { SpeechChoice(it.id, it.name, it.id) }
            else remoteVoices = VoiceListFetcher.getVoicesList(apiKey = config.apiKey, ttsEndpointUrl = config.urlTemplate).getOrThrow()
        } catch (failure: CancellationException) { throw failure
        } catch (failure: Exception) {
            AppLogger.e("SpeechTtsChoices", "读取服务模型或音色失败", failure)
            error = failure.localizedMessage ?: failure.javaClass.simpleName
        } finally { loading = false }
    }
    SpeechWorkbenchField(
        stringResource(if (type == VoiceServiceType.DOUBAO_TTS) R.string.speech_services_doubao_appid else R.string.speech_services_openai_model),
        config.modelName, { onChange(config.copy(modelName = it)) },
        hint = when (type) {
            VoiceServiceType.SILICONFLOW_TTS -> stringResource(R.string.speech_services_siliconflow_model_name_hint)
            VoiceServiceType.MINIMAX_TTS -> stringResource(R.string.speech_services_minimax_model_name_hint)
            VoiceServiceType.MIMO_TTS -> stringResource(R.string.speech_services_mimo_model_name_hint)
            VoiceServiceType.OPENAI_WS_TTS -> stringResource(R.string.speech_services_openai_ws_model_hint)
            else -> ""
        },
        trailing = if (type == VoiceServiceType.OPENAI_TTS) { { IconButton(onClick = { panel = "model" }) { Icon(Icons.Default.List, stringResource(R.string.speech_services_openai_model_select), Modifier.size(20.dp)) } } } else null,
    )
    SpeechWorkbenchField(
        stringResource(R.string.speech_services_openai_voice_id), config.voiceId, { onChange(config.copy(voiceId = it)) },
        multiline = type == VoiceServiceType.MIMO_TTS,
        hint = when (type) {
            VoiceServiceType.SILICONFLOW_TTS -> stringResource(R.string.speech_services_siliconflow_voice_id_hint)
            VoiceServiceType.MINIMAX_TTS -> stringResource(R.string.speech_services_minimax_voice_id_hint)
            VoiceServiceType.MIMO_TTS -> stringResource(R.string.speech_services_mimo_voice_id_hint)
            VoiceServiceType.DOUBAO_TTS -> stringResource(R.string.speech_services_doubao_voice_id_hint)
            VoiceServiceType.OPENAI_WS_TTS -> stringResource(R.string.speech_services_openai_ws_voice_id_hint)
            else -> ""
        },
        trailing = { IconButton(onClick = { panel = "voice" }) { Icon(Icons.Default.List, stringResource(R.string.speech_services_openai_voice_select), Modifier.size(20.dp)) } },
    )
    val selection = panel
    if (selection != null) SpeechChoicePanel(
        title = stringResource(if (selection == "model") R.string.speech_services_openai_model_select else R.string.speech_services_openai_voice_select),
        choices = if (selection == "model") models else (remoteVoices + presets).distinctBy { it.id }.map { SpeechChoice(it.id, it.name, it.id) },
        selected = if (selection == "model") config.modelName else config.voiceId,
        onSelect = { if (selection == "model") onChange(config.copy(modelName = it)) else onChange(config.copy(voiceId = it)); panel = null },
        onDismiss = { panel = null },
        manualValue = if (selection == "model") config.modelName else config.voiceId,
        loading = loading, error = error, onRefresh = if (type == VoiceServiceType.OPENAI_TTS) ({ revision++ }) else null,
    )
}

@Composable
private fun TtsCleanerEditor(rules: List<String>, onChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    var templates by remember { mutableStateOf(false) }
    SpeechWorkbenchFold(
        stringResource(R.string.speech_services_tts_cleaner_title),
        stringResource(R.string.speech_workbench_cleaner_count, rules.count { it.isNotBlank() }),
    ) {
        rules.forEachIndexed { index, rule ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SpeechWorkbenchField(
                    stringResource(R.string.speech_workbench_rule_number, index + 1), rule, { value -> onChange(rules.toMutableList().apply { this[index] = value }) }, Modifier.weight(1f),
                    error = if (rule.isNotBlank() && runCatching { Regex(rule) }.isFailure) stringResource(R.string.speech_workbench_regex_error) else null,
                )
                IconButton(onClick = { onChange(rules.filterIndexed { i, _ -> i != index }) }) { Icon(Icons.Default.Delete, stringResource(R.string.speech_services_tts_cleaner_delete), Modifier.size(20.dp)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onChange(rules + "") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text(stringResource(R.string.speech_services_tts_cleaner_add)) }
            TextButton(onClick = { templates = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.speech_services_tts_cleaner_template)) }
        }
    }
    if (templates) SpeechChoicePanel(
        title = stringResource(R.string.speech_services_tts_cleaner_template),
        choices = listOf(
            SpeechChoice("\\*[^*]+\\*", context.getString(R.string.speech_services_tts_cleaner_template_asterisk)),
            SpeechChoice("\\*\\*[^*]+\\*\\*", context.getString(R.string.speech_services_tts_cleaner_template_double_asterisk)),
            SpeechChoice("\\([^)]+\\)", context.getString(R.string.speech_services_tts_cleaner_template_parenthesis)),
            SpeechChoice("（[^）]+）", context.getString(R.string.speech_services_tts_cleaner_template_chinese_parenthesis)),
            SpeechChoice("<[^>]+>", context.getString(R.string.speech_services_tts_cleaner_template_xml)),
        ), selected = "", onSelect = { onChange(rules + it); templates = false }, onDismiss = { templates = false },
    )
}

private fun VoiceServiceType.urlLabel(): Int = when (this) {
    VoiceServiceType.HTTP_TTS -> R.string.speech_services_http_url_template
    VoiceServiceType.MINIMAX_TTS -> R.string.speech_services_minimax_url
    VoiceServiceType.MIMO_TTS -> R.string.speech_services_mimo_url
    VoiceServiceType.DOUBAO_TTS -> R.string.speech_services_doubao_url
    VoiceServiceType.OPENAI_WS_TTS -> R.string.speech_services_openai_ws_url
    else -> R.string.speech_services_openai_url
}
private fun VoiceServiceType.urlPlaceholder(): Int = when (this) {
    VoiceServiceType.HTTP_TTS -> R.string.speech_services_http_url_placeholder
    VoiceServiceType.MINIMAX_TTS -> R.string.speech_services_minimax_url_placeholder
    VoiceServiceType.MIMO_TTS -> R.string.speech_services_mimo_url_placeholder
    VoiceServiceType.DOUBAO_TTS -> R.string.speech_services_doubao_url_placeholder
    VoiceServiceType.OPENAI_WS_TTS -> R.string.speech_services_openai_ws_url_placeholder
    else -> R.string.speech_services_openai_url_placeholder
}
private fun VoiceServiceType.urlHint(): Int = when (this) {
    VoiceServiceType.MINIMAX_TTS -> R.string.speech_services_minimax_url_hint
    VoiceServiceType.MIMO_TTS -> R.string.speech_services_mimo_url_hint
    VoiceServiceType.DOUBAO_TTS -> R.string.speech_services_doubao_url_hint
    VoiceServiceType.OPENAI_WS_TTS -> R.string.speech_services_openai_ws_url_hint
    else -> R.string.speech_services_openai_url_hint
}
