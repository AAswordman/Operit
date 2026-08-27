package com.ai.assistance.operit.ui.features.assistant.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.api.speech.PersonalWakeEnrollment
import com.ai.assistance.operit.ui.features.assistant.components.AvatarConfigSection
import com.ai.assistance.operit.ui.features.assistant.components.AvatarPreviewSection
import com.ai.assistance.operit.ui.features.assistant.components.CompactSwitchRow
import com.ai.assistance.operit.ui.features.assistant.components.VoiceAutoAttachGrid
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarControllerFactoryImpl
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import kotlinx.coroutines.launch

/** 助手配置屏幕 提供DragonBones模型预览和相关配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigScreen() {
    val context = LocalContext.current
    val viewModel: AssistantConfigViewModel =
        viewModel(factory = AssistantConfigViewModel.Factory(context))
    val uiState by viewModel.uiState.collectAsState()
    val avatarControllerFactory = remember { AvatarControllerFactoryImpl() }
    val sharedAvatarController =
        uiState.currentAvatarModel?.let { model ->
            avatarControllerFactory.createController(model)
        }

    val wakePrefs = remember { WakeWordPreferences(context.applicationContext) }
    val wakeListeningEnabled by wakePrefs.alwaysListeningEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_ALWAYS_LISTENING_ENABLED)
    val wakePhrase by wakePrefs.wakePhraseFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE)
    val wakePhraseRegexEnabled by wakePrefs.wakePhraseRegexEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE_REGEX_ENABLED)
    val wakeRecognitionMode by wakePrefs.wakeRecognitionModeFlow.collectAsState(initial = WakeWordPreferences.WakeRecognitionMode.STT)
    val personalWakeTemplates by wakePrefs.personalWakeTemplatesFlow.collectAsState(initial = emptyList())
    val inactivityTimeoutSeconds by wakePrefs.voiceCallInactivityTimeoutSecondsFlow.collectAsState(
        initial = WakeWordPreferences.DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS
    )
    val wakeGreetingEnabled by wakePrefs.wakeGreetingEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_ENABLED)
    val wakeGreetingText by wakePrefs.wakeGreetingTextFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT)
    val wakeCreateNewChatOnWakeEnabled by wakePrefs.wakeCreateNewChatOnWakeEnabledFlow.collectAsState(
        initial = WakeWordPreferences.DEFAULT_WAKE_CREATE_NEW_CHAT_ON_WAKE_ENABLED
    )
    val autoNewChatGroup by wakePrefs.autoNewChatGroupFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP)
    val voiceAutoAttachEnabled by wakePrefs.voiceAutoAttachEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_VOICE_AUTO_ATTACH_ENABLED)
    val voiceAutoAttachItems by wakePrefs.voiceAutoAttachItemsFlow.collectAsState(initial = WakeWordPreferences.getDefaultVoiceAutoAttachItems(context))
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(wakePrefs) {
        wakePrefs.migrateVoiceAutoAttachItemsIfNeeded()
    }

    val requestMicPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                coroutineScope.launch {
                    wakePrefs.saveAlwaysListeningEnabled(true)
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.microphone_permission_denied_toast),
                    android.widget.Toast.LENGTH_SHORT
                )
                    .show()
            }
        }

    var wakePhraseInput by remember { mutableStateOf("") }
    var inactivityTimeoutInput by remember { mutableStateOf("") }
    var wakeGreetingTextInput by remember { mutableStateOf("") }
    var autoNewChatGroupInput by remember { mutableStateOf("") }

    var selectedConfigTab by rememberSaveable { mutableStateOf(0) }
    var isAvatarPreviewCollapsed by rememberSaveable { mutableStateOf(false) }

    var personalWakeConfigDialogVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(wakePhrase) {
        if (wakePhraseInput.isBlank()) {
            wakePhraseInput = wakePhrase
        }
    }

    LaunchedEffect(inactivityTimeoutSeconds) {
        if (inactivityTimeoutInput.isBlank()) {
            inactivityTimeoutInput = inactivityTimeoutSeconds.toString()
        }
    }

    LaunchedEffect(wakeGreetingText) {
        if (wakeGreetingTextInput.isBlank()) {
            wakeGreetingTextInput = wakeGreetingText
        }
    }

    LaunchedEffect(autoNewChatGroup) {
        if (autoNewChatGroupInput.isBlank()) {
            autoNewChatGroupInput = autoNewChatGroup
        }
    }

    // 启动文件选择器
    val zipFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // 导入选择的 zip / glb / gltf / mp4 文件
                    viewModel.importAvatarFromZip(uri)
                }
            }
        }

    // 打开文件选择器的函数
    val openZipFilePicker = {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "model/vnd.autodesk.fbx",
                        "model/fbx",
                        "application/fbx",
                        "model/gltf-binary",
                        "model/gltf+json",
                        "video/mp4",
                        "application/octet-stream"
                    )
                )
            }
        zipFileLauncher.launch(intent)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState(initial = uiState.scrollPosition)

    // 在 Composable 函数中获取字符串资源，以便在 LaunchedEffect 中使用
    val operationSuccessString = context.getString(R.string.operation_success)
    val errorOccurredString = context.getString(R.string.error_occurred_simple)

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { position ->
            viewModel.updateScrollPosition(position)
        }
    }

    // 显示操作结果的 SnackBar
    LaunchedEffect(uiState.operationSuccess, uiState.errorMessage) {
        if (uiState.operationSuccess) {
            snackbarHostState.showSnackbar(operationSuccessString)
            viewModel.clearOperationSuccess()
        } else if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar(uiState.errorMessage ?: errorOccurredString)
            viewModel.clearErrorMessage()
        }
    }

    CustomScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 主要内容
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 12.dp)
            ) {
                TabRow(selectedTabIndex = selectedConfigTab) {
                    Tab(
                        selected = selectedConfigTab == 0,
                        onClick = { selectedConfigTab = 0 },
                        text = { Text(text = stringResource(R.string.avatar_config)) }
                    )
                    Tab(
                        selected = selectedConfigTab == 1,
                        onClick = { selectedConfigTab = 1 },
                        text = { Text(text = stringResource(R.string.voice_wakeup_section_title)) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (selectedConfigTab == 0) {
                    // Avatar预览区域
                    if (!isAvatarPreviewCollapsed) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            AvatarPreviewSection(
                                modifier = Modifier.fillMaxSize(),
                                uiState = uiState,
                                avatarController = sharedAvatarController
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = { isAvatarPreviewCollapsed = !isAvatarPreviewCollapsed }) {
                            Icon(
                                imageVector =
                                    if (isAvatarPreviewCollapsed) Icons.Default.ExpandMore
                                    else Icons.Default.ExpandLess,
                                contentDescription = stringResource(
                                    if (isAvatarPreviewCollapsed) R.string.model_config_expand
                                    else R.string.model_config_collapse
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(scrollState)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AvatarConfigSection(
                                viewModel = viewModel,
                                uiState = uiState,
                                avatarController = sharedAvatarController,
                                onImportClick = { openZipFilePicker() }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else {
                    // Voice Wake-up Section
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(scrollState)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                        // Wake Mode
                        Text(
                            text = stringResource(R.string.voice_wakeup_mode_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        var modeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = modeExpanded,
                            onExpandedChange = { modeExpanded = it }
                        ) {
                            val modeLabel =
                                when (wakeRecognitionMode) {
                                    WakeWordPreferences.WakeRecognitionMode.STT -> stringResource(R.string.voice_wakeup_mode_stt)
                                    WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE -> stringResource(R.string.voice_wakeup_mode_personal)
                                }
                            OutlinedTextField(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                value = modeLabel,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(10.dp),
                            )

                            ExposedDropdownMenu(
                                expanded = modeExpanded,
                                onDismissRequest = { modeExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.voice_wakeup_mode_stt)) },
                                    onClick = {
                                        modeExpanded = false
                                        coroutineScope.launch {
                                            wakePrefs.saveWakeRecognitionMode(WakeWordPreferences.WakeRecognitionMode.STT)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.voice_wakeup_mode_personal)) },
                                    onClick = {
                                        modeExpanded = false
                                        coroutineScope.launch {
                                            wakePrefs.saveWakeRecognitionMode(WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE)
                                        }
                                    }
                                )
                            }
                        }

                        if (wakeRecognitionMode == WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE) {
                            if (personalWakeTemplates.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.voice_wakeup_personal_no_templates),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { personalWakeConfigDialogVisible = true },
                                ) {
                                    Text(text = stringResource(R.string.voice_wakeup_personal_configure))
                                }

                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    enabled = personalWakeTemplates.isNotEmpty(),
                                    onClick = {
                                        coroutineScope.launch {
                                            wakePrefs.savePersonalWakeTemplates(emptyList())
                                        }
                                    },
                                ) {
                                    Text(text = stringResource(R.string.voice_wakeup_personal_clear))
                                }
                            }
                        }

                        // Always Listening
                        CompactSwitchRow(
                            title = stringResource(R.string.voice_wakeup_always_listen_title),
                            description = stringResource(R.string.voice_wakeup_always_listen_desc),
                            checked = wakeListeningEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val granted =
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        coroutineScope.launch {
                                            wakePrefs.saveAlwaysListeningEnabled(true)
                                        }
                                    } else {
                                        requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    coroutineScope.launch {
                                        wakePrefs.saveAlwaysListeningEnabled(false)
                                    }
                                }
                            }
                        )

                        if (wakeRecognitionMode == WakeWordPreferences.WakeRecognitionMode.STT) {
                            // Wake Phrase Input
                            OutlinedTextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                value = wakePhraseInput,
                                onValueChange = { newValue ->
                                    wakePhraseInput = newValue
                                    coroutineScope.launch {
                                        wakePrefs.saveWakePhrase(newValue.ifBlank { WakeWordPreferences.DEFAULT_WAKE_PHRASE })
                                    }
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                label = { Text(stringResource(R.string.voice_wakeup_phrase_label)) },
                                placeholder = { Text(stringResource(R.string.voice_wakeup_phrase_supporting)) },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )

                            // Regex Toggle
                            CompactSwitchRow(
                                title = stringResource(R.string.voice_wakeup_regex_title),
                                description = stringResource(R.string.voice_wakeup_regex_desc),
                                checked = wakePhraseRegexEnabled,
                                onCheckedChange = { enabled ->
                                    coroutineScope.launch {
                                        wakePrefs.saveWakePhraseRegexEnabled(enabled)
                                    }
                                }
                            )
                        }

                        // Timeout Input
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            value = inactivityTimeoutInput,
                            onValueChange = { newValue ->
                                val filtered = newValue.filter { it.isDigit() }
                                inactivityTimeoutInput = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null) {
                                    val clamped = parsed.coerceIn(1, 600)
                                    coroutineScope.launch {
                                        wakePrefs.saveVoiceCallInactivityTimeoutSeconds(clamped)
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            label = { Text(stringResource(R.string.voice_wakeup_inactivity_timeout_label)) },
                            placeholder = { Text(stringResource(R.string.voice_wakeup_inactivity_timeout_supporting)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        // Greeting Toggle
                        CompactSwitchRow(
                            title = stringResource(R.string.voice_wakeup_greeting_title),
                            description = stringResource(R.string.voice_wakeup_greeting_desc),
                            checked = wakeGreetingEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    wakePrefs.saveWakeGreetingEnabled(enabled)
                                }
                            }
                        )

                        // Greeting Text Input
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            value = wakeGreetingTextInput,
                            onValueChange = { newValue ->
                                wakeGreetingTextInput = newValue
                                coroutineScope.launch {
                                    wakePrefs.saveWakeGreetingText(newValue.ifBlank { WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT })
                                }
                            },
                            singleLine = true,
                            enabled = wakeGreetingEnabled,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            label = { Text(stringResource(R.string.voice_wakeup_greeting_text_label)) },
                            placeholder = { Text(stringResource(R.string.voice_wakeup_greeting_text_supporting)) },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        CompactSwitchRow(
                            title = stringResource(R.string.voice_wakeup_create_new_chat_title),
                            description = stringResource(R.string.voice_wakeup_create_new_chat_desc),
                            checked = wakeCreateNewChatOnWakeEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    wakePrefs.saveWakeCreateNewChatOnWakeEnabled(enabled)
                                }
                            }
                        )

                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            value = autoNewChatGroupInput,
                            onValueChange = { newValue ->
                                autoNewChatGroupInput = newValue
                                coroutineScope.launch {
                                    wakePrefs.saveAutoNewChatGroup(
                                        newValue.ifBlank { WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP }
                                    )
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            label = { Text(stringResource(R.string.voice_wakeup_auto_new_chat_group_label)) },
                            placeholder = {
                                Text(
                                    stringResource(
                                        R.string.voice_wakeup_auto_new_chat_group_supporting,
                                        WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP
                                    )
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 0.dp))

                        Text(
                            text = stringResource(R.string.voice_keyword_attachments_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        CompactSwitchRow(
                            title = stringResource(R.string.voice_keyword_attachments_enabled_title),
                            description = stringResource(R.string.voice_keyword_attachments_enabled_desc),
                            checked = voiceAutoAttachEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    wakePrefs.saveVoiceAutoAttachEnabled(enabled)
                                }
                            }
                        )

                        if (voiceAutoAttachEnabled) {
                            VoiceAutoAttachGrid(
                                items = voiceAutoAttachItems,
                                onItemsChange = { newItems ->
                                    coroutineScope.launch {
                                        wakePrefs.saveVoiceAutoAttachItems(newItems)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

            if (personalWakeConfigDialogVisible) {
                val scope = rememberCoroutineScope()
                var step1 by remember { mutableStateOf<FloatArray?>(null) }
                var step2 by remember { mutableStateOf<FloatArray?>(null) }
                var step3 by remember { mutableStateOf<FloatArray?>(null) }
                var recordingStep by remember { mutableStateOf(0) }

                AlertDialog(
                    onDismissRequest = {
                        if (recordingStep == 0) personalWakeConfigDialogVisible = false
                    },
                    title = { Text(text = stringResource(R.string.voice_wakeup_personal_config_dialog_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.voice_wakeup_personal_config_dialog_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            @Composable
                            fun stepRow(index: Int, value: FloatArray?, onRecord: () -> Unit) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.voice_wakeup_personal_config_step, index),
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    val busy = recordingStep == index
                                    val label =
                                        when {
                                            busy -> stringResource(R.string.voice_wakeup_personal_config_recording)
                                            value != null -> stringResource(R.string.voice_wakeup_personal_config_record_done)
                                            else -> stringResource(R.string.voice_wakeup_personal_config_record)
                                        }
                                    FilledTonalButton(
                                        onClick = onRecord,
                                        enabled = recordingStep == 0,
                                    ) {
                                        Text(text = label)
                                    }
                                }
                            }

                            stepRow(1, step1) {
                                recordingStep = 1
                                scope.launch {
                                    val feat = PersonalWakeEnrollment.recordOneTemplate(context)
                                    if (feat != null) step1 = feat
                                    recordingStep = 0
                                }
                            }
                            stepRow(2, step2) {
                                recordingStep = 2
                                scope.launch {
                                    val feat = PersonalWakeEnrollment.recordOneTemplate(context)
                                    if (feat != null) step2 = feat
                                    recordingStep = 0
                                }
                            }
                            stepRow(3, step3) {
                                recordingStep = 3
                                scope.launch {
                                    val feat = PersonalWakeEnrollment.recordOneTemplate(context)
                                    if (feat != null) step3 = feat
                                    recordingStep = 0
                                }
                            }
                        }
                    },
                    confirmButton = {
                        val canSave = step1 != null && step2 != null && step3 != null && recordingStep == 0
                        TextButton(
                            enabled = canSave,
                            onClick = {
                                if (!canSave) return@TextButton
                                coroutineScope.launch {
                                    val templates =
                                        listOf(step1!!, step2!!, step3!!).map { f ->
                                            WakeWordPreferences.PersonalWakeTemplate(features = f.toList())
                                        }
                                    wakePrefs.savePersonalWakeTemplates(templates)
                                }
                                personalWakeConfigDialogVisible = false
                            }
                        ) {
                            Text(text = stringResource(R.string.voice_wakeup_personal_config_save))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = recordingStep == 0,
                            onClick = { personalWakeConfigDialogVisible = false }
                        ) {
                            Text(text = stringResource(R.string.voice_wakeup_personal_config_cancel))
                        }
                    }
                )
            }

            // 加载指示器覆盖层
            if (uiState.isLoading || uiState.isImporting) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surface
                                    .copy(alpha = 0.7f)
                            ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text =
                                if (uiState.isImporting) stringResource(R.string.importing_model)
                                else stringResource(R.string.processing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
