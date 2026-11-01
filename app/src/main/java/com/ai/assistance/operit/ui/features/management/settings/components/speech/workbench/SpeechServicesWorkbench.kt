package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptFilledTonalButton as FilledTonalButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.stt.SttServiceFactory
import com.ai.assistance.operit.api.speech.tts.TtsServiceFactory
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.management.settings.components.speech.SpeechRecognitionTestSection
import com.ai.assistance.operit.ui.features.management.settings.components.speech.WakeSettingsSection
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpeechServicesWorkbench(initialTabIndex: Int) {
    val context = LocalContext.current
    val prefs = remember { SpeechServiceProfilesPreferences(context.applicationContext) }
    val ttsProfiles by prefs.ttsProfilesFlow.collectAsState(initial = emptyList())
    val sttProfiles by prefs.sttProfilesFlow.collectAsState(initial = emptyList())
    val activeTts by prefs.currentTtsProfileOrNullFlow.collectAsState(initial = null)
    val activeStt by prefs.currentSttProfileOrNullFlow.collectAsState(initial = null)
    val tts = activeTts
    val stt = activeStt
    if (tts == null || stt == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // 仅用档案 ID 初始化；保存回流和重命名不会重新构造正在编辑的表单。
    val ttsState = remember(tts.id) {
        SpeechProfileEditorState(TtsProfileDraft(tts), tts, acceptExternal = { saved, incoming -> incoming.updatedAt >= saved.updatedAt }) { draft ->
            val saved = prefs.updateTtsProfile(draft.toProfile())
            withContext(Dispatchers.IO) { TtsServiceFactory.resetInstance() }
            saved
        }
    }
    val sttState = remember(stt.id) {
        SpeechProfileEditorState(stt, stt, acceptExternal = { saved, incoming -> incoming.updatedAt >= saved.updatedAt }) { draft ->
            val saved = prefs.updateSttProfile(draft)
            withContext(Dispatchers.IO) { SttServiceFactory.resetInstance() }
            saved
        }
    }
    LaunchedEffect(tts, ttsState.pending, ttsState.saving) {
        ttsState.syncExternal(tts) { TtsProfileDraft(it) }
    }
    LaunchedEffect(stt, sttState.pending, sttState.saving) {
        sttState.syncExternal(stt) { it }
    }
    PersistSpeechDraft(ttsState)
    PersistSpeechDraft(sttState)

    var tab by rememberSaveable { mutableStateOf(initialTabIndex.coerceIn(0, 2)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf<String?>(null) }
    var nameAction by remember { mutableStateOf<String?>(null) }
    var deletionTarget by remember { mutableStateOf<SpeechChoice?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val isTts = tab == 0
    val currentName = if (isTts) ttsState.draft.profile.name else sttState.draft.name
    val currentId = if (isTts) tts.id else stt.id
    val profileChoices = if (isTts) ttsProfiles.map { SpeechChoice(it.id, it.name, context.getString(it.serviceType.titleRes())) }
        else sttProfiles.map { SpeechChoice(it.id, it.name, context.getString(it.serviceType.titleRes())) }
    val currentStatePending = if (isTts) ttsState.pending else sttState.pending
    val currentStateError = if (isTts) ttsState.error else sttState.error

    fun perform(action: suspend () -> Unit) {
        if (actionBusy) return
        actionBusy = true
        actionError = null
        scope.launch {
            try {
                val saved = if (isTts) ttsState.save() else sttState.save()
                if (saved) action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("SpeechServicesWorkbench", "语音档案操作失败", error)
                actionError = error.localizedMessage ?: error.javaClass.simpleName
            } finally {
                actionBusy = false
            }
        }
    }

    LaunchedEffect(tab) { listState.scrollToItem(0) }
    CustomScaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            TabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                listOf(R.string.speech_workbench_tab_tts, R.string.speech_workbench_tab_stt, R.string.voice_wakeup_section_title).forEachIndexed { index, title ->
                    Tab(selected = tab == index, enabled = !actionBusy, onClick = { tab = index }, text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(when (index) { 0 -> Icons.Default.VolumeUp; 1 -> Icons.Default.Mic; else -> Icons.Default.RecordVoiceOver }, null, Modifier.size(16.dp))
                            Text(stringResource(title), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        }
                    })
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 0) item(key = "tts-auto-read") { TtsAutoReadSetting() }
                if (tab < 2) {
                    item(key = "profile") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpeechSectionLabel(stringResource(if (isTts) R.string.speech_workbench_tts_profile else R.string.speech_workbench_stt_profile), stringResource(R.string.speech_workbench_profile_active_hint))
                            SpeechWorkbenchCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SpeechWorkbenchRow(
                                        title = currentName,
                                        value = stringResource(R.string.speech_workbench_switch),
                                        description = if (isTts) stringResource(ttsState.draft.profile.serviceType.titleRes()) else stringResource(sttState.draft.serviceType.titleRes()),
                                        icon = if (isTts) Icons.Default.VolumeUp else Icons.Default.Mic,
                                        enabled = !actionBusy,
                                        onClick = { panel = "profiles" },
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { panel = "manage" }, enabled = !actionBusy) { Icon(Icons.Default.MoreVert, stringResource(R.string.speech_workbench_manage_profile)) }
                                }
                            }
                            Text(
                                text = when {
                                    currentStateError != null || actionError != null -> stringResource(R.string.speech_workbench_save_error, currentStateError ?: actionError.orEmpty())
                                    currentStatePending || actionBusy -> stringResource(R.string.speech_workbench_saving)
                                    else -> stringResource(R.string.speech_workbench_saved)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentStateError != null || actionError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
                if (tab == 0) item(key = "tts-editor") {
                    key(tts.id) { TtsProfileEditor(ttsState.draft, { ttsState.draft = it }) }
                }
                if (tab == 0) item(key = "tts-preview") {
                    SpeechSynthesisTestSection(ttsState.draft)
                }
                if (tab == 1) item(key = "stt-editor") {
                    key(stt.id) { SttProfileEditor(sttState.draft) { sttState.draft = it } }
                }
                if (tab == 1) item(key = "stt-test") {
                    SpeechRecognitionTestSection(profileId = stt.id, serviceType = sttState.draft.serviceType, httpConfig = sttState.draft.httpConfig)
                }
                if (tab == 2) item(key = "wake") { WakeSettingsSection() }
            }
        }
    }

    if (panel == "profiles") SpeechChoicePanel(
        title = stringResource(R.string.speech_services_profile_current),
        choices = profileChoices,
        selected = currentId,
        onDismiss = { panel = null },
        error = actionError ?: currentStateError,
        loading = actionBusy,
        enabled = !actionBusy,
        dismissEnabled = !actionBusy,
        onSelect = { id -> perform {
            if (isTts) { prefs.selectTtsProfile(id); withContext(Dispatchers.IO) { TtsServiceFactory.resetInstance() } }
            else { prefs.selectSttProfile(id); withContext(Dispatchers.IO) { SttServiceFactory.resetInstance() } }
            panel = null
        } },
        footer = {
            FilledTonalButton(onClick = { panel = null; nameAction = "create" }, enabled = !actionBusy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.speech_services_profile_create))
            }
        },
    )
    if (panel == "manage") SpeechWorkbenchSheet(stringResource(R.string.speech_workbench_manage_profile), { panel = null }) {
        SpeechWorkbenchRow(stringResource(R.string.rename), currentName, { panel = null; nameAction = "rename" }, icon = Icons.Default.Edit)
        SpeechWorkbenchRow(stringResource(R.string.speech_workbench_duplicate), "", { panel = null; nameAction = "copy" }, icon = Icons.Default.ContentCopy)
        SpeechWorkbenchRow(
            stringResource(R.string.speech_services_profile_delete),
            "",
            { panel = null; deletionTarget = profileChoices.first { it.id == currentId } },
            description = stringResource(if (profileChoices.size > 1) R.string.speech_workbench_delete_current_hint else R.string.speech_workbench_keep_one_profile),
            icon = Icons.Default.Delete,
            enabled = profileChoices.size > 1,
        )
    }
    val action = nameAction
    if (action != null) ProfileNamePanel(
        title = stringResource(when (action) { "rename" -> R.string.rename; "copy" -> R.string.speech_workbench_duplicate; else -> R.string.speech_services_profile_create }),
        initialName = when (action) { "rename" -> currentName; "copy" -> context.getString(R.string.speech_workbench_copy_name, currentName); else -> "" },
        allowTemplate = action == "create",
        busy = actionBusy,
        error = actionError ?: currentStateError,
        onDismiss = { if (!actionBusy) nameAction = null },
        onConfirm = { name, copyCurrent -> perform {
            if (isTts) {
                val current = ttsState.draft.toProfile()
                when (action) {
                    "rename" -> { ttsState.draft = ttsState.draft.copy(profile = current.copy(name = name)); if (!ttsState.save()) return@perform }
                    else -> {
                        val template = if (action == "copy" || copyCurrent) current else current.copy(
                            serviceType = SpeechServicesPreferences.DEFAULT_TTS_SERVICE_TYPE,
                            httpConfig = SpeechServicesPreferences.DEFAULT_HTTP_TTS_PRESET,
                            vitsConfig = SpeechServicesPreferences.DEFAULT_VITS_TTS_PACKAGE_CONFIG,
                            cleanerRegexs = SpeechServicesPreferences.DEFAULT_TTS_CLEANER_REGEXS,
                            speechRate = SpeechServicesPreferences.DEFAULT_TTS_SPEECH_RATE,
                            pitch = SpeechServicesPreferences.DEFAULT_TTS_PITCH,
                        )
                        prefs.createTtsProfile(name, template)
                        withContext(Dispatchers.IO) { TtsServiceFactory.resetInstance() }
                    }
                }
            } else {
                val current = sttState.draft
                when (action) {
                    "rename" -> { sttState.draft = current.copy(name = name); if (!sttState.save()) return@perform }
                    else -> {
                        val template = if (action == "copy" || copyCurrent) current else current.copy(serviceType = SpeechServicesPreferences.DEFAULT_STT_SERVICE_TYPE, httpConfig = SpeechServicesPreferences.DEFAULT_STT_HTTP_PRESET)
                        prefs.createSttProfile(name, template)
                        withContext(Dispatchers.IO) { SttServiceFactory.resetInstance() }
                    }
                }
            }
            nameAction = null
        } },
    )
    val target = deletionTarget
    if (target != null) AlertDialog(
        onDismissRequest = { if (!actionBusy) deletionTarget = null },
        title = { Text(stringResource(R.string.speech_services_profile_delete)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.speech_services_profile_delete_message, target.title))
                (actionError ?: currentStateError)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !actionBusy, onClick = { perform {
            // 删除对象固定为打开确认时的档案，切换成功后发生错误也不会删错下一项。
            if (isTts) {
                if (currentId == target.id) prefs.selectTtsProfile(profileChoices.first { it.id != target.id }.id)
                prefs.deleteTtsProfile(target.id)
                withContext(Dispatchers.IO) { TtsServiceFactory.resetInstance() }
            } else {
                if (currentId == target.id) prefs.selectSttProfile(profileChoices.first { it.id != target.id }.id)
                prefs.deleteSttProfile(target.id)
                withContext(Dispatchers.IO) { SttServiceFactory.resetInstance() }
            }
            deletionTarget = null
        } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(enabled = !actionBusy, onClick = { deletionTarget = null }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun <D, P> PersistSpeechDraft(state: SpeechProfileEditorState<D, P>) {
    LaunchedEffect(state) {
        try {
            snapshotFlow { state.draft }.debounce(350).collect { state.save() }
        } finally {
            // 离页和切换档案取消防抖时，最后一次有效输入仍写入它所属的档案。
            withContext(NonCancellable) { state.save() }
        }
    }
}

@Composable
private fun ProfileNamePanel(
    title: String,
    initialName: String,
    allowTemplate: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var template by rememberSaveable { mutableStateOf(false) }
    SpeechWorkbenchSheet(title, onDismiss, dismissEnabled = !busy) {
        SpeechWorkbenchField(stringResource(R.string.name), name, { name = it }, enabled = !busy)
        if (allowTemplate) Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { template = !template }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = template, onCheckedChange = { template = it }, enabled = !busy)
            Text(stringResource(R.string.speech_workbench_copy_current), style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Button(enabled = name.isNotBlank() && !busy, onClick = { onConfirm(name.trim(), template) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(android.R.string.ok)) }
    }
}
