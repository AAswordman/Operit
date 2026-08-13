package com.ai.assistance.operit.ui.features.toolbox.screens.texttospeech

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.voice.SimpleVoiceProvider
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.api.voice.VoiceService
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.ai.assistance.operit.api.voice.TtsException

/** 文本转语音演示屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechScreen(navController: NavController) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()
        val profilePrefs = remember { SpeechServiceProfilesPreferences(context) }
        val currentTtsProfile by profilePrefs.currentTtsProfileOrNullFlow.collectAsState(initial = null)
        if (currentTtsProfile == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                }
                return
        }
        val activeTtsProfile = checkNotNull(currentTtsProfile)
        val ttsServiceType = activeTtsProfile.serviceType
        val httpConfig = activeTtsProfile.httpConfig
        var voiceServiceVersion by remember { mutableStateOf(0) }
        var voiceService by remember(voiceServiceVersion) {
                mutableStateOf(VoiceServiceFactory.getInstance(context))
        }
        
        // 状态变量
        var inputText by remember { mutableStateOf("") }
        var speechRate by remember { mutableStateOf(1.0f) }
        var speechPitch by remember { mutableStateOf(1.0f) }
        var isInitialized by remember { mutableStateOf(false) }
        var isSpeaking by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var errorDetails by remember { mutableStateOf<String?>(null) }
        var debugInfo by remember { mutableStateOf<String?>(null) }
        var ttsLocaleTagInput by remember(httpConfig) { mutableStateOf(httpConfig.localeTag) }
        var ttsVoiceIdInput by remember(httpConfig) { mutableStateOf(httpConfig.voiceId) }
        var simpleTtsVoices by remember { mutableStateOf<List<VoiceService.Voice>>(emptyList()) }
        var simpleTtsVoicesLoading by remember { mutableStateOf(false) }
        var simpleTtsVoicesError by remember { mutableStateOf<String?>(null) }
        var simpleTtsLocaleExpanded by remember { mutableStateOf(false) }
        var simpleTtsShowVoiceDialog by remember { mutableStateOf(false) }

        val simpleTtsLocaleOptions = remember(simpleTtsVoices) {
                simpleTtsVoices.map { it.locale.trim() }.filter { it.isNotBlank() }.distinct().sorted()
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

        fun refreshVoiceService(): VoiceService {
                val latestVoiceService = VoiceServiceFactory.getInstance(context)
                if (voiceService !== latestVoiceService) {
                        voiceService = latestVoiceService
                }
                return voiceService
        }

        // 监听语音服务状态
        LaunchedEffect(voiceService) {
                try {
                        isInitialized = voiceService.initialize()
                        if (!isInitialized) {
                                error = context.getString(R.string.tts_init_failed)
                                errorDetails = context.getString(R.string.tts_init_error_details)
                        }
                } catch (e: Exception) {
                        error = context.getString(R.string.tts_init_error)
                        errorDetails = handleTtsError(e)
                        debugInfo = context.getString(R.string.tts_debug_service_type, voiceService.javaClass.simpleName)
                }

                voiceService.speakingStateFlow.collect { speaking -> isSpeaking = speaking }
        }

        LaunchedEffect(ttsServiceType) {
                if (ttsServiceType != VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS) return@LaunchedEffect
                simpleTtsVoicesLoading = true
                simpleTtsVoicesError = null
                var provider: SimpleVoiceProvider? = null
                try {
                        provider = SimpleVoiceProvider(
                                context = context.applicationContext,
                                initialLocaleTag = ttsLocaleTagInput,
                                initialVoiceId = ttsVoiceIdInput,
                                defaultSpeechRate = activeTtsProfile.speechRate,
                                defaultPitch = activeTtsProfile.pitch,
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

        fun saveSimpleTtsSelection(localeTag: String, voiceId: String) {
                coroutineScope.launch {
                        try {
                                profilePrefs.updateTtsProfile(
                                        activeTtsProfile.copy(
                                                httpConfig = httpConfig.copy(localeTag = localeTag, voiceId = voiceId)
                                        )
                                )
                                VoiceServiceFactory.resetInstance()
                                voiceService = VoiceServiceFactory.getInstance(context)
                                voiceServiceVersion++
                        } catch (error: Exception) {
                                AppLogger.e("TextToSpeechScreen", "Failed to save TTS profile voice", error)
                        }
                }
        }

        // 播放文本
        @SuppressLint("StringFormatMatches")
        fun speakText() {
                if (inputText.isBlank()) {
                        error = context.getString(R.string.tts_input_empty)
                        errorDetails = null
                        debugInfo = null
                        return
                }

                // 清除之前的错误信息
                error = null
                errorDetails = null
                debugInfo = null

                coroutineScope.launch {
                        try {
                                val currentVoiceService = refreshVoiceService()
                                val success =
                                        currentVoiceService.speak(inputText, true, speechRate, speechPitch)
                                if (!success) {
                                        error = context.getString(R.string.tts_speak_failed)
                                        errorDetails = context.getString(R.string.tts_speak_error_details)
                                        debugInfo = context.getString(R.string.tts_debug_params, inputText, speechRate, speechPitch)
                                }
                        } catch (e: Exception) {
                                error = context.getString(R.string.tts_speak_error, e.message ?: "Unknown error")
                                errorDetails = handleTtsError(e)
                                debugInfo = context.getString(R.string.tts_debug_params_with_service, inputText, speechRate, speechPitch, refreshVoiceService().javaClass.simpleName)
                        }
                }
        }

        // 停止播放
        fun stopSpeaking() {
                coroutineScope.launch {
                        try {
                                refreshVoiceService().stop()
                        } catch (e: Exception) {
                                error = context.getString(R.string.tts_stop_error, e.message ?: "Unknown error")
                        }
                }
        }

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .verticalScroll(scrollState)
                                .padding(16.dp)
        ) {
                // 标题
                Text(
                        text = stringResource(R.string.tts_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                )

                // 文本输入卡片
                Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                        text = stringResource(R.string.tts_input_text),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                                        placeholder = { Text(stringResource(R.string.tts_input_hint)) },
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor =
                                                                MaterialTheme.colorScheme.primary,
                                                        unfocusedBorderColor =
                                                                MaterialTheme.colorScheme.outline
                                                ),
                                        maxLines = 5
                                )
                        }
                }

                // 语音设置卡片
                Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                        text = stringResource(R.string.tts_speech_settings),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 语速调节
                                Text(
                                        text = stringResource(R.string.tts_speech_rate, speechRate),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                Slider(
                                        value = speechRate,
                                        onValueChange = { speechRate = it },
                                        valueRange = 0.5f..2.0f,
                                        steps = 5,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 音调调节
                                Text(
                                        text = stringResource(R.string.tts_speech_pitch, speechPitch),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                Slider(
                                        value = speechPitch,
                                        onValueChange = { speechPitch = it },
                                        valueRange = 0.5f..2.0f,
                                        steps = 5,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )

                                if (ttsServiceType == VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS) {
                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                                text = stringResource(R.string.speech_services_simple_tts_settings),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
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
                                                                        ttsVoiceIdInput = ""
                                                                        saveSimpleTtsSelection("", "")
                                                                        simpleTtsLocaleExpanded = false
                                                                }
                                                        )
                                                        simpleTtsLocaleOptions.forEach { localeTag ->
                                                                DropdownMenuItem(
                                                                        text = { Text(localeTag) },
                                                                        onClick = {
                                                                                val nextVoiceId =
                                                                                        if (simpleTtsSelectedVoice?.locale?.equals(localeTag, ignoreCase = true) == true) {
                                                                                                ttsVoiceIdInput
                                                                                        } else {
                                                                                                ""
                                                                                        }
                                                                                ttsLocaleTagInput = localeTag
                                                                                ttsVoiceIdInput = nextVoiceId
                                                                                saveSimpleTtsSelection(localeTag, nextVoiceId)
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
                                                                        IconButton(
                                                                                onClick = {
                                                                                        ttsVoiceIdInput = ""
                                                                                        saveSimpleTtsSelection(ttsLocaleTagInput, "")
                                                                                }
                                                                        ) {
                                                                                Icon(
                                                                                        imageVector = Icons.Default.Clear,
                                                                                        contentDescription = stringResource(R.string.speech_services_simple_tts_voice_clear)
                                                                                )
                                                                        }
                                                                }
                                                                IconButton(onClick = { simpleTtsShowVoiceDialog = true }) {
                                                                        Icon(
                                                                                imageVector = Icons.Filled.FormatListBulleted,
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
                }

                // 操作按钮
                Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Button(
                                onClick = { speakText() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = isInitialized && !isSpeaking && inputText.isNotBlank(),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                shape = RoundedCornerShape(8.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.tts_play),
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.tts_play_speech), style = MaterialTheme.typography.titleMedium)
                        }

                        Button(
                                onClick = { stopSpeaking() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = isSpeaking,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                shape = RoundedCornerShape(8.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = stringResource(R.string.tts_stop),
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.tts_stop_speech), style = MaterialTheme.typography.titleMedium)
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 状态指示器
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.secondaryContainer
                                ),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isInitialized) Icons.Default.CheckCircle
                                                        else Icons.Default.Error,
                                                contentDescription = null,
                                                tint =
                                                        if (isInitialized) Color(0xFF4CAF50)
                                                        else MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                                text =
                                                        if (isInitialized) stringResource(R.string.tts_engine_initialized)
                                                        else stringResource(R.string.tts_engine_not_initialized),
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isSpeaking) Icons.AutoMirrored.Filled.VolumeUp
                                                        else Icons.AutoMirrored.Filled.VolumeOff,
                                                contentDescription = null,
                                                tint =
                                                        if (isSpeaking) Color(0xFF2196F3)
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSecondaryContainer
                                        )
                                        Text(
                                                text = if (isSpeaking) stringResource(R.string.tts_playing) else stringResource(R.string.tts_not_playing),
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }
                        }
                }

                // 错误提示
                if (error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(8.dp)
                        ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Error,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                        text = stringResource(R.string.tts_error),
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Text(
                                                text = error ?: "",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                        
                                        // 错误详情
                                        if (errorDetails != null) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                        text = stringResource(R.string.tts_error_details),
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                        text = errorDetails ?: "",
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                        
                                        // 调试信息
                                        if (debugInfo != null) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                        text = stringResource(R.string.tts_debug_info),
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                        text = debugInfo ?: "",
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                        
                                        // 清除错误按钮
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                        ) {
                                                OutlinedButton(
                                                        onClick = {
                                                                error = null
                                                                errorDetails = null
                                                                debugInfo = null
                                                        },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                                contentColor = MaterialTheme.colorScheme.error
                                                        )
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Clear,
                                                                contentDescription = stringResource(R.string.tts_clear_error),
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(stringResource(R.string.tts_clear_error_message))
                                                }
                                        }
                                }
                        }
                }

                // 使用说明
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                )
                ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = stringResource(R.string.tts_usage_instructions),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                        text = stringResource(R.string.tts_usage_instructions_content),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                        text = stringResource(R.string.tts_accessibility_note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                )
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
                                                                saveSimpleTtsSelection(ttsLocaleTagInput, "")
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
                                                                saveSimpleTtsSelection(ttsLocaleTagInput.ifBlank { voice.locale }, voice.id)
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

/**
 * 处理TTS异常并返回格式化的错误详情
 *
 * @param e 捕获到的异常
 * @return 格式化后的错误详情字符串
 */
private fun handleTtsError(e: Exception): String {
    return when (e) {
        is TtsException -> {
            // TTS 异常，优先显示服务器返回的具体信息
            val code = e.httpStatusCode
            val body = e.errorBody?.takeIf { it.isNotBlank() }
            if (code != null && body != null) {
                "TTS service error (HTTP $code): $body"
            } else if (code != null) {
                "TTS service error, status code: $code"
            } else if (body != null) {
                "TTS service error: $body"
            } else {
                "TTS service unknown error: ${e.cause?.message ?: e.message}"
            }
        }
        is UnknownHostException -> "Network error: Unable to reach host, please check network connection and DNS settings."
        is SocketTimeoutException -> "Network timeout: Server response timeout, please check network status."
        is ConnectException -> "Network error: Unable to connect to server, please check server address and port."
        is ProtocolException -> "Network protocol error: ${e.message}"
        is IOException -> "Network IO error, please check device network connection."
        else -> "Unknown error: ${e.javaClass.simpleName}\n${e.stackTraceToString().take(300)}..."
    }
}
