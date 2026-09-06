package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.SummarySectionConfig
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ContextSummarySettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var maxImageHistoryUserTurnsInput by remember { mutableStateOf("") }
    var maxMediaHistoryUserTurnsInput by remember { mutableStateOf("") }
    val savedMaxImageHistoryUserTurns by
        apiPreferences.maxImageHistoryUserTurnsFlow.collectAsState(
            initial = ApiPreferences.DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
        )
    val savedMaxMediaHistoryUserTurns by
        apiPreferences.maxMediaHistoryUserTurnsFlow.collectAsState(
            initial = ApiPreferences.DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
        )

    val hasBackgroundImage = LocalThemePreferenceSnapshot.current.useBackgroundImage
    val componentBackgroundColor =
        if (hasBackgroundImage) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        }

    LaunchedEffect(Unit) {
        maxImageHistoryUserTurnsInput = apiPreferences.maxImageHistoryUserTurnsFlow.first().toString()
        maxMediaHistoryUserTurnsInput = apiPreferences.maxMediaHistoryUserTurnsFlow.first().toString()
    }

    val functionMappings by
        functionalConfigManager.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())
    val currentChatConfigMapping = functionMappings[FunctionType.CHAT] ?: FunctionConfigMapping()
    val currentChatConfigId = currentChatConfigMapping.configId

    val currentConfig by produceState<ModelConfigData?>(initialValue = null, key1 = currentChatConfigId) {
        if (currentChatConfigId.isBlank()) {
            value = null
            return@produceState
        }
        modelConfigManager.getModelConfigFlow(currentChatConfigId).collect { config ->
            value = config
        }
    }
    var contextLengthInput by remember(currentConfig?.id) {
        mutableStateOf(formatFloatValue(currentConfig?.contextLength))
    }
    var maxContextLengthInput by remember(currentConfig?.id) {
        mutableStateOf(formatFloatValue(currentConfig?.maxContextLength))
    }
    var contextError by remember { mutableStateOf<String?>(null) }

    var enableSummary by remember(currentConfig?.id) {
        mutableStateOf(currentConfig?.enableSummary ?: false)
    }
    var summaryTokenThresholdInput by remember(currentConfig?.id) {
        mutableStateOf(formatFloatValue(currentConfig?.summaryTokenThreshold))
    }
    var enableSummaryByMessageCount by remember(currentConfig?.id) {
        mutableStateOf(currentConfig?.enableSummaryByMessageCount ?: false)
    }
    var summaryMessageCountThresholdInput by remember(currentConfig?.id) {
        mutableStateOf(currentConfig?.summaryMessageCountThreshold?.toString().orEmpty())
    }
    var summaryCustomRulesInput by remember(currentConfig?.id) {
        mutableStateOf(currentConfig?.summaryCustomRules.orEmpty())
    }
    val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
    var summarySectionsInput by remember(currentConfig?.id) {
        mutableStateOf(emptyList<SummarySectionConfig>())
    }
    var summaryError by remember { mutableStateOf<String?>(null) }
    var fullscreenTextEditor by remember(currentConfig?.id) {
        mutableStateOf<FullscreenTextEditorRequest?>(null)
    }

    LaunchedEffect(currentConfig?.id, currentConfig?.contextLength) {
        contextLengthInput = formatFloatValue(currentConfig?.contextLength)
    }
    LaunchedEffect(currentConfig?.id, currentConfig?.maxContextLength) {
        maxContextLengthInput = formatFloatValue(currentConfig?.maxContextLength)
    }
    LaunchedEffect(currentConfig?.id, currentConfig?.enableSummary) {
        enableSummary = currentConfig?.enableSummary ?: false
    }
    LaunchedEffect(currentConfig?.id, currentConfig?.summaryTokenThreshold) {
        summaryTokenThresholdInput = formatFloatValue(currentConfig?.summaryTokenThreshold)
    }
    LaunchedEffect(currentConfig?.id, currentConfig?.enableSummaryByMessageCount) {
        enableSummaryByMessageCount = currentConfig?.enableSummaryByMessageCount ?: false
    }
    LaunchedEffect(currentConfig?.id, currentConfig?.summaryMessageCountThreshold) {
        summaryMessageCountThresholdInput =
            currentConfig?.summaryMessageCountThreshold?.toString().orEmpty()
    }
    LaunchedEffect(currentConfig?.id, currentConfig?.summaryCustomRules) {
        summaryCustomRulesInput = currentConfig?.summaryCustomRules.orEmpty()
    }
    LaunchedEffect(currentConfig?.id, currentConfig?.summarySectionOverrides, useEnglish) {
        summarySectionsInput = FunctionalPrompts.resolveSummarySections(
            overrides = currentConfig?.summarySectionOverrides.orEmpty(),
            useEnglish = useEnglish
        )
    }
    val errorValidContextLength = stringResource(id = R.string.model_config_error_valid_context_length)
    val errorValidMaxContextLength =
        stringResource(id = R.string.model_config_error_valid_max_context_length)
    val errorSaveFailed = stringResource(id = R.string.model_config_error_save_failed)
    val errorSummaryThresholdRange =
        stringResource(id = R.string.model_config_error_summary_threshold_range)
    val errorValidMessageCount = stringResource(id = R.string.model_config_error_valid_message_count)

    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }

    val currentConfigDisplayName = remember(currentConfig, currentChatConfigId) {
        buildBoundConfigDisplayName(currentConfig, currentChatConfigId)
    }

    ContextSummaryAutoSaveEffects(
        currentConfig = currentConfig,
        contextInputsProvider = { contextLengthInput to maxContextLengthInput },
        summaryInputsProvider = {
            listOf(
                enableSummary,
                summaryTokenThresholdInput,
                enableSummaryByMessageCount,
                summaryMessageCountThresholdInput
            )
        },
        modelConfigManager = modelConfigManager,
        errorValidContextLength = errorValidContextLength,
        errorValidMaxContextLength = errorValidMaxContextLength,
        errorSaveFailed = errorSaveFailed,
        errorSummaryThresholdRange = errorSummaryThresholdRange,
        errorValidMessageCount = errorValidMessageCount,
        onContextErrorChange = { contextError = it },
        onSummaryErrorChange = { summaryError = it }
    )
    HistoryRetentionAutoSaveEffects(
        historyInputsProvider = {
            maxImageHistoryUserTurnsInput to maxMediaHistoryUserTurnsInput
        },
        savedHistoryValuesProvider = {
            savedMaxImageHistoryUserTurns to savedMaxMediaHistoryUserTurns
        },
        apiPreferences = apiPreferences,
        errorSaveFailed = errorSaveFailed,
        onHistoryErrorChange = { historyError = it }
    )

    CustomScaffold() { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(scrollState)
            ) {
                SettingsInfoBanner(
                    text = stringResource(id = R.string.context_summary_note),
                    backgroundColor = componentBackgroundColor
                )
                Spacer(modifier = Modifier.size(12.dp))

                SectionTitle(
                    text = stringResource(id = R.string.model_config),
                    icon = Icons.Default.Link
                )
                BoundModelConfigCard(
                    configDisplayName = currentConfigDisplayName,
                    backgroundColor = componentBackgroundColor
                )
                Spacer(modifier = Modifier.size(12.dp))

                // 自定义总结规则自动保存
                ContextSummaryCustomRulesAutoSaveEffect(
                    currentConfig = currentConfig,
                    summaryCustomRulesInputProvider = { summaryCustomRulesInput },
                    modelConfigManager = modelConfigManager,
                    errorSaveFailed = errorSaveFailed,
                    onSummaryErrorChange = { summaryError = it }
                )
                ContextSummarySectionsAutoSaveEffect(
                    currentConfig = currentConfig,
                    summarySectionsInputProvider = { summarySectionsInput },
                    useEnglish = useEnglish,
                    modelConfigManager = modelConfigManager,
                    errorSaveFailed = errorSaveFailed,
                    onSummaryErrorChange = { summaryError = it }
                )
                RenderContextSummaryConfigSections(
                    componentBackgroundColor = componentBackgroundColor,
                    contextLengthInput = contextLengthInput,
                    onContextLengthInputChange = {
                        contextLengthInput = it
                        contextError = null
                    },
                    maxContextLengthInput = maxContextLengthInput,
                    onMaxContextLengthInputChange = {
                        maxContextLengthInput = it
                        contextError = null
                    },
                    contextError = contextError,
                    enableSummary = enableSummary,
                    onEnableSummaryChange = { enableSummary = it },
                    summaryTokenThresholdInput = summaryTokenThresholdInput,
                    onSummaryTokenThresholdInputChange = {
                        summaryTokenThresholdInput = it
                        summaryError = null
                    },
                    enableSummaryByMessageCount = enableSummaryByMessageCount,
                    onEnableSummaryByMessageCountChange = { enableSummaryByMessageCount = it },
                    summaryMessageCountThresholdInput = summaryMessageCountThresholdInput,
                    onSummaryMessageCountThresholdInputChange = {
                        summaryMessageCountThresholdInput = it
                        summaryError = null
                    },
                    summaryCustomRulesInput = summaryCustomRulesInput,
                    onSummaryCustomRulesInputChange = {
                        summaryCustomRulesInput = it
                    },
                    summarySectionsInput = summarySectionsInput,
                    onSummarySectionsInputChange = {
                        summarySectionsInput = it
                    },
                    onOpenFullscreenEditor = { editorTitle, editorValue, onEditorValueChange ->
                        fullscreenTextEditor = FullscreenTextEditorRequest(
                            title = editorTitle,
                            value = editorValue,
                            onValueChange = onEditorValueChange
                        )
                    },
                    summaryError = summaryError
                )

                Spacer(modifier = Modifier.size(12.dp))
                RenderHistoryRetentionSection(
                    componentBackgroundColor = componentBackgroundColor,
                    maxImageHistoryUserTurnsInput = maxImageHistoryUserTurnsInput,
                    onMaxImageHistoryUserTurnsInputChange = { maxImageHistoryUserTurnsInput = it },
                    maxMediaHistoryUserTurnsInput = maxMediaHistoryUserTurnsInput,
                    onMaxMediaHistoryUserTurnsInputChange = { maxMediaHistoryUserTurnsInput = it },
                    onReset = {
                        scope.launch {
                            historyError = null
                            apiPreferences.resetHistoryRetentionSettings()
                            maxImageHistoryUserTurnsInput =
                                apiPreferences.maxImageHistoryUserTurnsFlow.first().toString()
                            maxMediaHistoryUserTurnsInput =
                                apiPreferences.maxMediaHistoryUserTurnsFlow.first().toString()
                            showSaveSuccessMessage = true
                        }
                    },
                    historyError = historyError
                )

                Spacer(modifier = Modifier.size(16.dp))
            }

            RenderContextSummaryDialogs(
                showSaveSuccessMessage = showSaveSuccessMessage,
                onDismissSaveSuccess = { showSaveSuccessMessage = false }
            )
            fullscreenTextEditor?.let { request ->
                FullscreenSettingsTextEditor(
                    request = request,
                    onDismiss = { fullscreenTextEditor = null }
                )
            }
        }
    }
}

@Composable
private fun ContextSummaryAutoSaveEffects(
    currentConfig: ModelConfigData?,
    contextInputsProvider: () -> Pair<String, String>,
    summaryInputsProvider: () -> List<Any>,
    modelConfigManager: ModelConfigManager,
    errorValidContextLength: String,
    errorValidMaxContextLength: String,
    errorSaveFailed: String,
    errorSummaryThresholdRange: String,
    errorValidMessageCount: String,
    onContextErrorChange: (String?) -> Unit,
    onSummaryErrorChange: (String?) -> Unit
) {
    val latestConfig by rememberUpdatedState(currentConfig)

    LaunchedEffect(currentConfig?.id) {
        val configId = currentConfig?.id ?: return@LaunchedEffect
        snapshotFlow { contextInputsProvider() }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest { (contextText, maxText) ->
                val contextValue = contextText.toFloatOrNull()
                val maxValue = maxText.toFloatOrNull()
                when {
                    contextValue == null || contextValue <= 0f ->
                        onContextErrorChange(errorValidContextLength)
                    maxValue == null || maxValue <= 0f ->
                        onContextErrorChange(errorValidMaxContextLength)
                    else -> {
                        val current = latestConfig ?: return@collectLatest
                        if (current.id != configId) return@collectLatest
                        if (current.contextLength == contextValue && current.maxContextLength == maxValue) {
                            onContextErrorChange(null)
                            return@collectLatest
                        }
                        try {
                            modelConfigManager.updateContextSettings(
                                configId = current.id,
                                contextLength = contextValue,
                                maxContextLength = maxValue,
                                enableMaxContextMode = current.enableMaxContextMode
                            )
                            onContextErrorChange(null)
                        } catch (e: Exception) {
                            onContextErrorChange(e.message ?: errorSaveFailed)
                        }
                    }
                }
            }
    }

    LaunchedEffect(currentConfig?.id) {
        val configId = currentConfig?.id ?: return@LaunchedEffect
        snapshotFlow { summaryInputsProvider() }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest {
                val current = latestConfig ?: return@collectLatest
                if (current.id != configId) return@collectLatest

                val enableSummary = it[0] as Boolean
                val summaryTokenThresholdInput = it[1] as String
                val enableSummaryByMessageCount = it[2] as Boolean
                val summaryMessageCountThresholdInput = it[3] as String

                if (!enableSummary) {
                    if (current.enableSummary) {
                        try {
                            modelConfigManager.updateSummarySettings(
                                configId = current.id,
                                enableSummary = false,
                                summaryTokenThreshold = current.summaryTokenThreshold,
                                enableSummaryByMessageCount = current.enableSummaryByMessageCount,
                                summaryMessageCountThreshold = current.summaryMessageCountThreshold
                            )
                            onSummaryErrorChange(null)
                        } catch (e: Exception) {
                            onSummaryErrorChange(e.message ?: errorSaveFailed)
                        }
                    }
                    return@collectLatest
                }

                val threshold = summaryTokenThresholdInput.toFloatOrNull()
                val messageCount = summaryMessageCountThresholdInput.toIntOrNull()
                when {
                    threshold == null || threshold <= 0f || threshold >= 1f ->
                        onSummaryErrorChange(errorSummaryThresholdRange)
                    enableSummaryByMessageCount && (messageCount == null || messageCount <= 0) ->
                        onSummaryErrorChange(errorValidMessageCount)
                    else -> {
                        val nextMessageCount =
                            if (enableSummaryByMessageCount) {
                                messageCount ?: current.summaryMessageCountThreshold
                            } else {
                                current.summaryMessageCountThreshold
                            }
                        val isNoOp =
                            current.enableSummary == enableSummary &&
                                current.summaryTokenThreshold == threshold &&
                                current.enableSummaryByMessageCount == enableSummaryByMessageCount &&
                                current.summaryMessageCountThreshold == nextMessageCount
                        if (isNoOp) {
                            onSummaryErrorChange(null)
                            return@collectLatest
                        }
                        try {
                            modelConfigManager.updateSummarySettings(
                                configId = current.id,
                                enableSummary = enableSummary,
                                summaryTokenThreshold = threshold,
                                enableSummaryByMessageCount = enableSummaryByMessageCount,
                                summaryMessageCountThreshold = nextMessageCount
                            )
                            onSummaryErrorChange(null)
                        } catch (e: Exception) {
                            onSummaryErrorChange(e.message ?: errorSaveFailed)
                        }
                    }
                }
            }
    }
}

@Composable
private fun HistoryRetentionAutoSaveEffects(
    historyInputsProvider: () -> Pair<String, String>,
    savedHistoryValuesProvider: () -> Pair<Int, Int>,
    apiPreferences: ApiPreferences,
    errorSaveFailed: String,
    onHistoryErrorChange: (String?) -> Unit
) {
    val latestSavedHistoryValues by rememberUpdatedState(savedHistoryValuesProvider())

    LaunchedEffect(apiPreferences) {
        snapshotFlow { historyInputsProvider() }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest { (imageTurnsText, mediaTurnsText) ->
                val imageTurns = imageTurnsText.toIntOrNull()
                val mediaTurns = mediaTurnsText.toIntOrNull()
                if (imageTurns == null || imageTurns < 0 || mediaTurns == null || mediaTurns < 0) {
                    return@collectLatest
                }

                val (savedImageTurns, savedMediaTurns) = latestSavedHistoryValues
                if (imageTurns == savedImageTurns && mediaTurns == savedMediaTurns) {
                    onHistoryErrorChange(null)
                    return@collectLatest
                }

                try {
                    apiPreferences.saveMaxImageHistoryUserTurns(imageTurns)
                    apiPreferences.saveMaxMediaHistoryUserTurns(mediaTurns)
                    onHistoryErrorChange(null)
                } catch (e: Exception) {
                    onHistoryErrorChange(e.message ?: errorSaveFailed)
                }
            }
    }
}

@Composable
private fun ContextSummaryCustomRulesAutoSaveEffect(
    currentConfig: ModelConfigData?,
    summaryCustomRulesInputProvider: () -> String,
    modelConfigManager: ModelConfigManager,
    errorSaveFailed: String,
    onSummaryErrorChange: (String?) -> Unit
) {
    val latestConfig by rememberUpdatedState(currentConfig)

    LaunchedEffect(currentConfig?.id) {
        val configId = currentConfig?.id ?: return@LaunchedEffect
        snapshotFlow { summaryCustomRulesInputProvider() }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest { rulesText ->
                val current = latestConfig ?: return@collectLatest
                if (current.id != configId) return@collectLatest
                if (current.summaryCustomRules == rulesText) return@collectLatest
                try {
                    modelConfigManager.updateSummarySettings(
                        configId = current.id,
                        enableSummary = current.enableSummary,
                        summaryTokenThreshold = current.summaryTokenThreshold,
                        enableSummaryByMessageCount = current.enableSummaryByMessageCount,
                        summaryMessageCountThreshold = current.summaryMessageCountThreshold,
                        summaryCustomRules = rulesText
                    )
                    onSummaryErrorChange(null)
                } catch (e: Exception) {
                    onSummaryErrorChange(e.message ?: errorSaveFailed)
                }
            }
    }
}

@Composable
private fun ContextSummarySectionsAutoSaveEffect(
    currentConfig: ModelConfigData?,
    summarySectionsInputProvider: () -> List<SummarySectionConfig>,
    useEnglish: Boolean,
    modelConfigManager: ModelConfigManager,
    errorSaveFailed: String,
    onSummaryErrorChange: (String?) -> Unit
) {
    val latestConfig by rememberUpdatedState(currentConfig)

    LaunchedEffect(currentConfig?.id) {
        val configId = currentConfig?.id ?: return@LaunchedEffect
        snapshotFlow { summarySectionsInputProvider() }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest { sections ->
                val current = latestConfig ?: return@collectLatest
                if (current.id != configId) return@collectLatest
                val overrides =
                    FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish)
                if (current.summarySectionOverrides == overrides) return@collectLatest
                try {
                    modelConfigManager.updateSummarySettings(
                        configId = current.id,
                        enableSummary = current.enableSummary,
                        summaryTokenThreshold = current.summaryTokenThreshold,
                        enableSummaryByMessageCount = current.enableSummaryByMessageCount,
                        summaryMessageCountThreshold = current.summaryMessageCountThreshold,
                        summarySectionOverrides = overrides
                    )
                    onSummaryErrorChange(null)
                } catch (e: Exception) {
                    onSummaryErrorChange(e.message ?: errorSaveFailed)
                }
            }
    }
}

@Composable
private fun RenderContextSummaryConfigSections(
    componentBackgroundColor: Color,
    contextLengthInput: String,
    onContextLengthInputChange: (String) -> Unit,
    maxContextLengthInput: String,
    onMaxContextLengthInputChange: (String) -> Unit,
    contextError: String?,
    enableSummary: Boolean,
    onEnableSummaryChange: (Boolean) -> Unit,
    summaryTokenThresholdInput: String,
    onSummaryTokenThresholdInputChange: (String) -> Unit,
    enableSummaryByMessageCount: Boolean,
    onEnableSummaryByMessageCountChange: (Boolean) -> Unit,
    summaryMessageCountThresholdInput: String,
    onSummaryMessageCountThresholdInputChange: (String) -> Unit,
    summaryCustomRulesInput: String,
    onSummaryCustomRulesInputChange: (String) -> Unit,
    summarySectionsInput: List<SummarySectionConfig>,
    onSummarySectionsInputChange: (List<SummarySectionConfig>) -> Unit,
    onOpenFullscreenEditor: (String, String, (String) -> Unit) -> Unit,
    summaryError: String?
) {
    SectionTitle(
        text = stringResource(id = R.string.settings_context_title),
        icon = Icons.Default.Analytics
    )
    SettingsInfoBanner(
        text = stringResource(id = R.string.settings_context_card_content),
        backgroundColor = componentBackgroundColor
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_context_length),
        subtitle = stringResource(id = R.string.settings_context_length_subtitle),
        value = contextLengthInput,
        onValueChange = onContextLengthInputChange,
        unitText = "K",
        backgroundColor = componentBackgroundColor,
        allowDecimal = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_max_context_length),
        subtitle = stringResource(id = R.string.settings_max_context_length_subtitle),
        value = maxContextLengthInput,
        onValueChange = onMaxContextLengthInputChange,
        unitText = "K",
        backgroundColor = componentBackgroundColor,
        allowDecimal = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
    )
    contextError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    Spacer(modifier = Modifier.size(8.dp))
    SectionTitle(
        text = stringResource(id = R.string.settings_summary_title),
        icon = Icons.Default.Summarize
    )
    SettingsSwitchRow(
        title = stringResource(id = R.string.settings_enable_summary),
        subtitle = stringResource(id = R.string.settings_enable_summary_desc),
        checked = enableSummary,
        onCheckedChange = onEnableSummaryChange,
        backgroundColor = componentBackgroundColor
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_summary_threshold),
        subtitle = stringResource(id = R.string.settings_summary_threshold_subtitle),
        value = summaryTokenThresholdInput,
        onValueChange = onSummaryTokenThresholdInputChange,
        backgroundColor = componentBackgroundColor,
        enabled = enableSummary,
        allowDecimal = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    )
    SettingsSwitchRow(
        title = stringResource(id = R.string.settings_enable_summary_by_message_count),
        subtitle = stringResource(id = R.string.settings_enable_summary_by_message_count_desc),
        checked = enableSummaryByMessageCount,
        onCheckedChange = onEnableSummaryByMessageCountChange,
        backgroundColor = componentBackgroundColor,
        enabled = enableSummary
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_summary_message_count_threshold),
        subtitle = stringResource(id = R.string.settings_summary_message_count_threshold_subtitle),
        value = summaryMessageCountThresholdInput,
        onValueChange = onSummaryMessageCountThresholdInputChange,
        unitText = stringResource(id = R.string.model_config_unit_items),
        backgroundColor = componentBackgroundColor,
        enabled = enableSummary && enableSummaryByMessageCount
    )
    summaryError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    Spacer(modifier = Modifier.size(8.dp))
    val globalRulesTitle = stringResource(id = R.string.settings_summary_custom_rules)
    SettingsMultilineTextField(
        title = globalRulesTitle,
        subtitle = stringResource(id = R.string.settings_summary_custom_rules_desc),
        value = summaryCustomRulesInput,
        onValueChange = onSummaryCustomRulesInputChange,
        backgroundColor = componentBackgroundColor,
        enabled = enableSummary,
        onOpenFullscreen = {
            onOpenFullscreenEditor(
                globalRulesTitle,
                summaryCustomRulesInput,
                onSummaryCustomRulesInputChange
            )
        }
    )
    Spacer(modifier = Modifier.size(12.dp))
    SectionTitle(
        text = stringResource(id = R.string.settings_summary_structure),
        icon = Icons.Default.Summarize
    )
    summarySectionsInput.forEachIndexed { index, section ->
        SummarySectionEditor(
            section = section,
            onSectionChange = { updatedSection ->
                onSummarySectionsInputChange(
                    summarySectionsInput.mapIndexed { currentIndex, currentSection ->
                        if (currentIndex == index) updatedSection else currentSection
                    }
                )
            },
            backgroundColor = componentBackgroundColor,
            enabled = enableSummary,
            onOpenFullscreenEditor = onOpenFullscreenEditor
        )
    }
}

@Composable
private fun SummarySectionEditor(
    section: SummarySectionConfig,
    onSectionChange: (SummarySectionConfig) -> Unit,
    backgroundColor: Color,
    enabled: Boolean,
    onOpenFullscreenEditor: (String, String, (String) -> Unit) -> Unit
) {
    SettingsSwitchRow(
        title = section.title,
        subtitle = stringResource(id = R.string.settings_summary_section_enabled_desc),
        checked = section.enabled,
        onCheckedChange = { onSectionChange(section.copy(enabled = it)) },
        backgroundColor = backgroundColor,
        enabled = enabled
    )
    SettingsMultilineTextField(
        title = stringResource(id = R.string.settings_summary_section_title),
        subtitle = stringResource(id = R.string.settings_summary_section_title_desc),
        value = section.title,
        onValueChange = { onSectionChange(section.copy(title = it)) },
        backgroundColor = backgroundColor,
        enabled = enabled && section.enabled,
        singleLine = true,
        minHeight = 40.dp
    )
    SettingsMultilineTextField(
        title = stringResource(id = R.string.settings_summary_section_instruction),
        subtitle = stringResource(id = R.string.settings_summary_section_instruction_desc),
        value = section.instruction,
        onValueChange = { onSectionChange(section.copy(instruction = it)) },
        backgroundColor = backgroundColor,
        enabled = enabled && section.enabled,
        onOpenFullscreen = {
            onOpenFullscreenEditor(section.title, section.instruction) { newValue ->
                onSectionChange(section.copy(instruction = newValue))
            }
        }
    )
    Spacer(modifier = Modifier.size(8.dp))
}

@Composable
private fun RenderHistoryRetentionSection(
    componentBackgroundColor: Color,
    maxImageHistoryUserTurnsInput: String,
    onMaxImageHistoryUserTurnsInputChange: (String) -> Unit,
    maxMediaHistoryUserTurnsInput: String,
    onMaxMediaHistoryUserTurnsInputChange: (String) -> Unit,
    onReset: () -> Unit,
    historyError: String?
) {
    SectionTitle(
        text = stringResource(id = R.string.settings_history_retention_title),
        icon = Icons.Default.History
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_max_image_history_user_turns),
        subtitle = stringResource(id = R.string.settings_max_image_history_user_turns_subtitle),
        value = maxImageHistoryUserTurnsInput,
        onValueChange = onMaxImageHistoryUserTurnsInputChange,
        unitText = stringResource(id = R.string.context_unit_times),
        backgroundColor = componentBackgroundColor
    )

    SettingsInputField(
        title = stringResource(id = R.string.settings_max_media_history_user_turns),
        subtitle = stringResource(id = R.string.settings_max_media_history_user_turns_subtitle),
        value = maxMediaHistoryUserTurnsInput,
        onValueChange = onMaxMediaHistoryUserTurnsInputChange,
        unitText = stringResource(id = R.string.context_unit_times),
        backgroundColor = componentBackgroundColor
    )

    Button(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
    ) {
        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.context_reset_all_settings), style = MaterialTheme.typography.bodyLarge)
    }

    historyError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun BoxScope.RenderContextSummaryDialogs(
    showSaveSuccessMessage: Boolean,
    onDismissSaveSuccess: () -> Unit
) {
    if (showSaveSuccessMessage) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            onDismissSaveSuccess()
        }
        Snackbar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            action = {
                TextButton(onClick = onDismissSaveSuccess) {
                    Text(stringResource(id = android.R.string.ok))
                }
            }
        ) {
            Text(stringResource(id = R.string.settings_saved))
        }
    }
}

private fun formatFloatValue(value: Float?): String {
    if (value == null) return ""
    return if (value % 1f == 0f) value.toInt().toString() else value.toString()
}

private fun buildBoundConfigDisplayName(config: ModelConfigData?, fallbackConfigId: String): String {
    val effectiveConfig = config ?: return fallbackConfigId.ifBlank { FunctionalConfigManager.DEFAULT_CONFIG_ID }
    return if (effectiveConfig.name.isBlank()) {
        effectiveConfig.id
    } else {
        effectiveConfig.name
    }
}

@Composable
private fun BoundModelConfigCard(configDisplayName: String, backgroundColor: Color) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.context_summary_current_model_config, configDisplayName),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = stringResource(id = R.string.context_summary_bound_config_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsInfoBanner(text: String, backgroundColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backgroundColor: Color,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(contentAlpha),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingsInputField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    unitText: String? = null,
    backgroundColor: Color,
    enabled: Boolean = true,
    allowDecimal: Boolean = false,
    keyboardOptions: KeyboardOptions =
        KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
) {
    val focusManager = LocalFocusManager.current
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = { newText ->
                    if (enabled) {
                        onValueChange(if (allowDecimal) filterDecimalInput(newText) else newText.filter { it.isDigit() })
                    }
                },
                enabled = enabled,
                modifier =
                    Modifier.width(if (allowDecimal) 88.dp else 72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                textStyle =
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                keyboardOptions = keyboardOptions,
                keyboardActions = KeyboardActions(onDone = { if (enabled) focusManager.clearFocus() }),
                singleLine = true
            )
            if (unitText != null) {
                Text(
                    text = unitText,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

private fun filterDecimalInput(input: String): String {
    var dotSeen = false
    val result = StringBuilder()
    input.forEachIndexed { index, c ->
        when {
            c.isDigit() -> result.append(c)
            c == '.' && !dotSeen -> {
                dotSeen = true
                if (result.isEmpty() && index == 0) result.append("0.") else result.append(c)
            }
        }
    }
    return result.toString()
}

@Composable
private fun SettingsMultilineTextField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    backgroundColor: Color,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minHeight: Dp = 80.dp,
    onOpenFullscreen: (() -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(contentAlpha)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = { newText ->
                if (enabled) {
                    onValueChange(newText)
                }
            },
            enabled = enabled,
            modifier =
                Modifier.fillMaxWidth()
                    .let { if (singleLine) it.heightIn(min = minHeight) else it.height(120.dp) }
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            textStyle =
                TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 5,
            maxLines = if (singleLine) 1 else 5,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentAlignment =
                            if (singleLine) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                    if (!singleLine && onOpenFullscreen != null) {
                        IconButton(
                            onClick = { if (enabled) onOpenFullscreen() },
                            enabled = enabled,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription =
                                    stringResource(id = R.string.chat_fullscreen_input),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

private data class FullscreenTextEditorRequest(
    val title: String,
    val value: String,
    val onValueChange: (String) -> Unit
)

@Composable
private fun FullscreenSettingsTextEditor(
    request: FullscreenTextEditorRequest,
    onDismiss: () -> Unit
) {
    var editorValue by remember(request.title, request.value) { mutableStateOf(request.value) }

    fun finishEditing() {
        request.onValueChange(editorValue)
        onDismiss()
    }

    Dialog(
        onDismissRequest = { finishEditing() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .systemBarsPadding()
                        .imePadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { finishEditing() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.common_close)
                        )
                    }
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { finishEditing() }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(id = R.string.save)
                        )
                    }
                }
                HorizontalDivider()
                TextField(
                    value = editorValue,
                    onValueChange = { editorValue = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
