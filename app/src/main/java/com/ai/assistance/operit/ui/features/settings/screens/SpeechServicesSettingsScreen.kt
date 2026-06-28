package com.ai.assistance.operit.ui.features.settings.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.foundation.layout.Arrangement
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.api.voice.SiliconFlowVoiceProvider
import com.ai.assistance.operit.api.voice.MimoVoiceProvider
import com.ai.assistance.operit.api.voice.DoubaoVoiceProvider
import com.ai.assistance.operit.api.voice.OpenAIRealtimeVoiceProvider
import com.ai.assistance.operit.api.voice.OpenAIVoiceProvider
import com.ai.assistance.operit.api.voice.SimpleVoiceProvider
import com.ai.assistance.operit.api.voice.VoiceListFetcher
import com.ai.assistance.operit.api.voice.VoiceService
import com.ai.assistance.operit.api.chat.llmprovider.ModelListFetcher
import androidx.compose.runtime.LaunchedEffect

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechServicesSettingsScreen(
    onBackPressed: () -> Unit,
    onNavigateToTextToSpeech: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { SpeechServicesPreferences(context) }

    // --- State for TTS Settings ---
    val ttsServiceType by prefs.ttsServiceTypeFlow.collectAsState(initial = VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS)
    val httpConfig by prefs.ttsHttpConfigFlow.collectAsState(initial = SpeechServicesPreferences.DEFAULT_HTTP_TTS_PRESET)
    val vitsConfig by prefs.ttsVitsPackageConfigFlow.collectAsState(initial = SpeechServicesPreferences.DEFAULT_VITS_TTS_PACKAGE_CONFIG)
    val ttsCleanerRegexs by prefs.ttsCleanerRegexsFlow.collectAsState(initial = emptyList())
    val ttsSpeechRate by prefs.ttsSpeechRateFlow.collectAsState(initial = SpeechServicesPreferences.DEFAULT_TTS_SPEECH_RATE)
    val ttsPitch by prefs.ttsPitchFlow.collectAsState(initial = SpeechServicesPreferences.DEFAULT_TTS_PITCH)

    var ttsServiceTypeInput by remember(ttsServiceType) { mutableStateOf(ttsServiceType) }
    var ttsUrlTemplateInput by remember(httpConfig) { mutableStateOf(httpConfig.urlTemplate) }
    var ttsApiKeyInput by remember(httpConfig) { mutableStateOf(httpConfig.apiKey) }
    var ttsHeadersInput by remember(httpConfig) { mutableStateOf(Json.encodeToString(httpConfig.headers)) }
    var ttsHttpMethodInput by remember(httpConfig) { mutableStateOf(httpConfig.httpMethod) }
    var ttsRequestBodyInput by remember(httpConfig) { mutableStateOf(httpConfig.requestBody) }
    var ttsContentTypeInput by remember(httpConfig) { mutableStateOf(httpConfig.contentType) }
    var ttsLocaleTagInput by remember(httpConfig) { mutableStateOf(httpConfig.localeTag) }
    var ttsVoiceIdInput by remember(httpConfig) { mutableStateOf(httpConfig.voiceId) }
    var ttsModelNameInput by remember(httpConfig) { mutableStateOf(httpConfig.modelName) }
    var ttsResponsePipelineInput by remember(httpConfig) {
        mutableStateOf(HttpTtsResponsePipelineStep.encodeList(httpConfig.responsePipeline))
    }
    var vitsPackagePathInput by remember(vitsConfig) { mutableStateOf(vitsConfig.packagePath) }
    var vitsSpeakerIdInput by remember(vitsConfig) { mutableStateOf(vitsConfig.speakerId) }
    var vitsOptionsInput by remember(vitsConfig) { mutableStateOf(Json.encodeToString(vitsConfig.options)) }
    var ttsSpeechRateInput by remember(ttsSpeechRate) { mutableStateOf(ttsSpeechRate) }
    var ttsPitchInput by remember(ttsPitch) { mutableStateOf(ttsPitch) }
    var ttsHeadersJsonError by remember { mutableStateOf<String?>(null) }
    var ttsResponsePipelineJsonError by remember { mutableStateOf<String?>(null) }
    var vitsOptionsJsonError by remember { mutableStateOf<String?>(null) }
    var httpMethodDropdownExpanded by remember { mutableStateOf(false) }
    val ttsCleanerRegexsState = remember { mutableStateListOf<String>() }
    val hasHttpTtsJsonError = ttsHeadersJsonError != null || ttsResponsePipelineJsonError != null
    val hasVitsTtsJsonError = vitsOptionsJsonError != null
    var simpleTtsVoices by remember { mutableStateOf<List<VoiceService.Voice>>(emptyList()) }
    var simpleTtsVoicesLoading by remember { mutableStateOf(false) }
    var simpleTtsVoicesError by remember { mutableStateOf<String?>(null) }
    var simpleTtsLocaleExpanded by remember { mutableStateOf(false) }
    var simpleTtsShowVoiceDialog by remember { mutableStateOf(false) }

    // --- State for STT Settings ---
    val sttServiceType by prefs.sttServiceTypeFlow.collectAsState(initial = SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN)
    val sttHttpConfig by prefs.sttHttpConfigFlow.collectAsState(initial = SpeechServicesPreferences.DEFAULT_STT_HTTP_PRESET)
    var sttServiceTypeInput by remember(sttServiceType) { mutableStateOf(sttServiceType) }

    var sttEndpointUrlInput by remember(sttHttpConfig) { mutableStateOf(sttHttpConfig.endpointUrl) }
    var sttApiKeyInput by remember(sttHttpConfig) { mutableStateOf(sttHttpConfig.apiKey) }
    var sttModelNameInput by remember(sttHttpConfig) { mutableStateOf(sttHttpConfig.modelName) }

    // --- State for VAD Settings ---
    val vadConfig by prefs.vadConfigFlow.collectAsState(initial = SpeechServicesPreferences.DEFAULT_VAD_CONFIG)
    var vadModeInput by remember(vadConfig) { mutableStateOf(vadConfig.mode) }
    var vadSpeechDurationMsInput by remember(vadConfig) { mutableStateOf(vadConfig.speechDurationMs.toString()) }
    var vadSilenceDurationMsInput by remember(vadConfig) { mutableStateOf(vadConfig.silenceDurationMs.toString()) }

    // 同步 DataStore 的数据到 State
    LaunchedEffect(ttsCleanerRegexs) {
        if (ttsCleanerRegexs != ttsCleanerRegexsState.toList()) {
            ttsCleanerRegexsState.clear()
            ttsCleanerRegexsState.addAll(ttsCleanerRegexs)
        }
    }

    val hasPendingChanges =
        ttsServiceTypeInput != ttsServiceType ||
            ttsUrlTemplateInput != httpConfig.urlTemplate ||
            ttsApiKeyInput != httpConfig.apiKey ||
            ttsHeadersInput != Json.encodeToString(httpConfig.headers) ||
            ttsHttpMethodInput != httpConfig.httpMethod ||
            ttsRequestBodyInput != httpConfig.requestBody ||
            ttsContentTypeInput != httpConfig.contentType ||
            ttsLocaleTagInput != httpConfig.localeTag ||
            ttsVoiceIdInput != httpConfig.voiceId ||
            ttsModelNameInput != httpConfig.modelName ||
            ttsResponsePipelineInput != HttpTtsResponsePipelineStep.encodeList(httpConfig.responsePipeline) ||
            vitsPackagePathInput != vitsConfig.packagePath ||
            vitsSpeakerIdInput != vitsConfig.speakerId ||
            vitsOptionsInput != Json.encodeToString(vitsConfig.options) ||
            ttsCleanerRegexsState.toList() != ttsCleanerRegexs ||
            ttsSpeechRateInput != ttsSpeechRate ||
            ttsPitchInput != ttsPitch ||
            sttServiceTypeInput != sttServiceType ||
            sttEndpointUrlInput != sttHttpConfig.endpointUrl ||
            sttApiKeyInput != sttHttpConfig.apiKey ||
            sttModelNameInput != sttHttpConfig.modelName ||
            vadModeInput != vadConfig.mode ||
            vadSpeechDurationMsInput != vadConfig.speechDurationMs.toString() ||
            vadSilenceDurationMsInput != vadConfig.silenceDurationMs.toString()

    LaunchedEffect(
        ttsServiceTypeInput,
        ttsUrlTemplateInput,
        ttsApiKeyInput,
        ttsHeadersInput,
        ttsHttpMethodInput,
        ttsRequestBodyInput,
        ttsContentTypeInput,
        ttsLocaleTagInput,
        ttsVoiceIdInput,
        ttsModelNameInput,
        ttsResponsePipelineInput,
        vitsPackagePathInput,
        vitsSpeakerIdInput,
        vitsOptionsInput,
        ttsSpeechRateInput,
        ttsPitchInput,
        ttsCleanerRegexsState.toList(),
        sttServiceTypeInput,
        sttEndpointUrlInput,
        sttApiKeyInput,
        sttModelNameInput,
        vadModeInput,
        vadSpeechDurationMsInput,
        vadSilenceDurationMsInput
    ) {
        if (!hasPendingChanges) return@LaunchedEffect
        if (ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.HTTP_TTS && hasHttpTtsJsonError) return@LaunchedEffect
        if (ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.VITS_TTS && hasVitsTtsJsonError) return@LaunchedEffect

        kotlinx.coroutines.delay(500)

        if (!hasPendingChanges) return@LaunchedEffect
        if (ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.HTTP_TTS && hasHttpTtsJsonError) return@LaunchedEffect
        if (ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.VITS_TTS && hasVitsTtsJsonError) return@LaunchedEffect

        val headers = if (ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.HTTP_TTS) {
            if (ttsHeadersInput.isBlank()) {
                emptyMap()
            } else {
                try {
                    Json.decodeFromString<Map<String, String>>(ttsHeadersInput)
                } catch (_: Exception) {
                    return@LaunchedEffect
                }
            }
        } else {
            emptyMap()
        }

        val responsePipeline =
            if (ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.HTTP_TTS) {
                try {
                    HttpTtsResponsePipelineStep.parseList(ttsResponsePipelineInput)
                } catch (_: Exception) {
                    return@LaunchedEffect
                }
            } else {
                httpConfig.responsePipeline
            }

        val vitsOptions =
            if (ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.VITS_TTS) {
                if (vitsOptionsInput.isBlank()) {
                    emptyMap()
                } else {
                    try {
                        Json.decodeFromString<Map<String, String>>(vitsOptionsInput)
                    } catch (_: Exception) {
                        return@LaunchedEffect
                    }
                }
            } else {
                vitsConfig.options
            }

        val httpConfigData = SpeechServicesPreferences.TtsHttpConfig(
            urlTemplate = ttsUrlTemplateInput,
            apiKey = ttsApiKeyInput,
            headers = headers,
            httpMethod = ttsHttpMethodInput,
            requestBody = ttsRequestBodyInput,
            contentType = ttsContentTypeInput,
            localeTag = ttsLocaleTagInput,
            voiceId = ttsVoiceIdInput,
            modelName = ttsModelNameInput,
            responsePipeline = responsePipeline
        )

        val vitsConfigData = SpeechServicesPreferences.VitsTtsPackageConfig(
            packagePath = vitsPackagePathInput,
            speakerId = vitsSpeakerIdInput,
            options = vitsOptions
        )

        val sttHttpConfigData = SpeechServicesPreferences.SttHttpConfig(
            endpointUrl = sttEndpointUrlInput,
            apiKey = sttApiKeyInput,
            modelName = sttModelNameInput,
        )

        try {
            prefs.saveTtsSettings(
                serviceType = ttsServiceTypeInput,
                httpConfig = httpConfigData,
                vitsConfig = vitsConfigData,
                cleanerRegexs = ttsCleanerRegexsState.toList(),
                speechRate = ttsSpeechRateInput,
                pitch = ttsPitchInput
            )

            prefs.saveSttSettings(
                serviceType = sttServiceTypeInput,
                httpConfig = sttHttpConfigData,
            )

            VoiceServiceFactory.resetInstance()
            SpeechServiceFactory.resetInstance()
        } catch (_: Exception) {
            // Keep UI editable; auto-save retries on next change.
        }
    }

    LaunchedEffect(ttsServiceTypeInput) {
        if (ttsServiceTypeInput != VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS) return@LaunchedEffect
        simpleTtsVoicesLoading = true
        simpleTtsVoicesError = null
        var provider: SimpleVoiceProvider? = null
        try {
            provider = SimpleVoiceProvider(
                context = context.applicationContext,
                initialLocaleTag = ttsLocaleTagInput,
                initialVoiceId = ttsVoiceIdInput
            )
            simpleTtsVoices = provider.getAvailableVoices()
        } catch (e: Exception) {
            simpleTtsVoices = emptyList()
            simpleTtsVoicesError = e.message
        } finally {
            provider?.shutdown()
            simpleTtsVoicesLoading = false
        }
    }

    val simpleTtsLocaleOptions = remember(simpleTtsVoices) {
        simpleTtsVoices
            .map { it.locale.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val simpleTtsSelectedVoice = remember(simpleTtsVoices, ttsVoiceIdInput) {
        simpleTtsVoices.firstOrNull { it.id == ttsVoiceIdInput }
    }
    val simpleTtsFilteredVoices = remember(simpleTtsVoices, ttsLocaleTagInput) {
        if (ttsLocaleTagInput.isBlank()) {
            simpleTtsVoices
        } else {
            simpleTtsVoices.filter { it.locale.equals(ttsLocaleTagInput, ignoreCase = true) }
                .ifEmpty { simpleTtsVoices }
        }
    }


    CustomScaffold { paddingValues ->
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // --- TTS Section ---
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.speech_services_tts_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Text(
                            text = stringResource(R.string.speech_services_tts_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = stringResource(R.string.speech_services_service_type),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        var ttsDropdownExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = ttsDropdownExpanded,
                            onExpandedChange = { ttsDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when(ttsServiceTypeInput) {
                                    VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> stringResource(R.string.speech_services_tts_type_simple)
                                    VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> stringResource(R.string.speech_services_tts_type_http)
                                    VoiceServiceFactory.VoiceServiceType.OPENAI_WS_TTS -> stringResource(R.string.speech_services_tts_type_openai_ws)
                                    VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS -> stringResource(R.string.speech_services_tts_type_siliconflow)
                                    VoiceServiceFactory.VoiceServiceType.MINIMAX_TTS -> stringResource(R.string.speech_services_tts_type_minimax)
                                    VoiceServiceFactory.VoiceServiceType.MIMO_TTS -> stringResource(R.string.speech_services_tts_type_mimo)
                                    VoiceServiceFactory.VoiceServiceType.DOUBAO_TTS -> stringResource(R.string.speech_services_tts_type_doubao)
                                    VoiceServiceFactory.VoiceServiceType.OPENAI_TTS -> stringResource(R.string.speech_services_tts_type_openai)
                                    VoiceServiceFactory.VoiceServiceType.VITS_TTS -> stringResource(R.string.speech_services_tts_type_vits)
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.speech_services_tts_engine)) },
                                trailingIcon = { 
                                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_dropdown_expand))
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = ttsDropdownExpanded,
                                onDismissRequest = { ttsDropdownExpanded = false }
                            ) {
                                VoiceServiceFactory.VoiceServiceType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = when(type) {
                                                    VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> stringResource(R.string.speech_services_tts_type_simple)
                                                    VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> stringResource(R.string.speech_services_tts_type_http)
                                                    VoiceServiceFactory.VoiceServiceType.OPENAI_WS_TTS -> stringResource(R.string.speech_services_tts_type_openai_ws)
                                                    VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS -> stringResource(R.string.speech_services_tts_type_siliconflow)
                                                    VoiceServiceFactory.VoiceServiceType.MINIMAX_TTS -> stringResource(R.string.speech_services_tts_type_minimax)
                                                    VoiceServiceFactory.VoiceServiceType.MIMO_TTS -> stringResource(R.string.speech_services_tts_type_mimo)
                                                    VoiceServiceFactory.VoiceServiceType.DOUBAO_TTS -> stringResource(R.string.speech_services_tts_type_doubao)
                                                    VoiceServiceFactory.VoiceServiceType.OPENAI_TTS -> stringResource(R.string.speech_services_tts_type_openai)
                                                    VoiceServiceFactory.VoiceServiceType.VITS_TTS -> stringResource(R.string.speech_services_tts_type_vits)
                                                },
                                                fontWeight = if (ttsServiceTypeInput == type) FontWeight.Medium else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            ttsServiceTypeInput = type
                                            if (type == VoiceServiceFactory.VoiceServiceType.DOUBAO_TTS) {
                                                ttsUrlTemplateInput = DoubaoVoiceProvider.DEFAULT_ENDPOINT_URL
                                                ttsVoiceIdInput = DoubaoVoiceProvider.DEFAULT_VOICE_ID
                                                ttsContentTypeInput = "application/json"
                                            }
                                            ttsDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.speech_services_tts_speech_rate_value, ttsSpeechRateInput),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Slider(
                            value = ttsSpeechRateInput,
                            onValueChange = { ttsSpeechRateInput = it },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.speech_services_tts_pitch_value, ttsPitchInput),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Slider(
                            value = ttsPitchInput,
                            onValueChange = { ttsPitchInput = it },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS) {
                            Column {
                                Text(
                                    text = stringResource(R.string.speech_services_simple_tts_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = stringResource(R.string.speech_services_simple_tts_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                ExposedDropdownMenuBox(
                                    expanded = simpleTtsLocaleExpanded,
                                    onExpandedChange = { simpleTtsLocaleExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = if (ttsLocaleTagInput.isBlank()) {
                                            stringResource(R.string.speech_services_simple_tts_locale_follow_system)
                                        } else {
                                            ttsLocaleTagInput
                                        },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.speech_services_simple_tts_locale)) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = stringResource(R.string.speech_services_dropdown_expand)
                                            )
                                        },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = simpleTtsLocaleExpanded,
                                        onDismissRequest = { simpleTtsLocaleExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.speech_services_simple_tts_locale_follow_system)) },
                                            onClick = {
                                                ttsLocaleTagInput = ""
                                                simpleTtsLocaleExpanded = false
                                            }
                                        )
                                        simpleTtsLocaleOptions.forEach { localeTag ->
                                            DropdownMenuItem(
                                                text = { Text(localeTag) },
                                                onClick = {
                                                    ttsLocaleTagInput = localeTag
                                                    if (simpleTtsSelectedVoice?.locale?.equals(localeTag, ignoreCase = true) != true) {
                                                        ttsVoiceIdInput = ""
                                                    }
                                                    simpleTtsLocaleExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = simpleTtsSelectedVoice?.let { "${it.name} (${it.locale})" }
                                        ?: stringResource(R.string.speech_services_simple_tts_voice_default),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.speech_services_simple_tts_voice)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        Row {
                                            if (ttsVoiceIdInput.isNotBlank()) {
                                                IconButton(onClick = { ttsVoiceIdInput = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = stringResource(R.string.speech_services_simple_tts_voice_clear)
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { simpleTtsShowVoiceDialog = true }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                                    contentDescription = stringResource(R.string.speech_services_simple_tts_voice_select)
                                                )
                                            }
                                        }
                                    }
                                )

                                if (simpleTtsVoicesLoading) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.speech_services_simple_tts_loading),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                simpleTtsVoicesError?.let { msg ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // TTS Cleaner Regex List
                        Text(
                            text = stringResource(R.string.speech_services_tts_cleaner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        Text(
                            text = stringResource(R.string.speech_services_tts_cleaner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Column {
                            ttsCleanerRegexsState.forEachIndexed { index, regex ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = regex,
                                        onValueChange = { ttsCleanerRegexsState[index] = it },
                                        placeholder = { Text(stringResource(R.string.speech_services_tts_cleaner_placeholder)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                    IconButton(onClick = { ttsCleanerRegexsState.removeAt(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.speech_services_tts_cleaner_delete))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { ttsCleanerRegexsState.add("") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.speech_services_tts_cleaner_add))
                                }
                                
                                var showTemplateMenu by remember { mutableStateOf(false) }
                                OutlinedButton(
                                    onClick = { showTemplateMenu = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.speech_services_tts_cleaner_template))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                
                                DropdownMenu(
                                    expanded = showTemplateMenu,
                                    onDismissRequest = { showTemplateMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_asterisk)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("\\*[^*]+\\*")
                                            showTemplateMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_double_asterisk)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("\\*\\*[^*]+\\*\\*")
                                            showTemplateMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_parenthesis)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("\\([^)]+\\)")
                                            showTemplateMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_chinese_parenthesis)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("（[^）]+）")
                                            showTemplateMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.speech_services_tts_cleaner_template_xml)) },
                                        onClick = {
                                            ttsCleanerRegexsState.add("<[^>]+>")
                                            showTemplateMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.HTTP_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_http_tts_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsUrlTemplateInput,
                                    onValueChange = { ttsUrlTemplateInput = it },
                                    label = { Text(stringResource(R.string.speech_services_http_url_template)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_http_url_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_http_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_http_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsHeadersInput,
                                    onValueChange = { 
                                        ttsHeadersInput = it
                                        try {
                                            Json.decodeFromString<Map<String, String>>(it)
                                            ttsHeadersJsonError = null
                                        } catch (e: Exception) {
                                            if (it.isNotBlank() && it != "{}") {
                                                ttsHeadersJsonError = context.getString(R.string.speech_services_http_headers_error)
                                            } else {
                                                ttsHeadersJsonError = null
                                            }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.speech_services_http_headers)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_http_headers_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    isError = ttsHeadersJsonError != null
                                )

                                if (ttsHeadersJsonError != null) {
                                    Text(
                                        text = ttsHeadersJsonError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = ttsHttpMethodInput,
                                        onValueChange = { },
                                        label = { Text(stringResource(R.string.speech_services_http_method)) },
                                        readOnly = true,
                                        modifier = Modifier.weight(1f),
                                        trailingIcon = {
                                            DropdownMenu(
                                                expanded = httpMethodDropdownExpanded,
                                                onDismissRequest = { httpMethodDropdownExpanded = false }
                                            ) {
                                                listOf("GET", "POST").forEach { method ->
                                                    DropdownMenuItem(
                                                        text = { Text(method) },
                                                        onClick = {
                                                            ttsHttpMethodInput = method
                                                            httpMethodDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { httpMethodDropdownExpanded = true }) {
                                                Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_http_method_select))
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    OutlinedTextField(
                                        value = ttsContentTypeInput,
                                        onValueChange = { ttsContentTypeInput = it },
                                        label = { Text(stringResource(R.string.speech_services_http_content_type)) },
                                        placeholder = { Text(stringResource(R.string.speech_services_http_content_type_placeholder)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                if (ttsHttpMethodInput == "POST") {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = ttsRequestBodyInput,
                                        onValueChange = { ttsRequestBodyInput = it },
                                        label = { Text(stringResource(R.string.speech_services_http_request_body)) },
                                        placeholder = { Text(stringResource(R.string.speech_services_http_request_body_placeholder)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsResponsePipelineInput,
                                    onValueChange = {
                                        ttsResponsePipelineInput = it
                                        try {
                                            HttpTtsResponsePipelineStep.parseList(it)
                                            ttsResponsePipelineJsonError = null
                                        } catch (e: Exception) {
                                            ttsResponsePipelineJsonError =
                                                if (it.isBlank() || it.trim() == "[]") {
                                                    null
                                                } else {
                                                    context.getString(R.string.speech_services_http_response_pipeline_error)
                                                }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.speech_services_http_response_pipeline)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_http_response_pipeline_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 6,
                                    isError = ttsResponsePipelineJsonError != null,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_http_response_pipeline_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                if (ttsResponsePipelineJsonError != null) {
                                    Text(
                                        text = ttsResponsePipelineJsonError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.VITS_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_vits_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = vitsPackagePathInput,
                                    onValueChange = { vitsPackagePathInput = it },
                                    label = { Text(stringResource(R.string.speech_services_vits_package_path)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_vits_package_path_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_vits_package_path_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = vitsSpeakerIdInput,
                                    onValueChange = { vitsSpeakerIdInput = it },
                                    label = { Text(stringResource(R.string.speech_services_vits_speaker_id)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_vits_speaker_id_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_vits_speaker_id_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = vitsOptionsInput,
                                    onValueChange = {
                                        vitsOptionsInput = it
                                        try {
                                            if (it.isBlank()) {
                                                vitsOptionsJsonError = null
                                            } else {
                                                Json.decodeFromString<Map<String, String>>(it)
                                                vitsOptionsJsonError = null
                                            }
                                        } catch (_: Exception) {
                                            vitsOptionsJsonError = context.getString(R.string.speech_services_vits_options_error)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.speech_services_vits_options)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_vits_options_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 5,
                                    isError = vitsOptionsJsonError != null,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_vits_options_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                if (vitsOptionsJsonError != null) {
                                    Text(
                                        text = vitsOptionsJsonError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_siliconflow_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_siliconflow_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_siliconflow_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 模型设置
                                Text(
                                    text = stringResource(R.string.speech_services_siliconflow_model_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsModelNameInput,
                                    onValueChange = { ttsModelNameInput = it },
                                    label = { Text(stringResource(R.string.speech_services_siliconflow_model_name)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_siliconflow_model_name_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_siliconflow_model_name_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 音色选择
                                var voiceDropdownExpanded by remember { mutableStateOf(false) }
                                val availableVoices = remember(context) { SiliconFlowVoiceProvider.getAvailableVoices(context) }
                                val selectedVoiceName = remember(ttsVoiceIdInput) {
                                    availableVoices.find { it.id == ttsVoiceIdInput }?.name ?: context.getString(R.string.speech_services_siliconflow_voice_custom)
                                }

                                Text(
                                    text = stringResource(R.string.speech_services_siliconflow_voice_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 预设音色选择下拉菜单
                                ExposedDropdownMenuBox(
                                    expanded = voiceDropdownExpanded,
                                    onExpandedChange = { voiceDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedVoiceName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.speech_services_siliconflow_voice_select)) },
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_siliconflow_voice_select_icon))
                                        },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = voiceDropdownExpanded,
                                        onDismissRequest = { voiceDropdownExpanded = false }
                                    ) {
                                        availableVoices.forEach { voice ->
                                            DropdownMenuItem(
                                                text = { Text(voice.name) },
                                                onClick = {
                                                    ttsVoiceIdInput = voice.id
                                                    voiceDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 自定义音色ID输入框
                                OutlinedTextField(
                                    value = ttsVoiceIdInput,
                                    onValueChange = { ttsVoiceIdInput = it },
                                    label = { Text(stringResource(R.string.speech_services_siliconflow_voice_id)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_siliconflow_voice_id_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_siliconflow_voice_id_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.MINIMAX_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_minimax_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsUrlTemplateInput,
                                    onValueChange = { ttsUrlTemplateInput = it },
                                    label = { Text(stringResource(R.string.speech_services_minimax_url)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_minimax_url_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_minimax_url_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_minimax_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_minimax_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsModelNameInput,
                                    onValueChange = { ttsModelNameInput = it },
                                    label = { Text(stringResource(R.string.speech_services_minimax_model_name)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_minimax_model_name_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_minimax_model_name_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsVoiceIdInput,
                                    onValueChange = { ttsVoiceIdInput = it },
                                    label = { Text(stringResource(R.string.speech_services_minimax_voice_id)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_minimax_voice_id_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_minimax_voice_id_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.MIMO_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_mimo_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsUrlTemplateInput,
                                    onValueChange = { ttsUrlTemplateInput = it },
                                    label = { Text(stringResource(R.string.speech_services_mimo_url)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_mimo_url_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_mimo_url_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_mimo_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_mimo_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsModelNameInput,
                                    onValueChange = { ttsModelNameInput = it },
                                    label = { Text(stringResource(R.string.speech_services_mimo_model_name)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_mimo_model_name_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_mimo_model_name_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                var mimoVoiceDropdownExpanded by remember { mutableStateOf(false) }
                                val mimoVoices = remember { MimoVoiceProvider.AVAILABLE_VOICES }
                                val selectedMimoVoiceName = remember(ttsVoiceIdInput) {
                                    mimoVoices.find { it.id == ttsVoiceIdInput }?.name
                                        ?: context.getString(R.string.speech_services_mimo_voice_custom)
                                }

                                ExposedDropdownMenuBox(
                                    expanded = mimoVoiceDropdownExpanded,
                                    onExpandedChange = { mimoVoiceDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedMimoVoiceName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.speech_services_mimo_voice_select)) },
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_mimo_voice_select_icon))
                                        },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = mimoVoiceDropdownExpanded,
                                        onDismissRequest = { mimoVoiceDropdownExpanded = false }
                                    ) {
                                        mimoVoices.forEach { voice ->
                                            DropdownMenuItem(
                                                text = { Text("${voice.name} (${voice.id})") },
                                                onClick = {
                                                    ttsVoiceIdInput = voice.id
                                                    mimoVoiceDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsVoiceIdInput,
                                    onValueChange = { ttsVoiceIdInput = it },
                                    label = { Text(stringResource(R.string.speech_services_mimo_voice_id)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_mimo_voice_id_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 8,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_mimo_voice_id_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.DOUBAO_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_doubao_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsUrlTemplateInput,
                                    onValueChange = { ttsUrlTemplateInput = it },
                                    label = { Text(stringResource(R.string.speech_services_doubao_url)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_doubao_url_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_doubao_url_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsModelNameInput,
                                    onValueChange = { ttsModelNameInput = it },
                                    label = { Text(stringResource(R.string.speech_services_doubao_appid)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_doubao_appid_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_doubao_token)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_doubao_token_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                var doubaoVoiceDropdownExpanded by remember { mutableStateOf(false) }
                                val doubaoVoices = remember { DoubaoVoiceProvider.AVAILABLE_VOICES }
                                val selectedDoubaoVoiceName = remember(ttsVoiceIdInput) {
                                    doubaoVoices.find { it.id == ttsVoiceIdInput }?.name
                                        ?: context.getString(R.string.speech_services_doubao_voice_custom)
                                }

                                ExposedDropdownMenuBox(
                                    expanded = doubaoVoiceDropdownExpanded,
                                    onExpandedChange = { doubaoVoiceDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedDoubaoVoiceName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.speech_services_doubao_voice_select)) },
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_doubao_voice_select_icon))
                                        },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = doubaoVoiceDropdownExpanded,
                                        onDismissRequest = { doubaoVoiceDropdownExpanded = false }
                                    ) {
                                        doubaoVoices.forEach { voice ->
                                            DropdownMenuItem(
                                                text = { Text("${voice.name} (${voice.id})") },
                                                onClick = {
                                                    ttsVoiceIdInput = voice.id
                                                    doubaoVoiceDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsVoiceIdInput,
                                    onValueChange = { ttsVoiceIdInput = it },
                                    label = { Text(stringResource(R.string.speech_services_doubao_voice_id)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_doubao_voice_id_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_doubao_voice_id_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.OPENAI_WS_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_openai_ws_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsUrlTemplateInput,
                                    onValueChange = { ttsUrlTemplateInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_ws_url)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_ws_url_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_openai_ws_url_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsModelNameInput,
                                    onValueChange = { ttsModelNameInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_model)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_ws_model_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_openai_ws_model_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = stringResource(R.string.speech_services_openai_voice_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val builtinRealtimeVoices = remember { OpenAIRealtimeVoiceProvider.AVAILABLE_VOICES }
                                var openAiRealtimeShowVoicesDialog by remember { mutableStateOf(false) }
                                var openAiRealtimeVoiceSearchQuery by remember { mutableStateOf("") }

                                val filteredRealtimeVoices = remember(builtinRealtimeVoices, openAiRealtimeVoiceSearchQuery) {
                                    val q = openAiRealtimeVoiceSearchQuery.trim().lowercase()
                                    if (q.isBlank()) {
                                        builtinRealtimeVoices
                                    } else {
                                        builtinRealtimeVoices.filter { v ->
                                            v.id.lowercase().contains(q) || v.name.lowercase().contains(q)
                                        }
                                    }
                                }

                                LaunchedEffect(openAiRealtimeShowVoicesDialog) {
                                    if (openAiRealtimeShowVoicesDialog) {
                                        openAiRealtimeVoiceSearchQuery = ""
                                    }
                                }

                                if (openAiRealtimeShowVoicesDialog) {
                                    AlertDialog(
                                        onDismissRequest = { openAiRealtimeShowVoicesDialog = false },
                                        title = { Text(stringResource(R.string.speech_services_openai_voice_select)) },
                                        text = {
                                            Column {
                                                OutlinedTextField(
                                                    value = openAiRealtimeVoiceSearchQuery,
                                                    onValueChange = { openAiRealtimeVoiceSearchQuery = it },
                                                    label = { Text(stringResource(R.string.search)) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Search,
                                                            contentDescription = stringResource(R.string.search),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        if (openAiRealtimeVoiceSearchQuery.isNotBlank()) {
                                                            IconButton(onClick = { openAiRealtimeVoiceSearchQuery = "" }) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Clear,
                                                                    contentDescription = stringResource(R.string.clear)
                                                                )
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                if (filteredRealtimeVoices.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.no_models_found),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                                                        items(
                                                            items = filteredRealtimeVoices,
                                                            key = { it.id }
                                                        ) { voice ->
                                                            val displayName = if (voice.name == voice.id) {
                                                                voice.name
                                                            } else {
                                                                "${voice.name} (${voice.id})"
                                                            }
                                                            DropdownMenuItem(
                                                                text = { Text(displayName) },
                                                                onClick = {
                                                                    ttsVoiceIdInput = voice.id
                                                                    openAiRealtimeShowVoicesDialog = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { openAiRealtimeShowVoicesDialog = false }) {
                                                Text(stringResource(android.R.string.ok))
                                            }
                                        }
                                    )
                                }

                                OutlinedTextField(
                                    value = ttsVoiceIdInput,
                                    onValueChange = { ttsVoiceIdInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_voice_id)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_ws_voice_id_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_openai_ws_voice_id_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { openAiRealtimeShowVoicesDialog = true }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                                contentDescription = stringResource(R.string.speech_services_openai_voice_select_icon)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.OPENAI_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_openai_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsUrlTemplateInput,
                                    onValueChange = { ttsUrlTemplateInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_url)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_url_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_openai_url_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                var openAiModels by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
                                var openAiModelsFetchError by remember { mutableStateOf<String?>(null) }
                                var openAiModelsRefreshing by remember { mutableStateOf(false) }
                                var openAiShowModelsDialog by remember { mutableStateOf(false) }
                                var openAiModelSearchQuery by remember { mutableStateOf("") }

                                fun refreshOpenAiModels() {
                                    if (openAiModelsRefreshing) return
                                    openAiModelsRefreshing = true
                                    openAiModelsFetchError = null
                                    scope.launch {
                                        try {
                                            val result = ModelListFetcher.getModelsList(
                                                context = context,
                                                apiKey = ttsApiKeyInput,
                                                apiEndpoint = ttsUrlTemplateInput,
                                                apiProviderType = ApiProviderType.OPENAI_GENERIC
                                            )
                                            result.fold(
                                                onSuccess = { models ->
                                                    openAiModels = models
                                                },
                                                onFailure = { e ->
                                                    openAiModelsFetchError =
                                                        context.getString(
                                                            R.string.speech_services_openai_models_fetch_failed,
                                                            e.message ?: "Unknown error"
                                                        )
                                                }
                                            )
                                        } finally {
                                            openAiModelsRefreshing = false
                                        }
                                    }
                                }

                                val filteredOpenAiModels = remember(openAiModels, openAiModelSearchQuery) {
                                    val q = openAiModelSearchQuery.trim().lowercase()
                                    if (q.isBlank()) {
                                        openAiModels
                                    } else {
                                        openAiModels.filter { m ->
                                            m.id.lowercase().contains(q) || m.name.lowercase().contains(q)
                                        }
                                    }
                                }

                                LaunchedEffect(openAiShowModelsDialog) {
                                    if (openAiShowModelsDialog) {
                                        openAiModelSearchQuery = ""
                                    }
                                }

                                if (openAiShowModelsDialog) {
                                    AlertDialog(
                                        onDismissRequest = { openAiShowModelsDialog = false },
                                        title = { Text(stringResource(R.string.speech_services_openai_model_select)) },
                                        text = {
                                            Column {
                                                OutlinedTextField(
                                                    value = openAiModelSearchQuery,
                                                    onValueChange = { openAiModelSearchQuery = it },
                                                    label = { Text(stringResource(R.string.search_models)) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Search,
                                                            contentDescription = stringResource(R.string.search),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        if (openAiModelSearchQuery.isNotBlank()) {
                                                            IconButton(onClick = { openAiModelSearchQuery = "" }) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Clear,
                                                                    contentDescription = stringResource(R.string.clear)
                                                                )
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                if (filteredOpenAiModels.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.no_models_found),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    LazyColumn(
                                                        modifier = Modifier.heightIn(max = 360.dp)
                                                    ) {
                                                        items(
                                                            items = filteredOpenAiModels,
                                                            key = { it.id }
                                                        ) { model ->
                                                            val displayName = if (model.name == model.id) {
                                                                model.name
                                                            } else {
                                                                "${model.name} (${model.id})"
                                                            }
                                                            DropdownMenuItem(
                                                                text = { Text(displayName) },
                                                                onClick = {
                                                                    ttsModelNameInput = model.id
                                                                    openAiShowModelsDialog = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = { openAiShowModelsDialog = false }
                                            ) {
                                                Text(stringResource(android.R.string.ok))
                                            }
                                        }
                                    )
                                }

                                OutlinedTextField(
                                    value = ttsModelNameInput,
                                    onValueChange = { ttsModelNameInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_model)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_model_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { refreshOpenAiModels() },
                                                enabled =
                                                    !openAiModelsRefreshing &&
                                                        ttsUrlTemplateInput.isNotBlank() &&
                                                        ttsApiKeyInput.isNotBlank()
                                            ) {
                                                if (openAiModelsRefreshing) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = stringResource(R.string.speech_services_openai_models_refresh)
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { openAiShowModelsDialog = true },
                                                enabled = openAiModels.isNotEmpty()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                                    contentDescription = stringResource(R.string.available_models_list)
                                                )
                                            }
                                        }
                                    }
                                )

                                openAiModelsFetchError?.let { msg ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = stringResource(R.string.speech_services_openai_voice_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val builtinOpenAiVoices = remember { OpenAIVoiceProvider.AVAILABLE_VOICES }
                                var openAiVoices by remember { mutableStateOf<List<VoiceService.Voice>>(emptyList()) }
                                var openAiVoicesFetchError by remember { mutableStateOf<String?>(null) }
                                var openAiVoicesRefreshing by remember { mutableStateOf(false) }
                                var openAiShowVoicesDialog by remember { mutableStateOf(false) }
                                var openAiVoiceSearchQuery by remember { mutableStateOf("") }

                                fun refreshOpenAiVoices() {
                                    if (openAiVoicesRefreshing) return
                                    openAiVoicesRefreshing = true
                                    openAiVoicesFetchError = null
                                    scope.launch {
                                        try {
                                            val result = VoiceListFetcher.getVoicesList(
                                                apiKey = ttsApiKeyInput,
                                                ttsEndpointUrl = ttsUrlTemplateInput
                                            )
                                            result.fold(
                                                onSuccess = { voices ->
                                                    openAiVoices = voices
                                                },
                                                onFailure = { e ->
                                                    openAiVoicesFetchError =
                                                        context.getString(
                                                            R.string.speech_services_openai_voices_fetch_failed,
                                                            e.message ?: "Unknown error"
                                                        )
                                                }
                                            )
                                        } finally {
                                            openAiVoicesRefreshing = false
                                        }
                                    }
                                }

                                val effectiveOpenAiVoices = remember(openAiVoices, builtinOpenAiVoices) {
                                    val sourceVoices =
                                        if (openAiVoices.isNotEmpty()) openAiVoices else builtinOpenAiVoices
                                    sourceVoices.distinctBy { it.id }
                                }

                                val filteredOpenAiVoices = remember(effectiveOpenAiVoices, openAiVoiceSearchQuery) {
                                    val q = openAiVoiceSearchQuery.trim().lowercase()
                                    if (q.isBlank()) {
                                        effectiveOpenAiVoices
                                    } else {
                                        effectiveOpenAiVoices.filter { v ->
                                            v.id.lowercase().contains(q) || v.name.lowercase().contains(q)
                                        }
                                    }
                                }

                                LaunchedEffect(openAiShowVoicesDialog) {
                                    if (openAiShowVoicesDialog) {
                                        openAiVoiceSearchQuery = ""
                                    }
                                }

                                if (openAiShowVoicesDialog) {
                                    AlertDialog(
                                        onDismissRequest = { openAiShowVoicesDialog = false },
                                        title = { Text(stringResource(R.string.speech_services_openai_voice_select)) },
                                        text = {
                                            Column {
                                                OutlinedTextField(
                                                    value = openAiVoiceSearchQuery,
                                                    onValueChange = { openAiVoiceSearchQuery = it },
                                                    label = { Text(stringResource(R.string.search)) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Search,
                                                            contentDescription = stringResource(R.string.search),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        if (openAiVoiceSearchQuery.isNotBlank()) {
                                                            IconButton(onClick = { openAiVoiceSearchQuery = "" }) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Clear,
                                                                    contentDescription = stringResource(R.string.clear)
                                                                )
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                if (filteredOpenAiVoices.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.no_models_found),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                                                        items(
                                                            items = filteredOpenAiVoices,
                                                            key = { it.id }
                                                        ) { voice ->
                                                            val displayName = if (voice.name == voice.id) {
                                                                voice.name
                                                            } else {
                                                                "${voice.name} (${voice.id})"
                                                            }
                                                            DropdownMenuItem(
                                                                text = { Text(displayName) },
                                                                onClick = {
                                                                    ttsVoiceIdInput = voice.id
                                                                    openAiShowVoicesDialog = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { openAiShowVoicesDialog = false }) {
                                                Text(stringResource(android.R.string.ok))
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsVoiceIdInput,
                                    onValueChange = { ttsVoiceIdInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_voice_id)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_voice_id_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { refreshOpenAiVoices() },
                                                enabled =
                                                    !openAiVoicesRefreshing &&
                                                        ttsUrlTemplateInput.isNotBlank() &&
                                                        ttsApiKeyInput.isNotBlank()
                                            ) {
                                                if (openAiVoicesRefreshing) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = stringResource(R.string.speech_services_openai_voices_refresh)
                                                    )
                                                }
                                            }

                                            IconButton(onClick = { openAiShowVoicesDialog = true }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                                    contentDescription = stringResource(R.string.speech_services_openai_voice_select_icon)
                                                )
                                            }
                                        }
                                    }
                                )

                                openAiVoicesFetchError?.let { msg ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // --- STT Section ---
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.speech_services_stt_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = stringResource(R.string.speech_services_stt_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = stringResource(R.string.speech_services_service_type),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        var sttDropdownExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = sttDropdownExpanded,
                            onExpandedChange = { sttDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when(sttServiceTypeInput) {
                                    SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN -> stringResource(R.string.speech_services_stt_type_sherpa)
                                    SpeechServiceFactory.SpeechServiceType.OPENAI_STT -> stringResource(R.string.speech_services_stt_type_openai)
                                    SpeechServiceFactory.SpeechServiceType.DEEPGRAM_STT -> stringResource(R.string.speech_services_stt_type_deepgram)
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.speech_services_stt_engine)) },
                                trailingIcon = { 
                                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.speech_services_dropdown_expand))
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = sttDropdownExpanded,
                                onDismissRequest = { sttDropdownExpanded = false }
                            ) {
                                SpeechServiceFactory.SpeechServiceType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = when(type) {
                                                    SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN -> stringResource(R.string.speech_services_stt_type_sherpa)
                                                    SpeechServiceFactory.SpeechServiceType.OPENAI_STT -> stringResource(R.string.speech_services_stt_type_openai)
                                                    SpeechServiceFactory.SpeechServiceType.DEEPGRAM_STT -> stringResource(R.string.speech_services_stt_type_deepgram)
                                                },
                                                fontWeight = if (sttServiceTypeInput == type) FontWeight.Medium else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            sttServiceTypeInput = type
                                            sttDropdownExpanded = false
                                        },
                                        enabled = true
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = sttServiceTypeInput == SpeechServiceFactory.SpeechServiceType.OPENAI_STT) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_openai_stt_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = sttEndpointUrlInput,
                                    onValueChange = { sttEndpointUrlInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_stt_url)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_stt_url_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_openai_stt_url_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = sttApiKeyInput,
                                    onValueChange = { sttApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_stt_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_stt_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = sttModelNameInput,
                                    onValueChange = { sttModelNameInput = it },
                                    label = { Text(stringResource(R.string.speech_services_openai_stt_model)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_openai_stt_model_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        AnimatedVisibility(visible = sttServiceTypeInput == SpeechServiceFactory.SpeechServiceType.DEEPGRAM_STT) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.speech_services_deepgram_stt_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = sttEndpointUrlInput,
                                    onValueChange = { sttEndpointUrlInput = it },
                                    label = { Text(stringResource(R.string.speech_services_deepgram_stt_url)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_deepgram_stt_url_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = stringResource(R.string.speech_services_deepgram_stt_url_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = sttApiKeyInput,
                                    onValueChange = { sttApiKeyInput = it },
                                    label = { Text(stringResource(R.string.speech_services_deepgram_stt_api_key)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_deepgram_stt_api_key_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = sttModelNameInput,
                                    onValueChange = { sttModelNameInput = it },
                                    label = { Text(stringResource(R.string.speech_services_deepgram_stt_model)) },
                                    placeholder = { Text(stringResource(R.string.speech_services_deepgram_stt_model_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.speech_services_stt_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 信息卡片
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.speech_services_info_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsInfoRow(
                                title = stringResource(R.string.speech_services_info_tts_title),
                                description = stringResource(R.string.speech_services_info_tts_desc)
                            )
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                            
                            SettingsInfoRow(
                                title = stringResource(R.string.speech_services_info_stt_title),
                                description = stringResource(R.string.speech_services_info_stt_desc)
                            )
                        }
                    }
                }
                
                // 底部区域
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToTextToSpeech,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.speech_services_test_tts))
                        }
                    }
                }
                
                // 底部空间
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (simpleTtsShowVoiceDialog) {
        AlertDialog(
            onDismissRequest = { simpleTtsShowVoiceDialog = false },
            title = { Text(stringResource(R.string.speech_services_simple_tts_voice_select)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    item {
                        TextButton(
                            onClick = {
                                ttsVoiceIdInput = ""
                                simpleTtsShowVoiceDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.speech_services_simple_tts_voice_default))
                        }
                    }
                    items(simpleTtsFilteredVoices) { voice ->
                        TextButton(
                            onClick = {
                                ttsVoiceIdInput = voice.id
                                if (ttsLocaleTagInput.isBlank()) {
                                    ttsLocaleTagInput = voice.locale
                                }
                                simpleTtsShowVoiceDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${voice.name} (${voice.locale})")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { simpleTtsShowVoiceDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsInfoRow(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
    }
} 
