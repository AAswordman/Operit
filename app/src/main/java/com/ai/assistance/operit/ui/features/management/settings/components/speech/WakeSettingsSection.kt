package com.ai.assistance.operit.ui.features.management.settings.components.speech

import com.ai.assistance.operit.ui.theme.script.ThemeScriptFilledTonalButton as FilledTonalButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.management.settings.components.speech.CompactSwitchRow
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.*
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechSectionLabel
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWakeAttachments
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWorkbenchField

/** 使用原有唤醒配置键，按监听、识别方式、唤醒后行为和附件组织。 */
@Composable
fun WakeSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val prefs = remember { WakeWordPreferences(context.applicationContext) }
    val listening by prefs.alwaysListeningEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_ALWAYS_LISTENING_ENABLED)
    val savedPhrase by prefs.wakePhraseFlow.collectAsState(initial = null)
    val regexEnabled by prefs.wakePhraseRegexEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE_REGEX_ENABLED)
    val mode by prefs.wakeRecognitionModeFlow.collectAsState(initial = WakeWordPreferences.WakeRecognitionMode.STT)
    val templates by prefs.personalWakeTemplatesFlow.collectAsState(initial = emptyList())
    val savedTimeout by prefs.voiceCallInactivityTimeoutSecondsFlow.collectAsState(initial = null)
    val greetingEnabled by prefs.wakeGreetingEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_ENABLED)
    val savedGreeting by prefs.wakeGreetingTextFlow.collectAsState(initial = null)
    val newChat by prefs.wakeCreateNewChatOnWakeEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_CREATE_NEW_CHAT_ON_WAKE_ENABLED)
    val savedGroup by prefs.autoNewChatGroupFlow.collectAsState(initial = null)
    val attachEnabled by prefs.voiceAutoAttachEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_VOICE_AUTO_ATTACH_ENABLED)
    val attachItems by prefs.voiceAutoAttachItemsFlow.collectAsState(initial = WakeWordPreferences.getDefaultVoiceAutoAttachItems(context))
    val scope = rememberCoroutineScope()
    var enrollment by rememberSaveable { mutableStateOf(false) }

    fun save(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                AppLogger.e("WakeSettings", "保存唤醒设置失败", failure)
                OKToast.error(context, context.getString(R.string.speech_workbench_save_error, failure.localizedMessage.orEmpty()), OKToast.LENGTH_SHORT).show()
            }
        }
    }
    val listenPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save { prefs.saveAlwaysListeningEnabled(true) }
        else OKToast.warning(context, context.getString(R.string.microphone_permission_denied_toast), OKToast.LENGTH_SHORT).show()
    }
    val enrollmentPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enrollment = true
        else OKToast.warning(context, context.getString(R.string.microphone_permission_denied_toast), OKToast.LENGTH_SHORT).show()
    }
    if (savedPhrase == null || savedTimeout == null || savedGreeting == null || savedGroup == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    // 等实际配置到达再初始化输入，避免默认值闪回覆盖已发布的设置。
    var phrase by remember { mutableStateOf(checkNotNull(savedPhrase)) }
    var timeout by remember { mutableStateOf(checkNotNull(savedTimeout).toString()) }
    var greeting by remember { mutableStateOf(checkNotNull(savedGreeting)) }
    var group by remember { mutableStateOf(checkNotNull(savedGroup)) }
    val phraseValid = phrase.isNotBlank() && (!regexEnabled || runCatching { Regex(phrase) }.isSuccess)
    val timeoutValid = timeout.toIntOrNull()?.let { it in 1..600 } == true

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SpeechSectionLabel(stringResource(R.string.speech_workbench_wake_listening))
        SpeechWorkbenchCard {
            CompactSwitchRow(
                title = stringResource(R.string.voice_wakeup_always_listen_title), description = stringResource(R.string.voice_wakeup_always_listen_desc), checked = listening,
                onCheckedChange = { enabled ->
                    if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) listenPermission.launch(Manifest.permission.RECORD_AUDIO)
                    else save { prefs.saveAlwaysListeningEnabled(enabled) }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            TextButton(onClick = {
                try { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (failure: Exception) {
                    AppLogger.e("WakeSettings", "打开系统助手设置失败", failure)
                    OKToast.error(context, context.getString(R.string.cannot_open_settings), OKToast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.default_assistant_guide_open_settings), Modifier.weight(1f))
                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
            }
        }
        SpeechSectionLabel(stringResource(R.string.speech_workbench_wake_method))
        SpeechWorkbenchCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == WakeWordPreferences.WakeRecognitionMode.STT,
                    onClick = { save { prefs.saveWakeRecognitionMode(WakeWordPreferences.WakeRecognitionMode.STT) } },
                    label = { Text(stringResource(R.string.speech_workbench_wake_stt)) }, modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = mode == WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE,
                    onClick = { save { prefs.saveWakeRecognitionMode(WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE) } },
                    label = { Text(stringResource(R.string.speech_workbench_wake_personal)) }, modifier = Modifier.weight(1f),
                )
            }
            if (mode == WakeWordPreferences.WakeRecognitionMode.STT) {
                SpeechWorkbenchField(
                    stringResource(R.string.voice_wakeup_phrase_label), phrase,
                    { value ->
                        phrase = value
                        if (value.isNotBlank() && (!regexEnabled || runCatching { Regex(value) }.isSuccess)) save { prefs.saveWakePhrase(value) }
                    },
                    placeholder = stringResource(R.string.voice_wakeup_phrase_supporting),
                    error = if (!phraseValid) stringResource(R.string.speech_workbench_regex_error) else null,
                )
                Text(stringResource(R.string.speech_workbench_wake_local_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompactSwitchRow(
                    title = stringResource(R.string.voice_wakeup_regex_title), description = stringResource(R.string.voice_wakeup_regex_desc), checked = regexEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled || (phrase.isNotBlank() && runCatching { Regex(phrase) }.isSuccess)) {
                            save {
                                if (phrase.isNotBlank()) prefs.saveWakePhrase(phrase)
                                prefs.saveWakePhraseRegexEnabled(enabled)
                            }
                        } else OKToast.warning(context, context.getString(R.string.speech_workbench_regex_error), OKToast.LENGTH_SHORT).show()
                    },
                )
            } else {
                Text(stringResource(R.string.voice_wakeup_personal_config_dialog_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, color = if (index < templates.size) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(if (index < templates.size) Icons.Default.Check else Icons.Default.Mic, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.voice_wakeup_personal_config_step, index + 1), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (templates.isEmpty()) Text(stringResource(R.string.voice_wakeup_personal_no_templates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(modifier = Modifier.weight(1f), onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) enrollment = true
                        else enrollmentPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text(stringResource(R.string.voice_wakeup_personal_configure)) }
                    OutlinedButton(modifier = Modifier.weight(1f), enabled = templates.isNotEmpty(), onClick = { save { prefs.savePersonalWakeTemplates(emptyList()) } }) { Text(stringResource(R.string.voice_wakeup_personal_clear)) }
                }
            }
        }
        SpeechSectionLabel(stringResource(R.string.speech_workbench_wake_after))
        SpeechWorkbenchCard {
            CompactSwitchRow(
                title = stringResource(R.string.voice_wakeup_greeting_title), description = stringResource(R.string.voice_wakeup_greeting_desc), checked = greetingEnabled,
                onCheckedChange = { save { prefs.saveWakeGreetingEnabled(it) } },
            )
            SpeechWorkbenchField(stringResource(R.string.voice_wakeup_greeting_text_label), greeting, { value -> greeting = value; save { prefs.saveWakeGreetingText(value.ifBlank { WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT }) } }, enabled = greetingEnabled)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            CompactSwitchRow(
                title = stringResource(R.string.voice_wakeup_create_new_chat_title), description = stringResource(R.string.voice_wakeup_create_new_chat_desc), checked = newChat,
                onCheckedChange = { save { prefs.saveWakeCreateNewChatOnWakeEnabled(it) } },
            )
            SpeechWorkbenchField(stringResource(R.string.voice_wakeup_auto_new_chat_group_label), group, { value -> group = value; save { prefs.saveAutoNewChatGroup(value.ifBlank { WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP }) } }, enabled = newChat)
            SpeechWorkbenchField(
                stringResource(R.string.voice_wakeup_inactivity_timeout_label), timeout,
                { value ->
                    timeout = value.filter(Char::isDigit)
                    timeout.toIntOrNull()?.takeIf { it in 1..600 }?.let { seconds -> save { prefs.saveVoiceCallInactivityTimeoutSeconds(seconds) } }
                }, keyboardType = KeyboardType.Number,
                hint = stringResource(R.string.voice_wakeup_inactivity_timeout_supporting),
                error = if (!timeoutValid) stringResource(R.string.voice_wakeup_inactivity_timeout_supporting) else null,
            )
        }
        SpeechSectionLabel(stringResource(R.string.voice_keyword_attachments_title))
        SpeechWorkbenchCard {
            CompactSwitchRow(
                title = stringResource(R.string.voice_keyword_attachments_enabled_title), description = stringResource(R.string.voice_keyword_attachments_enabled_desc), checked = attachEnabled,
                onCheckedChange = { save { prefs.saveVoiceAutoAttachEnabled(it) } },
            )
            if (attachEnabled) SpeechWakeAttachments(items = attachItems, onChange = { save { prefs.saveVoiceAutoAttachItems(it) } })
        }
    }
    if (enrollment && isCurrentScreen) PersonalWakeEnrollmentDialog(wakePrefs = prefs, onDismiss = { enrollment = false })
}
